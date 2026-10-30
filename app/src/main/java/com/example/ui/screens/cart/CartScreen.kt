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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
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
    val isScheduled = fulfillment == DelivioStatusConstants.FULFILLMENT_SCHEDULED

    var showCustomTipDialog by remember { mutableStateOf(false) }
    var customTipInput by remember { mutableStateOf("") }
    var customTipError by remember { mutableStateOf<String?>(null) }

    val authUiState by (authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val currentUser = authUiState?.currentUser
    val walletBalance = currentUser?.walletBalance ?: 0.0
    var selectedPaymentMethod by remember { mutableStateOf("cod") } // "cod", "wallet"

    val feesResult by cartViewModel.feesResult.collectAsState()
    val platformSettings by cartViewModel.platformSettings.collectAsState()

    val subtotal = feesResult.subtotal
    val deliveryFee = feesResult.deliveryFee
    val total = feesResult.total
    val freeThreshold = cartViewModel.freeDeliveryThreshold
    val diffToFree = (freeThreshold - subtotal).coerceAtLeast(0.0)

    val coroutineScope = rememberCoroutineScope()
    var isPlacingOrder by remember { mutableStateOf(false) }

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

    val isOrderAllowed = isStoreOpen && (selectedPaymentMethod != "wallet" || walletBalance >= total)
    val isAddressMissing = !isPickup && currentAddress.id == "addr_none"
    val isScheduleMissing = isScheduled && scheduledFor == null
    val missingReason = when {
        !isStoreOpen -> "Store is closed ($nextOpenText)"
        isAddressMissing -> "Set delivery address"
        isScheduleMissing -> "Select delivery date & time"
        selectedPaymentMethod == "wallet" && walletBalance < total -> "Insufficient wallet balance"
        else -> null
    }
    val canPlaceOrder = isOrderAllowed && !isAddressMissing && !isScheduleMissing && !isPlacingOrder

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
                                        val result = cartViewModel.placeOrder(
                                            address = if (isPickup) null else currentAddress,
                                            customerId = customerId,
                                            customerName = customerName,
                                            customerPhone = customerPhone,
                                            cityId = "puerto-princesa",
                                            paymentMethod = selectedPaymentMethod
                                        )
                                        isPlacingOrder = false
                                        if (result.isSuccess) {
                                            mainViewModel.showSnackbar(
                                                if (isPickup) "Order placed for pickup! Track your order now."
                                                else "Order placed! Tracking your delivery now."
                                            )
                                        } else {
                                            val errMsg = result.exceptionOrNull()?.localizedMessage
                                                ?: result.exceptionOrNull()?.message
                                                ?: "Failed to place order"
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
                        text = "From ${currentRestaurant!!.name}",
                        fontSize = 12.sp,
                        color = YellowPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (cartItems.isNotEmpty()) {
                Text(
                    text = "Clear Cart",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { cartViewModel.clearCart() }
                        .padding(4.dp)
                )
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
                                    "Delivery" to DelivioStatusConstants.FULFILLMENT_DELIVERY,
                                    "Schedule Delivery" to DelivioStatusConstants.FULFILLMENT_SCHEDULED
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
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                    if (isScheduled) {
                        item {
                            ScheduleDatePickerCard(
                                scheduledFor = scheduledFor,
                                currentRestaurant = currentRestaurant,
                                onPickTime = {
                                    showScheduleDateTimePicker(
                                        context = context,
                                        restaurant = currentRestaurant,
                                        timezone = platformSettings.timezone,
                                        onSelected = { chosenMillis -> cartViewModel.setScheduledFor(chosenMillis) },
                                        onError = { err -> mainViewModel.showSnackbar(err) }
                                    )
                                }
                            )
                        }
                    } else {
                        // Free Delivery Progress Bar for instant delivery
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
                    }

                    // Delivery Address Card
                    item {
                        DeliveryAddressCard(currentAddress = currentAddress)
                    }
                }

                // Cart Items
                items(cartItems) { item ->
                    CartItemRow(
                        item = item,
                        onIncrease = { cartViewModel.updateQuantity(item.menuItem.id, item.quantity + 1) },
                        onDecrease = { cartViewModel.updateQuantity(item.menuItem.id, item.quantity - 1) },
                        onRemove = { cartViewModel.removeItem(item.menuItem.id) }
                    )
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
                                    onClick = { cartViewModel.applyPromo() },
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
                                            text = "Code '$appliedPromoCode' active (-${Formatters.formatPrice(promoDiscount)})",
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Remove",
                                            color = ErrorRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { cartViewModel.removePromo() }
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

                            if (!isPickup && feesResult.tip > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tip", color = YellowPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(Formatters.formatPrice(feesResult.tip), color = YellowPrimary, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (feesResult.discount > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Discount", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
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
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = "${Formatters.formatPrice(item.menuItem.price)} each",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Formatters.formatPrice(item.totalPrice),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = YellowPrimary
                )
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
                text = restaurant.name,
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
                    pickupName = restaurant.name,
                    pickupAddress = restaurant.address,
                    pickupLat = restaurant.lat,
                    pickupLng = restaurant.lng,
                    dropoffName = restaurant.name,
                    dropoffAddress = restaurant.address,
                    dropoffLat = restaurant.lat,
                    dropoffLng = restaurant.lng,
                    orderStatus = "Pick Up Store",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Button(
                onClick = {
                    val uri = Uri.parse("geo:${restaurant.lat},${restaurant.lng}?q=${Uri.encode("${restaurant.name}, ${restaurant.address}")}")
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

private fun isInsideBusinessHours(restaurant: Restaurant?, calendar: Calendar): Pair<Boolean, String?> {
    val hours = restaurant?.hours ?: return Pair(true, null)
    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "sunday"
        Calendar.MONDAY -> "monday"
        Calendar.TUESDAY -> "tuesday"
        Calendar.WEDNESDAY -> "wednesday"
        Calendar.THURSDAY -> "thursday"
        Calendar.FRIDAY -> "friday"
        Calendar.SATURDAY -> "saturday"
        else -> ""
    }

    val dayData = hours[dayOfWeek] as? Map<*, *>
    val isClosed = (dayData?.get("is_closed") as? Boolean) ?: false
    if (isClosed) {
        return Pair(false, "Merchant is closed on ${dayOfWeek.replaceFirstChar { it.uppercase() }}s.")
    }

    val openStr = (dayData?.get("open") ?: hours["open"]) as? String
    val closeStr = (dayData?.get("close") ?: hours["close"]) as? String
    if (openStr != null && closeStr != null) {
        val openParts = openStr.split(":").mapNotNull { it.toIntOrNull() }
        val closeParts = closeStr.split(":").mapNotNull { it.toIntOrNull() }
        if (openParts.size >= 2 && closeParts.size >= 2) {
            val chosenMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val openMinutes = openParts[0] * 60 + openParts[1]
            val closeMinutes = closeParts[0] * 60 + closeParts[1]
            if (chosenMinutes < openMinutes || chosenMinutes > closeMinutes) {
                return Pair(false, "Scheduled time must be within business hours ($openStr - $closeStr).")
            }
        }
    }
    return Pair(true, null)
}

private fun showScheduleDateTimePicker(
    context: Context,
    restaurant: Restaurant?,
    timezone: String = "Asia/Manila",
    onSelected: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    val minMillis = System.currentTimeMillis() + 45 * 60 * 1000
    val maxMillis = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000

    val datePicker = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val timePicker = android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val chosenCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val chosenMillis = chosenCal.timeInMillis
                    if (chosenMillis < minMillis - 30000) {
                        onError("Scheduled delivery must be at least 45 minutes ahead.")
                        return@TimePickerDialog
                    }
                    if (chosenMillis > maxMillis) {
                        onError("Scheduled delivery must be within 7 days.")
                        return@TimePickerDialog
                    }
                    val (validHours, hoursMsg) = com.example.util.BusinessHoursUtil.isScheduleTimeValid(restaurant, chosenMillis, timezone)
                    if (!validHours) {
                        onError(hoursMsg ?: "Selected time is outside merchant business hours.")
                        return@TimePickerDialog
                    }
                    onSelected(chosenMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY) + 1,
                cal.get(Calendar.MINUTE),
                false
            )
            timePicker.show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    datePicker.datePicker.minDate = System.currentTimeMillis()
    datePicker.datePicker.maxDate = maxMillis
    datePicker.show()
}
