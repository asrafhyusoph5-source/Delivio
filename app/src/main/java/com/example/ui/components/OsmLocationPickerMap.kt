package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/**
 * Modern OpenStreetMap (osmdroid) Location Picker with:
 * - Search bar with 500ms debounce (Nominatim API, countrycodes=ph)
 * - Draggable/panning map with stationary center pin
 * - Nominatim reverse geocode on map pan settle
 * - "Use my current location" GPS button with runtime permission handling
 * - Location confirmation button that returns address, lat, and lng
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
    mapHeightDp: Int = 260,
    showConfirmButton: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentAddress by remember { mutableStateOf(initialAddress.ifBlank { "Puerto Princesa, Palawan" }) }
    var currentLat by remember { mutableDoubleStateOf(if (initialLat != 0.0) initialLat else 9.7392) }
    var currentLng by remember { mutableDoubleStateOf(if (initialLng != 0.0) initialLng else 118.7353) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<NominatimResult>() }
    var isSearchResultsVisible by remember { mutableStateOf(false) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    // Init osmdroid cache and user-agent
    LaunchedEffect(Unit) {
        OsmConfig.init(context)
        if (initialAddress.isBlank()) {
            // Trigger initial reverse geocoding
            val result = NominatimService.reverseGeocode(currentLat, currentLng)
            result.onSuccess { addr ->
                currentAddress = addr
            }
        }
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

        delay(500L) // 500ms debounce
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

    // GPS Location Fetcher
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
                                onLocationConfirmed(addr, targetLat, targetLng)
                            }
                        }
                    } else {
                        // Fallback to LocationManager
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
                            Toast.makeText(context, "Turn on device location/GPS to detect current location.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Could not obtain current location. Please select on map.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Location permission denied. You can still pan the map to set your address.", Toast.LENGTH_LONG).show()
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with Title Badge
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

            // Nominatim Search Bar
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search places in Philippines...", fontSize = 13.sp) },
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
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_search_input_${title.replace(" ", "_").lowercase()}")
                )
            }

            // Friendly error message if search fails or offline
            if (searchErrorMessage != null) {
                Text(
                    text = searchErrorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Visible Search Results List (Overlaid Dropdown)
            AnimatedVisibility(
                visible = isSearchResultsVisible && searchResults.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
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
                                        onLocationConfirmed(result.displayName, result.lat, result.lon)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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

            // Map View Box with Center Pin and Floating Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeightDp.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                // Native osmdroid MapView hosted via AndroidView
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(16.0)
                            controller.setCenter(GeoPoint(currentLat, currentLng))
                            
                            addMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    isMoving = true
                                    reverseGeocodeJob?.cancel()
                                    reverseGeocodeJob = coroutineScope.launch {
                                        delay(450L) // Wait for map movement to settle
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
                                                onLocationConfirmed(addr, center.latitude, center.longitude)
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
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // STATIONARY CENTER PIN OVERLAY (stays centered while user pans)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = if (isMoving) (-12).dp else (-18).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = pinColor,
                            shadowElevation = if (isMoving) 8.dp else 4.dp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Center pin",
                                    tint = TextOnYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        // Pin needle shadow
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 4.dp)
                                .offset(y = 2.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }

                // Floating Action Controls: Current Location & Zoom Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "Use My Current Location" GPS Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(38.dp)
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
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Use my current location",
                                tint = YellowPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Zoom In
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomIn() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Zoom Out
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomOut() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Tip banner overlay at top
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = if (isMoving) "Release to set address..." else "Pan map to reposition pin",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Reverse-Geocoded Address Display & Coordinates
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
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
                            text = if (isReverseGeocoding) "Updating address..." else currentAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lat: ${String.format("%.5f", currentLat)} • Lng: ${String.format("%.5f", currentLng)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    if (isReverseGeocoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = YellowPrimary
                        )
                    }
                }
            }

            // Confirm Button (Saves address text + lat/lng)
            if (showConfirmButton) {
                Button(
                    onClick = {
                        onLocationConfirmed(currentAddress, currentLat, currentLng)
                        Toast.makeText(context, "Location saved: $currentAddress", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pinColor,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("confirm_location_button_${title.replace(" ", "_").lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Confirm This Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
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
