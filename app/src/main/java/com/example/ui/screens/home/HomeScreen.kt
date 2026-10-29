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
import androidx.compose.runtime.setValue
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
import com.example.ui.components.RestaurantCard
import com.example.ui.components.SetManualAddressDialog
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
    onNavigateToSearch: () -> Unit,
    onNavigateToInbox: () -> Unit = { mainViewModel.selectTab(AppTab.INBOX) },
    modifier: Modifier = Modifier
) {
    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val savedAddresses by mainViewModel.savedAddresses.collectAsState()

    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val banners by restaurantRepository.service.getBannersFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())

    // Default: none selected
    var selectedService by remember { mutableStateOf<ServiceType?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddressSheet by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }

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
    // Selected delivery address's city if there is one, otherwise the last used city, otherwise the first active city in cities.
    // If the city cannot be detected, use the first active city and still show its shops.
    val detectedCity = remember(currentAddress, lastCityId, cities) {
        val addrCity = if (currentAddress.id != "addr_none" && currentAddress.fullAddress.isNotBlank()) {
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

    LaunchedEffect(detectedCity.id) {
        restaurantRepository.loadCityMerchants(detectedCity.id)
        mainViewModel.setLastCityId(detectedCity.id)
    }

    // Filter restaurants based on selected category and selected service
    // "All" shows every shop of both types and never requires cuisineTags. A category chip needs that category in cuisineTags.
    val filteredRestaurants = remember(selectedCategory, selectedService, allRestaurants) {
        val baseList = when (selectedService) {
            ServiceType.FOOD -> allRestaurants.filter { it.type.equals("food", ignoreCase = true) || !it.isConvenience }
            ServiceType.CONVENIENCE -> allRestaurants.filter { it.type.equals("convenience", ignoreCase = true) || it.isConvenience }
            else -> allRestaurants // Default: none selected shows every shop of every type in the city
        }

        if (selectedCategory.equals("All", ignoreCase = true)) {
            baseList
        } else {
            baseList.filter { rest ->
                rest.cuisineTags.any { it.equals(selectedCategory, ignoreCase = true) }
            }
        }
    }

    var categoryProducts by remember { mutableStateOf<List<com.example.data.repository.CategoryProduct>>(emptyList()) }
    var isLoadingCategoryProducts by remember { mutableStateOf(false) }
    var sortByPrice by remember { mutableStateOf(false) }
    var showSwitchStoreDialog by remember { mutableStateOf(false) }
    var switchStorePendingProduct by remember { mutableStateOf<com.example.data.repository.CategoryProduct?>(null) }

    LaunchedEffect(selectedCategory, detectedCity.id, platformSettings.timezone) {
        if (selectedCategory != "All") {
            isLoadingCategoryProducts = true
            categoryProducts = restaurantRepository.getCategoryProducts(
                cityId = detectedCity.id,
                categoryName = selectedCategory,
                timezone = platformSettings.timezone
            )
            isLoadingCategoryProducts = false
        } else {
            categoryProducts = emptyList()
        }
    }

    val sortedProducts = remember(categoryProducts, sortByPrice) {
        if (sortByPrice) categoryProducts.sortedBy { it.price }
        else categoryProducts.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    val fastDeliveryMerchants = remember(allRestaurants, platformSettings.timezone) {
        allRestaurants.filter {
            com.example.util.BusinessHoursUtil.effectiveOpen(it, platformSettings.timezone) && it.etaMinutes <= 25
        }
    }

    val dealMerchants = remember(allRestaurants, platformSettings.timezone) {
        allRestaurants.filter {
            com.example.util.BusinessHoursUtil.effectiveOpen(it, platformSettings.timezone) && it.dealDescription != null
        }
    }

    if (showSwitchStoreDialog && switchStorePendingProduct != null) {
        val prod = switchStorePendingProduct!!
        val currentRestName = cartViewModel?.currentRestaurant?.value?.name ?: "another shop"
        AlertDialog(
            onDismissRequest = {
                showSwitchStoreDialog = false
                switchStorePendingProduct = null
            },
            title = { Text("Switch Shop?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Your cart contains items from $currentRestName. Do you want to discard your current cart and add items from ${prod.merchantName} instead?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        cartViewModel?.clearCart()
                        val menuItem = com.example.data.model.MenuItem(
                            id = prod.id,
                            restaurantId = prod.merchantId,
                            name = prod.name,
                            description = "",
                            price = prod.price,
                            category = prod.categoryName,
                            emoji = "🍽️",
                            imageUrl = prod.imageUrl
                        )
                        cartViewModel?.addItem(menuItem, prod.merchant)
                        mainViewModel.showSnackbar("Added ${prod.name} to cart")
                        showSwitchStoreDialog = false
                        switchStorePendingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    )
                ) {
                    Text("Discard & Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSwitchStoreDialog = false
                        switchStorePendingProduct = null
                    }
                ) {
                    Text("Keep Current Cart")
                }
            }
        )
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
    if (selectedService == ServiceType.PARCEL) {
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
                cityId = detectedCity.id,
                authViewModel = authViewModel,
                onOrderBooked = {
                    mainViewModel.showSnackbar("Parcel delivery order booked! Tracking rider now.")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (selectedService == ServiceType.PABILI) {
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
                cityId = detectedCity.id,
                authViewModel = authViewModel,
                onOrderBooked = {
                    mainViewModel.showSnackbar("Pabili errand booked! Rider is on the way to the vendor.")
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    if (selectedService == ServiceType.TAXI) {
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
                cityId = detectedCity.id,
                authViewModel = authViewModel,
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
                                        text = "Deliver to • ${detectedCity.name}",
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
                                text = "Search dishes, restaurants, groceries in ${detectedCity.name}...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // HOME SERVICE ROW: 5 evenly spaced large image icons (56dp), no "All" chip, super-app launcher style
        item {
            Column(modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    launcherServices.forEach { item ->
                        val isEnabled = platformSettings.services[item.key] != false
                        val isSelected = selectedService == item.serviceType

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isEnabled) {
                                        mainViewModel.showSnackbar("${item.title} is currently unavailable")
                                        return@clickable
                                    }
                                    when (item.serviceType) {
                                        ServiceType.FOOD, ServiceType.CONVENIENCE -> {
                                            selectedService = if (isSelected) null else item.serviceType
                                            selectedCategory = "All"
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
                                        if (isSelected) YellowPrimary
                                        else if (!isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                DelivioIcon(
                                    iconFileName = item.iconFile,
                                    contentDescription = item.title,
                                    size = 56.dp,
                                    fallbackIcon = item.fallbackIcon,
                                    modifier = if (!isEnabled) Modifier.alpha(0.38f) else Modifier
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (!isEnabled) "Unavailable" else item.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isEnabled) MaterialTheme.colorScheme.outline
                                        else if (isSelected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                            selectedService = ServiceType.CONVENIENCE
                            selectedCategory = "All"
                        } else if (banner.link.category == "food") {
                            selectedService = ServiceType.FOOD
                            selectedCategory = "All"
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // CUISINES & CATEGORIES (Two horizontal scroll rows, 48dp icons over labels, yellow ring when selected)
        item {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Text(
                    text = "Cuisines & Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )

                // Row 1: Food (Shown when none selected or Food selected)
                if (selectedService == null || selectedService == ServiceType.FOOD) {
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
                        val foodChips = listOf("All") + DelivioIconConstants.FOOD_CATEGORIES
                        foodChips.forEach { catName ->
                            val isSelected = selectedCategory == catName
                            val iconFile = DelivioIconConstants.CATEGORY_ICONS[catName]
                            val fallback = DelivioIconConstants.CATEGORY_FALLBACK_ICONS[catName] ?: Icons.Default.Star

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedCategory = if (selectedCategory.equals(catName, ignoreCase = true)) "All" else catName
                                    }
                                    .testTag("food_chip_$catName")
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
                                    DelivioIcon(
                                        iconFileName = iconFile,
                                        contentDescription = catName,
                                        size = 48.dp,
                                        fallbackIcon = fallback
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = catName,
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

                // Row 2: Convenience (Shown when none selected or Convenience selected)
                if (selectedService == null || selectedService == ServiceType.CONVENIENCE) {
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
                        DelivioIconConstants.CONVENIENCE_CATEGORIES.forEach { catName ->
                            val isSelected = selectedCategory == catName
                            val iconFile = DelivioIconConstants.CATEGORY_ICONS[catName]
                            val fallback = DelivioIconConstants.CATEGORY_FALLBACK_ICONS[catName] ?: Icons.Default.Storefront

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedCategory = if (selectedCategory.equals(catName, ignoreCase = true)) "All" else catName
                                    }
                                    .testTag("conv_chip_$catName")
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
                                    DelivioIcon(
                                        iconFileName = iconFile,
                                        contentDescription = catName,
                                        size = 48.dp,
                                        fallbackIcon = fallback
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = catName,
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

        // FAST DELIVERY SECTION (only for All and when non-empty)
        if (selectedCategory == "All" && fastDeliveryMerchants.isNotEmpty()) {
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
                    items(fastDeliveryMerchants) { rest ->
                        RestaurantCard(
                            restaurant = rest,
                            onClick = { onNavigateToRestaurant(rest.id) },
                            modifier = Modifier.width(260.dp)
                        )
                    }
                }
            }
        }

        // DEALS & PROMOTIONS SECTION
        if (selectedCategory == "All" && dealMerchants.isNotEmpty()) {
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
                    items(dealMerchants) { rest ->
                        RestaurantCard(
                            restaurant = rest,
                            onClick = { onNavigateToRestaurant(rest.id) },
                            modifier = Modifier.width(260.dp)
                        )
                    }
                }
            }
        }

        if (selectedCategory.equals("All", ignoreCase = true)) {
            // MAIN LIST HEADER: List title shows the category name and the shop count
            item {
                val sectionTitle = "$selectedCategory (${filteredRestaurants.size})"

                SectionHeader(
                    title = sectionTitle,
                    icon = if (selectedService == ServiceType.CONVENIENCE) Icons.Default.Storefront else Icons.Default.Star,
                    iconTint = YellowPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                )
            }

            // EMPTY RESULT OR MERCHANTS CARDS
            if (filteredRestaurants.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
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
                            Text(
                                text = "No shops available",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
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
                                        text = "City: ${detectedCity.name} (${detectedCity.id})",
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
            } else {
                items(filteredRestaurants) { rest ->
                    RestaurantCard(
                        restaurant = rest,
                        onClick = { onNavigateToRestaurant(rest.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            // CATEGORY PRODUCTS GRID (Two-column grid titled "<Category> (N items)")
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "$selectedCategory (${sortedProducts.size} items)",
                        icon = Icons.Default.Storefront,
                        iconTint = YellowPrimary
                    )

                    // Sort toggle
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable { sortByPrice = !sortByPrice }
                            .testTag("category_sort_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (sortByPrice) "Sort: Price (₱)" else "Sort: Name (A-Z)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLoadingCategoryProducts) {
                items(3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryProductSkeleton(modifier = Modifier.weight(1f))
                        CategoryProductSkeleton(modifier = Modifier.weight(1f))
                    }
                }
            } else if (sortedProducts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Text(
                                text = "No $selectedCategory products yet - Coming soon",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { selectedCategory = "All" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowPrimary,
                                    contentColor = TextOnYellow
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View All Shops", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(sortedProducts.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val prod1 = pair[0]
                        CategoryProductCard(
                            product = prod1,
                            onProductClick = { onNavigateToRestaurant(prod1.merchantId) },
                            onAddToCart = {
                                val currentRest = cartViewModel?.currentRestaurant?.value
                                val cartItems = cartViewModel?.cartItems?.value ?: emptyList()
                                if (cartItems.isNotEmpty() && currentRest != null && currentRest.id != prod1.merchantId) {
                                    switchStorePendingProduct = prod1
                                    showSwitchStoreDialog = true
                                } else {
                                    val menuItem = com.example.data.model.MenuItem(
                                        id = prod1.id,
                                        restaurantId = prod1.merchantId,
                                        name = prod1.name,
                                        description = "",
                                        price = prod1.price,
                                        category = prod1.categoryName,
                                        emoji = "🍽️",
                                        imageUrl = prod1.imageUrl
                                    )
                                    cartViewModel?.addItem(menuItem, prod1.merchant)
                                    mainViewModel.showSnackbar("Added ${prod1.name} to cart")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.size > 1) {
                            val prod2 = pair[1]
                            CategoryProductCard(
                                product = prod2,
                                onProductClick = { onNavigateToRestaurant(prod2.merchantId) },
                                onAddToCart = {
                                    val currentRest = cartViewModel?.currentRestaurant?.value
                                    val cartItems = cartViewModel?.cartItems?.value ?: emptyList()
                                    if (cartItems.isNotEmpty() && currentRest != null && currentRest.id != prod2.merchantId) {
                                        switchStorePendingProduct = prod2
                                        showSwitchStoreDialog = true
                                    } else {
                                        val menuItem = com.example.data.model.MenuItem(
                                            id = prod2.id,
                                            restaurantId = prod2.merchantId,
                                            name = prod2.name,
                                            description = "",
                                            price = prod2.price,
                                            category = prod2.categoryName,
                                            emoji = "🍽️",
                                            imageUrl = prod2.imageUrl
                                        )
                                        cartViewModel?.addItem(menuItem, prod2.merchant)
                                        mainViewModel.showSnackbar("Added ${prod2.name} to cart")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
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

@Composable
fun CategoryProductCard(
    product: com.example.data.repository.CategoryProduct,
    onProductClick: () -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onProductClick() }
            .testTag("product_card_${product.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                if (product.imageUrl.isNotBlank()) {
                    com.example.ui.components.DelivioAsyncImage(
                        imageUrl = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍽️", fontSize = 32.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = if (product.isMerchantOpen) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (product.isMerchantOpen) "OPEN" else "CLOSED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = product.merchantName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.util.Formatters.formatPrice(product.price),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (product.isMerchantOpen) {
                        IconButton(
                            onClick = onAddToCart,
                            modifier = Modifier
                                .size(32.dp)
                                .background(YellowPrimary, CircleShape)
                                .testTag("add_product_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to cart",
                                tint = TextOnYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Closed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryProductSkeleton(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
