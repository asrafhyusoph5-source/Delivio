package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.CommissionSettings
import com.example.data.model.DeliveryRates
import com.example.data.model.DispatchSettings
import com.example.data.model.LocationPoint
import com.example.data.model.ParcelPeakNightRule
import com.example.data.model.ParcelVehicleRate
import com.example.data.model.RateConfig
import com.example.data.model.Schema2DeliveryConfig
import com.example.data.model.Schema2FreeDeliveryConfig
import com.example.data.model.Schema2NightConfig
import com.example.data.model.Schema2PeakConfig
import com.example.data.model.Schema2PickupConfig
import com.example.data.model.Schema2ServiceFeeConfig
import com.example.data.model.Schema2ServiceRates
import com.example.data.model.VatConfig
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.ui.screens.home.PabiliLiveFeeBreakdownCard
import com.example.ui.screens.home.ParcelFareBreakdownCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DelivioTheme
import com.example.ui.theme.DisabledReadableDark
import com.example.ui.theme.DisabledReadableLight
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.util.FeeCalculator
import com.example.util.ParcelFeeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PricingContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun schema2DeliveryFee_appliesClampPeakMultiplierNightSurchargeOptionAdjustAndExtraStops() {
        val zone = ZoneId.of("Asia/Manila")
        val peakAndNightMs = ZonedDateTime.of(2026, 6, 1, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
        val offPeakDayMs = ZonedDateTime.of(2026, 6, 1, 15, 0, 0, 0, zone).toInstant().toEpochMilli()

        val rate = Schema2ServiceRates(
            delivery = Schema2DeliveryConfig(
                baseFare = 45.0,
                includedKm = 2.0,
                perKm = 12.0,
                minFee = 45.0,
                maxFee = 200.0
            ),
            pickup = Schema2PickupConfig(
                baseFee = 15.0,
                freeKm = 1.0,
                perKm = 8.0,
                maxFee = 60.0
            ),
            serviceFee = Schema2ServiceFeeConfig(mode = "flat", value = 10.0),
            peak = Schema2PeakConfig(enabled = true, multiplier = 1.5, startTime = "18:00", endTime = "21:00"),
            night = Schema2NightConfig(enabled = true, surcharge = 25.0, startTime = "19:30", endTime = "05:00"),
            extraStopFee = 15.0
        )

        // 5 km -> base 45 + (5 - 2) * 12 = 81.0
        // Off-peak day + priority option(+20): 81 + 20 = 101.0
        val dayPriorityFee = FeeCalculator.calculateSchema2DeliveryFee(
            rate = rate,
            distanceKm = 5.0,
            optionAdjustment = 20.0,
            nowMillis = offPeakDayMs,
            timezone = "Asia/Manila"
        )
        assertEquals(101.0, dayPriorityFee, 0.001)

        // Peak + Night + 2 extra stops (20:00): (81.0 * 1.5) + 25.0 + (2 * 15.0) = 121.5 + 25 + 30 = 176.5
        val peakNightTaxiFee = FeeCalculator.calculateSchema2DeliveryFee(
            rate = rate,
            distanceKm = 5.0,
            extraStops = 2,
            nowMillis = peakAndNightMs,
            timezone = "Asia/Manila"
        )
        assertEquals(176.5, peakNightTaxiFee, 0.001)

        // Max fee clamp check: 30 km -> 45 + 28*12 = 381 -> clamped to 200.0 * 1.0
        val clampedDayFee = FeeCalculator.calculateSchema2DeliveryFee(
            rate = rate,
            distanceKm = 30.0,
            nowMillis = offPeakDayMs,
            timezone = "Asia/Manila"
        )
        assertEquals(200.0, clampedDayFee, 0.001)
    }

    @Test
    fun schema2PickupFee_computesPlatformPaidFeeAndIsExcludedFromCustomerFees() {
        val pickupCfg = Schema2PickupConfig(
            baseFee = 15.0,
            freeKm = 1.0,
            perKm = 8.0,
            maxFee = 45.0
        )
        // Within freeKm: 15.0
        assertEquals(15.0, FeeCalculator.calculateSchema2PickupFee(pickupCfg, 0.8), 0.001)
        // 3.5 km: 15 + 2.5 * 8 = 35.0
        assertEquals(35.0, FeeCalculator.calculateSchema2PickupFee(pickupCfg, 3.5), 0.001)
        // Clamped to maxFee: 10 km -> 15 + 72 = 87 -> 45.0
        assertEquals(45.0, FeeCalculator.calculateSchema2PickupFee(pickupCfg, 10.0), 0.001)

        // Verify customer calculation ignores pickupFee completely
        val s2 = Schema2ServiceRates(
            delivery = Schema2DeliveryConfig(baseFare = 50.0, includedKm = 2.0, perKm = 10.0, minFee = 50.0, maxFee = 200.0),
            pickup = pickupCfg,
            serviceFee = Schema2ServiceFeeConfig(mode = "flat", value = 12.0)
        )
        val res = FeeCalculator.calculateForSchema2Service(
            itemsSubtotal = 300.0,
            effectiveDeliveryFee = 50.0,
            rate = s2,
            serviceKey = "food"
        )
        assertEquals(362.0, res.total, 0.001)
    }

    @Test
    fun schema2ServiceFee_appliesCorrectPercentBasePerService() {
        val percentRate = Schema2ServiceRates(
            serviceFee = Schema2ServiceFeeConfig(mode = "percent", value = 10.0)
        )

        // Food, convenience, pabili: percent of (items + deliveryFee)
        val foodFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "food",
            itemsSubtotal = 400.0,
            effectiveDeliveryFee = 50.0
        )
        assertEquals(45.0, foodFee, 0.001)

        val convFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "convenience",
            itemsSubtotal = 250.0,
            effectiveDeliveryFee = 50.0
        )
        assertEquals(30.0, convFee, 0.001)

        val pabiliFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "pabili",
            itemsSubtotal = 500.0,
            effectiveDeliveryFee = 60.0
        )
        assertEquals(56.0, pabiliFee, 0.001)

        // Parcel, taxi, instant: percent of deliveryFee ONLY
        val parcelFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "parcel",
            itemsSubtotal = 999.0,
            effectiveDeliveryFee = 80.0
        )
        assertEquals(8.0, parcelFee, 0.001)

        val taxiFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "taxi",
            itemsSubtotal = 0.0,
            effectiveDeliveryFee = 140.0
        )
        assertEquals(14.0, taxiFee, 0.001)

        val instantFee = FeeCalculator.calculateSchema2ServiceFee(
            rate = percentRate,
            serviceKey = "instant",
            itemsSubtotal = 500.0,
            effectiveDeliveryFee = 120.0
        )
        assertEquals(12.0, instantFee, 0.001)
    }

    @Test
    fun vatCalculation_supportsAllThreeBasesForAddedAndIncludedModes() {
        // 1. serviceFee only (added & included)
        val addedServiceOnly = FeeCalculator.calculateFees(
            subtotal = 500.0,
            deliveryFee = 50.0,
            serviceFeeType = "flat",
            serviceFeeValue = 20.0,
            vatConfig = VatConfig(enabled = true, percent = 12.0, mode = "added", base = "serviceFee")
        )
        assertEquals(20.0, addedServiceOnly.vatBase, 0.001)
        assertEquals(2.40, addedServiceOnly.vat, 0.001)
        assertEquals(572.40, addedServiceOnly.total, 0.001)

        val includedServiceOnly = FeeCalculator.calculateFees(
            subtotal = 500.0,
            deliveryFee = 50.0,
            serviceFeeType = "flat",
            serviceFeeValue = 112.0,
            vatConfig = VatConfig(enabled = true, percent = 12.0, mode = "included", base = "service_fee")
        )
        assertEquals(112.0, includedServiceOnly.vatBase, 0.001)
        assertEquals(12.0, includedServiceOnly.vat, 0.001)
        assertEquals(662.0, includedServiceOnly.total, 0.001)

        // 2. delivery + service ("fees")
        val addedFees = FeeCalculator.calculateFees(
            subtotal = 500.0,
            deliveryFee = 60.0,
            serviceFeeType = "flat",
            serviceFeeValue = 40.0,
            vatConfig = VatConfig(enabled = true, percent = 12.0, mode = "added", base = "fees")
        )
        assertEquals(100.0, addedFees.vatBase, 0.001)
        assertEquals(12.0, addedFees.vat, 0.001)
        assertEquals(612.0, addedFees.total, 0.001)

        // 3. entire order ("total")
        val addedTotal = FeeCalculator.calculateFees(
            subtotal = 400.0,
            deliveryFee = 60.0,
            serviceFeeType = "flat",
            serviceFeeValue = 40.0,
            vatConfig = VatConfig(enabled = true, percent = 12.0, mode = "added", base = "total")
        )
        assertEquals(500.0, addedTotal.vatBase, 0.001)
        assertEquals(60.0, addedTotal.vat, 0.001)
        assertEquals(560.0, addedTotal.total, 0.001)
    }

    @Test
    fun schema1Fallback_preservesLegacyCalculationWhenSchemaIsNot2() {
        val rates = DeliveryRates(
            schema = 1,
            food = RateConfig(
                baseFare = 39.0,
                includedKm = 2.0,
                perKmRate = 10.0,
                minFee = 39.0,
                maxFee = 250.0,
                serviceFee = 10.0
            )
        )
        assertFalse(rates.isSchema2)

        val feeResult = FeeCalculator.calculateForRateConfig(
            subtotal = 200.0,
            deliveryFee = rates.food.baseFare,
            rateConfig = rates.food
        )
        assertEquals(39.0, feeResult.deliveryFee, 0.001)
        assertEquals(10.0, feeResult.serviceFee, 0.001)
        assertEquals(249.0, feeResult.total, 0.001)
    }

    @Test
    fun orderRepository_taxiSnapshotWritesPricingAndPreservesFees() {
        val repo = OrderRepository(FirestoreService())
        val doc = repo.buildTaxiOrderDocument(
            customerId = "cust_1",
            customerName = "Ana Reyes",
            customerPhone = "+63 917 111 2222",
            vehicleType = "tricycle",
            cityId = "puerto-princesa",
            pickup = LocationPoint(address = "Rizal Ave", lat = 9.739, lng = 118.735),
            dropoff = LocationPoint(address = "Malvar St", lat = 9.742, lng = 118.740),
            stops = emptyList(),
            distanceKm = 3.0,
            rideFare = 52.0,
            serviceFee = 8.0,
            total = 67.2,
            paymentMethod = "cod",
            note = "Near gate",
            vat = 7.2,
            vatPercent = 12.0,
            vatMode = "added",
            vatBase = 60.0,
            serviceFeeType = "flat",
            serviceFeePercent = 0.0,
            ratesVersion = 4
        )

        // Legacy fees map keys intact
        assertEquals(52.0, doc.fees.subtotal, 0.001)
        assertEquals(8.0, doc.fees.serviceFee, 0.001)
        assertEquals(7.2, doc.fees.vat, 0.001)
        assertEquals(67.2, doc.fees.total, 0.001)

        // New pricing snapshot populated accurately
        val pricing = doc.pricing
        assertNotNull(pricing)
        assertEquals(2, pricing!!.schema)
        assertEquals(4, pricing.ratesVersion)
        assertEquals("taxi", pricing.serviceKey)
        assertEquals("tricycle", pricing.vehicle)
        assertEquals(52.0, pricing.deliveryFee, 0.001)
        assertEquals(8.0, pricing.serviceFee, 0.001)
        assertEquals(7.2, pricing.vat, 0.001)
        assertEquals(67.2, pricing.total, 0.001)
        assertEquals("customer", pricing.payer)
    }

    @Test
    fun commissionAndDispatchDefaults_matchContract() {
        val commission = CommissionSettings()
        assertEquals(10.0, commission.rider.single, 0.001)
        assertEquals(20.0, commission.rider.second, 0.001)
        assertEquals(30.0, commission.rider.thirdPlus, 0.001)
        assertEquals(15.0, commission.merchantDefault, 0.001)

        val dispatch = DispatchSettings()
        assertEquals(45.0, dispatch.batchDirectionToleranceDeg, 0.001)
        assertEquals(40.0, dispatch.batchMaxDetourPercent, 0.001)
    }

    @Test
    fun comingSoonContrast_meetsWcagMinimum45InLightAndDarkThemes() {
        val lightSurfaceRatio = contrastRatio(DisabledReadableLight, LightSurface)
        val lightVariantRatio = contrastRatio(DisabledReadableLight, LightSurfaceVariant)
        val darkSurfaceRatio = contrastRatio(DisabledReadableDark, DarkSurface)
        val darkVariantRatio = contrastRatio(DisabledReadableDark, DarkSurfaceVariant)

        assertTrue("Light surface contrast was $lightSurfaceRatio", lightSurfaceRatio >= 4.5)
        assertTrue("Light variant contrast was $lightVariantRatio", lightVariantRatio >= 4.5)
        assertTrue("Dark surface contrast was $darkSurfaceRatio", darkSurfaceRatio >= 4.5)
        assertTrue("Dark variant contrast was $darkVariantRatio", darkVariantRatio >= 4.5)
    }

    @Test
    fun pabiliAndParcelBreakdownCards_showFreeDeliveryAndVatCleanlyWithoutInternalFields() {
        val parcelQuote = ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = "motorcycle",
            rate = ParcelVehicleRate(
                baseFare = 50.0,
                includedKm = 3.0,
                perKm = 14.0,
                serviceFee = 15.0,
                peak = ParcelPeakNightRule(enabled = true, multiplier = 1.2)
            ),
            distanceKm = 5.0,
            weightKg = 5.0,
            vatConfig = VatConfig(enabled = true, percent = 12.0, mode = "added", base = "fees")
        )

        composeRule.setContent {
            DelivioTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PabiliLiveFeeBreakdownCard(
                        deliveryFee = 0.0,
                        serviceFee = 20.0,
                        budgetLimit = 500.0,
                        vat = 2.40,
                        vatMode = "added",
                        freeDeliveryEnabled = true,
                        freeDeliveryMinSubtotal = 400.0,
                        estimatedItemsTotal = 450.0,
                        originalDeliveryFee = 55.0
                    )
                    ParcelFareBreakdownCard(
                        quote = parcelQuote,
                        feePayer = "sender",
                        selectedPaymentMethod = "cod"
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Free delivery UI
        composeRule.onNodeWithTag("pabili_free_delivery_card").assertIsDisplayed()
        composeRule.onNodeWithText("Free delivery unlocked!").assertIsDisplayed()
        composeRule.onNodeWithText("₱55.00").assertIsDisplayed()
        composeRule.onNodeWithText("Free delivery").assertIsDisplayed()

        // VAT displayed cleanly and internal peak/night/commission hidden
        composeRule.onNodeWithTag("parcel_vat_fee_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Pick Up Fee", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Commission", substring = true).assertDoesNotExist()
    }
}
