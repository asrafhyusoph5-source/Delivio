package com.example.ui.screens.shop

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import kotlinx.coroutines.launch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.RestaurantRepository
import com.example.ui.components.RestaurantCard
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

@Composable
fun ShopScreen(
    restaurantRepository: RestaurantRepository,
    onNavigateToRestaurant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allRestaurants by restaurantRepository.restaurants.collectAsState()
    val mainCategories by restaurantRepository.mainCategories.collectAsState()
    val cities by restaurantRepository.service.getCitiesFlow().collectAsState(initial = emptyList())
    val platformSettings by restaurantRepository.service.getPlatformSettingsFlow().collectAsState(initial = com.example.data.model.PlatformSettings())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
    val userFavorites by restaurantRepository.getFavoritesFlow(currentUid).collectAsState(initial = emptySet())
    val primaryCity = remember(cities) {
        cities.firstOrNull { it.status.equals("active", ignoreCase = true) }
            ?: cities.firstOrNull()
            ?: com.example.data.model.City(id = "puerto-princesa")
    }
    val deliveryRates by restaurantRepository.service.getDeliveryRatesForShopFlow(
        shopCityId = primaryCity.id,
        customerCityId = primaryCity.id
    ).collectAsState(initial = com.example.data.model.DeliveryRates())

    var searchQuery by remember { mutableStateOf("") }
    var filterRating by remember { mutableStateOf(false) }
    var filterFastest by remember { mutableStateOf(false) }
    var filterFreeDelivery by remember { mutableStateOf(false) }
    var filterOpenOnly by remember { mutableStateOf(false) }

    val filteredList = remember(allRestaurants, searchQuery, filterRating, filterFastest, filterFreeDelivery, filterOpenOnly, platformSettings, mainCategories, cities, deliveryRates) {
        allRestaurants.filter { rest ->
            val matchesQuery = com.example.util.MainCategoriesUtil.shopMatchesSearchQuery(rest, searchQuery, mainCategories)
            val shopCity = com.example.util.CityDeliveryRateResolver.findMatchingCity(rest.cityId, cities) ?: primaryCity
            val distanceKm = if ((shopCity.lat != 0.0 || shopCity.lng != 0.0) && (rest.lat != 0.0 || rest.lng != 0.0)) {
                com.example.util.BranchGroupingUtil.calculateDistanceKm(shopCity.lat, shopCity.lng, rest.lat, rest.lng)
            } else 0.0
            val estEta = com.example.util.EtaCalculator.estimateMinutes(rest.prepTimeMinutes, distanceKm)
            val estFee = com.example.util.CityDeliveryRateResolver.resolveShopDeliveryFee(
                shop = rest,
                distanceKm = distanceKm,
                deliveryRates = deliveryRates,
                defaultRates = com.example.data.model.DeliveryRates()
            )

            val matchesRating = if (filterRating) (rest.ratingCount > 0 && rest.averageRating >= 4.8) else true
            val matchesFastest = if (filterFastest) com.example.util.EtaCalculator.isFastDelivery(estEta) else true
            val matchesFreeDelivery = if (filterFreeDelivery) estFee == 0.0 else true
            val matchesOpenOnly = if (filterOpenOnly) {
                com.example.util.BusinessHoursUtil.effectiveOpen(
                    shop = rest,
                    timezone = platformSettings.timezone,
                    platformHolidays = platformSettings.holidays
                )
            } else true

            matchesQuery && matchesRating && matchesFastest && matchesFreeDelivery && matchesOpenOnly
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Shop Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = YellowPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Merchants & Restaurants",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "Browse curated spots, order fresh food on demand",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search filter field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter shops by name or cuisine...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
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
                    .testTag("shop_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterOpenOnly,
                    onClick = { filterOpenOnly = !filterOpenOnly },
                    label = { Text("Open Now 🟢") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = YellowPrimary,
                        selectedLabelColor = TextOnYellow
                    ),
                    modifier = Modifier.testTag("filter_open_only_chip")
                )

                FilterChip(
                    selected = filterRating,
                    onClick = { filterRating = !filterRating },
                    label = { Text("Rating 4.8+ ★") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = YellowPrimary,
                        selectedLabelColor = TextOnYellow
                    )
                )

                FilterChip(
                    selected = filterFastest,
                    onClick = { filterFastest = !filterFastest },
                    label = { Text("Fastest (≤25 min)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = YellowPrimary,
                        selectedLabelColor = TextOnYellow
                    )
                )

                FilterChip(
                    selected = filterFreeDelivery,
                    onClick = { filterFreeDelivery = !filterFreeDelivery },
                    label = { Text("Free Delivery") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = YellowPrimary,
                        selectedLabelColor = TextOnYellow
                    )
                )
            }
        }

        // Restaurant List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "${filteredList.size} restaurants available",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No merchants match your filters",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Try clearing filters to see all restaurants",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { restaurant ->
                    val isFav = userFavorites.contains(restaurant.id)
                    val shopCity = com.example.util.CityDeliveryRateResolver.findMatchingCity(restaurant.cityId, cities) ?: primaryCity
                    val distanceKm = if ((shopCity.lat != 0.0 || shopCity.lng != 0.0) && (restaurant.lat != 0.0 || restaurant.lng != 0.0)) {
                        com.example.util.BranchGroupingUtil.calculateDistanceKm(shopCity.lat, shopCity.lng, restaurant.lat, restaurant.lng)
                    } else 0.0
                    val estEta = com.example.util.EtaCalculator.estimateMinutes(restaurant.prepTimeMinutes, distanceKm)
                    val estFee = com.example.util.CityDeliveryRateResolver.resolveShopDeliveryFee(
                        shop = restaurant,
                        distanceKm = distanceKm,
                        deliveryRates = deliveryRates,
                        defaultRates = com.example.data.model.DeliveryRates()
                    )
                    RestaurantCard(
                        restaurant = restaurant,
                        onClick = { onNavigateToRestaurant(restaurant.id) },
                        timezone = platformSettings.timezone,
                        platformHolidays = platformSettings.holidays,
                        isFavorite = isFav,
                        onToggleFavorite = {
                            if (currentUid.isNotEmpty()) {
                                scope.launch { restaurantRepository.toggleFavorite(currentUid, restaurant.id) }
                            }
                        },
                        estimatedEtaMinutes = estEta,
                        estimatedDeliveryFee = estFee
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
