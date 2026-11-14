package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StarGold
import com.example.ui.theme.SuccessGreen
import com.example.util.Formatters
import com.example.util.RatingFormatUtil

val SaleAccentRed = Color(0xFFD32F2F)
val SaleBadgeBg = Color(0xFFFFE5E5)

@Composable
fun ShopRatingBadge(
    ratingSum: Double,
    ratingCount: Int,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
    scoreFontSize: TextUnit = 13.sp,
    countFontSize: TextUnit = 12.sp
) {
    if (ratingCount <= 0) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = SuccessGreen.copy(alpha = 0.14f),
            modifier = modifier.testTag("shop_rating_new_chip")
        ) {
            Text(
                text = "New",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.testTag("shop_rating_value")
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Rating",
                tint = if (isMuted) Color.Gray else StarGold,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(1.dp))
            Text(
                text = RatingFormatUtil.formatAverage(ratingSum, ratingCount),
                fontWeight = FontWeight.Bold,
                fontSize = scoreFontSize,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = RatingFormatUtil.formatCountBracketed(ratingCount),
                fontSize = countFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PriceDropBadge(
    badgeText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = SaleBadgeBg,
        modifier = modifier
    ) {
        Text(
            text = badgeText,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = SaleAccentRed,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceDropInlineText(
    effectivePrice: Double,
    regularPrice: Double,
    isOnSale: Boolean,
    discountBadge: String?,
    itemSize: String = "",
    prefix: String = "",
    modifier: Modifier = Modifier,
    priceFontSize: TextUnit = 14.sp,
    regularFontSize: TextUnit = 12.sp,
    sizeFontSize: TextUnit = 11.sp,
    badgeFontSize: TextUnit = 10.sp,
    defaultPriceColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val sizeText = Formatters.formatItemSize(itemSize)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text = "$prefix${Formatters.formatPrice(effectivePrice)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = priceFontSize,
                color = if (isOnSale) SaleAccentRed else defaultPriceColor
            )
            if (sizeText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = sizeText,
                    fontSize = sizeFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isOnSale && regularPrice > effectivePrice) {
            Text(
                text = "$prefix${Formatters.formatPrice(regularPrice)}",
                fontSize = regularFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        if (!discountBadge.isNullOrBlank()) {
            PriceDropBadge(
                badgeText = discountBadge,
                fontSize = badgeFontSize,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}
