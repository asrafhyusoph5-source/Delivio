package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ItemVariant
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.util.VariationUtil
import com.example.viewmodel.VariationSelectorState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariationSelectorBottomSheet(
    state: VariationSelectorState,
    onClose: () -> Unit,
    onConfirm: (ItemVariant, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("variation_selector_bottom_sheet")
    ) {
        VariationSelectorContent(
            state = state,
            showTopAppBar = false,
            onClose = onClose,
            onConfirm = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
        )
    }
}

@Composable
fun VariationSelectorFullScreen(
    state: VariationSelectorState,
    onClose: () -> Unit,
    onConfirm: (ItemVariant, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("variation_selector_fullscreen")
    ) {
        VariationSelectorContent(
            state = state,
            showTopAppBar = true,
            onClose = onClose,
            onConfirm = onConfirm,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariationSelectorContent(
    state: VariationSelectorState,
    showTopAppBar: Boolean,
    onClose: () -> Unit,
    onConfirm: (ItemVariant, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.item
    val isEditing = state.editingCartItemId != null
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val selectedOptions = remember(state) {
        mutableStateMapOf<String, String>().apply {
            putAll(state.initialSelections)
        }
    }
    var quantity by remember(state) {
        mutableIntStateOf(state.initialQuantity.coerceAtLeast(1))
    }
    var highlightedVariationId by remember(state) {
        mutableStateOf<String?>(null)
    }

    val currentSelectionsMap = selectedOptions.toMap()
    val completeVariant = remember(item, currentSelectionsMap) {
        VariationUtil.findCompleteVariant(item.variations, item.variants, currentSelectionsMap)
    }
    val summary = remember(item) { VariationUtil.getSummary(item) }
    val displayImageUrl = remember(item, currentSelectionsMap) {
        VariationUtil.resolveDisplayImageUrl(item.imageUrl, item.variations, currentSelectionsMap)
    }

    // Clamp quantity to selected variant's live stock when variant becomes complete or switches
    LaunchedEffect(completeVariant?.key, completeVariant?.stock) {
        val maxStock = completeVariant?.stock ?: 0
        if (completeVariant != null && maxStock > 0 && quantity > maxStock) {
            quantity = maxStock
        } else if (quantity < 1) {
            quantity = 1
        }
    }

    val isVariantAvailable = completeVariant != null && completeVariant.stock > 0
    val maxQuantity = completeVariant?.stock?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier) {
        if (showTopAppBar) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .testTag("variation_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "Edit Selection" else "Choose Option",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Shared Product Header Card (Image switches live with Variation 1 option)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .testTag("variation_header_image"),
                contentAlignment = Alignment.Center
            ) {
                if (displayImageUrl.isNotBlank()) {
                    DelivioAsyncImage(
                        imageUrl = displayImageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = item.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!showTopAppBar) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .testTag("variation_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Dynamic Price
                if (completeVariant != null) {
                    val effPrice = VariationUtil.effectiveVariantPrice(completeVariant, item)
                    val isVariantOnSale = VariationUtil.isVariantSaleInEffect(completeVariant, item)
                    val badge = VariationUtil.variantDiscountBadge(
                        variant = completeVariant,
                        saleActive = item.saleActive,
                        saleStartsAt = item.saleStartsAt,
                        saleEndsAt = item.saleEndsAt
                    )
                    PriceDropInlineText(
                        effectivePrice = effPrice,
                        regularPrice = completeVariant.price,
                        isOnSale = isVariantOnSale,
                        discountBadge = badge,
                        itemSize = item.itemSize,
                        priceFontSize = 18.sp,
                        regularFontSize = 13.sp,
                        sizeFontSize = 12.sp,
                        badgeFontSize = 10.sp
                    )
                } else {
                    val rangeText = VariationUtil.formatIncompletePriceRange(item, currentSelectionsMap)
                    val sizeText = Formatters.formatItemSize(item.itemSize)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = rangeText,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = if (summary.maxDiscountPercent > 0) SaleAccentRed else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("variation_price_range")
                            )
                            if (sizeText.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = sizeText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!summary.upToBadge.isNullOrBlank()) {
                            PriceDropBadge(
                                badgeText = summary.upToBadge,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                // Stock line + Low stock badge + Missing hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (completeVariant != null) {
                        Text(
                            text = if (completeVariant.stock > 0) "Stock: ${completeVariant.stock}" else "Out of stock",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (completeVariant.stock > 0) MaterialTheme.colorScheme.onSurfaceVariant else ErrorRed,
                            modifier = Modifier.testTag("variation_stock_label")
                        )
                        if (completeVariant.stock in 1..5) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = "Only ${completeVariant.stock} left",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        val partialStock = VariationUtil.formatStockCountForPartial(
                            item.variations,
                            item.variants,
                            currentSelectionsMap
                        )
                        Text(
                            text = "Stock: $partialStock",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("variation_stock_label")
                        )
                    }
                }

                val missingHint = VariationUtil.formatMissingHint(item.variations, currentSelectionsMap)
                if (missingHint != null) {
                    Text(
                        text = missingHint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("variation_missing_hint")
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Variations list (up to 4)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (showTopAppBar && item.description.isNotBlank()) {
                item(key = "desc") {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(item.variations, key = { _, v -> v.id }) { varIndex, variation ->
                val isHighlighted = highlightedVariationId == variation.id && selectedOptions[variation.id].isNullOrBlank()
                val isFirstVariation = varIndex == 0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isHighlighted) {
                                Modifier
                                    .border(1.5.dp, ErrorRed, RoundedCornerShape(12.dp))
                                    .background(ErrorRed.copy(alpha = 0.06f))
                                    .padding(10.dp)
                            } else {
                                Modifier
                            }
                        )
                        .testTag("variation_section_${variation.id}"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = variation.name.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = if (isHighlighted) ErrorRed else MaterialTheme.colorScheme.onSurface
                        )
                        if (isHighlighted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Please select ${variation.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        variation.options.forEach { option ->
                            val isSelected = selectedOptions[variation.id] == option.id
                            val isPermOos = VariationUtil.isOptionPermanentlyUnavailable(
                                variants = item.variants,
                                variationIndex = varIndex,
                                optionId = option.id
                            )
                            val isSelectable = VariationUtil.isOptionSelectable(
                                variations = item.variations,
                                variants = item.variants,
                                variationIndex = varIndex,
                                optionId = option.id,
                                currentSelections = currentSelectionsMap
                            )
                            val chipEnabled = isSelected || isSelectable

                            val bgColor = when {
                                isSelected -> YellowPrimary.copy(alpha = 0.22f)
                                !chipEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val borderColor = when {
                                isSelected -> YellowPrimary
                                !chipEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            val textColor = when {
                                isSelected -> MaterialTheme.colorScheme.onSurface
                                !chipEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bgColor,
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(if (!chipEnabled) Modifier.alpha(0.65f) else Modifier)
                                    .clickable(enabled = chipEnabled) {
                                        if (isSelected) {
                                            selectedOptions.remove(variation.id)
                                        } else {
                                            selectedOptions[variation.id] = option.id
                                        }
                                        highlightedVariationId = null
                                    }
                                    .testTag("variation_option_${variation.id}_${option.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (isFirstVariation && option.imageUrl.isNotBlank()) {
                                        DelivioAsyncImage(
                                            imageUrl = option.imageUrl.trim(),
                                            contentDescription = option.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Text(
                                        text = option.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor,
                                        textDecoration = if (isPermOos) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Bar: Quantity Stepper + Live Add to Cart / Update Cart Button
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stepper capped at variant stock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity -= 1 },
                        enabled = quantity > 1,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("variation_qty_minus")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease quantity",
                            tint = if (quantity > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "$quantity",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("variation_qty_text")
                    )
                    IconButton(
                        onClick = { if (completeVariant != null && quantity < maxQuantity) quantity += 1 },
                        enabled = isVariantAvailable && quantity < maxQuantity,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("variation_qty_plus")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase quantity",
                            tint = if (isVariantAvailable && quantity < maxQuantity) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Large Action Button
                val actionLabel = when {
                    completeVariant == null -> VariationUtil.formatMissingHint(item.variations, currentSelectionsMap) ?: "Select options"
                    completeVariant.stock <= 0 -> "Out of stock"
                    else -> {
                        val effPrice = VariationUtil.effectiveVariantPrice(completeVariant, item)
                        val verb = if (isEditing) "Update cart" else "Add to cart"
                        "$verb • ${Formatters.formatPrice(effPrice * quantity)}"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isVariantAvailable) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (completeVariant == null) {
                                val firstMissing = VariationUtil.firstMissingVariation(item.variations, currentSelectionsMap)
                                if (firstMissing != null) {
                                    highlightedVariationId = firstMissing.id
                                    val idx = item.variations.indexOfFirst { it.id == firstMissing.id }
                                    if (idx >= 0) {
                                        val scrollIndex = idx + if (showTopAppBar && item.description.isNotBlank()) 1 else 0
                                        scope.launch { listState.animateScrollToItem(scrollIndex) }
                                    }
                                }
                            } else if (completeVariant.stock > 0) {
                                onConfirm(completeVariant, quantity)
                            }
                        }
                        .testTag("variation_confirm_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isVariantAvailable) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
