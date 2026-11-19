package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.MockData
import com.example.data.model.Address
import com.example.data.model.MenuItem
import com.example.data.model.Restaurant
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.DelivioBottomNavBar
import com.example.ui.components.DeliveryDetailsBottomSheet
import com.example.ui.navigation.NavDestinations
import com.example.ui.screens.cart.CartScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.theme.DelivioTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel
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
class ViewCartAndDeliveryAddressTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createTestViewModels(): Triple<MainViewModel, CartViewModel, RestaurantRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefRepo = PreferenceRepository(context)
        val firestoreService = FirestoreService()
        val mainViewModel = MainViewModel(prefRepo, firestoreService)
        val cartRepo = CartRepository()
        val orderRepo = OrderRepository(firestoreService)
        val cartViewModel = CartViewModel(cartRepo, orderRepo)
        val restaurantRepo = RestaurantRepository(firestoreService)
        return Triple(mainViewModel, cartViewModel, restaurantRepo)
    }

    private fun seedCartItem(cartViewModel: CartViewModel, restLat: Double = 9.7392, restLng: Double = 118.7353): Restaurant {
        val rest = MockData.restaurants.first().copy(
            id = "shop_conv_1",
            name = "Palawan Quick Mart",
            type = "convenience",
            isConvenience = true,
            cityId = "puerto-princesa",
            lat = restLat,
            lng = restLng
        )
        val item = MenuItem(
            id = "item_1",
            restaurantId = rest.id,
            name = "Bottled Water 1L",
            description = "Pure water",
            price = 35.0,
            category = "Drinks",
            emoji = "💧"
        )
        cartViewModel.addItem(item, rest, 2)
        return rest
    }

    @Test
    fun viewYourCart_pushesCartOnTopWithoutPoppingShopOrList_supportsLaunchSingleTopAndBack() {
        val (mainViewModel, cartViewModel, _) = createTestViewModels()
        seedCartItem(cartViewModel)
        lateinit var navController: NavHostController

        composeRule.setContent {
            DelivioTheme {
                navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = NavDestinations.MAIN
                ) {
                    composable(NavDestinations.MAIN) {
                        Button(
                            onClick = { navController.navigate(NavDestinations.serviceShops("convenience")) },
                            modifier = Modifier.testTag("go_convenience_list")
                        ) { Text("Convenience List") }
                    }
                    composable(
                        route = NavDestinations.SERVICE_SHOPS,
                        arguments = listOf(navArgument("serviceType") { type = NavType.StringType })
                    ) {
                        Button(
                            onClick = { navController.navigate(NavDestinations.merchantDetail("shop_conv_1")) },
                            modifier = Modifier.testTag("open_shop_detail")
                        ) { Text("Open Convenience Shop") }
                    }
                    composable(
                        route = NavDestinations.MERCHANT_DETAIL,
                        arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
                    ) {
                        Button(
                            onClick = {
                                navController.navigate(NavDestinations.CART) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.testTag("floating_view_cart_pill")
                        ) { Text("View your cart") }
                    }
                    composable(NavDestinations.CART) {
                        Scaffold(
                            bottomBar = {
                                DelivioBottomNavBar(
                                    selectedTab = AppTab.CART,
                                    cartItemCount = 2,
                                    inboxUnreadCount = 0,
                                    onTabSelected = { tab ->
                                        if (tab != AppTab.CART) {
                                            mainViewModel.selectTab(tab)
                                            navController.popBackStack(NavDestinations.MAIN, inclusive = false)
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                                CartScreen(
                                    cartViewModel = cartViewModel,
                                    mainViewModel = mainViewModel,
                                    onExploreShop = {},
                                    onAddMoreItems = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigate: MAIN -> SERVICE_SHOPS(convenience) -> MERCHANT_DETAIL -> CART
        composeRule.onNodeWithTag("go_convenience_list").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_shop_detail").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_view_cart_pill").performClick()
        composeRule.waitForIdle()

        // Verify My Cart is open, Cart tab is selected, and neither shop nor shops list was popped
        composeRule.onNodeWithText("My Cart").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_cart").assertIsSelected()
        val routes = navController.currentBackStack.value.mapNotNull { it.destination.route }
        assertTrue(routes.contains(NavDestinations.MAIN))
        assertTrue(routes.contains(NavDestinations.SERVICE_SHOPS))
        assertTrue(routes.contains(NavDestinations.MERCHANT_DETAIL))
        assertEquals(NavDestinations.CART, navController.currentBackStackEntry?.destination?.route)

        // Calling navigate(CART) with launchSingleTop a second time must not duplicate CART
        composeRule.runOnUiThread {
            navController.navigate(NavDestinations.CART) { launchSingleTop = true }
        }
        composeRule.waitForIdle()
        val cartEntries = navController.currentBackStack.value.count { it.destination.route == NavDestinations.CART }
        assertEquals(1, cartEntries)

        // Back returns directly to the shop page (MERCHANT_DETAIL), then to SERVICE_SHOPS
        composeRule.runOnUiThread {
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(NavDestinations.MERCHANT_DETAIL, navController.currentBackStackEntry?.destination?.route)

        composeRule.runOnUiThread {
            navController.popBackStack()
        }
        composeRule.waitForIdle()
        assertEquals(NavDestinations.SERVICE_SHOPS, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun cartScreen_withNoAddress_allThreeTriggersOpenMapPickerAndKeepSetDeliveryAddressLabel() {
        val (mainViewModel, cartViewModel, _) = createTestViewModels()
        seedCartItem(cartViewModel)
        var mapPickerOpens = 0

        composeRule.setContent {
            DelivioTheme {
                CartScreen(
                    cartViewModel = cartViewModel,
                    mainViewModel = mainViewModel,
                    onExploreShop = {},
                    onOpenDeliveryMapPicker = { _, _, _, _, _ -> mapPickerOpens++ }
                )
            }
        }
        composeRule.waitForIdle()

        // Verify Delivery Destination - None - Tap to set or pin location
        composeRule.onNodeWithTag("cart_delivery_address_card").assertIsDisplayed()
        composeRule.onNodeWithText("Delivery Destination").assertIsDisplayed()
        composeRule.onNodeWithText("None").assertIsDisplayed()
        composeRule.onNodeWithText("Tap to set or pin location").assertIsDisplayed()

        // Verify red "Set delivery address" text and disabled-looking bottom button label
        composeRule.onNodeWithTag("cart_bottom_set_address_text").assertIsDisplayed()
        composeRule.onNodeWithTag("place_order_button").assertIsDisplayed()

        // Trigger 1: Tap Delivery Destination card
        composeRule.onNodeWithTag("cart_delivery_address_card").performClick()
        assertEquals(1, mapPickerOpens)

        // Trigger 2: Tap red "Set delivery address" text
        composeRule.onNodeWithTag("cart_bottom_set_address_text").performClick()
        assertEquals(2, mapPickerOpens)

        // Trigger 3: Tap bottom button (keeps "Set delivery address" label, yet is tappable)
        composeRule.onNodeWithTag("place_order_button").performClick()
        assertEquals(3, mapPickerOpens)
    }

    @Test
    fun deliveryDetailsBottomSheet_saveSwitchAnd3AddressCapWorkCorrectly() {
        val (mainViewModel, _, _) = createTestViewModels()
        var savedCountState = 0
        var confirmedArgs: List<Any>? = null

        composeRule.setContent {
            DelivioTheme {
                DeliveryDetailsBottomSheet(
                    initialAddress = "Rizal Avenue, Barangay San Miguel, Puerto Princesa",
                    lat = 9.7450,
                    lng = 118.7410,
                    savedAddressCount = savedCountState,
                    onDismiss = {},
                    onConfirm = { addressText, label, landmark, saveToMyAddresses ->
                        val result = mainViewModel.confirmDeliveryDetails(
                            addressText = addressText,
                            label = label,
                            landmarkOrUnit = landmark,
                            lat = 9.7450,
                            lng = 118.7410,
                            saveToMyAddresses = saveToMyAddresses
                        )
                        confirmedArgs = listOf(addressText, label, landmark, saveToMyAddresses, result)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Switch is ON by default when < 3 saved addresses
        composeRule.onNodeWithText("Delivery details").assertIsDisplayed()
        composeRule.onNodeWithTag("delivery_details_save_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("delivery_details_save_switch").assertIsOn()
        composeRule.onAllNodesWithTag("delivery_details_max_addresses_note").assertCountEquals(0)

        // Edit address, select "Work", add landmark, turn switch OFF, and confirm
        composeRule.onNodeWithTag("delivery_details_address_input").performTextClearance()
        composeRule.onNodeWithTag("delivery_details_address_input").performTextInput("Malvar Street, Puerto Princesa")
        composeRule.onNodeWithTag("delivery_details_label_Work").performClick()
        composeRule.onNodeWithTag("delivery_details_landmark_input").performTextInput("Unit 4B")
        composeRule.onNodeWithTag("delivery_details_save_switch").performClick()
        composeRule.onNodeWithTag("delivery_details_save_switch").assertIsOff()
        composeRule.onNodeWithTag("delivery_details_confirm_button").performClick()
        composeRule.waitForIdle()

        // Ephemeral address set as active; savedAddresses remains empty
        assertEquals(0, mainViewModel.savedAddresses.value.size)
        assertEquals("Work", mainViewModel.currentAddress.value.title)
        assertEquals("Malvar Street, Puerto Princesa", mainViewModel.currentAddress.value.fullAddress)
        assertEquals("Unit 4B", mainViewModel.currentAddress.value.details)
        assertTrue(mainViewModel.currentAddress.value.id.startsWith("addr_order_"))
    }

    @Test
    fun deliveryDetailsBottomSheet_hidesSwitchAndShowsLimitNoteAt3SavedAddresses() {
        val (mainViewModel, _, _) = createTestViewModels()

        composeRule.setContent {
            DelivioTheme {
                DeliveryDetailsBottomSheet(
                    initialAddress = "San Pedro Highway, Puerto Princesa",
                    lat = 9.7600,
                    lng = 118.7500,
                    savedAddressCount = 3,
                    onDismiss = {},
                    onConfirm = { addressText, label, landmark, saveToMyAddresses ->
                        mainViewModel.confirmDeliveryDetails(
                            addressText = addressText,
                            label = label,
                            landmarkOrUnit = landmark,
                            lat = 9.7600,
                            lng = 118.7500,
                            saveToMyAddresses = saveToMyAddresses
                        )
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("delivery_details_save_switch").assertCountEquals(0)
        composeRule.onNodeWithTag("delivery_details_max_addresses_note").assertIsDisplayed()
        composeRule.onNodeWithText("You already have 3 saved addresses").assertIsDisplayed()

        composeRule.onNodeWithTag("delivery_details_label_Other").performClick()
        composeRule.onNodeWithTag("delivery_details_confirm_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Other", mainViewModel.currentAddress.value.title)
        assertEquals("San Pedro Highway, Puerto Princesa", mainViewModel.currentAddress.value.fullAddress)
    }

    @Test
    fun cartScreen_afterConfirmingAddress_updatesButtonRecalculatesFeesAndClearsRedText() {
        val (mainViewModel, cartViewModel, _) = createTestViewModels()
        seedCartItem(cartViewModel, restLat = 9.7392, restLng = 118.7353)

        composeRule.setContent {
            DelivioTheme {
                CartScreen(
                    cartViewModel = cartViewModel,
                    mainViewModel = mainViewModel,
                    onExploreShop = {}
                )
            }
        }
        composeRule.waitForIdle()

        val initialDeliveryFee = cartViewModel.feesResult.value.deliveryFee
        val initialTotal = cartViewModel.feesResult.value.total
        composeRule.onNodeWithTag("cart_bottom_set_address_text").assertIsDisplayed()

        // Confirm a delivery address ~12 km north of the shop
        composeRule.runOnUiThread {
            mainViewModel.confirmDeliveryDetails(
                addressText = "Barangay Irawan, Puerto Princesa",
                label = "Home",
                landmarkOrUnit = "Gate 1",
                lat = 9.8500,
                lng = 118.7353,
                saveToMyAddresses = false
            )
        }
        composeRule.waitForIdle()

        // Red "Set delivery address" disappears and Place Order button shows price
        composeRule.onAllNodesWithTag("cart_bottom_set_address_text").assertCountEquals(0)
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Barangay Irawan, Puerto Princesa").assertIsDisplayed()

        val newDeliveryFee = cartViewModel.feesResult.value.deliveryFee
        val newTotal = cartViewModel.feesResult.value.total
        assertTrue(
            "Expected delivery fee to increase for ~12 km distance (was $initialDeliveryFee, now $newDeliveryFee)",
            newDeliveryFee > initialDeliveryFee
        )
        assertTrue(
            "Expected total to increase (was $initialTotal, now $newTotal)",
            newTotal > initialTotal
        )
    }

    @Test
    fun cartScreen_whenSavedAddressesExist_cardOpensSavedAddressesSheetWithPinNewLocation() {
        val (mainViewModel, cartViewModel, _) = createTestViewModels()
        seedCartItem(cartViewModel)
        mainViewModel.confirmDeliveryDetails(
            addressText = "123 Rizal Ave, Puerto Princesa",
            label = "Home",
            lat = 9.7400,
            lng = 118.7360,
            saveToMyAddresses = true
        )
        var mapPickerOpens = 0

        composeRule.setContent {
            DelivioTheme {
                CartScreen(
                    cartViewModel = cartViewModel,
                    mainViewModel = mainViewModel,
                    onExploreShop = {},
                    onOpenDeliveryMapPicker = { _, _, _, _, _ -> mapPickerOpens++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cart_delivery_address_card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saved_addresses_bottom_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_new_location_button").assertIsDisplayed()

        composeRule.onNodeWithTag("pin_new_location_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, mapPickerOpens)
    }

    @Test
    fun homeScreen_deliverToNone_opensSharedMapPickerDirectly() {
        val (mainViewModel, cartViewModel, restaurantRepo) = createTestViewModels()
        val orderRepo = OrderRepository(restaurantRepo.service)
        var homeMapPickerOpens = 0

        composeRule.setContent {
            DelivioTheme {
                HomeScreen(
                    restaurantRepository = restaurantRepo,
                    orderRepository = orderRepo,
                    mainViewModel = mainViewModel,
                    cartViewModel = cartViewModel,
                    onNavigateToRestaurant = {},
                    onNavigateToSearch = {},
                    onOpenDeliveryMapPicker = { _, _, _, _, _ -> homeMapPickerOpens++ }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals("None", mainViewModel.currentAddress.value.title)
        composeRule.onNodeWithTag("delivery_address_selector").performClick()
        composeRule.waitForIdle()
        assertEquals(1, homeMapPickerOpens)
    }
}
