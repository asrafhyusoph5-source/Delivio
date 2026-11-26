package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.AirportDirectory
import com.example.data.model.CabinClass
import com.example.data.model.City
import com.example.data.model.FlightDateUtil
import com.example.data.model.FlightFormValidator
import com.example.data.model.FlightLeg
import com.example.data.model.FlightSearchUiState
import com.example.data.model.LoanOffer
import com.example.data.model.PlatformSettings
import com.example.data.model.TravelersCount
import com.example.data.model.TripType
import com.example.ui.screens.flight.FlightScreenStateful
import com.example.ui.screens.more.MoreServicesContent
import com.example.ui.theme.DelivioTheme
import com.example.util.ServiceAvailability
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class FlightAndMoreServicesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dayMillis = 86_400_000L

    @Test
    fun airportDirectory_containsExpectedPhilippineAndInternationalAirports_andSearchesCleanly() {
        val requiredPhCodes = listOf("PPS", "MNL", "CEB", "DVO", "ILO", "KLO", "TAG", "CRK")
        requiredPhCodes.forEach { code ->
            val found = AirportDirectory.findByCode(code)
            assertNotNull("Expected PH airport $code", found)
            assertTrue(found!!.isDomestic)
        }

        val intlResults = AirportDirectory.search("Singapore")
        assertTrue(intlResults.any { it.code == "SIN" && !it.isDomestic })

        val davaoSearch = AirportDirectory.search("DVO")
        assertTrue(davaoSearch.any { it.code == "DVO" && it.city == "Davao" })
    }

    @Test
    fun travelersCount_enforcesMaxNineAndInfantsUpToAdults() {
        var travelers = TravelersCount()
        assertEquals("1 Adult", travelers.formatSummary())

        // 1 infant is permitted for 1 adult, but a 2nd infant is blocked (infants <= adults)
        assertTrue(FlightFormValidator.canIncrementInfants(travelers))
        travelers = FlightFormValidator.incrementInfants(travelers)
        assertEquals(1, travelers.infants)
        assertFalse(FlightFormValidator.canIncrementInfants(travelers))
        travelers = FlightFormValidator.incrementInfants(travelers)
        assertEquals(1, travelers.infants)

        // Increment adults -> now a 2nd infant can be added
        travelers = FlightFormValidator.incrementAdults(travelers)
        assertTrue(FlightFormValidator.canIncrementInfants(travelers))
        travelers = FlightFormValidator.incrementInfants(travelers)
        assertEquals(2, travelers.adults)
        assertEquals(2, travelers.infants)
        assertEquals("2 Adults, 2 Infants", travelers.formatSummary())

        // Decreasing adults clamps infants down so infants <= adults
        travelers = FlightFormValidator.decrementAdults(travelers)
        assertEquals(1, travelers.adults)
        assertEquals(1, travelers.infants)

        // Cap total travelers at 9
        repeat(10) {
            travelers = FlightFormValidator.incrementChildren(travelers)
        }
        assertEquals(9, travelers.total)
        assertFalse(FlightFormValidator.canIncrementAdults(travelers))
        assertFalse(FlightFormValidator.canIncrementChildren(travelers))
    }

    @Test
    fun flightValidator_checksDatesAirportsAndMultiCityRules() {
        val today = FlightDateUtil.localTodayUtcMidnightMillis()
        val tomorrow = today + dayMillis
        val dayAfter = today + 2 * dayMillis
        val yesterday = today - dayMillis

        val pps = AirportDirectory.findByCode("PPS")!!
        val mnl = AirportDirectory.findByCode("MNL")!!
        val ceb = AirportDirectory.findByCode("CEB")!!

        // Missing date
        val missingDate = FlightSearchUiState(
            tripType = TripType.ONE_WAY,
            origin = pps,
            destination = mnl,
            departureDateMillis = null
        )
        assertNotNull(FlightFormValidator.validate(missingDate, today))

        // Past date rejected
        val pastDate = missingDate.copy(departureDateMillis = yesterday)
        assertEquals("Departure date cannot be in the past.", FlightFormValidator.validate(pastDate, today))

        // Same origin & destination rejected
        val sameAirport = missingDate.copy(destination = pps, departureDateMillis = today)
        assertEquals("Origin and destination airports cannot be the same.", FlightFormValidator.validate(sameAirport, today))

        // Round trip requires return > departure
        val invalidReturn = FlightSearchUiState(
            tripType = TripType.ROUND_TRIP,
            origin = pps,
            destination = mnl,
            departureDateMillis = tomorrow,
            returnDateMillis = tomorrow
        )
        assertEquals("Return date must be after departure date.", FlightFormValidator.validate(invalidReturn, today))

        val validRoundTrip = invalidReturn.copy(returnDateMillis = dayAfter)
        assertNull(FlightFormValidator.validate(validRoundTrip, today))

        // Multi City up to 5 legs valid; > 5 invalid
        val validFiveLegs = (1..5).map { idx ->
            FlightLeg(
                id = "leg_$idx",
                origin = if (idx % 2 == 1) pps else mnl,
                destination = if (idx % 2 == 1) mnl else ceb,
                departureDateMillis = tomorrow + idx * dayMillis
            )
        }
        val validMulti = FlightSearchUiState(
            tripType = TripType.MULTI_CITY,
            multiCityLegs = validFiveLegs
        )
        assertNull(FlightFormValidator.validate(validMulti, today))

        val sixLegs = validFiveLegs + FlightLeg("leg_6", ceb, pps, tomorrow + 6 * dayMillis)
        assertNotNull(FlightFormValidator.validate(validMulti.copy(multiCityLegs = sixLegs), today))
    }

    @Test
    fun availability_allowsFlightAndLoansWithoutDeliveryRatesWhenSwitchesOn() {
        val city = City(id = "puerto-princesa", name = "Puerto Princesa", status = "active", active = true)
        val platform = PlatformSettings(services = mapOf("flight" to true, "loans" to true))

        val flightAvail = ServiceAvailability.availability(
            city = city,
            service = "flight",
            platformSettings = platform,
            deliveryRates = null,
            logResult = false
        )
        assertTrue(flightAvail.ok)

        val loansCityOff = ServiceAvailability.availability(
            city = city.copy(services = mapOf("loans" to false)),
            service = "loans",
            platformSettings = platform,
            deliveryRates = null,
            logResult = false
        )
        assertFalse(loansCityOff.ok)
        assertEquals("Delivio Loans is switched off in Puerto Princesa", loansCityOff.reason)
    }

    @Test
    fun moreServices_showsAllEnabledTiles_gatesLoansOnOffer_andShowsEmptyState() {
        var showFlight by mutableStateOf(true)
        var showLoans by mutableStateOf(true)
        var showJobFair by mutableStateOf(true)

        composeRule.setContent {
            DelivioTheme {
                MoreServicesContent(
                    showFlight = showFlight,
                    showLoans = showLoans,
                    showJobFair = showJobFair,
                    loanOffer = LoanOffer(uid = "u1", enabled = true, maxAmount = 15000.0),
                    onBack = {},
                    onTileClick = {}
                )
            }
        }

        composeRule.onNodeWithText("More services").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_flight").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_loans").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_jobfair").assertIsDisplayed()

        // Turn off Loans -> only Flight & Job Fair stay visible
        showLoans = false
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("more_tile_loans").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("more_tile_flight").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_jobfair").assertIsDisplayed()

        // Turn off all services -> shows "Nothing here yet"
        showFlight = false
        showJobFair = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_services_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }

    @Test
    fun flightScreen_supportsTripTypesSwapMultiCityTravelersAndComingSoonFlow() {
        val today = FlightDateUtil.localTodayUtcMidnightMillis()
        val tomorrow = today + dayMillis
        val returnDay = today + 4 * dayMillis
        val recents = mutableListOf<String>()

        composeRule.setContent {
            DelivioTheme {
                var state by remember {
                    mutableStateOf(
                        FlightSearchUiState(
                            tripType = TripType.ROUND_TRIP,
                            departureDateMillis = tomorrow,
                            returnDateMillis = returnDay
                        )
                    )
                }
                FlightScreenStateful(
                    uiState = state,
                    isServiceEnabled = true,
                    onStateChange = { state = it },
                    onSaveRecentAirport = { code -> recents.add(code) },
                    onBack = {}
                )
            }
        }

        // Verify One Way hides Return field, Round Trip shows Return field
        composeRule.onNodeWithTag("flight_return_date_field").assertIsDisplayed()
        composeRule.onNodeWithTag("flight_trip_one_way").performClick()
        assertTrue(composeRule.onAllNodesWithTag("flight_return_date_field").fetchSemanticsNodes().isEmpty())

        // Verify default Travelers "1 Adult" and origin/dest swap
        composeRule.onNodeWithText("1 Adult").assertIsDisplayed()
        composeRule.onNodeWithText("PPS • Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithText("MNL • Manila").assertIsDisplayed()

        composeRule.onNodeWithTag("flight_swap_button").performClick()
        composeRule.onNodeWithText("MNL • Manila").assertIsDisplayed()
        composeRule.onNodeWithText("PPS • Puerto Princesa").assertIsDisplayed()

        // Open Destination sheet, confirm blocked origin PPS cannot be picked, search CEB & select
        composeRule.onNodeWithTag("flight_destination_field").performClick()
        composeRule.onNodeWithTag("airport_MNL").assertIsNotEnabled()
        composeRule.onNodeWithTag("airport_search_input").performTextInput("Cebu")
        composeRule.onNodeWithTag("airport_CEB").performClick()
        assertTrue(recents.contains("CEB"))

        // Open Travelers sheet & verify steppers
        composeRule.onNodeWithTag("flight_travelers_field").performScrollTo().performClick()
        composeRule.onNodeWithTag("travelers_adults_plus").performClick()
        composeRule.onNodeWithTag("travelers_infants_plus").performClick()
        composeRule.onNodeWithTag("travelers_done_button").performClick()
        composeRule.onNodeWithText("2 Adults, 1 Infant").assertIsDisplayed()

        // Switch Cabin Class to Business
        composeRule.onNodeWithTag("flight_class_business").performScrollTo().performClick()

        // Submit Search -> opens Coming soon screen with summary & 4 rows, Back returns to form
        composeRule.onNodeWithTag("flight_search_button").performScrollTo().performClick()
        composeRule.onNodeWithText("Flight booking is coming soon. Payments and ticketing are not available yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Payment").assertIsDisplayed()
        composeRule.onNodeWithText("Passenger details").assertIsDisplayed()
        composeRule.onNodeWithText("Seat selection").assertIsDisplayed()
        composeRule.onNodeWithText("Baggage").assertIsDisplayed()

        composeRule.onNodeWithTag("flight_coming_soon_back_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("flight_search_button").performScrollTo().assertIsDisplayed()

        // Multi City: starts with 2 legs, can add up to 5 max, can remove
        composeRule.onNodeWithTag("flight_trip_multi_city").performScrollTo().performClick()
        composeRule.onNodeWithTag("flight_leg_origin_0").assertIsDisplayed()
        composeRule.onNodeWithTag("flight_leg_origin_1").assertIsDisplayed()
        composeRule.onNodeWithTag("flight_add_leg_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("flight_add_leg_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("flight_add_leg_button").performScrollTo().performClick()
        // At 5 legs, "Add another flight" should disappear
        assertTrue(composeRule.onAllNodesWithTag("flight_add_leg_button").fetchSemanticsNodes().isEmpty())

        // Removing leg 4 brings button back
        composeRule.onNodeWithTag("flight_remove_leg_4").performScrollTo().performClick()
        composeRule.onNodeWithTag("flight_add_leg_button").performScrollTo().assertIsDisplayed()
    }
}
