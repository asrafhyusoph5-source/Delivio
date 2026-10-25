package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.api.NominatimResult
import com.example.data.api.NominatimService
import com.example.data.api.OsrmRoutingService
import com.example.data.api.RouteResult
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.OsmConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Live Order Tracking Map using osmdroid (OpenStreetMap).
 * Shows:
 * 1. Store / Pickup Pin
 * 2. Delivery / Drop-off Pin
 * 3. Live Rider Location Marker
 * 4. OSRM driving route polyline (with straight-line fallback)
 * 5. ETA and distance in km
 * 6. Nominatim Philippine search bar with 500ms debounce
 */
@Composable
fun OsmOrderTrackingMap(
    pickupName: String,
    pickupAddress: String,
    pickupLat: Double,
    pickupLng: Double,
    dropoffName: String,
    dropoffAddress: String,
    dropoffLat: Double,
    dropoffLng: Double,
    taxiStops: List<com.example.data.model.TaxiStop> = emptyList(),
    isTaxiOrder: Boolean = false,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderName: String? = null,
    orderStatus: String = "Rider is delivering",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var riderMarker by remember { mutableStateOf<Marker?>(null) }

    var distanceKm by remember { mutableStateOf("...") }
    var etaMinutes by remember { mutableIntStateOf(10) }
    var isFallbackRoute by remember { mutableStateOf(false) }

    // Search bar state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<NominatimResult>() }
    var isSearchResultsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        OsmConfig.init(context)
    }

    // Debounced Nominatim search on tracking map
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
                searchErrorMessage = "No locations found for \"${searchQuery.trim()}\""
            }
        }.onFailure {
            searchResults.clear()
            isSearchResultsVisible = false
            searchErrorMessage = "Search unavailable offline."
        }
    }

    // Calculate OSRM Route (multi-point if taxi order)
    LaunchedEffect(pickupLat, pickupLng, dropoffLat, dropoffLng, taxiStops.size, isTaxiOrder) {
        val effectiveStartLat = if (pickupLat != 0.0) pickupLat else 9.7392
        val effectiveStartLng = if (pickupLng != 0.0) pickupLng else 118.7353
        val effectiveEndLat = if (dropoffLat != 0.0) dropoffLat else 9.7420
        val effectiveEndLng = if (dropoffLng != 0.0) dropoffLng else 118.7400

        val routeResult = if (isTaxiOrder && taxiStops.isNotEmpty()) {
            val allPoints = mutableListOf<Pair<Double, Double>>()
            allPoints.add(effectiveStartLat to effectiveStartLng)
            allPoints.add(effectiveEndLat to effectiveEndLng)
            taxiStops.forEach { s ->
                if (s.lat != 0.0 && s.lng != 0.0) {
                    allPoints.add(s.lat to s.lng)
                }
            }
            OsrmRoutingService.calculateMultiPointRoute(allPoints)
        } else {
            OsrmRoutingService.calculateRoute(
                startLat = effectiveStartLat,
                startLng = effectiveStartLng,
                endLat = effectiveEndLat,
                endLng = effectiveEndLng
            )
        }

        distanceKm = "${routeResult.distanceKm} km"
        etaMinutes = routeResult.durationMinutes
        isFallbackRoute = routeResult.isFallback

        // Update map overlays
        mapViewRef?.let { map ->
            updateMapOverlays(
                map = map,
                context = context,
                routeResult = routeResult,
                pickupName = pickupName,
                pickupLat = effectiveStartLat,
                pickupLng = effectiveStartLng,
                dropoffName = dropoffName,
                dropoffLat = effectiveEndLat,
                dropoffLng = effectiveEndLng,
                taxiStops = taxiStops,
                isTaxiOrder = isTaxiOrder,
                riderLat = riderLat,
                riderLng = riderLng,
                riderName = riderName,
                onPolylineCreated = { routePolyline = it },
                onRiderMarkerCreated = { riderMarker = it }
            )
        }
    }

    // Live update rider marker when rider coordinates push from Firestore
    LaunchedEffect(riderLat, riderLng) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            mapViewRef?.let { map ->
                val riderPos = GeoPoint(riderLat, riderLng)
                if (riderMarker != null) {
                    riderMarker?.position = riderPos
                    map.invalidate()
                } else {
                    val marker = Marker(map).apply {
                        position = riderPos
                        title = "Rider: ${riderName ?: "Delivio Driver"}"
                        snippet = "Live delivery GPS location"
                        icon = createCustomIcon(context, 0xFFFFA000.toInt(), "🛵")
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(marker)
                    riderMarker = marker
                    map.invalidate()
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with Live ETA & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Text(
                        text = "Live Tracking",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = YellowPrimary.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "ETA: ~$etaMinutes mins",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = YellowPrimary
                        )
                        Text(
                            text = "($distanceKm)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Bar for inspecting locations on tracking map
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search location on map...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; searchResults.clear(); isSearchResultsVisible = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("tracking_map_search_input")
                )
            }

            if (searchErrorMessage != null) {
                Text(text = searchErrorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }

            // Search Results overlay
            AnimatedVisibility(visible = isSearchResultsVisible && searchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(searchResults) { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mapViewRef?.controller?.apply {
                                            setZoom(16.0)
                                            animateTo(GeoPoint(res.lat, res.lon))
                                        }
                                        searchQuery = ""
                                        searchResults.clear()
                                        isSearchResultsVisible = false
                                        focusManager.clearFocus()
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(res.displayName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Osmdroid Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                            val startLat = if (pickupLat != 0.0) pickupLat else 9.7392
                            val startLng = if (pickupLng != 0.0) pickupLng else 118.7353
                            controller.setZoom(14.5)
                            controller.setCenter(GeoPoint(startLat, startLng))

                            mapViewRef = this
                        }
                    },
                    update = { view ->
                        mapViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Zoom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.size(28.dp)) {
                        IconButton(onClick = { mapViewRef?.controller?.zoomIn() }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(14.dp))
                        }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.size(28.dp)) {
                        IconButton(onClick = { mapViewRef?.controller?.zoomOut() }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Legend / Route Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📍 Store", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = YellowPrimary)
                    Text("➔", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("🛵 Rider", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                    Text("➔", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("🏁 You", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }

                Text(
                    text = if (isFallbackRoute) "Direct path route" else "OSRM Road Navigation",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun updateMapOverlays(
    map: MapView,
    context: android.content.Context,
    routeResult: RouteResult,
    pickupName: String,
    pickupLat: Double,
    pickupLng: Double,
    dropoffName: String,
    dropoffLat: Double,
    dropoffLng: Double,
    taxiStops: List<com.example.data.model.TaxiStop> = emptyList(),
    isTaxiOrder: Boolean = false,
    riderLat: Double?,
    riderLng: Double?,
    riderName: String?,
    onPolylineCreated: (Polyline) -> Unit,
    onRiderMarkerCreated: (Marker) -> Unit
) {
    map.overlays.clear()

    // 1. Pickup Marker (Store/Merchant or Taxi Pickup)
    val pickupColor = if (isTaxiOrder) 0xFF43A047.toInt() else 0xFFE53935.toInt() // Green for Taxi, Red for Store
    val pickupEmoji = if (isTaxiOrder) "📍" else "🏪"
    val pickupMarker = Marker(map).apply {
        position = GeoPoint(pickupLat, pickupLng)
        title = "Pickup: $pickupName"
        snippet = if (isTaxiOrder) "Passenger Pick Up" else "Store Location"
        icon = createCustomIcon(context, pickupColor, pickupEmoji)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }
    map.overlays.add(pickupMarker)

    // 2. Dropoff Marker (#2 for Taxi, Red)
    val dropoffColor = if (isTaxiOrder) 0xFFE53935.toInt() else 0xFF43A047.toInt()
    val dropoffEmoji = if (isTaxiOrder) "2" else "🏠"
    val dropoffMarker = Marker(map).apply {
        position = GeoPoint(dropoffLat, dropoffLng)
        title = if (isTaxiOrder) "Stop #2 (Drop-off): $dropoffName" else "Drop-off: $dropoffName"
        snippet = "Destination"
        icon = createCustomIcon(context, dropoffColor, dropoffEmoji)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }
    map.overlays.add(dropoffMarker)

    // 3. Extra Taxi Stops (Numbered 3, 4, 5... completed stops greyed out)
    if (isTaxiOrder) {
        taxiStops.forEach { stop ->
            if (stop.lat != 0.0 && stop.lng != 0.0) {
                val stopColor = if (stop.completed) 0xFF9E9E9E.toInt() else 0xFFE53935.toInt()
                val stopMarker = Marker(map).apply {
                    position = GeoPoint(stop.lat, stop.lng)
                    title = "Stop #${stop.number}: ${stop.label}"
                    snippet = stop.address
                    icon = createCustomIcon(context, stopColor, "${stop.number}")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(stopMarker)
            }
        }
    }

    // 4. Rider Marker (if available)
    if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
        val riderM = Marker(map).apply {
            position = GeoPoint(riderLat, riderLng)
            title = if (isTaxiOrder) "Driver: ${riderName ?: "Delivio Driver"}" else "Rider: ${riderName ?: "Delivio Driver"}"
            snippet = "Live GPS location"
            icon = createCustomIcon(context, 0xFFFFA000.toInt(), if (isTaxiOrder) "🚕" else "🛵")
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        map.overlays.add(riderM)
        onRiderMarkerCreated(riderM)
    }

    // 5. Polyline Route
    if (routeResult.points.isNotEmpty()) {
        val polyline = Polyline(map).apply {
            setPoints(routeResult.points)
            outlinePaint.color = 0xFFFFB300.toInt() // Amber Yellow
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        map.overlays.add(polyline)
        onPolylineCreated(polyline)
    }

    // 6. Fit bounding box
    try {
        val allPoints = mutableListOf(GeoPoint(pickupLat, pickupLng), GeoPoint(dropoffLat, dropoffLng))
        if (isTaxiOrder) {
            taxiStops.forEach { s ->
                if (s.lat != 0.0 && s.lng != 0.0) {
                    allPoints.add(GeoPoint(s.lat, s.lng))
                }
            }
        }
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            allPoints.add(GeoPoint(riderLat, riderLng))
        }
        val minLat = allPoints.minOf { it.latitude }
        val maxLat = allPoints.maxOf { it.latitude }
        val minLng = allPoints.minOf { it.longitude }
        val maxLng = allPoints.maxOf { it.longitude }

        val deltaLat = (maxLat - minLat).coerceAtLeast(0.005)
        val deltaLng = (maxLng - minLng).coerceAtLeast(0.005)

        val box = BoundingBox(maxLat + deltaLat * 0.25, maxLng + deltaLng * 0.25, minLat - deltaLat * 0.25, minLng - deltaLng * 0.25)
        map.zoomToBoundingBox(box, true, 40)
    } catch (_: Exception) {}

    map.invalidate()
}

/**
 * Creates custom crisp marker bitmap with colored pin circle and emoji
 */
private fun createCustomIcon(context: android.content.Context, colorInt: Int, emoji: String): Drawable {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    // Outer pin circle
    canvas.drawCircle(size / 2f, size / 2f, size / 2.3f, paint)

    // Inner white border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.3f - 3f, strokePaint)

    // Emoji icon text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }
    val yOffset = (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(emoji, size / 2f, size / 2f - yOffset, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
