package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Real, fully functional OpenStreetMap interactive view powered by Leaflet.js
 * Supports drag-to-pin, pinch-to-zoom, pan, real coordinates, layer switching,
 * and two-way bridge to Kotlin.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealMapView(
    initialLat: Double = 14.5547,
    initialLng: Double = 121.0244,
    initialZoom: Int = 14,
    markerTitle: String = "Selected Location",
    pinHexColor: String = "#FFC107",
    isInteractive: Boolean = true,
    showControls: Boolean = true,
    showQuickLocations: Boolean = true,
    onLocationChanged: (Double, Double, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    var currentLat by remember { mutableDoubleStateOf(initialLat) }
    var currentLng by remember { mutableDoubleStateOf(initialLng) }
    var currentAddress by remember { mutableStateOf(markerTitle) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var activeLayer by remember { mutableStateOf("streets") } // "streets", "carto", "satellite"

    // Quick real location landmarks
    val quickLocations = remember {
        listOf(
            QuickLocation("Downtown Hub", 14.5547, 121.0244, "1200 Market St, Downtown"),
            QuickLocation("Financial Plaza", 14.5580, 121.0180, "Ayala Ave, Financial District"),
            QuickLocation("Greenbelt Park", 14.5525, 121.0205, "Legazpi Village, Greenbelt"),
            QuickLocation("Bonifacio High", 14.5510, 121.0500, "9th Ave, BGC High Street"),
            QuickLocation("Ocean Terminal", 14.5790, 120.9720, "Roxas Blvd, Harbor View")
        )
    }

    val htmlContent = remember(initialLat, initialLng, initialZoom, pinHexColor, isInteractive) {
        generateLeafletHtml(
            lat = initialLat,
            lng = initialLng,
            zoom = initialZoom,
            pinColor = pinHexColor,
            isInteractive = isInteractive,
            title = markerTitle
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1B2228) else Color(0xFFECEFF1)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Android WebView hosting Leaflet OpenStreetMap
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        addJavascriptInterface(
                            MapBridge(
                                onLocation = { lat, lng, addr ->
                                    Handler(Looper.getMainLooper()).post {
                                        currentLat = lat
                                        currentLng = lng
                                        currentAddress = addr
                                        onLocationChanged(lat, lng, addr)
                                    }
                                },
                                onReady = {
                                    Handler(Looper.getMainLooper()).post {
                                        isMapLoaded = true
                                    }
                                }
                            ),
                            "AndroidBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isMapLoaded = true
                            }
                        }

                        loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading spinner while tile map engine starts
            if (!isMapLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color(0xCC1A1F26) else Color(0xCCEAEFF3)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = YellowPrimary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Loading real map...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Top Banner: Live OpenStreetMap Indicator & Coordinates
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Text(
                        text = "Real Map 🌐",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "• %.4f, %.4f".format(currentLat, currentLng),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Map Layer switcher toggle (Streets / Carto / Satellite)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapLayerChip(
                        label = "OSM",
                        isSelected = activeLayer == "streets",
                        onClick = {
                            activeLayer = "streets"
                            webViewRef?.evaluateJavascript("setTileLayer('streets');", null)
                        }
                    )
                    MapLayerChip(
                        label = "Light",
                        isSelected = activeLayer == "carto",
                        onClick = {
                            activeLayer = "carto"
                            webViewRef?.evaluateJavascript("setTileLayer('carto');", null)
                        }
                    )
                    MapLayerChip(
                        label = "Sat",
                        isSelected = activeLayer == "satellite",
                        onClick = {
                            activeLayer = "satellite"
                            webViewRef?.evaluateJavascript("setTileLayer('satellite');", null)
                        }
                    )
                }
            }

            // Map Floating Controls: Zoom In, Zoom Out, Recenter
            if (showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MapControlButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        onClick = {
                            webViewRef?.evaluateJavascript("zoomIn();", null)
                        }
                    )
                    MapControlButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        onClick = {
                            webViewRef?.evaluateJavascript("zoomOut();", null)
                        }
                    )
                    MapControlButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "Recenter on Pin",
                        onClick = {
                            webViewRef?.evaluateJavascript("recenter();", null)
                        }
                    )
                }
            }

            // Bottom Quick Landmark Chips
            if (showQuickLocations && isInteractive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickLocations.forEach { loc ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        currentLat = loc.lat
                                        currentLng = loc.lng
                                        currentAddress = loc.address
                                        onLocationChanged(loc.lat, loc.lng, loc.address)
                                        webViewRef?.evaluateJavascript(
                                            "moveToLocation(${loc.lat}, ${loc.lng}, '${loc.name}');",
                                            null
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = YellowPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = loc.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

@Composable
private fun MapLayerChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) YellowPrimary else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private data class QuickLocation(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String
)

private class MapBridge(
    private val onLocation: (Double, Double, String) -> Unit,
    private val onReady: () -> Unit
) {
    @JavascriptInterface
    fun onLocationSelected(lat: Double, lng: Double, address: String) {
        onLocation(lat, lng, address)
    }

    @JavascriptInterface
    fun onMapReady() {
        onReady()
    }
}

/**
 * Generates high-performance, responsive Leaflet HTML page using real OpenStreetMap tile layers.
 */
private fun generateLeafletHtml(
    lat: Double,
    lng: Double,
    zoom: Int,
    pinColor: String,
    isInteractive: Boolean,
    title: String
): String {
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <style>
            html, body {
                width: 100%;
                height: 100%;
                margin: 0;
                padding: 0;
                overflow: hidden;
                background-color: #E2E6EA;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            }
            #map {
                width: 100%;
                height: 100%;
                background: #E5E9EC;
            }
            .leaflet-control-zoom {
                display: none !important;
            }
            .leaflet-control-attribution {
                font-size: 8px !important;
                background: rgba(255,255,255,0.7) !important;
                padding: 0 4px !important;
            }
            .custom-pin {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 32px;
                height: 32px;
                border-radius: 50%;
                background: $pinColor;
                border: 3px solid #FFFFFF;
                box-shadow: 0 4px 10px rgba(0,0,0,0.35);
                color: #111;
                font-size: 16px;
                cursor: pointer;
            }
            .pin-pulse {
                position: absolute;
                width: 44px;
                height: 44px;
                border-radius: 50%;
                background: $pinColor;
                opacity: 0.35;
                animation: pulseAnimation 1.6s infinite ease-out;
                pointer-events: none;
            }
            @keyframes pulseAnimation {
                0% { transform: scale(0.6); opacity: 0.7; }
                100% { transform: scale(1.6); opacity: 0; }
            }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>
            let currentLat = $lat;
            let currentLng = $lng;
            let currentTitle = "$title";

            // Initialize Leaflet Map centered at requested coordinates
            const map = L.map('map', {
                center: [currentLat, currentLng],
                zoom: $zoom,
                zoomControl: false,
                attributionControl: true,
                dragging: $isInteractive,
                touchZoom: $isInteractive,
                doubleClickZoom: $isInteractive,
                scrollWheelZoom: $isInteractive
            });

            // Define Tile Layers: OpenStreetMap standard, Carto Light, and Esri Satellite
            const osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OSM'
            });

            const cartoLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                maxZoom: 19,
                subdomains: 'abcd',
                attribution: '&copy; Carto'
            });

            const satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                maxZoom: 19,
                attribution: '&copy; Esri'
            });

            // Add default layer
            osmLayer.addTo(map);

            let activeTileLayer = osmLayer;
            window.setTileLayer = function(type) {
                map.removeLayer(activeTileLayer);
                if (type === 'carto') {
                    activeTileLayer = cartoLayer;
                } else if (type === 'satellite') {
                    activeTileLayer = satLayer;
                } else {
                    activeTileLayer = osmLayer;
                }
                activeTileLayer.addTo(map);
            };

            // Custom Pin Icon
            const pinIcon = L.divIcon({
                className: 'custom-pin-wrapper',
                html: '<div class="pin-pulse"></div><div class="custom-pin">📍</div>',
                iconSize: [32, 32],
                iconAnchor: [16, 16]
            });

            // Add Marker
            const marker = L.marker([currentLat, currentLng], {
                icon: pinIcon,
                draggable: $isInteractive
            }).addTo(map);

            function notifyLocation(lat, lng, name) {
                currentLat = lat;
                currentLng = lng;
                const formatted = name || (lat.toFixed(4) + ', ' + lng.toFixed(4));
                if (window.AndroidBridge && window.AndroidBridge.onLocationSelected) {
                    window.AndroidBridge.onLocationSelected(lat, lng, formatted);
                }
            }

            ${if (isInteractive) """
            // Tap on map to relocate pin
            map.on('click', function(e) {
                const lat = e.latlng.lat;
                const lng = e.latlng.lng;
                marker.setLatLng([lat, lng]);
                notifyLocation(lat, lng, 'Pinned: ' + lat.toFixed(4) + ', ' + lng.toFixed(4));
            });

            marker.on('dragend', function(e) {
                const pos = marker.getLatLng();
                notifyLocation(pos.lat, pos.lng, 'Pinned: ' + pos.lat.toFixed(4) + ', ' + pos.lng.toFixed(4));
            });
            """ else ""}

            window.zoomIn = function() { map.zoomIn(); };
            window.zoomOut = function() { map.zoomOut(); };
            window.recenter = function() {
                map.setView(marker.getLatLng(), $zoom);
            };

            window.moveToLocation = function(lat, lng, name) {
                marker.setLatLng([lat, lng]);
                map.flyTo([lat, lng], 15, { animate: true, duration: 1.0 });
                notifyLocation(lat, lng, name);
            };

            if (window.AndroidBridge && window.AndroidBridge.onMapReady) {
                window.AndroidBridge.onMapReady();
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}
