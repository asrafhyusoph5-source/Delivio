package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.AppDesignerServiceConfig
import com.example.data.model.AppDesignerSettings
import com.example.data.model.City
import com.example.data.model.LoanOffer
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import com.example.ui.components.DelivioIcon
import com.example.ui.screens.more.MoreServicesContent
import com.example.ui.screens.more.buildMoreServiceTile
import com.example.ui.theme.DelivioTheme
import com.example.util.AppDesignerParser
import com.example.util.BranchGroupingUtil
import com.example.util.ServiceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppDesignerServicesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parseServices_parsesAllEightServicesFromMap_andDefaultsMissingFields() {
        val rawMap = mapOf(
            "food" to mapOf(
                "label" to "Eats",
                "iconUrl" to "https://cdn.example.com/eats.png",
                "visible" to true,
                "placement" to "home",
                "order" to 2
            ),
            "convenience" to mapOf(
                "label" to "Mart",
                "iconUrl" to "",
                "visible" to false,
                "placement" to "home",
                "order" to 0
            ),
            "flight" to mapOf(
                "label" to "Flights",
                "iconUrl" to "https://cdn.example.com/flight.png",
                "visible" to true,
                "placement" to "home",
                "order" to 1
            ),
            "parcel" to mapOf(
                "label" to "Padala",
                "visible" to true,
                "placement" to "more",
                "order" to 0
            )
        )

        val parsed = AppDesignerParser.parseServices(rawMap)
        assertEquals(8, parsed.size)
        assertEquals(
            setOf("food", "convenience", "parcel", "pabili", "taxi", "flight", "loans", "jobfair"),
            parsed.keys
        )

        assertEquals("Eats", parsed["food"]?.label)
        assertEquals("https://cdn.example.com/eats.png", parsed["food"]?.iconUrl)
        assertTrue(parsed["food"]?.visible == true)
        assertEquals("home", parsed["food"]?.placement)
        assertEquals(2, parsed["food"]?.order)

        assertEquals("Mart", parsed["convenience"]?.label)
        assertFalse(parsed["convenience"]?.visible == true)

        assertEquals("Flights", parsed["flight"]?.label)
        assertEquals("home", parsed["flight"]?.placement)
        assertEquals(1, parsed["flight"]?.order)

        assertEquals("Padala", parsed["parcel"]?.label)
        assertEquals("more", parsed["parcel"]?.placement)
        assertEquals(0, parsed["parcel"]?.order)

        // Missing entries retain defaults
        assertEquals("Pabili", parsed["pabili"]?.label)
        assertEquals("home", parsed["pabili"]?.placement)
        assertEquals("Job Fair", parsed["jobfair"]?.label)
        assertEquals("more", parsed["jobfair"]?.placement)
    }

    @Test
    fun parseServices_supportsListFormat_andStringCoercions() {
        val rawList = listOf(
            mapOf("id" to "jobfair", "label" to "Careers", "visible" to "true", "placement" to "HOME", "order" to "0"),
            mapOf("id" to "loans", "label" to "Cash", "visible" to "false", "placement" to "invalid", "order" to 9)
        )

        val parsed = AppDesignerParser.parseServices(rawList)
        assertEquals("Careers", parsed["jobfair"]?.label)
        assertTrue(parsed["jobfair"]?.visible == true)
        assertEquals("home", parsed["jobfair"]?.placement)
        assertEquals(0, parsed["jobfair"]?.order)

        assertEquals("Cash", parsed["loans"]?.label)
        assertFalse(parsed["loans"]?.visible == true)
        // Invalid placement falls back to loans default ("more")
        assertEquals("more", parsed["loans"]?.placement)
    }

    @Test
    fun orderedServicesForPlacement_filtersHidden_andSortsByOrder() {
        val settings = AppDesignerSettings(
            services = mapOf(
                "food" to AppDesignerServiceConfig("food", "Eats", "", true, "home", 3),
                "convenience" to AppDesignerServiceConfig("convenience", "Mart", "", false, "home", 0),
                "flight" to AppDesignerServiceConfig("flight", "Fly", "", true, "home", 1),
                "parcel" to AppDesignerServiceConfig("parcel", "Padala", "", true, "more", 0),
                "loans" to AppDesignerServiceConfig("loans", "Loans", "", false, "more", 1),
                "jobfair" to AppDesignerServiceConfig("jobfair", "Jobs", "", true, "more", 2)
            )
        )

        val home = settings.orderedServicesForPlacement("home")
        assertEquals(listOf("flight", "food", "pabili", "taxi"), home.map { it.id })

        val more = settings.orderedServicesForPlacement("more")
        assertEquals(listOf("parcel", "jobfair"), more.map { it.id })
    }

    @Test
    fun branchGrouping_and_serviceAvailability_hideServicesWithVisibleFalse() {
        val city = City(
            id = "puerto-princesa",
            name = "Puerto Princesa",
            status = "active",
            active = true,
            lat = 9.7392,
            lng = 118.7353
        )
        val foodShop = Restaurant(
            id = "r_food",
            name = "Food Spot",
            category = "Burgers",
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = "Rizal Ave",
            phone = "09170000001",
            cityId = "puerto-princesa",
            type = "restaurant",
            isConvenience = false,
            status = "active",
            lat = 9.7392,
            lng = 118.7353
        )
        val convShop = Restaurant(
            id = "r_conv",
            name = "Mart Spot",
            category = "Grocery Stores",
            etaMinutes = 15,
            deliveryFee = 39.0,
            address = "Rizal Ave",
            phone = "09170000002",
            cityId = "puerto-princesa",
            type = "convenience",
            isConvenience = true,
            status = "active",
            lat = 9.7392,
            lng = 118.7353
        )

        val designerWithConvHidden = AppDesignerSettings(
            services = AppDesignerParser.defaultServices() + (
                "convenience" to AppDesignerServiceConfig("convenience", "Convenience", "", false, "home", 1)
            ) + (
                "flight" to AppDesignerServiceConfig("flight", "Flight", "", false, "more", 5)
            )
        )

        val eligible = BranchGroupingUtil.getEligibleGroupedShops(
            allShops = listOf(foodShop, convShop),
            cities = listOf(city),
            platformSettings = PlatformSettings(),
            defaultRadiusKm = 10.0,
            customerLat = 9.7392,
            customerLng = 118.7353,
            fallbackCity = city,
            appDesignerSettings = designerWithConvHidden
        )

        assertEquals(listOf("r_food"), eligible.map { it.restaurant.id })

        val flightAvail = ServiceAvailability.availability(
            city = city,
            service = "flight",
            platformSettings = PlatformSettings(),
            deliveryRates = null,
            logResult = false,
            appDesignerSettings = designerWithConvHidden
        )
        assertFalse(flightAvail.ok)
        assertTrue(flightAvail.checks.any { it.key == "app_designer_visible" && !it.passed })
    }

    @Test
    fun moreServices_usesCustomLabelsOrderAndHidesHiddenServices() {
        val settings = AppDesignerSettings(
            services = AppDesignerParser.defaultServices().toMutableMap().apply {
                this["parcel"] = AppDesignerServiceConfig("parcel", "Express Padala", "", true, "more", 0)
                this["flight"] = AppDesignerServiceConfig("flight", "Air Travel", "", true, "more", 1)
                this["jobfair"] = AppDesignerServiceConfig("jobfair", "Careers", "", false, "more", 2)
                this["loans"] = AppDesignerServiceConfig("loans", "Quick Cash", "", true, "more", 3)
            }
        )

        val tiles = settings.orderedServicesForPlacement("more")
            .map { buildMoreServiceTile(it, LoanOffer(uid = "u1", enabled = true, maxAmount = 10000.0)) }

        composeRule.setContent {
            DelivioTheme {
                MoreServicesContent(
                    tiles = tiles,
                    onBack = {},
                    onTileClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_tile_parcel").assertIsDisplayed()
        composeRule.onNodeWithText("Express Padala").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_flight").assertIsDisplayed()
        composeRule.onNodeWithText("Air Travel").assertIsDisplayed()
        composeRule.onNodeWithTag("more_tile_loans").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Cash").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("more_tile_jobfair").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun delivioIcon_emptyUrlUsesBuiltInIconImmediately_nonEmptyShowsLoadingPlaceholder() {
        composeRule.setContent {
            DelivioTheme {
                DelivioIcon(
                    iconFileName = "",
                    contentDescription = "Built-in Food Icon",
                    fallbackIcon = Icons.Default.Restaurant
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Built-in Food Icon").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("icon_loading_placeholder").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun delivioIcon_blankWhitespaceUrlUsesBuiltInIconImmediately() {
        composeRule.setContent {
            DelivioTheme {
                DelivioIcon(
                    iconFileName = "   ",
                    contentDescription = "Blank URL Icon",
                    fallbackIcon = Icons.Default.Restaurant
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Blank URL Icon").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("icon_loading_placeholder").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun delivioIcon_remoteOrBrokenUrlShowsLoadingPlaceholderThenFallsBackToBuiltInIcon() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent {
                DelivioTheme {
                    DelivioIcon(
                        iconFileName = "file:///nonexistent_service_icon.png",
                        contentDescription = "Fallback Service Icon",
                        fallbackIcon = Icons.Default.Restaurant
                    )
                }
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("icon_loading_placeholder").assertIsDisplayed()

            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.waitUntil(timeoutMillis = 3000L) {
                composeRule.onAllNodesWithTag("icon_loading_placeholder").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithContentDescription("Fallback Service Icon").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}
