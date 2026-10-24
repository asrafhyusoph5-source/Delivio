package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.Formatters
import com.example.viewmodel.AuthViewModel

@Composable
fun ParcelBookingView(
    orderRepository: OrderRepository,
    cityId: String = "puerto-princesa",
    authViewModel: AuthViewModel? = null,
    onOrderBooked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
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
    var itemDescription by remember { mutableStateOf("Documents & gift package") }
    var isFragile by remember { mutableStateOf(false) }

    val vehicleTypes = listOf("Motorcycle", "Sedan")
    var selectedVehicle by remember { mutableStateOf("Motorcycle") }

    // Fee payer & Payment method
    var feePayer by remember { mutableStateOf("sender") } // "sender" | "recipient"
    var selectedPaymentMethod by remember { mutableStateOf("cod") } // "cod" | "wallet"

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
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = feePayer == "recipient",
                        onClick = { feePayer = "recipient" },
                        label = { Text("Recipient Pays (Dropoff)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YellowPrimary,
                            selectedLabelColor = TextOnYellow
                        ),
                        modifier = Modifier.weight(1f)
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                            text = "Rider collects cash from ${if (feePayer == "recipient") "recipient upon dropoff" else "sender upon pickup"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Delivio Wallet
                val isWallet = selectedPaymentMethod == "wallet"
                val hasEnoughBalance = walletBalance >= totalFee
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isWallet) YellowPrimary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable(enabled = hasEnoughBalance) {
                            selectedPaymentMethod = "wallet"
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                text = "Insufficient balance (needs ${Formatters.formatPrice(totalFee)})",
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
                    Text("₱1.00", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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

                // Payment Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedPaymentMethod == "wallet") SuccessGreen.copy(alpha = 0.12f) else YellowPrimary.copy(alpha = 0.18f),
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
                            imageVector = if (selectedPaymentMethod == "wallet") Icons.Default.AccountBalanceWallet else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (selectedPaymentMethod == "wallet") SuccessGreen else TextOnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (selectedPaymentMethod == "wallet") "Paid with Delivio Wallet" else "Cash on Delivery - pay ${Formatters.formatPrice(totalFee)} to the rider",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedPaymentMethod == "wallet") SuccessGreen else TextOnYellow
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                val isBookingAllowed = (selectedPaymentMethod != "wallet" || walletBalance >= totalFee)
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

                        val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
                        val customerId = fbUser?.uid ?: ""

                        coroutineScope.launch {
                            val result = orderRepository.bookParcelOrder(
                                customerId = customerId,
                                customerName = senderName.trim(),
                                customerPhone = senderPhone.trim(),
                                parcelDetails = parcel,
                                deliveryFee = baseFee + weightFee,
                                serviceFee = 1.0,
                                cityId = cityId,
                                feePayer = feePayer,
                                paymentMethod = selectedPaymentMethod
                            )
                            if (result.isSuccess) {
                                onOrderBooked()
                            } else {
                                errorMessage = result.exceptionOrNull()?.localizedMessage
                                    ?: result.exceptionOrNull()?.message
                                    ?: "Failed to book parcel"
                            }
                        }
                    },
                    enabled = isBookingAllowed,
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
                        text = if (selectedPaymentMethod == "wallet") "Pay with Wallet • ${Formatters.formatPrice(totalFee)}" else "Book Parcel Rider • ${Formatters.formatPrice(totalFee)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
