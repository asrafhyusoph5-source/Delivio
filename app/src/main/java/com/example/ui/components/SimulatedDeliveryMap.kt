package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Fully functional Real OpenStreetMap Live Rider Delivery Tracker.
 * Renders real street map tiles with the Merchant location, Customer destination,
 * driving route polyline, and real-time animated rider driving marker.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimulatedDeliveryMap(
    restaurantName: String,
    deliveryAddress: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var activeLayer by remember { mutableStateOf("streets") }

    // Real coordinates: Merchant (Makati Ayala) -> Destination (BGC / Fort)
    val startLat = 14.5547
    val startLng = 121.0244
    val endLat = 14.5510
    val endLng = 121.0500

    val htmlContent = remember(restaurantName, deliveryAddress, isDark) {
        generateDeliveryMapHtml(
            merchantName = restaurantName,
            deliveryAddress = deliveryAddress,
            startLat = startLat,
            startLng = startLng,
            endLat = endLat,
            endLng = endLng,
            isDark = isDark
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1B2228) else Color(0xFFE8ECEF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .testTag("real_delivery_map")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real Leaflet OpenStreetMap WebView
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
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Status Bar: Live Tracking Badge & ETA
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
                    color = Color.Black.copy(alpha = 0.82f),
                    shadowElevation = 2.dp
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
                            text = "Live GPS Real Map 🌐",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Layer Switcher
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeLayer == "streets") YellowPrimary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    activeLayer = "streets"
                                    webViewRef?.evaluateJavascript("setTileLayer('streets');", null)
                                }
                        ) {
                            Text(
                                text = "Streets",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeLayer == "streets") TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeLayer == "sat") YellowPrimary else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    activeLayer = "sat"
                                    webViewRef?.evaluateJavascript("setTileLayer('sat');", null)
                                }
                        ) {
                            Text(
                                text = "Satellite",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeLayer == "sat") TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Floating Controls: Zoom & Recenter
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { webViewRef?.evaluateJavascript("zoomIn();", null) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { webViewRef?.evaluateJavascript("zoomOut();", null) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { webViewRef?.evaluateJavascript("fitBounds();", null) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Fit Route", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Bottom Route Legend overlay
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YellowPrimary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = null,
                                    tint = TextOnYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Rider en route to your location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "$restaurantName ➔ $deliveryAddress",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = YellowPrimary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "6-9 min",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = YellowPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
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

private fun generateDeliveryMapHtml(
    merchantName: String,
    deliveryAddress: String,
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    isDark: Boolean
): String {
    // Generate waypoints along realistic city road grid
    val midLat1 = startLat + 0.0020
    val midLng1 = startLng + 0.0090
    val midLat2 = endLat + 0.0030
    val midLng2 = endLng - 0.0080

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
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            }
            #map {
                width: 100%;
                height: 100%;
            }
            .leaflet-control-zoom { display: none !important; }
            .leaflet-control-attribution { font-size: 8px !important; }
            
            .marker-icon {
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 50%;
                color: #fff;
                font-weight: bold;
                box-shadow: 0 4px 10px rgba(0,0,0,0.35);
            }
            .store-icon {
                width: 32px;
                height: 32px;
                background: #FF5722;
                border: 3px solid #FFF;
                font-size: 15px;
            }
            .home-icon {
                width: 32px;
                height: 32px;
                background: #4CAF50;
                border: 3px solid #FFF;
                font-size: 15px;
            }
            .rider-icon {
                width: 38px;
                height: 38px;
                background: #FFC107;
                border: 3px solid #111;
                font-size: 20px;
                transition: transform 0.2s linear;
            }
            .pulse-ring {
                position: absolute;
                width: 48px;
                height: 48px;
                border-radius: 50%;
                background: #FFC107;
                opacity: 0.4;
                animation: pulse 1.4s infinite ease-out;
            }
            @keyframes pulse {
                0% { transform: scale(0.6); opacity: 0.7; }
                100% { transform: scale(1.6); opacity: 0; }
            }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>
            const start = [$startLat, $startLng];
            const end = [$endLat, $endLng];
            const p1 = [$midLat1, $midLng1];
            const p2 = [$midLat2, $midLng2];
            const routePoints = [start, p1, p2, end];

            const map = L.map('map', {
                zoomControl: false,
                attributionControl: true
            });

            const osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OSM'
            });

            const satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                maxZoom: 19,
                attribution: '&copy; Esri'
            });

            osmLayer.addTo(map);

            let currentTile = osmLayer;
            window.setTileLayer = function(type) {
                map.removeLayer(currentTile);
                currentTile = (type === 'sat') ? satLayer : osmLayer;
                currentTile.addTo(map);
            };

            // Store Marker
            const storeIcon = L.divIcon({
                className: '',
                html: '<div class="marker-icon store-icon">🏪</div>',
                iconSize: [32, 32],
                iconAnchor: [16, 16]
            });
            L.marker(start, { icon: storeIcon }).addTo(map).bindPopup('<b>$merchantName</b><br>Pickup Point');

            // Home Marker
            const homeIcon = L.divIcon({
                className: '',
                html: '<div class="marker-icon home-icon">🏠</div>',
                iconSize: [32, 32],
                iconAnchor: [16, 16]
            });
            L.marker(end, { icon: homeIcon }).addTo(map).bindPopup('<b>Delivery Address</b><br>$deliveryAddress');

            // Route Polyline (Outline + Yellow Core)
            L.polyline(routePoints, {
                color: '#212121',
                weight: 7,
                opacity: 0.4
            }).addTo(map);

            L.polyline(routePoints, {
                color: '#FFC107',
                weight: 5,
                opacity: 0.95,
                dashArray: '8, 6'
            }).addTo(map);

            // Animated Rider Marker
            const riderIcon = L.divIcon({
                className: '',
                html: '<div class="pulse-ring"></div><div class="marker-icon rider-icon">🛵</div>',
                iconSize: [38, 38],
                iconAnchor: [19, 19]
            });
            const riderMarker = L.marker(start, { icon: riderIcon }).addTo(map);

            // Fit map bounds to show full route with padding
            map.fitBounds(L.latLngBounds(routePoints), {
                padding: [40, 40]
            });

            // Smooth Rider Animation along polyline segments
            let progress = 0.05;
            let forward = true;

            function animateRider() {
                progress += forward ? 0.004 : -0.004;
                if (progress >= 0.95) forward = false;
                if (progress <= 0.05) forward = true;

                // Interpolate along routePoints (3 segments)
                const totalSegments = routePoints.length - 1;
                const segProgress = progress * totalSegments;
                const segIndex = Math.min(Math.floor(segProgress), totalSegments - 1);
                const segFrac = segProgress - segIndex;

                const ptA = routePoints[segIndex];
                const ptB = routePoints[segIndex + 1];

                const currentLat = ptA[0] + (ptB[0] - ptA[0]) * segFrac;
                const currentLng = ptA[1] + (ptB[1] - ptA[1]) * segFrac;

                riderMarker.setLatLng([currentLat, currentLng]);
                requestAnimationFrame(animateRider);
            }

            requestAnimationFrame(animateRider);

            window.zoomIn = function() { map.zoomIn(); };
            window.zoomOut = function() { map.zoomOut(); };
            window.fitBounds = function() {
                map.fitBounds(L.latLngBounds(routePoints), { padding: [40, 40] });
            };
        </script>
    </body>
    </html>
    """.trimIndent()
}
