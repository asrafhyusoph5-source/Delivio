package com.example

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.example.crash.CrashReportActivity
import com.example.crash.CrashReportManager
import com.example.crash.DiagnosticReport
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.ui.navigation.NavDestinations
import com.example.ui.screens.home.ServiceScreenHeader
import com.example.ui.screens.home.TaxiBookingView
import com.example.ui.screens.map.MapPickerScreen
import com.example.ui.theme.DelivioTheme
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.TaxiViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashAndTaxiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun crashReportActivity_rendersIndependentLightUiWithAllRequiredControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val traceLines = (1..55).joinToString("\n") { "at com.example.Startup.step$it(Startup.kt:$it)" }
        val report = DiagnosticReport(
            id = "rep_1",
            timestamp = 1_730_000_000_000L,
            appVersion = "1.0 (1)",
            threadName = "main",
            exceptionType = "java.lang.IllegalStateException",
            message = "Startup initialization failed",
            stackTrace = traceLines,
            isFatal = true
        )

        val intent = Intent(context, CrashReportActivity::class.java).apply {
            putExtra(CrashReportManager.EXTRA_REPORT_JSON, report.toJson().toString())
        }
        val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
        val activity = controller.get()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)

        val allTexts = collectTexts(rootView)
        assertTrue(allTexts.any { it.contains("The app closed unexpectedly") })
        assertTrue(allTexts.any { it.contains("java.lang.IllegalStateException: Startup initialization failed") })
        assertTrue(allTexts.any { it.contains("step1") && it.contains("step40") })
        assertFalse(allTexts.any { it.contains("step41") })

        val buttons = collectButtonLabels(rootView)
        assertTrue(buttons.contains("Copy report"))
        assertTrue(buttons.contains("Share report"))
        assertTrue(buttons.contains("Restart app"))
    }

    @Test
    fun taxiRide_pickUpDropOffAndAddStop_navigateToMapPickerWithoutReturningHome_andPreserveState() {
        val orderRepository = OrderRepository(FirestoreService())
        var navControllerRef: androidx.navigation.NavHostController? = null
        var openPickerCallCount = 0
        var lastNavigateError: String? = null

        composeRule.setContent {
            DelivioTheme {
                val navController = rememberNavController()
                navControllerRef = navController

                NavHost(
                    navController = navController,
                    startDestination = NavDestinations.TAXI_RIDE
                ) {
                    composable(NavDestinations.MAIN) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("Home Screen")
                            Button(
                                onClick = { navController.navigate(NavDestinations.taxiRide("puerto-princesa")) },
                                modifier = Modifier.testTag("open_taxi_btn")
                            ) {
                                Text("Taxi")
                            }
                        }
                    }

                    composable(
                        route = NavDestinations.TAXI_RIDE,
                        arguments = listOf(
                            navArgument("cityId") {
                                type = NavType.StringType
                                defaultValue = "puerto-princesa"
                            }
                        )
                    ) { backStackEntry ->
                        val cityId = backStackEntry.arguments?.getString("cityId") ?: "puerto-princesa"
                        val taxiViewModel: TaxiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            viewModelStoreOwner = backStackEntry,
                            factory = TaxiViewModel.Factory(cityId)
                        )
                        val savedStateHandle = backStackEntry.savedStateHandle
                        val pickerResultId by savedStateHandle.getStateFlow("map_picker_result_id", 0L).collectAsState()

                        LaunchedEffect(pickerResultId) {
                            if (pickerResultId != 0L) {
                                taxiViewModel.applyPickedLocation(
                                    stopType = savedStateHandle.get<String>("map_picker_stop_type") ?: "pickup",
                                    stopIndex = savedStateHandle.get<Int>("map_picker_stop_index") ?: 0,
                                    address = savedStateHandle.get<String>("map_picker_address").orEmpty(),
                                    lat = savedStateHandle.get<Double>("map_picker_lat") ?: 0.0,
                                    lng = savedStateHandle.get<Double>("map_picker_lng") ?: 0.0
                                )
                                savedStateHandle.remove<Long>("map_picker_result_id")
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            ServiceScreenHeader(title = "Taxi Ride", onBack = { navController.popBackStack() })
                            TaxiBookingView(
                                orderRepository = orderRepository,
                                cityId = cityId,
                                taxiViewModel = taxiViewModel,
                                onOpenMapPicker = { title, stopType, stopIndex, initialAddress, initialLat, initialLng, cityLat, cityLng, pinColor ->
                                    openPickerCallCount++
                                    try {
                                        navController.navigate(
                                            NavDestinations.mapPicker(
                                                title = title,
                                                stopType = stopType,
                                                stopIndex = stopIndex,
                                                initialAddress = initialAddress,
                                                initialLat = initialLat,
                                                initialLng = initialLng,
                                                cityLat = cityLat,
                                                cityLng = cityLng,
                                                pinColor = pinColor
                                            )
                                        )
                                    } catch (t: Throwable) {
                                        lastNavigateError = t.stackTraceToString()
                                    }
                                },
                                onOrderBooked = {}
                            )
                        }
                    }

                    composable(
                        route = NavDestinations.MAP_PICKER,
                        arguments = listOf(
                            navArgument("title") { type = NavType.StringType; defaultValue = "Select Location" },
                            navArgument("stopType") { type = NavType.StringType; defaultValue = "pickup" },
                            navArgument("stopIndex") { type = NavType.IntType; defaultValue = 0 },
                            navArgument("initialAddress") { type = NavType.StringType; defaultValue = "" },
                            navArgument("initialLat") { type = NavType.StringType; defaultValue = "9.7392" },
                            navArgument("initialLng") { type = NavType.StringType; defaultValue = "118.7353" },
                            navArgument("cityLat") { type = NavType.StringType; defaultValue = "9.7392" },
                            navArgument("cityLng") { type = NavType.StringType; defaultValue = "118.7353" },
                            navArgument("pinColor") { type = NavType.StringType; defaultValue = "yellow" }
                        )
                    ) { backStackEntry ->
                        val title = backStackEntry.arguments?.getString("title") ?: "Select Location"
                        val stopType = backStackEntry.arguments?.getString("stopType") ?: "pickup"
                        val stopIndex = backStackEntry.arguments?.getInt("stopIndex") ?: 0
                        val initialAddress = backStackEntry.arguments?.getString("initialAddress") ?: ""
                        val initialLat = backStackEntry.arguments?.getString("initialLat")?.toDoubleOrNull() ?: 9.7392
                        val initialLng = backStackEntry.arguments?.getString("initialLng")?.toDoubleOrNull() ?: 118.7353
                        val cityLat = backStackEntry.arguments?.getString("cityLat")?.toDoubleOrNull() ?: 9.7392
                        val cityLng = backStackEntry.arguments?.getString("cityLng")?.toDoubleOrNull() ?: 118.7353
                        val pinColorName = backStackEntry.arguments?.getString("pinColor") ?: "yellow"
                        val pinColor = when (pinColorName) {
                            "green" -> SuccessGreen
                            "red" -> ErrorRed
                            else -> YellowPrimary
                        }
                        MapPickerScreen(
                            title = title,
                            initialAddress = initialAddress,
                            initialLat = initialLat,
                            initialLng = initialLng,
                            cityLat = cityLat,
                            cityLng = cityLng,
                            pinColor = pinColor,
                            onDismiss = { navController.popBackStack() },
                            onConfirm = { address, lat, lng ->
                                navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                    handle["map_picker_address"] = address
                                    handle["map_picker_lat"] = lat
                                    handle["map_picker_lng"] = lng
                                    handle["map_picker_stop_type"] = stopType
                                    handle["map_picker_stop_index"] = stopIndex
                                    handle["map_picker_result_id"] = System.nanoTime()
                                }
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }

        // Verify Taxi Ride screen loaded and no tracker bubble overlays it
        composeRule.onNodeWithText("Taxi Ride").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("tracker_bubble").fetchSemanticsNodes().size)

        // 1. Tap Pick Up from -> opens MapPicker without popping to Home
        composeRule.onNodeWithTag("taxi_pickup_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        val afterPickupRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue(
            "Expected map_picker after pickup tap (calls=$openPickerCallCount, err=$lastNavigateError), got: '$afterPickupRoute'",
            afterPickupRoute.startsWith("map_picker")
        )
        composeRule.onNodeWithTag("btn_confirm_map_location").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        val afterPickupConfirmRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue("Expected taxi_ride after confirm, got: '$afterPickupConfirmRoute'", afterPickupConfirmRoute.startsWith("taxi_ride"))
        composeRule.onNodeWithText("Taxi Ride").assertIsDisplayed()

        // 2. Tap Drop off to -> opens MapPicker without popping to Home
        composeRule.onNodeWithTag("taxi_dropoff_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        val afterDropoffRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue("Expected map_picker after dropoff tap, got: '$afterDropoffRoute'", afterDropoffRoute.startsWith("map_picker"))
        composeRule.onNodeWithTag("btn_confirm_map_location").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        val afterDropoffConfirmRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue("Expected taxi_ride after dropoff confirm, got: '$afterDropoffConfirmRoute'", afterDropoffConfirmRoute.startsWith("taxi_ride"))

        // 3. Tap Add Next Stop -> opens MapPicker, confirms stop #3, returns to Taxi Ride with stop row displayed
        composeRule.onNodeWithTag("taxi_add_stop_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val afterAddStopRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue("Expected map_picker after add stop tap, got: '$afterAddStopRoute'", afterAddStopRoute.startsWith("map_picker"))
        composeRule.onNodeWithTag("btn_confirm_map_location").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        val afterStopConfirmRoute = navControllerRef?.currentDestination?.route.orEmpty()
        assertTrue("Expected taxi_ride after stop confirm, got: '$afterStopConfirmRoute'", afterStopConfirmRoute.startsWith("taxi_ride"))
        composeRule.onNodeWithTag("taxi_stop_row_3").performScrollTo().assertIsDisplayed()
    }

    private fun collectTexts(view: View): List<String> {
        val result = mutableListOf<String>()
        if (view is TextView && view !is Button) {
            result.add(view.text?.toString().orEmpty())
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(collectTexts(view.getChildAt(i)))
            }
        }
        return result
    }

    private fun collectButtonLabels(view: View): List<String> {
        val result = mutableListOf<String>()
        if (view is Button) {
            result.add(view.text?.toString().orEmpty())
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(collectButtonLabels(view.getChildAt(i)))
            }
        }
        return result
    }
}
