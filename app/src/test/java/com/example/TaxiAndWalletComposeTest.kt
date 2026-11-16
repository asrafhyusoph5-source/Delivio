package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.datasource.MockData
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.ServiceType
import com.example.data.model.WalletActivityCategory
import com.example.data.model.WalletActivityItem
import com.example.ui.components.TaxiDriverCard
import com.example.ui.screens.wallet.WalletActivityRow
import com.example.ui.theme.DelivioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaxiAndWalletComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun taxiDriverCard_doesNotShowPromptBeforeTimeout() {
        val freshOrder = Order(
            id = "taxi_fresh_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.PLACED,
            rawStatus = "Looking for a driver...",
            deliveryAddress = null,
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            tip = 0.0,
            discount = 0.0,
            total = 120.0,
            createdAt = System.currentTimeMillis(),
            rider = null,
            serviceType = ServiceType.TAXI,
            vehicleType = "Tricycle"
        )

        composeRule.setContent {
            DelivioTheme {
                TaxiDriverCard(
                    order = freshOrder,
                    onCall = {},
                    driverWaitTimeoutMs = 90_000L
                )
            }
        }

        composeRule.onNodeWithText("Looking for a driver...").assertIsDisplayed()
        composeRule.onNodeWithText("No driver has accepted yet").assertDoesNotExist()
    }

    @Test
    fun taxiDriverCard_after90Seconds_showsCardWithKeepWaitingAndCancelRide() {
        var keepWaitingCalls = 0
        var cancelCalls = 0

        val timedOutOrder = Order(
            id = "taxi_timeout_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.PLACED,
            rawStatus = "Looking for a driver...",
            deliveryAddress = null,
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            tip = 0.0,
            discount = 0.0,
            total = 120.0,
            createdAt = System.currentTimeMillis() - 91_000L,
            rider = null,
            serviceType = ServiceType.TAXI,
            vehicleType = "Tricycle"
        )

        composeRule.setContent {
            DelivioTheme {
                TaxiDriverCard(
                    order = timedOutOrder,
                    onCall = {},
                    driverWaitTimeoutMs = 90_000L,
                    onKeepWaiting = { keepWaitingCalls++ },
                    onCancelRide = { cancelCalls++ }
                )
            }
        }

        composeRule.onNodeWithTag("taxi_no_driver_card").assertIsDisplayed()
        composeRule.onNodeWithText("No driver has accepted yet").assertIsDisplayed()
        composeRule.onNodeWithText("Keep waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel ride").assertIsDisplayed()

        // Tap Cancel ride -> invokes existing cancel callback
        composeRule.onNodeWithTag("btn_taxi_cancel_ride").performClick()
        assertEquals(1, cancelCalls)

        // Tap Keep waiting -> hides prompt card and restarts wait
        composeRule.onNodeWithTag("btn_taxi_keep_waiting").performClick()
        assertEquals(1, keepWaitingCalls)
        composeRule.onNodeWithText("No driver has accepted yet").assertDoesNotExist()
    }

    @Test
    fun walletActivityRow_rendersItemWithoutTimeSafely() {
        val noTimeItem = WalletActivityItem(
            id = "old_1",
            category = WalletActivityCategory.TOP_UPS,
            title = "Top-up by Delivio",
            timestamp = null,
            signedAmount = 150.0,
            note = "Legacy adjustment"
        )

        composeRule.setContent {
            DelivioTheme {
                WalletActivityRow(item = noTimeItem)
            }
        }

        composeRule.onNodeWithText("Top-up by Delivio").assertIsDisplayed()
        composeRule.onNodeWithText("Legacy adjustment").assertIsDisplayed()
        composeRule.onNodeWithText("+₱150.00").assertIsDisplayed()
    }

    @Test
    fun taxiRouteSummaryCard_showsPickUpDropOffAndShortStopAddressTitle() {
        val taxiOrder = Order(
            id = "taxi_route_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.PLACED,
            rawStatus = "Looking for a driver...",
            pickupAddress = com.example.data.model.Address(
                id = "pickup",
                title = "Pick Up",
                fullAddress = "Rizal Avenue, Barangay San Miguel, Puerto Princesa, Palawan",
                lat = 9.7392,
                lng = 118.7353
            ),
            deliveryAddress = com.example.data.model.Address(
                id = "dropoff",
                title = "Drop off",
                fullAddress = "San Pedro National Highway, Barangay San Pedro, Puerto Princesa",
                lat = 9.7510,
                lng = 118.7480
            ),
            stops = listOf(
                com.example.data.model.TaxiStop(
                    number = 3,
                    label = "Next Stop",
                    address = "Malvar Street, Barangay Tanglaw, Puerto Princesa, Palawan",
                    lat = 9.7440,
                    lng = 118.7400
                )
            ),
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            tip = 0.0,
            discount = 0.0,
            total = 150.0,
            createdAt = System.currentTimeMillis(),
            rider = null,
            serviceType = ServiceType.TAXI,
            vehicleType = "Tricycle"
        )

        composeRule.setContent {
            DelivioTheme {
                com.example.ui.components.TaxiRouteSummaryCard(order = taxiOrder)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Route & Stops").assertIsDisplayed()
        composeRule.onNodeWithText("Pick up").assertIsDisplayed()
        composeRule.onNodeWithText("Rizal Avenue, Barangay San Miguel, Puerto Princesa, Palawan").assertIsDisplayed()
        composeRule.onNodeWithText("Pick Up: Pick Up").assertDoesNotExist()

        composeRule.onNodeWithText("Malvar Street, Barangay Tanglaw").assertIsDisplayed()
        composeRule.onNodeWithText("Malvar Street, Barangay Tanglaw, Puerto Princesa, Palawan").assertIsDisplayed()
        composeRule.onNodeWithText("Stop 3: Next Stop").assertDoesNotExist()

        composeRule.onNodeWithText("Drop off").assertIsDisplayed()
        composeRule.onNodeWithText("San Pedro National Highway, Barangay San Pedro, Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("Drop off: Drop off").assertDoesNotExist()
    }

    @Test
    fun trackingMapLegend_showsTaxiLegendForTaxiAndStoreLegendForDelivery() {
        composeRule.setContent {
            DelivioTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.ui.components.TrackingMapLegend(
                        isTaxiOrder = true,
                        isFallbackRoute = false
                    )
                    com.example.ui.components.TrackingMapLegend(
                        isTaxiOrder = false,
                        isFallbackRoute = false
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("📍 Pick-up").assertIsDisplayed()
        composeRule.onNodeWithText("🚕 Driver").assertIsDisplayed()
        composeRule.onNodeWithText("🏁 Drop-off").assertIsDisplayed()

        composeRule.onNodeWithText("📍 Store").assertIsDisplayed()
        composeRule.onNodeWithText("🛵 Rider").assertIsDisplayed()
        composeRule.onNodeWithText("🏁 You").assertIsDisplayed()
    }
}
