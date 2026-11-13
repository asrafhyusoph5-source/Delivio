package com.example.ui.screens.shop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.DispatchSettings
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.ChooseBranchBottomSheet
import com.example.ui.components.RestaurantCard
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.BranchGroupingUtil
import com.example.util.BranchWithDistance
import com.example.util.BusinessHoursUtil
import com.example.util.DelivioIconConstants
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class ServiceShopSort(val label: String) {
    NEAREST("Nearest"),
    TOP_RATED("Top Rated"),
    FASTEST("Fastest"),
    OPEN_NOW("Open Now")
}

@Composable
fun ServiceShopsScreen(
    serviceType: String,
    restaurantRepository: RestaurantRepository,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val normalizedService = if (serviceType.equals("convenience", ignoreCase = true)) "convenience" else "food"
    val screenTitle = if (normalizedService == "convenience") "Convenience" else "Food"

    val scope = rememberCoroutineScope()
    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val currentAddress by mainViewModel.currentAddress.collectAsState()
    val lastCityId by mainViewModel.lastCityId.collectAsState()
    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = PlatformSettings())
    val dispatchSettings by restaurantRepository.service.getDispatchSettingsFlow().collectAsState(initial = DispatchSettings())

    val currentCity = remember(currentAddress, lastCityId, cities) {
        val activeCity = if (!lastCityId.isNullOrBlank()) {
            cities.firstOrNull { it.id.equals(lastCityId, ignoreCase = true) }
        } else null
        activeCity
            ?: cities.firstOrNull { it.status.equals("active", ignoreCase = true) }
            ?: cities.firstOrNull()
            ?: City(id = "puerto-princesa", name = "Puerto Princesa", province = "Palawan", status = "active")
    }

    val liveCity by restaurantRepository.service.getCityDocumentFlow(currentCity.id).collectAsState(initial = null)
    val effectiveCity = liveCity ?: currentCity
    val deliveryRates by restaurantRepository.service.getDeliveryRatesFlow(effectiveCity.id)
        .collectAsState(initial = DeliveryRates())

    val isServiceOnForType = remember(effectiveCity, platformSettings) {
        { serviceKey: String ->
            val platformOn = platformSettings.services[serviceKey] != false
            val cityOn = effectiveCity.services[serviceKey] != false
            platformOn && cityOn
        }
    }

    val (customerLat, customerLng) = remember(currentAddress, effectiveCity) {
        BranchGroupingUtil.resolveCustomerCoordinates(currentAddress, effectiveCity)
    }

    val categoryOptions = remember(normalizedService) {
        val list = if (normalizedService == "convenience") {
            DelivioIconConstants.CONVENIENCE_CATEGORIES
        } else {
            DelivioIconConstants.FOOD_CATEGORIES
        }
        listOf("All") + list
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf(ServiceShopSort.NEAREST) }
    var activeBranchSheetList by remember { mutableStateOf<List<BranchWithDistance>?>(null) }

    val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
    val userFavorites by restaurantRepository.getFavoritesFlow(currentUid).collectAsState(initial = emptySet())

    // All shops for this service type (deliverable, filtered by search & category), grouped to 1 card per chain (nearest branch)
    val serviceShops = remember(allRestaurants, normalizedService) {
        allRestaurants.filter { rest ->
            val isConv = rest.isConvenience || rest.type.equals("convenience", ignoreCase = true)
            if (normalizedService == "convenience") isConv else !isConv
        }
    }

    val groupedShops = remember(
        serviceShops,
        searchQuery,
        selectedCategory,
        selectedSort,
        customerLat,
        customerLng,
        dispatchSettings.searchRadiusKm,
        effectiveCity,
        platformSettings
    ) {
        val matchingFilter = serviceShops.filter { rest ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim()
                rest.displayName.contains(q, ignoreCase = true) ||
                    rest.name.contains(q, ignoreCase = true) ||
                    rest.branchName.contains(q, ignoreCase = true) ||
                    rest.category.contains(q, ignoreCase = true) ||
                    rest.cuisineTags.any { it.contains(q, ignoreCase = true) }
            }
            val matchesCategory = if (selectedCategory.equals("All", ignoreCase = true)) true else {
                rest.cuisineTags.any { it.equals(selectedCategory, ignoreCase = true) } ||
                    rest.category.equals(selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCategory
        }

        val grouped = BranchGroupingUtil.groupByChainNearestBranchWithDistance(
            shops = matchingFilter,
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = dispatchSettings.searchRadiusKm,
            isServiceOnForType = isServiceOnForType
        )

        when (selectedSort) {
            ServiceShopSort.NEAREST -> grouped
            ServiceShopSort.TOP_RATED -> grouped.sortedWith(
                compareByDescending<BranchWithDistance> { it.restaurant.rating }.thenBy { it.distanceKm }
            )
            ServiceShopSort.FASTEST -> grouped.sortedWith(
                compareBy<BranchWithDistance> { it.restaurant.etaMinutes }.thenBy { it.distanceKm }
            )
            ServiceShopSort.OPEN_NOW -> grouped
                .filter { BusinessHoursUtil.effectiveOpen(it.restaurant, platformSettings.timezone) }
        }
    }

    if (activeBranchSheetList != null) {
        ChooseBranchBottomSheet(
            branches = activeBranchSheetList!!,
            deliveryRates = deliveryRates,
            timezone = platformSettings.timezone,
            onSelectBranch = { chosenBranch ->
                activeBranchSheetList = null
                onNavigateToRestaurant(chosenBranch.id)
            },
            onDismiss = { activeBranchSheetList = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("service_shops_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = screenTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = currentAddress.title.takeIf { currentAddress.id != "addr_none" && it.isNotBlank() }
                                    ?: effectiveCity.name,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search $screenTitle shops...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = YellowPrimary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YellowPrimary,
                            focusedLabelColor = YellowPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .testTag("service_shops_search")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryOptions.forEach { category ->
                            val isSelected = selectedCategory.equals(category, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YellowPrimary,
                                    selectedLabelColor = TextOnYellow
                                ),
                                modifier = Modifier.testTag("service_category_chip_$category")
                            )
                        }
                    }

                    // Sort Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ServiceShopSort.values().forEach { sortOption ->
                            val isSelected = selectedSort == sortOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSort = sortOption },
                                label = {
                                    Text(
                                        text = sortOption.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.testTag("service_sort_${sortOption.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Shops List (1 card per chain, nearest deliverable branch only)
            if (groupedShops.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
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
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "No nearby $screenTitle shops",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try a different filter or delivery address.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groupedShops, key = { it.restaurant.id }) { branchItem ->
                        val rest = branchItem.restaurant
                        val isFav = userFavorites.contains(rest.id)
                        RestaurantCard(
                            restaurant = rest,
                            timezone = platformSettings.timezone,
                            onClick = {
                                if (rest.chainId.isBlank()) {
                                    onNavigateToRestaurant(rest.id)
                                } else {
                                    val chainBranches = BranchGroupingUtil.getDeliverableBranchesForChain(
                                        allShops = allRestaurants,
                                        chainId = rest.chainId,
                                        customerLat = customerLat,
                                        customerLng = customerLng,
                                        defaultRadiusKm = dispatchSettings.searchRadiusKm,
                                        isServiceOnForType = isServiceOnForType
                                    )
                                    if (chainBranches.size <= 1) {
                                        onNavigateToRestaurant(rest.id)
                                    } else {
                                        activeBranchSheetList = chainBranches
                                    }
                                }
                            },
                            isFavorite = isFav,
                            onToggleFavorite = {
                                if (currentUid.isNotEmpty()) {
                                    scope.launch { restaurantRepository.toggleFavorite(currentUid, rest.id) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
