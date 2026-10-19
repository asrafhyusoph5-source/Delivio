package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.DelivioBottomNavBar
import com.example.ui.components.FloatingOrderTracker
import com.example.ui.screens.cart.CartScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.shop.MerchantDetailScreen
import com.example.ui.screens.shop.ShopScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YellowPrimary
import com.example.ui.theme.TextOnYellow
import com.example.viewmodel.AppTab
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OrderViewModel

object NavDestinations {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val MERCHANT_DETAIL = "merchant_detail/{restaurantId}"

    fun merchantDetail(restaurantId: String) = "merchant_detail/$restaurantId"
}

@Composable
fun DelivioNavGraph(
    authViewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    cartViewModel: CartViewModel,
    orderViewModel: OrderViewModel,
    restaurantRepository: RestaurantRepository,
    navController: NavHostController = rememberNavController()
) {
    val authState by authViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val merchantCancellationAlert by orderViewModel.merchantCancellationAlert.collectAsState()

    // One-time Dialog: "Your order was cancelled by the merchant." + reason
    if (merchantCancellationAlert != null) {
        val alert = merchantCancellationAlert!!
        AlertDialog(
            onDismissRequest = { orderViewModel.dismissMerchantCancellationAlert() },
            title = {
                Text(
                    text = "Order Cancelled",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Your order was cancelled by the merchant.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!alert.reason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reason: ${alert.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { orderViewModel.dismissMerchantCancellationAlert() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Collect snackbars from mainViewModel
    LaunchedEffect(Unit) {
        mainViewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Collect status changes from orderViewModel
    LaunchedEffect(Unit) {
        orderViewModel.statusNotificationEvent.collect { statusMsg ->
            snackbarHostState.showSnackbar(statusMsg)
        }
    }

    val startDestination = if (authState.isLoggedIn) NavDestinations.MAIN else NavDestinations.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(NavDestinations.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(NavDestinations.MAIN) {
                        popUpTo(NavDestinations.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NavDestinations.MAIN) {
            MainContainerScreen(
                authViewModel = authViewModel,
                mainViewModel = mainViewModel,
                cartViewModel = cartViewModel,
                orderViewModel = orderViewModel,
                restaurantRepository = restaurantRepository,
                snackbarHostState = snackbarHostState,
                onNavigateToMerchant = { restaurantId ->
                    navController.navigate(NavDestinations.merchantDetail(restaurantId))
                },
                onLogout = {
                    navController.navigate(NavDestinations.LOGIN) {
                        popUpTo(NavDestinations.MAIN) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavDestinations.MERCHANT_DETAIL,
            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
            val activeOrder by orderViewModel.activeOrder.collectAsState()
            val activeOrders by orderViewModel.activeOrders.collectAsState()
            val trackerState by orderViewModel.trackerState.collectAsState()
            val isMerchantCollapsed by orderViewModel.isMerchantCollapsed.collectAsState()
            val isRiderCollapsed by orderViewModel.isRiderCollapsed.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                MerchantDetailScreen(
                    restaurantId = restaurantId,
                    restaurantRepository = restaurantRepository,
                    cartViewModel = cartViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToCart = {
                        mainViewModel.selectTab(AppTab.CART)
                        navController.popBackStack()
                    }
                )

                // Floating Order Tracker over merchant page if active
                if (activeOrder != null) {
                    FloatingOrderTracker(
                        order = activeOrder!!,
                        trackerState = trackerState,
                        isMerchantCollapsed = isMerchantCollapsed,
                        isRiderCollapsed = isRiderCollapsed,
                        onStateChange = { orderViewModel.setTrackerState(it) },
                        onToggleMerchant = { orderViewModel.toggleMerchantCollapsed() },
                        onToggleRider = { orderViewModel.toggleRiderCollapsed() },
                        onDismissOrder = { orderViewModel.dismissActiveOrder() },
                        orderViewModel = orderViewModel,
                        activeOrders = activeOrders,
                        onSelectOrder = { orderViewModel.selectActiveOrder(it) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun MainContainerScreen(
    authViewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    cartViewModel: CartViewModel,
    orderViewModel: OrderViewModel,
    restaurantRepository: RestaurantRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToMerchant: (String) -> Unit,
    onLogout: () -> Unit
) {
    val selectedTab by mainViewModel.selectedTab.collectAsState()
    val cartItems by cartViewModel.cartItems.collectAsState()
    val totalCartCount = cartItems.sumOf { it.quantity }

    val activeOrder by orderViewModel.activeOrder.collectAsState()
    val activeOrders by orderViewModel.activeOrders.collectAsState()
    val trackerState by orderViewModel.trackerState.collectAsState()
    val isMerchantCollapsed by orderViewModel.isMerchantCollapsed.collectAsState()
    val isRiderCollapsed by orderViewModel.isRiderCollapsed.collectAsState()

    Scaffold(
        bottomBar = {
            DelivioBottomNavBar(
                selectedTab = selectedTab,
                cartItemCount = totalCartCount,
                onTabSelected = { mainViewModel.selectTab(it) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Render Selected Screen
            when (selectedTab) {
                AppTab.SHOP -> {
                    ShopScreen(
                        restaurantRepository = restaurantRepository,
                        onNavigateToRestaurant = onNavigateToMerchant
                    )
                }
                AppTab.SEARCH -> {
                    SearchScreen(
                        restaurantRepository = restaurantRepository,
                        cartViewModel = cartViewModel,
                        onNavigateToRestaurant = onNavigateToMerchant
                    )
                }
                AppTab.HOME -> {
                    HomeScreen(
                        restaurantRepository = restaurantRepository,
                        orderRepository = orderViewModel.repository,
                        mainViewModel = mainViewModel,
                        onNavigateToRestaurant = onNavigateToMerchant,
                        onNavigateToSearch = { mainViewModel.selectTab(AppTab.SEARCH) }
                    )
                }
                AppTab.CART -> {
                    CartScreen(
                        cartViewModel = cartViewModel,
                        mainViewModel = mainViewModel,
                        onExploreShop = { mainViewModel.selectTab(AppTab.SHOP) }
                    )
                }
                AppTab.PROFILE -> {
                    ProfileScreen(
                        authViewModel = authViewModel,
                        mainViewModel = mainViewModel,
                        orderViewModel = orderViewModel,
                        cartViewModel = cartViewModel,
                        onLogout = onLogout,
                        onNavigateToCart = { mainViewModel.selectTab(AppTab.CART) }
                    )
                }
            }

            // FLOATING ORDER TRACKER: Positioned above Bottom Navigation Bar on every screen
            if (activeOrder != null) {
                FloatingOrderTracker(
                    order = activeOrder!!,
                    trackerState = trackerState,
                    isMerchantCollapsed = isMerchantCollapsed,
                    isRiderCollapsed = isRiderCollapsed,
                    onStateChange = { orderViewModel.setTrackerState(it) },
                    onToggleMerchant = { orderViewModel.toggleMerchantCollapsed() },
                    onToggleRider = { orderViewModel.toggleRiderCollapsed() },
                    onDismissOrder = { orderViewModel.dismissActiveOrder() },
                    orderViewModel = orderViewModel,
                    activeOrders = activeOrders,
                    onSelectOrder = { orderViewModel.selectActiveOrder(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }
    }
}
