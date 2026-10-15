package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Fully functional Real OpenStreetMap Interactive Location Picker.
 * Directly integrates real OpenStreetMap tiles with pan, zoom, draggable pin,
 * and live coordinate updates.
 */
@Composable
fun InteractiveLocationPickerMap(
    title: String,
    initialAddress: String,
    pinColor: Color = YellowPrimary,
    onLocationSelected: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf(initialAddress) }
    var lat by remember { mutableDoubleStateOf(14.5547) }
    var lng by remember { mutableDoubleStateOf(121.0244) }

    val hexColor = remember(pinColor) {
        val r = (pinColor.red * 255).toInt().coerceIn(0, 255)
        val g = (pinColor.green * 255).toInt().coerceIn(0, 255)
        val b = (pinColor.blue * 255).toInt().coerceIn(0, 255)
        "#%02X%02X%02X".format(r, g, b)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("location_picker_map_${title.replace(" ", "_")}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Real OpenStreetMap Map Component
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 340.dp else 220.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            RealMapView(
                initialLat = lat,
                initialLng = lng,
                initialZoom = 15,
                markerTitle = title,
                pinHexColor = hexColor,
                isInteractive = true,
                showControls = true,
                showQuickLocations = true,
                onLocationChanged = { newLat, newLng, newAddr ->
                    lat = newLat
                    lng = newLng
                    selectedAddress = newAddr
                    onLocationSelected(newAddr, newLat.toFloat(), newLng.toFloat())
                },
                modifier = Modifier.fillMaxSize()
            )

            // Header Overlay with Pin Title & Expand/Collapse Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = pinColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Expand / Collapse map height
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isExpanded) "Shrink Map" else "Expand Map",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Location & Coordinate info footer bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF43A047),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (selectedAddress.isNotBlank()) selectedAddress else initialAddress,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Text(
                    text = "Tap or drag map to set",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
