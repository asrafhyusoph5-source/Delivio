package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.OsrmRoutingService
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliItem
import com.example.data.model.RateConfig
import com.example.data.repository.OrderRepository
import com.example.ui.components.OsmLocationPickerMap
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Pabili (Buy For Me) Errand Booking Screen.
 * Features:
 * 1. Step 1: Store name with sample placeholder only (no pre-filled text) + OpenStreetMap "Buy from" pin picker
 * 2. Step 2: Itemized errand list with quantity and estimated prices + optional budget limit
 * 3. Step 3: Recipient details + OpenStreetMap "Deliver to" destination pin picker
 * 4. Live Fee Breakdown based on deliveryRates/{cityId}.services.pabili derived from store pin location:
 *    Distance (OSRM road or fallback straight-line), Delivery fee, Service fee, Estimated items, Total.
 *    Validates that store and dropoff are within the same active service city.
 */
@Composable
fun PabiliBookingView(
    orderRepository: OrderRepository,
    cityId: String = "puerto-princesa",
    authViewModel: AuthViewModel? = null,
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val authUiState by (authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val currentUser = authUiState?.currentUser

    // 1. Store details
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var storeLat by remember { mutableDoubleStateOf(0.0) }
    var storeLng by remember { mutableDoubleStateOf(0.0) }

    // 2. Errand items list
    val items = remember {
        mutableStateListOf(
            PabiliItem(name = "", quantity = 1, estimatedPrice = 0.0)
        )
    }
    var budgetLimit by remember { mutableDoubleStateOf(1000.0) }

    // 3. Recipient & Dropoff details
    var recipientName by remember(currentUser) {
        mutableStateOf(currentUser?.name ?: "")
    }
    var recipientPhone by remember(currentUser) {
        mutableStateOf(currentUser?.phone ?: "")
    }
    var deliveryAddress by remember { mutableStateOf("") }
    var deliveryLat by remember { mutableDoubleStateOf(0.0) }
    var deliveryLng by remember { mutableDoubleStateOf(0.0) }
    var riderNotes by remember { mutableStateOf("Please ask for receipt and check expiry dates.") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBooking by remember { mutableStateOf(false) }

    // Firestore Cities & Delivery Rates
    val cities by orderRepository.firestoreService.getCitiesFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    // Resolved city matching store pin coordinates
    var resolvedCity by remember { mutableStateOf<City?>(null) }
    var isRouteAvailable by remember { mutableStateOf(true) }
    var routeDistanceKm by remember { mutableDoubleStateOf(0.0) }
    var distanceMethod by remember { mutableStateOf("osrm") }
    var isCalculatingRoute by remember { mutableStateOf(false) }

    // Rates for resolved city
    var currentRateConfig by remember { mutableStateOf(RateConfig(baseFare = 49.0, includedKm = 2.0, perKmRate = 12.0, minFee = 49.0, maxFee = 300.0, serviceFee = 20.0)) }

    // Find city containing coordinates
    fun findCityForCoords(lat: Double, lng: Double, cityList: List<City>): City? {
        if (lat == 0.0 || lng == 0.0) return null
        return cityList.firstOrNull { city ->
            city.status.equals("active", ignoreCase = true) &&
                    OsrmRoutingService.calculateHaversineDistanceKm(lat, lng, city.lat, city.lng) <= city.radiusKm
        } ?: cityList.firstOrNull { it.id == cityId }
    }

    // Dynamic Route & Fee Recalculation whenever Store Pin or Delivery Pin moves
    LaunchedEffect(storeLat, storeLng, deliveryLat, deliveryLng, cities) {
        val hasStorePin = storeLat != 0.0 && storeLng != 0.0
        val hasDropoffPin = deliveryLat != 0.0 && deliveryLng != 0.0

        if (hasStorePin) {
            val matchedStoreCity = findCityForCoords(storeLat, storeLng, cities)
            resolvedCity = matchedStoreCity

            if (hasDropoffPin) {
                val matchedDropoffCity = findCityForCoords(deliveryLat, deliveryLng, cities)

                if (matchedStoreCity == null || matchedDropoffCity == null || matchedStoreCity.id != matchedDropoffCity.id) {
                    isRouteAvailable = false
                } else {
                    isRouteAvailable = true
                }

                // Compute road distance via OSRM with straight-line fallback
                isCalculatingRoute = true
                val route = OsrmRoutingService.calculateRoute(storeLat, storeLng, deliveryLat, deliveryLng)
                isCalculatingRoute = false
                routeDistanceKm = route.distanceKm
                distanceMethod = if (route.isFallback) "straight_line" else "osrm"
            } else {
                routeDistanceKm = 0.0
                isRouteAvailable = true
            }
        }
    }

    // Listen to rates for resolved city
    val targetCityId = resolvedCity?.id ?: cityId
    LaunchedEffect(targetCityId) {
        orderRepository.firestoreService.getDeliveryRatesFlow(targetCityId).collect { rates ->
            currentRateConfig = rates.pabili
        }
    }

    // Calculate Fees
    val validItems = items.filter { it.name.isNotBlank() }
    val estimatedItemsTotal = validItems.sumOf { it.estimatedPrice * it.quantity }

    val deliveryFee = remember(routeDistanceKm, currentRateConfig) {
        if (routeDistanceKm <= 0.0) {
            currentRateConfig.baseFare
        } else {
            val extraKm = (routeDistanceKm - currentRateConfig.includedKm).coerceAtLeast(0.0)
            val fee = currentRateConfig.baseFare + (extraKm * currentRateConfig.perKmRate)
            fee.coerceIn(currentRateConfig.minFee, currentRateConfig.maxFee) + currentRateConfig.surcharge
        }
    }
    val serviceFee = currentRateConfig.serviceFee
    val totalEstimatedToPay = estimatedItemsTotal + deliveryFee + serviceFee

    // Validation checks
    val isStoreNameSet = storeName.isNotBlank()
    val isStorePinSet = storeAddress.isNotBlank() && storeLat != 0.0 && storeLng != 0.0
    val hasValidItems = validItems.isNotEmpty()
    val isRecipientNameSet = recipientName.isNotBlank()
    val isRecipientPhoneSet = recipientPhone.isNotBlank()
    val isDropoffPinSet = deliveryAddress.isNotBlank() && deliveryLat != 0.0 && deliveryLng != 0.0

    val missingFields = mutableListOf<String>().apply {
        if (!isStoreNameSet) add("Store Name")
        if (!isStorePinSet) add("Store Pin on Map")
        if (!hasValidItems) add("At least 1 item with name & quantity")
        if (!isRecipientNameSet) add("Recipient Name")
        if (!isRecipientPhoneSet) add("Recipient Phone")
        if (!isDropoffPinSet) add("Delivery Destination Pin")
        if (!isRouteAvailable && isStorePinSet && isDropoffPinSet) add("Active Route (Store and delivery must be in the same city)")
    }
    val isFormValid = missingFields.isEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
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
                    val missingHint = if (!isFormValid) "Missing: ${missingFields.firstOrNull() ?: "Required fields"}" else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Estimated Total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatPrice(totalEstimatedToPay),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = YellowPrimary
                            )
                        }

                        if (missingHint != null) {
                            Text(
                                text = missingHint,
                                color = MaterialTheme.colorScheme.error,
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
                        onClick = {
                            if (!isFormValid) return@Button
                            errorMessage = null
                            isBooking = true

                            val pabili = PabiliDetails(
                                storeName = storeName.trim(),
                                storeAddress = storeAddress.trim(),
                                storeLat = storeLat.toFloat(),
                                storeLng = storeLng.toFloat(),
                                items = validItems,
                                recipientName = recipientName.trim(),
                                recipientPhone = recipientPhone.trim(),
                                deliveryAddress = deliveryAddress.trim(),
                                deliveryLat = deliveryLat.toFloat(),
                                deliveryLng = deliveryLng.toFloat(),
                                riderNotes = riderNotes.trim()
                            )

                            val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
                            val customerId = fbUser?.uid ?: ""

                            coroutineScope.launch {
                                val result = orderRepository.bookPabiliOrder(
                                    customerId = customerId,
                                    customerName = recipientName.trim(),
                                    customerPhone = recipientPhone.trim(),
                                    pabiliDetails = pabili,
                                    itemTotal = estimatedItemsTotal,
                                    deliveryFee = deliveryFee,
                                    serviceFee = serviceFee,
                                    cityId = targetCityId,
                                    distanceKm = routeDistanceKm,
                                    distanceMethod = distanceMethod,
                                    budget = budgetLimit
                                )
                                isBooking = false
                                if (result.isSuccess) {
                                    onOrderBooked()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.localizedMessage
                                        ?: result.exceptionOrNull()?.message
                                        ?: "Failed to book pabili errand."
                                }
                            }
                        },
                        enabled = isFormValid && !isBooking,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = TextOnYellow,
                            disabledContainerColor = YellowPrimary.copy(alpha = 0.35f),
                            disabledContentColor = TextOnYellow.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("book_pabili_order_button")
                    ) {
                        if (isBooking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnYellow, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Booking Errand...", fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = if (isFormValid) "Place Pabili Order • ${Formatters.formatPrice(totalEstimatedToPay)}" else "Complete Required Fields to Order",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
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
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = "Pabili",
                        tint = TextOnYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Pabili Service (Buy For Me)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time OpenStreetMap routing with live delivery fee calculated per kilometer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // STEP 1: STORE OR MERCHANT TO BUY FROM
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
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1", color = TextOnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Store or Merchant to Buy From *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Store Name with sample text only as placeholder hint (required)
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store / Merchant Name *") },
                    placeholder = { Text("e.g. Mercury Drug, Robinsons Supermarket, Watsons", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_store_name")
                )

                Text(
                    text = "Pick the exact store pin location on the map below (Required):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // OpenStreetMap Store Pin Picker with Search & Draggable Pin
                OsmLocationPickerMap(
                    title = "Store Pin (Buy from)",
                    initialAddress = storeAddress,
                    initialLat = if (storeLat != 0.0) storeLat else 9.7392,
                    initialLng = if (storeLng != 0.0) storeLng else 118.7353,
                    pinColor = YellowPrimary,
                    mapHeightDp = 220,
                    showConfirmButton = true,
                    onLocationConfirmed = { addr, lat, lng ->
                        storeAddress = addr
                        storeLat = lat
                        storeLng = lng
                    }
                )

                if (storeAddress.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = YellowPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📍 Store Pin:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = YellowPrimary)
                            Text(storeAddress, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // STEP 2: LIST OF ITEMS TO BUY
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
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(YellowPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", color = TextOnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "List of Items to Buy * (${items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items.forEachIndexed { index, item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Item #${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = YellowPrimary
                                )
                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete item",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { newName -> items[index] = item.copy(name = newName) },
                                label = { Text("Item Name & Brand *") },
                                placeholder = { Text("e.g. Paracetamol 500mg, Bread, Milk", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Qty stepper
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (item.quantity > 1) items[index] = item.copy(quantity = item.quantity - 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    IconButton(
                                        onClick = { items[index] = item.copy(quantity = item.quantity + 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Estimated price field
                                OutlinedTextField(
                                    value = if (item.estimatedPrice > 0) String.format("%.2f", item.estimatedPrice) else "",
                                    onValueChange = { priceStr ->
                                        val parsed = priceStr.toDoubleOrNull() ?: 0.0
                                        items[index] = item.copy(estimatedPrice = parsed)
                                    },
                                    label = { Text("Est. Price (₱)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { items.add(PabiliItem(name = "", quantity = 1, estimatedPrice = 0.0)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_pabili_item_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Another Item", fontWeight = FontWeight.Bold)
                }

                // Budget Limit Field
                OutlinedTextField(
                    value = if (budgetLimit > 0) String.format("%.0f", budgetLimit) else "",
                    onValueChange = { str ->
                        budgetLimit = str.toDoubleOrNull() ?: 1000.0
                    },
                    label = { Text("Spending Budget Limit (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Budget warning if estimated items exceed budget
                if (budgetLimit > 0 && estimatedItemsTotal > budgetLimit) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Warning: Estimated items total (${Formatters.formatPrice(estimatedItemsTotal)}) exceeds your budget of ${Formatters.formatPrice(budgetLimit)}.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Estimated Items:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.formatPrice(estimatedItemsTotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // STEP 3: RECIPIENT & DELIVER TO DESTINATION MAP
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
                        text = "Deliver To *",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient Name *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_recipient_name")
                )

                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("Recipient Contact Number *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pabili_recipient_phone")
                )

                Text(
                    text = "Pick the delivery drop-off pin location on the map below (Required):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // OpenStreetMap Dropoff Pin Picker with Search & Draggable Pin
                OsmLocationPickerMap(
                    title = "Destination Pin (Deliver to)",
                    initialAddress = deliveryAddress,
                    initialLat = if (deliveryLat != 0.0) deliveryLat else 9.7420,
                    initialLng = if (deliveryLng != 0.0) deliveryLng else 118.7400,
                    pinColor = Color(0xFF43A047),
                    mapHeightDp = 220,
                    showConfirmButton = true,
                    onLocationConfirmed = { addr, lat, lng ->
                        deliveryAddress = addr
                        deliveryLat = lat
                        deliveryLng = lng
                    }
                )

                if (deliveryAddress.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF43A047).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🏠 Drop-off Pin:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                            Text(deliveryAddress, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                OutlinedTextField(
                    value = riderNotes,
                    onValueChange = { riderNotes = it },
                    label = { Text("Instructions for Rider") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = YellowPrimary) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. LIVE FEE BREAKDOWN (Card directly above Place Order button)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Fee Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCalculatingRoute) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = YellowPrimary)
                    }
                }

                // If outside active cities or store/dropoff in different cities
                if (!isRouteAvailable) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Delivery not available for this route. Store pin and drop-off pin must both be located within the same active service city.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Distance", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(
                        text = if (routeDistanceKm > 0.0) "${String.format("%.1f", routeDistanceKm)} km" else "Set both pins to calculate",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // Delivery fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Delivery fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(deliveryFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // Service fee
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Service fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(serviceFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // Estimated items total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estimated items total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(estimatedItemsTotal), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // Estimated total to pay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Estimated total to pay", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = Formatters.formatPrice(totalEstimatedToPay), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = YellowPrimary)
                }

                // Notice stating item prices are estimates
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Item prices are estimates. The final amount is based on the rider's receipt.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                // Validation missing fields helper
                if (!isFormValid) {
                    Text(
                        text = "Please complete: ${missingFields.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Payment Chip (Pabili is strictly COD only)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = YellowPrimary.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("pabili_payment_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = TextOnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Cash on Delivery - pay ${Formatters.formatPrice(totalEstimatedToPay)} to the rider",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOnYellow
                        )
                    }
                }

            }
        }
    }
}
}
