package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.Address
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
import com.example.data.model.Restaurant
import com.example.data.model.Rider
import com.example.data.model.ServiceType
import com.example.data.repository.TrackerDisplayState
import com.example.ui.components.FloatingOrderTracker
import com.example.ui.components.OrderItemsSummary
import com.example.ui.components.RatingCardContent
import com.example.ui.components.RiderSection
import com.example.ui.components.StepperTimeline
import com.example.ui.components.TrackingMapLegend
import com.example.ui.components.resolvePabiliHeaderTitle
import com.example.ui.components.resolvePabiliStepIndexOrNull
import com.example.ui.screens.home.PabiliBottomBar
import com.example.ui.screens.home.PabiliLiveFeeBreakdownCard
import com.example.ui.theme.DelivioTheme
import com.example.util.DelivioStatusConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PabiliScreenAndTrackerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun samplePabiliOrder(
        rawStatus: String = "Placed Order",
        rider: Rider? = null,
        riderLat: Double? = null,
        riderLng: Double? = null,
        paidOnMerchant: Double? = null,
        budgetLimit: Double? = 500.0
    ): Order {
        return Order(
            id = "pabili_order_1",
            restaurant = Restaurant(
                id = "service_partner_pabili",
                name = "Mercury Drug Leak Shop",
                category = "Errand",
                rating = 4.9,
                reviewCount = 99,
                ratingSum = 490.0,
                ratingCount = 99,
                etaMinutes = 20,
                deliveryFee = 55.0,
                imageUrl = "https://example.com/shop.jpg",
                address = "123 Leak Shop Street",
                phone = "09171234567"
            ),
            items = emptyList(),
            status = OrderStatus.PLACED,
            rawStatus = rawStatus,
            createdAt = 1_700_000_000_000L,
            subtotal = 0.0,
            deliveryFee = 55.0,
            serviceFee = 20.0,
            discount = 0.0,
            total = 75.0,
            deliveryAddress = Address(
                id = "addr_dest",
                title = "Home",
                fullAddress = "456 Rizal Ave, Puerto Princesa",
                lat = 9.7420,
                lng = 118.7400
            ),
            pickupAddress = Address(
                id = "addr_vendor",
                title = "Vendor",
                fullAddress = "Rizal Ave Vendor Pin, Puerto Princesa",
                lat = 9.7392,
                lng = 118.7353
            ),
            rider = rider,
            riderId = rider?.id,
            riderLat = riderLat,
            riderLng = riderLng,
            serviceType = ServiceType.PABILI,
            pabiliDetails = PabiliDetails(
                storeName = "Secret Pharmacy Store",
                storeAddress = "Rizal Ave Vendor Pin, Puerto Princesa",
                storeLat = 9.7392f,
                storeLng = 118.7353f,
                items = listOf(
                    PabiliItem(name = "Paracetamol 500mg", quantity = 2, estimatedPrice = 50.0)
                ),
                recipientName = "Maria Santos",
                recipientPhone = "09180001111",
                deliveryAddress = "456 Rizal Ave, Puerto Princesa",
                deliveryLat = 9.7420f,
                deliveryLng = 118.7400f,
                riderNotes = "Ask for receipt"
            ),
            budgetLimit = budgetLimit,
            paidOnMerchant = paidOnMerchant
        )
    }

    @Test
    fun pabiliStatusMapping_matchesOnlyExactTrimmedLowercasedStrings() {
        assertEquals(0, resolvePabiliStepIndexOrNull("Placed Order"))
        assertEquals(0, resolvePabiliStepIndexOrNull("  placed order "))
        assertEquals(1, resolvePabiliStepIndexOrNull("Rider is on the way to vendor"))
        assertEquals(2, resolvePabiliStepIndexOrNull(" arrived at vendor "))
        assertEquals(3, resolvePabiliStepIndexOrNull("RIDER IS ON THE WAY TO RECIPIENT"))
        assertEquals(4, resolvePabiliStepIndexOrNull("Arrived at recipient"))
        assertEquals(5, resolvePabiliStepIndexOrNull("Delivered"))

        // Partial / unrelated strings must NOT match
        assertNull(resolvePabiliStepIndexOrNull("on the way"))
        assertNull(resolvePabiliStepIndexOrNull("Rider is on the way"))
        assertNull(resolvePabiliStepIndexOrNull("Arrived"))
        assertNull(resolvePabiliStepIndexOrNull("On trip"))
        assertNull(resolvePabiliStepIndexOrNull(""))
        assertNull(resolvePabiliStepIndexOrNull(null))
    }

    @Test
    fun pabiliHeaderTitles_followExactStatusProgression() {
        assertEquals("Placed Order", resolvePabiliHeaderTitle(0))
        assertEquals("Rider is on the way to the vendor", resolvePabiliHeaderTitle(1))
        assertEquals("Rider is at the vendor", resolvePabiliHeaderTitle(2))
        assertEquals("Rider is on the way to you", resolvePabiliHeaderTitle(3))
        assertEquals("Your rider has arrived", resolvePabiliHeaderTitle(4))
        assertEquals("Delivered", resolvePabiliHeaderTitle(5))
    }

    @Test
    fun pabiliTimeline_ignoresRiderPresenceAndLocation_andKeepsLastKnownOnUnknownStatus() {
        val assignedRider = Rider(
            id = "r_1",
            name = "Carlos Mendoza",
            phone = "09175551234",
            vehicle = "Honda Click 125",
            licensePlate = "ABC-789",
            rating = 4.9
        )
        var currentOrder by mutableStateOf(
            samplePabiliOrder(
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

        // Exact 6 Pabili steps rendered
        DelivioStatusConstants.PABILI_STATUS_FLOW.forEach { step ->
            composeRule.onNodeWithText(step).assertIsDisplayed()
        }
        // Still at step 0 even though rider & GPS coordinates are present
        composeRule.onNodeWithText("Finding a rider for your errand").assertIsDisplayed()

        // Step 1: Rider is on the way to vendor
        currentOrder = currentOrder.copy(rawStatus = "Rider is on the way to vendor")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider is heading to the vendor").assertIsDisplayed()

        // Step 2: Arrived at vendor
        currentOrder = currentOrder.copy(rawStatus = "Arrived at vendor")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider is buying your items").assertIsDisplayed()

        // Unknown status must keep last known step (step 2: "Rider is buying your items")
        currentOrder = currentOrder.copy(rawStatus = "Some Unknown Backend Status")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rider is buying your items").assertIsDisplayed()

        // Step 3: Rider is on the way to recipient
        currentOrder = currentOrder.copy(rawStatus = "Rider is on the way to recipient")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Heading to your destination").assertIsDisplayed()

        // Step 4: Arrived at recipient
        currentOrder = currentOrder.copy(rawStatus = "Arrived at recipient")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your rider has arrived").assertIsDisplayed()

        // Step 5: Delivered
        currentOrder = currentOrder.copy(rawStatus = "Delivered", status = OrderStatus.DELIVERED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delivered safely!").assertIsDisplayed()
    }

    @Test
    fun pabiliTracking_hidesMerchantDetails_andShowsRiderCardAndVendorLegend() {
        val rider = Rider(
            id = "rider_pabili_1",
            name = "Marco Villanueva",
            phone = "09178889999",
            vehicle = "Yamaha NMAX",
            licensePlate = "XYZ-123",
            rating = 4.95
        )
        val pabiliOrder = samplePabiliOrder(
            rawStatus = "Rider is on the way to vendor",
            rider = rider,
            paidOnMerchant = null
        )

        composeRule.setContent {
            DelivioTheme {
                Column {
                    TrackingMapLegend(
                        isTaxiOrder = false,
                        isPabiliOrder = true,
                        isFallbackRoute = false
                    )
                    RiderSection(
                        rider = rider,
                        isCollapsed = false,
                        onToggleCollapse = {},
                        onCall = {},
                        onOpenChat = {},
                        etaText = "ETA: 15-25 mins",
                        alwaysShowVehicleDetails = true
                    )
                    OrderItemsSummary(order = pabiliOrder)
                }
            }
        }
        composeRule.waitForIdle()

        // Legend shows Vendor -> Rider -> You
        composeRule.onNodeWithText("📍 Vendor").assertIsDisplayed()
        composeRule.onNodeWithText("🛵 Rider").assertIsDisplayed()
        composeRule.onNodeWithText("🏁 You").assertIsDisplayed()
        composeRule.onNodeWithText("📍 Store").assertDoesNotExist()

        // Rider card shows name, rating, vehicle, plate, ETA, Call, Chat
        composeRule.onNodeWithText("Marco Villanueva").assertIsDisplayed()
        composeRule.onNodeWithText("4.95").assertIsDisplayed()
        composeRule.onNodeWithText("Yamaha NMAX • XYZ-123").assertIsDisplayed()
        composeRule.onNodeWithText("ETA: 15-25 mins").assertIsDisplayed()
        composeRule.onNodeWithTag("call_rider_button").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_rider_button").assertIsDisplayed()

        // No merchant / shop details anywhere
        composeRule.onNodeWithText("Store: Secret Pharmacy Store").assertDoesNotExist()
        composeRule.onNodeWithText("Mercury Drug Leak Shop").assertDoesNotExist()
        composeRule.onNodeWithText("123 Leak Shop Street").assertDoesNotExist()
        composeRule.onNodeWithTag("call_merchant_button").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_merchant_button").assertDoesNotExist()
    }

    @Test
    fun pabiliOrderSummary_beforeAndAfterPickup_showsFeesAndPaidOnMerchantTotals() {
        var pabiliOrder by mutableStateOf(
            samplePabiliOrder(
                rawStatus = "Rider is on the way to vendor",
                paidOnMerchant = null
            )
        )

        composeRule.setContent {
            DelivioTheme {
                OrderItemsSummary(order = pabiliOrder)
            }
        }
        composeRule.waitForIdle()

        // Before pickup: delivery fee + service fee + total fees + COD fees note
        composeRule.onNodeWithText("Delivery fee").assertIsDisplayed()
        composeRule.onNodeWithText("₱55.00").assertIsDisplayed()
        composeRule.onNodeWithText("Service fee").assertIsDisplayed()
        composeRule.onNodeWithText("₱20.00").assertIsDisplayed()
        composeRule.onNodeWithText("Total fees").assertIsDisplayed()
        composeRule.onNodeWithText("₱75.00").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Cash on Delivery - pay ₱75.00 in fees, plus the receipt total of your items, to the rider"
        ).assertIsDisplayed()

        // After pickup: paidOnMerchant = 350.0 -> total to pay = 75 + 350 = 425.00
        pabiliOrder = pabiliOrder.copy(
            rawStatus = "Rider is on the way to recipient",
            paidOnMerchant = 350.0
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Items paid at the store: ₱350.00").assertIsDisplayed()
        composeRule.onNodeWithText("Total to pay your rider: ₱425.00").assertIsDisplayed()
        composeRule.onNodeWithText("Cash on Delivery - pay ₱425.00 to the rider").assertIsDisplayed()
    }

    @Test
    fun pabiliLiveFeeBreakdown_andBottomBar_showOnlyFeesAndBudgetLimitNote() {
        composeRule.setContent {
            DelivioTheme {
                Column {
                    PabiliLiveFeeBreakdownCard(
                        deliveryFee = 55.0,
                        serviceFee = 20.0,
                        budgetLimit = 500.0
                    )
                    PabiliBottomBar(
                        totalFees = 75.0,
                        isFormValid = true,
                        missingFields = emptyList(),
                        isBooking = false,
                        onBookClick = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Removed rows
        composeRule.onNodeWithText("Distance").assertDoesNotExist()
        composeRule.onNodeWithText("Estimated items total").assertDoesNotExist()
        composeRule.onNodeWithText("Estimated Total").assertDoesNotExist()
        composeRule.onNodeWithText("Estimated total to pay").assertDoesNotExist()

        // Required breakdown rows
        composeRule.onNodeWithText("Delivery fee").assertIsDisplayed()
        composeRule.onNodeWithText("₱55.00").assertIsDisplayed()
        composeRule.onNodeWithText("Service fee").assertIsDisplayed()
        composeRule.onNodeWithText("₱20.00").assertIsDisplayed()

        // Budget limit note & COD line
        composeRule.onNodeWithText(
            "Your items are paid at the store by your rider and charged at the receipt price (your budget limit is ₱500)."
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Cash on Delivery - pay ₱75.00 in fees, plus the receipt total of your items, to the rider"
        ).assertIsDisplayed()

        // Bottom bar button
        composeRule.onNodeWithText("Place Pabili Order - ₱75.00").assertIsDisplayed()
    }

    @Test
    fun pabiliRatingCard_doesNotShowMerchantNameOrMealPrompt() {
        composeRule.setContent {
            DelivioTheme {
                RatingCardContent(
                    restaurantName = "Leak Shop Name",
                    riderName = "Marco",
                    onDismiss = {},
                    isPabili = true
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("How was your Pabili errand with Marco?").assertIsDisplayed()
        composeRule.onNodeWithText("How was your meal from Leak Shop Name?").assertDoesNotExist()
    }

    @Test
    fun delivioStatusConstants_isRiderOnTheWay_pabiliUsesTrimmedExactMatchOnly() {
        assertTrue(DelivioStatusConstants.isRiderOnTheWay("pabili", "Rider is on the way to vendor"))
        assertTrue(DelivioStatusConstants.isRiderOnTheWay(" PABILI ", "  rider is on the way to recipient  "))
        assertFalse(DelivioStatusConstants.isRiderOnTheWay("pabili", "on the way"))
        assertFalse(DelivioStatusConstants.isRiderOnTheWay("pabili", "Rider is on the way"))
        assertFalse(DelivioStatusConstants.isRiderOnTheWay("pabili", "Arrived at vendor"))
    }

    @Test
    fun pabiliOrderSummary_ignoresUninitializedZeroPaidOnMerchant() {
        val pabiliOrder = samplePabiliOrder(
            rawStatus = "Rider is on the way to recipient",
            paidOnMerchant = 0.0
        )

        composeRule.setContent {
            DelivioTheme {
                OrderItemsSummary(order = pabiliOrder)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Items paid at the store: ₱0.00").assertDoesNotExist()
        composeRule.onNodeWithText("Total fees").assertIsDisplayed()
        composeRule.onNodeWithText("₱75.00").assertIsDisplayed()
    }

    @Test
    fun floatingOrderTracker_pabiliKeepsLastKnownStepAcrossCollapseAndExpand_andHidesMerchantUi() {
        val rider = Rider(
            id = "rider_pabili_2",
            name = "Marco Villanueva",
            phone = "09178889999",
            vehicle = "Yamaha NMAX",
            licensePlate = "XYZ-123",
            rating = 4.95
        )
        var currentOrder by mutableStateOf(
            samplePabiliOrder(
                rawStatus = "Arrived at vendor",
                rider = rider,
                paidOnMerchant = 350.0
            ).copy(
                editedAt = 1_700_000_001_000L,
                editNote = "Should not show for Pabili"
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

        // Collapsed shows step 2 header ("Rider is at the vendor") and hides shop-updated badge
        composeRule.onNodeWithText("Rider is at the vendor").assertIsDisplayed()
        composeRule.onNodeWithText("⚠️ Updated by shop").assertDoesNotExist()

        // Backend emits unknown status while still collapsed, then user expands
        currentOrder = currentOrder.copy(rawStatus = "Unknown Transient Status")
        composeRule.waitForIdle()
        displayState = TrackerDisplayState.EXPANDED
        composeRule.waitForIdle()

        // Expanded sheet retains step 2 ("Rider is at the vendor"), hides merchant & shop-updated banner,
        // and still shows rider card, Vendor -> Rider -> You legend, and paidOnMerchant totals.
        composeRule.onNodeWithTag("tracker_header_title").assertIsDisplayed()
        composeRule.onNodeWithText("Rider is at the vendor").assertIsDisplayed()
        composeRule.onNodeWithText("⚠️ Your order was updated by the shop").assertDoesNotExist()
        composeRule.onNodeWithText("Mercury Drug Leak Shop").assertDoesNotExist()
        composeRule.onNodeWithTag("call_merchant_button").assertDoesNotExist()
        composeRule.onNodeWithText("📍 Vendor").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Marco Villanueva").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Items paid at the store: ₱350.00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Total to pay your rider: ₱425.00").performScrollTo().assertIsDisplayed()
    }
}
