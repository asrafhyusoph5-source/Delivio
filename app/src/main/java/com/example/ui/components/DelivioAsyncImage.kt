package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.YellowPrimary

/**
 * Universal AsyncImage wrapper powered by Coil.
 * - Trims spaces from every URL
 * - Shows a loading placeholder while fetching
 * - On failure shows a small "Image unavailable" icon and logs URL & error via Log.e("ImageLoad", ...)
 * - Never shows emoji or gradient placeholders when an image URL exists
 */
@Composable
fun DelivioAsyncImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showUnavailableLabel: Boolean = false,
    errorIconSize: Dp = 22.dp,
    fallback: @Composable (() -> Unit)? = null
) {
    val trimmedUrl = remember(imageUrl) { imageUrl?.trim().orEmpty() }

    if (trimmedUrl.isEmpty()) {
        if (fallback != null) {
            fallback()
        } else {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription ?: "Image placeholder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(errorIconSize)
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val imageRequest = remember(trimmedUrl) {
        ImageRequest.Builder(context)
            .data(trimmedUrl)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = YellowPrimary,
                    strokeWidth = 2.dp
                )
            }
        },
        error = { errorState ->
            val error = errorState.result.throwable
            Log.e("ImageLoad", "Failed to load image from URL: '$trimmedUrl': ${error.message}", error)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Image unavailable",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(errorIconSize)
                    )
                    if (showUnavailableLabel) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Image unavailable",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}
