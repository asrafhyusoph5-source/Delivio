package com.example.ui.screens.flight

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Airport
import com.example.data.model.CabinClass
import com.example.data.model.City
import com.example.data.model.FlightDateUtil
import com.example.data.model.FlightFormValidator
import com.example.data.model.FlightLeg
import com.example.data.model.FlightSearchUiState
import com.example.data.model.PlatformSettings
import com.example.data.model.TripType
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.ServiceAvailability

private sealed class ActiveAirportPicker {
    data object MainOrigin : ActiveAirportPicker()
    data object MainDestination : ActiveAirportPicker()
    data class LegOrigin(val index: Int) : ActiveAirportPicker()
    data class LegDestination(val index: Int) : ActiveAirportPicker()
}

private sealed class ActiveDatePicker {
    data object Departure : ActiveDatePicker()
    data object Return : ActiveDatePicker()
    data class MultiLeg(val index: Int) : ActiveDatePicker()
}

@Composable
fun FlightScreen(
    restaurantRepository: RestaurantRepository,
    preferenceRepository: PreferenceRepository,
    cityId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())
    val appDesignerSettings by restaurantRepository.appDesignerSettings.collectAsState()
    val resolvedCity = remember(cityId, cities) {
        ServiceAvailability.resolveCity(cityId, cities) ?: City(id = cityId, name = cityId)
    }
    val liveCity by restaurantRepository.service.getCityDocumentFlow(resolvedCity.id).collectAsState(initial = null)
    val effectiveCity = liveCity ?: resolvedCity

    val availabilityResult = remember(effectiveCity, platformSettings, appDesignerSettings) {
        ServiceAvailability.availability(
            city = effectiveCity,
            service = "flight",
            platformSettings = platformSettings,
            deliveryRates = null,
            appDesignerSettings = appDesignerSettings
        )
    }

    var uiState by remember {
        mutableStateOf(
            FlightSearchUiState(
                recentAirportCodes = preferenceRepository.getRecentAirportCodes()
            )
        )
    }

    FlightScreenStateful(
        uiState = uiState,
        isServiceEnabled = availabilityResult.ok,
        unavailableReason = availabilityResult.reason,
        onStateChange = { uiState = it },
        onSaveRecentAirport = { code ->
            preferenceRepository.addRecentAirportCode(code)
            uiState = uiState.copy(recentAirportCodes = preferenceRepository.getRecentAirportCodes())
        },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun FlightScreenStateful(
    uiState: FlightSearchUiState,
    isServiceEnabled: Boolean = true,
    unavailableReason: String = "",
    onStateChange: (FlightSearchUiState) -> Unit,
    onSaveRecentAirport: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeAirportPicker by remember { mutableStateOf<ActiveAirportPicker?>(null) }
    var activeDatePicker by remember { mutableStateOf<ActiveDatePicker?>(null) }
    var showTravelersSheet by remember { mutableStateOf(false) }

    if (uiState.showComingSoonResult) {
        BackHandler {
            onStateChange(uiState.copy(showComingSoonResult = false))
        }
        FlightComingSoonContent(
            uiState = uiState,
            onBack = { onStateChange(uiState.copy(showComingSoonResult = false)) },
            modifier = modifier
        )
        return
    }

    FlightSearchFormContent(
        uiState = uiState,
        isServiceEnabled = isServiceEnabled,
        unavailableReason = unavailableReason,
        onBack = onBack,
        onTripTypeSelected = { tripType ->
            onStateChange(uiState.copy(tripType = tripType, validationError = null))
        },
        onOpenOrigin = { activeAirportPicker = ActiveAirportPicker.MainOrigin },
        onOpenDestination = { activeAirportPicker = ActiveAirportPicker.MainDestination },
        onSwapMain = {
            onStateChange(
                uiState.copy(
                    origin = uiState.destination,
                    destination = uiState.origin,
                    validationError = null
                )
            )
        },
        onOpenDepartureDate = { activeDatePicker = ActiveDatePicker.Departure },
        onOpenReturnDate = { activeDatePicker = ActiveDatePicker.Return },
        onOpenLegOrigin = { idx -> activeAirportPicker = ActiveAirportPicker.LegOrigin(idx) },
        onOpenLegDestination = { idx -> activeAirportPicker = ActiveAirportPicker.LegDestination(idx) },
        onSwapLeg = { idx ->
            val updated = uiState.multiCityLegs.toMutableList()
            val leg = updated.getOrNull(idx)
            if (leg != null) {
                updated[idx] = leg.copy(origin = leg.destination, destination = leg.origin)
                onStateChange(uiState.copy(multiCityLegs = updated, validationError = null))
            }
        },
        onOpenLegDate = { idx -> activeDatePicker = ActiveDatePicker.MultiLeg(idx) },
        onAddLeg = {
            if (uiState.multiCityLegs.size < FlightFormValidator.MAX_MULTI_CITY_LEGS) {
                val lastLeg = uiState.multiCityLegs.lastOrNull()
                val nextIndex = uiState.multiCityLegs.size + 1
                val newLeg = FlightLeg(
                    id = "leg_${nextIndex}_${System.nanoTime()}",
                    origin = lastLeg?.destination,
                    destination = null,
                    departureDateMillis = null
                )
                onStateChange(
                    uiState.copy(
                        multiCityLegs = uiState.multiCityLegs + newLeg,
                        validationError = null
                    )
                )
            }
        },
        onRemoveLeg = { idx ->
            if (uiState.multiCityLegs.size > 1) {
                val updated = uiState.multiCityLegs.toMutableList().apply { removeAt(idx) }
                onStateChange(uiState.copy(multiCityLegs = updated, validationError = null))
            }
        },
        onOpenTravelers = { showTravelersSheet = true },
        onSelectCabinClass = { cabin ->
            onStateChange(uiState.copy(cabinClass = cabin, validationError = null))
        },
        onSearchFlight = {
            val error = FlightFormValidator.validate(uiState)
            if (error != null) {
                onStateChange(uiState.copy(validationError = error))
            } else {
                onStateChange(uiState.copy(validationError = null, showComingSoonResult = true))
            }
        },
        modifier = modifier
    )

    activeAirportPicker?.let { picker ->
        val (title, blockedCode) = when (picker) {
            is ActiveAirportPicker.MainOrigin -> "Select Origin" to uiState.destination?.code
            is ActiveAirportPicker.MainDestination -> "Select Destination" to uiState.origin?.code
            is ActiveAirportPicker.LegOrigin -> "Select Origin (Flight ${picker.index + 1})" to
                uiState.multiCityLegs.getOrNull(picker.index)?.destination?.code
            is ActiveAirportPicker.LegDestination -> "Select Destination (Flight ${picker.index + 1})" to
                uiState.multiCityLegs.getOrNull(picker.index)?.origin?.code
        }
        AirportSearchSheet(
            title = title,
            blockedAirportCode = blockedCode,
            recentAirportCodes = uiState.recentAirportCodes,
            onDismiss = { activeAirportPicker = null },
            onSelectAirport = { airport ->
                onSaveRecentAirport(airport.code)
                val updatedState = when (picker) {
                    is ActiveAirportPicker.MainOrigin -> uiState.copy(origin = airport, validationError = null)
                    is ActiveAirportPicker.MainDestination -> uiState.copy(destination = airport, validationError = null)
                    is ActiveAirportPicker.LegOrigin -> {
                        val legs = uiState.multiCityLegs.toMutableList()
                        legs.getOrNull(picker.index)?.let { legs[picker.index] = it.copy(origin = airport) }
                        uiState.copy(multiCityLegs = legs, validationError = null)
                    }
                    is ActiveAirportPicker.LegDestination -> {
                        val legs = uiState.multiCityLegs.toMutableList()
                        legs.getOrNull(picker.index)?.let { legs[picker.index] = it.copy(destination = airport) }
                        uiState.copy(multiCityLegs = legs, validationError = null)
                    }
                }
                onStateChange(updatedState)
                activeAirportPicker = null
            }
        )
    }

    activeDatePicker?.let { picker ->
        val todayMillis = FlightDateUtil.localTodayUtcMidnightMillis()
        val dayMs = 86_400_000L
        val (initialMillis, minMillis) = when (picker) {
            is ActiveDatePicker.Departure -> uiState.departureDateMillis to todayMillis
            is ActiveDatePicker.Return -> {
                val dep = uiState.departureDateMillis?.let { FlightDateUtil.normalizeToUtcMidnight(it) }
                val minRet = if (dep != null && dep >= todayMillis) dep + dayMs else todayMillis + dayMs
                uiState.returnDateMillis to minRet
            }
            is ActiveDatePicker.MultiLeg -> {
                uiState.multiCityLegs.getOrNull(picker.index)?.departureDateMillis to todayMillis
            }
        }
        FlightDatePickerDialog(
            initialSelectedDateMillis = initialMillis,
            minSelectableUtcMillis = minMillis,
            onDismiss = { activeDatePicker = null },
            onConfirmDate = { selectedUtcMillis ->
                val updatedState = when (picker) {
                    is ActiveDatePicker.Departure -> {
                        val clearedReturn = if (uiState.returnDateMillis != null &&
                            FlightDateUtil.normalizeToUtcMidnight(uiState.returnDateMillis) <= selectedUtcMillis
                        ) {
                            null
                        } else {
                            uiState.returnDateMillis
                        }
                        uiState.copy(
                            departureDateMillis = selectedUtcMillis,
                            returnDateMillis = clearedReturn,
                            validationError = null
                        )
                    }
                    is ActiveDatePicker.Return -> uiState.copy(
                        returnDateMillis = selectedUtcMillis,
                        validationError = null
                    )
                    is ActiveDatePicker.MultiLeg -> {
                        val legs = uiState.multiCityLegs.toMutableList()
                        legs.getOrNull(picker.index)?.let {
                            legs[picker.index] = it.copy(departureDateMillis = selectedUtcMillis)
                        }
                        uiState.copy(multiCityLegs = legs, validationError = null)
                    }
                }
                onStateChange(updatedState)
                activeDatePicker = null
            }
        )
    }

    if (showTravelersSheet) {
        TravelersBottomSheet(
            initialTravelers = uiState.travelers,
            onDismiss = { showTravelersSheet = false },
            onConfirm = { newTravelers ->
                onStateChange(uiState.copy(travelers = newTravelers, validationError = null))
                showTravelersSheet = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlightSearchFormContent(
    uiState: FlightSearchUiState,
    isServiceEnabled: Boolean = true,
    unavailableReason: String = "",
    onBack: () -> Unit,
    onTripTypeSelected: (TripType) -> Unit,
    onOpenOrigin: () -> Unit,
    onOpenDestination: () -> Unit,
    onSwapMain: () -> Unit,
    onOpenDepartureDate: () -> Unit,
    onOpenReturnDate: () -> Unit,
    onOpenLegOrigin: (Int) -> Unit,
    onOpenLegDestination: (Int) -> Unit,
    onSwapLeg: (Int) -> Unit,
    onOpenLegDate: (Int) -> Unit,
    onAddLeg: () -> Unit,
    onRemoveLeg: (Int) -> Unit,
    onOpenTravelers: () -> Unit,
    onSelectCabinClass: (CabinClass) -> Unit,
    onSearchFlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                color = YellowPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("flight_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Flight",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.FlightTakeoff,
                        contentDescription = null,
                        tint = TextOnYellow,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(22.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 36.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Yellow curved backdrop band with segmented trip type cards
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(YellowPrimary)
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripType.entries.forEach { type ->
                            val isSelected = uiState.tripType == type
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else YellowPrimary.copy(alpha = 0.65f),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) TextOnYellow else TextOnYellow.copy(alpha = 0.25f)
                                ),
                                shadowElevation = if (isSelected) 4.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onTripTypeSelected(type) }
                                    .testTag("flight_trip_${type.name.lowercase()}")
                            ) {
                                Text(
                                    text = type.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else TextOnYellow,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isServiceEnabled) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ErrorRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("flight_unavailable_banner")
                    ) {
                        Text(
                            text = unavailableReason.ifBlank { "Delivio Flight is currently unavailable." },
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Rounded white form card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (uiState.tripType != TripType.MULTI_CITY) {
                            // Origin & Destination with round swap button centered between fields
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FlightInputBox(
                                        label = "From",
                                        value = uiState.origin?.displayTitle ?: "Select Origin",
                                        subtext = uiState.origin?.displaySubtitle ?: "Choose departure airport",
                                        icon = Icons.Default.FlightTakeoff,
                                        isPlaceholder = uiState.origin == null,
                                        onClick = onOpenOrigin,
                                        modifier = Modifier.testTag("flight_origin_field")
                                    )
                                    FlightInputBox(
                                        label = "To",
                                        value = uiState.destination?.displayTitle ?: "Select Destination",
                                        subtext = uiState.destination?.displaySubtitle ?: "Choose arrival airport",
                                        icon = Icons.Default.FlightLand,
                                        isPlaceholder = uiState.destination == null,
                                        onClick = onOpenDestination,
                                        modifier = Modifier.testTag("flight_destination_field")
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = YellowPrimary,
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 14.dp)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onSwapMain)
                                        .testTag("flight_swap_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = "Swap Origin and Destination",
                                            tint = TextOnYellow,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            // Departure + Return (hidden for One Way)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FlightInputBox(
                                    label = "Departure",
                                    value = FlightDateUtil.formatShortDate(uiState.departureDateMillis),
                                    subtext = null,
                                    icon = Icons.Default.CalendarMonth,
                                    isPlaceholder = uiState.departureDateMillis == null,
                                    onClick = onOpenDepartureDate,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("flight_departure_date_field")
                                )

                                if (uiState.tripType == TripType.ROUND_TRIP) {
                                    FlightInputBox(
                                        label = "Return",
                                        value = FlightDateUtil.formatShortDate(uiState.returnDateMillis),
                                        subtext = null,
                                        icon = Icons.Default.CalendarMonth,
                                        isPlaceholder = uiState.returnDateMillis == null,
                                        onClick = onOpenReturnDate,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("flight_return_date_field")
                                    )
                                }
                            }
                        } else {
                            // Multi City legs
                            uiState.multiCityLegs.forEachIndexed { index, leg ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                                text = "Flight ${index + 1}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (uiState.multiCityLegs.size > 1) {
                                                IconButton(
                                                    onClick = { onRemoveLeg(index) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .size(40.dp)
                                                        .testTag("flight_remove_leg_$index")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Flight ${index + 1}",
                                                        tint = ErrorRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FlightInputBox(
                                                    label = "From",
                                                    value = leg.origin?.displayTitle ?: "Select Origin",
                                                    subtext = leg.origin?.displaySubtitle,
                                                    icon = Icons.Default.FlightTakeoff,
                                                    isPlaceholder = leg.origin == null,
                                                    onClick = { onOpenLegOrigin(index) },
                                                    modifier = Modifier.testTag("flight_leg_origin_$index")
                                                )
                                                FlightInputBox(
                                                    label = "To",
                                                    value = leg.destination?.displayTitle ?: "Select Destination",
                                                    subtext = leg.destination?.displaySubtitle,
                                                    icon = Icons.Default.FlightLand,
                                                    isPlaceholder = leg.destination == null,
                                                    onClick = { onOpenLegDestination(index) },
                                                    modifier = Modifier.testTag("flight_leg_destination_$index")
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = YellowPrimary,
                                                shadowElevation = 2.dp,
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 10.dp)
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .clickable { onSwapLeg(index) }
                                                    .testTag("flight_leg_swap_$index")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.SwapVert,
                                                        contentDescription = "Swap airports for Flight ${index + 1}",
                                                        tint = TextOnYellow,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        FlightInputBox(
                                            label = "Departure Date",
                                            value = FlightDateUtil.formatShortDate(leg.departureDateMillis),
                                            subtext = null,
                                            icon = Icons.Default.CalendarMonth,
                                            isPlaceholder = leg.departureDateMillis == null,
                                            onClick = { onOpenLegDate(index) },
                                            modifier = Modifier.testTag("flight_leg_date_$index")
                                        )
                                    }
                                }
                            }

                            if (uiState.multiCityLegs.size < FlightFormValidator.MAX_MULTI_CITY_LEGS) {
                                OutlinedButton(
                                    onClick = onAddLeg,
                                    border = BorderStroke(1.dp, YellowPrimary),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("flight_add_leg_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add another flight",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Travelers field (default "1 Adult")
                        FlightInputBox(
                            label = "Travelers",
                            value = uiState.travelers.formatSummary(),
                            subtext = "Max 9 travelers • Infants up to adults",
                            icon = Icons.Default.Person,
                            isPlaceholder = false,
                            onClick = onOpenTravelers,
                            modifier = Modifier.testTag("flight_travelers_field")
                        )

                        // Cabin Class selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Class",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CabinClass.entries.forEach { cabin ->
                                    val selected = uiState.cabinClass == cabin
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        border = if (selected) BorderStroke(1.5.dp, TextOnYellow.copy(alpha = 0.35f)) else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSelectCabinClass(cabin) }
                                            .testTag("flight_class_${cabin.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = cabin.label,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (selected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.validationError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ErrorRed.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("flight_validation_error")
                            ) {
                                Text(
                                    text = uiState.validationError,
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onSearchFlight,
                            enabled = isServiceEnabled,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YellowPrimary,
                                contentColor = TextOnYellow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("flight_search_button")
                        ) {
                            Text(
                                text = "Search Flight",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightInputBox(
    label: String,
    value: String,
    subtext: String?,
    icon: ImageVector,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = YellowPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = if (isPlaceholder) FontWeight.Medium else FontWeight.Bold,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtext.isNullOrBlank()) {
                    Text(
                        text = subtext,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FlightComingSoonContent(
    uiState: FlightSearchUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                color = YellowPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("flight_coming_soon_back_top")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Flight",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = YellowPrimary.copy(alpha = 0.16f)),
                    border = BorderStroke(1.5.dp, YellowPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.Top,
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
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TextOnYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Coming soon",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Flight booking is coming soon. Payments and ticketing are not available yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("flight_coming_soon_message")
                            )
                        }
                    }
                }
            }

            // Summary of the search
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("flight_search_summary_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Search Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SummaryRow("Trip Type", uiState.tripType.label)
                        if (uiState.tripType == TripType.MULTI_CITY) {
                            uiState.multiCityLegs.forEachIndexed { idx, leg ->
                                val route = "${leg.origin?.code ?: "--"} → ${leg.destination?.code ?: "--"}"
                                SummaryRow(
                                    label = "Flight ${idx + 1}",
                                    value = "$route • ${FlightDateUtil.formatShortDate(leg.departureDateMillis)}"
                                )
                            }
                        } else {
                            SummaryRow(
                                label = "Route",
                                value = "${uiState.origin?.code ?: "--"} (${uiState.origin?.city ?: ""}) → ${uiState.destination?.code ?: "--"} (${uiState.destination?.city ?: ""})"
                            )
                            SummaryRow(
                                label = "Departure",
                                value = FlightDateUtil.formatDate(uiState.departureDateMillis)
                            )
                            if (uiState.tripType == TripType.ROUND_TRIP) {
                                SummaryRow(
                                    label = "Return",
                                    value = FlightDateUtil.formatDate(uiState.returnDateMillis)
                                )
                            }
                        }
                        SummaryRow("Travelers", uiState.travelers.formatSummary())
                        SummaryRow("Class", uiState.cabinClass.label)
                    }
                }
            }

            // Coming soon rows
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ComingSoonFeatureRow(Icons.Default.CreditCard, "Payment")
                        HorizontalDivider()
                        ComingSoonFeatureRow(Icons.Default.Person, "Passenger details")
                        HorizontalDivider()
                        ComingSoonFeatureRow(Icons.Default.AirlineSeatReclineNormal, "Seat selection")
                        HorizontalDivider()
                        ComingSoonFeatureRow(Icons.Default.Luggage, "Baggage")
                    }
                }
            }

            item {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("flight_coming_soon_back_button")
                ) {
                    Text("Back", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComingSoonFeatureRow(
    icon: ImageVector,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("coming_soon_row_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BadgeOrange.copy(alpha = 0.14f)
        ) {
            Text(
                text = "Coming soon",
                color = BadgeOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
