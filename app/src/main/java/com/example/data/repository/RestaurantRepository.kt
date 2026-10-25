package com.example.data.repository

import com.example.data.model.MenuItem
import com.example.data.model.Merchant
import com.example.data.model.Restaurant
import com.example.data.model.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RestaurantRepository(
    private val firestoreService: FirestoreService
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val initialMerchants = emptyList<Restaurant>()

    private val _restaurants = MutableStateFlow<List<Restaurant>>(initialMerchants)
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _currentCityId = MutableStateFlow("puerto-princesa")
    val currentCityId: StateFlow<String> = _currentCityId.asStateFlow()

    private var merchantsJob: Job? = null

    init {
        loadCityMerchants("puerto-princesa")
    }

    fun loadCityMerchants(cityId: String) {
        _currentCityId.value = cityId
        merchantsJob?.cancel()
        merchantsJob = scope.launch {
            firestoreService.getMerchantsFlow(cityId).collect { merchants ->
                // Filter: only active merchants; suspended or pending are hidden
                val activeMerchants = merchants.filter { it.status == "active" }
                val mapped = activeMerchants.map { m ->
                    Restaurant(
                        id = m.id,
                        name = m.name,
                        category = if (m.type == "convenience") "Convenience" else (m.cuisineTags.firstOrNull() ?: "Food"),
                        type = m.type,
                        cuisineTags = m.cuisineTags,
                        rating = m.rating,
                        reviewCount = 145,
                        etaMinutes = 25,
                        deliveryFee = 39.0,
                        address = m.address,
                        phone = "+63 917 555 0199",
                        bannerEmoji = if (m.type == "convenience") "🏪" else "🍔",
                        logoUrl = m.logoUrl,
                        bannerUrl = m.bannerUrl,
                        imageUrl = m.imageUrl,
                        isOpen = m.isOpen,
                        isConvenience = m.type == "convenience",
                        dealDescription = if (m.isOpen) "Delivio Certified Partner" else null,
                        hours = m.hours,
                        lat = m.lat,
                        lng = m.lng
                    )
                }
                _restaurants.value = mapped
            }
        }
    }

    fun getRestaurantById(id: String): Restaurant? {
        return _restaurants.value.find { it.id == id }
    }

    fun getMenuItems(restaurantId: String): List<MenuItem> {
        val fallback = firestoreService.defaultMerchants.find { it.id == restaurantId }
        val items = fallback?.items ?: emptyList()
        return items.map { item ->
            MenuItem(
                id = item.id,
                restaurantId = restaurantId,
                name = item.name,
                description = item.description,
                price = item.price,
                category = fallback?.categories?.find { it.id == item.categoryId }?.name ?: "Main",
                emoji = if (item.name.contains("Burger", true)) "🍔"
                else if (item.name.contains("Pizza", true)) "🍕"
                else if (item.name.contains("Drink", true) || item.name.contains("Tea", true) || item.name.contains("Coffee", true)) "🥤"
                else if (item.name.contains("Wing", true) || item.name.contains("Chicken", true)) "🍗"
                else if (item.name.contains("Shake", true) || item.name.contains("Cake", true) || item.name.contains("Dessert", true)) "🍰"
                else "🍽️",
                imageUrl = item.imageUrl,
                isPopular = item.price > 150.0,
                discountBadge = if (!item.isAvailable) "Sold out" else null
            )
        }
    }

    suspend fun updateMerchantShopProfile(
        merchantId: String,
        name: String,
        address: String,
        logoUrl: String,
        bannerUrl: String,
        imageUrl: String
    ): Result<Unit> {
        val res = firestoreService.updateMerchantProfile(
            merchantId = merchantId,
            name = name,
            address = address,
            logoUrl = logoUrl,
            bannerUrl = bannerUrl,
            imageUrl = imageUrl
        )
        _restaurants.value = _restaurants.value.map { r ->
            if (r.id == merchantId) {
                r.copy(
                    name = name.ifBlank { r.name },
                    address = address.ifBlank { r.address },
                    logoUrl = logoUrl.trim(),
                    bannerUrl = bannerUrl.trim(),
                    imageUrl = imageUrl.trim()
                )
            } else r
        }
        return res
    }

    suspend fun saveMerchantMenuItem(
        merchantId: String,
        item: com.example.data.model.MerchantItem
    ): Result<Unit> {
        return firestoreService.saveMerchantItem(merchantId, item)
    }

    fun getRestaurantsByServiceType(serviceType: ServiceType): List<Restaurant> {
        return when (serviceType) {
            ServiceType.ALL -> _restaurants.value
            ServiceType.FOOD -> _restaurants.value.filter { !it.isConvenience }
            ServiceType.CONVENIENCE -> _restaurants.value.filter { it.isConvenience }
            else -> _restaurants.value
        }
    }

    fun filterRestaurants(
        category: String = "All",
        minRating: Double? = null,
        fastestOnly: Boolean = false,
        freeDeliveryOnly: Boolean = false
    ): List<Restaurant> {
        return _restaurants.value.filter { rest ->
            val matchesCategory = if (category == "All") true else {
                val m = firestoreService.defaultMerchants.find { it.id == rest.id }
                m?.cuisineTags?.any { it.equals(category, ignoreCase = true) } == true ||
                        rest.category.equals(category, ignoreCase = true)
            }
            val matchesRating = if (minRating != null) rest.rating >= minRating else true
            val matchesFastest = if (fastestOnly) rest.etaMinutes <= 25 else true
            val matchesFreeDelivery = if (freeDeliveryOnly) rest.deliveryFee == 0.0 else true

            matchesCategory && matchesRating && matchesFastest && matchesFreeDelivery
        }
    }

    fun search(query: String): Pair<List<Restaurant>, List<MenuItem>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList<Restaurant>() to emptyList<MenuItem>()

        val matchingRestaurants = _restaurants.value.filter {
            it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
        }

        val allItems = _restaurants.value.flatMap { getMenuItems(it.id) }
        val matchingItems = allItems.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }

        return matchingRestaurants to matchingItems
    }

    val service: FirestoreService
        get() = firestoreService
}
