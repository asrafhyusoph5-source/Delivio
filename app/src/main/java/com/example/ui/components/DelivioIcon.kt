package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
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
    fallbackTint: Color = TextOnYellow
) {
    val fullUrl = remember(iconFileName) {
        if (!iconFileName.isNullOrBlank()) {
            if (iconFileName.startsWith("http://") || iconFileName.startsWith("https://")) {
                iconFileName
            } else {
                DelivioIconConstants.ICON_BASE_URL + iconFileName
            }
        } else null
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
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
        loading = {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(SoftYellowCircleBg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = YellowPrimary,
                    modifier = Modifier.size(size * 0.4f)
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(SoftYellowCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = fallbackTint,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    )
}
