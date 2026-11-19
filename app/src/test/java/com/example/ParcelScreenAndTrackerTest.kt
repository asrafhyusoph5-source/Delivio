package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.Address
import com.example.data.model.DeliveryRates
import com.example.data.model.LocationPoint
import com.example.data.model.Order
import com.example.data.model.OrderDocument
import com.example.data.model.OrderFees
import com.example.data.model.OrderStatus
import com.example.data.model.ParcelDetails
import com.example.data.model.ParcelInfo
import com.example.data.model.ParcelPeakNightRule
import com.example.data.model.ParcelVehicleRate
import com.example.data.model.RateConfig
import com.example.data.model.Restaurant
import com.example.data.model.Rider
import com.example.data.model.RiderVehicleInfo
import com.example.data.model.ServiceType
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.data.repository.TrackerDisplayState
import com.example.ui.components.FloatingOrderTracker
import com.example.ui.components.OrderItemsSummary
import com.example.ui.components.RatingCardContent
import com.example.ui.components.RiderSection
import com.example.ui.components.StepperTimeline
import com.example.ui.components.TrackingMapLegend
import com.example.ui.components.resolveParcelHeaderTitle
import com.example.ui.components.resolveParcelStepIndexOrNull
import com.example.ui.screens.home.ParcelBottomBar
import com.example.ui.screens.home.ParcelFareBreakdownCard
import com.example.ui.screens.home.ParcelPackageDetailsCard
import com.example.ui.theme.DelivioTheme
import com.example.util.DelivioStatusConstants
import com.example.util.ParcelFeeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ParcelScreenAndTrackerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleParcelOrder(
        rawStatus: String = "Placed Order",
        vehicleType: String? = "motorcycle",
        rider: Rider? = null,
        riderLat: Double? = null,
        riderLng: Double? = null,
        paymentMethod: String = "cod",
        cashToCollect: Double = 95.0,
        cashCollectAt: String? = "sender"
    ): Order {
        return Order(
            id = "parcel_order_1",
            restaurant = Restaurant(
                id = "service_partner",
                name = "Leaked Merchant Name",
                category = "Parcel",
                rating = 4.8,
                reviewCount = 50,
                ratingSum = 240.0,
                ratingCount = 50,
                etaMinutes = 20,
                deliveryFee = 80.0,
                imageUrl = "https://example.com/shop.jpg",
                address = "999 Leaked Shop Address",
                phone = "09171234567"
            ),
            items = emptyList(),
            status = OrderStatus.PLACED,
            rawStatus = rawStatus,
            createdAt = 1_700_000_000_000L,
            subtotal = 0.0,
            deliveryFee = 80.0,
            serviceFee = 15.0,
            discount = 0.0,
            total = 95.0,
            deliveryAddress = Address(
                id = "dropoff",
                title = "Recipient: Sarah Connor",
                fullAddress = "Malvar St, Puerto Princesa",
                lat = 9.7420,
                lng = 118.7400
            ),
            pickupAddress = Address(
                id = "pickup",
                title = "Sender: Juan Dela Cruz",
                fullAddress = "Rizal Ave, Puerto Princesa",
                lat = 9.7392,
                lng = 118.7353
            ),
            rider = rider,
            riderId = rider?.id,
            riderLat = riderLat,
            riderLng = riderLng,
            serviceType = ServiceType.PARCEL,
            vehicleType = vehicleType,
            paymentMethod = paymentMethod,
            cashToCollect = cashToCollect,
            cashCollectAt = cashCollectAt,
            parcelDetails = ParcelDetails(
                senderName = "Juan Dela Cruz",
                senderPhone = "+63 917 111 2222",
                senderAddress = "Rizal Ave, Puerto Princesa",
                pickupLat = 9.7392f,
                pickupLng = 118.7353f,
                recipientName = "Sarah Connor",
                recipientPhone = "+63 917 890 1234",
                recipientAddress = "Malvar St, Puerto Princesa",
                dropoffLat = 9.7420f,
                dropoffLng = 118.7400f,
                packageSize = "Medium / Box",
                weightKg = 5.0,
                itemDescription = "Important contract documents",
                isFragile = true,
                vehicleType = vehicleType ?: "motorcycle"
            )
        )
    }

    @Test
    fun parcelStatusMapping_matchesOnlyExactTrimmedLowercasedStrings() {
        assertEquals(0, resolveParcelStepIndexOrNull("Placed Order"))
        assertEquals(0, resolveParcelStepIndexOrNull("  placed order "))
        assertEquals(1, resolveParcelStepIndexOrNull("Rider is on the way to sender"))
        assertEquals(2, resolveParcelStepIndexOrNull(" arrived at sender "))
        assertEquals(3, resolveParcelStepIndexOrNull("RIDER IS ON THE WAY TO RECIPIENT"))
        assertEquals(4, resolveParcelStepIndexOrNull("Arrived at recipient"))
        assertEquals(5, resolveParcelStepIndexOrNull("Delivered"))

        // Partial or unrelated status strings must NOT match
        assertNull(resolveParcelStepIndexOrNull("on the way"))
        assertNull(resolveParcelStepIndexOrNull("Rider is on the way"))
        assertNull(resolveParcelStepIndexOrNull("Rider is on the way to vendor"))
        assertNull(resolveParcelStepIndexOrNull("Arrived"))
        assertNull(resolveParcelStepIndexOrNull("On trip"))
        assertNull(resolveParcelStepIndexOrNull(""))
        assertNull(resolveParcelStepIndexOrNull(null))
    }

    @Test
    fun parcelHeaderTitles_followExactStatusProgression() {
        assertEquals("Placed Order", resolveParcelHeaderTitle(0))
        assertEquals("Rider is on the way to the sender", resolveParcelHeaderTitle(1))
        assertEquals("Rider is at the sender", resolveParcelHeaderTitle(2))
        assertEquals("Rider is on the way to the recipient", resolveParcelHeaderTitle(3))
        assertEquals("Rider is at the recipient", resolveParcelHeaderTitle(4))
        assertEquals("Delivered", resolveParcelHeaderTitle(5))
    }

    @Test
    fun parcelTimeline_ignoresRiderPresenceAndLocation_andKeepsLastKnownOnUnknownStatus() {
        val assignedRider = Rider(
            id = "r_parcel_1",
            name = "Carlos Mendoza",
            phone = "09175551234",
            vehicle = "Motorcycle",
            licensePlate = "ABC-123",
            rating = 4.9
        )
        var currentOrder by mutableStateOf(
            sampleParcelOrder(
                rawStatus = "Placed Order",
                rider = assignedRider,
                riderLat = 9.7400,
                riderLng = 118.7360
            )
        )

        composeRule.setContent {
            DelivioTheme {
                StepperTimeline(order = currentOrder)
            }
        }
        composeRule.waitForIdle()

        DelivioStatusConstants.PARCEL_STATUS_FLOW.forEach { step ->
            composeRule.onNodeWithText(step).assertIsDisplayed()
        }
        // Must stay on step 0 despite rider and GPS coordinates being present
        composeRule.onNodeWithText("Finding a rider for your parcel").assertIsDisplayed()

        // Step 1
        currentOrder = currentOrder.copy(rawStatus = "Rider is on the way to sender")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider is heading to the sender").assertIsDisplayed()

        // Step 2
        currentOrder = currentOrder.copy(rawStatus = "Arrived at sender")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider has arrived at the sender").assertIsDisplayed()

        // Unknown status keeps last known step (step 2)
        currentOrder = currentOrder.copy(rawStatus = "Transient Unknown Backend Status")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider has arrived at the sender").assertIsDisplayed()

        // Step 3
        currentOrder = currentOrder.copy(rawStatus = "Rider is on the way to recipient")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider is heading to the recipient").assertIsDisplayed()

        // Step 4
        currentOrder = currentOrder.copy(rawStatus = "Arrived at recipient")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider has arrived at the recipient").assertIsDisplayed()

        // Step 5
        currentOrder = currentOrder.copy(rawStatus = "Delivered", status = OrderStatus.DELIVERED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delivered safely!").assertIsDisplayed()
    }

    @Test
    fun parcelTracking_hidesMerchantCard_showsSenderLegend_bookedVehicle_andCashRules() {
        val motoRider = Rider(
            id = "rider_moto",
            name = "Marco Villanueva",
            phone = "09178889999",
            vehicle = "4 Seater Car", // Wrong vehicle string on rider should be ignored when order.vehicleType is passed
            licensePlate = "ABC-123",
            rating = 4.95
        )
        var currentVehicleType by mutableStateOf<String?>("motorcycle")
        var currentOrder by mutableStateOf(
            sampleParcelOrder(
                rawStatus = "Rider is on the way to sender",
                vehicleType = "motorcycle",
                rider = motoRider,
                paymentMethod = "cod",
                cashToCollect = 95.0,
                cashCollectAt = "sender"
            )
        )

        composeRule.setContent {
            DelivioTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TrackingMapLegend(
                        isTaxiOrder = false,
                        isPabiliOrder = false,
                        isParcelOrder = true,
                        isFallbackRoute = false
                    )
                    RiderSection(
                        rider = currentOrder.rider!!,
                        isCollapsed = false,
                        onToggleCollapse = {},
                        onCall = {},
                        onOpenChat = {},
                        etaText = "ETA: 15-25 mins",
                        alwaysShowVehicleDetails = true,
                        isParcel = true,
                        parcelVehicleType = currentVehicleType
                    )
                    OrderItemsSummary(order = currentOrder)
                }
            }
        }
        composeRule.waitForIdle()

        // Map legend
        composeRule.onNodeWithText("📍 Sender").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🛵 Rider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("🏁 Recipient").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("📍 Store").assertDoesNotExist()
        composeRule.onNodeWithText("📍 Vendor").assertDoesNotExist()

        // Rider booked vehicle strictly formats as Motorcycle (ABC-123)
        composeRule.onNodeWithText("Marco Villanueva").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Motorcycle (ABC-123)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("call_rider_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("chat_rider_button").performScrollTo().assertIsDisplayed()

        // Parcel Delivery Information + pickup cash collection rule
        composeRule.onNodeWithText("Parcel Delivery Information").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vehicle: Motorcycle • ⚠️ Fragile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cash on Delivery - pay ₱95.00 to the rider at pickup").performScrollTo().assertIsDisplayed()

        // Switch to sedan & recipient fee payer
        currentVehicleType = "sedan"
        currentOrder = currentOrder.copy(
            vehicleType = "sedan",
            rider = motoRider.copy(vehicle = "Sedan", licensePlate = "XYZ-789"),
            cashCollectAt = "recipient"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sedan (XYZ-789)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cash on Delivery - pay ₱95.00 to the rider at drop-off").performScrollTo().assertIsDisplayed()

        // Switch to wallet: no cash at any step
        currentOrder = currentOrder.copy(
            paymentMethod = "wallet",
            cashToCollect = 0.0,
            cashCollectAt = null
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paid with Delivio Wallet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun floatingOrderTracker_parcelHidesMerchantAndShopEdited_andRetainsStepAcrossCollapseExpand() {
        val rider = Rider(
            id = "rider_parcel_2",
            name = "Marco Villanueva",
            phone = "09178889999",
            vehicle = "Sedan",
            licensePlate = "XYZ-789",
            rating = 4.95
        )
        var currentOrder by mutableStateOf(
            sampleParcelOrder(
                rawStatus = "Arrived at sender",
                vehicleType = "sedan",
                rider = rider,
                paymentMethod = "cod",
                cashToCollect = 165.0,
                cashCollectAt = "recipient"
            ).copy(
                deliveryFee = 140.0,
                serviceFee = 25.0,
                total = 165.0,
                editedAt = 1_700_000_001_000L,
                editNote = "Should not display on parcel"
            )
        )
        var displayState by mutableStateOf(TrackerDisplayState.COLLAPSED)

        composeRule.setContent {
            DelivioTheme {
                FloatingOrderTracker(
                    order = currentOrder,
                    trackerState = displayState,
                    isMerchantCollapsed = false,
                    isRiderCollapsed = false,
                    onStateChange = { displayState = it },
                    onToggleMerchant = {},
                    onToggleRider = {},
                    onDismissOrder = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        composeRule.waitForIdle()

        // Collapsed shows step 2 header and hides shop-updated badge
        composeRule.onNodeWithText("Rider is at the sender").assertIsDisplayed()
        composeRule.onNodeWithText("⚠️ Updated by shop").assertDoesNotExist()

        // Unknown status while collapsed, then expand
        currentOrder = currentOrder.copy(rawStatus = "Transient Unknown Status")
        composeRule.waitForIdle()
        displayState = TrackerDisplayState.EXPANDED
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tracker_header_title").assertIsDisplayed()
        composeRule.onNodeWithText("Rider is at the sender").assertIsDisplayed()
        composeRule.onNodeWithText("⚠️ Your order was updated by the shop").assertDoesNotExist()
        composeRule.onNodeWithText("Leaked Merchant Name").assertDoesNotExist()
        composeRule.onNodeWithText("999 Leaked Shop Address").assertDoesNotExist()
        composeRule.onNodeWithTag("call_merchant_button").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_merchant_button").assertDoesNotExist()
        composeRule.onNodeWithText("📍 Sender").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sedan (XYZ-789)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cash on Delivery - pay ₱165.00 to the rider at drop-off").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun parcelRatingCard_doesNotShowMerchantNameOrMealPrompt() {
        composeRule.setContent {
            DelivioTheme {
                RatingCardContent(
                    restaurantName = "Leaked Merchant",
                    riderName = "Marco",
                    onDismiss = {},
                    isParcel = true
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("How was your parcel delivery with Marco?").assertIsDisplayed()
        composeRule.onNodeWithText("How was your meal from Leaked Merchant?").assertDoesNotExist()
    }

    @Test
    fun parcelRatesParser_fallsBackToLegacyForMotorcycleOnly_andKeepsSedanNullWhenMissing() {
        val legacyParcel = RateConfig(
            baseFare = 55.0,
            includedKm = 2.0,
            perKmRate = 12.0,
            minFee = 55.0,
            maxFee = 300.0,
            serviceFee = 15.0,
            serviceFeeType = "flat"
        )

        // Missing parcel.motorcycle and parcel.sedan
        val (motoFallback, missingSedan) = ParcelFeeCalculator.parseParcelRatesFromParcelMap(
            parcelMap = null,
            legacyParcel = legacyParcel
        )
        assertEquals(55.0, motoFallback.baseFare, 0.001)
        assertEquals(20.0, motoFallback.maxWeightKg, 0.001)
        assertNull(missingSedan)

        val ratesWithMissingSedan = DeliveryRates(
            parcel = legacyParcel,
            parcelMotorcycle = motoFallback,
            parcelSedan = null
        )
        assertEquals(55.0, ratesWithMissingSedan.getParcelMotorcycleRate().baseFare, 0.001)
        assertNull(ratesWithMissingSedan.parcelSedan)

        // Explicit parcel.motorcycle + parcel.sedan with percent service fee + overnight night rule
        val parcelMap = mapOf(
            "motorcycle" to mapOf(
                "enabled" to true,
                "baseFare" to 60.0,
                "includedKm" to 3.0,
                "perKm" to 15.0,
                "minFee" to 60.0,
                "maxFee" to 350.0,
                "maxWeightKg" to 25.0,
                "driverShare" to 0.85,
                "platformServiceFee" to mapOf(
                    "mode" to "percent",
                    "value" to 10.0,
                    "min" to 12.0,
                    "max" to 50.0
                ),
                "peak" to mapOf("enabled" to true, "fee" to 15.0, "startTime" to "11:00", "endTime" to "13:00"),
                "night" to mapOf("enabled" to true, "fee" to 20.0, "startTime" to "22:00", "endTime" to "05:00")
            ),
            "sedan" to mapOf(
                "enabled" to true,
                "baseFare" to 120.0,
                "includedKm" to 3.0,
                "perKm" to 25.0,
                "minFee" to 120.0,
                "maxFee" to 900.0,
                "maxWeightKg" to 100.0,
                "surcharge" to 10.0,
                "platformServiceFee" to mapOf("mode" to "flat", "value" to 30.0)
            )
        )
        val (moto, sedan) = ParcelFeeCalculator.parseParcelRatesFromParcelMap(parcelMap, legacyParcel)
        assertEquals(60.0, moto.baseFare, 0.001)
        assertEquals(25.0, moto.maxWeightKg, 0.001)
        assertEquals("percent", moto.serviceFeeType)
        assertEquals(12.0, moto.serviceFeeMin!!, 0.001)
        assertEquals(50.0, moto.serviceFeeMax!!, 0.001)
        assertEquals(0.85, moto.driverShare!!, 0.001)

        assertNotNull(sedan)
        assertEquals(120.0, sedan!!.baseFare, 0.001)
        assertEquals(100.0, sedan.maxWeightKg, 0.001)
        assertEquals(30.0, sedan.serviceFee, 0.001)

        // Peak at 12:00 Manila time (active) vs 15:00 (inactive); Night at 23:30 Manila (active overnight)
        val zone = ZoneId.of("Asia/Manila")
        val noonMs = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val afternoonMs = ZonedDateTime.of(2026, 5, 1, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        val midnightMs = ZonedDateTime.of(2026, 5, 1, 23, 30, 0, 0, zone).toInstant().toEpochMilli()

        // 5 km => extra 2km * 15 = 30 => 60 + 30 = 90 + peak(15) = 105; 10% serviceFee clamped [12..50] = 10.50 -> 12.00
        val quotePeak = ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = "motorcycle",
            rate = moto,
            distanceKm = 5.0,
            weightKg = 10.0,
            nowMillis = noonMs,
            timezone = "Asia/Manila"
        )
        assertEquals(105.0, quotePeak.deliveryFee, 0.001)
        assertEquals(15.0, quotePeak.peakFee, 0.001)
        assertEquals(0.0, quotePeak.nightFee, 0.001)
        assertEquals(12.0, quotePeak.serviceFee, 0.001)
        assertEquals(117.0, quotePeak.totalFee, 0.001)
        assertFalse(quotePeak.isWeightExceeded)

        // Afternoon: no peak, no night => delivery 90.0
        val quoteAfternoon = ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = "motorcycle",
            rate = moto,
            distanceKm = 5.0,
            weightKg = 26.0,
            nowMillis = afternoonMs,
            timezone = "Asia/Manila"
        )
        assertEquals(90.0, quoteAfternoon.deliveryFee, 0.001)
        assertTrue(quoteAfternoon.isWeightExceeded)

        // Overnight 23:30 => night active (+20) => delivery 110.0
        val quoteNight = ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = "motorcycle",
            rate = moto,
            distanceKm = 5.0,
            weightKg = 10.0,
            nowMillis = midnightMs,
            timezone = "Asia/Manila"
        )
        assertEquals(20.0, quoteNight.nightFee, 0.001)
        assertEquals(110.0, quoteNight.deliveryFee, 0.001)

        // Sedan quote
        val sedanQuote = ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = "sedan",
            rate = sedan,
            distanceKm = 5.0,
            weightKg = 80.0,
            nowMillis = afternoonMs,
            timezone = "Asia/Manila"
        )
        // base 120 + 2*25(50) + surcharge 10 = 180 delivery, 30 service => 210
        assertEquals(180.0, sedanQuote.deliveryFee, 0.001)
        assertEquals(30.0, sedanQuote.serviceFee, 0.001)
        assertEquals(210.0, sedanQuote.totalFee, 0.001)
        assertFalse(sedanQuote.isWeightExceeded)
    }

    @Test
    fun parcelBookingUi_showsSedanHelper_unavailableMessage_weightLimits_andRecalculatesFareOnVehicleChange() {
        val motoRate = ParcelVehicleRate(
            baseFare = 50.0,
            includedKm = 3.0,
            perKm = 14.0,
            serviceFee = 15.0,
            maxWeightKg = 20.0
        )
        val sedanRate = ParcelVehicleRate(
            baseFare = 120.0,
            includedKm = 3.0,
            perKm = 25.0,
            serviceFee = 30.0,
            maxWeightKg = 100.0
        )

        var selectedVehicle by mutableStateOf("motorcycle")
        var sedanAvailable by mutableStateOf(false)
        var weightKg by mutableStateOf(15.0)

        composeRule.setContent {
            val activeRate = if (selectedVehicle == "sedan" && sedanAvailable) sedanRate else motoRate
            val quote = ParcelFeeCalculator.calculateParcelQuote(
                vehicleType = selectedVehicle,
                rate = activeRate,
                distanceKm = 5.0,
                weightKg = weightKg
            )
            DelivioTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ParcelPackageDetailsCard(
                        packageSizes = listOf("Small / Pouch", "Medium / Box", "Large / Cargo"),
                        selectedSize = "Medium / Box",
                        onSelectSize = {},
                        weightKg = weightKg,
                        weightText = ParcelFeeCalculator.formatWeightLimitKg(weightKg),
                        onWeightTextChange = {},
                        onDecreaseWeight = {},
                        onIncreaseWeight = {},
                        maxWeightKg = activeRate.maxWeightKg,
                        selectedVehicle = selectedVehicle,
                        isWeightExceeded = quote.isWeightExceeded,
                        itemDescription = "Boxes",
                        onItemDescriptionChange = {},
                        isFragile = false,
                        onFragileChange = {},
                        isSedanAvailable = sedanAvailable,
                        isMotorcycleAvailable = true,
                        onSelectVehicle = { selectedVehicle = it }
                    )
                    ParcelFareBreakdownCard(
                        quote = quote,
                        feePayer = "recipient",
                        selectedPaymentMethod = "cod"
                    )
                    ParcelBottomBar(
                        totalFee = quote.totalFee,
                        isFormValid = !quote.isWeightExceeded && weightKg > 0.0,
                        missingReason = if (quote.isWeightExceeded) "Weight limit exceeded" else null,
                        onBookClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Helper text and unavailable Sedan note
        composeRule.onNodeWithText("Sedan: for 4-wheel and 8-wheel cars").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sedan is not available in your area yet").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("parcel_vehicle_sedan").assertIsNotEnabled()
        composeRule.onNodeWithText("Max 20kg for motorcycle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_parcel_order_button").performScrollTo().assertIsEnabled()

        // Motorcycle quote: 50 + 2*14 = 78 delivery + 15 service = 93
        composeRule.onNodeWithTag("parcel_total_fee_value").performScrollTo().assertTextEquals("₱93.00")
        composeRule.onNodeWithText("Cash on Delivery - pay ₱93.00 to the rider at drop-off").performScrollTo().assertIsDisplayed()

        // Exceed motorcycle max weight -> shows error and disables booking
        weightKg = 25.0
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weight exceeds 20kg max limit for motorcycle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_parcel_order_button").performScrollTo().assertIsNotEnabled()

        // Enable Sedan and switch to it -> 25kg is now valid (Max 100kg for sedan) and fare updates immediately
        sedanAvailable = true
        selectedVehicle = "sedan"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("parcel_sedan_unavailable_note").assertDoesNotExist()
        composeRule.onNodeWithTag("parcel_vehicle_sedan").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Max 100kg for sedan").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_parcel_order_button").performScrollTo().assertIsEnabled()
        // Sedan quote: 120 + 2*25 = 170 delivery + 30 service = 200
        composeRule.onNodeWithTag("parcel_total_fee_value").performScrollTo().assertTextEquals("₱200.00")
        composeRule.onNodeWithText("Cash on Delivery - pay ₱200.00 to the rider at drop-off").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun orderRepository_mapDocToOrder_setsParcelVehicleStrictlyFromDocVehicleType_andHidesMerchant() {
        val repo = OrderRepository(FirestoreService())

        val docWithMissingVehicleType = OrderDocument(
            id = "doc_parcel_1",
            orderNumber = "DLV-1234",
            serviceType = "parcel",
            merchantId = null,
            merchantName = "Some Stale Merchant",
            vehicleType = null, // Missing vehicle type must NEVER default to "4 Seater Car" or "Motorcycle" on rider card
            riderId = "rider_1",
            riderName = "Juan",
            riderPhone = "09170000000",
            riderVehicle = RiderVehicleInfo(vehicleType = "car4", brand = "Toyota", model = "Vios", plate = "TAX-999"),
            status = "Rider is on the way to sender",
            pickup = LocationPoint(
                label = "Sender: Alice",
                address = "Rizal Ave",
                lat = 9.7392,
                lng = 118.7353,
                contactName = "Alice",
                contactPhone = "09171111111"
            ),
            dropoff = LocationPoint(
                label = "Recipient: Bob",
                address = "Malvar St",
                lat = 9.7420,
                lng = 118.7400,
                contactName = "Bob",
                contactPhone = "09172222222"
            ),
            parcel = ParcelInfo(
                size = "Small / Pouch",
                weightKg = 2.0,
                description = "Keys",
                fragile = false,
                feePayer = "recipient"
            ),
            fees = OrderFees(deliveryFee = 60.0, serviceFee = 15.0, total = 75.0),
            paymentMethod = "cod",
            cashToCollect = 75.0,
            cashCollectAt = "recipient"
        )

        val mappedMissing = repo.mapDocToOrder(docWithMissingVehicleType)
        assertEquals(ServiceType.PARCEL, mappedMissing.serviceType)
        assertEquals("Parcel Service", mappedMissing.restaurant.name)
        assertEquals("", mappedMissing.restaurant.address)
        assertEquals("", mappedMissing.restaurant.phone)
        assertEquals(0.0, mappedMissing.restaurant.rating, 0.001)
        assertEquals("", mappedMissing.rider?.vehicle)
        assertEquals("TAX-999", mappedMissing.rider?.licensePlate)

        val docSedan = docWithMissingVehicleType.copy(vehicleType = "sedan")
        val mappedSedan = repo.mapDocToOrder(docSedan)
        assertEquals("Sedan", mappedSedan.rider?.vehicle)
        assertEquals("sedan", mappedSedan.vehicleType)
        assertEquals(75.0, mappedSedan.cashToCollect, 0.001)
        assertEquals("recipient", mappedSedan.cashCollectAt)
    }
}
