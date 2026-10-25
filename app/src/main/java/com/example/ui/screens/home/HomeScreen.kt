package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil.compose.AsyncImage
import com.example.data.model.Banner
import com.example.data.model.City
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import com.example.data.model.ServiceType
import com.example.data.repository.OrderRepository
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.RestaurantCard
import com.example.ui.components.SetManualAddressDialog
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.DelivioCategoryConstants
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    restaurantRepository: RestaurantRepository,
    orderRepository: OrderRepository,
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateToRestaurant: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val savedAddresses by mainViewModel.savedAddresses.collectAsState()

    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val banners by restaurantRepository.service.getBannersFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())

    var selectedService by remember { mutableStateOf(ServiceType.ALL) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddressSheet by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }

    // Detect city: matched to cities collection by center and radius
    val detectedCity = remember(currentAddress, cities) {
        cities.firstOrNull { c ->
            currentAddress.fullAddress.contains(c.name, ignoreCase = true) ||
            currentAddress.title.contains(c.name, ignoreCase = true)
        } ?: cities.firstOrNull() ?: City(id = "puerto-princesa", name = "Puerto Princesa", province = "Palawan")
    }

    // Filter restaurants based on selected category and selected service
    val filteredRestaurants = remember(selectedCategory, selectedService, allRestaurants) {
        val baseList = when (selectedService) {
            ServiceType.FOOD -> allRestaurants.filter { it.type.equals("food", ignoreCase = true) || !it.isConvenience }
            ServiceType.CONVENIENCE -> allRestaurants.filter { it.type.equals("convenience", ignoreCase = true) || it.isConvenience }
            else -> allRestaurants // All lists every active shop of both types
        }

        if (selectedCategory.equals("All", ignoreCase = true)) {
            baseList
        } else {
            baseList.filter { rest ->
                val isFoodCat = DelivioCategoryConstants.FOOD_CATEGORIES.any { it.equals(selectedCategory, ignoreCase = true) }
                val isConvCat = DelivioCategoryConstants.CONVENIENCE_CATEGORIES.any { it.equals(selectedCategory, ignoreCase = true) }

                if (isFoodCat) {
                    (rest.type.equals("food", ignoreCase = true) || !rest.isConvenience) &&
                            rest.cuisineTags.any { it.equals(selectedCategory, ignoreCase = true) }
                } else if (isConvCat) {
                    (rest.type.equals("convenience", ignoreCase = true) || rest.isConvenience) &&
                            rest.cuisineTags.any { it.equals(selectedCategory, ignoreCase = true) }
                } else {
                    rest.cuisineTags.any { it.equals(selectedCategory, ignoreCase = true) }
                }
            }
        }
    }

    val fastDeliveryMerchants = remember(allRestaurants) {
        allRestaurants.filter { it.isOpen && it.etaMinutes <= 25 }
    }

    val dealMerchants = remember(allRestaurants) {
        allRestaurants.filter { it.isOpen && it.dealDescription != null }
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // TOP APP BAR: Brand, Address Switcher, Notification
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

                        // Right: Notification Bell Icon
                        IconButton(
                            onClick = { mainViewModel.showSnackbar("All notifications up to date! 🔔") },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
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

        // TOP SERVICE PILLS: All, Food, Convenience, Parcel, Pabili, Taxi
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val services = remember(platformSettings) {
                        buildList {
                            add(ServiceType.ALL)
                            if (platformSettings.services["food"] != false) add(ServiceType.FOOD)
                            if (platformSettings.services["convenience"] != false) add(ServiceType.CONVENIENCE)
                            if (platformSettings.services["parcel"] != false) add(ServiceType.PARCEL)
                            if (platformSettings.services["pabili"] != false) add(ServiceType.PABILI)
                            if (platformSettings.services["taxi"] != false) add(ServiceType.TAXI)
                        }
                    }

                    services.forEach { service ->
                        val isSelected = selectedService == service
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surface,
                            shadowElevation = if (isSelected) 4.dp else 1.dp,
                            modifier = Modifier
                                .clickable {
                                    selectedService = service
                                    selectedCategory = "All"
                                }
                                .testTag("service_tab_${service.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = service.emoji, fontSize = 18.sp)
                                Text(
                                    text = service.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // CONDITIONAL CONTENT BASED ON SELECTED SERVICE
        when (selectedService) {
            ServiceType.PARCEL -> {
                item {
                    ParcelBookingView(
                        orderRepository = orderRepository,
                        cityId = detectedCity.id,
                        authViewModel = authViewModel,
                        onOrderBooked = {
                            mainViewModel.showSnackbar("Parcel delivery order booked! Tracking rider now.")
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            ServiceType.PABILI -> {
                item {
                    PabiliBookingView(
                        orderRepository = orderRepository,
                        cityId = detectedCity.id,
                        authViewModel = authViewModel,
                        onOrderBooked = {
                            mainViewModel.showSnackbar("Pabili errand booked! Rider is on the way to the vendor.")
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            ServiceType.TAXI -> {
                item {
                    TaxiBookingView(
                        orderRepository = orderRepository,
                        cityId = detectedCity.id,
                        authViewModel = authViewModel,
                        onOrderBooked = {
                            mainViewModel.showSnackbar("Taxi ride booked! Searching for nearby driver.")
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            else -> {
                // "ALL", "FOOD", "CONVENIENCE" FLOW
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
                                } else if (banner.link.category == "food") {
                                    selectedService = ServiceType.FOOD
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                // CUISINES & CATEGORIES (Two scrolling rows, single selection)
                item {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        Text(
                            text = "Cuisines & Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        )

                        // Row 1: Food (Shown for ALL or FOOD)
                        if (selectedService == ServiceType.ALL || selectedService == ServiceType.FOOD) {
                            Text(
                                text = "Food",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val foodChips = listOf("All") + DelivioCategoryConstants.FOOD_CATEGORIES
                                foodChips.forEach { catName ->
                                    val isSelected = selectedCategory == catName
                                    val icon = DelivioCategoryConstants.FOOD_ICONS[catName] ?: "🍴"
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surface,
                                        shadowElevation = if (isSelected) 3.dp else 1.dp,
                                        modifier = Modifier
                                            .clickable { selectedCategory = catName }
                                            .testTag("food_chip_$catName")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = icon, fontSize = 15.sp)
                                            Text(
                                                text = catName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Row 2: Convenience (Shown for ALL or CONVENIENCE)
                        if (selectedService == ServiceType.ALL || selectedService == ServiceType.CONVENIENCE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Convenience",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DelivioCategoryConstants.CONVENIENCE_CATEGORIES.forEach { catName ->
                                    val isSelected = selectedCategory == catName
                                    val icon = DelivioCategoryConstants.CONVENIENCE_ICONS[catName] ?: "🏪"
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surface,
                                        shadowElevation = if (isSelected) 3.dp else 1.dp,
                                        modifier = Modifier
                                            .clickable { selectedCategory = catName }
                                            .testTag("conv_chip_$catName")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = icon, fontSize = 15.sp)
                                            Text(
                                                text = catName,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
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

                // DEALS SECTION (only for All and when non-empty)
                if (selectedCategory == "All" && dealMerchants.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Featured Partners in ${detectedCity.name}",
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

                // MAIN LIST HEADER: List title shows the chip name and the shop count
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
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(text = "🏪", fontSize = 42.sp)
                                Text(
                                    text = if (selectedCategory == "All") "No shops available yet" else "No shops in $selectedCategory yet - Coming soon",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "We are currently onboarding partners for this selection in ${detectedCity.name}. Please check back soon!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
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
            }
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
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DelivioPromoBannerCarousel(
    banners: List<Banner>,
    onBannerClick: (Banner) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) {
        // Placeholder banner
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
                    Text("⚡", fontSize = 42.sp, modifier = Modifier.padding(start = 8.dp))
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
