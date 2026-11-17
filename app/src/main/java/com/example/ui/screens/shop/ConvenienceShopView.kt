package com.example.ui.screens.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CartItem
import com.example.data.model.MainCategoriesConfig
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantMenu
import com.example.data.model.MerchantSubcategory
import com.example.data.model.PlatformSettings
import com.example.data.model.PromoCode
import com.example.data.model.Restaurant
import com.example.ui.components.DelivioAsyncImage
import com.example.ui.components.PriceDropInlineText
import com.example.ui.components.SaleAccentRed
import com.example.ui.components.ShopRatingBadge
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.util.MainCategoriesUtil
import com.example.util.MenuAvailabilityUtil
import com.example.util.PriceDropUtil
import com.example.util.VariationUtil
import kotlinx.coroutines.launch
import java.util.Locale

private const val ALL_CATEGORIES_CHIP_ID = "__ALL__"

/**
 * Resolves effective grid columns for convenience shops based on admin setting (1..3)
 * and container width:
 * - >= 600.dp: admin + 2 (max 6)
 * - < 320.dp: at most 2 columns
 * - otherwise: admin (1..3)
 */
fun resolveConvenienceColumns(adminColumns: Int, availableWidthDp: Dp): Int {
    val clampedAdmin = adminColumns.coerceIn(1, 3)
    return when {
        availableWidthDp >= 600.dp -> (clampedAdmin + 2).coerceAtMost(6)
        availableWidthDp < 320.dp -> clampedAdmin.coerceAtMost(2)
        else -> clampedAdmin
    }
}

internal fun convenienceItemMatchesQuery(
    item: MerchantItem,
    searchQuery: String,
    mainCategories: MainCategoriesConfig
): Boolean {
    if (searchQuery.isBlank()) return true
    val normQ = MainCategoriesUtil.normalizeKey(searchQuery)
    val activeCats = MainCategoriesUtil.activeForType(mainCategories, "convenience")
    return MainCategoriesUtil.normalizeKey(item.name).contains(normQ) ||
        MainCategoriesUtil.normalizeKey(item.description).contains(normQ) ||
        MainCategoriesUtil.normalizeKey(item.subcategoryName).contains(normQ) ||
        MainCategoriesUtil.normalizeKey(item.categoryName).contains(normQ) ||
        MainCategoriesUtil.itemMatchesCategoryQuery(item.categoryName, searchQuery, activeCats)
}

internal fun buildSubcategorySections(
    subcategories: List<MerchantSubcategory>,
    items: List<MerchantItem>
): List<ShopSubcategorySection> {
    val result = mutableListOf<ShopSubcategorySection>()
    val assignedItemIds = mutableSetOf<String>()

    if (subcategories.isNotEmpty()) {
        subcategories.sortedBy { it.sortOrder }.forEach { subcat ->
            val normSubId = MainCategoriesUtil.normalizeKey(subcat.id)
            val normSubName = MainCategoriesUtil.normalizeKey(subcat.name)
            val matchingItems = items.filter { item ->
                val itemSubId = MainCategoriesUtil.normalizeKey(item.subcategoryId)
                val itemSubName = MainCategoriesUtil.normalizeKey(item.subcategoryName)
                val itemCatName = MainCategoriesUtil.normalizeKey(item.categoryName)
                !assignedItemIds.contains(item.id) && (
                    (itemSubId.isNotEmpty() && (itemSubId == normSubId || itemSubId == normSubName)) ||
                        (itemSubName.isNotEmpty() && itemSubName == normSubName) ||
                        (itemCatName.isNotEmpty() && itemCatName == normSubName)
                    )
            }.distinctBy { it.id }
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

        val unassigned = items.filter { !assignedItemIds.contains(it.id) }.distinctBy { it.id }
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
        val grouped = items.groupBy { item ->
            if (item.subcategoryName.isNotBlank()) item.subcategoryName
            else if (item.categoryName.isNotBlank()) item.categoryName
            else "Other"
        }
        val (otherGroup, standardGroups) = grouped.entries.partition { it.key.equals("Other", ignoreCase = true) }
        var order = 0
        standardGroups.sortedBy { it.key.lowercase(Locale.ROOT) }.forEach { (name, groupItems) ->
            val available = groupItems.filter { it.isAvailable }
            if (available.isNotEmpty()) {
                result.add(
                    ShopSubcategorySection(
                        id = "derived_$name",
                        name = name,
                        sortOrder = order++,
                        items = groupItems
                    )
                )
            }
        }
        if (otherGroup.isNotEmpty()) {
            val groupItems = otherGroup.first().value
            val available = groupItems.filter { it.isAvailable }
            if (available.isNotEmpty()) {
                result.add(
                    ShopSubcategorySection(
                        id = "other_section",
                        name = "Other",
                        sortOrder = 9999,
                        items = groupItems
                    )
                )
            }
        }
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConvenienceShopContent(
    restaurant: Restaurant,
    firestoreSubcategories: List<MerchantSubcategory>,
    firestoreItems: List<MerchantItem>,
    firestoreMenus: List<MerchantMenu>,
    cartItems: List<CartItem>,
    platformSettings: PlatformSettings,
    soldCountMap: Map<String, Int>,
    adminColumns: Int,
    isStoreOpen: Boolean,
    canOrderFromStore: Boolean,
    hasOtherBranches: Boolean,
    etaRangeText: String,
    shopVouchers: List<PromoCode>,
    appliedPromoCode: String?,
    searchQuery: String,
    mainCategories: MainCategoriesConfig,
    onChangeBranch: () -> Unit,
    onUseVoucher: (PromoCode) -> Unit,
    onDirectAdd: (MerchantItem, String) -> Unit,
    onDecrease: (MerchantItem) -> Unit,
    onOpenSelectorSheet: (MerchantItem, String) -> Unit,
    onOpenProductPage: (MerchantItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = navBottomPadding + 96.dp

    val catalogSections = remember(firestoreSubcategories, firestoreItems) {
        buildSubcategorySections(firestoreSubcategories, firestoreItems)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val effectiveColumns = resolveConvenienceColumns(adminColumns, maxWidth)

        if (effectiveColumns == 1) {
            ConvenienceSingleColumnList(
                restaurant = restaurant,
                firestoreSubcategories = firestoreSubcategories,
                firestoreItems = firestoreItems,
                firestoreMenus = firestoreMenus,
                cartItems = cartItems,
                platformSettings = platformSettings,
                soldCountMap = soldCountMap,
                isStoreOpen = isStoreOpen,
                canOrderFromStore = canOrderFromStore,
                hasOtherBranches = hasOtherBranches,
                etaRangeText = etaRangeText,
                shopVouchers = shopVouchers,
                appliedPromoCode = appliedPromoCode,
                searchQuery = searchQuery,
                mainCategories = mainCategories,
                bottomPadding = contentBottomPadding,
                onChangeBranch = onChangeBranch,
                onUseVoucher = onUseVoucher,
                onDirectAdd = onDirectAdd,
                onDecrease = onDecrease,
                onOpenSelectorSheet = onOpenSelectorSheet,
                onOpenProductPage = onOpenProductPage
            )
        } else {
            ConvenienceMultiColumnGrid(
                restaurant = restaurant,
                catalogSections = catalogSections,
                firestoreItems = firestoreItems,
                firestoreMenus = firestoreMenus,
                cartItems = cartItems,
                platformSettings = platformSettings,
                columns = effectiveColumns,
                isStoreOpen = isStoreOpen,
                canOrderFromStore = canOrderFromStore,
                hasOtherBranches = hasOtherBranches,
                etaRangeText = etaRangeText,
                shopVouchers = shopVouchers,
                appliedPromoCode = appliedPromoCode,
                searchQuery = searchQuery,
                mainCategories = mainCategories,
                bottomPadding = contentBottomPadding,
                onChangeBranch = onChangeBranch,
                onUseVoucher = onUseVoucher,
                onDirectAdd = onDirectAdd,
                onOpenSelectorSheet = onOpenSelectorSheet,
                onOpenProductPage = onOpenProductPage
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvenienceMultiColumnGrid(
    restaurant: Restaurant,
    catalogSections: List<ShopSubcategorySection>,
    firestoreItems: List<MerchantItem>,
    firestoreMenus: List<MerchantMenu>,
    cartItems: List<CartItem>,
    platformSettings: PlatformSettings,
    columns: Int,
    isStoreOpen: Boolean,
    canOrderFromStore: Boolean,
    hasOtherBranches: Boolean,
    etaRangeText: String,
    shopVouchers: List<PromoCode>,
    appliedPromoCode: String?,
    searchQuery: String,
    mainCategories: MainCategoriesConfig,
    bottomPadding: Dp,
    onChangeBranch: () -> Unit,
    onUseVoucher: (PromoCode) -> Unit,
    onDirectAdd: (MerchantItem, String) -> Unit,
    onOpenSelectorSheet: (MerchantItem, String) -> Unit,
    onOpenProductPage: (MerchantItem, String) -> Unit
) {
    var selectedClassId by rememberSaveable(restaurant.id) { mutableStateOf(ALL_CATEGORIES_CHIP_ID) }
    val validSelectedId = if (selectedClassId == ALL_CATEGORIES_CHIP_ID || catalogSections.any { it.id == selectedClassId }) {
        selectedClassId
    } else {
        ALL_CATEGORIES_CHIP_ID
    }

    val orderedItemsWithSection = remember(catalogSections, validSelectedId, searchQuery, mainCategories) {
        val baseList: List<Pair<MerchantItem, String>> = if (validSelectedId == ALL_CATEGORIES_CHIP_ID) {
            val seenIds = mutableSetOf<String>()
            buildList {
                catalogSections.forEach { section ->
                    section.items
                        .sortedBy { it.name.lowercase(Locale.ROOT) }
                        .forEach { item ->
                            if (seenIds.add(item.id)) {
                                add(item to section.name)
                            }
                        }
                }
            }
        } else {
            val chosenSection = catalogSections.firstOrNull { it.id == validSelectedId }
            chosenSection?.items
                ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                ?.map { it to chosenSection.name }
                ?: emptyList()
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { (item, _) ->
                convenienceItemMatchesQuery(item, searchQuery, mainCategories)
            }
        }
    }

    val chunkedRows = remember(orderedItemsWithSection, columns) {
        orderedItemsWithSection.chunked(columns)
    }

    val gridListState = rememberLazyListState()
    val nowMillis = remember(firestoreItems, firestoreMenus) { System.currentTimeMillis() }

    LazyColumn(
        state = gridListState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("convenience_products_grid"),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(key = "compact_shop_header") {
            ConvenienceCompactHeader(
                restaurant = restaurant,
                isStoreOpen = isStoreOpen,
                hasOtherBranches = hasOtherBranches,
                onChangeBranch = onChangeBranch,
                etaRangeText = etaRangeText,
                shopVouchers = shopVouchers,
                appliedPromoCode = appliedPromoCode,
                onUseVoucher = onUseVoucher
            )
        }

        stickyHeader(key = "convenience_chip_row") {
            ConvenienceCategoryChipRow(
                sections = catalogSections,
                selectedSectionId = validSelectedId,
                onSelectSection = { sectionId ->
                    selectedClassId = sectionId
                }
            )
        }

        if (firestoreItems.isEmpty() && searchQuery.isBlank()) {
            item(key = "coming_soon_card") {
                ConvenienceEmptyCatalogCard()
            }
        } else if (orderedItemsWithSection.isEmpty()) {
            item(key = "no_products_found") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp)
                        .testTag("convenience_no_products_found"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No products found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(
                items = chunkedRows,
                key = { rowIndex, row ->
                    "row_${columns}_${rowIndex}_${row.firstOrNull()?.first?.id.orEmpty()}"
                }
            ) { rowIndex, rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(start = 12.dp, end = 12.dp, top = if (rowIndex == 0) 10.dp else 8.dp)
                        .testTag("convenience_grid_row_$rowIndex"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (item, sectionName) ->
                        val isVariation = VariationUtil.isVariationItem(item)
                        val quantityInCart = cartItems.filter { it.menuItem.id == item.id }.sumOf { it.quantity }
                        val isOrderableNow = MenuAvailabilityUtil.isItemOrderable(
                            item = item,
                            menus = firestoreMenus,
                            targetMillis = nowMillis,
                            timezone = platformSettings.timezone
                        )
                        val availabilityLabel = if (!isOrderableNow && item.inStock && item.isAvailable) {
                            MenuAvailabilityUtil.getAvailabilityLabel(item, firestoreMenus, platformSettings.timezone)
                        } else null

                        ConvenienceGridCard(
                            item = item,
                            columns = columns,
                            quantityInCart = quantityInCart,
                            isStoreOpen = canOrderFromStore,
                            isOrderableNow = isOrderableNow,
                            availabilityLabel = availabilityLabel,
                            onDirectAdd = { onDirectAdd(item, sectionName) },
                            onOpenSelector = {
                                if (isVariation) {
                                    onOpenSelectorSheet(item, sectionName)
                                } else {
                                    onOpenProductPage(item, sectionName)
                                }
                            },
                            onOpenProductPage = { onOpenProductPage(item, sectionName) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    val emptySlots = columns - rowItems.size
                    repeat(emptySlots.coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvenienceSingleColumnList(
    restaurant: Restaurant,
    firestoreSubcategories: List<MerchantSubcategory>,
    firestoreItems: List<MerchantItem>,
    firestoreMenus: List<MerchantMenu>,
    cartItems: List<CartItem>,
    platformSettings: PlatformSettings,
    soldCountMap: Map<String, Int>,
    isStoreOpen: Boolean,
    canOrderFromStore: Boolean,
    hasOtherBranches: Boolean,
    etaRangeText: String,
    shopVouchers: List<PromoCode>,
    appliedPromoCode: String?,
    searchQuery: String,
    mainCategories: MainCategoriesConfig,
    bottomPadding: Dp,
    onChangeBranch: () -> Unit,
    onUseVoucher: (PromoCode) -> Unit,
    onDirectAdd: (MerchantItem, String) -> Unit,
    onDecrease: (MerchantItem) -> Unit,
    onOpenSelectorSheet: (MerchantItem, String) -> Unit,
    onOpenProductPage: (MerchantItem, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val tabScrollState = rememberScrollState()
    val tabPositions = remember { mutableStateMapOf<String, Int>() }

    val filteredItems = remember(firestoreItems, searchQuery, mainCategories) {
        if (searchQuery.isBlank()) firestoreItems
        else firestoreItems.filter { convenienceItemMatchesQuery(it, searchQuery, mainCategories) }
    }

    val sections = remember(firestoreSubcategories, filteredItems) {
        buildSubcategorySections(firestoreSubcategories, filteredItems)
    }

    val sectionHeaderIndices = remember(sections) {
        var currentIndex = 1 // 0 is compact_shop_header
        if (sections.size > 1) currentIndex++ // 1 is sticky_subcategory_tabs
        val map = mutableMapOf<String, Int>()
        sections.forEach { section ->
            map[section.id] = currentIndex
            currentIndex += 1 + section.items.size
        }
        map
    }

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

    LaunchedEffect(activeSectionId) {
        val targetX = tabPositions[activeSectionId]
        if (targetX != null) {
            tabScrollState.animateScrollTo((targetX - 70).coerceAtLeast(0))
        }
    }

    val nowMillis = remember(firestoreItems, firestoreMenus) { System.currentTimeMillis() }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("convenience_single_column_list"),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(key = "compact_shop_header") {
            ConvenienceCompactHeader(
                restaurant = restaurant,
                isStoreOpen = isStoreOpen,
                hasOtherBranches = hasOtherBranches,
                onChangeBranch = onChangeBranch,
                etaRangeText = etaRangeText,
                shopVouchers = shopVouchers,
                appliedPromoCode = appliedPromoCode,
                onUseVoucher = onUseVoucher
            )
        }

        if (sections.size > 1) {
            stickyHeader(key = "sticky_subcategory_tabs") {
                ShopSubcategoryTabsBar(
                    sections = sections,
                    activeSectionId = activeSectionId,
                    tabScrollState = tabScrollState,
                    tabPositions = tabPositions,
                    onTabClick = { section ->
                        scope.launch {
                            val targetIdx = sectionHeaderIndices[section.id] ?: 0
                            lazyListState.animateScrollToItem(targetIdx)
                        }
                    }
                )
            }
        }

        if (firestoreItems.isEmpty() && searchQuery.isBlank()) {
            item(key = "coming_soon_card") {
                ConvenienceEmptyCatalogCard()
            }
        } else if (sections.isEmpty()) {
            item(key = "no_products_found") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp)
                        .testTag("convenience_no_products_found"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No products found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            sections.forEach { section ->
                item(key = "heading_${section.id}") {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
                            .testTag("section_header_${section.id}")
                    )
                }

                itemsIndexed(
                    items = section.items,
                    key = { idx, item -> "${section.id}_${item.id}_$idx" }
                ) { _, item ->
                    val isVariation = VariationUtil.isVariationItem(item)
                    val quantityInCart = cartItems.filter { it.menuItem.id == item.id }.sumOf { it.quantity }
                    val isOrderableNow = MenuAvailabilityUtil.isItemOrderable(
                        item = item,
                        menus = firestoreMenus,
                        targetMillis = nowMillis,
                        timezone = platformSettings.timezone
                    )
                    val availabilityLabel = if (!isOrderableNow && item.inStock && item.isAvailable) {
                        MenuAvailabilityUtil.getAvailabilityLabel(item, firestoreMenus, platformSettings.timezone)
                    } else null
                    val effectiveSold = maxOf(item.soldCount, soldCountMap[item.id] ?: 0)

                    ConvenienceWideRowCard(
                        item = item,
                        soldCount = effectiveSold,
                        quantityInCart = quantityInCart,
                        isStoreOpen = canOrderFromStore,
                        isOrderableNow = isOrderableNow,
                        availabilityLabel = availabilityLabel,
                        onAdd = {
                            if (isVariation) {
                                onOpenSelectorSheet(item, section.name)
                            } else if (item.hasSets || item.sets.isNotEmpty()) {
                                onOpenProductPage(item, section.name)
                            } else {
                                onDirectAdd(item, section.name)
                            }
                        },
                        onDecrease = { onDecrease(item) },
                        onCustomize = { onOpenProductPage(item, section.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConvenienceCompactHeader(
    restaurant: Restaurant,
    isStoreOpen: Boolean,
    hasOtherBranches: Boolean,
    onChangeBranch: () -> Unit,
    etaRangeText: String,
    shopVouchers: List<PromoCode>,
    appliedPromoCode: String?,
    onUseVoucher: (PromoCode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("convenience_compact_header")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 64.dp rounded-square logo on left, plain background, uncropped
                val cleanLogo = restaurant.logoUrl.trim()
                val firstLetter = restaurant.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("convenience_header_logo")
                ) {
                    if (cleanLogo.isNotBlank()) {
                        DelivioAsyncImage(
                            imageUrl = cleanLogo,
                            contentDescription = restaurant.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstLetter,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = restaurant.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("convenience_header_name")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isStoreOpen) SuccessGreen else ErrorRed,
                            modifier = Modifier.testTag("convenience_header_status_chip")
                        ) {
                            Text(
                                text = if (isStoreOpen) "OPEN" else "CLOSED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        ShopRatingBadge(
                            ratingSum = restaurant.ratingSum,
                            ratingCount = restaurant.ratingCount,
                            iconSize = 14.dp,
                            scoreFontSize = 12.sp,
                            countFontSize = 11.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.testTag("convenience_header_eta")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeliveryDining,
                                contentDescription = null,
                                tint = YellowPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = etaRangeText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (hasOtherBranches) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onChangeBranch() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("change_branch_link")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Change branch",
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Change branch",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YellowPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (shopVouchers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "Vouchers",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(shopVouchers, key = { idx, voucher -> "${voucher.code}_$idx" }) { _, voucher ->
                            val isApplied = appliedPromoCode.equals(voucher.code, ignoreCase = true)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (isApplied) YellowPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.widthIn(min = 156.dp, max = 210.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = voucher.title.ifBlank { voucher.code },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
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
                                    Button(
                                        onClick = { onUseVoucher(voucher) },
                                        enabled = !isApplied,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = YellowPrimary,
                                            contentColor = TextOnYellow,
                                            disabledContainerColor = SuccessGreen.copy(alpha = 0.2f),
                                            disabledContentColor = SuccessGreen
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
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

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.8.dp
            )
        }
    }
}

@Composable
internal fun ConvenienceCategoryChipRow(
    sections: List<ShopSubcategorySection>,
    selectedSectionId: String,
    onSelectSection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("convenience_category_chip_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConvenienceCategoryChip(
                label = "All",
                selected = selectedSectionId == ALL_CATEGORIES_CHIP_ID,
                onClick = { onSelectSection(ALL_CATEGORIES_CHIP_ID) },
                modifier = Modifier.testTag("convenience_chip_all")
            )

            sections.forEach { section ->
                ConvenienceCategoryChip(
                    label = section.name,
                    selected = selectedSectionId == section.id,
                    onClick = { onSelectSection(section.id) },
                    modifier = Modifier.testTag("convenience_chip_${section.id}")
                )
            }
        }
    }
}

@Composable
private fun ConvenienceCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConvenienceGridCard(
    item: MerchantItem,
    columns: Int,
    quantityInCart: Int,
    isStoreOpen: Boolean,
    isOrderableNow: Boolean,
    availabilityLabel: String?,
    onDirectAdd: () -> Unit,
    onOpenSelector: () -> Unit,
    onOpenProductPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSoldOut = !item.isAvailable || !item.inStock
    val canAdd = isStoreOpen && isOrderableNow && !isSoldOut
    val isCompact3Plus = columns >= 3
    val isVariation = VariationUtil.isVariationItem(item)
    val variationSummary = remember(item) { VariationUtil.getSummary(item) }

    val hasRequiredAddons = isVariation || (item.hasSets && (
        item.sets.any { it.required && it.cheapestChoicePrice > 0.0 } ||
            PriceDropUtil.effectiveFromPrice(item) > PriceDropUtil.effectivePrice(item)
        ))
    val isOnSale = if (isVariation) variationSummary.lowestIsOnSale else PriceDropUtil.isSaleInEffect(item)
    val effectiveDisplayPrice = when {
        isVariation -> variationSummary.lowestEffectivePrice
        hasRequiredAddons -> PriceDropUtil.effectiveFromPrice(item)
        else -> PriceDropUtil.effectivePrice(item)
    }
    val regularDisplayPrice = when {
        isVariation -> variationSummary.lowestRegularPrice
        hasRequiredAddons -> PriceDropUtil.regularFromPrice(item)
        else -> item.price
    }
    val discountBadge = if (isVariation) variationSummary.upToBadge else PriceDropUtil.discountBadgeLabel(item)
    val cleanItemSize = remember(item.itemSize) { item.itemSize.trim().removePrefix("/") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (canAdd) 1.5.dp else 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .clickable(enabled = canAdd) { onOpenProductPage() }
            .testTag("convenience_grid_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact3Plus) 8.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Square (1:1) rounded image box, centered, shown in full (ContentScale.Fit)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                        .testTag("convenience_card_image_box_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.imageUrl.isNotBlank()) {
                        DelivioAsyncImage(
                            imageUrl = item.imageUrl.trim(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .then(if (!canAdd) Modifier.alpha(0.45f) else Modifier)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = item.name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(if (isCompact3Plus) 28.dp else 36.dp)
                                .then(if (!canAdd) Modifier.alpha(0.45f) else Modifier)
                        )
                    }

                    // Small red price drop badge on top-left of image
                    if (!discountBadge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE53935),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .testTag("convenience_discount_badge_${item.id}")
                        ) {
                            Text(
                                text = discountBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Small cart count pill on top-right when item is in cart
                    if (quantityInCart > 0 && !isSoldOut) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF212121),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .testTag("convenience_cart_qty_${item.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$quantityInCart",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Sold out label over faded image
                    if (isSoldOut) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.68f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .testTag("convenience_sold_out_label_${item.id}")
                        ) {
                            Text(
                                text = "Sold out",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 32.dp yellow circle "+" button fully inside image area, 6.dp from bottom-right corner
                    val hasOptions = isVariation || item.hasSets || item.sets.isNotEmpty()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .shadow(elevation = if (canAdd) 2.dp else 0.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(if (canAdd) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = canAdd) {
                                if (hasOptions) onOpenSelector() else onDirectAdd()
                            }
                            .testTag(if (canAdd) "convenience_add_btn_${item.id}" else "convenience_add_btn_disabled_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (canAdd) {
                                if (hasOptions) "Choose options for ${item.name}" else "Add ${item.name}"
                            } else {
                                "${item.name} unavailable"
                            },
                            tint = if (canAdd) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Name: max 2 lines, 12.sp (3+ cols) or 13.sp (2 cols), reserving 2 lines height
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isCompact3Plus) 12.sp else 13.sp,
                    lineHeight = if (isCompact3Plus) 15.sp else 17.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (canAdd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )

                // Small amber "Available <time>" label when scheduled unavailable right now
                if (!isSoldOut && !isOrderableNow) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("convenience_unavailable_label_${item.id}")
                    ) {
                        Text(
                            text = availabilityLabel ?: "Available later",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Bottom Price & Item Size block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .then(if (!canAdd) Modifier.alpha(0.6f) else Modifier)
            ) {
                val showSaleStrike = isOnSale && regularDisplayPrice > effectiveDisplayPrice
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    AutoSizedConveniencePriceText(
                        priceText = Formatters.formatCompactPrice(
                            amount = effectiveDisplayPrice,
                            hideWholeZeroDecimals = isCompact3Plus
                        ),
                        hasFromPrefix = hasRequiredAddons,
                        priceColor = if (showSaleStrike) SaleAccentRed else MaterialTheme.colorScheme.onSurface,
                        initialFontSize = if (isCompact3Plus) 13.sp else 14.sp,
                        minFontSize = 11.sp
                    )

                    if (showSaleStrike) {
                        Text(
                            text = Formatters.formatCompactPrice(
                                amount = regularDisplayPrice,
                                hideWholeZeroDecimals = isCompact3Plus
                            ),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                if (cleanItemSize.isNotBlank()) {
                    Text(
                        text = cleanItemSize,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoSizedConveniencePriceText(
    priceText: String,
    hasFromPrefix: Boolean,
    priceColor: Color,
    initialFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    var currentSp by remember(priceText, hasFromPrefix, initialFontSize) {
        mutableFloatStateOf(initialFontSize.value)
    }
    val prefixColor = MaterialTheme.colorScheme.onSurfaceVariant
    val prefixSize = 10.sp

    val annotated = remember(priceText, hasFromPrefix, priceColor, prefixColor) {
        buildAnnotatedString {
            if (hasFromPrefix) {
                withStyle(
                    SpanStyle(
                        color = prefixColor,
                        fontSize = prefixSize,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append("from ")
                }
            }
            withStyle(
                SpanStyle(
                    color = priceColor,
                    fontWeight = FontWeight.ExtraBold
                )
            ) {
                append(priceText)
            }
        }
    }

    Text(
        text = annotated,
        fontSize = currentSp.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.didOverflowWidth && currentSp > minFontSize.value) {
                currentSp = (currentSp - 0.5f).coerceAtLeast(minFontSize.value)
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun ConvenienceWideRowCard(
    item: MerchantItem,
    soldCount: Int,
    quantityInCart: Int,
    isStoreOpen: Boolean,
    isOrderableNow: Boolean,
    availabilityLabel: String?,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSoldOut = !item.isAvailable || !item.inStock
    val canAdd = isStoreOpen && isOrderableNow && !isSoldOut
    val isVariation = VariationUtil.isVariationItem(item)
    val variationSummary = remember(item) { VariationUtil.getSummary(item) }

    val hasRequiredAddons = isVariation || (item.hasSets && (
        item.sets.any { it.required && it.cheapestChoicePrice > 0.0 } ||
            PriceDropUtil.effectiveFromPrice(item) > PriceDropUtil.effectivePrice(item)
        ))
    val isOnSale = if (isVariation) variationSummary.lowestIsOnSale else PriceDropUtil.isSaleInEffect(item)
    val effectiveDisplayPrice = when {
        isVariation -> variationSummary.lowestEffectivePrice
        hasRequiredAddons -> PriceDropUtil.effectiveFromPrice(item)
        else -> PriceDropUtil.effectivePrice(item)
    }
    val regularDisplayPrice = when {
        isVariation -> variationSummary.lowestRegularPrice
        hasRequiredAddons -> PriceDropUtil.regularFromPrice(item)
        else -> item.price
    }
    val discountBadge = if (isVariation) variationSummary.upToBadge else PriceDropUtil.discountBadgeLabel(item)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (canAdd) 2.dp else 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .clickable(enabled = canAdd) { onCustomize() }
            .then(if (!canAdd) Modifier.alpha(0.55f) else Modifier)
            .testTag("convenience_wide_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                PriceDropInlineText(
                    effectivePrice = effectiveDisplayPrice,
                    regularPrice = regularDisplayPrice,
                    isOnSale = isOnSale && regularDisplayPrice > effectiveDisplayPrice,
                    discountBadge = discountBadge,
                    itemSize = item.itemSize,
                    prefix = if (hasRequiredAddons) "from " else "",
                    priceFontSize = 15.sp,
                    regularFontSize = 12.sp,
                    sizeFontSize = 12.sp,
                    badgeFontSize = 10.sp,
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

                if (soldCount > 0) {
                    Text(
                        text = "$soldCount sold",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (!isSoldOut && !isOrderableNow && !availabilityLabel.isNullOrBlank()) {
                    Text(
                        text = availabilityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotBlank()) {
                    DelivioAsyncImage(
                        imageUrl = item.imageUrl.trim(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = item.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    val hasOptions = isVariation || item.hasSets || item.sets.isNotEmpty()
                    ItemActionWidget(
                        canAdd = canAdd,
                        isStoreOpen = isStoreOpen,
                        isSoldOut = isSoldOut,
                        isOrderableNow = isOrderableNow,
                        hasSets = hasOptions,
                        quantityInCart = quantityInCart,
                        onAdd = { if (hasOptions) onCustomize() else onAdd() },
                        onDecrease = onDecrease,
                        onCustomize = { if (isVariation) onAdd() else onCustomize() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvenienceEmptyCatalogCard() {
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
                text = "This merchant has not published any available products yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
