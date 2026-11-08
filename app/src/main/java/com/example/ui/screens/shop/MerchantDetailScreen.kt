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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.theme.TextDark
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
                    (item.subcategoryId.isNotBlank() && item.subcategoryId.equals(subcat.id, ignoreCase = true)) ||
                    (item.subcategoryId.isNotBlank() && item.subcategoryId.equals(subcat.name, ignoreCase = true)) ||
                    (item.subcategoryName.isNotBlank() && item.subcategoryName.equals(subcat.name, ignoreCase = true)) ||
                    (item.categoryName.isNotBlank() && item.categoryName.equals(subcat.name, ignoreCase = true))
                }
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
            val unassigned = filteredItems.filter { !assignedItemIds.contains(it.id) }
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
                        // Bold heading: Subcategory Name
                        item(key = "heading_${section.id}") {
                            Text(
                                text = section.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                            )
                        }

                        // Rows for this section
                        items(section.items, key = { it.id }) { item ->
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

        // 5. TIDY STICKY BOTTOM CART BAR (only when items exist and store is open or order for later accepted)
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YellowPrimary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$cartCountForThisMerchant",
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnYellow,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "View Cart",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Formatters.formatPrice(cartSubtotalForThisMerchant),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextDark
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onNavigateToCart()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sticky_cart_checkout_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Checkout", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = "Checkout",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item row layout matching the reference photo specifications:
 * - Item name (bold)
 * - Price "₱ 120.00"
 * - Two-line grey description
 * - Optional rounded image on the right with a round "+" button at its bottom-right corner;
 *   rows without image have round "+" at the right end.
 * - Items with sets open Customize.
 * - Sold-out items are greyed with "Sold out".
 * - Closed shop items stay visible and "+" is disabled with "Closed".
 */
@Composable
private fun ShopItemRow(
    item: MerchantItem,
    quantityInCart: Int,
    isStoreOpen: Boolean,
    isOrderableNow: Boolean = true,
    availabilityLabel: String? = null,
    onAdd: () -> Unit,
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
        // Left Column: Item Name, Badges (Sold out / Available <days and time>), Price, Description
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
                    color = MaterialTheme.colorScheme.onSurface
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

            val priceDisplay = if (item.hasSets) {
                "From ${Formatters.formatPrice(item.fromPrice)}"
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
        }

        // Right side: Optional rounded image with round "+" at bottom-right corner,
        // or round "+" at the right end when there is no image.
        if (hasImage) {
            Box(
                modifier = Modifier
                    .size(80.dp)
            ) {
                DelivioAsyncImage(
                    imageUrl = item.imageUrl.trim(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )

                // Round "+" button or closed/disabled badge at the bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                ) {
                    ItemActionButton(
                        canAdd = canAdd,
                        isStoreOpen = isStoreOpen,
                        isSoldOut = isSoldOut,
                        isOrderableNow = isOrderableNow,
                        quantityInCart = quantityInCart,
                        hasSets = item.hasSets,
                        onClick = {
                            if (item.hasSets) onCustomize() else onAdd()
                        }
                    )
                }
            }
        } else {
            // Rows without image have round "+" at the right end
            ItemActionButton(
                canAdd = canAdd,
                isStoreOpen = isStoreOpen,
                isSoldOut = isSoldOut,
                isOrderableNow = isOrderableNow,
                quantityInCart = quantityInCart,
                hasSets = item.hasSets,
                onClick = {
                    if (item.hasSets) onCustomize() else onAdd()
                }
            )
        }
    }
}

@Composable
private fun ItemActionButton(
    canAdd: Boolean,
    isStoreOpen: Boolean,
    isSoldOut: Boolean,
    isOrderableNow: Boolean = true,
    quantityInCart: Int,
    hasSets: Boolean,
    onClick: () -> Unit
) {
    if (!isStoreOpen) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
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
            color = MaterialTheme.colorScheme.surfaceVariant
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
        // Disabled "+" button
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .size(34.dp)
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
    } else {
        // Round "+" button
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(34.dp)
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
