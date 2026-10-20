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
                // Native osmdroid MapView with touch conflict disallowInterceptTouchEvent
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(17.0)
                            controller.setCenter(GeoPoint(confirmedLat, confirmedLng))
                            applyOsmTilesTheme(this, isDarkTheme)

                            // TOUCH CONFLICT FIX: Disallow parent scroll while touching the map
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }

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
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenLocationPicker(
                title = title,
                initialAddress = confirmedAddress,
                initialLat = confirmedLat,
                initialLng = confirmedLng,
                pinColor = pinColor,
                isDarkTheme = isDarkTheme,
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

/**
 * Complete Full-Screen Map View with:
 * - Close (X) button (closes without saving)
 * - Debounced Nominatim search bar with dropdown results list
 * - Stationary center pin fixed at the center while map pans under it
 * - Nominatim reverse geocode on pan settle
 * - Zoom (+) and (-) buttons
 * - My-location GPS button with runtime permission handling
 * - Bottom card with reverse-geocoded address and lat/lng
 * - Large "Confirm This Location" button returning values and closing view
 */
@Composable
private fun FullScreenLocationPicker(
    title: String,
    initialAddress: String,
    initialLat: Double,
    initialLng: Double,
    pinColor: Color,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (addressText: String, lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val titleSlug = title.lowercase().replace(" ", "_").replace("(", "").replace(")", "")

    var currentAddress by remember { mutableStateOf(initialAddress) }
    var currentLat by remember { mutableDoubleStateOf(initialLat) }
    var currentLng by remember { mutableDoubleStateOf(initialLng) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<NominatimResult>() }
    var isSearchResultsVisible by remember { mutableStateOf(false) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    // Intercept hardware Back button to close without saving
    BackHandler {
        onDismiss()
    }

    // Debounced Nominatim Search (500ms debounce)
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length < 2) {
            searchResults.clear()
            isSearchResultsVisible = false
            searchErrorMessage = null
            isSearching = false
            return@LaunchedEffect
        }

        delay(500L)
        isSearching = true
        searchErrorMessage = null

        val result = NominatimService.searchPlaces(searchQuery.trim())
        isSearching = false
        result.onSuccess { list ->
            searchResults.clear()
            searchResults.addAll(list)
            isSearchResultsVisible = list.isNotEmpty()
            if (list.isEmpty()) {
                searchErrorMessage = "No Philippine locations found for \"${searchQuery.trim()}\""
            }
        }.onFailure {
            searchResults.clear()
            isSearchResultsVisible = false
            searchErrorMessage = "Search unavailable. You can pan the map directly to pick your pin."
        }
    }

    // Device GPS Location Fetcher
    fun fetchDeviceLocation() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val targetLat = loc.latitude
                        val targetLng = loc.longitude
                        currentLat = targetLat
                        currentLng = targetLng
                        mapViewRef?.controller?.apply {
                            setZoom(17.0)
                            animateTo(GeoPoint(targetLat, targetLng))
                        }
                        coroutineScope.launch {
                            isReverseGeocoding = true
                            val revResult = NominatimService.reverseGeocode(targetLat, targetLng)
                            isReverseGeocoding = false
                            revResult.onSuccess { addr ->
                                currentAddress = addr
                            }
                        }
                    } else {
                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        if (gpsLoc != null) {
                            val targetLat = gpsLoc.latitude
                            val targetLng = gpsLoc.longitude
                            currentLat = targetLat
                            currentLng = targetLng
                            mapViewRef?.controller?.apply {
                                setZoom(17.0)
                                animateTo(GeoPoint(targetLat, targetLng))
                            }
                        } else {
                            Toast.makeText(context, "Turn on device GPS to detect current location.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Could not obtain GPS location.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Location access unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation()
        } else {
            Toast.makeText(context, "Location permission denied. You can pan the map to place the pin.", Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // FULL-SCREEN OSM MAP (Pans under fixed center pin)
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(currentLat, currentLng))
                        applyOsmTilesTheme(this, isDarkTheme)

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                isMoving = true
                                reverseGeocodeJob?.cancel()
                                reverseGeocodeJob = coroutineScope.launch {
                                    delay(450L) // Wait for pan settle
                                    isMoving = false
                                    val center = mapCenter
                                    if (center != null) {
                                        currentLat = center.latitude
                                        currentLng = center.longitude
                                        isReverseGeocoding = true
                                        val revResult = NominatimService.reverseGeocode(center.latitude, center.longitude)
                                        isReverseGeocoding = false
                                        revResult.onSuccess { addr ->
                                            currentAddress = addr
                                        }
                                    }
                                }
                                return true
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                return true
                            }
                        })
                        mapViewRef = this
                    }
                },
                update = { view ->
                    mapViewRef = view
                    applyOsmTilesTheme(view, isDarkTheme)
                },
                modifier = Modifier.fillMaxSize()
            )

            // FIXED CENTER PIN OVERLAY (Pin is fixed at the center while map pans under it)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (isMoving) (-18).dp else (-24).dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = pinColor,
                        shadowElevation = if (isMoving) 12.dp else 6.dp,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Center pin",
                                tint = TextOnYellow,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    // Pin needle shadow
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 6.dp)
                            .offset(y = 3.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.40f))
                    )
                }
            }

            // TOP BAR & SEARCH OVERLAY
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row with Close (X) Button and Title
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("full_screen_close_button_${titleSlug}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close full-screen map without saving",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "OSM • Zoom 17",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Nominatim Search Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search location in Philippines...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = YellowPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = YellowPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            searchResults.clear()
                                            isSearchResultsVisible = false
                                            searchErrorMessage = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("full_screen_search_input_${titleSlug}")
                    )
                }

                // Error Message
                if (searchErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = searchErrorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Dropdown Search Results List
                AnimatedVisibility(
                    visible = isSearchResultsVisible && searchResults.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(searchResults) { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentLat = result.lat
                                            currentLng = result.lon
                                            currentAddress = result.displayName
                                            mapViewRef?.controller?.apply {
                                                setZoom(17.0)
                                                animateTo(GeoPoint(result.lat, result.lon))
                                            }
                                            searchQuery = ""
                                            searchResults.clear()
                                            isSearchResultsVisible = false
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = YellowPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.shortName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = result.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // PANNING TIP OVERLAY (Center Top)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-75).dp)
            ) {
                Text(
                    text = if (isMoving) "Release to set pin..." else "Pan map under pin to place",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // FLOATING ACTION CONTROLS: My-Location & Zoom (+) / (-)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 190.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // My-Location Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            if (hasFine || hasCoarse) {
                                fetchDeviceLocation()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("full_screen_gps_button_${titleSlug}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My current location",
                            tint = YellowPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Zoom In (+)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("full_screen_zoom_in_${titleSlug}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom Out (-)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("full_screen_zoom_out_${titleSlug}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // BOTTOM CARD WITH REVERSE-GEOCODED ADDRESS & LARGE CONFIRM BUTTON
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Address Details Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pinColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = pinColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isReverseGeocoding) "Updating address from pin..." else currentAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Lat: ${String.format(Locale.US, "%.5f", currentLat)} • Lng: ${String.format(Locale.US, "%.5f", currentLng)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        if (isReverseGeocoding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = YellowPrimary
                            )
                        }
                    }

                    // LARGE "CONFIRM THIS LOCATION" BUTTON
                    Button(
                        onClick = {
                            onConfirm(currentAddress, currentLat, currentLng)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pinColor,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_location_button_${titleSlug}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirm This Location",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mapViewRef?.onDetach()
            } catch (_: Exception) {}
        }
    }
}
