package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.repository.TrackerDisplayState
import com.example.ui.theme.StarGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.ui.theme.YellowTint
import com.example.util.Formatters
import kotlin.math.roundToInt

@Composable
fun FloatingOrderTracker(
    order: Order,
    trackerState: TrackerDisplayState,
    isMerchantCollapsed: Boolean,
    isRiderCollapsed: Boolean,
    onStateChange: (TrackerDisplayState) -> Unit,
    onToggleMerchant: () -> Unit,
    onToggleRider: () -> Unit,
    onDismissOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    fun dialPhone(phoneNumber: String, name: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Dialing $name: $phoneNumber", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(trackerState) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator < -40f) {
                            // Dragged UP
                            if (trackerState == TrackerDisplayState.BUBBLE) {
                                onStateChange(TrackerDisplayState.COLLAPSED)
                            } else if (trackerState == TrackerDisplayState.COLLAPSED) {
                                onStateChange(TrackerDisplayState.EXPANDED)
                            }
                        } else if (dragAccumulator > 40f) {
                            // Dragged DOWN
                            if (trackerState == TrackerDisplayState.EXPANDED) {
                                onStateChange(TrackerDisplayState.COLLAPSED)
                            } else if (trackerState == TrackerDisplayState.COLLAPSED) {
                                onStateChange(TrackerDisplayState.BUBBLE)
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                )
            }
    ) {
        when (trackerState) {
            TrackerDisplayState.BUBBLE -> {
                // Floating Bubble Pill (Tappable to expand)
                TrackerBubbleView(
                    order = order,
                    onClick = { onStateChange(TrackerDisplayState.COLLAPSED) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 8.dp)
                        .testTag("tracker_bubble")
                )
            }

            TrackerDisplayState.COLLAPSED -> {
                // Collapsed Pill View above Bottom Navigation Bar
                TrackerCollapsedView(
                    order = order,
                    onExpand = { onStateChange(TrackerDisplayState.EXPANDED) },
                    onMinimize = { onStateChange(TrackerDisplayState.BUBBLE) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("tracker_collapsed")
                )
            }

            TrackerDisplayState.EXPANDED -> {
                // Expanded Sheet View
                TrackerExpandedSheet(
                    order = order,
                    isMerchantCollapsed = isMerchantCollapsed,
                    isRiderCollapsed = isRiderCollapsed,
                    onCollapse = { onStateChange(TrackerDisplayState.COLLAPSED) },
                    onMinimize = { onStateChange(TrackerDisplayState.BUBBLE) },
                    onToggleMerchant = onToggleMerchant,
                    onToggleRider = onToggleRider,
                    onCallMerchant = { dialPhone(order.restaurant.phone, order.restaurant.name) },
                    onCallRider = { order.rider?.phone?.let { dialPhone(it, order.rider.name) } },
                    onDismissOrder = onDismissOrder,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .testTag("tracker_expanded")
                )
            }
        }
    }
}

@Composable
private fun TrackerBubbleView(
    order: Order,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = YellowPrimary,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBike,
                contentDescription = "Active Order",
                tint = TextOnYellow,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = order.status.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextOnYellow
                )
                Text(
                    text = "${order.restaurant.etaMinutes}m ETA",
                    fontSize = 10.sp,
                    color = TextOnYellow.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Expand",
                tint = TextOnYellow,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TrackerCollapsedView(
    order: Order,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = when (order.status) {
        OrderStatus.PLACED -> 0.15f
        OrderStatus.PREPARING -> 0.40f
        OrderStatus.READY -> 0.65f
        OrderStatus.ON_THE_WAY -> 0.85f
        OrderStatus.DELIVERED -> 1.0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top Drag indicator bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status Badge Icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon: ImageVector = when (order.status) {
                            OrderStatus.PLACED -> Icons.Default.LocalDining
                            OrderStatus.PREPARING -> Icons.Default.Store
                            OrderStatus.READY -> Icons.Default.Check
                            OrderStatus.ON_THE_WAY -> Icons.Default.DirectionsBike
                            OrderStatus.DELIVERED -> Icons.Default.Check
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = order.status.title,
                            tint = TextOnYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = order.status.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (order.status == OrderStatus.ON_THE_WAY) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(YellowPrimary)
                                )
                            }
                        }
                        Text(
                            text = "${order.restaurant.name} • ETA ~${order.restaurant.etaMinutes} mins",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize to bubble",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand details",
                            tint = YellowPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Yellow Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = YellowPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun TrackerExpandedSheet(
    order: Order,
    isMerchantCollapsed: Boolean,
    isRiderCollapsed: Boolean,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMerchant: () -> Unit,
    onToggleRider: () -> Unit,
    onCallMerchant: () -> Unit,
    onCallRider: () -> Unit,
    onDismissOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top Drag Handle Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Header with Status and Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = YellowPrimary
                            ) {
                                val badgeText = when (order.serviceType) {
                                    com.example.data.model.ServiceType.PARCEL -> "PARCEL EXPRESS"
                                    com.example.data.model.ServiceType.PABILI -> "PABILI SERVICE"
                                    com.example.data.model.ServiceType.CONVENIENCE -> "CONVENIENCE MART"
                                    else -> "LIVE ORDER"
                                }
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextOnYellow,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = order.id,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = order.status.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = order.status.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5-STEP TIMELINE STEPPER
                StepperTimeline(
                    currentStatus = order.status,
                    statusTimestamps = order.statusTimestamps,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // CONDITIONAL MAP: SHOWN ONLY WHEN "Rider is on the way"
                if (order.status.showMap()) {
                    Text(
                        text = "Live Delivery Route",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SimulatedDeliveryMap(
                        restaurantName = order.restaurant.name,
                        deliveryAddress = order.deliveryAddress.fullAddress,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // If Delivered: Show Confetti & Rating prompt
                if (order.status == OrderStatus.DELIVERED) {
                    RatingCardContent(
                        restaurantName = order.restaurant.name,
                        riderName = order.rider?.name,
                        onDismiss = onDismissOrder,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                // MERCHANT SECTION (Collapsible)
                MerchantSection(
                    restaurant = order.restaurant,
                    isCollapsed = isMerchantCollapsed,
                    onToggleCollapse = onToggleMerchant,
                    onCall = onCallMerchant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // RIDER SECTION (Collapsible, shown from "Ready" onward)
                if (order.status.hasRider()) {
                    val rider = order.rider
                    if (rider != null) {
                        RiderSection(
                            rider = rider,
                            isCollapsed = isRiderCollapsed,
                            onToggleCollapse = onToggleRider,
                            onCall = onCallRider
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ORDER SUMMARY
                OrderItemsSummary(
                    order = order,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Confetti overlay when Delivered
            if (order.status == OrderStatus.DELIVERED) {
                ConfettiEffect(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StepperTimeline(
    currentStatus: OrderStatus,
    statusTimestamps: Map<OrderStatus, Long>,
    modifier: Modifier = Modifier
) {
    val statuses = OrderStatus.values()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        statuses.forEachIndexed { index, status ->
            val isCurrent = status == currentStatus
            val isPassed = status.stepIndex < currentStatus.stepIndex
            val isPending = status.stepIndex > currentStatus.stepIndex
            val timestamp = statusTimestamps[status]

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Stepper Node column (Circle + Vertical connector line)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> YellowPrimary
                                    isPassed -> SuccessGreen
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPassed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(TextOnYellow)
                            )
                        } else {
                            Text(
                                text = "${status.stepIndex + 1}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (index < statuses.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(
                                    if (isPassed) SuccessGreen
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Status info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = status.title,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = when {
                                isCurrent -> YellowPrimary
                                isPassed -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        if (isCurrent) {
                            Text(
                                text = status.subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (timestamp != null) {
                        Text(
                            text = Formatters.formatTime(timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantSection(
    restaurant: com.example.data.model.Restaurant,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCollapse() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(restaurant.bannerEmoji, fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "Merchant",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = YellowPrimary
                        )
                        Text(
                            text = restaurant.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Call Button (always enabled for active order)
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary)
                            .testTag("call_merchant_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Merchant",
                            tint = TextOnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Toggle Merchant Details"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Address: ${restaurant.address}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Phone: ${restaurant.phone}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RiderSection(
    rider: com.example.data.model.Rider,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCollapse() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(rider.avatarEmoji, fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "Delivery Rider",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = YellowPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rider.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StarGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${rider.rating}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Call Button (enabled once order placed)
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary)
                            .testTag("call_rider_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Rider",
                            tint = TextOnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Toggle Rider Details"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vehicle: ${rider.vehicle} (${rider.licensePlate})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${rider.totalDeliveries}+ deliveries completed with 99% on-time record",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderItemsSummary(
    order: Order,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (order.serviceType == com.example.data.model.ServiceType.PARCEL && order.parcelDetails != null) {
                val parcel = order.parcelDetails
                Text(
                    text = "Parcel Delivery Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Package: ${parcel.packageSize} (${parcel.weightKg} kg)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "Item: ${parcel.itemDescription}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Vehicle: ${parcel.vehicleType} • ${if (parcel.isFragile) "⚠️ Fragile" else "Standard"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "From: ${parcel.senderName} (${parcel.senderPhone})", fontSize = 12.sp)
                Text(text = "To: ${parcel.recipientName} (${parcel.recipientPhone})", fontSize = 12.sp)
            } else if (order.serviceType == com.example.data.model.ServiceType.PABILI && order.pabiliDetails != null) {
                val pabili = order.pabiliDetails
                Text(
                    text = "Pabili Items List (${pabili.items.size} items)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(text = "Store: ${pabili.storeName}", fontSize = 12.sp, color = YellowPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                pabili.items.forEach { pItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${pItem.quantity}x ${pItem.name}", fontSize = 13.sp)
                        Text(text = Formatters.formatPrice(pItem.estimatedPrice * pItem.quantity), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (pabili.riderNotes.isNotBlank()) {
                    Text(text = "Notes: ${pabili.riderNotes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Text(
                    text = "Order Details (${order.items.sumOf { it.quantity }} items)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.menuItem.name}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Formatters.formatPrice(item.totalPrice),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Paid",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = Formatters.formatPrice(order.total),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = YellowPrimary
                )
            }
        }
    }
}
