package com.example.ui.screens.shop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.theme.TextDark
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MenuItem
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantSubcategory
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

private data class ShopSubcategorySection(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val items: List<MerchantItem>
)

@OptIn(ExperimentalFoundationApi::class)
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
    val lazyListState = rememberLazyListState()
    val tabScrollState = rememberScrollState()
    val tabPositions = remember { mutableStateMapOf<String, Int>() }

    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val restaurant = remember(restaurantId, allRestaurants) {
        allRestaurants.find { it.id == restaurantId }
    }

    // Load subcategories from merchants/{id}/subcategories (sorted by sortOrder)
    val firestoreSubcategories by restaurantRepository.service.getMerchantSubcategoriesFlow(restaurantId)
        .collectAsState(initial = emptyList())

    // Load merchant items where available == true from merchants/{id}/items
    val firestoreItems by restaurantRepository.service.getMerchantItemsFlow(restaurantId)
        .collectAsState(initial = emptyList())

    val platformSettings by cartViewModel.platformSettings.collectAsState()

    // Read merchants/{shopId}/menus live
    val firestoreMenus by restaurantRepository.getMerchantMenusFlow(restaurantId)
        .collectAsState(initial = emptyList())

    val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
    val userFavorites by restaurantRepository.getFavoritesFlow(currentUid).collectAsState(initial = emptySet())
    val isFavorite = userFavorites.contains(restaurantId)

    val shopVouchers by restaurantRepository.service.getShopPublicVouchersFlow(restaurantId)
        .collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Filter items by search query
    val filteredItems = remember(firestoreItems, searchQuery) {
        if (searchQuery.isBlank()) {
            firestoreItems
        } else {
            val q = searchQuery.trim()
            firestoreItems.filter { item ->
                item.name.contains(q, ignoreCase = true) ||
                item.description.contains(q, ignoreCase = true) ||
                item.subcategoryName.contains(q, ignoreCase = true) ||
                item.categoryName.contains(q, ignoreCase = true)
            }
        }
    }

    // Group items into subcategories (Tabs come from merchants/{id}/subcategories sorted by sortOrder).
    // Only subcategories with at least one available item get a tab.
    // Items with no subcategory form a final section and tab "Other".
    val sections = remember(firestoreSubcategories, filteredItems) {
        val result = mutableListOf<ShopSubcategorySection>()
        val assignedItemIds = mutableSetOf<String>()

        if (firestoreSubcategories.isNotEmpty()) {
            // Match against defined subcategories
            firestoreSubcategories.sortedBy { it.sortOrder }.forEach { subcat ->
                val matchingItems = filteredItems.filter { item ->
                    !assignedItemIds.contains(item.id) && (
                        (item.subcategoryId.isNotBlank() && item.subcategoryId.equals(subcat.id, ignoreCase = true)) ||
                        (item.subcategoryId.isNotBlank() && item.subcategoryId.equals(subcat.name, ignoreCase = true)) ||
                        (item.subcategoryName.isNotBlank() && item.subcategoryName.equals(subcat.name, ignoreCase = true)) ||
                        (item.categoryName.isNotBlank() && item.categoryName.equals(subcat.name, ignoreCase = true))
                    )
                }.distinctBy { it.id }
                // Only subcategories with at least one available item get a tab
                val availableInSubcat = matchingItems.filter { it.isAvailable }
                if (availableInSubcat.isNotEmpty()) {
                    result.add(
                        ShopSubcategorySection(
                            id = subcat.id,
                            name = subcat.name,
                            sortOrder = subcat.sortOrder,
                            items = matchingItems
                        )
                    )
                    matchingItems.forEach { assignedItemIds.add(it.id) }
                }
            }

            // Items with no matching subcategory form a final section and tab "Other"
            val unassigned = filteredItems.filter { !assignedItemIds.contains(it.id) }.distinctBy { it.id }
            val availableUnassigned = unassigned.filter { it.isAvailable }
            if (availableUnassigned.isNotEmpty()) {
                result.add(
                    ShopSubcategorySection(
                        id = "other_section",
                        name = "Other",
                        sortOrder = 9999,
                        items = unassigned
                    )
                )
            }
        } else {
            // Fallback if no subcategories collection exists in Firestore:
            // Derive sections from distinct categoryName/subcategoryName
            val grouped = filteredItems.groupBy { item ->
                if (item.subcategoryName.isNotBlank()) item.subcategoryName
                else if (item.categoryName.isNotBlank()) item.categoryName
                else "Other"
            }
            val (otherGroup, standardGroups) = grouped.entries.partition { it.key.equals("Other", ignoreCase = true) }
            var order = 0
            standardGroups.sortedBy { it.key.lowercase(java.util.Locale.ROOT) }.forEach { (name, items) ->
                val available = items.filter { it.isAvailable }
                if (available.isNotEmpty()) {
                    result.add(
                        ShopSubcategorySection(
                            id = "derived_$name",
                            name = name,
                            sortOrder = order++,
                            items = items
                        )
                    )
                }
            }
            if (otherGroup.isNotEmpty()) {
                val items = otherGroup.first().value
                val available = items.filter { it.isAvailable }
                if (available.isNotEmpty()) {
                    result.add(
                        ShopSubcategorySection(
                            id = "other_section",
                            name = "Other",
                            sortOrder = 9999,
                            items = items
                        )
                    )
                }
            }
        }
        result
    }

    // Map section start index in LazyColumn
    val sectionHeaderIndices = remember(sections) {
        var currentIndex = 1 // 0 is shop_header
        if (sections.size > 1) currentIndex++ // 1 is stickyHeader
        val map = mutableMapOf<String, Int>()
        sections.forEach { section ->
            map[section.id] = currentIndex
            currentIndex += 1 + section.items.size // 1 for header + items
        }
        map
    }

    // Determine current active section while scrolling
    val activeSectionId by remember(sections, sectionHeaderIndices) {
        derivedStateOf {
            val firstVisible = lazyListState.firstVisibleItemIndex
            var currentId = sections.firstOrNull()?.id ?: ""
            for (section in sections) {
                val idx = sectionHeaderIndices[section.id] ?: 0
                if (firstVisible >= idx) {
                    currentId = section.id
                } else {
                    break
                }
            }
            currentId
        }
    }

    // Scrolling highlights matching tab and keeps it in view (using tabScrollState.animateScrollTo)
    LaunchedEffect(activeSectionId) {
        val targetX = tabPositions[activeSectionId]
        if (targetX != null) {
            tabScrollState.animateScrollTo((targetX - 70).coerceAtLeast(0))
        }
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
    val isStoreOpen = com.example.util.BusinessHoursUtil.effectiveOpen(restaurant)
    var showStoreClosedDialog by rememberSaveable(restaurant.id) {
        mutableStateOf(!isStoreOpen)
    }
    var allowOrderForLater by rememberSaveable(restaurant.id) {
        mutableStateOf(false)
    }
    val canOrderFromStore = isStoreOpen || allowOrderForLater

    if (!isStoreOpen && showStoreClosedDialog) {
        AlertDialog(
            onDismissRequest = {
                showStoreClosedDialog = false
                onBack()
            },
            title = {
                Text(text = "Store is closed", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "This store is closed right now, but you can still order for later. Choose a delivery or pick-up time to continue.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        allowOrderForLater = true
                        showStoreClosedDialog = false
                    },
                    modifier = Modifier.testTag("btn_store_closed_okay")
                ) {
                    Text("Okay", fontWeight = FontWeight.Bold, color = YellowPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStoreClosedDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("btn_store_closed_cancel")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. PINNED TOP BAR: Back arrow, search field "Search <shop name>", overflow menu
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("merchant_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Search field "Search <shop name>"
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search ${restaurant.name}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(YellowPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Heart Favorite button
                    IconButton(
                        onClick = {
                            if (currentUid.isNotEmpty()) {
                                scope.launch {
                                    restaurantRepository.toggleFavorite(currentUid, restaurantId)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("shop_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color(0xFFFF3B5C) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Overflow menu
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = !showOverflowMenu },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Store Details") },
                                onClick = {
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Store") },
                                onClick = {
                                    showOverflowMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 2. SINGLE SCROLL CONTAINER (LazyColumn with sticky header)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = if (cartCountForThisMerchant > 0) 140.dp else 80.dp)
            ) {
                // SHOP HEADER: Image, name, rating, ETA, Open/Closed badge
                item(key = "shop_header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val heroImageUrl = remember(restaurant.bannerUrl, restaurant.imageUrl, restaurant.logoUrl) {
                            if (restaurant.bannerUrl.isNotBlank()) restaurant.bannerUrl.trim()
                            else if (restaurant.imageUrl.isNotBlank()) restaurant.imageUrl.trim()
                            else if (restaurant.logoUrl.isNotBlank()) restaurant.logoUrl.trim()
                            else ""
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
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
                                        fontSize = 64.sp
                                    )
                                }
                            }
                        }

                        // Merchant Info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
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

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isStoreOpen) SuccessGreen else ErrorRed
                                ) {
                                    Text(
                                        text = if (isStoreOpen) "OPEN" else "CLOSED",
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

                            if (!isStoreOpen) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.testTag("chip_closed_order_later")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Closed now - order for later",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (shopVouchers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Vouchers",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(shopVouchers, key = { idx, voucher -> "${voucher.code}_$idx" }) { _, voucher ->
                                        val appliedPromoCode by cartViewModel.appliedPromoCode.collectAsState()
                                        val isApplied = appliedPromoCode.equals(voucher.code, ignoreCase = true)

                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isApplied) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                            modifier = Modifier.widthIn(min = 160.dp, max = 220.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = voucher.title.ifBlank { voucher.code },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = voucher.condition.ifBlank {
                                                        if (voucher.minSubtotal > 0.0) "Min. spend ₱${voucher.minSubtotal.toInt()}" else "No min. spend"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Button(
                                                    onClick = {
                                                        val uid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                                                        val sType = if (restaurant.isConvenience) "convenience" else "food"
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
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier
                                                        .height(32.dp)
                                                        .testTag("use_voucher_${voucher.code}")
                                                ) {
                                                    Text(
                                                        text = if (isApplied) "Applied" else "Use",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
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

                // 3. STICKY HORIZONTAL TAB BAR PINNED UNDER SEARCH BAR WHILE SCROLLING
                // With only one section, hide the tab bar
                if (sections.size > 1) {
                    stickyHeader(key = "sticky_subcategory_tabs") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tabScrollState)
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                sections.forEach { section ->
                                    val isActive = activeSectionId == section.id

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                tabPositions[section.id] = coords.positionInParent().x.toInt()
                                            }
                                            .clickable {
                                                scope.launch {
                                                    val targetIdx = sectionHeaderIndices[section.id] ?: 0
                                                    lazyListState.animateScrollToItem(targetIdx)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .testTag("tab_${section.id}")
                                    ) {
                                        Text(
                                            text = section.name,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (isActive) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Dark underline for active tab
                                        Box(
                                            modifier = Modifier
                                                .width(if (isActive) 32.dp else 0.dp)
                                                .height(3.dp)
                                                .background(
                                                    if (isActive) Color(0xFF1E1E1E) else Color.Transparent,
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. MENU SECTIONS & ROWS
                if (firestoreItems.isEmpty()) {
                    item(key = "coming_soon_card") {
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
                                    text = "This merchant has not published any available menu items yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (sections.isEmpty() && searchQuery.isNotEmpty()) {
                    item(key = "no_search_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No items matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    sections.forEach { section ->
                        // Class name as a large bold header
                        item(key = "heading_${section.id}") {
                            Text(
                                text = section.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp)
                            )
                        }

                        // Rows for this section
                        itemsIndexed(section.items, key = { idx, item -> "${section.id}_${item.id}_$idx" }) { _, item ->
                            val cartItem = cartItems.find { it.menuItem.id == item.id }
                            val quantityInCart = cartItem?.quantity ?: 0

                            val menuItem = MenuItem(
                                id = item.id,
                                restaurantId = restaurant.id,
                                name = item.name,
                                description = item.description,
                                price = item.price,
                                category = item.categoryName.ifBlank { section.name },
                                emoji = "🍽️",
                                imageUrl = item.imageUrl,
                                hasSets = item.hasSets,
                                sets = item.sets,
                                menuIds = item.menuIds
                            )

                            val isOrderableNow = com.example.util.MenuAvailabilityUtil.isItemOrderable(
                                item = item,
                                menus = firestoreMenus,
                                targetMillis = System.currentTimeMillis(),
                                timezone = platformSettings.timezone
                            )
                            val availabilityLabel = if (!isOrderableNow && item.inStock && item.isAvailable) {
                                com.example.util.MenuAvailabilityUtil.getAvailabilityLabel(item, firestoreMenus)
                            } else null

                            ShopItemRow(
                                item = item,
                                quantityInCart = quantityInCart,
                                isStoreOpen = canOrderFromStore,
                                isOrderableNow = isOrderableNow,
                                availabilityLabel = availabilityLabel,
                                onAdd = {
                                    if (item.hasSets) {
                                        cartViewModel.openCustomize(menuItem, restaurant)
                                    } else {
                                        cartViewModel.addItem(menuItem, restaurant, 1)
                                    }
                                },
                                onDecrease = {
                                    cartViewModel.updateQuantity(item.id, (quantityInCart - 1).coerceAtLeast(0))
                                },
                                onCustomize = {
                                    cartViewModel.openCustomize(menuItem, restaurant)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Thin horizontal divider between rows
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. FLOATING "VIEW YOUR CART" PILL IN BRAND YELLOW AT THE BOTTOM
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        AnimatedVisibility(
            visible = cartCountForThisMerchant > 0 && canOrderFromStore,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = YellowPrimary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onNavigateToCart()
                    }
                    .testTag("floating_view_cart_pill")
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Count circle on the left
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF212121),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$cartCountForThisMerchant",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // "View your cart" with shop name under it
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "View your cart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextOnYellow
                            )
                            Text(
                                text = restaurant.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextOnYellow.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Total on the right
                    Text(
                        text = Formatters.formatPrice(cartSubtotalForThisMerchant),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = TextOnYellow,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Item row layout matching specifications:
 * - Name in bold (2 lines at most)
 * - Price ("₱182.00", or "from ₱251.00" when required options change the price)
 * - Short grey description (2 lines)
 * - Small tags ("Popular" flame for best sellers, and rating percentage only if real data exists)
 * - Image on the right (rounded 16 dp, about 104 dp) with a round "+" button at the bottom-right corner.
 *   Without an image, the "+" sits on the right.
 * - Items with sets open Customize; items without sets are added directly
 *   (and when quantity in cart > 0, the "+" becomes a stepper).
 */
@Composable
private fun ShopItemRow(
    item: MerchantItem,
    quantityInCart: Int,
    isStoreOpen: Boolean,
    isOrderableNow: Boolean = true,
    availabilityLabel: String? = null,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSoldOut = !item.isAvailable || !item.inStock
    val canAdd = isStoreOpen && isOrderableNow && !isSoldOut
    val hasImage = item.imageUrl.isNotBlank()

    Row(
        modifier = modifier
            .clickable(enabled = canAdd) {
                if (item.hasSets) onCustomize() else onAdd()
            }
            .then(if (!canAdd) Modifier.alpha(0.55f) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Item Name, Price, Description, Small Tags
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSoldOut) {
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

            // Available label when not orderable now (e.g. "Available 6:00 AM - 10:00 AM")
            if (!isSoldOut && !isOrderableNow && !availabilityLabel.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = availabilityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Price ("₱182.00", or "from ₱251.00" when required options change the price)
            val hasRequiredAddons = item.hasSets && (
                item.sets.any { it.required && it.cheapestChoicePrice > 0.0 } || item.fromPrice > item.price
            )
            val priceDisplay = if (hasRequiredAddons) {
                "from ${Formatters.formatPrice(item.fromPrice)}"
            } else {
                Formatters.formatPrice(item.price)
            }

            Text(
                text = priceDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Small tags ("Popular" flame for best sellers, and rating percentage only if real data exists)
            val isPopular = item.isPopular || item.tags.any {
                it.contains("popular", ignoreCase = true) || it.contains("best", ignoreCase = true)
            }
            val ratingPct = item.ratingPercentage ?: item.rating?.let { (it * 20).toInt() }

            if (isPopular || (ratingPct != null && ratingPct > 0)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    if (isPopular) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFECE0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_flame),
                                contentDescription = "Popular",
                                tint = Color(0xFFFF5722),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Popular",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5722)
                            )
                        }
                    }

                    if (ratingPct != null && ratingPct > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF0FDF4))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "$ratingPct%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        }

        // Right side: Image on the right (rounded 16 dp, about 104 dp) with a round "+" button at the bottom-right corner.
        // Without an image, the "+" sits on the right.
        if (hasImage) {
            Box(
                modifier = Modifier
                    .size(104.dp)
            ) {
                DelivioAsyncImage(
                    imageUrl = item.imageUrl.trim(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )

                // Round "+" button or stepper at bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    ItemActionWidget(
                        canAdd = canAdd,
                        isStoreOpen = isStoreOpen,
                        isSoldOut = isSoldOut,
                        isOrderableNow = isOrderableNow,
                        hasSets = item.hasSets,
                        quantityInCart = quantityInCart,
                        onAdd = {
                            if (item.hasSets) onCustomize() else onAdd()
                        },
                        onDecrease = onDecrease,
                        onCustomize = onCustomize
                    )
                }
            }
        } else {
            // Rows without image have the button / stepper at the right end
            ItemActionWidget(
                canAdd = canAdd,
                isStoreOpen = isStoreOpen,
                isSoldOut = isSoldOut,
                isOrderableNow = isOrderableNow,
                hasSets = item.hasSets,
                quantityInCart = quantityInCart,
                onAdd = {
                    if (item.hasSets) onCustomize() else onAdd()
                },
                onDecrease = onDecrease,
                onCustomize = onCustomize
            )
        }
    }
}

@Composable
private fun ItemActionWidget(
    canAdd: Boolean,
    isStoreOpen: Boolean,
    isSoldOut: Boolean,
    isOrderableNow: Boolean = true,
    hasSets: Boolean,
    quantityInCart: Int,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isStoreOpen) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            Text(
                text = "Closed",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    } else if (isSoldOut) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            Text(
                text = "Sold out",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    } else if (!isOrderableNow) {
        IconButton(
            onClick = {},
            enabled = false,
            modifier = modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .testTag("add_item_button_disabled")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Not available right now",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    } else if (!hasSets && quantityInCart > 0) {
        // Items without sets: when quantity in cart > 0, the "+" becomes a stepper
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, YellowPrimary.copy(alpha = 0.6f)),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .testTag("item_stepper_minus")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "$quantityInCart",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(YellowPrimary)
                        .testTag("item_stepper_plus")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = TextOnYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    } else {
        // Round "+" button
        IconButton(
            onClick = {
                if (hasSets) onCustomize() else onAdd()
            },
            modifier = modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(YellowPrimary)
                .testTag("add_item_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (hasSets) "Customize" else "Add",
                tint = TextOnYellow,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
