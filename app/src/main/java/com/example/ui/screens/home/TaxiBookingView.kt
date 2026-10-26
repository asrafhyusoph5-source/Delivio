package com.example.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.viewmodel.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
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
import com.example.data.api.NominatimResult
import com.example.data.api.NominatimService
import com.example.data.api.OsrmRoutingService
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.LocationPoint
import com.example.data.model.PlatformSettings
import com.example.data.model.RideRateConfig
import com.example.data.model.TaxiStop
import com.example.data.repository.OrderRepository
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.util.OsmConfig
import com.example.viewmodel.AuthViewModel
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

data class TaxiVehicleOption(
    val type: String,
    val name: String,
    val iconVector: ImageVector,
    val seats: String,
    val icon: String = ""
)

val TAXI_VEHICLE_OPTIONS = listOf(
    TaxiVehicleOption("motorcycle", "Motorcycle", Icons.Default.TwoWheeler, "1 seat"),
    TaxiVehicleOption("tricycle", "Tricycle", Icons.Default.Moped, "3 seats"),
    TaxiVehicleOption("car4", "4 Seater Car", Icons.Default.DirectionsCar, "4 seats"),
    TaxiVehicleOption("car8", "8 Seater Car", Icons.Default.AirportShuttle, "8 seats")
)

private enum class LocationPickerTarget {
    PICKUP,
    DROPOFF,
    STOP
}

@Composable
fun TaxiBookingView(
    orderRepository: OrderRepository,
    cityId: String,
    authViewModel: AuthViewModel? = null,
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fs = orderRepository.firestoreService
    val cities by fs.getCitiesFlow().collectAsState(initial = emptyList())
    val platformSettings by fs.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())

    val authState by (authViewModel?.uiState ?: remember { MutableStateFlow(AuthUiState()) }).collectAsState()
    val user = authState.currentUser
    val walletBalance = user?.walletBalance ?: 0.0

    // Pickup & Drop-off state
    var pickupPoint by remember { mutableStateOf<LocationPoint?>(null) }
    var dropoffPoint by remember { mutableStateOf<LocationPoint?>(null) }
    val nextStops = remember { mutableStateListOf<TaxiStop>() }

    // Detected city from pickup point
    val pickupCity = remember(pickupPoint, cities) {
        val p = pickupPoint
        if (p != null && p.lat != 0.0 && p.lng != 0.0) {
            cities.firstOrNull { c ->
                val dist = OsrmRoutingService.calculateHaversineDistanceKm(p.lat, p.lng, c.lat, c.lng)
                dist <= c.radiusKm
            }
        } else {
            cities.firstOrNull { it.id == cityId } ?: cities.firstOrNull()
        }
    }

    val isOutsideActiveCity = remember(pickupPoint, cities) {
        val p = pickupPoint
        if (p != null && p.lat != 0.0 && p.lng != 0.0) {
            cities.none { c ->
                val dist = OsrmRoutingService.calculateHaversineDistanceKm(p.lat, p.lng, c.lat, c.lng)
                dist <= c.radiusKm
            }
        } else {
            false
        }
    }

    val effectiveCityId = pickupCity?.id ?: cityId
    val deliveryRates by fs.getDeliveryRatesFlow(effectiveCityId).collectAsState(initial = DeliveryRates())

    // Filter available ride types according to platform settings and city delivery rates
    val availableVehicles = remember(platformSettings, deliveryRates) {
        TAXI_VEHICLE_OPTIONS.filter { opt ->
            val platformAllowed = platformSettings.taxiVehicles[opt.type] != false
            val cityAllowed = deliveryRates.rideRates[opt.type]?.enabled != false
            platformAllowed && cityAllowed
        }
    }

    var selectedVehicleType by remember { mutableStateOf("motorcycle") }
    LaunchedEffect(availableVehicles) {
        if (availableVehicles.isNotEmpty() && availableVehicles.none { it.type == selectedVehicleType }) {
            selectedVehicleType = availableVehicles.first().type
        }
    }

    // Routing & Distance Calculation
    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    LaunchedEffect(pickupPoint, dropoffPoint, nextStops.size, nextStops.map { it.lat to it.lng }) {
        val p = pickupPoint
        val d = dropoffPoint
        if (p != null && d != null && p.lat != 0.0 && d.lat != 0.0) {
            isCalculatingRoute = true
            val points = mutableListOf<Pair<Double, Double>>()
            points.add(p.lat to p.lng)
            points.add(d.lat to d.lng)
            nextStops.forEach { s ->
                if (s.lat != 0.0 && s.lng != 0.0) {
                    points.add(s.lat to s.lng)
                }
            }
            val res = OsrmRoutingService.calculateMultiPointRoute(points)
            routeDistanceKm = res.distanceKm
            isCalculatingRoute = false
        } else {
            routeDistanceKm = 0.0
        }
    }

    // Fare calculation
    val currentRate = deliveryRates.rideRates[selectedVehicleType] ?: RideRateConfig()
    val baseFare = currentRate.baseFare
    val includedKm = currentRate.includedKm
    val perKm = currentRate.perKm
    val minFare = currentRate.minFare
    val perStopFee = currentRate.perStopFee
    val serviceFee = currentRate.serviceFee
    val peakSurcharge = deliveryRates.surcharges.peak
    val nightSurcharge = deliveryRates.surcharges.night

    val extraKm = (routeDistanceKm - includedKm).coerceAtLeast(0.0)
    val rideFare = (baseFare + extraKm * perKm).coerceAtLeast(minFare)
    val extraStopsFee = perStopFee * nextStops.size
    val totalFare = if (pickupPoint != null && dropoffPoint != null) {
        rideFare + extraStopsFee + serviceFee + peakSurcharge + nightSurcharge
    } else 0.0

    // Payment & Booking state
    var paymentMethod by remember { mutableStateOf("cod") } // "cod" or "wallet"
    var driverNote by remember { mutableStateOf("") }
    var isBooking by remember { mutableStateOf(false) }

    // Location Picker Sheet Modal State
    var showPickerSheet by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf(LocationPickerTarget.PICKUP) }
    var targetStopIndex by remember { mutableStateOf(0) }
    var sheetTitle by remember { mutableStateOf("Pick Up from") }
    var sheetInitialAddress by remember { mutableStateOf("") }
    var sheetInitialDetails by remember { mutableStateOf("") }
    var sheetInitialLat by remember { mutableDoubleStateOf(9.7392) }
    var sheetInitialLng by remember { mutableDoubleStateOf(118.7353) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner: outside active city
        if (isOutsideActiveCity) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ErrorRed.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                    Text(
                        text = "Taxi is not available here",
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 1. RIDE TYPE CARDS (Horizontal Scroll)
        Text(
            text = "Select Ride Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            availableVehicles.forEach { vehicle ->
                val isSelected = vehicle.type == selectedVehicleType
                val vehicleRate = deliveryRates.rideRates[vehicle.type] ?: RideRateConfig()
                val estExtraKm = (routeDistanceKm - vehicleRate.includedKm).coerceAtLeast(0.0)
                val estFare = (vehicleRate.baseFare + estExtraKm * vehicleRate.perKm).coerceAtLeast(vehicleRate.minFare) +
                        (vehicleRate.perStopFee * nextStops.size) + vehicleRate.serviceFee + peakSurcharge + nightSurcharge

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) YellowPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { selectedVehicleType = vehicle.type }
                        .testTag("taxi_type_${vehicle.type}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = vehicle.iconVector,
                            contentDescription = vehicle.name,
                            tint = if (isSelected) TextOnYellow else YellowPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = vehicle.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = vehicle.seats,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pickupPoint != null && dropoffPoint != null) {
                            Text(
                                text = Formatters.formatPrice(estFare),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = YellowPrimary
                            )
                        } else {
                            Text(
                                text = "From ₱${vehicleRate.baseFare.toInt()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. ROUTE SECTION
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Trip Route",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Row 1: Pick Up from
                RouteStopRow(
                    label = "Pick Up from",
                    address = pickupPoint?.address,
                    details = pickupPoint?.details,
                    pinIcon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Pick Up",
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onTap = {
                        pickerTarget = LocationPickerTarget.PICKUP
                        sheetTitle = "Pick Up from"
                        sheetInitialAddress = pickupPoint?.address ?: ""
                        sheetInitialDetails = pickupPoint?.details ?: ""
                        sheetInitialLat = pickupPoint?.lat ?: (pickupCity?.lat ?: 9.7392)
                        sheetInitialLng = pickupPoint?.lng ?: (pickupCity?.lng ?: 118.7353)
                        showPickerSheet = true
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Row 2: Drop off to (#2)
                RouteStopRow(
                    label = "Drop off to",
                    address = dropoffPoint?.address,
                    details = dropoffPoint?.details,
                    pinIcon = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ErrorRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ErrorRed
                            )
                        }
                    },
                    onTap = {
                        pickerTarget = LocationPickerTarget.DROPOFF
                        sheetTitle = "Drop off to"
                        sheetInitialAddress = dropoffPoint?.address ?: ""
                        sheetInitialDetails = dropoffPoint?.details ?: ""
                        sheetInitialLat = dropoffPoint?.lat ?: (pickupPoint?.lat ?: 9.7420)
                        sheetInitialLng = dropoffPoint?.lng ?: (pickupPoint?.lng ?: 118.7400)
                        showPickerSheet = true
                    }
                )

                // Extra Stops (Stops 3, 4, 5, 6 - max 4 extra stops)
                nextStops.forEachIndexed { index, stop ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    val stopNum = index + 3
                    RouteStopRow(
                        label = "Next Stop to",
                        address = stop.address,
                        details = stop.details,
                        stopNumber = stopNum,
                        canRemove = true,
                        pinIcon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$stopNum",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ErrorRed
                                )
                            }
                        },
                        onTap = {
                            pickerTarget = LocationPickerTarget.STOP
                            targetStopIndex = index
                            sheetTitle = "Next Stop to (#$stopNum)"
                            sheetInitialAddress = stop.address
                            sheetInitialDetails = stop.details
                            sheetInitialLat = if (stop.lat != 0.0) stop.lat else (dropoffPoint?.lat ?: 9.7420)
                            sheetInitialLng = if (stop.lng != 0.0) stop.lng else (dropoffPoint?.lng ?: 118.7400)
                            showPickerSheet = true
                        },
                        onRemove = {
                            nextStops.removeAt(index)
                            // Renumber the rest
                            for (i in 0 until nextStops.size) {
                                nextStops[i] = nextStops[i].copy(number = i + 3)
                            }
                        }
                    )
                }

                // "+" button to add up to 4 extra stops
                if (nextStops.size < 4) {
                    Button(
                        onClick = {
                            val newNum = nextStops.size + 3
                            val newStop = TaxiStop(number = newNum, label = "Next Stop")
                            nextStops.add(newStop)
                            // Immediately open picker for the newly added stop
                            pickerTarget = LocationPickerTarget.STOP
                            targetStopIndex = nextStops.lastIndex
                            sheetTitle = "Next Stop to (#$newNum)"
                            sheetInitialAddress = ""
                            sheetInitialDetails = ""
                            sheetInitialLat = dropoffPoint?.lat ?: 9.7420
                            sheetInitialLng = dropoffPoint?.lng ?: 118.7400
                            showPickerSheet = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("taxi_add_stop_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Next Stop (Stop #${nextStops.size + 3})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 3. LIVE FARE CARD
        if (pickupPoint != null && dropoffPoint != null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fare Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (isCalculatingRoute) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", routeDistanceKm)} km",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ride Fare", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatPrice(rideFare), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (nextStops.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Extra Stops (${nextStops.size}x ₱${perStopFee.toInt()})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatPrice(extraStopsFee), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service Fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatPrice(serviceFee), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (peakSurcharge > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Peak Hour Surcharge", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatPrice(peakSurcharge), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (nightSurcharge > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Night Surcharge", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.formatPrice(nightSurcharge), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Fare (Locked)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = Formatters.formatPrice(totalFare),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = YellowPrimary
                        )
                    }
                }
            }
        }

        // 4. NOTE FOR DRIVER
        OutlinedTextField(
            value = driverNote,
            onValueChange = { driverNote = it },
            label = { Text("Note for the driver (optional)") },
            placeholder = { Text("e.g. Waiting near the lobby gate") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = YellowPrimary,
                focusedLabelColor = YellowPrimary
            )
        )

        // 5. PAYMENT METHOD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Payment Method",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Cash Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { paymentMethod = "cod" }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = paymentMethod == "cod",
                        onClick = { paymentMethod = "cod" },
                        colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Payments, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                            Text("Cash", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text("Pay the driver directly at completion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Delivio Wallet Option
                val hasEnoughWallet = walletBalance >= totalFare
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = hasEnoughWallet || totalFare == 0.0) {
                            if (hasEnoughWallet || totalFare == 0.0) {
                                paymentMethod = "wallet"
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = paymentMethod == "wallet",
                        onClick = {
                            if (hasEnoughWallet || totalFare == 0.0) {
                                paymentMethod = "wallet"
                            }
                        },
                        enabled = hasEnoughWallet || totalFare == 0.0,
                        colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(16.dp))
                            Text("Delivio Wallet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("(₱${String.format(Locale.US, "%.2f", walletBalance)})", fontSize = 12.sp, color = YellowPrimary, fontWeight = FontWeight.Bold)
                        }
                        if (!hasEnoughWallet && totalFare > 0) {
                            Text(
                                text = "Insufficient balance (₱${String.format(Locale.US, "%.2f", walletBalance)}). Top up or pay with cash.",
                                fontSize = 11.sp,
                                color = ErrorRed
                            )
                        } else {
                            Text("Fast, contactless payment deducted atomically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 6. BOOK RIDE BUTTON
        val canBook = pickupPoint != null &&
                dropoffPoint != null &&
                !isOutsideActiveCity &&
                !isCalculatingRoute &&
                totalFare > 0 &&
                (paymentMethod != "wallet" || walletBalance >= totalFare)

        Button(
            onClick = {
                val p = pickupPoint ?: return@Button
                val d = dropoffPoint ?: return@Button
                val customerId = user?.uid ?: "guest"
                val customerName = user?.name ?: "Customer"
                val customerPhone = user?.phone ?: "+63 917 555 0100"

                isBooking = true
                coroutineScope.launch {
                    val result = orderRepository.placeTaxiOrder(
                        customerId = customerId,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        vehicleType = selectedVehicleType,
                        cityId = effectiveCityId,
                        pickup = p,
                        dropoff = d,
                        stops = nextStops.toList(),
                        distanceKm = routeDistanceKm,
                        rideFare = rideFare,
                        serviceFee = serviceFee,
                        total = totalFare,
                        paymentMethod = paymentMethod,
                        note = driverNote
                    )
                    isBooking = false
                    result.onSuccess {
                        Toast.makeText(context, "Taxi booked! Searching for nearby driver...", Toast.LENGTH_LONG).show()
                        onOrderBooked()
                    }.onFailure { err ->
                        Toast.makeText(context, "Failed to book: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = canBook && !isBooking,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = YellowPrimary,
                contentColor = TextOnYellow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_book_taxi_ride")
        ) {
            if (isBooking) {
                CircularProgressIndicator(color = TextOnYellow, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Booking your ride...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pickupPoint == null || dropoffPoint == null) "Set Pick Up and Drop Off to Book"
                    else if (isOutsideActiveCity) "Taxi Unavailable Here"
                    else "Book Ride • ${Formatters.formatPrice(totalFare)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // FLOATING FULL-SCREEN LOCATION PICKER SHEET
    if (showPickerSheet) {
        Dialog(
            onDismissRequest = { showPickerSheet = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            TaxiLocationPickerSheet(
                title = sheetTitle,
                initialAddress = sheetInitialAddress,
                initialDetails = sheetInitialDetails,
                initialLat = sheetInitialLat,
                initialLng = sheetInitialLng,
                pinColor = if (pickerTarget == LocationPickerTarget.PICKUP) SuccessGreen else ErrorRed,
                onDismiss = { showPickerSheet = false },
                onConfirm = { address, lat, lng, details ->
                    when (pickerTarget) {
                        LocationPickerTarget.PICKUP -> {
                            pickupPoint = LocationPoint(
                                label = "Pick Up",
                                address = address,
                                lat = lat,
                                lng = lng,
                                details = details
                            )
                        }
                        LocationPickerTarget.DROPOFF -> {
                            dropoffPoint = LocationPoint(
                                label = "Drop off",
                                address = address,
                                lat = lat,
                                lng = lng,
                                details = details
                            )
                        }
                        LocationPickerTarget.STOP -> {
                            if (targetStopIndex in 0 until nextStops.size) {
                                val s = nextStops[targetStopIndex]
                                nextStops[targetStopIndex] = s.copy(
                                    address = address,
                                    lat = lat,
                                    lng = lng,
                                    details = details
                                )
                            }
                        }
                    }
                    showPickerSheet = false
                }
            )
        }
    }
}

@Composable
private fun RouteStopRow(
    label: String,
    address: String?,
    details: String?,
    pinIcon: @Composable () -> Unit,
    onTap: () -> Unit,
    stopNumber: Int? = null,
    canRemove: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTap() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pinIcon()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (stopNumber != null) "$label (#$stopNumber)" else label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!address.isNullOrBlank()) {
                Text(
                    text = address,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!details.isNullOrBlank()) {
                    Text(
                        text = details,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "Tap to set location",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (canRemove && onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove stop",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            IconButton(
                onClick = onTap,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Floating Full-Screen OpenStreetMap Location Picker Sheet
 * Specifically designed for Taxi stops with Nominatim search (countrycodes=ph),
 * stationary centered pin, address details field, and navigationBarsPadding confirm button.
 */
@Composable
fun TaxiLocationPickerSheet(
    title: String,
    initialAddress: String,
    initialDetails: String,
    initialLat: Double,
    initialLng: Double,
    pinColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (address: String, lat: Double, lng: Double, details: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isSystemDark = isSystemInDarkTheme()
    val isAppDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isDarkTheme = isSystemDark || isAppDark

    var currentAddress by remember { mutableStateOf(initialAddress.ifBlank { "Detecting location..." }) }
    var currentDetails by remember { mutableStateOf(initialDetails) }
    var currentLat by remember { mutableDoubleStateOf(if (initialLat != 0.0) initialLat else 9.7392) }
    var currentLng by remember { mutableDoubleStateOf(if (initialLng != 0.0) initialLng else 118.7353) }

    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<NominatimResult>() }
    var isSearching by remember { mutableStateOf(false) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Initialize osmdroid
    LaunchedEffect(Unit) {
        OsmConfig.init(context)
        if (initialAddress.isBlank() && currentLat != 0.0 && currentLng != 0.0) {
            val res = NominatimService.reverseGeocode(currentLat, currentLng)
            res.onSuccess { addr -> currentAddress = addr }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Native osmdroid MapView
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(currentLat, currentLng))

                        if (isDarkTheme) {
                            try {
                                val inverseMatrix = android.graphics.ColorMatrix(
                                    floatArrayOf(
                                        -0.85f, 0f, 0f, 0f, 255f,
                                        0f, -0.85f, 0f, 0f, 255f,
                                        0f, 0f, -0.85f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                                overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(inverseMatrix))
                            } catch (_: Exception) {}
                        }

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val center = mapCenter ?: return false
                                currentLat = center.latitude
                                currentLng = center.longitude

                                reverseGeocodeJob?.cancel()
                                reverseGeocodeJob = coroutineScope.launch {
                                    delay(700)
                                    isReverseGeocoding = true
                                    val res = NominatimService.reverseGeocode(center.latitude, center.longitude)
                                    isReverseGeocoding = false
                                    res.onSuccess { addr -> currentAddress = addr }
                                }
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean = false
                        })

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Centered Fixed Pin
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = if (isReverseGeocoding) "Locating..." else "Move map to position pin",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Target Location",
                        tint = pinColor,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // Top Search Bar (Nominatim debounced, countrycodes=ph)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            searchJob?.cancel()
                            if (query.trim().length >= 2) {
                                isSearching = true
                                searchJob = coroutineScope.launch {
                                    delay(450)
                                    val res = NominatimService.searchPlaces(query.trim())
                                    isSearching = false
                                    res.onSuccess { list ->
                                        searchResults.clear()
                                        searchResults.addAll(list)
                                    }
                                }
                            } else {
                                searchResults.clear()
                                isSearching = false
                            }
                        },
                        placeholder = { Text("Search places in the Philippines...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = YellowPrimary)
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchResults.clear()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowPrimary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Search Results Dropdown List
                if (searchResults.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .heightIn(max = 220.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(searchResults) { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentAddress = result.displayName
                                            currentLat = result.lat
                                            currentLng = result.lon
                                            mapViewRef?.controller?.apply {
                                                setZoom(17.0)
                                                setCenter(GeoPoint(result.lat, result.lon))
                                            }
                                            searchQuery = ""
                                            searchResults.clear()
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = pinColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = result.displayName,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            // Right Map Controls: My Location & Zoom (+ / -)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // My Location Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    onClick = {
                        try {
                            val client = LocationServices.getFusedLocationProviderClient(context)
                            client.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    currentLat = loc.latitude
                                    currentLng = loc.longitude
                                    mapViewRef?.controller?.apply {
                                        setZoom(17.0)
                                        setCenter(GeoPoint(loc.latitude, loc.longitude))
                                    }
                                } else {
                                    Toast.makeText(context, "Locating GPS position...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location", tint = YellowPrimary)
                    }
                }

                // Zoom In Button (+)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                }

                // Zoom Out Button (-)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                }
            }

            // Bottom Confirmation Card
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = pinColor, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentAddress,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Lat: ${String.format(Locale.US, "%.5f", currentLat)} • Lng: ${String.format(Locale.US, "%.5f", currentLng)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Landmark / Driver Note Field
                    OutlinedTextField(
                        value = currentDetails,
                        onValueChange = { currentDetails = it },
                        label = { Text("Address details / landmark / note for the driver") },
                        placeholder = { Text("e.g. Near blue gate, across Shell station") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowPrimary,
                            focusedLabelColor = YellowPrimary
                        )
                    )

                    // Confirm Button
                    Button(
                        onClick = {
                            onConfirm(currentAddress, currentLat, currentLng, currentDetails)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_confirm_taxi_location")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm This Location", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
