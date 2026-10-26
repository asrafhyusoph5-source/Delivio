package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ServiceType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.ErrorRed
import com.example.util.DelivioStatusConstants
import com.example.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier,
    orderViewModel: OrderViewModel? = null,
    activeOrders: List<Order> = emptyList(),
    onSelectOrder: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { context.getSharedPreferences("delivio_tracker_prefs", Context.MODE_PRIVATE) }

    var isRightEdge by remember { mutableStateOf(prefs.getBoolean("is_right_edge", true)) }
    var yRatio by remember { mutableFloatStateOf(prefs.getFloat("y_ratio", 0.72f)) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }
    var bubbleHeightPx by remember { mutableFloatStateOf(0f) }
    var collapsedWidthPx by remember { mutableFloatStateOf(0f) }
    var collapsedHeightPx by remember { mutableFloatStateOf(0f) }

    fun dialPhone(phoneNumber: String, name: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Dialing $name: $phoneNumber", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        when (trackerState) {
            TrackerDisplayState.BUBBLE -> {
                val edgePaddingPx = with(density) { 16.dp.toPx() }
                val targetBaseX = if (isRightEdge) (screenWidthPx - bubbleWidthPx - edgePaddingPx).coerceAtLeast(0f) else edgePaddingPx
                val targetBaseY = (screenHeightPx * yRatio).coerceIn(
                    with(density) { 60.dp.toPx() },
                    (screenHeightPx - bubbleHeightPx - with(density) { 70.dp.toPx() }).coerceAtLeast(60f)
                )

                val effectiveX = if (isDragging) (targetBaseX + dragOffsetX).coerceIn(0f, (screenWidthPx - bubbleWidthPx).coerceAtLeast(0f)) else targetBaseX
                val effectiveY = if (isDragging) (targetBaseY + dragOffsetY).coerceIn(0f, (screenHeightPx - bubbleHeightPx).coerceAtLeast(0f)) else targetBaseY

                TrackerBubbleView(
                    order = order,
                    activeOrders = activeOrders,
                    onClick = {
                        if (!isDragging) {
                            onStateChange(TrackerDisplayState.COLLAPSED)
                        }
                    },
                    modifier = Modifier
                        .offset { IntOffset(effectiveX.roundToInt(), effectiveY.roundToInt()) }
                        .onSizeChanged {
                            bubbleWidthPx = it.width.toFloat()
                            bubbleHeightPx = it.height.toFloat()
                        }
                        .pointerInput(isRightEdge, yRatio, screenWidthPx, screenHeightPx) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    val finalX = targetBaseX + dragOffsetX
                                    val centerX = finalX + (bubbleWidthPx / 2f)
                                    val newIsRightEdge = centerX >= (screenWidthPx / 2f)
                                    val finalY = (targetBaseY + dragOffsetY).coerceIn(
                                        with(density) { 60.dp.toPx() },
                                        (screenHeightPx - bubbleHeightPx - with(density) { 70.dp.toPx() }).coerceAtLeast(60f)
                                    )
                                    val newYRatio = (finalY / screenHeightPx).coerceIn(0.1f, 0.85f)
                                    isRightEdge = newIsRightEdge
                                    yRatio = newYRatio
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    isDragging = false
                                    prefs.edit().putBoolean("is_right_edge", newIsRightEdge).putFloat("y_ratio", newYRatio).apply()
                                },
                                onDragCancel = {
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                }
                            )
                        }
                        .testTag("tracker_bubble")
                )
            }

            TrackerDisplayState.COLLAPSED -> {
                val edgePaddingPx = with(density) { 12.dp.toPx() }
                val targetBaseX = if (isRightEdge) (screenWidthPx - collapsedWidthPx - edgePaddingPx).coerceAtLeast(0f) else edgePaddingPx
                val targetBaseY = (screenHeightPx * yRatio).coerceIn(
                    with(density) { 60.dp.toPx() },
                    (screenHeightPx - collapsedHeightPx - with(density) { 70.dp.toPx() }).coerceAtLeast(60f)
                )

                val effectiveX = if (isDragging) (targetBaseX + dragOffsetX).coerceIn(0f, (screenWidthPx - collapsedWidthPx).coerceAtLeast(0f)) else targetBaseX
                val effectiveY = if (isDragging) (targetBaseY + dragOffsetY).coerceIn(0f, (screenHeightPx - collapsedHeightPx).coerceAtLeast(0f)) else targetBaseY

                TrackerCollapsedView(
                    order = order,
                    activeOrders = activeOrders,
                    onSelectOrder = onSelectOrder,
                    onExpand = { onStateChange(TrackerDisplayState.EXPANDED) },
                    onMinimize = { onStateChange(TrackerDisplayState.BUBBLE) },
                    modifier = Modifier
                        .offset { IntOffset(effectiveX.roundToInt(), effectiveY.roundToInt()) }
                        .onSizeChanged {
                            collapsedWidthPx = it.width.toFloat()
                            collapsedHeightPx = it.height.toFloat()
                        }
                        .pointerInput(isRightEdge, yRatio, screenWidthPx, screenHeightPx) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    val finalX = targetBaseX + dragOffsetX
                                    val centerX = finalX + (collapsedWidthPx / 2f)
                                    val newIsRightEdge = centerX >= (screenWidthPx / 2f)
                                    val finalY = (targetBaseY + dragOffsetY).coerceIn(
                                        with(density) { 60.dp.toPx() },
                                        (screenHeightPx - collapsedHeightPx - with(density) { 70.dp.toPx() }).coerceAtLeast(60f)
                                    )
                                    val newYRatio = (finalY / screenHeightPx).coerceIn(0.1f, 0.85f)
                                    isRightEdge = newIsRightEdge
                                    yRatio = newYRatio
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    isDragging = false
                                    prefs.edit().putBoolean("is_right_edge", newIsRightEdge).putFloat("y_ratio", newYRatio).apply()
                                },
                                onDragCancel = {
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                }
                            )
                        }
                        .testTag("tracker_collapsed")
                )
            }

            TrackerDisplayState.EXPANDED -> {
                TrackerExpandedSheet(
                    order = order,
                    activeOrders = activeOrders,
                    onSelectOrder = onSelectOrder,
                    isMerchantCollapsed = isMerchantCollapsed,
                    isRiderCollapsed = isRiderCollapsed,
                    onCollapse = { onStateChange(TrackerDisplayState.COLLAPSED) },
                    onMinimize = { onStateChange(TrackerDisplayState.BUBBLE) },
                    onToggleMerchant = onToggleMerchant,
                    onToggleRider = onToggleRider,
                    onCallMerchant = { dialPhone(order.restaurant.phone, order.restaurant.name) },
                    onCallRider = { order.rider?.phone?.let { dialPhone(it, order.rider.name) } },
                    onDismissOrder = onDismissOrder,
                    orderViewModel = orderViewModel,
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
    activeOrders: List<Order> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPickup = order.fulfillment.equals(DelivioStatusConstants.FULFILLMENT_PICKUP, ignoreCase = true)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = YellowPrimary,
        shadowElevation = 8.dp,
        modifier = modifier.size(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (order.serviceType == ServiceType.TAXI) {
                Icon(
                    imageVector = Icons.Default.LocalTaxi,
                    contentDescription = "Taxi",
                    tint = TextOnYellow,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = if (isPickup) Icons.Default.Store else Icons.Default.DirectionsBike,
                    contentDescription = "Active Order",
                    tint = TextOnYellow,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (activeOrders.size > 1) {
                Surface(
                    shape = CircleShape,
                    color = ErrorRed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${activeOrders.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerCollapsedView(
    order: Order,
    activeOrders: List<Order> = emptyList(),
    onSelectOrder: (String) -> Unit = {},
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTaxi = order.serviceType == ServiceType.TAXI
    val isPickup = order.fulfillment.equals(DelivioStatusConstants.FULFILLMENT_PICKUP, ignoreCase = true)
    val progress = when {
        isTaxi -> when {
            order.rawStatus.equals("Completed", true) || order.status == OrderStatus.COMPLETED -> 1.0f
            order.rawStatus.equals("On trip", true) -> 0.75f
            order.rawStatus.equals("Driver arrived", true) -> 0.50f
            order.rawStatus.equals("Driver is on the way", true) -> 0.30f
            else -> 0.15f
        }
        isPickup -> when (order.status) {
            OrderStatus.PLACED -> 0.25f
            OrderStatus.PREPARING -> 0.50f
            OrderStatus.READY -> 0.75f
            OrderStatus.COMPLETED -> 1.0f
            else -> 0.25f
        }
        else -> when (order.status) {
            OrderStatus.PLACED -> 0.15f
            OrderStatus.PREPARING -> 0.40f
            OrderStatus.READY -> 0.65f
            OrderStatus.ON_THE_WAY -> 0.85f
            OrderStatus.DELIVERED, OrderStatus.COMPLETED -> 1.0f
            OrderStatus.CANCELLED, OrderStatus.REJECTED -> 0f
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .widthIn(min = 280.dp, max = 330.dp)
            .clickable { onExpand() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Drag indicator bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )

            // Multiple active orders badge & selector
            if (activeOrders.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = YellowPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${activeOrders.size} Orders",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    activeOrders.forEachIndexed { idx, o ->
                        val isSelected = o.id == order.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onSelectOrder(o.id) }
                        ) {
                            Text(
                                text = "#${idx + 1} ${o.restaurant.name.take(10)}",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTaxi) {
                            Icon(
                                imageVector = Icons.Default.LocalTaxi,
                                contentDescription = "Taxi",
                                tint = TextOnYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            val icon: ImageVector = when {
                                isPickup -> when (order.status) {
                                    OrderStatus.PLACED -> Icons.Default.LocalDining
                                    OrderStatus.PREPARING -> Icons.Default.Store
                                    OrderStatus.READY -> Icons.Default.Store
                                    OrderStatus.COMPLETED -> Icons.Default.Check
                                    else -> Icons.Default.Store
                                }
                                else -> when (order.status) {
                                    OrderStatus.PLACED -> Icons.Default.LocalDining
                                    OrderStatus.PREPARING -> Icons.Default.Store
                                    OrderStatus.READY -> Icons.Default.Check
                                    OrderStatus.ON_THE_WAY -> Icons.Default.DirectionsBike
                                    OrderStatus.DELIVERED, OrderStatus.COMPLETED -> Icons.Default.Check
                                    OrderStatus.CANCELLED, OrderStatus.REJECTED -> Icons.Default.Close
                                }
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = order.status.title,
                                tint = TextOnYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        val displayTitle = when {
                            isTaxi -> when {
                                order.rawStatus in listOf("Placed Order", "Looking for a driver...", "placed", "pending") -> "Looking for a driver..."
                                order.rawStatus.equals("Driver is on the way", true) -> "Driver is on the way"
                                order.rawStatus.equals("Driver arrived", true) -> "Driver arrived"
                                order.rawStatus.equals("On trip", true) -> "On trip"
                                order.rawStatus.equals("Completed", true) -> "Completed"
                                else -> order.rawStatus
                            }
                            order.fulfillment == DelivioStatusConstants.FULFILLMENT_SCHEDULED && order.status == OrderStatus.PLACED ->
                                DelivioStatusConstants.getFirstStepTitle(order.fulfillment, order.scheduledFor)
                            else -> order.status.title
                        }
                        Text(
                            text = displayTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = when {
                            isTaxi -> if (order.rider != null) {
                                "${order.rider.name} • ${order.rider.vehicle} (${order.rider.licensePlate})"
                            } else {
                                "${order.vehicleType ?: "Taxi"} • Searching nearby"
                            }
                            isPickup -> "${order.restaurant.name} • Pick Up"
                            else -> "${order.restaurant.name} • ~${order.restaurant.etaMinutes}m ETA"
                        }
                        Text(
                            text = subtitleText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Minimize to round icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand details",
                            tint = YellowPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
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
    activeOrders: List<Order> = emptyList(),
    onSelectOrder: (String) -> Unit = {},
    isMerchantCollapsed: Boolean,
    isRiderCollapsed: Boolean,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMerchant: () -> Unit,
    onToggleRider: () -> Unit,
    onCallMerchant: () -> Unit,
    onCallRider: () -> Unit,
    onDismissOrder: () -> Unit,
    modifier: Modifier = Modifier,
    orderViewModel: OrderViewModel? = null
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showConfirmCancelDialog by remember { mutableStateOf(false) }
        var cancelReasonInput by remember { mutableStateOf("") }
        var cannotCancelMessage by remember { mutableStateOf<String?>(null) }
        var isCheckingCancelStatus by remember { mutableStateOf(false) }

        fun handleCancelClick() {
            if (orderViewModel == null) {
                Toast.makeText(context, "Order service unavailable", Toast.LENGTH_SHORT).show()
                return
            }
            isCheckingCancelStatus = true
            scope.launch {
                val latest = orderViewModel.getLatestOrder(order.id)
                isCheckingCancelStatus = false
                val freshStatus = latest?.status ?: order.rawStatus
                val isPlaced = freshStatus.equals("Placed Order", ignoreCase = true) ||
                        freshStatus.equals("placed", ignoreCase = true) ||
                        freshStatus.equals("pending", ignoreCase = true)

                val serviceType = (latest?.serviceType ?: order.serviceType.name).lowercase()
                val isTaxi = serviceType == "taxi"
                val isParcelOrPabili = serviceType in listOf("parcel", "pabili") ||
                        (latest?.merchantId.isNullOrEmpty() && order.restaurant.id.startsWith("service_partner"))
                val riderAssigned = latest?.riderId != null || order.rider != null

                if (isTaxi) {
                    val canCancelTaxi = freshStatus.equals("Placed Order", ignoreCase = true) ||
                            freshStatus.equals("placed", ignoreCase = true) ||
                            freshStatus.equals("pending", ignoreCase = true) ||
                            freshStatus.equals("Looking for a driver...", ignoreCase = true) ||
                            freshStatus.equals("Driver is on the way", ignoreCase = true)
                    if (!canCancelTaxi) {
                        val msg = if (freshStatus.equals("Driver arrived", ignoreCase = true)) {
                            "You can't cancel the ride because the driver has already arrived."
                        } else if (freshStatus.equals("On trip", ignoreCase = true)) {
                            "You can't cancel the ride while on trip."
                        } else {
                            "This ride cannot be cancelled."
                        }
                        cannotCancelMessage = msg
                    } else {
                        cancelReasonInput = ""
                        showConfirmCancelDialog = true
                    }
                } else if (!isPlaced) {
                    val msg = if (isParcelOrPabili && (riderAssigned || !isPlaced)) {
                        "You can't cancel the order because the rider already accepted it."
                    } else {
                        "You can't cancel the order because the merchant already prepared the order."
                    }
                    cannotCancelMessage = msg
                } else {
                    cancelReasonInput = ""
                    showConfirmCancelDialog = true
                }
            }
        }

        val isDeliveredOrCompleted = order.status.isDeliveredOrCompleted() ||
                order.rawStatus.equals("Delivered", ignoreCase = true) ||
                order.rawStatus.equals("Completed", ignoreCase = true)

        val isTaxi = order.serviceType == ServiceType.TAXI
        fun dialPhone(phoneNumber: String, name: String) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Dialing $name: $phoneNumber", Toast.LENGTH_SHORT).show()
            }
        }

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

                // Multi-order switcher if tracking 2 or more orders simultaneously
                if (activeOrders.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Orders (${activeOrders.size}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        activeOrders.forEachIndexed { idx, o ->
                            val isSelected = o.id == order.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { onSelectOrder(o.id) }
                                    .testTag("expanded_select_order_${o.id}")
                            ) {
                                Text(
                                    text = "#${idx + 1} ${o.restaurant.name.take(14)} • ${o.status.title.take(10)}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

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
                                    com.example.data.model.ServiceType.TAXI -> "DELIVIO TAXI"
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

                        val isTaxi = order.serviceType == ServiceType.TAXI
                        val headerTitle = when {
                            isTaxi -> when {
                                order.rawStatus in listOf("Placed Order", "Looking for a driver...", "placed", "pending") -> "Looking for a driver..."
                                order.rawStatus.equals("Driver is on the way", true) -> "Driver is on the way"
                                order.rawStatus.equals("Driver arrived", true) -> "Driver arrived"
                                order.rawStatus.equals("On trip", true) -> "On trip"
                                order.rawStatus.equals("Completed", true) -> "Completed"
                                else -> order.rawStatus
                            }
                            else -> order.status.title
                        }
                        val headerSubtitle = when {
                            isTaxi -> when {
                                order.rawStatus in listOf("Placed Order", "Looking for a driver...", "placed", "pending") -> "Searching for nearby drivers"
                                order.rawStatus.equals("Driver is on the way", true) -> "Driver is heading to pickup point"
                                order.rawStatus.equals("Driver arrived", true) -> "Driver has arrived at pickup location"
                                order.rawStatus.equals("On trip", true) -> "Heading to destination"
                                order.rawStatus.equals("Completed", true) -> "Trip completed safely"
                                else -> order.status.subtitle
                            }
                            else -> order.status.subtitle
                        }

                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = headerSubtitle,
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

                // TIMELINE STEPPER
                StepperTimeline(
                    order = order,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val isPickup = order.fulfillment.equals(DelivioStatusConstants.FULFILLMENT_PICKUP, ignoreCase = true)

                // PICKUP LOCATION CARD OR DELIVERY MAP
                if (isPickup) {
                    MerchantPickupLocationCard(
                        restaurant = order.restaurant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    // CONDITIONAL MAP: SHOWN ONLY WHEN "Rider is on the way" OR whenever it is a TAXI order!
                    if (order.status.showMap() || isTaxi) {
                        Text(
                            text = if (isTaxi) "Live Taxi Route (OpenStreetMap)" else "Live Delivery Route (OpenStreetMap)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val pLat = order.pickupAddress?.lat ?: order.pabiliDetails?.storeLat?.toDouble() ?: 9.7392
                        val pLng = order.pickupAddress?.lng ?: order.pabiliDetails?.storeLng?.toDouble() ?: 118.7353
                        val dLat = order.deliveryAddress?.lat ?: 9.7420
                        val dLng = order.deliveryAddress?.lng ?: 118.7400

                        OsmOrderTrackingMap(
                            pickupName = if (isTaxi) "Pick Up: ${order.pickupAddress?.title ?: "My Location"}" else order.restaurant.name,
                            pickupAddress = order.pickupAddress?.fullAddress ?: order.restaurant.address,
                            pickupLat = pLat,
                            pickupLng = pLng,
                            dropoffName = order.deliveryAddress?.title?.ifBlank { "Delivery Destination" } ?: "Delivery Destination",
                            dropoffAddress = order.deliveryAddress?.fullAddress ?: "",
                            dropoffLat = dLat,
                            dropoffLng = dLng,
                            riderLat = order.riderLat,
                            riderLng = order.riderLng,
                            riderName = order.rider?.name,
                            orderStatus = if (isTaxi) order.rawStatus else order.status.title,
                            taxiStops = order.stops,
                            isTaxiOrder = isTaxi,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // If Delivered OR Completed: Show Rating prompt / Taxi completed card
                if (isDeliveredOrCompleted) {
                    if (isTaxi) {
                        TaxiTripCompletedCard(
                            order = order,
                            onDismiss = onDismissOrder,
                            onSubmitRating = { stars, comment ->
                                val fbUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
                                val effectiveCustId = fbUid ?: "customer"
                                orderViewModel?.submitReview(
                                    orderId = order.id,
                                    customerId = effectiveCustId,
                                    riderId = order.rider?.id,
                                    rating = stars,
                                    comment = comment
                                )
                            },
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        RatingCardContent(
                            restaurantName = order.restaurant.name,
                            riderName = if (isPickup) null else order.rider?.name,
                            onDismiss = onDismissOrder,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                if (isTaxi) {
                    // TAXI SPECIFIC SECTIONS:
                    // Driver Card (or Searching card)
                    TaxiDriverCard(
                        order = order,
                        onCall = { order.rider?.phone?.let { dialPhone(it, order.rider.name) } }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Taxi Route & Stops Summary
                    TaxiRouteSummaryCard(order = order)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Taxi Fare Breakdown Card
                    TaxiFareSummaryCard(order = order)
                } else {
                    // MERCHANT SECTION (Collapsible)
                    MerchantSection(
                        restaurant = order.restaurant,
                        isCollapsed = isMerchantCollapsed,
                        onToggleCollapse = onToggleMerchant,
                        onCall = onCallMerchant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // RIDER SECTION (Collapsible, shown from "Ready" onward, HIDDEN for pickup)
                    if (!isPickup && order.status.hasRider()) {
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
                }

                // CANCEL ORDER BUTTON (shown only for active orders; hidden once Delivered, Completed, Rejected, or Cancelled)
                val isTerminal = DelivioStatusConstants.isTerminal(order.rawStatus) ||
                        DelivioStatusConstants.isTerminal(order.status.title) ||
                        order.status == OrderStatus.DELIVERED ||
                        order.status == OrderStatus.COMPLETED ||
                        order.status == OrderStatus.CANCELLED ||
                        order.status == OrderStatus.REJECTED

                val canCancelThis = if (isTaxi) {
                    DelivioStatusConstants.canCancelTaxi(order.rawStatus)
                } else {
                    !isTerminal
                }

                if (canCancelThis) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { handleCancelClick() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ErrorRed
                        ),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_cancel_order")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Cancel order",
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isCheckingCancelStatus) "Checking status..." else if (isTaxi) "Cancel ride" else "Cancel order",
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Confetti overlay when Delivered or Completed
            if (isDeliveredOrCompleted) {
                ConfettiEffect(modifier = Modifier.fillMaxSize())
            }
        }

        // CONFIRM CANCEL DIALOG WITH OPTIONAL REASON
        if (showConfirmCancelDialog && orderViewModel != null) {
            AlertDialog(
                onDismissRequest = { showConfirmCancelDialog = false },
                title = {
                    Text("Cancel order?", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "Are you sure you want to cancel this order?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val quickReasons = if (order.serviceType == ServiceType.TAXI) {
                            listOf("Changed mind", "Driver too far", "Booked by mistake", "Found another ride")
                        } else {
                            listOf("Changed mind", "Placed by mistake", "Takes too long")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickReasons.forEach { r ->
                                val isSelected = cancelReasonInput == r
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { cancelReasonInput = r }
                                ) {
                                    Text(
                                        text = r,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cancelReasonInput,
                            onValueChange = { cancelReasonInput = it },
                            label = { Text("Reason (optional)") },
                            placeholder = { Text("e.g. Changed mind, placed by mistake") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_reason_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            orderViewModel.cancelOrder(
                                orderId = order.id,
                                reason = cancelReasonInput,
                                onSuccess = {
                                    showConfirmCancelDialog = false
                                    Toast.makeText(context, "Order cancelled", Toast.LENGTH_SHORT).show()
                                    onDismissOrder()
                                },
                                onCannotCancel = { msg ->
                                    showConfirmCancelDialog = false
                                    cannotCancelMessage = msg
                                },
                                onError = { err ->
                                    showConfirmCancelDialog = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_cancel_order_button")
                    ) {
                        Text("Cancel order", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmCancelDialog = false }) {
                        Text("Keep Order")
                    }
                }
            )
        }

        // POPUP DIALOG WHEN ORDER CANNOT BE CANCELLED
        if (cannotCancelMessage != null) {
            AlertDialog(
                onDismissRequest = { cannotCancelMessage = null },
                title = {
                    Text("Cannot Cancel Order", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = cannotCancelMessage!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { cannotCancelMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("cannot_cancel_ok_button")
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun StepperTimeline(
    order: Order,
    modifier: Modifier = Modifier
) {
    val isTaxi = order.serviceType == ServiceType.TAXI
    val flow = DelivioStatusConstants.getFlowForFulfillment(order.fulfillment, order.serviceType.name)
    val isPickup = order.fulfillment.equals(DelivioStatusConstants.FULFILLMENT_PICKUP, ignoreCase = true)

    val currentStepIndex = when {
        isTaxi -> when {
            order.rawStatus.equals("Completed", true) || order.status == OrderStatus.COMPLETED -> 4
            order.rawStatus.equals("On trip", true) -> 3
            order.rawStatus.equals("Driver arrived", true) -> 2
            order.rawStatus.equals("Driver is on the way", true) -> 1
            else -> 0
        }
        isPickup -> when {
            order.rawStatus.equals("Completed", true) || order.status == OrderStatus.COMPLETED -> 3
            order.rawStatus.equals("Ready", true) || order.status == OrderStatus.READY -> 2
            order.rawStatus.equals("Preparing", true) || order.status == OrderStatus.PREPARING -> 1
            else -> 0
        }
        else -> when {
            order.rawStatus.equals("Delivered", true) || order.status == OrderStatus.DELIVERED -> 4
            order.rawStatus.equals("Rider is on the way", true) || order.status == OrderStatus.ON_THE_WAY -> 3
            order.rawStatus.equals("Ready", true) || order.status == OrderStatus.READY -> 2
            order.rawStatus.equals("Preparing", true) || order.status == OrderStatus.PREPARING -> 1
            else -> 0
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        flow.forEachIndexed { index, stepName ->
            val isCurrent = index == currentStepIndex
            val isPassed = index < currentStepIndex
            val stepTitle = when {
                isTaxi && index == 0 -> "Looking for a driver..."
                isTaxi && stepName == DelivioStatusConstants.STATUS_DRIVER_ON_THE_WAY -> "Driver is on the way"
                isTaxi && stepName == DelivioStatusConstants.STATUS_DRIVER_ARRIVED -> "Driver arrived"
                isTaxi && stepName == DelivioStatusConstants.STATUS_ON_TRIP -> "On trip"
                isTaxi && stepName == DelivioStatusConstants.STATUS_COMPLETED -> "Completed"
                index == 0 -> DelivioStatusConstants.getFirstStepTitle(order.fulfillment, order.scheduledFor)
                else -> stepName
            }
            val stepSubtitle = when {
                isTaxi -> when (stepName) {
                    "Placed Order" -> "Searching for nearby drivers"
                    "Driver is on the way" -> "Driver is heading to pickup location"
                    "Driver arrived" -> "Driver is waiting at pickup location"
                    "On trip" -> {
                        val nextStop = order.stops.firstOrNull { !it.completed }?.label ?: order.deliveryAddress?.title ?: "Destination"
                        "Next stop: $nextStop"
                    }
                    "Completed" -> "Trip completed safely"
                    else -> ""
                }
                index == 2 && isPickup -> DelivioStatusConstants.getReadySubtitle(order.fulfillment)
                stepName == "Placed Order" -> "We received your order"
                stepName == "Preparing" -> "Merchant is preparing items"
                stepName == "Ready" -> if (isPickup) "Ready for pick up" else "Order is packaged"
                stepName == "Rider is on the way" -> "Heading to your destination"
                stepName == "Delivered" -> "Delivered safely! Enjoy"
                stepName == "Completed" -> "Picked up safely! Enjoy"
                else -> ""
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
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
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (index < flow.size - 1) {
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stepTitle,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = when {
                                isCurrent -> YellowPrimary
                                isPassed -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        if (isCurrent && stepSubtitle.isNotEmpty()) {
                            Text(
                                text = stepSubtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantPickupLocationCard(
    restaurant: com.example.data.model.Restaurant,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Pick Up Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = restaurant.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = restaurant.address,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    val uri = Uri.parse("geo:${restaurant.lat},${restaurant.lng}?q=${Uri.encode("${restaurant.name}, ${restaurant.address}")}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No map application available", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("pickup_directions_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get Directions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            if (order.serviceType == ServiceType.PABILI && order.finalReceiptTotal != null) {
                val finalItems = order.finalReceiptTotal
                val finalAmountDue = finalItems + order.deliveryFee + order.serviceFee

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Final items total (Receipt)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatPrice(finalItems), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatPrice(order.deliveryFee), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatPrice(order.serviceFee), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Final amount due",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = Formatters.formatPrice(finalAmountDue),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = YellowPrimary
                        )
                    }
                }
            } else {
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

            Spacer(modifier = Modifier.height(10.dp))
            val isWallet = order.paymentMethod.equals("wallet", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isWallet) SuccessGreen.copy(alpha = 0.15f) else YellowPrimary.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isWallet) Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (isWallet) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isWallet) "Paid with Delivio Wallet" else "Cash on Delivery - pay ₱${String.format(java.util.Locale.US, "%.2f", order.total)} to the rider",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWallet) SuccessGreen else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TaxiDriverCard(
    order: Order,
    onCall: () -> Unit
) {
    val rider = order.rider
    val vehicle = order.riderVehicle
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
            if (rider == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalTaxi,
                            contentDescription = "Taxi",
                            tint = YellowPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Looking for a driver...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Matching with nearby ${order.vehicleType ?: "taxi"} drivers in your city",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(YellowPrimary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Driver",
                                tint = YellowPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rider.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = StarGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${rider.rating}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val etaText = if (order.rawStatus.equals("Driver arrived", true)) {
                                "Driver has arrived!"
                            } else if (order.rawStatus.equals("On trip", true)) {
                                "On trip"
                            } else {
                                "ETA: ~${order.restaurant.etaMinutes.coerceAtLeast(3)} mins away"
                            }
                            Text(
                                text = etaText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = YellowPrimary
                            )
                        }
                    }

                    // Call driver button
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary)
                            .testTag("call_taxi_driver_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call Driver",
                            tint = TextOnYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                val vehicleTypeDisplay = vehicle?.vehicleType ?: order.vehicleType ?: "4 Seater Car"
                val modelColorDisplay = if (vehicle != null && vehicle.model.isNotEmpty()) {
                    "${vehicle.color} ${vehicle.model}".trim()
                } else {
                    order.rider?.vehicle ?: "Taxi"
                }
                val plateDisplay = vehicle?.plate?.ifEmpty { rider.licensePlate } ?: rider.licensePlate

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Model: $modelColorDisplay",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Type: $vehicleTypeDisplay",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = plateDisplay,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxiRouteSummaryCard(
    order: Order,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Route & Stops",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Pickup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = SuccessGreen,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Pick Up: ${order.pickupAddress?.title?.ifBlank { "Pickup Point" } ?: "Pickup Point"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = order.pickupAddress?.fullAddress ?: "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Next stops (if any)
            order.stops.forEach { stop ->
                Spacer(modifier = Modifier.height(8.dp))
                val isNextStop = order.rawStatus.equals("On trip", true) && !stop.completed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (stop.completed) Color.Gray else if (isNextStop) YellowPrimary else ErrorRed,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${stop.number}",
                                color = if (isNextStop) TextOnYellow else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Stop ${stop.number}: ${stop.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (stop.completed) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                            if (isNextStop) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = YellowPrimary
                                ) {
                                    Text(
                                        "NEXT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextOnYellow,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = stop.address,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Dropoff (Stop 2)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = ErrorRed,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Drop off: ${order.deliveryAddress?.title?.ifBlank { "Destination" } ?: "Destination"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = order.deliveryAddress?.fullAddress ?: "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TaxiFareSummaryCard(
    order: Order,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fare Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = YellowPrimary.copy(alpha = 0.2f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (order.paymentMethod.equals("wallet", true)) Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                            contentDescription = null,
                            tint = TextOnYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (order.paymentMethod.equals("wallet", true)) "Wallet" else "Cash",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ride Fare", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₱${String.format(java.util.Locale.US, "%.2f", order.subtotal)}", fontSize = 12.sp)
            }
            if (order.serviceFee > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Service Fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₱${String.format(java.util.Locale.US, "%.2f", order.serviceFee)}", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Fare (Locked)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "₱${String.format(java.util.Locale.US, "%.2f", order.total)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = YellowPrimary
                )
            }
        }
    }
}

@Composable
private fun TaxiTripCompletedCard(
    order: Order,
    onDismiss: () -> Unit,
    onSubmitRating: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var ratingSubmitted by remember { mutableStateOf(false) }

    fun shareTrip() {
        val driverName = order.rider?.name ?: "Assigned Driver"
        val plate = order.riderVehicle?.plate ?: order.rider?.licensePlate ?: "N/A"
        val vehicle = "${order.riderVehicle?.color ?: ""} ${order.riderVehicle?.model ?: order.rider?.vehicle ?: "Taxi"}".trim()
        val pickup = order.pickupAddress?.fullAddress ?: ""
        val dropoff = order.deliveryAddress?.fullAddress ?: ""
        val stopsInfo = if (order.stops.isNotEmpty()) {
            "\nStops: " + order.stops.joinToString(" -> ") { it.address }
        } else ""

        val shareText = "Delivio Taxi Trip Details\n" +
                "Driver: $driverName\n" +
                "Vehicle: $vehicle ($plate)\n" +
                "Pickup: $pickup\n" +
                "Drop-off: $dropoff$stopsInfo\n" +
                "Fare: ₱${String.format(java.util.Locale.US, "%.2f", order.total)}"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Trip")
        context.startActivity(shareIntent)
    }

    fun callEmergency() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Dialing 911", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trip Completed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Fare: ₱${String.format(java.util.Locale.US, "%.2f", order.total)} • ${if (order.paymentMethod.equals("wallet", true)) "Delivio Wallet (Paid)" else "Cash to driver"}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!ratingSubmitted) {
                Text("Rate your driver:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { rating = i },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= rating) StarGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Comment for driver (optional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSubmitRating(rating, comment)
                        ratingSubmitted = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Rating", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thank you for your rating!", fontWeight = FontWeight.Bold, color = SuccessGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { shareTrip() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share trip", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { callEmergency() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("911", fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
