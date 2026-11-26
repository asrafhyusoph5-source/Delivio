package com.example.ui.screens.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Airport
import com.example.data.model.AirportDirectory
import com.example.data.model.FlightDateUtil
import com.example.data.model.FlightFormValidator
import com.example.data.model.TravelersCount
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportSearchSheet(
    title: String,
    blockedAirportCode: String?,
    recentAirportCodes: List<String>,
    onDismiss: () -> Unit,
    onSelectAirport: (Airport) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val recentAirports = remember(recentAirportCodes, query) {
        if (query.isNotBlank()) emptyList()
        else recentAirportCodes.mapNotNull { AirportDirectory.findByCode(it) }
    }
    val filteredAll = remember(query) { AirportDirectory.search(query) }
    val domestic = remember(filteredAll) { filteredAll.filter { it.isDomestic } }
    val international = remember(filteredAll) { filteredAll.filter { !it.isDomestic } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search airport code, city, or name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("airport_search_input")
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (recentAirports.isNotEmpty()) {
                    item {
                        SectionLabel(text = "Recent Searches", icon = Icons.Default.History)
                    }
                    items(recentAirports, key = { "recent_${it.code}" }) { airport ->
                        val isBlocked = blockedAirportCode.equals(airport.code, ignoreCase = true)
                        AirportRow(
                            airport = airport,
                            isDisabled = isBlocked,
                            onClick = { onSelectAirport(airport) },
                            tagPrefix = "recent_airport"
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                if (domestic.isNotEmpty()) {
                    item {
                        SectionLabel(text = "Philippine Airports", icon = Icons.Default.FlightTakeoff)
                    }
                    items(domestic, key = { "dom_${it.code}" }) { airport ->
                        val isBlocked = blockedAirportCode.equals(airport.code, ignoreCase = true)
                        AirportRow(
                            airport = airport,
                            isDisabled = isBlocked,
                            onClick = { onSelectAirport(airport) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                if (international.isNotEmpty()) {
                    item {
                        SectionLabel(text = "International Airports", icon = Icons.Default.Public)
                    }
                    items(international, key = { "intl_${it.code}" }) { airport ->
                        val isBlocked = blockedAirportCode.equals(airport.code, ignoreCase = true)
                        AirportRow(
                            airport = airport,
                            isDisabled = isBlocked,
                            onClick = { onSelectAirport(airport) }
                        )
                    }
                }

                if (filteredAll.isEmpty()) {
                    item {
                        Text(
                            text = "No matching airports found for \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YellowPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AirportRow(
    airport: Airport,
    isDisabled: Boolean,
    onClick: () -> Unit,
    tagPrefix: String = "airport"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .alpha(if (isDisabled) 0.45f else 1f)
            .clickable(enabled = !isDisabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("${tagPrefix}_${airport.code}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = YellowPrimary.copy(alpha = 0.22f)
        ) {
            Text(
                text = airport.code,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = TextOnYellow,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = airport.city,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isDisabled) "${airport.displaySubtitle} • Already selected" else airport.displaySubtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelersBottomSheet(
    initialTravelers: TravelersCount,
    onDismiss: () -> Unit,
    onConfirm: (TravelersCount) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var count by remember(initialTravelers) { mutableStateOf(initialTravelers) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Travelers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Up to 9 travelers total. Infants cannot exceed adults.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TravelerStepperRow(
                title = "Adults",
                subtitle = "12 years and above",
                value = count.adults,
                canDecrement = FlightFormValidator.canDecrementAdults(count),
                canIncrement = FlightFormValidator.canIncrementAdults(count),
                onDecrement = { count = FlightFormValidator.decrementAdults(count) },
                onIncrement = { count = FlightFormValidator.incrementAdults(count) },
                tagPrefix = "adults"
            )
            HorizontalDivider()
            TravelerStepperRow(
                title = "Children",
                subtitle = "2 to 11 years",
                value = count.children,
                canDecrement = FlightFormValidator.canDecrementChildren(count),
                canIncrement = FlightFormValidator.canIncrementChildren(count),
                onDecrement = { count = FlightFormValidator.decrementChildren(count) },
                onIncrement = { count = FlightFormValidator.incrementChildren(count) },
                tagPrefix = "children"
            )
            HorizontalDivider()
            TravelerStepperRow(
                title = "Infants",
                subtitle = "Under 2 years (on lap)",
                value = count.infants,
                canDecrement = FlightFormValidator.canDecrementInfants(count),
                canIncrement = FlightFormValidator.canIncrementInfants(count),
                onDecrement = { count = FlightFormValidator.decrementInfants(count) },
                onIncrement = { count = FlightFormValidator.incrementInfants(count) },
                tagPrefix = "infants"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = { onConfirm(count) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("travelers_done_button")
            ) {
                Text("Done (${count.formatSummary()})", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun TravelerStepperRow(
    title: String,
    subtitle: String,
    value: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedIconButton(
                onClick = onDecrement,
                enabled = canDecrement,
                shape = CircleShape,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag("travelers_${tagPrefix}_minus")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $title")
            }

            Text(
                text = value.toString(),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .width(24.dp)
                    .testTag("travelers_${tagPrefix}_value")
            )

            OutlinedIconButton(
                onClick = onIncrement,
                enabled = canIncrement,
                shape = CircleShape,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(48.dp)
                    .testTag("travelers_${tagPrefix}_plus")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $title")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDatePickerDialog(
    initialSelectedDateMillis: Long?,
    minSelectableUtcMillis: Long,
    onDismiss: () -> Unit,
    onConfirmDate: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis?.takeIf { it >= minSelectableUtcMillis }
            ?: minSelectableUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return FlightDateUtil.normalizeToUtcMidnight(utcTimeMillis) >= minSelectableUtcMillis
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = datePickerState.selectedDateMillis
                    if (picked != null) {
                        onConfirmDate(FlightDateUtil.normalizeToUtcMidnight(picked))
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
