package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class ConfettiParticle(
    val startX: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
        )
    }

    val colors = listOf(
        Color(0xFFFFC107),
        Color(0xFFFF5722),
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFE91E63),
        Color(0xFFFFEB3B)
    )

    val particles = remember {
        List(60) {
            ConfettiParticle(
                startX = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.3f,
                speedY = 0.5f + Random.nextFloat() * 0.7f,
                color = colors[Random.nextInt(colors.size)],
                size = 12f + Random.nextFloat() * 14f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val curX = (p.startX + p.speedX * t) * w
            val curY = (p.speedY * t) * h
            val alpha = (1f - t).coerceIn(0f, 1f)

            drawRect(
                color = p.color.copy(alpha = alpha),
                topLeft = Offset(curX, curY),
                size = Size(p.size, p.size * 0.6f)
            )
        }
    }
}
