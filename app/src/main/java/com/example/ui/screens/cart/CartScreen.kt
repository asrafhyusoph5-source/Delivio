package com.example.ui.screens.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import com.example.ui.components.DelivioAsyncImage
import com.example.data.model.MenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CartItem
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Address
import com.example.data.model.Restaurant
import com.example.ui.components.OsmOrderTrackingMap
import com.example.util.DelivioStatusConstants
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel

@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel? = null,
    onExploreShop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val currentRestaurant by cartViewModel.currentRestaurant.collectAsState()
    val appliedPromoCode by cartViewModel.appliedPromoCode.collectAsState()
    val promoDiscount by cartViewModel.promoDiscount.collectAsState()
    val deliveryNotes by cartViewModel.deliveryNotes.collectAsState()
    val promoInput by cartViewModel.promoInput.collectAsState()
    val promoMessage by cartViewModel.promoMessage.collectAsState()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val fulfillment by cartViewModel.fulfillment.collectAsState()
    val scheduledFor by cartViewModel.scheduledFor.collectAsState()
    val tipAmount by cartViewModel.tipAmount.collectAsState()
    val context = LocalContext.current

    val isPickup = fulfillment == DelivioStatusConstants.FULFILLMENT_PICKUP
    val isScheduled = scheduledFor != null
    var showSchedulePickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(fulfillment) {
        if (fulfillment == DelivioStatusConstants.FULFILLMENT_SCHEDULED) {
            cartViewModel.setFulfillment(DelivioStatusConstants.FULFILLMENT_DELIVERY)
        }
    }

    var showCustomTipDialog by remember { mutableStateOf(false) }
    var customTipInput by remember { mutableStateOf("") }
    var customTipError by remember { mutableStateOf<String?>(null) }

    val authUiState by (authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val currentUser = authUiState?.currentUser
    val walletBalance = currentUser?.walletBalance ?: 0.0
    var selectedPaymentMethod by remember { mutableStateOf("cod") } // "cod", "wallet"

    val feesResult by cartViewModel.feesResult.collectAsState()
    val platformSettings by cartViewModel.platformSettings.collectAsState()
    val dispatchSettings by cartViewModel.dispatchSettings.collectAsState()
    val selectedDeliveryOption by cartViewModel.selectedDeliveryOption.collectAsState()
    var deliveryOptionInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    val checkoutSettings by cartViewModel.checkoutSettings.collectAsState()
    val shopId = currentRestaurant?.id ?: ""
    val merchantItems by remember(shopId) {
        if (shopId.isNotBlank()) cartViewModel.firestoreService.getMerchantItemsFlow(shopId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val bestSellers by remember(shopId) {
        if (shopId.isNotBlank()) cartViewModel.firestoreService.getMerchantBestSellersFlow(shopId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val shopMenus by remember(shopId) {
        if (shopId.isNotBlank()) cartViewModel.getMerchantMenusFlow(shopId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val shopVouchers by remember(shopId) {
        if (shopId.isNotBlank()) cartViewModel.firestoreService.getShopPublicVouchersFlow(shopId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val eligibleMustTryItems = remember(merchantItems, cartItems, shopId, shopMenus, platformSettings.timezone) {
        if (shopId.isBlank()) emptyList()
        else {
            val cartItemMenuIds = cartItems.map { it.menuItem.id }.toSet()
            val now = System.currentTimeMillis()
            merchantItems.filter { item ->
                !item.hasSets &&
                item.sets.isEmpty() &&
                item.isAvailable &&
                item.inStock &&
                !cartItemMenuIds.contains(item.id) &&
                com.example.util.MenuAvailabilityUtil.isItemOrderable(item, shopMenus, now, platformSettings.timezone)
            }
        }
    }

    val mustTryList = remember(eligibleMustTryItems, bestSellers, checkoutSettings, shopId) {
        if (!checkoutSettings.mustTry.enabled || eligibleMustTryItems.isEmpty()) {
            emptyList()
        } else {
            val eligibleMap = eligibleMustTryItems.associateBy { it.id }.toMutableMap()

            // (1) best sellers from merchants/{shopId}/stats/bestSellers (items [{itemId, qty}], highest qty first)
            val group1 = bestSellers.mapNotNull { stat -> eligibleMap.remove(stat.itemId) }

            // (2) products tagged "Best Seller"
            val group2 = eligibleMap.values
                .filter { item -> item.tags.any { it.equals("Best Seller", ignoreCase = true) } }
                .toList()
            group2.forEach { eligibleMap.remove(it.id) }

            // (3) the remaining eligible products in random order (stable for the session)
            val remaining = eligibleMap.values.toList()
            val seed = (shopId.hashCode().toLong() xor 0x5DEECE66DL)
            val group3 = remaining.shuffled(java.util.Random(seed))

            (group1 + group2 + group3).take(checkoutSettings.mustTry.maxItems.coerceAtLeast(1))
        }
    }

    val subtotal = feesResult.subtotal
    val deliveryFee = feesResult.deliveryFee
    val total = feesResult.total
    val freeThreshold = cartViewModel.freeDeliveryThreshold
    val diffToFree = (freeThreshold - subtotal).coerceAtLeast(0.0)

    val coroutineScope = rememberCoroutineScope()
    var isPlacingOrder by remember { mutableStateOf(false) }
    var showClearCartConfirm by remember { mutableStateOf(false) }

    if (showClearCartConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCartConfirm = false },
            title = {
                Text(
                    text = "Clear Cart?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "This will remove all items from your cart.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCartConfirm = false
                        cartViewModel.clearCart()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearCartConfirm = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (deliveryOptionInfo != null) {
        val (title, infoText) = deliveryOptionInfo!!
        AlertDialog(
            onDismissRequest = { deliveryOptionInfo = null },
            title = {
                Text(title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(infoText, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = { deliveryOptionInfo = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    )
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSchedulePickerSheet) {
        ScheduleDateTimePickerSheet(
            restaurant = currentRestaurant,
            cartItems = cartItems,
            shopMenus = shopMenus,
            timezone = platformSettings.timezone,
            initialScheduledFor = scheduledFor,
            isPickup = isPickup,
            onSelectTime = { chosenMillis ->
                cartViewModel.setScheduledFor(chosenMillis)
            },
            onDismiss = { showSchedulePickerSheet = false },
            onError = { err -> mainViewModel.showSnackbar(err) }
        )
    }

    if (showCustomTipDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomTipDialog = false
                customTipError = null
            },
            title = {
                Text("Enter Tip Amount", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Enter a whole peso amount between ₱1 and ₱500 to tip your rider:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customTipInput,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            customTipInput = digits
                            customTipError = null
                        },
                        prefix = { Text("₱", fontWeight = FontWeight.Bold) },
                        placeholder = { Text("e.g. 50") },
                        singleLine = true,
                        isError = customTipError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_tip_input_field")
                    )
                    if (customTipError != null) {
                        Text(
                            text = customTipError!!,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entered = customTipInput.toIntOrNull()
                        if (entered == null || entered < 1 || entered > 500) {
                            customTipError = "Please enter an amount from ₱1 to ₱500"
                        } else {
                            cartViewModel.setTip(entered.toDouble())
                            showCustomTipDialog = false
                            customTipError = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier.testTag("confirm_custom_tip_button")
                ) {
                    Text("Confirm Tip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomTipDialog = false
                        customTipError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val isStoreOpen = currentRestaurant?.let {
        com.example.util.BusinessHoursUtil.effectiveOpen(it, platformSettings.timezone)
    } ?: true
    val nextOpenText = currentRestaurant?.let {
        com.example.util.BusinessHoursUtil.getNextOpenText(it, platformSettings.timezone)
    } ?: "Opens soon"

    val firstUnavailable = cartItems.firstNotNullOfOrNull { item -> cartViewModel.getFirstUnavailableChoice(item) }
    val hasUnavailableChoices = firstUnavailable != null

    val checkTimeMillis = scheduledFor ?: System.currentTimeMillis()
    val firstUnavailableItem = cartItems.firstOrNull { item ->
        !com.example.util.MenuAvailabilityUtil.isMenuItemOrderable(item.menuItem, shopMenus, checkTimeMillis, platformSettings.timezone)
    }
    val hasUnavailableItems = firstUnavailableItem != null

    val isStoreClosedAndNotScheduled = !isStoreOpen && scheduledFor == null
    val isOrderAllowed = (isStoreOpen || scheduledFor != null) && (selectedPaymentMethod != "wallet" || walletBalance >= total)
    val isAddressMissing = !isPickup && currentAddress.id == "addr_none"
    val isScheduleTimeInvalid = scheduledFor != null && currentRestaurant != null &&
        !com.example.util.BusinessHoursUtil.isScheduleTimeValid(currentRestaurant, scheduledFor!!, platformSettings.timezone).first

    val missingReason = when {
        !isStoreOpen && scheduledFor == null -> "Choose a time"
        isScheduleTimeInvalid -> "Store is closed at scheduled time"
        hasUnavailableItems -> {
            if (scheduledFor != null) "${firstUnavailableItem!!.menuItem.name} is not available at the scheduled time"
            else "${firstUnavailableItem!!.menuItem.name} is not available right now"
        }
        hasUnavailableChoices -> "${firstUnavailable!!.choiceName} is no longer available"
        isAddressMissing -> "Set delivery address"
        selectedPaymentMethod == "wallet" && walletBalance < total -> "Insufficient wallet balance"
        else -> null
    }
    val canPlaceOrder = isOrderAllowed && !hasUnavailableChoices && !hasUnavailableItems && !isAddressMissing && !isStoreClosedAndNotScheduled && !isScheduleTimeInvalid && !isPlacingOrder

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (cartItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Amount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatPrice(total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = YellowPrimary
                                )
                            }

                            if (missingReason != null) {
                                Text(
                                    text = missingReason,
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (canPlaceOrder) {
                                    if (hasUnavailableItems) {
                                        val msg = if (isScheduled) "${firstUnavailableItem!!.menuItem.name} is not available at the scheduled time."
                                                  else "${firstUnavailableItem!!.menuItem.name} is not available right now."
                                        mainViewModel.showSnackbar(msg)
                                        return@Button
                                    }
                                    if (isScheduled && scheduledFor != null) {
                                        val minTime = System.currentTimeMillis() + 45 * 60 * 1000 - 60000
                                        if (scheduledFor!! < minTime) {
                                            mainViewModel.showSnackbar("Scheduled delivery must be at least 45 minutes ahead.")
                                            return@Button
                                        }
                                    }
                                    isPlacingOrder = true
                                    val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
                                    val customerId = fbUser?.uid ?: ""
                                    val customerName = currentUser?.name?.ifBlank { null } ?: fbUser?.displayName?.ifEmpty { null } ?: "Delivio Customer"
                                    val customerPhone = currentUser?.phone?.ifBlank { null } ?: fbUser?.phoneNumber?.ifEmpty { null } ?: ""

                                    coroutineScope.launch {
                                        val serviceName = if (currentRestaurant?.isConvenience == true) "convenience" else "food"
                                        val targetCityId = currentRestaurant?.cityId?.ifEmpty { "puerto-princesa" } ?: "puerto-princesa"
                                        val isServiceActive = cartViewModel.firestoreService.checkServiceOn(serviceName, targetCityId)
                                        if (!isServiceActive) {
                                            isPlacingOrder = false
                                            mainViewModel.showSnackbar("This service is not available in your area.")
                                            return@launch
                                        }

                                        val result = cartViewModel.placeOrder(
                                            address = if (isPickup) null else currentAddress,
                                            customerId = customerId,
                                            customerName = customerName,
                                            customerPhone = customerPhone,
                                            cityId = targetCityId,
                                            paymentMethod = selectedPaymentMethod,
                                            fulfillmentOverride = fulfillment
                                        )
                                        isPlacingOrder = false
                                        if (result.isSuccess) {
                                            mainViewModel.showSnackbar(
                                                if (isPickup) "Order placed for pickup! Track your order now."
                                                else "Order placed! Tracking your delivery now."
                                            )
                                        } else {
                                            val err = result.exceptionOrNull()
                                            val errMsg = if (err?.message?.contains("This code is no longer available", ignoreCase = true) == true) {
                                                "This code is no longer available"
                                            } else if (err?.message?.contains("This service is not available", ignoreCase = true) == true ||
                                                err?.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                                                "This service is not available in your area."
                                            } else if (err?.message?.contains("This store just closed", ignoreCase = true) == true) {
                                                "This store just closed and is not accepting orders."
                                            } else {
                                                err?.localizedMessage ?: err?.message ?: "Failed to place order"
                                            }
                                            mainViewModel.showSnackbar(errMsg)
                                        }
                                    }
                                }
                            },
                            enabled = canPlaceOrder,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowPrimary,
                                contentColor = TextOnYellow,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("place_order_button")
                        ) {
                            if (isPlacingOrder) {
                                CircularProgressIndicator(
                                    color = TextOnYellow,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = if (canPlaceOrder) "Place Order • ${Formatters.formatPrice(total)}" else (missingReason ?: "Place Order"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "My Cart",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                if (currentRestaurant != null) {
                    Text(
                        text = "From ${currentRestaurant!!.displayName}",
                        fontSize = 12.sp,
                        color = YellowPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (cartItems.isNotEmpty()) {
                TextButton(
                    onClick = { showClearCartConfirm = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("clear_cart_button")
                ) {
                    Text(
                        text = "Clear Cart",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (cartItems.isEmpty()) {
            // EMPTY STATE
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(YellowPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = "Empty Cart",
                        tint = YellowPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Your Cart is Empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Explore the delicious menus and find your favorite meal today!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                )

                Button(
                    onClick = onExploreShop,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("explore_restaurants_button")
                ) {
                    Text(
                        text = "Browse Restaurants",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            // CART WITH ITEMS
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. FULFILLMENT SELECTOR: "Pick Up", "Delivery", "Schedule Delivery"
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fulfillment_selector_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "How would you like your order?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val options = listOf(
                                    "Pick Up" to DelivioStatusConstants.FULFILLMENT_PICKUP,
                                    "Delivery" to DelivioStatusConstants.FULFILLMENT_DELIVERY
                                )
                                options.forEach { (label, value) ->
                                    val isSelected = fulfillment == value
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) YellowPrimary else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { cartViewModel.setFulfillment(value) }
                                            .testTag("fulfillment_option_$value")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Order for later row (shown for BOTH Delivery and Pick Up)
                            val isLaterScheduled = scheduledFor != null
                            val scheduleTimeFormatter = remember { SimpleDateFormat("EEE, h:mm a", Locale.US) }
                            val scheduledTimeStr = scheduledFor?.let { scheduleTimeFormatter.format(java.util.Date(it)) }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLaterScheduled) YellowPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(
                                    width = if (isLaterScheduled) 1.5.dp else 1.dp,
                                    color = if (isLaterScheduled) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showSchedulePickerSheet = true }
                                    .testTag("order_for_later_row")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = YellowPrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = YellowPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (isLaterScheduled && scheduledTimeStr != null) {
                                            val scheduleText = if (isPickup) "Pick up at $scheduledTimeStr" else "Delivery at $scheduledTimeStr"
                                            Text(
                                                text = scheduleText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        } else {
                                            val actionText = if (isPickup) "Choose a pick-up time" else "Choose a delivery time"
                                            Text(
                                                text = "Order for later",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = actionText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isLaterScheduled) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Change",
                                                color = YellowPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .clickable { showSchedulePickerSheet = true }
                                                    .testTag("btn_change_schedule")
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                            Text(
                                                text = "Remove",
                                                color = ErrorRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .clickable { cartViewModel.setScheduledFor(null) }
                                                    .testTag("btn_remove_schedule")
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Choose a time",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. FULFILLMENT SPECIFIC CARDS
                if (isPickup) {
                    item {
                        currentRestaurant?.let { restaurant ->
                            MerchantPickupCard(restaurant = restaurant)
                        }
                    }
                } else {
                    // DELIVERY OPTIONS SECTION (For Delivery and Schedule Delivery)
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Delivery options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                            )

                            // Show only enabled options from settings/dispatch
                            val optionKeys = listOf("priority", "standard", "saver")
                            optionKeys.forEach { optKey ->
                                val optConfig = dispatchSettings.options[optKey] ?: when (optKey) {
                                    "priority" -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 20.0, holdMinutes = 0, maxBatch = 1)
                                    "saver" -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = -10.0, holdMinutes = 12, maxBatch = 3)
                                    else -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 0.0, holdMinutes = 5, maxBatch = 2)
                                }

                                if (optConfig.enabled) {
                                    val isSelected = selectedDeliveryOption.equals(optKey, ignoreCase = true)
                                    val etaText = if (scheduledFor != null) {
                                        val sdf = SimpleDateFormat("EEE, h:mm a", Locale.US)
                                        sdf.format(java.util.Date(scheduledFor!!))
                                    } else {
                                        cartViewModel.getOptionEtaText(optKey)
                                    }
                                    val optFee = cartViewModel.getOptionDeliveryFee(optKey)
                                    val holdMinutes = optConfig.holdMinutes

                                    val (titleText, infoExplanation) = when (optKey) {
                                        "priority" -> Pair("Priority ⚡", "Priority = one dedicated rider")
                                        "saver" -> Pair("Saver", "Saver = cheaper, waits up to $holdMinutes minutes to share a rider")
                                        else -> Pair("Standard", "Standard = may be delivered together with another order, waits up to $holdMinutes minutes")
                                    }

                                    val subtitleText = when (optKey) {
                                        "priority" -> "$etaText • With On-Time Promise"
                                        else -> etaText
                                    }

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) YellowPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { cartViewModel.selectDeliveryOption(optKey) }
                                            .testTag("delivery_option_$optKey")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { cartViewModel.selectDeliveryOption(optKey) },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = YellowPrimary
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = titleText,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            deliveryOptionInfo = Pair(titleText, infoExplanation)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = "Option details",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = subtitleText,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = Formatters.formatPrice(optFee),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Free Delivery Progress Bar
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeliveryDining,
                                            contentDescription = null,
                                            tint = if (diffToFree == 0.0) SuccessGreen else YellowPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = if (diffToFree == 0.0) "🎉 You unlocked FREE delivery!"
                                            else "Add ${Formatters.formatPrice(diffToFree)} more for FREE delivery!",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (diffToFree == 0.0) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val progress = (subtotal / freeThreshold).toFloat().coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (diffToFree == 0.0) SuccessGreen else YellowPrimary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                    // Delivery Address Card
                    item {
                        DeliveryAddressCard(currentAddress = currentAddress)
                    }
                }

                // Cart Items
                itemsIndexed(cartItems, key = { idx, item -> "${item.id}_$idx" }) { _, item ->
                    val unavailableChoice = cartViewModel.getFirstUnavailableChoice(item)
                    val isItemUnavailable = !com.example.util.MenuAvailabilityUtil.isMenuItemOrderable(item.menuItem, shopMenus, checkTimeMillis, platformSettings.timezone)
                    val unavailableItemMessage = if (isItemUnavailable) {
                        if (isScheduled) "${item.menuItem.name} is not available at the scheduled time"
                        else "${item.menuItem.name} is not available right now"
                    } else null

                    CartItemRow(
                        item = item,
                        unavailableChoice = unavailableChoice,
                        isItemUnavailable = isItemUnavailable,
                        unavailableItemMessage = unavailableItemMessage,
                        onFix = {
                            currentRestaurant?.let { rest ->
                                cartViewModel.openCustomize(
                                    item = item.menuItem,
                                    restaurant = rest,
                                    initialSelections = item.selections,
                                    initialQuantity = item.quantity,
                                    editingCartItemId = item.id
                                )
                            }
                        },
                        onIncrease = { cartViewModel.updateQuantity(item.id, item.quantity + 1) },
                        onDecrease = { cartViewModel.updateQuantity(item.id, item.quantity - 1) },
                        onRemove = { cartViewModel.removeItem(item.id) }
                    )
                }

                // Add more items button
                item {
                    OutlinedButton(
                        onClick = onExploreShop,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("add_more_items_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = YellowPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add more items",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // "Must try!" Section
                if (mustTryList.isNotEmpty() && checkoutSettings.mustTry.enabled && currentRestaurant != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("must_try_section")
                        ) {
                            Text(
                                text = checkoutSettings.mustTry.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = checkoutSettings.mustTry.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(mustTryList, key = { idx, item -> "${item.id}_$idx" }) { _, mustTryItem ->
                                    MustTryCard(
                                        item = mustTryItem,
                                        onAdd = {
                                            currentRestaurant?.let { rest ->
                                                val menuItem = MenuItem(
                                                    id = mustTryItem.id,
                                                    restaurantId = rest.id,
                                                    name = mustTryItem.name,
                                                    description = mustTryItem.description,
                                                    price = mustTryItem.price,
                                                    category = mustTryItem.categoryName,
                                                    emoji = "🍽️",
                                                    imageUrl = mustTryItem.imageUrl,
                                                    hasSets = false,
                                                    sets = emptyList(),
                                                    menuIds = mustTryItem.menuIds,
                                                    itemSize = mustTryItem.itemSize,
                                                    availability = mustTryItem.availability
                                                )
                                                cartViewModel.addItem(menuItem, rest, 1)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Delivery Notes
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Special Delivery Instructions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = deliveryNotes,
                                onValueChange = { cartViewModel.onNotesChanged(it) },
                                placeholder = { Text("e.g. Extra napkins, gate code #1234, leave at door") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .testTag("delivery_notes_field")
                            )
                        }
                    }
                }

                // Promo Code Box
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Promo Voucher Code",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = promoInput,
                                    onValueChange = { cartViewModel.onPromoInputChanged(it) },
                                    placeholder = { Text("Try SAVE20 or WELCOME") },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("promo_code_input")
                                )

                                Button(
                                    onClick = {
                                        val uid = authViewModel?.uiState?.value?.currentUser?.uid
                                        val sType = if (currentRestaurant?.isConvenience == true) "convenience" else "food"
                                        cartViewModel.applyPromo(uid = uid, serviceType = sType)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("apply_promo_button")
                                ) {
                                    Text("Apply", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (promoMessage != null) {
                                Text(
                                    text = promoMessage!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (appliedPromoCode != null) SuccessGreen else ErrorRed,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            if (shopVouchers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Vouchers available",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(shopVouchers, key = { idx, voucher -> "${voucher.code}_$idx" }) { _, voucher ->
                                        val isApplied = appliedPromoCode.equals(voucher.code, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isApplied) YellowPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isApplied) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.widthIn(min = 150.dp, max = 220.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = voucher.title.ifBlank { voucher.code },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (voucher.condition.isNotBlank()) {
                                                    Text(
                                                        text = voucher.condition,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Button(
                                                    onClick = {
                                                        val uid = authViewModel?.uiState?.value?.currentUser?.uid
                                                        val sType = if (currentRestaurant?.isConvenience == true) "convenience" else "food"
                                                        cartViewModel.applyShopVoucher(voucher, uid = uid, serviceType = sType)
                                                    },
                                                    enabled = !isApplied,
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = YellowPrimary,
                                                        contentColor = TextOnYellow,
                                                        disabledContainerColor = SuccessGreen.copy(alpha = 0.2f),
                                                        disabledContentColor = SuccessGreen
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                    modifier = Modifier
                                                        .height(30.dp)
                                                        .testTag("apply_available_voucher_${voucher.code}")
                                                ) {
                                                    Text(
                                                        text = if (isApplied) "Applied" else "Apply",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (appliedPromoCode != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Voucher ($appliedPromoCode) -${Formatters.formatPrice(promoDiscount)}",
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Remove",
                                            color = ErrorRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { cartViewModel.removePromo() }
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tip Your Rider (Delivery and Schedule Delivery only)
                if (!isPickup) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rider_tip_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolunteerActivism,
                                        contentDescription = null,
                                        tint = YellowPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Tip your rider (optional)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "100% of your tip goes directly to your rider",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isCustom = tipAmount > 0.0 && tipAmount !in listOf(10.0, 30.0, 60.0)

                                    TipOptionChip(
                                        label = "Not now",
                                        isSelected = tipAmount == 0.0,
                                        onClick = { cartViewModel.setTip(0.0) },
                                        modifier = Modifier.weight(1.1f),
                                        tag = "tip_not_now"
                                    )

                                    TipOptionChip(
                                        label = "₱10",
                                        isSelected = tipAmount == 10.0,
                                        onClick = { cartViewModel.setTip(10.0) },
                                        modifier = Modifier.weight(0.9f),
                                        tag = "tip_10"
                                    )

                                    TipOptionChip(
                                        label = "₱30",
                                        isSelected = tipAmount == 30.0,
                                        onClick = { cartViewModel.setTip(30.0) },
                                        modifier = Modifier.weight(0.9f),
                                        tag = "tip_30"
                                    )

                                    TipOptionChip(
                                        label = "₱60",
                                        isSelected = tipAmount == 60.0,
                                        onClick = { cartViewModel.setTip(60.0) },
                                        modifier = Modifier.weight(0.9f),
                                        tag = "tip_60"
                                    )

                                    TipOptionChip(
                                        label = if (isCustom) "₱${tipAmount.toInt()}" else "Enter",
                                        isSelected = isCustom,
                                        onClick = { showCustomTipDialog = true },
                                        modifier = Modifier.weight(1.1f),
                                        tag = "tip_enter_amount"
                                    )
                                }
                            }
                        }
                    }
                }

                // Payment Method Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Payment Method",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // 1. Cash on Delivery / Pay at pickup
                            val isCod = selectedPaymentMethod == "cod"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCod) YellowPrimary.copy(alpha = 0.10f) else Color.Transparent)
                                    .clickable { selectedPaymentMethod = "cod" }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isCod,
                                    onClick = { selectedPaymentMethod = "cod" },
                                    colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPickup) "Pay at pickup" else "Cash on Delivery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isPickup) "Pay cash at the store when you pick up your order" else "Pay cash to rider upon delivery (Default)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 2. Delivio Wallet
                            val isWallet = selectedPaymentMethod == "wallet"
                            val hasEnoughBalance = walletBalance >= total
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isWallet) YellowPrimary.copy(alpha = 0.10f) else Color.Transparent)
                                    .clickable(enabled = hasEnoughBalance) {
                                        selectedPaymentMethod = "wallet"
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isWallet,
                                    onClick = {
                                        if (hasEnoughBalance) selectedPaymentMethod = "wallet"
                                    },
                                    enabled = hasEnoughBalance,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = YellowPrimary,
                                        disabledSelectedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Delivio Wallet",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (hasEnoughBalance) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = Formatters.formatPrice(walletBalance),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (hasEnoughBalance) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!hasEnoughBalance) {
                                        Text(
                                            text = "Insufficient balance (needs ${Formatters.formatPrice(total)})",
                                            fontSize = 12.sp,
                                            color = ErrorRed,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = "Instant 1-tap checkout deducted from your wallet",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                            // 3. Online Payments (Coming soon, disabled)
                            Text(
                                text = "Online Payments",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val comingSoonMethods = listOf(
                                "GCash" to "E-Wallet",
                                "Maya" to "E-Wallet",
                                "Bank Transfer" to "InstaPay / PESONet",
                                "Credit / Debit Card" to "Visa / Mastercard"
                            )

                            comingSoonMethods.forEach { (name, type) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        )
                                        Text(
                                            text = type,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "Coming soon",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bill Summary
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Order Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formatters.formatPrice(feesResult.subtotal), fontWeight = FontWeight.SemiBold)
                            }

                            if (!isPickup) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Delivery fee", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (feesResult.deliveryFee == 0.0) "FREE" else Formatters.formatPrice(feesResult.deliveryFee),
                                        color = if (feesResult.deliveryFee == 0.0) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val sFeeLabel = if (feesResult.serviceFeeType == "percent" && feesResult.serviceFeePercent > 0.0) {
                                    "Service fee (${feesResult.serviceFeePercent.toInt()}%)"
                                } else {
                                    "Service fee"
                                }
                                Text(sFeeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formatters.formatPrice(feesResult.serviceFee), fontWeight = FontWeight.SemiBold)
                            }

                            if (!isPickup) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Tip",
                                        color = if (feesResult.tip > 0.0) YellowPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (feesResult.tip > 0.0) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        Formatters.formatPrice(feesResult.tip),
                                        color = if (feesResult.tip > 0.0) YellowPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (feesResult.discount > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val discountLabel = if (appliedPromoCode != null) "Voucher ($appliedPromoCode)" else "Discount"
                                    Text(discountLabel, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                    Text("-${Formatters.formatPrice(feesResult.discount)}", color = SuccessGreen, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (platformSettings.vat.enabled && feesResult.vat > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val vatLabel = if (feesResult.vatMode == "included") "VAT (included)" else "VAT"
                                    Text(vatLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(Formatters.formatPrice(feesResult.vat), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val totalTitle = if (platformSettings.vat.enabled) "Total (incl. VAT)" else "Total"
                                Text(
                                    text = totalTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = Formatters.formatPrice(feesResult.total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YellowPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment Chip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedPaymentMethod == "wallet") SuccessGreen.copy(alpha = 0.12f) else YellowPrimary.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .testTag("checkout_payment_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (selectedPaymentMethod == "wallet") Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = if (selectedPaymentMethod == "wallet") SuccessGreen else TextOnYellow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (selectedPaymentMethod == "wallet") "Paid with Delivio Wallet"
                                               else if (isPickup) "Pay at pickup - pay ${Formatters.formatPrice(feesResult.total)} at the counter"
                                               else "Cash on Delivery - pay ${Formatters.formatPrice(feesResult.total)} to the rider",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedPaymentMethod == "wallet") SuccessGreen else TextOnYellow
                                    )
                                }
                            }

                            // Store Closed Warning
                            if (!isStoreOpen) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ErrorRed.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .testTag("cart_store_closed_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Closed",
                                            tint = ErrorRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "This store is closed ($nextOpenText). You cannot place orders right now.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRed
                                        )
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
}
}
}

@Composable
private fun CartItemRow(
    item: CartItem,
    unavailableChoice: com.example.data.model.SelectedItemChoice? = null,
    isItemUnavailable: Boolean = false,
    unavailableItemMessage: String? = null,
    onFix: () -> Unit = {},
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isItemUnavailable) BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isItemUnavailable) Modifier.alpha(0.85f) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(YellowPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // Where an imageUrl exists, never show the emoji or gradient placeholder.
                val itemImageUrl = item.menuItem.imageUrl.trim()
                if (itemImageUrl.isNotEmpty()) {
                    com.example.ui.components.DelivioAsyncImage(
                        imageUrl = itemImageUrl,
                        contentDescription = item.menuItem.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(item.menuItem.emoji, fontSize = 28.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.menuItem.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (item.selections.isNotEmpty()) {
                    val selectionsText = item.selections.joinToString(", ") { sel ->
                        if (sel.price > 0.0) "${sel.choiceName} (+${Formatters.formatPrice(sel.price)})"
                        else sel.choiceName
                    }
                    Text(
                        text = selectionsText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                val itemSizeLabel = Formatters.formatItemSize(item.menuItem.itemSize)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${Formatters.formatPrice(item.unitPrice)} each",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (itemSizeLabel.isNotBlank()) {
                        Text(
                            text = itemSizeLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = Formatters.formatPrice(item.totalPrice),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = YellowPrimary
                    )
                    if (itemSizeLabel.isNotBlank()) {
                        Text(
                            text = itemSizeLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (isItemUnavailable) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = unavailableItemMessage ?: "${item.menuItem.name} is not available right now",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Remove",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ErrorRed,
                                modifier = Modifier
                                    .clickable { onRemove() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("remove_unavailable_item_${item.id}")
                            )
                        }
                    }
                }
                if (unavailableChoice != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${unavailableChoice.choiceName} is no longer available",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Fix",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ErrorRed,
                                modifier = Modifier
                                    .clickable { onFix() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Stepper & Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onDecrease,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${item.quantity}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = onIncrease,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = ErrorRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TipOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MerchantPickupCard(
    restaurant: Restaurant,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth().testTag("merchant_pickup_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Pick Up Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = restaurant.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = restaurant.address,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Small map preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                OsmOrderTrackingMap(
                    pickupName = restaurant.displayName,
                    pickupAddress = restaurant.address,
                    pickupLat = restaurant.lat,
                    pickupLng = restaurant.lng,
                    dropoffName = restaurant.displayName,
                    dropoffAddress = restaurant.address,
                    dropoffLat = restaurant.lat,
                    dropoffLng = restaurant.lng,
                    orderStatus = "Pick Up Store",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Button(
                onClick = {
                    val uri = Uri.parse("geo:${restaurant.lat},${restaurant.lng}?q=${Uri.encode("${restaurant.displayName}, ${restaurant.address}")}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No map application available", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("pickup_directions_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Directions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ScheduleDatePickerCard(
    scheduledFor: Long?,
    currentRestaurant: Restaurant?,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = if (scheduledFor != null) {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy 'at' hh:mm a", Locale.US)
        sdf.format(scheduledFor)
    } else {
        null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth().testTag("schedule_date_picker_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Schedule Delivery Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Select a time at least 45 minutes ahead (up to 7 days).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (formattedTime != null) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickTime() }
                    .testTag("pick_schedule_time_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (formattedTime != null) "Scheduled For" else "Choose Date & Time",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (formattedTime != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedTime ?: "Tap to select delivery time",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (formattedTime != null) MaterialTheme.colorScheme.onSurface else YellowPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Pick Date & Time",
                        tint = if (formattedTime != null) SuccessGreen else YellowPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryAddressCard(
    currentAddress: Address,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth().testTag("cart_delivery_address_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(YellowPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Delivery Destination",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentAddress.title.ifBlank { "Home / Pinned Location" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentAddress.fullAddress.ifBlank { "Puerto Princesa City, Palawan" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDateTimePickerSheet(
    restaurant: Restaurant?,
    cartItems: List<CartItem>,
    shopMenus: List<com.example.data.model.MerchantMenu>,
    timezone: String,
    initialScheduledFor: Long?,
    isPickup: Boolean,
    onSelectTime: (Long) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val zoneId = remember(timezone) {
        try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }
    }

    val nowMillis = System.currentTimeMillis()
    val nowZoned = remember(nowMillis, zoneId) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
    }

    var selectedDateOffset by remember { mutableIntStateOf(0) }

    val days = remember(nowZoned) {
        (0..7).map { offset ->
            val date = nowZoned.plusDays(offset.toLong())
            val label = when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> {
                    val dayName = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    val monthName = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    "$dayName, $monthName ${date.dayOfMonth}"
                }
            }
            Triple(offset, label, date)
        }
    }

    // Earliest slot: now + prep time + 30 minutes
    val prepMinutes = restaurant?.prepTimeMinutes ?: 15
    val minSlotMillis = nowMillis + (prepMinutes + 30) * 60 * 1000L

    val selectedDayDate = days.getOrNull(selectedDateOffset)?.third ?: nowZoned
    val timeSlotsForDay = remember(selectedDayDate, minSlotMillis, restaurant) {
        val slots = mutableListOf<Long>()
        val closedUntil = restaurant?.closedUntil

        // Slots every 15 minutes from 00:00 to 23:45
        for (h in 0..23) {
            for (m in listOf(0, 15, 30, 45)) {
                val slotZoned = selectedDayDate.withHour(h).withMinute(m).withSecond(0).withNano(0)
                val slotMillis = slotZoned.toInstant().toEpochMilli()

                // Check earliest slot threshold
                if (slotMillis < minSlotMillis) continue

                // Check manually closed threshold
                if (closedUntil != null && slotMillis < closedUntil) continue

                // Check business hours
                val (isValidHours, _) = com.example.util.BusinessHoursUtil.isScheduleTimeValid(restaurant, slotMillis, timezone)
                if (!isValidHours) continue

                slots.add(slotMillis)
            }
        }
        slots
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order for later",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPickup) "Choose a pick-up time" else "Choose a delivery time",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Date",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date row (today to 7 days ahead)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(days) { (offset, label, _) ->
                    val isSelected = selectedDateOffset == offset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { selectedDateOffset = offset }
                            .testTag("date_tab_$offset")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Available Times",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (timeSlotsForDay.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No available times for this date.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.US) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(timeSlotsForDay) { slotMillis ->
                        val slotText = timeFormatter.format(java.util.Date(slotMillis))
                        val isCurrentSelected = initialScheduledFor == slotMillis

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrentSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentSelected) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val unavailableItem = cartItems.firstOrNull {
                                        !com.example.util.MenuAvailabilityUtil.isMenuItemOrderable(it.menuItem, shopMenus, slotMillis, timezone)
                                    }
                                    if (unavailableItem != null) {
                                        val label = com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(unavailableItem.menuItem, shopMenus, timezone)
                                        onError("${unavailableItem.menuItem.name} is not available at this time ($label).")
                                        return@clickable
                                    }
                                    onSelectTime(slotMillis)
                                    onDismiss()
                                }
                                .testTag("time_slot_$slotText")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = slotText,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrentSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MustTryCard(
    item: com.example.data.model.MerchantItem,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .width(136.dp)
            .testTag("must_try_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.imageUrl.isNotBlank()) {
                    DelivioAsyncImage(
                        imageUrl = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("🍽️", fontSize = 28.sp)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = YellowPrimary,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clickable { onAdd() }
                        .testTag("must_try_add_${item.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add ${item.name}",
                            tint = TextOnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "₱ ${String.format(java.util.Locale.US, "%.2f", item.price)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val sizeLabel = Formatters.formatItemSize(item.itemSize)
                    if (sizeLabel.isNotBlank()) {
                        Text(
                            text = sizeLabel,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
