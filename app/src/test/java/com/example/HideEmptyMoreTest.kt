package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppDesignerServiceConfig
import com.example.data.model.AppDesignerSettings
import com.example.data.model.City
import com.example.data.model.LoanOffer
import com.example.data.model.PlatformSettings
import com.example.data.model.ServiceLayoutConfig
import com.example.data.model.moreServices
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.more.MoreServicesScreen
import com.example.ui.theme.DelivioTheme
import com.example.util.AppDesignerParser
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HideEmptyMoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun moreServices_enforcesAllFourConditions_andLoadingMissingStates() {
        val activeCity = City(id = "ppc", name = "Puerto Princesa", status = "active")
        val flightOnlySettings = AppDesignerSettings(
            services = mapOf(
                "food" to AppDesignerServiceConfig("food", "Food", "", true, "home", 0),
                "flight" to AppDesignerServiceConfig("flight", "Flight", "", true, "more", 0),
                "loans" to AppDesignerServiceConfig("loans", "Loans", "", true, "more", 1),
                "jobfair" to AppDesignerServiceConfig("jobfair", "Job Fair", "", false, "more", 2)
            ),
            configuredServiceIds = setOf("food", "flight", "loans", "jobfair"),
            hasLoaded = true,
            hasDocument = true
        )

        // While loading -> empty (no flash), while home placement is unaffected
        val loading = flightOnlySettings.copy(hasLoaded = false, hasDocument = false)
        assertTrue(moreServices(loading, PlatformSettings(), activeCity, null).isEmpty())
        assertEquals(
            listOf("food", "convenience", "parcel", "pabili", "taxi"),
            loading.orderedServicesForPlacement("home").map { it.id }
        )

        // Missing or failed document -> empty, while home placement is unaffected
        val missingDoc = flightOnlySettings.copy(hasLoaded = true, hasDocument = false)
        assertTrue(moreServices(missingDoc, PlatformSettings(), activeCity, null).isEmpty())
        assertEquals(
            listOf("food", "convenience", "parcel", "pabili", "taxi"),
            missingDoc.orderedServicesForPlacement("home").map { it.id }
        )

        // Loans without offer does NOT count -> only Flight is in More
        assertEquals(
            listOf("flight"),
            moreServices(flightOnlySettings, PlatformSettings(), activeCity, null).map { it.id }
        )

        // Loans with disabled offer does NOT count
        assertEquals(
            listOf("flight"),
            moreServices(
                flightOnlySettings,
                PlatformSettings(),
                activeCity,
                LoanOffer(uid = "u1", enabled = false)
            ).map { it.id }
        )

        // Loans with enabled offer DOES count (in admin order)
        assertEquals(
            listOf("flight", "loans"),
            moreServices(
                flightOnlySettings,
                PlatformSettings(),
                activeCity,
                LoanOffer(uid = "u1", enabled = true, maxAmount = 5000.0)
            ).map { it.id }
        )

        // Platform off excludes Flight; missing key counts as ON
        assertEquals(
            emptyList<String>(),
            moreServices(
                flightOnlySettings,
                PlatformSettings(services = mapOf("flight" to false)),
                activeCity,
                null
            ).map { it.id }
        )
        assertEquals(
            listOf("flight"),
            moreServices(
                flightOnlySettings,
                PlatformSettings(services = emptyMap()),
                activeCity,
                null
            ).map { it.id }
        )

        // City off excludes Flight; missing key in city.services counts as ON
        assertEquals(
            emptyList<String>(),
            moreServices(
                flightOnlySettings,
                PlatformSettings(),
                activeCity.copy(services = mapOf("flight" to false)),
                null
            ).map { it.id }
        )
        assertEquals(
            listOf("flight"),
            moreServices(
                flightOnlySettings,
                PlatformSettings(),
                activeCity.copy(services = emptyMap()),
                null
            ).map { it.id }
        )

        // Service omitted from settings/appDesigner.services (admin removed it) does not leak from defaults
        val removedFromDoc = flightOnlySettings.copy(
            configuredServiceIds = setOf("food")
        )
        assertTrue(moreServices(removedFromDoc, PlatformSettings(), activeCity, null).isEmpty())
    }

    @Test
    fun homeScreen_hidesMoreWhenEmpty_andShowsOrRemovesLiveWithoutRestarting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val firestoreService = FirestoreService()
        val restaurantRepo = RestaurantRepository(firestoreService, prefRepo)
        val orderRepo = OrderRepository(firestoreService)
        val mainVm = MainViewModel(prefRepo, firestoreService)
        val cartVm = CartViewModel(CartRepository(), orderRepo)

        composeRule.setContent {
            DelivioTheme {
                HomeScreen(
                    restaurantRepository = restaurantRepo,
                    orderRepository = orderRepo,
                    mainViewModel = mainVm,
                    cartViewModel = cartVm,
                    onNavigateToRestaurant = {},
                    onNavigateToSearch = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Initially (no live doc / loading) -> More is hidden, home services unaffected
        composeRule.onAllNodesWithTag("service_launcher_more").assertCountEquals(0)
        composeRule.onNodeWithTag("service_launcher_food").assertIsDisplayed()

        // 1. Admin leaves More empty (only 5 home services in array) -> no More tile
        val onlyHomeList = listOf(
            AppDesignerServiceConfig("food", "Food", "", true, "home", 0),
            AppDesignerServiceConfig("convenience", "Convenience", "", true, "home", 1),
            AppDesignerServiceConfig("parcel", "Parcel", "", true, "home", 2),
            AppDesignerServiceConfig("pabili", "Pabili", "", true, "home", 3),
            AppDesignerServiceConfig("taxi", "Taxi", "", true, "home", 4)
        ).associateBy { it.id }

        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = AppDesignerParser.defaultServices() + onlyHomeList,
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi"),
                hasLoaded = true,
                hasDocument = true
            )
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("service_launcher_more").assertCountEquals(0)
        composeRule.onNodeWithTag("service_launcher_taxi").assertIsDisplayed()

        // 2. Loans in More without an offer does NOT make More appear
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = AppDesignerParser.defaultServices() + onlyHomeList + mapOf(
                    "loans" to AppDesignerServiceConfig("loans", "Loans", "", true, "more", 5)
                ),
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi", "loans"),
                hasLoaded = true,
                hasDocument = true
            )
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("service_launcher_more").assertCountEquals(0)

        // 3. Admin assigns Flight to More -> More tile appears live (works in 2-row layout too)
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = AppDesignerParser.defaultServices() + onlyHomeList + mapOf(
                    "flight" to AppDesignerServiceConfig("flight", "Flight", "", true, "more", 5)
                ),
                serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 4),
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi", "flight"),
                hasLoaded = true,
                hasDocument = true
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("service_launcher_more").assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("service_launcher_more") and hasAnyAncestor(hasTestTag("home_service_page_0_row_1"))
        ).assertIsDisplayed()

        // 4. Admin removes Flight -> More tile disappears live & 2-row grid re-flows without gap
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = AppDesignerParser.defaultServices() + onlyHomeList,
                serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 4),
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi"),
                hasLoaded = true,
                hasDocument = true
            )
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("service_launcher_more").assertCountEquals(0)
        composeRule.onNode(
            hasTestTag("service_launcher_taxi") and hasAnyAncestor(hasTestTag("home_service_page_0_row_1"))
        ).assertIsDisplayed()
    }

    @Test
    fun moreServicesScreen_showsOnlyMoreServices_andClosesBackToHomeWhenEmptied() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val firestoreService = FirestoreService()
        val restaurantRepo = RestaurantRepository(firestoreService, prefRepo)

        val homePlusFlightInMore = AppDesignerParser.defaultServices().toMutableMap().apply {
            this["flight"] = AppDesignerServiceConfig("flight", "Flight", "", true, "more", 0)
            this["loans"] = AppDesignerServiceConfig("loans", "Loans", "", true, "more", 1)
            this["jobfair"] = AppDesignerServiceConfig("jobfair", "Job Fair", "", false, "more", 2)
        }
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = homePlusFlightInMore,
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi", "flight", "loans", "jobfair"),
                hasLoaded = true,
                hasDocument = true
            )
        )

        var backCount = 0
        composeRule.setContent {
            DelivioTheme {
                MoreServicesScreen(
                    restaurantRepository = restaurantRepo,
                    cityId = "puerto-princesa",
                    currentUid = "",
                    onBack = { backCount++ },
                    onOpenFlight = {},
                    onOpenLoans = {},
                    onOpenJobFair = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Only Flight appears (Loans has no offer, Job Fair has visible=false)
        composeRule.onNodeWithTag("more_tile_flight").assertIsDisplayed()
        composeRule.onAllNodesWithTag("more_tile_loans").assertCountEquals(0)
        composeRule.onAllNodesWithTag("more_tile_jobfair").assertCountEquals(0)
        assertEquals(0, backCount)

        // Admin removes Flight -> More list becomes empty -> automatically goes back to Home
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = AppDesignerParser.defaultServices(),
                configuredServiceIds = setOf("food", "convenience", "parcel", "pabili", "taxi"),
                hasLoaded = true,
                hasDocument = true
            )
        )
        composeRule.waitForIdle()
        assertEquals(1, backCount)
    }
}
