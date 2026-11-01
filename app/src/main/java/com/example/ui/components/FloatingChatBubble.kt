package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.SupportTicket
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import kotlin.math.roundToInt

@Composable
fun FloatingChatBubble(
    unresolvedTickets: List<SupportTicket>,
    hasActiveOrderTracker: Boolean,
    onClick: (SupportTicket) -> Unit,
    modifier: Modifier = Modifier
) {
    if (unresolvedTickets.isEmpty()) return

    val density = LocalDensity.current
    val bubbleSize = 56.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Bottom bar padding (~80dp) + tracker offset if active (~140dp)
        val defaultBottomMarginPx = with(density) {
            if (hasActiveOrderTracker) 180.dp.toPx() else 100.dp.toPx()
        }
        val defaultRightMarginPx = with(density) { 16.dp.toPx() }

        var offsetX by remember { mutableFloatStateOf(screenWidthPx - bubbleSizePx - defaultRightMarginPx) }
        var offsetY by remember { mutableFloatStateOf(screenHeightPx - bubbleSizePx - defaultBottomMarginPx) }

        val animatedOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = spring(), label = "bubbleX")
        val animatedOffsetY by animateFloatAsState(targetValue = offsetY, animationSpec = spring(), label = "bubbleY")

        val hasUnread = unresolvedTickets.any { it.unreadByCustomer }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                .size(bubbleSize)
                .shadow(8.dp, CircleShape)
                .background(YellowPrimary, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap to nearest edge (left or right)
                            val center = screenWidthPx / 2f
                            offsetX = if (offsetX + bubbleSizePx / 2f < center) {
                                with(density) { 16.dp.toPx() }
                            } else {
                                screenWidthPx - bubbleSizePx - with(density) { 16.dp.toPx() }
                            }
                            // Keep Y inside screen bounds
                            val minY = with(density) { 60.dp.toPx() }
                            val maxY = screenHeightPx - bubbleSizePx - with(density) { 80.dp.toPx() }
                            offsetY = offsetY.coerceIn(minY, maxY)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - bubbleSizePx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - bubbleSizePx)
                    }
                }
                .clickable {
                    onClick(unresolvedTickets.first())
                }
                .testTag("floating_support_chat_bubble"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = "Support Chat",
                tint = TextOnYellow,
                modifier = Modifier.size(30.dp)
            )

            // Unread dot
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(14.dp)
                        .background(ErrorRed, CircleShape)
                )
            }
        }
    }
}
