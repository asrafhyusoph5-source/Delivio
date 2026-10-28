package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.location.LocationManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.api.NominatimResult
import com.example.data.api.NominatimService
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.ui.screens.map.MapPickerScreen
import com.example.util.OsmConfig
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

/**
 * Applies dark mode tile inversion to osmdroid MapView for seamless dark theme support.
 */
private fun applyOsmTilesTheme(mapView: MapView, isDark: Boolean) {
    try {
        if (isDark) {
            val inverseMatrix = ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 255f,
                    0f, -0.85f, 0f, 0f, 255f,
                    0f, 0f, -0.85f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
    } catch (_: Exception) {}
}

/**
 * Modern OpenStreetMap (osmdroid) Location Picker.
 *
 * Embedded Small Map features:
 * - Preview of the confirmed location with default zoom 17.
 * - Top-right "Expand" (zoom-in) icon button opens a full-screen map picker.
 * - Overlay hint: "Tap the expand icon to change".
 * - Touch conflict fix: requestDisallowInterceptTouchEvent prevents the parent form from scrolling while touching the map.
 * - OpenStreetMap tiles with dark mode support.
 *
 * Full-screen Map View features:
 * - Close (X) button (closes without saving).
 * - Debounced Nominatim search bar with auto-completing results list.
 * - Fixed center pin while the map pans under it with smooth reverse geocoding on settle.
 * - Zoom (+) and (-) buttons.
 * - My-location GPS button.
 * - Bottom card with reverse-geocoded address and lat/lng.
 * - Large "Confirm This Location" button returning confirmed address and coordinates.
 */
@Composable
fun OsmLocationPickerMap(
    title: String,
    initialAddress: String = "",
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    pinColor: Color = YellowPrimary,
    onLocationConfirmed: (addressText: String, lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
    mapHeightDp: Int = 200,
    showConfirmButton: Boolean = true
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isDarkTheme = isSystemDark || isAppDark
    val titleSlug = title.lowercase().replace(" ", "_").replace("(", "").replace(")", "")

    var confirmedAddress by remember(initialAddress) {
        mutableStateOf(initialAddress.ifBlank { "Puerto Princesa, Palawan" })
    }
    var confirmedLat by remember(initialLat) {
        mutableDoubleStateOf(if (initialLat != 0.0) initialLat else 9.7392)
    }
    var confirmedLng by remember(initialLng) {
        mutableDoubleStateOf(if (initialLng != 0.0) initialLng else 118.7353)
    }

    var isFullScreenOpen by remember { mutableStateOf(false) }
    var smallMapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Init osmdroid cache and configuration
    LaunchedEffect(Unit) {
        OsmConfig.init(context)
        if (initialAddress.isBlank() && confirmedLat != 0.0 && confirmedLng != 0.0) {
            val result = NominatimService.reverseGeocode(confirmedLat, confirmedLng)
            result.onSuccess { addr ->
                confirmedAddress = addr
            }
        }
    }

    // Keep small map centered when confirmed coordinates change
    LaunchedEffect(confirmedLat, confirmedLng) {
        smallMapViewRef?.controller?.apply {
            setZoom(17.0)
            setCenter(GeoPoint(confirmedLat, confirmedLng))
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with Title & Expand Prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = pinColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "OpenStreetMap",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // SMALL MAP PREVIEW CONTAINER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeightDp.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                // Native osmdroid MapView - Non-interactive preview that does not steal touch events
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false)
                            isTilesScaledToDpi = true
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(17.0)
                            controller.setCenter(GeoPoint(confirmedLat, confirmedLng))
                            isClickable = false
                            isFocusable = false
                            applyOsmTilesTheme(this, isDarkTheme)
                            smallMapViewRef = this
                        }
                    },
                    update = { view ->
                        smallMapViewRef = view
                        applyOsmTilesTheme(view, isDarkTheme)
                        view.controller.setCenter(GeoPoint(confirmedLat, confirmedLng))
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Transparent clickable overlay: drag scrolls parent page, tap opens full-screen picker
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { isFullScreenOpen = true }
                )

                // STATIONARY PIN PREVIEW AT CENTER
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-14).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = pinColor,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Confirmed pin",
                                    tint = TextOnYellow,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 4.dp)
                                .offset(y = 2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }

                // TOP RIGHT EXPAND (ZOOM-IN) ICON BUTTON
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                ) {
                    IconButton(
                        onClick = { isFullScreenOpen = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("expand_map_button_${titleSlug}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand to full-screen map",
                            tint = pinColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // PREVIEW HINT: "Tap the expand icon to change"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clickable { isFullScreenOpen = true }
                        .testTag("expand_hint_pill_${titleSlug}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Tap the expand icon to change",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Confirmed Address & Coordinates Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFullScreenOpen = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = pinColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = confirmedAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lat: ${String.format(Locale.US, "%.5f", confirmedLat)} • Lng: ${String.format(Locale.US, "%.5f", confirmedLng)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = { isFullScreenOpen = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand map",
                            tint = pinColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Optional Inline Confirm Button (if required by caller)
            if (showConfirmButton) {
                Button(
                    onClick = {
                        onLocationConfirmed(confirmedAddress, confirmedLat, confirmedLng)
                        Toast.makeText(context, "Location confirmed: $confirmedAddress", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pinColor,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("inline_confirm_location_button_${titleSlug}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Confirm Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // FULL-SCREEN MAP VIEW DIALOG
    if (isFullScreenOpen) {
        Dialog(
            onDismissRequest = {
                // Back or outside dismissal closes without saving
                isFullScreenOpen = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true
            )
        ) {
            MapPickerScreen(
                title = title,
                initialAddress = confirmedAddress,
                initialLat = confirmedLat,
                initialLng = confirmedLng,
                pinColor = pinColor,
                onDismiss = {
                    // Close (X) closes without saving
                    isFullScreenOpen = false
                },
                onConfirm = { newAddress, newLat, newLng ->
                    // Confirm saves address, lat, and lng into the form
                    confirmedAddress = newAddress
                    confirmedLat = newLat
                    confirmedLng = newLng
                    onLocationConfirmed(newAddress, newLat, newLng)
                    isFullScreenOpen = false
                    Toast.makeText(context, "Location confirmed!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                smallMapViewRef?.onDetach()
            } catch (_: Exception) {}
        }
    }
}
