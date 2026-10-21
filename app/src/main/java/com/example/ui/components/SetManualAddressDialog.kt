package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Modal Bottom Sheet allowing customers to manually enter all details of their address,
 * complete with preset label chips, street/unit fields, landmarks, phone,
 * and an embedded Real OpenStreetMap for precise location pin positioning.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetManualAddressDialog(
    initialTitle: String = "Home",
    initialAddress: String = "",
    initialDetails: String = "",
    initialLat: Double = 9.7392,
    initialLng: Double = 118.7353,
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onSaveAddress: (title: String, fullAddress: String, details: String, setAsActive: Boolean, lat: Double, lng: Double) -> Unit
) {
    val presets = remember { listOf("Home 🏠", "Work 🏢", "Condo 🏢", "Apartment 🏡", "Family 👨‍👩‍👧", "Custom 📍") }
    var selectedPreset by remember { mutableStateOf(if (initialTitle in listOf("Home", "Work", "Condo", "Apartment", "Family")) "$initialTitle 🏠" else "Custom 📍") }
    var addressTitle by remember { mutableStateOf(initialTitle.ifEmpty { "Home" }) }
    var streetAddress by remember { mutableStateOf(initialAddress) }
    var cityDistrict by remember { mutableStateOf("Puerto Princesa, Palawan") }
    var unitFloor by remember { mutableStateOf("") }
    var landmarkNotes by remember { mutableStateOf(initialDetails) }
    var contactPhone by remember { mutableStateOf("+63 917 123 4567") }
    var setAsActive by remember { mutableStateOf(true) }
    var showMapPicker by remember { mutableStateOf(true) }
    var pinLat by remember { mutableDoubleStateOf(initialLat) }
    var pinLng by remember { mutableDoubleStateOf(initialLng) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("set_manual_address_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 750.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextOnYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isEditing) "Edit Saved Address" else "Set Address Manually",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEditing) "Update your delivery address label, street, and pin" else "Enter your exact delivery address and pin",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Quick Preset Label Chips
            Text(
                text = "Address Label",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSel = selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedPreset = preset
                                val cleanName = preset.substringBefore(" ").trim()
                                if (cleanName != "Custom") {
                                    addressTitle = cleanName
                                }
                            }
                    ) {
                        Text(
                            text = preset,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Custom Title if needed
            OutlinedTextField(
                value = addressTitle,
                onValueChange = { addressTitle = it },
                label = { Text("Address Name (e.g. My Home, Office, Mom's House)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_address_title_input")
            )

            // Street & House Number
            OutlinedTextField(
                value = streetAddress,
                onValueChange = {
                    streetAddress = it
                    errorMessage = null
                },
                label = { Text("Street Address / Building Name *") },
                placeholder = { Text("e.g. 742 Evergreen Terrace, Sunshine Condominiums") },
                leadingIcon = {
                    Icon(Icons.Default.Home, contentDescription = null, tint = YellowPrimary)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_street_address_input")
            )

            // Unit / Floor & City District
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = unitFloor,
                    onValueChange = { unitFloor = it },
                    label = { Text("Unit / Floor") },
                    placeholder = { Text("e.g. Apt 4B") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.45f)
                )

                OutlinedTextField(
                    value = cityDistrict,
                    onValueChange = { cityDistrict = it },
                    label = { Text("City / District") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.55f)
                )
            }

            // Landmark & Delivery Instructions
            OutlinedTextField(
                value = landmarkNotes,
                onValueChange = { landmarkNotes = it },
                label = { Text("Nearest Landmark / Delivery Instructions") },
                placeholder = { Text("e.g. Ring bell twice, leave with lobby security") },
                leadingIcon = {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = YellowPrimary)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Contact Phone for this Address
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Contact Phone for Rider") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Real Map Pin Location Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Real Map Pin Position",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = YellowPrimary.copy(alpha = 0.2f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMapPicker = !showMapPicker }
                        ) {
                            Text(
                                text = if (showMapPicker) "Hide Map ⏶" else "Show Map ⏷",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YellowPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (showMapPicker) {
                        OsmLocationPickerMap(
                            title = addressTitle.ifBlank { "Delivery Pin" },
                            initialAddress = streetAddress,
                            initialLat = pinLat,
                            initialLng = pinLng,
                            pinColor = YellowPrimary,
                            mapHeightDp = 200,
                            showConfirmButton = false,
                            onLocationConfirmed = { addr, lat, lng ->
                                pinLat = lat
                                pinLng = lng
                                streetAddress = addr
                            }
                        )
                    }
                }
            }

            // Set as active address toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Set as Active Delivery Address",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Current orders will default to this address",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = setAsActive,
                    onCheckedChange = { setAsActive = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextOnYellow,
                        checkedTrackColor = YellowPrimary
                    )
                )
            }

            // Error display if validation fails
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val trimmedStreet = streetAddress.trim()
                        if (trimmedStreet.isEmpty()) {
                            errorMessage = "Please enter your street address or building name."
                            return@Button
                        }

                        val fullFormattedAddress = buildString {
                            if (unitFloor.isNotBlank()) append("${unitFloor.trim()}, ")
                            append(trimmedStreet)
                            if (cityDistrict.isNotBlank()) append(", ${cityDistrict.trim()}")
                        }

                        val combinedDetails = buildString {
                            if (landmarkNotes.isNotBlank()) append(landmarkNotes.trim())
                            if (contactPhone.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append("Tel: ${contactPhone.trim()}")
                            }
                        }

                        onSaveAddress(addressTitle.trim(), fullFormattedAddress, combinedDetails, setAsActive, pinLat, pinLng)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("save_manual_address_button")
                ) {
                    Text(
                        text = if (isEditing) "Update Address" else "Save Address",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
