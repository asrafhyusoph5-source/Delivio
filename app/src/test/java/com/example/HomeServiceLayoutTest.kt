package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppDesignerServiceConfig
import com.example.data.model.AppDesignerSettings
import com.example.data.model.ServiceLayoutConfig
import com.example.data.model.ServiceType
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeServiceLauncherSection
import com.example.ui.screens.home.LauncherItem
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
class HomeServiceLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildItems(keys: List<String>): List<LauncherItem> {
        return keys.map { key ->
            val svc = when (key) {
                "food" -> ServiceType.FOOD
                "convenience" -> ServiceType.CONVENIENCE
                "parcel" -> ServiceType.PARCEL
                "pabili" -> ServiceType.PABILI
                "taxi" -> ServiceType.TAXI
                else -> null
            }
            val icon = when (key) {
                "food" -> Icons.Default.Restaurant
                "convenience" -> Icons.Default.LocalGroceryStore
                "parcel" -> Icons.Default.Inventory2
                "pabili" -> Icons.Default.ShoppingBag
                "taxi" -> Icons.Default.DirectionsCar
                "flight" -> Icons.Default.Flight
                "loans" -> Icons.Default.Payments
                "jobfair" -> Icons.Default.Work
                else -> Icons.Default.Apps
            }
            val title = when (key) {
                "more" -> "More"
                "jobfair" -> "Job Fair"
                else -> key.replaceFirstChar { it.uppercase() }
            }
            LauncherItem(
                serviceType = svc,
                key = key,
                title = title,
                iconFile = null,
                fallbackIcon = icon
            )
        }
    }

    @Test
    fun parseServiceLayout_defaultsTo1Row_andParsesValidAndClampedValues() {
        val missing = AppDesignerParser.parseServiceLayout(null)
        assertEquals(1, missing.effectiveRows)
        assertEquals(5, missing.effectivePerRow)

        val twoByFour = AppDesignerParser.parseServiceLayout(mapOf("rows" to 2L, "perRow" to 4L))
        assertEquals(2, twoByFour.effectiveRows)
        assertEquals(4, twoByFour.effectivePerRow)

        val stringValues = AppDesignerParser.parseServiceLayout(mapOf("rows" to "2", "perRow" to "5"))
        assertEquals(2, stringValues.effectiveRows)
        assertEquals(5, stringValues.effectivePerRow)

        val clamped = AppDesignerParser.parseServiceLayout(mapOf("rows" to 9, "perRow" to 2))
        assertEquals(2, clamped.effectiveRows)
        assertEquals(4, clamped.effectivePerRow)
    }

    @Test
    fun twoRowGrid_fillsFirstRowBeforeSecond_keepsMoreLast_andHidesDotsOnSinglePage() {
        val items = buildItems(listOf("food", "convenience", "parcel", "pabili", "taxi", "more"))
        composeRule.setContent {
            DelivioTheme {
                HomeServiceLauncherSection(
                    items = items,
                    serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 4),
                    selectedService = null,
                    onItemClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_service_paged_grid").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("home_service_single_row").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("service_layout_page_dots").fetchSemanticsNodes().size)

        // First row receives the first 4 services
        listOf("food", "convenience", "parcel", "pabili").forEach { key ->
            composeRule.onNode(
                hasTestTag("service_launcher_$key") and hasAnyAncestor(hasTestTag("home_service_page_0_row_0"))
            ).assertIsDisplayed()
        }
        // Second row receives remaining services with "More" last
        listOf("taxi", "more").forEach { key ->
            composeRule.onNode(
                hasTestTag("service_launcher_$key") and hasAnyAncestor(hasTestTag("home_service_page_0_row_1"))
            ).assertIsDisplayed()
        }
    }

    @Test
    fun twoRowGrid_multiPage_showsPageDots_swipesPages_andUpdatesActiveLongDarkDot() {
        val items = buildItems(
            listOf("food", "convenience", "parcel", "pabili", "taxi", "flight", "loans", "jobfair", "more")
        )
        composeRule.setContent {
            DelivioTheme {
                HomeServiceLauncherSection(
                    items = items,
                    serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 4),
                    selectedService = null,
                    onItemClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 9 items with 8 per page (2x4) => 2 pages => dots shown
        composeRule.onNodeWithTag("service_layout_page_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("service_layout_dot_0").assertIsDisplayed().assertWidthIsEqualTo(18.dp)
        composeRule.onNodeWithTag("service_layout_dot_1").assertIsDisplayed().assertWidthIsEqualTo(6.dp)

        // Swipe to page 1 where "More" is the last tile
        composeRule.onNodeWithTag("home_service_paged_grid").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNode(
            hasTestTag("service_launcher_more") and hasAnyAncestor(hasTestTag("home_service_page_1_row_0"))
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("service_layout_dot_0").assertWidthIsEqualTo(6.dp)
        composeRule.onNodeWithTag("service_layout_dot_1").assertWidthIsEqualTo(18.dp)
    }

    @Test
    fun twoRowGrid_perRow5_fits9ItemsOnOnePageWithoutDots() {
        val items = buildItems(
            listOf("food", "convenience", "parcel", "pabili", "taxi", "flight", "loans", "jobfair", "more")
        )
        composeRule.setContent {
            DelivioTheme {
                HomeServiceLauncherSection(
                    items = items,
                    serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 5),
                    selectedService = null,
                    onItemClick = {}
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("service_layout_page_dots").fetchSemanticsNodes().size)
        listOf("food", "convenience", "parcel", "pabili", "taxi").forEach { key ->
            composeRule.onNode(
                hasTestTag("service_launcher_$key") and hasAnyAncestor(hasTestTag("home_service_page_0_row_0"))
            ).assertIsDisplayed()
        }
        listOf("flight", "loans", "jobfair", "more").forEach { key ->
            composeRule.onNode(
                hasTestTag("service_launcher_$key") and hasAnyAncestor(hasTestTag("home_service_page_0_row_1"))
            ).assertIsDisplayed()
        }
    }

    @Test
    fun homeScreen_switchesLiveBetween1RowAnd2RowsImmediately() {
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

        // Default (missing serviceLayout) -> 1 row single-row look
        composeRule.onNodeWithTag("home_service_single_row").assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_service_paged_grid").assertCountEquals(0)

        // Admin switches live to 2 rows, 4 per row, with 7 active home services + flight in More (8 tiles -> 1 page of 2x4)
        val allHomeWithFlightInMore = AppDesignerParser.defaultServices().mapValues { (id, cfg) ->
            when (id) {
                "flight" -> cfg.copy(placement = "more", visible = true)
                else -> cfg.copy(placement = "home", visible = true)
            }
        }
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = allHomeWithFlightInMore,
                serviceLayout = ServiceLayoutConfig(rows = 2, perRow = 4)
            )
        )
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("home_service_single_row").assertCountEquals(0)
        composeRule.onNodeWithTag("home_service_paged_grid").assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("service_launcher_food") and hasAnyAncestor(hasTestTag("home_service_page_0_row_0"))
        ).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("service_launcher_more") and hasAnyAncestor(hasTestTag("home_service_page_0_row_1"))
        ).assertIsDisplayed()

        // Admin switches live back to 1 row -> Home immediately switches back
        restaurantRepo.setAppDesignerSettingsForTest(
            AppDesignerSettings(
                services = allHomeWithFlightInMore,
                serviceLayout = ServiceLayoutConfig(rows = 1, perRow = 4)
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_service_single_row").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("home_service_paged_grid").fetchSemanticsNodes().isEmpty())
    }
}
