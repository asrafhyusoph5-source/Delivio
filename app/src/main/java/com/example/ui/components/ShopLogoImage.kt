package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Displays a shop's logo image cropped to fill its container.
 * When [logoUrl] is blank or fails to load, renders a neutral gradient with the shop's first letter.
 */
@Composable
fun ShopLogoImage(
    logoUrl: String?,
    shopName: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 38.sp
) {
    val trimmedUrl = remember(logoUrl) { logoUrl?.trim().orEmpty() }
    val firstLetter = remember(shopName) {
        shopName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
    }

    if (trimmedUrl.isEmpty()) {
        ShopLogoFallback(firstLetter = firstLetter, letterSize = letterSize, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val request = remember(trimmedUrl) {
        ImageRequest.Builder(context)
            .data(trimmedUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = shopName,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmer()
            )
        },
        error = { state ->
            Log.w("ImageLoad", "Failed loading shop logo '$trimmedUrl': ${state.result.throwable.message}")
            ShopLogoFallback(firstLetter = firstLetter, letterSize = letterSize, modifier = Modifier.fillMaxSize())
        }
    )
}

@Composable
private fun ShopLogoFallback(
    firstLetter: String,
    letterSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val topColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    val bottomColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(colors = listOf(topColor, bottomColor))
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter,
            fontSize = letterSize,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Displays a category's admin-configured image URL, falling back to a neutral circular icon
 * showing the category's first letter if the URL is blank or fails to load.
 */
@Composable
fun CategoryIconImage(
    imageUrl: String?,
    categoryName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val trimmedUrl = remember(imageUrl) { imageUrl?.trim().orEmpty() }
    val firstLetter = remember(categoryName) {
        categoryName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
    }

    if (trimmedUrl.isEmpty()) {
        CategoryLetterBadge(firstLetter = firstLetter, size = size, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val request = remember(trimmedUrl) {
        ImageRequest.Builder(context)
            .data(trimmedUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = categoryName,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        loading = {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .shimmer()
            )
        },
        error = { state ->
            Log.w("ImageLoad", "Failed loading category icon '$trimmedUrl': ${state.result.throwable.message}")
            CategoryLetterBadge(firstLetter = firstLetter, size = size)
        }
    )
}

@Composable
private fun CategoryLetterBadge(
    firstLetter: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE9ECEF)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstLetter,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF495057)
        )
    }
}
