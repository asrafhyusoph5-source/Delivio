package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Restaurant
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StarGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.ui.theme.YellowTint
import com.example.util.Formatters

@Composable
fun RestaurantCard(
    restaurant: Restaurant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen = restaurant.isOpen

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOpen) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOpen) 2.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("restaurant_card_${restaurant.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isOpen) Modifier.alpha(0.78f) else Modifier)
        ) {
            // Merchant cards use logoUrl, and fall back to bannerUrl, then imageUrl, then a placeholder.
            // Where an imageUrl exists, never show the emoji or gradient placeholder.
            val effectiveImageUrl = androidx.compose.runtime.remember(restaurant.logoUrl, restaurant.bannerUrl, restaurant.imageUrl) {
                if (restaurant.logoUrl.isNotBlank()) restaurant.logoUrl.trim()
                else if (restaurant.bannerUrl.isNotBlank()) restaurant.bannerUrl.trim()
                else if (restaurant.imageUrl.isNotBlank()) restaurant.imageUrl.trim()
                else ""
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                if (effectiveImageUrl.isNotEmpty()) {
                    DelivioAsyncImage(
                        imageUrl = effectiveImageUrl,
                        contentDescription = restaurant.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isOpen) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            YellowPrimary.copy(alpha = 0.25f),
                                            YellowPrimary.copy(alpha = 0.08f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Gray.copy(alpha = 0.35f),
                                            Color.Gray.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = restaurant.bannerEmoji,
                            fontSize = 56.sp
                        )
                    }
                }

                // Top Left Badge: Green OPEN vs Red CLOSED
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOpen) SuccessGreen else ErrorRed,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isOpen) "OPEN" else "CLOSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Bottom Right Tag: Opening Time vs ETA
                if (!isOpen) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.82f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = restaurant.nextOpenTime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${restaurant.etaMinutes} mins",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = if (isOpen) StarGold else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${restaurant.rating}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "(${restaurant.reviewCount})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open/Closed indicator text
                    if (!isOpen) {
                        Text(
                            text = "Currently Closed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    } else {
                        Text(
                            text = "${restaurant.category} • ${restaurant.priceTier}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeliveryDining,
                            contentDescription = null,
                            tint = if (isOpen && restaurant.deliveryFee == 0.0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (restaurant.deliveryFee == 0.0) "Free Delivery" else Formatters.formatPrice(restaurant.deliveryFee),
                            fontSize = 12.sp,
                            fontWeight = if (restaurant.deliveryFee == 0.0) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOpen && restaurant.deliveryFee == 0.0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Deal Tag banner if available & open
                if (isOpen && restaurant.isDeal && restaurant.dealDescription != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(YellowPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = BadgeOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = restaurant.dealDescription,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
