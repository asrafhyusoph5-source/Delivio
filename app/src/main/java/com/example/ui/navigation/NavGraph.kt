package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.example.util.FirestoreErrorBus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.shop.MerchantDetailScreen
import com.example.ui.screens.wallet.WalletScreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.AppTab
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.InboxViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OrderViewModel
import com.example.viewmodel.WalletViewModel

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
    walletViewModel: WalletViewModel,
    inboxViewModel: InboxViewModel,
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

    val firestoreError by FirestoreErrorBus.currentError.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Real Firestore Error Dialog with Copy button (never show on Login or Sign Up screens)
    if (firestoreError != null && authState.isLoggedIn) {
        val err = firestoreError!!
        val formattedError = err.displayFormattedText

        AlertDialog(
            onDismissRequest = { FirestoreErrorBus.dismiss() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Firestore Error",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Operation failed. Details below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formattedError,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(formattedError))
                            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_firestore_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = { FirestoreErrorBus.dismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("dismiss_firestore_error_button")
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
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

    // Collect incoming transfer events from walletViewModel
    LaunchedEffect(Unit) {
        walletViewModel.incomingTransferEvents.collect { incomingMsg ->
            snackbarHostState.showSnackbar(incomingMsg)
        }
    }

    // While the app is open, show a small banner "New message: <title>" when a message arrives
    LaunchedEffect(Unit) {
        inboxViewModel.newMessageBanner.collect { bannerMsg ->
            snackbarHostState.showSnackbar(bannerMsg)
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
                walletViewModel = walletViewModel,
                inboxViewModel = inboxViewModel,
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
                        modifier = Modifier.fillMaxSize()
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
    walletViewModel: WalletViewModel,
    inboxViewModel: InboxViewModel,
    restaurantRepository: RestaurantRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToMerchant: (String) -> Unit,
    onLogout: () -> Unit
) {
    val selectedTab by mainViewModel.selectedTab.collectAsState()
    val cartItems by cartViewModel.cartItems.collectAsState()
    val totalCartCount = cartItems.sumOf { it.quantity }
    val inboxUnreadCount by inboxViewModel.unreadCount.collectAsState()
    var isSearchingFromHome by remember { mutableStateOf(false) }

    // When searching from Home, back button returns to Home view
    if (isSearchingFromHome && selectedTab == AppTab.HOME) {
        BackHandler {
            isSearchingFromHome = false
        }
    }

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
                inboxUnreadCount = inboxUnreadCount,
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
            // Render Selected Screen: Tabs are Inbox, Wallet, Home, Cart, Profile
            when (selectedTab) {
                AppTab.INBOX -> {
                    isSearchingFromHome = false
                    InboxScreen(
                        inboxViewModel = inboxViewModel,
                        onNavigateToMerchant = onNavigateToMerchant,
                        onNavigateToCategory = { category ->
                            mainViewModel.selectTab(AppTab.HOME)
                        }
                    )
                }
                AppTab.WALLET -> {
                    isSearchingFromHome = false
                    WalletScreen(
                        walletViewModel = walletViewModel
                    )
                }
                AppTab.HOME -> {
                    if (isSearchingFromHome) {
                        SearchScreen(
                            restaurantRepository = restaurantRepository,
                            cartViewModel = cartViewModel,
                            onNavigateToRestaurant = { restaurantId ->
                                isSearchingFromHome = false
                                onNavigateToMerchant(restaurantId)
                            }
                        )
                    } else {
                        HomeScreen(
                            restaurantRepository = restaurantRepository,
                            orderRepository = orderViewModel.repository,
                            mainViewModel = mainViewModel,
                            authViewModel = authViewModel,
                            onNavigateToRestaurant = onNavigateToMerchant,
                            onNavigateToSearch = { isSearchingFromHome = true },
                            onNavigateToInbox = { mainViewModel.selectTab(AppTab.INBOX) }
                        )
                    }
                }
                AppTab.CART -> {
                    isSearchingFromHome = false
                    CartScreen(
                        cartViewModel = cartViewModel,
                        mainViewModel = mainViewModel,
                        authViewModel = authViewModel,
                        onExploreShop = { mainViewModel.selectTab(AppTab.HOME) }
                    )
                }
                AppTab.PROFILE -> {
                    isSearchingFromHome = false
                    ProfileScreen(
                        authViewModel = authViewModel,
                        mainViewModel = mainViewModel,
                        orderViewModel = orderViewModel,
                        cartViewModel = cartViewModel,
                        onLogout = onLogout,
                        onNavigateToCart = { mainViewModel.selectTab(AppTab.CART) },
                        onNavigateToWallet = { mainViewModel.selectTab(AppTab.WALLET) }
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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
