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

    @Test
    fun taxiDriverCard_motorcycleBookingShowsCorrectTypeAndVehicleNameModelFallback() {
        val motoOrder = Order(
            id = "taxi_moto_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.ON_THE_WAY,
            rawStatus = "Driver is on the way",
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            discount = 0.0,
            total = 65.0,
            createdAt = System.currentTimeMillis(),
            serviceType = ServiceType.TAXI,
            vehicleType = " motorcycle ",
            riderVehicle = null,
            rider = com.example.data.model.Rider(
                id = "rider_1",
                name = "Juan Dela Cruz",
                phone = "+63 917 000 1111",
                vehicle = "Motorcycle",
                licensePlate = "MC-1024",
                rating = 4.9
            )
        )

        composeRule.setContent {
            DelivioTheme {
                TaxiDriverCard(order = motoOrder, onCall = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Type: Motorcycle - 1 seat").assertIsDisplayed()
        composeRule.onNodeWithText("Type: 4 Seater Car").assertDoesNotExist()
        composeRule.onNodeWithText("Model: Motorcycle").assertIsDisplayed()
        composeRule.onNodeWithText("MC-1024").assertIsDisplayed()
    }

    @Test
    fun taxiDriverCard_showsDriverBrandOrModelAndVehicleTypeMappings() {
        assertEquals("Motorcycle - 1 seat", com.example.ui.components.formatTaxiVehicleType("motorcycle"))
        assertEquals("Motorcycle - 1 seat", com.example.ui.components.formatTaxiVehicleType(" MOTORCYCLE "))
        assertEquals("Tricycle - 3 seats", com.example.ui.components.formatTaxiVehicleType("tricycle"))
        assertEquals("4-seater car", com.example.ui.components.formatTaxiVehicleType("car4"))
        assertEquals("8-seater car", com.example.ui.components.formatTaxiVehicleType("car8"))
        assertEquals("Van", com.example.ui.components.formatTaxiVehicleType("van"))
        assertEquals("", com.example.ui.components.formatTaxiVehicleType(null))

        val carOrder = Order(
            id = "taxi_car_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.ON_THE_WAY,
            rawStatus = "On trip",
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            discount = 0.0,
            total = 220.0,
            createdAt = System.currentTimeMillis(),
            serviceType = ServiceType.TAXI,
            vehicleType = "CAR4",
            riderVehicle = com.example.data.model.RiderVehicleInfo(
                brand = "Toyota",
                model = "Vios",
                plate = "ABC-4321",
                vehicleType = "car4"
            ),
            rider = com.example.data.model.Rider(
                id = "rider_2",
                name = "Marco Santos",
                phone = "+63 917 222 3333",
                vehicle = "Toyota Vios",
                licensePlate = "ABC-4321",
                rating = 4.8
            )
        )

        composeRule.setContent {
            DelivioTheme {
                TaxiDriverCard(order = carOrder, onCall = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Model: Toyota Vios").assertIsDisplayed()
        composeRule.onNodeWithText("Type: 4-seater car").assertIsDisplayed()
        composeRule.onNodeWithText("ABC-4321").assertIsDisplayed()
    }

    @Test
    fun stepperTimeline_onTripWithArrivedAtDropoffAt_showsSubtitleAndKeepsFiveSteps() {
        val arrivedOrder = Order(
            id = "taxi_arrived_dropoff_1",
            restaurant = MockData.restaurants.first(),
            items = emptyList(),
            status = OrderStatus.ON_THE_WAY,
            rawStatus = "On trip",
            arrivedAtDropoffAt = System.currentTimeMillis(),
            subtotal = 0.0,
            deliveryFee = 0.0,
            serviceFee = 0.0,
            discount = 0.0,
            total = 140.0,
            createdAt = System.currentTimeMillis() - 600_000L,
            serviceType = ServiceType.TAXI,
            vehicleType = "motorcycle",
            rider = com.example.data.model.Rider(
                id = "rider_3",
                name = "Leo Reyes",
                phone = "+63 917 444 5555",
                vehicle = "Honda Click",
                licensePlate = "PAL-901",
                rating = 5.0
            )
        )

        composeRule.setContent {
            DelivioTheme {
                com.example.ui.components.StepperTimeline(order = arrivedOrder)
            }
        }
        composeRule.waitForIdle()

        // All 5 steps stay intact
        composeRule.onNodeWithText("Looking for a driver...").assertIsDisplayed()
        composeRule.onNodeWithText("Driver is on the way").assertIsDisplayed()
        composeRule.onNodeWithText("Driver arrived").assertIsDisplayed()
        composeRule.onNodeWithText("On trip").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()

        // Subtitle shown under "On trip"
        composeRule.onNodeWithText("Your driver has arrived at the drop-off point").assertIsDisplayed()
    }

    @Test
    fun nominatimReverseGeocode_preservesResidentialStreetWithBarangayAndCity() {
        val json = org.json.JSONObject(
            """
            {
              "display_name": "Manalo Street, Barangay Bancao-Bancao, Puerto Princesa, Palawan, Philippines",
              "address": {
                "residential": "Manalo Street",
                "suburb": "Barangay Bancao-Bancao",
                "city": "Puerto Princesa",
                "state": "Palawan"
              }
            }
            """.trimIndent()
        )
        val formatted = com.example.data.api.NominatimService.formatReverseGeocodeJson(json)
        assertEquals("Manalo Street, Barangay Bancao-Bancao, Puerto Princesa, Palawan", formatted)
    }
}
