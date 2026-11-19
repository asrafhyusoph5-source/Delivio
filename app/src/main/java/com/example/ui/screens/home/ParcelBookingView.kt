package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.OsrmRoutingService
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.ParcelDetails
import com.example.data.model.ParcelVehicleRate
import com.example.data.model.PlatformSettings
import com.example.data.repository.OrderRepository
import com.example.ui.components.InteractiveLocationPickerMap
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.util.ParcelFeeCalculator
import com.example.util.ParcelQuote
import com.example.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ParcelBookingView(
    orderRepository: OrderRepository,
    cityId: String = "puerto-princesa",
    authViewModel: AuthViewModel? = null,
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val authUiState by (authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val currentUser = authUiState?.currentUser
    val walletBalance = currentUser?.walletBalance ?: 0.0

    // Sender / "From"
    var senderName by remember(currentUser) { mutableStateOf(currentUser?.name ?: "") }
    var senderPhone by remember(currentUser) { mutableStateOf(currentUser?.phone ?: "") }
    var senderAddress by remember { mutableStateOf("Rizal Avenue, Puerto Princesa, Palawan") }
    var pickupLat by remember { mutableFloatStateOf(9.7392f) }
    var pickupLng by remember { mutableFloatStateOf(118.7353f) }

    // Recipient / "Where to"
    var recipientName by remember { mutableStateOf("Sarah Connor") }
    var recipientPhone by remember { mutableStateOf("+63 917 890 1234") }
    var recipientAddress by remember { mutableStateOf("Malvar Street, Puerto Princesa, Palawan") }
    var dropoffLat by remember { mutableFloatStateOf(9.7420f) }
    var dropoffLng by remember { mutableFloatStateOf(118.7400f) }

    // Package details
    val packageSizes = listOf("Small / Pouch", "Medium / Box", "Large / Cargo")
    var selectedSize by remember { mutableStateOf("Small / Pouch") }
    var weightKg by remember { mutableDoubleStateOf(2.5) }
    var weightText by remember { mutableStateOf("2.5") }
    var itemDescription by remember { mutableStateOf("Documents & gift package") }
    var isFragile by remember { mutableStateOf(false) }

    // Vehicle option: stored strictly as "motorcycle" or "sedan"
    var selectedVehicle by remember { mutableStateOf(ParcelFeeCalculator.VEHICLE_MOTORCYCLE) }

    // Fee payer & Payment method
    var feePayer by remember { mutableStateOf("sender") } // "sender" | "recipient"
    var selectedPaymentMethod by remember { mutableStateOf("cod") } // "cod" | "wallet"

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBooking by remember { mutableStateOf(false) }

    // Cities & City resolution from pickup coordinates
    val cities by orderRepository.firestoreService.getCitiesFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val platformSettings by orderRepository.firestoreService.getPlatformSettingsFlow()
        .collectAsStateWithLifecycle(initialValue = PlatformSettings())

    fun findCityForCoords(lat: Double, lng: Double, cityList: List<City>): City? {
        if (lat == 0.0 || lng == 0.0) return cityList.firstOrNull { it.id == cityId }
        return cityList.firstOrNull { city ->
            city.status.equals("active", ignoreCase = true) &&
                OsrmRoutingService.calculateHaversineDistanceKm(lat, lng, city.lat, city.lng) <= city.radiusKm
        } ?: cityList.firstOrNull { it.id == cityId }
    }

    val targetCityId = remember(pickupLat, pickupLng, cities, cityId) {
        findCityForCoords(pickupLat.toDouble(), pickupLng.toDouble(), cities)?.id ?: cityId
    }

    val deliveryRates by orderRepository.firestoreService.getDeliveryRatesFlow(targetCityId)
        .collectAsStateWithLifecycle(initialValue = orderRepository.firestoreService.defaultDeliveryRates)

    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var distanceMethod by remember { mutableStateOf("osrm") }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    LaunchedEffect(pickupLat, pickupLng, dropoffLat, dropoffLng) {
        val pLat = pickupLat.toDouble()
        val pLng = pickupLng.toDouble()
        val dLat = dropoffLat.toDouble()
        val dLng = dropoffLng.toDouble()
        if (pLat != 0.0 && pLng != 0.0 && dLat != 0.0 && dLng != 0.0) {
            isCalculatingRoute = true
            val route = OsrmRoutingService.calculateRoute(
                startLat = pLat,
                startLng = pLng,
                endLat = dLat,
                endLng = dLng
            )
            isCalculatingRoute = false
            routeDistanceKm = route.distanceKm
            distanceMethod = if (route.isFallback) "straight_line" else "osrm"
        }
    }

    val motorcycleRate = deliveryRates.getParcelMotorcycleRate()
    val sedanRate = deliveryRates.parcelSedan?.takeIf { it.enabled }
    val isSedanAvailable = sedanRate != null

    // Never leave Sedan selected if unavailable; never fall back to motorcycle rate for Sedan
    LaunchedEffect(isSedanAvailable) {
        if (!isSedanAvailable && selectedVehicle == ParcelFeeCalculator.VEHICLE_SEDAN) {
            selectedVehicle = ParcelFeeCalculator.VEHICLE_MOTORCYCLE
        }
    }

    val activeVehicleRate = if (selectedVehicle == ParcelFeeCalculator.VEHICLE_SEDAN && sedanRate != null) {
        sedanRate
    } else {
        motorcycleRate
    }
    val effectiveVehicleType = if (selectedVehicle == ParcelFeeCalculator.VEHICLE_SEDAN && sedanRate != null) {
        ParcelFeeCalculator.VEHICLE_SEDAN
    } else {
        ParcelFeeCalculator.VEHICLE_MOTORCYCLE
    }

    val quote = remember(effectiveVehicleType, activeVehicleRate, routeDistanceKm, weightKg, platformSettings.timezone) {
        ParcelFeeCalculator.calculateParcelQuote(
            vehicleType = effectiveVehicleType,
            rate = activeVehicleRate,
            distanceKm = routeDistanceKm,
            weightKg = weightKg,
            timezone = platformSettings.timezone
        )
    }

    val isWeightInvalid = weightKg <= 0.0 || quote.isWeightExceeded
    val isSelectedVehicleEnabled = if (effectiveVehicleType == ParcelFeeCalculator.VEHICLE_SEDAN) isSedanAvailable else motorcycleRate.enabled

    val isFormValid = senderName.isNotBlank() &&
        senderPhone.isNotBlank() &&
        senderAddress.isNotBlank() &&
        recipientName.isNotBlank() &&
        recipientPhone.isNotBlank() &&
        recipientAddress.isNotBlank() &&
        isSelectedVehicleEnabled &&
        !isWeightInvalid &&
        (selectedPaymentMethod != "wallet" || walletBalance >= quote.totalFee)

    val missingReason = when {
        senderName.isBlank() -> "Enter sender name"
        senderPhone.isBlank() -> "Enter sender phone"
        senderAddress.isBlank() -> "Enter pickup address"
        recipientName.isBlank() -> "Enter recipient name"
        recipientPhone.isBlank() -> "Enter recipient phone"
        recipientAddress.isBlank() -> "Enter delivery address"
        !isSelectedVehicleEnabled -> "Selected vehicle is unavailable"
        weightKg <= 0.0 -> "Enter a valid weight"
        quote.isWeightExceeded -> "Max ${ParcelFeeCalculator.formatWeightLimitKg(activeVehicleRate.maxWeightKg)}kg for $effectiveVehicleType"
        selectedPaymentMethod == "wallet" && walletBalance < quote.totalFee ->
            "Insufficient balance (₱${String.format(Locale.US, "%.2f", walletBalance)})"
        else -> null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            ParcelBottomBar(
                totalFee = quote.totalFee,
                isFormValid = isFormValid,
                isBooking = isBooking,
                missingReason = missingReason,
                onBookClick = {
                    if (!isFormValid || isBooking) {
                        if (!isFormValid) {
                            errorMessage = missingReason ?: "Please complete all sender and recipient details"
                        }
                        return@ParcelBottomBar
                    }
                    errorMessage = null
                    isBooking = true

                    val parcel = ParcelDetails(
                        senderName = senderName.trim(),
                        senderPhone = senderPhone.trim(),
                        senderAddress = senderAddress.trim(),
                        pickupLat = pickupLat,
                        pickupLng = pickupLng,
                        recipientName = recipientName.trim(),
                        recipientPhone = recipientPhone.trim(),
                        recipientAddress = recipientAddress.trim(),
                        dropoffLat = dropoffLat,
                        dropoffLng = dropoffLng,
                        packageSize = selectedSize,
                        weightKg = weightKg,
                        itemDescription = itemDescription.trim(),
                        isFragile = isFragile,
                        vehicleType = effectiveVehicleType
                    )

                    val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                    val customerId = fbUser?.uid ?: ""

                    coroutineScope.launch {
                        val isServiceActive = orderRepository.firestoreService.checkServiceOn("parcel", targetCityId)
                        if (!isServiceActive) {
                            isBooking = false
                            errorMessage = "This service is not available in your area."
                            return@launch
                        }

                        val result = orderRepository.bookParcelOrder(
                            customerId = customerId,
                            customerName = senderName.trim(),
                            customerPhone = senderPhone.trim(),
                            parcelDetails = parcel,
                            deliveryFee = quote.deliveryFee,
                            serviceFee = quote.serviceFee,
                            cityId = targetCityId,
                            feePayer = feePayer,
                            paymentMethod = selectedPaymentMethod,
                            distanceKm = routeDistanceKm,
                            distanceMethod = distanceMethod
                        )
                        isBooking = false
                        if (result.isSuccess) {
                            onOrderBooked()
                        } else {
                            val err = result.exceptionOrNull()
                            errorMessage = if (err?.message?.contains("This service is not available", ignoreCase = true) == true ||
                                err?.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true
                            ) {
                                "This service is not available in your area."
                            } else {
                                err?.localizedMessage ?: err?.message ?: "Failed to book parcel"
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Banner Header
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = YellowPrimary.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = "Parcel",
                            tint = TextOnYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Parcel Express Delivery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send packages customer-to-customer with live rider tracking",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // STEP 1: SENDER INFO & PICKUP "FROM" MAP
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Pickup Location & Sender (From)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        label = { Text("Sender Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_sender_name")
                    )

                    OutlinedTextField(
                        value = senderPhone,
                        onValueChange = { senderPhone = it },
                        label = { Text("Sender Contact Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_sender_phone")
                    )

                    OutlinedTextField(
                        value = senderAddress,
                        onValueChange = { senderAddress = it },
                        label = { Text("Pickup Street Address") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFE53935)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_sender_address")
                    )

                    Text(
                        text = "Map of \"From\" (Pickup Pin)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    InteractiveLocationPickerMap(
                        title = "Pickup Pin (From)",
                        initialAddress = senderAddress,
                        initialLat = pickupLat.toDouble(),
                        initialLng = pickupLng.toDouble(),
                        pinColor = Color(0xFFE53935),
                        onLocationSelected = { newAddress, x, y ->
                            senderAddress = newAddress
                            pickupLat = x
                            pickupLng = y
                        }
                    )
                }
            }

            // STEP 2: PACKAGE SPECIFICATIONS & VEHICLE OPTION
            ParcelPackageDetailsCard(
                packageSizes = packageSizes,
                selectedSize = selectedSize,
                onSelectSize = { selectedSize = it },
                weightKg = weightKg,
                weightText = weightText,
                onWeightTextChange = { raw ->
                    if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d*$"))) {
                        weightText = raw
                        weightKg = raw.toDoubleOrNull() ?: 0.0
                    }
                },
                onDecreaseWeight = {
                    val next = ((weightKg - 0.5).coerceAtLeast(0.5))
                    weightKg = next
                    weightText = ParcelFeeCalculator.formatWeightLimitKg(next)
                },
                onIncreaseWeight = {
                    val next = weightKg + 0.5
                    weightKg = next
                    weightText = ParcelFeeCalculator.formatWeightLimitKg(next)
                },
                maxWeightKg = activeVehicleRate.maxWeightKg,
                selectedVehicle = effectiveVehicleType,
                isWeightExceeded = quote.isWeightExceeded,
                itemDescription = itemDescription,
                onItemDescriptionChange = { itemDescription = it },
                isFragile = isFragile,
                onFragileChange = { isFragile = it },
                isSedanAvailable = isSedanAvailable,
                isMotorcycleAvailable = motorcycleRate.enabled,
                onSelectVehicle = { vehicle ->
                    if (vehicle == ParcelFeeCalculator.VEHICLE_SEDAN && !isSedanAvailable) {
                        return@ParcelPackageDetailsCard
                    }
                    selectedVehicle = vehicle
                }
            )

            // STEP 3: RECIPIENT INFO & DROPOFF "WHERE TO" MAP
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF43A047)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Destination & Recipient (Where to)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text("Recipient Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_recipient_name")
                    )

                    OutlinedTextField(
                        value = recipientPhone,
                        onValueChange = { recipientPhone = it },
                        label = { Text("Recipient Contact Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_recipient_phone")
                    )

                    OutlinedTextField(
                        value = recipientAddress,
                        onValueChange = { recipientAddress = it },
                        label = { Text("Delivery Street Address") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF43A047)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("parcel_recipient_address")
                    )

                    Text(
                        text = "Map of \"Where to\" (Dropoff Pin)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    InteractiveLocationPickerMap(
                        title = "Destination Pin (Where to)",
                        initialAddress = recipientAddress,
                        initialLat = dropoffLat.toDouble(),
                        initialLng = dropoffLng.toDouble(),
                        pinColor = Color(0xFF43A047),
                        onLocationSelected = { newAddress, x, y ->
                            recipientAddress = newAddress
                            dropoffLat = x
                            dropoffLng = y
                        }
                    )
                }
            }

            // FEE PAYER & PAYMENT METHODS
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
                        text = "Delivery Fee Payer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = feePayer == "sender",
                            onClick = { feePayer = "sender" },
                            label = { Text("Sender Pays (Pickup)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YellowPrimary,
                                selectedLabelColor = TextOnYellow
                            ),
                            modifier = Modifier.weight(1f).testTag("parcel_payer_sender")
                        )
                        FilterChip(
                            selected = feePayer == "recipient",
                            onClick = { feePayer = "recipient" },
                            label = { Text("Recipient Pays (Dropoff)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YellowPrimary,
                                selectedLabelColor = TextOnYellow
                            ),
                            modifier = Modifier.weight(1f).testTag("parcel_payer_recipient")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = YellowPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Payment Method",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Cash on Delivery (default)
                    val isCod = selectedPaymentMethod == "cod"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCod) YellowPrimary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { selectedPaymentMethod = "cod" }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("parcel_payment_cod"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCod,
                            onClick = { selectedPaymentMethod = "cod" },
                            colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cash on Delivery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Rider collects ${Formatters.formatPrice(quote.totalFee)} from ${if (feePayer == "recipient") "recipient upon drop-off" else "sender upon pickup"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Delivio Wallet
                    val isWallet = selectedPaymentMethod == "wallet"
                    val hasEnoughBalance = walletBalance >= quote.totalFee
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWallet) YellowPrimary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable(enabled = hasEnoughBalance) {
                                selectedPaymentMethod = "wallet"
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("parcel_payment_wallet"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isWallet,
                            onClick = {
                                if (hasEnoughBalance) selectedPaymentMethod = "wallet"
                            },
                            enabled = hasEnoughBalance,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = YellowPrimary,
                                disabledSelectedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Delivio Wallet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (hasEnoughBalance) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Formatters.formatPrice(walletBalance),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (hasEnoughBalance) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!hasEnoughBalance) {
                                Text(
                                    text = "Insufficient balance (needs ${Formatters.formatPrice(quote.totalFee)})",
                                    fontSize = 12.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "Instant 1-tap checkout deducted from your wallet",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // 3. Online Payments (Coming soon, disabled)
                    Text(
                        text = "Online Payments",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val onlineOptions = listOf("GCash", "Maya", "Bank Transfer", "Credit / Debit Card")
                    onlineOptions.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "Coming soon",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // LIVE FARE BREAKDOWN CARD
            ParcelFareBreakdownCard(
                quote = quote,
                feePayer = feePayer,
                selectedPaymentMethod = selectedPaymentMethod,
                isCalculatingRoute = isCalculatingRoute,
                errorMessage = errorMessage
            )
        }
    }
}

@Composable
internal fun ParcelPackageDetailsCard(
    packageSizes: List<String>,
    selectedSize: String,
    onSelectSize: (String) -> Unit,
    weightKg: Double,
    weightText: String,
    onWeightTextChange: (String) -> Unit,
    onDecreaseWeight: () -> Unit,
    onIncreaseWeight: () -> Unit,
    maxWeightKg: Double,
    selectedVehicle: String,
    isWeightExceeded: Boolean,
    itemDescription: String,
    onItemDescriptionChange: (String) -> Unit,
    isFragile: Boolean,
    onFragileChange: (Boolean) -> Unit,
    isSedanAvailable: Boolean,
    isMotorcycleAvailable: Boolean = true,
    onSelectVehicle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vehicleLabelLower = if (selectedVehicle == ParcelFeeCalculator.VEHICLE_SEDAN) "sedan" else "motorcycle"
    val maxWeightLabel = ParcelFeeCalculator.formatWeightLimitKg(maxWeightKg)

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(YellowPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("2", color = TextOnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Package Specifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(text = "Package Size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                packageSizes.forEach { size ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { onSelectSize(size) },
                        label = { Text(size, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YellowPrimary,
                            selectedLabelColor = TextOnYellow
                        )
                    )
                }
            }

            // Vehicle selection BEFORE or alongside weight so the weight limit stays prominent
            Text(text = "Vehicle Option", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val isMotoSel = selectedVehicle == ParcelFeeCalculator.VEHICLE_MOTORCYCLE
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMotoSel) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isMotoSel) BorderStroke(1.5.dp, TextOnYellow.copy(alpha = 0.2f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isMotorcycleAvailable) 1f else 0.45f)
                        .clickable(enabled = isMotorcycleAvailable) {
                            onSelectVehicle(ParcelFeeCalculator.VEHICLE_MOTORCYCLE)
                        }
                        .testTag("parcel_vehicle_motorcycle")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = "Motorcycle",
                            tint = if (isMotoSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Motorcycle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isMotoSel) TextOnYellow else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val isSedanSel = selectedVehicle == ParcelFeeCalculator.VEHICLE_SEDAN && isSedanAvailable
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        !isSedanAvailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        isSedanSel -> YellowPrimary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isSedanSel) BorderStroke(1.5.dp, TextOnYellow.copy(alpha = 0.2f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isSedanAvailable) 1f else 0.55f)
                        .clickable(enabled = isSedanAvailable) {
                            onSelectVehicle(ParcelFeeCalculator.VEHICLE_SEDAN)
                        }
                        .testTag("parcel_vehicle_sedan")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Sedan",
                            tint = when {
                                !isSedanAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                                isSedanSel -> TextOnYellow
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sedan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = when {
                                !isSedanAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                                isSedanSel -> TextOnYellow
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            Text(
                text = "Sedan: for 4-wheel and 8-wheel cars",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("parcel_sedan_helper_text")
            )

            if (!isSedanAvailable) {
                Text(
                    text = "Sedan is not available in your area yet",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ErrorRed,
                    modifier = Modifier.testTag("parcel_sedan_unavailable_note")
                )
            }

            // Weight Input + Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(text = "Weight (kg)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        text = "Max ${maxWeightLabel}kg for $vehicleLabelLower",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("parcel_weight_hint")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = onDecreaseWeight,
                        modifier = Modifier.size(40.dp).testTag("parcel_weight_decrease")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease weight", modifier = Modifier.size(16.dp))
                    }

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = onWeightTextChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = isWeightExceeded || weightKg <= 0.0,
                        modifier = Modifier
                            .width(72.dp)
                            .testTag("parcel_weight_input"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    IconButton(
                        onClick = onIncreaseWeight,
                        modifier = Modifier.size(40.dp).testTag("parcel_weight_increase")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase weight", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (isWeightExceeded) {
                Text(
                    text = "Weight exceeds ${maxWeightLabel}kg max limit for $vehicleLabelLower",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed,
                    modifier = Modifier.testTag("parcel_weight_error")
                )
            } else if (weightKg <= 0.0) {
                Text(
                    text = "Please enter a weight greater than 0 kg",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorRed,
                    modifier = Modifier.testTag("parcel_weight_error")
                )
            }

            OutlinedTextField(
                value = itemDescription,
                onValueChange = onItemDescriptionChange,
                label = { Text("Item Description (e.g. documents, phone)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("parcel_item_description")
            )

            // Fragile toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Fragile Item", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(text = "Rider handles with extra care", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isFragile,
                    onCheckedChange = onFragileChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextOnYellow,
                        checkedTrackColor = YellowPrimary
                    )
                )
            }
        }
    }
}

@Composable
internal fun ParcelFareBreakdownCard(
    quote: ParcelQuote,
    feePayer: String,
    selectedPaymentMethod: String,
    isCalculatingRoute: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val vehicleLabel = ParcelFeeCalculator.formatParcelVehicleDisplay(quote.vehicleType).ifBlank { "Motorcycle" }
    val isWallet = selectedPaymentMethod.equals("wallet", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("parcel_fare_breakdown")
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isCalculatingRoute) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = YellowPrimary)
                } else if (quote.distanceKm > 0.0) {
                    Text(
                        text = String.format(Locale.US, "%.1f km", quote.distanceKm),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Delivery Fee ($vehicleLabel)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = Formatters.formatPrice(quote.deliveryFee),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("parcel_delivery_fee_value")
                )
            }

            if (quote.peakFee > 0.0 || quote.nightFee > 0.0) {
                val extraNotes = buildList {
                    if (quote.peakFee > 0.0) add("Peak +${Formatters.formatPrice(quote.peakFee)}")
                    if (quote.nightFee > 0.0) add("Night +${Formatters.formatPrice(quote.nightFee)}")
                }.joinToString(" • ")
                Text(
                    text = "Includes $extraNotes",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Platform Service Fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    text = Formatters.formatPrice(quote.serviceFee),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("parcel_service_fee_value")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Total Delivery Cost", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = Formatters.formatPrice(quote.totalFee),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = YellowPrimary,
                    modifier = Modifier.testTag("parcel_total_fee_value")
                )
            }

            // Payment Chip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isWallet) SuccessGreen.copy(alpha = 0.12f) else YellowPrimary.copy(alpha = 0.18f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("parcel_payment_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isWallet) Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (isWallet) SuccessGreen else TextOnYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isWallet) {
                            "Paid with Delivio Wallet"
                        } else {
                            "Cash on Delivery - pay ${Formatters.formatPrice(quote.totalFee)} to the rider at ${if (feePayer == "recipient") "drop-off" else "pickup"}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWallet) SuccessGreen else TextOnYellow
                    )
                }
            }

            if (errorMessage != null) {
                Text(text = errorMessage, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
internal fun ParcelBottomBar(
    totalFee: Double,
    isFormValid: Boolean,
    isBooking: Boolean = false,
    missingReason: String? = null,
    onBookClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Delivery Cost",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatPrice(totalFee),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = YellowPrimary
                    )
                }

                if (missingReason != null) {
                    Text(
                        text = missingReason,
                        color = ErrorRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onBookClick,
                enabled = isFormValid && !isBooking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("book_parcel_order_button")
            ) {
                if (isBooking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnYellow, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Booking Parcel...", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = if (isFormValid) {
                            "Book Parcel Rider • ${Formatters.formatPrice(totalFee)}"
                        } else {
                            missingReason ?: "Complete Required Fields to Order"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
