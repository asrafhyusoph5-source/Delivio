package com.example.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Banner
import com.example.data.model.City
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import com.example.data.model.ServiceType
import com.example.data.repository.OrderRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.DelivioAsyncImage
import com.example.ui.components.DelivioIcon
import com.example.ui.components.OfflineBanner
import com.example.ui.components.RestaurantCard
import com.example.ui.components.RestaurantCardShimmer
import com.example.ui.components.SetManualAddressDialog
import com.example.ui.components.rememberIsOnline
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.DelivioIconConstants
import com.example.viewmodel.AppTab
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.MainViewModel

private data class LauncherItem(
    val serviceType: ServiceType,
    val key: String,
    val title: String,
    val iconFile: String,
    val fallbackIcon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    restaurantRepository: RestaurantRepository,
    orderRepository: OrderRepository,
    mainViewModel: MainViewModel,
    cartViewModel: CartViewModel? = null,
    authViewModel: AuthViewModel? = null,
    onNavigateToRestaurant: (String) -> Unit,
    onNavigateToServiceShops: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToMapPicker: () -> Unit = {},
    onNavigateToInbox: () -> Unit = { mainViewModel.selectTab(AppTab.INBOX) },
    modifier: Modifier = Modifier
) {
    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val mainCategories by restaurantRepository.mainCategories.collectAsState()
    val isShopsLoading by restaurantRepository.isLoading.collectAsState()
    val isOnline by rememberIsOnline()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val savedAddresses by mainViewModel.savedAddresses.collectAsState()

    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val banners by restaurantRepository.service.getBannersFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())
    val dispatchSettings by restaurantRepository.service.getDispatchSettingsFlow().collectAsState(initial = com.example.data.model.DispatchSettings())

    // Default: none selected - saved across navigation
    var selectedService by rememberSaveable { mutableStateOf<ServiceType?>(null) }
    var selectedCategory by remember { mutableStateOf<com.example.data.model.MainCategory?>(null) }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var showAddressSheet by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
    val userFavorites by restaurantRepository.getFavoritesFlow(currentUid).collectAsState(initial = emptySet())

    // Five evenly spaced launcher services
    val launcherServices = remember {
        listOf(
            LauncherItem(ServiceType.FOOD, "food", "Food", DelivioIconConstants.SVC_FOOD, DelivioIconConstants.getServiceFallback("food")),
            LauncherItem(ServiceType.CONVENIENCE, "convenience", "Convenience", DelivioIconConstants.SVC_CONVENIENCE, DelivioIconConstants.getServiceFallback("convenience")),
            LauncherItem(ServiceType.PARCEL, "parcel", "Parcel", DelivioIconConstants.SVC_PARCEL, DelivioIconConstants.getServiceFallback("parcel")),
            LauncherItem(ServiceType.PABILI, "pabili", "Pabili", DelivioIconConstants.SVC_PABILI, DelivioIconConstants.getServiceFallback("pabili")),
            LauncherItem(ServiceType.TAXI, "taxi", "Taxi", DelivioIconConstants.SVC_TAXI, DelivioIconConstants.getServiceFallback("taxi"))
        )
    }

    val lastCityId by mainViewModel.lastCityId.collectAsState()
    val diagnostic by restaurantRepository.diagnostic.collectAsState()

    // Detect city:
    // The city is the one from the selected delivery address (match by coordinates), otherwise the last used city, otherwise the first active city.
    val detectedCity = remember(currentAddress, lastCityId, cities) {
        val coordCity = if (currentAddress.id != "addr_none" && (currentAddress.lat != 0.0 || currentAddress.lng != 0.0)) {
            val matches = cities.mapNotNull { city ->
                val dist = com.example.data.api.OsrmRoutingService.calculateHaversineDistanceKm(
                    currentAddress.lat, currentAddress.lng, city.lat, city.lng
                )
                val radius = if (city.radiusKm > 0.0) city.radiusKm else 25.0
                if (dist <= radius) Pair(city, dist) else null
            }.sortedBy { it.second }
            matches.firstOrNull()?.first
        } else null

        val addrCity = coordCity ?: if (currentAddress.id != "addr_none" && currentAddress.fullAddress.isNotBlank()) {
            cities.firstOrNull { c ->
                currentAddress.fullAddress.contains(c.name, ignoreCase = true) ||
                currentAddress.title.contains(c.name, ignoreCase = true) ||
                currentAddress.fullAddress.contains(c.id, ignoreCase = true)
            }
        } else null

        val lastCity = if (addrCity == null && !lastCityId.isNullOrBlank()) {
            cities.firstOrNull { it.id.equals(lastCityId, ignoreCase = true) }
        } else null

        val firstActive = cities.firstOrNull { it.status.trim().lowercase(java.util.Locale.ROOT) == "active" }
            ?: cities.firstOrNull()
            ?: City(id = "puerto-princesa", name = "Puerto Princesa", province = "Palawan", status = "active")

        addrCity ?: lastCity ?: firstActive
    }

    // Listen live to cities/{id}, settings/platform, and deliveryRates
    val liveCity by restaurantRepository.service.getCityDocumentFlow(detectedCity.id).collectAsState(initial = null)
    val currentCity = liveCity ?: detectedCity
    val deliveryRates by restaurantRepository.service.getDeliveryRatesForShopFlow(
        shopCityId = currentCity.id,
        customerCityId = currentCity.id
    ).collectAsState(initial = com.example.data.model.DeliveryRates())

    // serviceOn(service, cityId) is true when settings/platform.services[service] is not false AND cities/{cityId}.services[service] is not false (missing value means on)
    val isServiceOn = remember(currentCity, platformSettings) {
        { serviceKey: String ->
            val platformOn = platformSettings.services[serviceKey] != false
            val cityOn = currentCity.services[serviceKey] != false
            platformOn && cityOn
        }
    }

    val activeLauncherServices = remember(launcherServices, currentCity, platformSettings) {
        launcherServices.filter { isServiceOn(it.key) }
    }

    LaunchedEffect(currentCity.services, platformSettings.services) {
        val currentKey = when (selectedService) {
            ServiceType.FOOD -> "food"
            ServiceType.CONVENIENCE -> "convenience"
            ServiceType.PARCEL -> "parcel"
            ServiceType.PABILI -> "pabili"
            ServiceType.TAXI -> "taxi"
            else -> null
        }
        if (currentKey != null && !isServiceOn(currentKey)) {
            selectedService = null
            selectedCategory = null
        }
    }

    LaunchedEffect(currentCity.id) {
        restaurantRepository.loadCityMerchants(currentCity.id)
        mainViewModel.setLastCityId(currentCity.id)
    }

    val (customerLat, customerLng) = remember(currentAddress, currentCity) {
        com.example.util.BranchGroupingUtil.resolveCustomerCoordinates(currentAddress, currentCity)
    }

    LaunchedEffect(currentCity.id, customerLat, customerLng) {
        cartViewModel?.updateCustomerDeliveryContext(currentCity.id, customerLat, customerLng)
    }

    // Filter restaurants based on eligibility (active status, city + service on, inside radius),
    // selected category (via unified MainCategoriesUtil), selected service, and nearest branch per chain.
    val filteredBranches = remember(
        selectedCategory,
        selectedService,
        showOnlyFavorites,
        userFavorites,
        allRestaurants,
        cities,
        currentCity,
        platformSettings,
        customerLat,
        customerLng,
        dispatchSettings.searchRadiusKm,
        mainCategories
    ) {
        val cityList = cities.ifEmpty { listOf(currentCity) }
        val eligibleGrouped = com.example.util.BranchGroupingUtil.getEligibleGroupedShops(
            allShops = allRestaurants,
            cities = cityList,
            platformSettings = platformSettings,
            defaultRadiusKm = dispatchSettings.searchRadiusKm,
            customerLat = customerLat,
            customerLng = customerLng,
            fallbackCity = currentCity,
            logExcluded = true
        )

        val serviceFiltered = when (selectedService) {
            ServiceType.FOOD -> eligibleGrouped.filter { !it.restaurant.isConvenience && !it.restaurant.type.equals("convenience", ignoreCase = true) }
            ServiceType.CONVENIENCE -> eligibleGrouped.filter { it.restaurant.isConvenience || it.restaurant.type.equals("convenience", ignoreCase = true) }
            else -> eligibleGrouped
        }

        val categoryFiltered = if (selectedCategory == null) {
            serviceFiltered
        } else {
            serviceFiltered.filter { branch ->
                com.example.util.MainCategoriesUtil.shopMatchesCategory(branch.restaurant, selectedCategory, mainCategories)
            }
        }

        if (showOnlyFavorites) {
            categoryFiltered.filter { userFavorites.contains(it.restaurant.id) }
        } else {
            categoryFiltered
        }
    }
    val filteredRestaurants = remember(filteredBranches) { filteredBranches.map { it.restaurant } }

    val fastDeliveryBranches = remember(filteredBranches, platformSettings.timezone, platformSettings.holidays) {
        filteredBranches.filter { branch ->
            com.example.util.BusinessHoursUtil.effectiveOpen(
                shop = branch.restaurant,
                timezone = platformSettings.timezone,
                platformHolidays = platformSettings.holidays
            ) && com.example.util.EtaCalculator.isFastDelivery(
                com.example.util.EtaCalculator.estimateMinutes(branch.restaurant.prepTimeMinutes, branch.distanceKm)
            )
        }
    }

    val dealBranches = remember(filteredBranches, platformSettings.timezone, platformSettings.holidays) {
        filteredBranches.filter { branch ->
            com.example.util.BusinessHoursUtil.effectiveOpen(
                shop = branch.restaurant,
                timezone = platformSettings.timezone,
                platformHolidays = platformSettings.holidays
            ) && branch.restaurant.dealDescription != null
        }
    }

    // Set Manual Address Dialog
    if (showManualAddressDialog) {
        SetManualAddressDialog(
            initialTitle = if (savedAddresses.none { it.title.equals("Home", ignoreCase = true) }) "Home"
                           else if (savedAddresses.none { it.title.equals("Work", ignoreCase = true) }) "Work"
                           else "Location",
            initialAddress = if (currentAddress.id != "addr_none") currentAddress.fullAddress else "",
            initialDetails = currentAddress.details,
            initialLat = currentAddress.lat,
            initialLng = currentAddress.lng,
            isEditing = false,
            onDismiss = { showManualAddressDialog = false },
            onSaveAddress = { title, fullAddress, details, setAsActive, lat, lng ->
                mainViewModel.addManualAddress(title, fullAddress, details, setAsActive, lat, lng)
            }
        )
    }

    // Address Selection Bottom Sheet
    if (showAddressSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddressSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delivery Address (${if (savedAddresses.isEmpty()) "None" else "${savedAddresses.size}/3"})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            if (savedAddresses.size >= 3) {
                                mainViewModel.showSnackbar("Maximum of 3 saved addresses reached. Please edit or delete one in your Profile.")
                            } else {
                                showAddressSheet = false
                                showManualAddressDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (savedAddresses.size >= 3) MaterialTheme.colorScheme.surfaceVariant else YellowPrimary,
                            contentColor = if (savedAddresses.size >= 3) MaterialTheme.colorScheme.outline else TextOnYellow
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (savedAddresses.size >= 3) "Max (3/3)" else "+ Set Manually",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (savedAddresses.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "None",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No saved addresses yet. Tap '+ Set Manually' to add your location.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    savedAddresses.forEach { address ->
                        val isSelected = address.id == currentAddress.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    mainViewModel.setAddress(address)
                                    showAddressSheet = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    mainViewModel.setAddress(address)
                                    showAddressSheet = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = address.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = address.fullAddress,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (address.details.isNotBlank()) {
                                    Text(
                                        text = address.details,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Parcel, Pabili, and Taxi open their own screens (back returns to Home) and never filter shops
    // Hidden completely if service is off
    if (selectedService == ServiceType.PARCEL && isServiceOn("parcel")) {
        BackHandler { selectedService = null }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ServiceScreenHeader(
                title = "Parcel Delivery",
                onBack = { selectedService = null }
            )
            ParcelBookingView(
                orderRepository = orderRepository,
                cityId = currentCity.id,
                authViewModel = authViewModel,
                onOrderBooked = {
                    mainViewModel.showSnackbar("Parcel delivery order booked! Tracking rider now.")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (selectedService == ServiceType.PABILI && isServiceOn("pabili")) {
        BackHandler { selectedService = null }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ServiceScreenHeader(
                title = "Pabili Errand",
                onBack = { selectedService = null }
            )
            PabiliBookingView(
                orderRepository = orderRepository,
                cityId = currentCity.id,
                authViewModel = authViewModel,
                onOrderBooked = {
                    mainViewModel.showSnackbar("Pabili errand booked! Rider is on the way to the vendor.")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (selectedService == ServiceType.TAXI && isServiceOn("taxi")) {
        BackHandler { selectedService = null }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ServiceScreenHeader(
                title = "Taxi Ride",
                onBack = { selectedService = null }
            )
            TaxiBookingView(
                orderRepository = orderRepository,
                cityId = currentCity.id,
                authViewModel = authViewModel,
                mainViewModel = mainViewModel,
                onNavigateToMapPicker = onNavigateToMapPicker,
                onOrderBooked = {
                    mainViewModel.showSnackbar("Taxi ride booked! Searching for nearby driver.")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // STANDARD HOME VIEW (None selected, Food, or Convenience)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top Offline Banner
        item {
            OfflineBanner(
                isOnline = isOnline,
                onRetry = { restaurantRepository.reload() }
            )
        }

        // TOP APP BAR: Brand, Address Switcher, Notification Bell
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Deliver to dropdown
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showAddressSheet = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .testTag("delivery_address_selector")
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
                                    contentDescription = "Location",
                                    tint = YellowPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Deliver to • ${currentCity.name}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = currentAddress.title.ifEmpty { currentAddress.fullAddress },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp)
                                )
                            }
                        }

                        // Right: Notification Bell Icon (Opens Inbox)
                        IconButton(
                            onClick = { onNavigateToInbox() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .testTag("home_notification_bell")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Inbox",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Trigger Bar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onNavigateToSearch() }
                            .testTag("home_search_bar")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Search dishes, restaurants, groceries in ${currentCity.name}...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Chips: All & Favorites
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllShopsSelected = !showOnlyFavorites && selectedService == null && selectedCategory == null
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAllShopsSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isAllShopsSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    showOnlyFavorites = false
                                    selectedService = null
                                    selectedCategory = null
                                }
                                .testTag("home_chip_all")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "All Shops",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (showOnlyFavorites) Color(0xFFFFE8EC) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (showOnlyFavorites) Color(0xFFFF3B5C) else MaterialTheme.colorScheme.onSurface,
                            border = if (showOnlyFavorites) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF3B5C)) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showOnlyFavorites = !showOnlyFavorites }
                                .testTag("home_chip_favorites")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (showOnlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (showOnlyFavorites) Color(0xFFFF3B5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Favorites" + if (userFavorites.isNotEmpty()) " (${userFavorites.size})" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // HOME SERVICE ROW: Evenly spaced large image icons (56dp), hides disabled services completely so row re-spaces
        if (activeLauncherServices.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        activeLauncherServices.forEach { item ->
                            val isSelected = selectedService == item.serviceType

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        when (item.serviceType) {
                                            ServiceType.FOOD -> {
                                                onNavigateToServiceShops("food")
                                            }
                                            ServiceType.CONVENIENCE -> {
                                                onNavigateToServiceShops("convenience")
                                            }
                                            ServiceType.PARCEL, ServiceType.PABILI, ServiceType.TAXI -> {
                                                selectedService = item.serviceType
                                            }
                                            else -> {}
                                        }
                                    }
                                    .testTag("service_launcher_${item.key}")
                            ) {
                                // 56dp image icon with yellow rounded highlight behind icon when selected
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isSelected) YellowPrimary else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DelivioIcon(
                                        iconFileName = item.iconFile,
                                        contentDescription = item.title,
                                        size = 56.dp,
                                        fallbackIcon = item.fallbackIcon
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // PROMO BANNERS FROM FIRESTORE
        if (banners.isNotEmpty()) {
            item {
                DelivioPromoBannerCarousel(
                    banners = banners,
                    onBannerClick = { banner ->
                        if (!banner.link.merchantId.isNullOrEmpty()) {
                            onNavigateToRestaurant(banner.link.merchantId)
                        } else if (banner.link.category == "convenience") {
                            onNavigateToServiceShops("convenience")
                        } else if (banner.link.category == "food") {
                            onNavigateToServiceShops("food")
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // CUISINES & CATEGORIES (Live from settings/mainCategories, cached; two rows: Food with All first, Convenience with All first)
        val hasFood = isServiceOn("food")
        val hasConv = isServiceOn("convenience")

        if (hasFood || hasConv) {
            item {
                val activeFoodCats = remember(mainCategories) {
                    com.example.util.MainCategoriesUtil.activeForType(mainCategories, "food")
                }
                val activeConvCats = remember(mainCategories) {
                    com.example.util.MainCategoriesUtil.activeForType(mainCategories, "convenience")
                }
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        text = "Cuisines & Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )

                    // Row 1: Food (with fixed "All" chip first)
                    if (hasFood) {
                        Text(
                            text = "Food",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Fixed Food "All" chip first
                            val isFoodAllSelected = !showOnlyFavorites && selectedService == ServiceType.FOOD && selectedCategory == null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showOnlyFavorites = false
                                        selectedService = ServiceType.FOOD
                                        selectedCategory = null
                                    }
                                    .testTag("food_chip_All")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isFoodAllSelected) {
                                                Modifier
                                                    .border(2.5.dp, YellowPrimary, CircleShape)
                                                    .background(YellowPrimary.copy(alpha = 0.15f))
                                            } else {
                                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DelivioIcon(
                                        iconFileName = DelivioIconConstants.CAT_ALL,
                                        contentDescription = "All Food",
                                        size = 48.dp,
                                        fallbackIcon = DelivioIconConstants.getCategoryFallback("All")
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "All",
                                    fontSize = 11.sp,
                                    fontWeight = if (isFoodAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isFoodAllSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 68.dp)
                                )
                            }

                            activeFoodCats.forEach { cat ->
                                val isSelected = !showOnlyFavorites &&
                                    selectedService == ServiceType.FOOD &&
                                    selectedCategory?.let {
                                        it.id.equals(cat.id, ignoreCase = true) && it.type.equals("food", ignoreCase = true)
                                    } == true

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            showOnlyFavorites = false
                                            selectedService = ServiceType.FOOD
                                            selectedCategory = if (isSelected) null else cat
                                        }
                                        .testTag("food_chip_${cat.name}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .border(2.5.dp, YellowPrimary, CircleShape)
                                                        .background(YellowPrimary.copy(alpha = 0.15f))
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.example.ui.components.CategoryIconImage(
                                            imageUrl = cat.imageUrl,
                                            categoryName = cat.name,
                                            size = 48.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = cat.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 68.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Convenience (with fixed "All" chip first)
                    if (hasConv) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Convenience",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Fixed Convenience "All" chip first
                            val isConvAllSelected = !showOnlyFavorites && selectedService == ServiceType.CONVENIENCE && selectedCategory == null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showOnlyFavorites = false
                                        selectedService = ServiceType.CONVENIENCE
                                        selectedCategory = null
                                    }
                                    .testTag("conv_chip_All")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isConvAllSelected) {
                                                Modifier
                                                    .border(2.5.dp, YellowPrimary, CircleShape)
                                                    .background(YellowPrimary.copy(alpha = 0.15f))
                                            } else {
                                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DelivioIcon(
                                        iconFileName = DelivioIconConstants.SVC_CONVENIENCE,
                                        contentDescription = "All Convenience",
                                        size = 48.dp,
                                        fallbackIcon = DelivioIconConstants.getServiceFallback("convenience")
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "All",
                                    fontSize = 11.sp,
                                    fontWeight = if (isConvAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isConvAllSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 68.dp)
                                )
                            }

                            activeConvCats.forEach { cat ->
                                val isSelected = !showOnlyFavorites &&
                                    selectedService == ServiceType.CONVENIENCE &&
                                    selectedCategory?.let {
                                        it.id.equals(cat.id, ignoreCase = true) && it.type.equals("convenience", ignoreCase = true)
                                    } == true

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            showOnlyFavorites = false
                                            selectedService = ServiceType.CONVENIENCE
                                            selectedCategory = if (isSelected) null else cat
                                        }
                                        .testTag("conv_chip_${cat.name}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .border(2.5.dp, YellowPrimary, CircleShape)
                                                        .background(YellowPrimary.copy(alpha = 0.15f))
                                                } else {
                                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.example.ui.components.CategoryIconImage(
                                            imageUrl = cat.imageUrl,
                                            categoryName = cat.name,
                                            size = 48.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = cat.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 68.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAST DELIVERY SECTION (only when no category filter and not favorites)
        if (selectedCategory == null && !showOnlyFavorites && fastDeliveryBranches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Fast Delivery (≤25 min)",
                    icon = Icons.Default.ElectricBolt,
                    iconTint = YellowPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(fastDeliveryBranches, key = { it.restaurant.id }) { branch ->
                        val rest = branch.restaurant
                        val isFav = userFavorites.contains(rest.id)
                        val estEta = com.example.util.EtaCalculator.estimateMinutes(rest.prepTimeMinutes, branch.distanceKm)
                        val estFee = com.example.util.CityDeliveryRateResolver.resolveShopDeliveryFee(
                            shop = rest,
                            distanceKm = branch.distanceKm,
                            deliveryRates = deliveryRates,
                            defaultRates = com.example.data.model.DeliveryRates()
                        )
                        RestaurantCard(
                            restaurant = rest,
                            onClick = { onNavigateToRestaurant(rest.id) },
                            timezone = platformSettings.timezone,
                            platformHolidays = platformSettings.holidays,
                            isFavorite = isFav,
                            onToggleFavorite = {
                                if (currentUid.isNotEmpty()) {
                                    scope.launch { restaurantRepository.toggleFavorite(currentUid, rest.id) }
                                }
                            },
                            estimatedEtaMinutes = estEta,
                            estimatedDeliveryFee = estFee,
                            modifier = Modifier.width(260.dp)
                        )
                    }
                }
            }
        }

        // DEALS & PROMOTIONS SECTION
        if (selectedCategory == null && !showOnlyFavorites && dealBranches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Offers & Discounts",
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = BadgeOrange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(dealBranches, key = { it.restaurant.id }) { branch ->
                        val rest = branch.restaurant
                        val isFav = userFavorites.contains(rest.id)
                        val estEta = com.example.util.EtaCalculator.estimateMinutes(rest.prepTimeMinutes, branch.distanceKm)
                        val estFee = com.example.util.CityDeliveryRateResolver.resolveShopDeliveryFee(
                            shop = rest,
                            distanceKm = branch.distanceKm,
                            deliveryRates = deliveryRates,
                            defaultRates = com.example.data.model.DeliveryRates()
                        )
                        RestaurantCard(
                            restaurant = rest,
                            onClick = { onNavigateToRestaurant(rest.id) },
                            timezone = platformSettings.timezone,
                            platformHolidays = platformSettings.holidays,
                            isFavorite = isFav,
                            onToggleFavorite = {
                                if (currentUid.isNotEmpty()) {
                                    scope.launch { restaurantRepository.toggleFavorite(currentUid, rest.id) }
                                }
                            },
                            estimatedEtaMinutes = estEta,
                            estimatedDeliveryFee = estFee,
                            modifier = Modifier.width(260.dp)
                        )
                    }
                }
            }
        }

        // MAIN LIST HEADER: List title shows the category name / service scope and the shop count
        item {
            val selectedCatLabel = selectedCategory?.name
            val sectionTitle = when {
                showOnlyFavorites -> "Favorite Shops (${filteredBranches.size})"
                selectedCatLabel != null -> "$selectedCatLabel (${filteredBranches.size} shops)"
                selectedService == ServiceType.FOOD -> "All Food (${filteredBranches.size})"
                selectedService == ServiceType.CONVENIENCE -> "All Convenience (${filteredBranches.size})"
                else -> "All Shops (${filteredBranches.size})"
            }

            SectionHeader(
                title = sectionTitle,
                icon = if (showOnlyFavorites) Icons.Default.Favorite else if (selectedService == ServiceType.CONVENIENCE) Icons.Default.Storefront else Icons.Default.Star,
                iconTint = if (showOnlyFavorites) Color(0xFFFF3B5C) else YellowPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
            )
        }

        // SHIMMER LOADING, EMPTY RESULT, OR MERCHANTS CARDS
        if (isShopsLoading && filteredBranches.isEmpty()) {
            items(4) {
                RestaurantCardShimmer(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        } else if (filteredBranches.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(YellowPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = YellowPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        val isNothingLeft = (!hasFood && !hasConv) || (activeLauncherServices.isEmpty() && filteredBranches.isEmpty())

                        if (isNothingLeft) {
                            Text(
                                text = "Coming soon in ${currentCity.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Services are currently unavailable in this area.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            val emptyLabel = when {
                                selectedCategory != null -> "No ${selectedCategory!!.name} shops available"
                                selectedService == ServiceType.FOOD -> "No food shops available"
                                selectedService == ServiceType.CONVENIENCE -> "No convenience shops available"
                                else -> "No shops available"
                            }
                            Text(
                                text = emptyLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (selectedCategory != null || selectedService != null || showOnlyFavorites) {
                                Button(
                                    onClick = {
                                        selectedCategory = null
                                        selectedService = null
                                        showOnlyFavorites = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("View All Shops", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "City: ${currentCity.name} (${currentCity.id})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "• Shops loaded: ${diagnostic.totalLoaded}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "• In other cities: ${diagnostic.inOtherCities}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "• Not active: ${diagnostic.notActive}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        restaurantRepository.reload()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("retry_shops_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredBranches, key = { it.restaurant.id }) { branch ->
                val rest = branch.restaurant
                val isFav = userFavorites.contains(rest.id)
                val estEta = com.example.util.EtaCalculator.estimateMinutes(rest.prepTimeMinutes, branch.distanceKm)
                val estFee = com.example.util.CityDeliveryRateResolver.resolveShopDeliveryFee(
                    shop = rest,
                    distanceKm = branch.distanceKm,
                    deliveryRates = deliveryRates,
                    defaultRates = com.example.data.model.DeliveryRates()
                )
                RestaurantCard(
                    restaurant = rest,
                    onClick = { onNavigateToRestaurant(rest.id) },
                    timezone = platformSettings.timezone,
                    platformHolidays = platformSettings.holidays,
                    isFavorite = isFav,
                    onToggleFavorite = {
                        if (currentUid.isNotEmpty()) {
                            scope.launch { restaurantRepository.toggleFavorite(currentUid, rest.id) }
                        }
                    },
                    estimatedEtaMinutes = estEta,
                    estimatedDeliveryFee = estFee,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ServiceScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DelivioPromoBannerCarousel(
    banners: List<Banner>,
    onBannerClick: (Banner) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(130.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(YellowPrimary, Color(0xFFFF9800))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.25f)) {
                            Text("DELIVIO SPECIAL", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Instant Delivery Across Town", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Hot meals, groceries & padala delivered fast", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(42.dp)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
    ) {
        items(banners) { banner ->
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .width(300.dp)
                    .height(130.dp)
                    .clickable { onBannerClick(banner) }
                    .testTag("banner_${banner.id}")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (banner.imageUrl.isNotBlank()) {
                        com.example.ui.components.DelivioAsyncImage(
                            imageUrl = banner.imageUrl,
                            contentDescription = banner.title,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(YellowPrimary, Color(0xFFFF9800))))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YellowPrimary
                        ) {
                            Text(
                                text = "PROMO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextOnYellow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = banner.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


