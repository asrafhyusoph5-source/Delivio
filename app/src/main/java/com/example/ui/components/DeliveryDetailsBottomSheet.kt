package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Address
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

/**
 * Shared bottom sheet shown after confirming a pinned location on MapPickerScreen.
 * Lets the customer edit the address text, pick a label (Home, Work, Other),
 * optionally add a Landmark or unit, and decide whether to save it to users/{uid}/addresses
 * (hidden with a 3-address limit note when 3 addresses are already saved).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDetailsBottomSheet(
    initialAddress: String,
    lat: Double,
    lng: Double,
    savedAddressCount: Int,
    initialLabel: String = "Home",
    initialLandmark: String = "",
    onDismiss: () -> Unit,
    onConfirm: (addressText: String, label: String, landmarkOrUnit: String, saveToMyAddresses: Boolean) -> Unit
) {
    val labelOptions = remember { listOf("Home", "Work", "Other") }
    var addressText by remember(initialAddress) { mutableStateOf(initialAddress) }
    var selectedLabel by remember(initialLabel) {
        mutableStateOf(if (initialLabel in labelOptions) initialLabel else "Home")
    }
    var landmarkOrUnit by remember(initialLandmark) { mutableStateOf(initialLandmark) }
    val canSaveNewAddress = savedAddressCount < 3
    var saveToMyAddresses by remember(canSaveNewAddress) { mutableStateOf(canSaveNewAddress) }
    var addressError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("delivery_details_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Delivery details",
                            tint = YellowPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Delivery details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delivery_details_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = addressText,
                onValueChange = {
                    addressText = it
                    addressError = null
                },
                label = { Text("Delivery address") },
                placeholder = { Text("Street, barangay, city") },
                isError = addressError != null,
                supportingText = addressError?.let { err -> { Text(err, color = ErrorRed) } },
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delivery_details_address_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Label",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    labelOptions.forEach { label ->
                        val isSelected = selectedLabel == label
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, YellowPrimary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedLabel = label }
                                .testTag("delivery_details_label_$label")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = landmarkOrUnit,
                onValueChange = { landmarkOrUnit = it },
                label = { Text("Landmark or unit (optional)") },
                placeholder = { Text("e.g. Gate 2, Unit 4B, near church") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delivery_details_landmark_input")
            )

            if (canSaveNewAddress) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Save to my addresses",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = saveToMyAddresses,
                            onCheckedChange = { saveToMyAddresses = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextOnYellow,
                                checkedTrackColor = YellowPrimary
                            ),
                            modifier = Modifier.testTag("delivery_details_save_switch")
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delivery_details_max_addresses_note")
                ) {
                    Text(
                        text = "You already have 3 saved addresses",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val trimmed = addressText.trim()
                    if (trimmed.isEmpty()) {
                        addressError = "Please enter your delivery address"
                    } else {
                        onConfirm(
                            trimmed,
                            selectedLabel,
                            landmarkOrUnit.trim(),
                            canSaveNewAddress && saveToMyAddresses
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("delivery_details_confirm_button")
            ) {
                Text(
                    text = "Confirm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Shared bottom sheet showing the customer's saved addresses plus a "Pin a new location" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAddressesBottomSheet(
    savedAddresses: List<Address>,
    currentAddress: Address,
    onSelectAddress: (Address) -> Unit,
    onPinNewLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("saved_addresses_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delivery Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            savedAddresses.forEach { address ->
                val isSelected = address.id == currentAddress.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) YellowPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .clickable { onSelectAddress(address) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("saved_address_option_${address.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectAddress(address) },
                        colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = address.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = address.fullAddress,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (address.details.isNotBlank()) {
                            Text(
                                text = address.details,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            OutlinedButton(
                onClick = onPinNewLocation,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, YellowPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("pin_new_location_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocationAlt,
                    contentDescription = "Pin a new location",
                    tint = YellowPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pin a new location",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
