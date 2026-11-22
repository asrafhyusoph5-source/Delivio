package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CartItem
import com.example.data.model.MerchantItem
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.util.PriceDropUtil
import com.example.util.RecommendedProduct
import com.example.util.VariationUtil

@Composable
fun YouMayAlsoLikeSection(
    recommendations: List<RecommendedProduct>,
    cartItems: List<CartItem>,
    isStoreOpen: Boolean,
    onCardClick: (MerchantItem) -> Unit,
    onQuickAddClick: (MerchantItem) -> Unit,
    modifier: Modifier = Modifier,
    contentHorizontalPadding: PaddingValues = PaddingValues(horizontal = 0.dp)
) {
    if (recommendations.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("you_may_also_like_section")
    ) {
        Text(
            text = "You May Also Like",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = contentHorizontalPadding,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("you_may_also_like_row")
        ) {
            items(
                items = recommendations,
                key = { it.item.id }
            ) { rec ->
                val item = rec.item
                val qtyInCart = cartItems.filter { it.menuItem.id == item.id }.sumOf { it.quantity }
                YouMayAlsoLikeCard(
                    item = item,
                    isOrderableNow = rec.isOrderable,
                    isStoreOpen = isStoreOpen,
                    quantityInCart = qtyInCart,
                    onCardClick = { onCardClick(item) },
                    onQuickAddClick = { onQuickAddClick(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouMayAlsoLikeCard(
    item: MerchantItem,
    isOrderableNow: Boolean,
    isStoreOpen: Boolean,
    quantityInCart: Int,
    onCardClick: () -> Unit,
    onQuickAddClick: () -> Unit
) {
    val isSoldOut = !item.isAvailable || !item.inStock
    val canAdd = isStoreOpen && isOrderableNow && !isSoldOut
    val isVariation = VariationUtil.isVariationItem(item)
    val hasOptionsOrVariations = isVariation || item.hasSets || item.sets.isNotEmpty()
    val variationSummary = remember(item) { VariationUtil.getSummary(item) }

    val hasRequiredAddons = isVariation || (item.hasSets && (
        item.sets.any { it.required && it.cheapestChoicePrice > 0.0 } ||
            PriceDropUtil.effectiveFromPrice(item) > PriceDropUtil.effectivePrice(item)
        ))

    val isOnSale = if (isVariation) variationSummary.lowestIsOnSale else PriceDropUtil.isSaleInEffect(item)
    val effectivePrice = when {
        isVariation -> variationSummary.lowestEffectivePrice
        hasRequiredAddons -> PriceDropUtil.effectiveFromPrice(item)
        else -> PriceDropUtil.effectivePrice(item)
    }
    val regularPrice = when {
        isVariation -> variationSummary.lowestRegularPrice
        hasRequiredAddons -> PriceDropUtil.regularFromPrice(item)
        else -> item.price
    }
    val discountBadge = if (isVariation) variationSummary.upToBadge else PriceDropUtil.discountBadgeLabel(item)
    val formattedSize = remember(item.itemSize) { Formatters.formatItemSize(item.itemSize) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (canAdd) 1.5.dp else 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCardClick() }
            .testTag("yml_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
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
                            .size(30.dp)
                            .then(if (!canAdd) Modifier.alpha(0.45f) else Modifier)
                    )
                }

                if (!discountBadge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE53935),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .testTag("yml_sale_badge_${item.id}")
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

                if (quantityInCart > 0 && !isSoldOut) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF212121),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(20.dp)
                            .testTag("yml_cart_qty_${item.id}")
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

                if (isSoldOut) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("yml_sold_out_${item.id}")
                    ) {
                        Text(
                            text = "Sold out",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (!isOrderableNow) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("yml_unavailable_${item.id}")
                    ) {
                        Text(
                            text = "Unavailable",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(44.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp))
                        .clickable(enabled = canAdd) { onQuickAddClick() }
                        .padding(5.dp)
                        .testTag(if (canAdd) "yml_add_btn_${item.id}" else "yml_add_btn_disabled_${item.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .shadow(elevation = if (canAdd) 2.dp else 0.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(if (canAdd) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (canAdd) {
                                if (hasOptionsOrVariations) "Choose options for ${item.name}" else "Add ${item.name}"
                            } else {
                                "${item.name} unavailable"
                            },
                            tint = if (canAdd) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.name,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (canAdd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(5.dp))

            val showSaleStrike = isOnSale && regularPrice > effectivePrice
            val priceColor = if (showSaleStrike) SaleAccentRed else MaterialTheme.colorScheme.onSurface
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val priceText = Formatters.formatCompactPrice(effectivePrice, hideWholeZeroDecimals = true)

            val priceAnnotated = remember(priceText, formattedSize, hasRequiredAddons, priceColor, mutedColor) {
                buildAnnotatedString {
                    if (hasRequiredAddons) {
                        withStyle(SpanStyle(color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Normal)) {
                            append("from ")
                        }
                    }
                    withStyle(SpanStyle(color = priceColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)) {
                        append(priceText)
                    }
                    if (formattedSize.isNotEmpty()) {
                        withStyle(SpanStyle(color = mutedColor, fontSize = 10.sp, fontWeight = FontWeight.Normal)) {
                            append(" $formattedSize")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!canAdd) Modifier.alpha(0.6f) else Modifier)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = priceAnnotated,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showSaleStrike) {
                        Text(
                            text = Formatters.formatCompactPrice(regularPrice, hideWholeZeroDecimals = true),
                            fontSize = 10.sp,
                            color = mutedColor,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}
