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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CartItem
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel

@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    mainViewModel: MainViewModel,
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

    val subtotal = cartViewModel.getSubtotal()
    val deliveryFee = cartViewModel.getDeliveryFee()
    val total = cartViewModel.getTotal()
    val freeThreshold = cartViewModel.freeDeliveryThreshold
    val diffToFree = (freeThreshold - subtotal).coerceAtLeast(0.0)

    val coroutineScope = rememberCoroutineScope()
    var isPlacingOrder by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                                Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formatters.formatPrice(subtotal), fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery Fee", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (deliveryFee == 0.0) "FREE" else Formatters.formatPrice(deliveryFee),
                                    color = if (deliveryFee == 0.0) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Service Fee", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Formatters.formatPrice(cartViewModel.serviceFee), fontWeight = FontWeight.SemiBold)
                            }

                            if (promoDiscount > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Promo Discount", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                                    Text("-${Formatters.formatPrice(promoDiscount)}", color = SuccessGreen, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Amount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = Formatters.formatPrice(total),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = YellowPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Store Closed Warning
                            if (currentRestaurant?.isOpen == false) {
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
                                            text = "Store is currently closed (${currentRestaurant?.nextOpenTime}). You cannot place orders right now.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRed
                                        )
                                    }
                                }
                            }

                            // YELLOW "PLACE ORDER" BUTTON
                            val isOrderAllowed = currentRestaurant?.isOpen != false
                            Button(
                                onClick = {
                                    if (isOrderAllowed && !isPlacingOrder) {
                                        isPlacingOrder = true
                                        val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
                                        val customerId = fbUser?.uid ?: ""
                                        val customerName = fbUser?.displayName?.ifEmpty { null } ?: "Delivio Customer"
                                        val customerPhone = fbUser?.phoneNumber?.ifEmpty { null } ?: "+63 917 123 4567"

                                        coroutineScope.launch {
                                            val result = cartViewModel.placeOrder(
                                                address = currentAddress,
                                                customerId = customerId,
                                                customerName = customerName,
                                                customerPhone = customerPhone,
                                                cityId = "puerto-princesa"
                                            )
                                            isPlacingOrder = false
                                            if (result.isSuccess) {
                                                mainViewModel.showSnackbar("Order placed! Tracking your delivery now.")
                                            } else {
                                                val errMsg = result.exceptionOrNull()?.localizedMessage
                                                    ?: result.exceptionOrNull()?.message
                                                    ?: "Failed to place order"
                                                mainViewModel.showSnackbar(errMsg)
                                            }
                                        }
                                    }
                                },
                                enabled = isOrderAllowed && !isPlacingOrder,
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
                                        text = if (isOrderAllowed) "Place Order • ${Formatters.formatPrice(total)}" else "Store Currently Closed",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
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
