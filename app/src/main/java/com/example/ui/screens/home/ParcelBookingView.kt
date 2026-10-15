package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.ParcelDetails
import com.example.data.repository.OrderRepository
import com.example.ui.components.InteractiveLocationPickerMap
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.AuthConstants
import com.example.util.Formatters

@Composable
fun ParcelBookingView(
    orderRepository: OrderRepository,
    cityId: String = "puerto-princesa",
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    // Sender / "From"
    var senderName by remember { mutableStateOf(AuthConstants.DEMO_USER_NAME) }
    var senderPhone by remember { mutableStateOf(AuthConstants.DEMO_USER_PHONE) }
    var senderAddress by remember { mutableStateOf("742 Evergreen Terrace, Springfield") }
    var pickupLat by remember { mutableFloatStateOf(0.25f) }
    var pickupLng by remember { mutableFloatStateOf(0.30f) }

    // Recipient / "Where to"
    var recipientName by remember { mutableStateOf("Sarah Connor") }
    var recipientPhone by remember { mutableStateOf("+1 (555) 890-1234") }
    var recipientAddress by remember { mutableStateOf("500 Howard St, Financial District") }
    var dropoffLat by remember { mutableFloatStateOf(0.75f) }
    var dropoffLng by remember { mutableFloatStateOf(0.70f) }

    // Package details
    val packageSizes = listOf("Small / Pouch", "Medium / Box", "Large / Cargo")
    var selectedSize by remember { mutableStateOf("Small / Pouch") }
    var weightKg by remember { mutableDoubleStateOf(2.5) }
    var itemDescription by remember { mutableStateOf("Documents & gift package") }
    var isFragile by remember { mutableStateOf(false) }

    val vehicleTypes = listOf("Motorcycle", "Sedan")
    var selectedVehicle by remember { mutableStateOf("Motorcycle") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fee calculation
    val baseFee = if (selectedVehicle == "Motorcycle") 4.50 else 7.50
    val weightFee = if (weightKg > 5.0) (weightKg - 5.0) * 0.50 else 0.0
    val totalFee = baseFee + weightFee + 1.00 // service fee

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    Text("📦", fontSize = 24.sp)
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

                // Interactive map of "from"
                InteractiveLocationPickerMap(
                    title = "Pickup Pin (From)",
                    initialAddress = senderAddress,
                    pinColor = Color(0xFFE53935),
                    onLocationSelected = { newAddress, x, y ->
                        senderAddress = newAddress
                        pickupLat = x
                        pickupLng = y
                    }
                )
            }
        }

        // STEP 2: PACKAGE DETAILS
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
                            onClick = { selectedSize = size },
                            label = { Text(size, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YellowPrimary,
                                selectedLabelColor = TextOnYellow
                            )
                        )
                    }
                }

                // Weight Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Weight (kls / kg)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(text = "Max 20kg for motorcycle", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { if (weightKg > 0.5) weightKg = (weightKg - 0.5).coerceAtLeast(0.5) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = String.format("%.1f kg", weightKg),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { if (weightKg < 25.0) weightKg = weightKg + 0.5 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = itemDescription,
                    onValueChange = { itemDescription = it },
                    label = { Text("Item Description (e.g. documents, phone)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
                        onCheckedChange = { isFragile = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextOnYellow,
                            checkedTrackColor = YellowPrimary
                        )
                    )
                }

                // Vehicle selection
                Text(text = "Vehicle Option", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    vehicleTypes.forEach { v ->
                        val isSel = selectedVehicle == v
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedVehicle = v }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (v == "Motorcycle") Icons.Default.DirectionsBike else Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = v,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

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

                // Interactive map of "where to"
                InteractiveLocationPickerMap(
                    title = "Destination Pin (Where to)",
                    initialAddress = recipientAddress,
                    pinColor = Color(0xFF43A047),
                    onLocationSelected = { newAddress, x, y ->
                        recipientAddress = newAddress
                        dropoffLat = x
                        dropoffLng = y
                    }
                )
            }
        }

        // PRICE BREAKDOWN & BOOKING
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
                Text(
                    text = "Fare Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Base Courier Fare ($selectedVehicle)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(Formatters.formatPrice(baseFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                if (weightFee > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Heavy Weight Surcharge", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(Formatters.formatPrice(weightFee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Insurance & Service Fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("$1.00", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total Delivery Cost", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = Formatters.formatPrice(totalFee), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = YellowPrimary)
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (senderName.isBlank() || senderPhone.isBlank() || recipientName.isBlank() || recipientPhone.isBlank()) {
                            errorMessage = "Please complete all sender and recipient details"
                            return@Button
                        }
                        errorMessage = null

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
                            vehicleType = selectedVehicle
                        )

                        coroutineScope.launch {
                            val result = orderRepository.bookParcelOrder(
                                customerId = "cust-1",
                                customerName = senderName.trim(),
                                customerPhone = senderPhone.trim(),
                                parcelDetails = parcel,
                                deliveryFee = baseFee + weightFee,
                                serviceFee = 15.0,
                                cityId = cityId
                            )
                            if (result.isSuccess) {
                                onOrderBooked()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to book parcel"
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("book_parcel_order_button")
                ) {
                    Text(
                        text = "Book Parcel Rider • ${Formatters.formatPrice(totalFee)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
