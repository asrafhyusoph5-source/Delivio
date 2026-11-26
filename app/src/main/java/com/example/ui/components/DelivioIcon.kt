package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.DelivioIconConstants

val SoftYellowCircleBg = Color(0xFFFFF9E6)

@Composable
fun DelivioIcon(
    iconFileName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    fallbackIcon: ImageVector = Icons.Default.Category,
    fallbackTint: Color = TextOnYellow,
    backgroundColor: Color = SoftYellowCircleBg,
    shape: Shape = CircleShape,
    fallbackIconSize: Dp = size * 0.55f,
    iconPadding: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    val trimmed = remember(iconFileName) { iconFileName?.trim().orEmpty() }
    val fullUrl = remember(trimmed) {
        if (trimmed.isNotEmpty()) {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                DelivioIconConstants.ICON_BASE_URL + trimmed
            }
        } else null
    }

    if (fullUrl == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = fallbackTint,
                modifier = Modifier.size(fallbackIconSize)
            )
        }
        return
    }

    val context = LocalContext.current
    val imageRequest = remember(fullUrl) {
        ImageRequest.Builder(context)
            .data(fullUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(backgroundColor)
                    .shimmer()
                    .testTag("icon_loading_placeholder"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = YellowPrimary,
                    modifier = Modifier.size(size * 0.36f)
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = fallbackTint,
                    modifier = Modifier.size(fallbackIconSize)
                )
            }
        },
        success = {
            SubcomposeAsyncImageContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(iconPadding)
            )
        }
    )
}
