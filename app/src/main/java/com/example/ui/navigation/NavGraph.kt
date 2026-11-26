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
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.example.util.CurrentScreenTracker
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
import com.example.ui.screens.map.MapPickerScreen
import com.example.ui.screens.cart.CartScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.shop.CustomizeScreen
import com.example.ui.screens.shop.MerchantDetailScreen
import com.example.ui.screens.wallet.WalletScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.AppTab
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.InboxViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OrderViewModel
import com.example.viewmodel.SupportViewModel
import com.example.viewmodel.TaxiViewModel
import com.example.viewmodel.WalletViewModel

object NavDestinations {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val CART = "cart"
    const val MERCHANT_DETAIL = "merchant_detail/{restaurantId}"
    const val SERVICE_SHOPS = "service_shops/{serviceType}"
    const val TAXI_RIDE = "taxi_ride?cityId={cityId}"
    const val MAP_PICKER =
        "map_picker?title={title}&stopType={stopType}&stopIndex={stopIndex}&initialAddress={initialAddress}&initialLat={initialLat}&initialLng={initialLng}&cityLat={cityLat}&cityLng={cityLng}&pinColor={pinColor}"
    const val ORDER_CHAT = "order_chat"
    const val JOB_FAIR = "job_fair?cityId={cityId}"
    const val JOB_DETAIL = "job_detail/{jobId}"
    const val MY_APPLICATIONS = "my_applications"
    const val SAVED_JOBS = "saved_jobs"

    fun merchantDetail(restaurantId: String) = "merchant_detail/$restaurantId"
    fun serviceShops(serviceType: String) = "service_shops/$serviceType"
    fun taxiRide(cityId: String = "puerto-princesa") =
        "taxi_ride?cityId=${android.net.Uri.encode(cityId.ifBlank { "puerto-princesa" })}"
    fun jobFair(cityId: String = "puerto-princesa") =
        "job_fair?cityId=${android.net.Uri.encode(cityId.ifBlank { "puerto-princesa" })}"
    fun jobDetail(jobId: String) = "job_detail/${android.net.Uri.encode(jobId)}"

    fun mapPicker(
        title: String = "Select Location",
        stopType: String = "pickup",
        stopIndex: Int = 0,
        initialAddress: String = "",
        initialLat: Double = 9.7392,
        initialLng: Double = 118.7353,
        cityLat: Double = 9.7392,
        cityLng: Double = 118.7353,
        pinColor: String = "yellow"
    ): String {
        val encTitle = android.net.Uri.encode(title.ifBlank { "Select Location" })
        val encStopType = android.net.Uri.encode(stopType.ifBlank { "pickup" })
        val encAddr = android.net.Uri.encode(initialAddress)
        val encPin = android.net.Uri.encode(pinColor.ifBlank { "yellow" })
        return "map_picker?title=$encTitle&stopType=$encStopType&stopIndex=$stopIndex&initialAddress=$encAddr&initialLat=$initialLat&initialLng=$initialLng&cityLat=$cityLat&cityLng=$cityLng&pinColor=$encPin"
    }
}

@Composable
fun DelivioNavGraph(
    authViewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    cartViewModel: CartViewModel,
    orderViewModel: OrderViewModel,
    walletViewModel: WalletViewModel,
    inboxViewModel: InboxViewModel,
    supportViewModel: SupportViewModel,
    chatViewModel: com.example.viewmodel.ChatViewModel,
    restaurantRepository: RestaurantRepository,
    jobFairViewModel: com.example.viewmodel.JobFairViewModel = remember {
        com.example.viewmodel.JobFairViewModel(com.example.data.repository.JobRepository())
    },
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

    // Rating Screen once per order after status "Delivered" or "Completed"
    val pendingReviewOrder by orderViewModel.pendingReviewOrder.collectAsState()
    var isSubmittingReview by remember { mutableStateOf(false) }

    if (pendingReviewOrder != null) {
        val reviewOrder = pendingReviewOrder!!
        val userBalance = authState.currentUser?.walletBalance ?: 0.0
        com.example.ui.components.OrderRatingDialog(
            order = reviewOrder,
            walletBalance = userBalance,
            onDismiss = { orderViewModel.dismissReview(reviewOrder.id) },
            onSubmit = { ratingMerchant, ratingRider, comment, tipAmount ->
                isSubmittingReview = true
                orderViewModel.submitReview(
                    order = reviewOrder,
                    ratingMerchant = ratingMerchant,
                    ratingRider = ratingRider,
                    comment = comment,
                    tipAmount = tipAmount
                ) { success, err ->
                    isSubmittingReview = false
                    if (success) {
                        mainViewModel.showSnackbar("Thank you for your review!")
                    } else if (err != null) {
                        mainViewModel.showSnackbar(err)
                    }
                }
            },
            isSubmitting = isSubmittingReview
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

    // While the app is open, show a small banner when a new admin message arrives
    LaunchedEffect(Unit) {
        supportViewModel.newAdminMessageBanner.collect { bannerMsg ->
            snackbarHostState.showSnackbar(bannerMsg)
        }
    }

    // Collect non-fatal Firestore error banners
    LaunchedEffect(Unit) {
        FirestoreErrorBus.nonFatalBanner.collect { bannerMsg ->
            snackbarHostState.showSnackbar(bannerMsg)
        }
    }

    val startDestination = if (authState.isLoggedIn) NavDestinations.MAIN else NavDestinations.LOGIN
    val customizeState by cartViewModel.customizeState.collectAsState()
    val variationSelectorState by cartViewModel.variationSelectorState.collectAsState()
    val overlayCartItems by cartViewModel.cartItems.collectAsState()
    val platformSettings by cartViewModel.platformSettings.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(NavDestinations.LOGIN) {
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "Login"
                }
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
                    supportViewModel = supportViewModel,
                    restaurantRepository = restaurantRepository,
                    snackbarHostState = snackbarHostState,
                    onNavigateToMerchant = { restaurantId ->
                        navController.navigate(NavDestinations.merchantDetail(restaurantId))
                    },
                    onNavigateToServiceShops = { serviceType ->
                        navController.navigate(NavDestinations.serviceShops(serviceType))
                    },
                    onNavigateToTaxi = { cityId ->
                        navController.navigate(NavDestinations.taxiRide(cityId))
                    },
                    onNavigateToJobFair = { cityId ->
                        navController.navigate(NavDestinations.jobFair(cityId))
                    },
                    onNavigateToMapPicker = {
                        navController.navigate(NavDestinations.mapPicker())
                    },
                    onOpenHomeDeliveryMapPicker = { initAddr, initLat, initLng, cityLat, cityLng ->
                        mainViewModel.openMapPicker(
                            title = "Set Delivery Location",
                            stopType = "home_delivery_address",
                            stopIndex = 0,
                            initialAddress = initAddr,
                            initialLat = initLat,
                            initialLng = initLng,
                            pinColor = YellowPrimary
                        )
                        navController.navigate(
                            NavDestinations.mapPicker(
                                title = "Set Delivery Location",
                                stopType = "home_delivery_address",
                                stopIndex = 0,
                                initialAddress = initAddr,
                                initialLat = initLat,
                                initialLng = initLng,
                                cityLat = cityLat,
                                cityLng = cityLng,
                                pinColor = "yellow"
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenCartDeliveryMapPicker = { initAddr, initLat, initLng, cityLat, cityLng ->
                        mainViewModel.openMapPicker(
                            title = "Set Delivery Location",
                            stopType = "cart_delivery_address",
                            stopIndex = 0,
                            initialAddress = initAddr,
                            initialLat = initLat,
                            initialLng = initLng,
                            pinColor = YellowPrimary
                        )
                        navController.navigate(
                            NavDestinations.mapPicker(
                                title = "Set Delivery Location",
                                stopType = "cart_delivery_address",
                                stopIndex = 0,
                                initialAddress = initAddr,
                                initialLat = initLat,
                                initialLng = initLng,
                                cityLat = cityLat,
                                cityLng = cityLng,
                                pinColor = "yellow"
                            )
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenChat = { args ->
                        chatViewModel.openChat(args)
                        navController.navigate(NavDestinations.ORDER_CHAT)
                    },
                    chatViewModel = chatViewModel,
                    onLogout = {
                        navController.navigate(NavDestinations.LOGIN) {
                            popUpTo(NavDestinations.MAIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavDestinations.CART) {
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "CART"
                }
                val cartItems by cartViewModel.cartItems.collectAsState()
                val totalCartCount = cartItems.sumOf { it.quantity }
                val inboxUnreadCount by inboxViewModel.unreadCount.collectAsState()
                val activeOrder by orderViewModel.activeOrder.collectAsState()
                val activeOrders by orderViewModel.activeOrders.collectAsState()
                val trackerState by orderViewModel.trackerState.collectAsState()
                val isMerchantCollapsed by orderViewModel.isMerchantCollapsed.collectAsState()
                val isRiderCollapsed by orderViewModel.isRiderCollapsed.collectAsState()

                Scaffold(
                    bottomBar = {
                        DelivioBottomNavBar(
                            selectedTab = AppTab.CART,
                            cartItemCount = totalCartCount,
                            inboxUnreadCount = inboxUnreadCount,
                            onTabSelected = { tab ->
                                if (tab != AppTab.CART) {
                                    mainViewModel.selectTab(tab)
                                    navController.popBackStack(NavDestinations.MAIN, inclusive = false)
                                }
                            }
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
                        CartScreen(
                            cartViewModel = cartViewModel,
                            mainViewModel = mainViewModel,
                            authViewModel = authViewModel,
                            onExploreShop = {
                                mainViewModel.selectTab(AppTab.HOME)
                                navController.popBackStack(NavDestinations.MAIN, inclusive = false)
                            },
                            onAddMoreItems = { navController.popBackStack() },
                            onOpenDeliveryMapPicker = { initAddr, initLat, initLng, cityLat, cityLng ->
                                mainViewModel.openMapPicker(
                                    title = "Set Delivery Location",
                                    stopType = "cart_delivery_address",
                                    stopIndex = 0,
                                    initialAddress = initAddr,
                                    initialLat = initLat,
                                    initialLng = initLng,
                                    pinColor = YellowPrimary
                                )
                                navController.navigate(
                                    NavDestinations.mapPicker(
                                        title = "Set Delivery Location",
                                        stopType = "cart_delivery_address",
                                        stopIndex = 0,
                                        initialAddress = initAddr,
                                        initialLat = initLat,
                                        initialLng = initLng,
                                        cityLat = cityLat,
                                        cityLng = cityLng,
                                        pinColor = "yellow"
                                    )
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        )

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
                                chatViewModel = chatViewModel,
                                onOpenChat = { args ->
                                    chatViewModel.openChat(args)
                                    navController.navigate(NavDestinations.ORDER_CHAT)
                                },
                                activeOrders = activeOrders,
                                onSelectOrder = { orderViewModel.selectActiveOrder(it) },
                                onOpenHelp = { order ->
                                    supportViewModel.openHelpForOrder(order) { mainViewModel.showSnackbar(it) }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                val cityId = backStackEntry.arguments?.getString("cityId").orEmpty().ifBlank { "puerto-princesa" }
                LaunchedEffect(cityId) {
                    CurrentScreenTracker.currentScreen = "TaxiRide"
                }

                val taxiViewModel: TaxiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = TaxiViewModel.Factory(cityId)
                )

                val savedStateHandle = backStackEntry.savedStateHandle
                val pickerResultId by savedStateHandle.getStateFlow("map_picker_result_id", 0L).collectAsState()

                LaunchedEffect(pickerResultId) {
                    if (pickerResultId != 0L) {
                        val pickedAddress = savedStateHandle.get<String>("map_picker_address").orEmpty()
                        val pickedLat = savedStateHandle.get<Double>("map_picker_lat") ?: 0.0
                        val pickedLng = savedStateHandle.get<Double>("map_picker_lng") ?: 0.0
                        val pickedStopType = savedStateHandle.get<String>("map_picker_stop_type") ?: "pickup"
                        val pickedStopIndex = savedStateHandle.get<Int>("map_picker_stop_index") ?: 0
                        if (pickedAddress.isNotBlank() || (pickedLat != 0.0 && pickedLng != 0.0)) {
                            taxiViewModel.applyPickedLocation(
                                stopType = pickedStopType,
                                stopIndex = pickedStopIndex,
                                address = pickedAddress,
                                lat = pickedLat,
                                lng = pickedLng
                            )
                        }
                        savedStateHandle.remove<Long>("map_picker_result_id")
                        savedStateHandle.remove<String>("map_picker_address")
                        savedStateHandle.remove<Double>("map_picker_lat")
                        savedStateHandle.remove<Double>("map_picker_lng")
                        savedStateHandle.remove<String>("map_picker_stop_type")
                        savedStateHandle.remove<Int>("map_picker_stop_index")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    com.example.ui.screens.home.ServiceScreenHeader(
                        title = "Taxi Ride",
                        onBack = { navController.popBackStack() }
                    )
                    com.example.ui.screens.home.TaxiBookingView(
                        orderRepository = orderViewModel.repository,
                        cityId = cityId,
                        taxiViewModel = taxiViewModel,
                        authViewModel = authViewModel,
                        mainViewModel = mainViewModel,
                        onOpenMapPicker = { title, stopType, stopIndex, initialAddress, initialLat, initialLng, cityLat, cityLng, pinColor ->
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
                        },
                        onOrderBooked = {
                            mainViewModel.showSnackbar("Taxi ride booked! Searching for nearby driver.")
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            composable(NavDestinations.ORDER_CHAT) {
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "OrderChat"
                }
                com.example.ui.screens.chat.OrderChatScreen(
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
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
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "MapPicker"
                }
                val legacyArgs by mainViewModel.mapPickerArgs.collectAsState()
                val argTitle = backStackEntry.arguments?.getString("title")?.takeIf { it.isNotBlank() }
                val argStopType = backStackEntry.arguments?.getString("stopType")?.takeIf { it.isNotBlank() }
                val argStopIndex = backStackEntry.arguments?.getInt("stopIndex") ?: 0
                val argInitialAddress = backStackEntry.arguments?.getString("initialAddress") ?: ""
                val argInitialLat = backStackEntry.arguments?.getString("initialLat")?.toDoubleOrNull()
                val argInitialLng = backStackEntry.arguments?.getString("initialLng")?.toDoubleOrNull()
                val argCityLat = backStackEntry.arguments?.getString("cityLat")?.toDoubleOrNull() ?: 9.7392
                val argCityLng = backStackEntry.arguments?.getString("cityLng")?.toDoubleOrNull() ?: 118.7353
                val argPinColor = backStackEntry.arguments?.getString("pinColor") ?: "yellow"

                val resolvedTitle = legacyArgs?.title ?: argTitle ?: "Select Location"
                val resolvedStopType = legacyArgs?.stopType ?: argStopType ?: "pickup"
                val resolvedStopIndex = legacyArgs?.stopIndex ?: argStopIndex
                val resolvedInitialAddress = legacyArgs?.initialAddress?.ifBlank { argInitialAddress } ?: argInitialAddress
                val resolvedInitialLat = legacyArgs?.initialLat ?: argInitialLat ?: argCityLat
                val resolvedInitialLng = legacyArgs?.initialLng ?: argInitialLng ?: argCityLng
                val resolvedPinColor = legacyArgs?.pinColor ?: when (argPinColor.lowercase()) {
                    "green" -> SuccessGreen
                    "red" -> ErrorRed
                    else -> YellowPrimary
                }

                MapPickerScreen(
                    title = resolvedTitle,
                    initialAddress = resolvedInitialAddress,
                    initialLat = resolvedInitialLat,
                    initialLng = resolvedInitialLng,
                    cityLat = argCityLat,
                    cityLng = argCityLng,
                    pinColor = resolvedPinColor,
                    onDismiss = {
                        mainViewModel.dismissMapPicker()
                        navController.popBackStack()
                    },
                    onConfirm = { address, lat, lng ->
                        navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                            handle["map_picker_address"] = address
                            handle["map_picker_lat"] = lat
                            handle["map_picker_lng"] = lng
                            handle["map_picker_stop_type"] = resolvedStopType
                            handle["map_picker_stop_index"] = resolvedStopIndex
                            handle["map_picker_result_id"] = System.nanoTime()
                        }
                        mainViewModel.confirmMapPicker(address, lat, lng)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = NavDestinations.SERVICE_SHOPS,
                arguments = listOf(navArgument("serviceType") { type = NavType.StringType })
            ) { backStackEntry ->
                val serviceType = backStackEntry.arguments?.getString("serviceType") ?: "food"
                LaunchedEffect(serviceType) {
                    CurrentScreenTracker.currentScreen = "ServiceShops_$serviceType"
                }
                com.example.ui.screens.shop.ServiceShopsScreen(
                    serviceType = serviceType,
                    restaurantRepository = restaurantRepository,
                    mainViewModel = mainViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToRestaurant = { restaurantId ->
                        navController.navigate(NavDestinations.merchantDetail(restaurantId))
                    }
                )
            }

            composable(
                route = NavDestinations.MERCHANT_DETAIL,
                arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
            ) { backStackEntry ->
                val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                LaunchedEffect(restaurantId) {
                    CurrentScreenTracker.currentScreen = "MerchantDetail"
                }
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
                        mainViewModel = mainViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToCart = {
                            navController.navigate(NavDestinations.CART) {
                                launchSingleTop = true
                            }
                        },
                        onSwitchBranch = { newBranchId ->
                            navController.navigate(NavDestinations.merchantDetail(newBranchId)) {
                                popUpTo(NavDestinations.MERCHANT_DETAIL) { inclusive = true }
                            }
                        }
                    )

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            composable(
                route = NavDestinations.JOB_FAIR,
                arguments = listOf(
                    navArgument("cityId") {
                        type = NavType.StringType
                        defaultValue = "puerto-princesa"
                    }
                )
            ) { backStackEntry ->
                val cityId = backStackEntry.arguments?.getString("cityId").orEmpty().ifBlank { "puerto-princesa" }
                val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
                val resolvedCity = com.example.util.ServiceAvailability.resolveCity(cityId, cities)
                val effectiveCityId = resolvedCity?.id ?: cityId
                val effectiveCityName = resolvedCity?.name ?: cityId
                LaunchedEffect(effectiveCityId) {
                    CurrentScreenTracker.currentScreen = "JobFair"
                }
                com.example.ui.screens.jobs.JobFairScreen(
                    jobFairViewModel = jobFairViewModel,
                    cityId = effectiveCityId,
                    cityName = effectiveCityName,
                    cities = cities,
                    onBack = { navController.popBackStack() },
                    onOpenJobDetail = { jobId ->
                        navController.navigate(NavDestinations.jobDetail(jobId))
                    },
                    onOpenSavedJobs = {
                        navController.navigate(NavDestinations.SAVED_JOBS)
                    },
                    onOpenMyApplications = {
                        navController.navigate(NavDestinations.MY_APPLICATIONS)
                    }
                )
            }

            composable(
                route = NavDestinations.JOB_DETAIL,
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) { backStackEntry ->
                val rawJobId = backStackEntry.arguments?.getString("jobId").orEmpty()
                val jobId = android.net.Uri.decode(rawJobId)
                LaunchedEffect(jobId) {
                    CurrentScreenTracker.currentScreen = "JobDetail"
                }
                com.example.ui.screens.jobs.JobDetailScreen(
                    jobId = jobId,
                    jobFairViewModel = jobFairViewModel,
                    currentUser = authState.currentUser,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavDestinations.MY_APPLICATIONS) {
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "MyApplications"
                }
                com.example.ui.screens.jobs.MyApplicationsScreen(
                    jobFairViewModel = jobFairViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenJobDetail = { jobId ->
                        navController.navigate(NavDestinations.jobDetail(jobId))
                    }
                )
            }

            composable(NavDestinations.SAVED_JOBS) {
                LaunchedEffect(Unit) {
                    CurrentScreenTracker.currentScreen = "SavedJobs"
                }
                com.example.ui.screens.jobs.SavedJobsScreen(
                    jobFairViewModel = jobFairViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenJobDetail = { jobId ->
                        navController.navigate(NavDestinations.jobDetail(jobId))
                    }
                )
            }
        }

        // FULL SCREEN CUSTOMIZE SCREEN FOR PRODUCTS WITH SETS
        if (customizeState != null) {
            val state = customizeState!!
            CustomizeScreen(
                state = state,
                onClose = { cartViewModel.closeCustomize() },
                onAddToCart = { selections, qty ->
                    if (state.editingCartItemId != null) {
                        cartViewModel.updateCartItemSelections(state.editingCartItemId, selections, qty)
                    } else {
                        cartViewModel.addItem(state.item, state.restaurant, qty, selections)
                    }
                    cartViewModel.closeCustomize()
                },
                cartItems = overlayCartItems,
                timezone = platformSettings.timezone,
                onOpenProduct = { recItem ->
                    cartViewModel.openRecommendedProduct(recItem, state.restaurant)
                },
                onQuickAddProduct = { recItem ->
                    cartViewModel.quickAddRecommendedProduct(recItem, state.restaurant)
                }
            )
        }

        // SHARED VARIATION SELECTOR (BOTTOM SHEET OR FULL SCREEN)
        if (variationSelectorState != null) {
            val state = variationSelectorState!!
            val handleVariationConfirm: (com.example.data.model.ItemVariant, Int) -> Unit = { variant, qty ->
                if (state.editingCartItemId != null) {
                    cartViewModel.updateCartItemVariant(state.editingCartItemId, state.item, variant, qty)
                } else {
                    cartViewModel.addVariantItem(state.item, state.restaurant, variant, qty)
                }
                cartViewModel.closeVariationSelector()
            }
            if (state.asBottomSheet) {
                com.example.ui.components.VariationSelectorBottomSheet(
                    state = state,
                    onClose = { cartViewModel.closeVariationSelector() },
                    onConfirm = handleVariationConfirm,
                    cartItems = overlayCartItems,
                    timezone = platformSettings.timezone,
                    onOpenProduct = { recItem ->
                        cartViewModel.openRecommendedProduct(recItem, state.restaurant)
                    },
                    onQuickAddProduct = { recItem ->
                        cartViewModel.quickAddRecommendedProduct(recItem, state.restaurant)
                    }
                )
            } else {
                com.example.ui.components.VariationSelectorFullScreen(
                    state = state,
                    onClose = { cartViewModel.closeVariationSelector() },
                    onConfirm = handleVariationConfirm,
                    cartItems = overlayCartItems,
                    timezone = platformSettings.timezone,
                    onOpenProduct = { recItem ->
                        cartViewModel.openRecommendedProduct(recItem, state.restaurant)
                    },
                    onQuickAddProduct = { recItem ->
                        cartViewModel.quickAddRecommendedProduct(recItem, state.restaurant)
                    }
                )
            }
        }

        // SUPPORT & REPORT OVERLAYS
        val unresolvedTickets by supportViewModel.unresolvedTickets.collectAsState()
        val isChatScreenOpen by supportViewModel.isChatScreenOpen.collectAsState()
        val isReasonDialogOpen by supportViewModel.isReasonDialogOpen.collectAsState()
        val isCreateTicketOpen by supportViewModel.isCreateTicketOpen.collectAsState()
        val isReportsListOpen by supportViewModel.isReportsListOpen.collectAsState()
        val selectedHelpOrder by supportViewModel.selectedHelpOrder.collectAsState()
        val selectedReason by supportViewModel.selectedReason.collectAsState()
        val activeOrderForBubble by orderViewModel.activeOrder.collectAsState()

        // FLOATING CHAT BUBBLE: While any ticket is unresolved, floats on every screen
        if (authState.isLoggedIn && unresolvedTickets.isNotEmpty() && !isChatScreenOpen) {
            com.example.ui.components.FloatingChatBubble(
                unresolvedTickets = unresolvedTickets,
                hasActiveOrderTracker = activeOrderForBubble != null,
                onClick = { ticket ->
                    supportViewModel.openTicketChat(ticket.id)
                }
            )
        }

        // STEP 1: "What went wrong?" Bottom Sheet
        if (isReasonDialogOpen && selectedHelpOrder != null) {
            com.example.ui.components.HelpReasonBottomSheet(
                order = selectedHelpOrder!!,
                onDismiss = { supportViewModel.dismissReasonDialog() },
                onReasonSelected = { reasonKey ->
                    supportViewModel.selectReason(reasonKey)
                }
            )
        }

        // STEP 2: "Tell us your problem" Bottom Sheet
        if (isCreateTicketOpen && selectedHelpOrder != null && selectedReason != null) {
            com.example.ui.components.CreateTicketBottomSheet(
                order = selectedHelpOrder!!,
                reasonKey = selectedReason!!,
                supportViewModel = supportViewModel,
                onDismiss = { supportViewModel.dismissCreateTicket() }
            )
        }

        // REPORTS LIST SCREEN: Under Profile > Help & Reports
        if (isReportsListOpen) {
            com.example.ui.screens.support.SupportReportsListScreen(
                supportViewModel = supportViewModel,
                onBack = { supportViewModel.closeReportsList() }
            )
        }

        // FULL SCREEN SUPPORT CHAT
        if (isChatScreenOpen) {
            com.example.ui.screens.support.SupportChatScreen(
                supportViewModel = supportViewModel,
                onSafeExit = { supportViewModel.closeTicketChat() }
            )
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
    supportViewModel: SupportViewModel,
    chatViewModel: com.example.viewmodel.ChatViewModel,
    restaurantRepository: RestaurantRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToMerchant: (String) -> Unit,
    onNavigateToServiceShops: (String) -> Unit = {},
    onNavigateToTaxi: (String) -> Unit = {},
    onNavigateToJobFair: (String) -> Unit = {},
    onNavigateToMapPicker: () -> Unit = {},
    onOpenHomeDeliveryMapPicker: ((initialAddress: String, initialLat: Double, initialLng: Double, cityLat: Double, cityLng: Double) -> Unit)? = null,
    onOpenCartDeliveryMapPicker: ((initialAddress: String, initialLat: Double, initialLng: Double, cityLat: Double, cityLng: Double) -> Unit)? = null,
    onOpenChat: (com.example.data.model.ChatArgs) -> Unit = {},
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

    LaunchedEffect(selectedTab, isSearchingFromHome) {
        CurrentScreenTracker.currentScreen = if (isSearchingFromHome) "Search" else selectedTab.name
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
                            mainViewModel = mainViewModel,
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
                            cartViewModel = cartViewModel,
                            authViewModel = authViewModel,
                            onNavigateToRestaurant = onNavigateToMerchant,
                            onNavigateToServiceShops = onNavigateToServiceShops,
                            onNavigateToTaxi = onNavigateToTaxi,
                            onNavigateToJobFair = onNavigateToJobFair,
                            onNavigateToSearch = { isSearchingFromHome = true },
                            onNavigateToMapPicker = onNavigateToMapPicker,
                            onOpenDeliveryMapPicker = onOpenHomeDeliveryMapPicker,
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
                        onExploreShop = { mainViewModel.selectTab(AppTab.HOME) },
                        onOpenDeliveryMapPicker = onOpenCartDeliveryMapPicker
                    )
                }
                AppTab.PROFILE -> {
                    isSearchingFromHome = false
                    ProfileScreen(
                        authViewModel = authViewModel,
                        mainViewModel = mainViewModel,
                        orderViewModel = orderViewModel,
                        cartViewModel = cartViewModel,
                        chatViewModel = chatViewModel,
                        onOpenChat = onOpenChat,
                        onLogout = onLogout,
                        onNavigateToCart = { mainViewModel.selectTab(AppTab.CART) },
                        onNavigateToWallet = { mainViewModel.selectTab(AppTab.WALLET) },
                        onOpenHelpReport = { order ->
                            supportViewModel.openHelpForOrder(order) { mainViewModel.showSnackbar(it) }
                        },
                        onNavigateToHelpReports = { supportViewModel.openReportsList() }
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
                    chatViewModel = chatViewModel,
                    onOpenChat = onOpenChat,
                    activeOrders = activeOrders,
                    onSelectOrder = { orderViewModel.selectActiveOrder(it) },
                    onOpenHelp = { order ->
                        supportViewModel.openHelpForOrder(order) { mainViewModel.showSnackbar(it) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
