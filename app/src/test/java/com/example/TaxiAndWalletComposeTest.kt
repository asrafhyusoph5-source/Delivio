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
}
