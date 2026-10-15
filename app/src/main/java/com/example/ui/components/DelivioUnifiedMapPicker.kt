package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class PlaceSuggestion(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

@Composable
fun DelivioUnifiedMapPicker(
    title: String,
    initialAddress: String,
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    pinColor: Color = YellowPrimary,
    onLocationConfirmed: (addressText: String, lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentAddress by remember { mutableStateOf(initialAddress) }
    var lat by remember { mutableDoubleStateOf(initialLat) }
    var lng by remember { mutableDoubleStateOf(initialLng) }
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    val searchSuggestions = remember { mutableStateListOf<PlaceSuggestion>() }

    // Geocoding helper
    fun searchPlaces(query: String) {
        if (query.isBlank()) {
            searchSuggestions.clear()
            return
        }
        isSearching = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(query, 5) { addresses ->
                        val results = addresses.map { a ->
                            PlaceSuggestion(
                                title = a.featureName ?: a.thoroughfare ?: query,
                                subtitle = a.getAddressLine(0) ?: "${a.locality ?: ""}, ${a.adminArea ?: ""}",
                                lat = a.latitude,
                                lng = a.longitude
                            )
                        }
                        searchSuggestions.clear()
                        searchSuggestions.addAll(results)
                        isSearching = false
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 5)
                    val results = addresses?.map { a ->
                        PlaceSuggestion(
                            title = a.featureName ?: a.thoroughfare ?: query,
                            subtitle = a.getAddressLine(0) ?: "${a.locality ?: ""}, ${a.adminArea ?: ""}",
                            lat = a.latitude,
                            lng = a.longitude
                        )
                    } ?: emptyList()
                    withContext(Dispatchers.Main) {
                        searchSuggestions.clear()
                        searchSuggestions.addAll(results)
                        isSearching = false
                    }
                }
            } catch (e: Exception) {
                Log.w("DelivioMapPicker", "Places search error: ${e.message}")
                // Fallback Puerto Princesa landmarks
                val fallbacks = listOf(
                    PlaceSuggestion("Rizal Avenue", "Puerto Princesa City Center, Palawan", 9.7420, 118.7400),
                    PlaceSuggestion("Robinsons Place Palawan", "National Highway, San Manuel, Puerto Princesa", 9.7712, 118.7525),
                    PlaceSuggestion("SM City Puerto Princesa", "Malvar St, Puerto Princesa, Palawan", 9.7460, 118.7390),
                    PlaceSuggestion("Capitol Park", "Rizal Avenue, Puerto Princesa, Palawan", 9.7392, 118.7353)
                ).filter { it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
                withContext(Dispatchers.Main) {
                    searchSuggestions.clear()
                    searchSuggestions.addAll(fallbacks)
                    isSearching = false
                }
            }
        }
    }

    // Reverse geocode
    fun reverseGeocode(targetLat: Double, targetLng: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(targetLat, targetLng, 1) { addresses ->
                        val first = addresses.firstOrNull()
                        val line = first?.getAddressLine(0) ?: "${targetLat.format(4)}, ${targetLng.format(4)}"
                        currentAddress = line
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(targetLat, targetLng, 1)
                    val first = addresses?.firstOrNull()
                    val line = first?.getAddressLine(0) ?: "${targetLat.format(4)}, ${targetLng.format(4)}"
                    withContext(Dispatchers.Main) {
                        currentAddress = line
                    }
                }
            } catch (e: Exception) {
                // Keep coordinates
            }
        }
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isLocating = true
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    isLocating = false
                    if (loc != null) {
                        lat = loc.latitude
                        lng = loc.longitude
                        reverseGeocode(loc.latitude, loc.longitude)
                    }
                }.addOnFailureListener {
                    isLocating = false
                }
            } catch (e: SecurityException) {
                isLocating = false
            }
        }
    }

    fun requestCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            isLocating = true
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    isLocating = false
                    if (loc != null) {
                        lat = loc.latitude
                        lng = loc.longitude
                        reverseGeocode(loc.latitude, loc.longitude)
                    }
                }.addOnFailureListener {
                    isLocating = false
                }
            } catch (e: SecurityException) {
                isLocating = false
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title Header with Expand Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(pinColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = pinColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isExpanded) "Shrink" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Search Bar with Autocomplete
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    searchPlaces(it)
                },
                placeholder = { Text("Search street, landmark, or venue...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = YellowPrimary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchSuggestions.clear()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchPlaces(searchQuery) }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YellowPrimary,
                    focusedLabelColor = YellowPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Autocomplete Suggestions List
            if (searchSuggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        searchSuggestions.take(4).forEachIndexed { idx, sugg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        lat = sugg.lat
                                        lng = sugg.lng
                                        currentAddress = sugg.subtitle.ifEmpty { sugg.title }
                                        searchQuery = ""
                                        searchSuggestions.clear()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = sugg.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = sugg.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (idx < searchSuggestions.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            // Real Interactive Map Box
            val hexColor = remember(pinColor) {
                val r = (pinColor.red * 255).toInt().coerceIn(0, 255)
                val g = (pinColor.green * 255).toInt().coerceIn(0, 255)
                val b = (pinColor.blue * 255).toInt().coerceIn(0, 255)
                "#%02X%02X%02X".format(r, g, b)
            }

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
                        currentAddress = newAddr
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // "Use My Location" floating button on map
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clickable { requestCurrentLocation() }
                        .testTag("use_my_location_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = YellowPrimary)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = YellowPrimary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "My Location",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Reverse-geocoded address display card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = pinColor, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected Pin Location",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentAddress.ifEmpty { "${lat.format(4)}, ${lng.format(4)}" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Confirm Button
            Button(
                onClick = {
                    onLocationConfirmed(currentAddress, lat, lng)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("confirm_map_location_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Confirm Location (${lat.format(3)}, ${lng.format(3)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
