package com.example.ui.screens.shop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MenuItem
import com.example.data.model.MerchantCategory
import com.example.data.model.MerchantItem
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.DelivioAsyncImage
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StarGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.CartViewModel
import kotlinx.coroutines.launch

@Composable
fun MerchantDetailScreen(
    restaurantId: String,
    restaurantRepository: RestaurantRepository,
    cartViewModel: CartViewModel,
    onBack: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()

    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val restaurant = remember(restaurantId, allRestaurants) {
        allRestaurants.find { it.id == restaurantId }
    }

    // Load merchant items where available == true from merchants/{id}/items
    // Do not depend on categories subcollection documents
    val firestoreItems by restaurantRepository.service.getMerchantItemsFlow(restaurantId)
        .collectAsState(initial = emptyList())

    // Sort in code
    val sortedItems = remember(firestoreItems) {
        firestoreItems.sortedWith(
            compareBy<MerchantItem> { if (it.categoryName.isBlank()) 1 else 0 }
                .thenBy { it.categoryName.lowercase(java.util.Locale.ROOT) }
                .thenBy { it.name.lowercase(java.util.Locale.ROOT) }
        )
    }

    // Group by categoryName; items without a categoryName go under "Other"
    val groupedItems = remember(sortedItems) {
        sortedItems.groupBy { item ->
            if (item.categoryName.isNotBlank()) item.categoryName else "Other"
        }
    }

    var selectedCategory by remember { mutableStateOf("All") }

    val categoryList = remember(groupedItems) {
        if (groupedItems.isEmpty()) emptyList()
        else listOf("All") + groupedItems.keys.toList()
    }

    if (restaurant == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Merchant not found")
        }
        return
    }

    val cartItems by cartViewModel.cartItems.collectAsState()
    val currentRestaurant by cartViewModel.currentRestaurant.collectAsState()

    val isCartFromThisMerchant = currentRestaurant?.id == restaurant.id
    val cartCountForThisMerchant = if (isCartFromThisMerchant) cartItems.sumOf { it.quantity } else 0
    val cartSubtotalForThisMerchant = if (isCartFromThisMerchant) cartViewModel.getSubtotal() else 0.0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (cartCountForThisMerchant > 0) 140.dp else 80.dp)
        ) {
            // Hero Banner
            item {
                val heroImageUrl = remember(restaurant.bannerUrl, restaurant.imageUrl, restaurant.logoUrl) {
                    if (restaurant.bannerUrl.isNotBlank()) restaurant.bannerUrl.trim()
                    else if (restaurant.imageUrl.isNotBlank()) restaurant.imageUrl.trim()
                    else if (restaurant.logoUrl.isNotBlank()) restaurant.logoUrl.trim()
                    else ""
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    if (heroImageUrl.isNotEmpty()) {
                        DelivioAsyncImage(
                            imageUrl = heroImageUrl,
                            contentDescription = restaurant.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            YellowPrimary.copy(alpha = 0.35f),
                                            YellowPrimary.copy(alpha = 0.10f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = restaurant.bannerEmoji,
                                fontSize = 72.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopStart)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .testTag("merchant_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            }

            // Merchant Info Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = restaurant.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f)
                        )

                        // Open / Closed Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (restaurant.isOpen) SuccessGreen else ErrorRed
                        ) {
                            Text(
                                text = if (restaurant.isOpen) "OPEN" else "CLOSED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "${restaurant.rating}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = " (140+ reviews)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text("•", color = MaterialTheme.colorScheme.outline)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeliveryDining, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = restaurant.etaMinutes.toString() + " mins", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Closed warning banner
                    if (!restaurant.isOpen) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ErrorRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "This store is currently closed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ErrorRed
                                    )
                                    Text(
                                        text = "You can view menu items, but orders cannot be placed at this time.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Menu Category Chips derived from items (grouped by categoryName; no categories subcollection dependency)
            if (categoryList.size > 1) {
                item {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(
                            text = "Menu Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryList.forEach { category ->
                                val isSelected = selectedCategory == category
                                val count = if (category == "All") firestoreItems.size else (groupedItems[category]?.size ?: 0)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = category },
                                    label = { Text("$category ($count)", fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = YellowPrimary,
                                        selectedLabelColor = TextOnYellow
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // CHECK: IF MERCHANT HAS NO ITEMS -> "COMING SOON" SCREEN
            if (firestoreItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⏳", fontSize = 48.sp)
                            Text(
                                text = "Menu Coming Soon",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This merchant has not published any available menu items yet. Orders cannot be placed at this time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Menu Items List grouped by categoryName; items without a categoryName go under "Other"
                val groupsToRender = if (selectedCategory == "All") {
                    groupedItems
                } else {
                    mapOf(selectedCategory to (groupedItems[selectedCategory] ?: emptyList()))
                }

                groupsToRender.forEach { (catName, itemsInCat) ->
                    if (itemsInCat.isNotEmpty()) {
                        item(key = "cat_header_$catName") {
                            Text(
                                text = "$catName (${itemsInCat.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }

                        items(itemsInCat, key = { it.id }) { item ->
                            val cartItem = cartItems.find { it.menuItem.id == item.id }
                            val quantityInCart = cartItem?.quantity ?: 0

                            DelivioMenuItemCard(
                                item = item,
                                quantityInCart = quantityInCart,
                                isStoreOpen = restaurant.isOpen,
                                onAdd = {
                                    val menuItem = MenuItem(
                                        id = item.id,
                                        restaurantId = restaurant.id,
                                        name = item.name,
                                        description = item.description,
                                        price = item.price,
                                        category = item.categoryName.ifBlank { "Other" },
                                        emoji = "🍽️",
                                        imageUrl = item.imageUrl
                                    )
                                    cartViewModel.addItem(menuItem, restaurant, 1)
                                },
                                onIncrease = {
                                    cartViewModel.updateQuantity(item.id, quantityInCart + 1)
                                },
                                onDecrease = {
                                    cartViewModel.updateQuantity(item.id, quantityInCart - 1)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // STICKY BOTTOM CART BAR (only when items exist and store is open)
        AnimatedVisibility(
            visible = cartCountForThisMerchant > 0 && restaurant.isOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YellowPrimary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$cartCountForThisMerchant",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextOnYellow,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "View Cart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = Formatters.formatPrice(cartSubtotalForThisMerchant),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = YellowPrimary
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToCart,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Checkout", fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DelivioMenuItemCard(
    item: MerchantItem,
    quantityInCart: Int,
    isStoreOpen: Boolean,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canAdd = isStoreOpen && item.isAvailable

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (!canAdd) Modifier.alpha(0.6f) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Where an imageUrl exists, never show the emoji or gradient placeholder.
            val trimmedItemUrl = remember(item.imageUrl) { item.imageUrl.trim() }
            if (trimmedItemUrl.isNotEmpty()) {
                DelivioAsyncImage(
                    imageUrl = trimmedItemUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!item.isAvailable) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ErrorRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Sold out",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Text(
                    text = Formatters.formatPrice(item.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = YellowPrimary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action: Add button or Stepper
            if (!canAdd) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (!isStoreOpen) "Store closed" else "Sold out",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (quantityInCart == 0) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onDecrease,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "$quantityInCart",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    IconButton(
                        onClick = onIncrease,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextOnYellow, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
