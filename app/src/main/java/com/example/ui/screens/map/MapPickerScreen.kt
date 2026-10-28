package com.example.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
fun applyOsmTilesTheme(mapView: MapView, isDark: Boolean) {
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
 * Single shared full-screen Map Location Picker.
 * Layout that can never be covered:
 * - Root is a Column with Modifier.fillMaxSize().systemBarsPadding().
 * - Children in order:
 *   (a) Top Bar: Close (X), Title, "OSM - Zoom 17" pill.
 *   (b) Search Bar: Debounced Nominatim search input and search results list when keyboard is open.
 *   (c) Map in Box with Modifier.weight(1f): stationary center pin, pan hint, zoom and my-location controls.
 *   (d) Bottom Panel as normal item below the map: one address line (ellipsize), coordinates, and 56dp full-width Confirm button.
 */
@Composable
fun MapPickerScreen(
    title: String,
    initialAddress: String = "",
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    pinColor: Color = YellowPrimary,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: (address: String, lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isSystemDark = isSystemInDarkTheme()
    val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isDarkTheme = isSystemDark || isAppDark

    var currentAddress by remember(initialAddress) {
        mutableStateOf(initialAddress.ifBlank { "Puerto Princesa, Palawan" })
    }
    var currentLat by remember(initialLat) {
        mutableDoubleStateOf(if (initialLat != 0.0) initialLat else 9.7392)
    }
    var currentLng by remember(initialLng) {
        mutableDoubleStateOf(if (initialLng != 0.0) initialLng else 118.7353)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<NominatimResult>() }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    // Intercept back button to dismiss
    BackHandler {
        onDismiss()
    }

    // Initialize osmdroid
    LaunchedEffect(Unit) {
        OsmConfig.init(context)
        if (initialAddress.isBlank() && currentLat != 0.0 && currentLng != 0.0) {
            val res = NominatimService.reverseGeocode(currentLat, currentLng)
            res.onSuccess { addr -> currentAddress = addr }
        }
    }

    // Debounced Nominatim Search (500ms debounce)
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length < 2) {
            searchResults.clear()
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
            if (list.isEmpty()) {
                searchErrorMessage = "No Philippine locations found for \"${searchQuery.trim()}\""
            }
        }.onFailure {
            searchResults.clear()
            searchErrorMessage = "Search unavailable. Pan the map directly to place pin."
        }
    }

    // Device GPS location fetcher
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
                            Toast.makeText(context, "Turn on device GPS to detect location.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Location permission denied. You can pan the map directly.", Toast.LENGTH_LONG).show()
        }
    }

    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

    // Root is a Column with Modifier.fillMaxSize().systemBarsPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // (a) TOP BAR: (X, title, "OSM - Zoom 17")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_map_picker_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close location picker without saving",
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
                    text = "OSM - Zoom 17",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // (b) SEARCH BAR
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search street, landmark, or barangay...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = YellowPrimary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        searchResults.clear()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = YellowPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("input_map_picker_search")
        )

        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = YellowPrimary
                )
                Text(
                    text = "Searching Philippine locations...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (searchErrorMessage != null) {
            Text(
                text = searchErrorMessage!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Show search results list when keyboard is open
        if (isKeyboardOpen && searchResults.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(max = 220.dp)
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
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = YellowPrimary,
                                modifier = Modifier.size(18.dp)
                            )
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

        // (c) THE MAP IN A BOX WITH Modifier.weight(1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Full-screen native osmdroid MapView
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

            // Center Pin
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
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 6.dp)
                            .offset(y = 3.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.40f))
                    )
                }
            }

            // "Pan map under pin to place" hint (Center Top)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = if (isMoving) "Release to set pin..." else "Pan map under pin to place",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Zoom and My-location buttons inside the map's bottom-right corner (never overlaps bottom panel)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // My-Location Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp)
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
                            .testTag("btn_map_picker_gps")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My location",
                            tint = YellowPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Zoom (+)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomIn() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("btn_map_picker_zoom_in")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom (-)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = { mapViewRef?.controller?.zoomOut() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("btn_map_picker_zoom_out")
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
        }

        // (d) BOTTOM PANEL AS A NORMAL COLUMN ITEM BELOW THE MAP
        // Hidden when keyboard is open to allow full space for search results
        if (!isKeyboardOpen) {
            val navBarsBottomInsetPx = WindowInsets.navigationBars.getBottom(density)
            val navigationBarsBottomInset = with(density) { navBarsBottomInsetPx.toDp() }
            val bottomMargin = maxOf(navigationBarsBottomInset, 24.dp) + 16.dp

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = bottomMargin)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Address line (one address line, ellipsize) with coordinates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pinColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = pinColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isReverseGeocoding) "Updating location..." else currentAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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

                    // Large Confirm Button: 56dp tall, full width, always fully visible and tappable
                    Button(
                        onClick = {
                            onConfirm(currentAddress, currentLat, currentLng)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btn_confirm_map_location")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirm This Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
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
