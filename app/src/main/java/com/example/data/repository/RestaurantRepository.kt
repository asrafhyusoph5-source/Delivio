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

data class MerchantsDiagnostic(
    val totalLoaded: Int = 0,
    val inOtherCities: Int = 0,
    val notActive: Int = 0
)

class RestaurantRepository(
    private val firestoreService: FirestoreService
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val initialMerchants = com.example.data.datasource.MockData.restaurants

    private val _restaurants = MutableStateFlow<List<Restaurant>>(initialMerchants)
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _currentCityId = MutableStateFlow("puerto-princesa")
    val currentCityId: StateFlow<String> = _currentCityId.asStateFlow()

    private val _diagnostic = MutableStateFlow(MerchantsDiagnostic())
    val diagnostic: StateFlow<MerchantsDiagnostic> = _diagnostic.asStateFlow()

    private var merchantsJob: Job? = null

    fun stopAndClear() {
        merchantsJob?.cancel()
        merchantsJob = null
        _restaurants.value = emptyList()
        _diagnostic.value = MerchantsDiagnostic()
    }

    fun loadCityMerchants(cityId: String) {
        val targetCity = cityId.trim()
        _currentCityId.value = targetCity
        merchantsJob?.cancel()
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser == null) {
            _restaurants.value = emptyList()
            return
        }
        merchantsJob = scope.launch {
            firestoreService.getMerchantsFlow().collect { allMerchants ->
                val totalLoaded = allMerchants.size
                val notActive = allMerchants.count { it.status.trim().lowercase(java.util.Locale.ROOT) != "active" }
                val inOtherCities = allMerchants.count {
                    it.status.trim().lowercase(java.util.Locale.ROOT) == "active" &&
                    !it.cityId.trim().equals(targetCity, ignoreCase = true)
                }

                _diagnostic.value = MerchantsDiagnostic(
                    totalLoaded = totalLoaded,
                    inOtherCities = inOtherCities,
                    notActive = notActive
                )

                // Match merchants.cityId to the city document ID, case-insensitively
                val cityMerchants = allMerchants.filter {
                    it.status.trim().lowercase(java.util.Locale.ROOT) == "active" &&
                    it.cityId.trim().equals(targetCity, ignoreCase = true)
                }

                val mapped = cityMerchants.map { m ->
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
                        status = m.status,
                        isPaused = m.isPaused,
                        pauseUntilTime = m.pauseUntilTime,
                        autoSchedule = m.autoSchedule,
                        lat = m.lat,
                        lng = m.lng
                    )
                }
                _restaurants.value = mapped
            }
        }
    }

    fun reload() {
        loadCityMerchants(_currentCityId.value)
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

    private val categoryProductsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<CategoryProduct>>>()

    suspend fun getCategoryProducts(cityId: String, categoryName: String, timezone: String = "Asia/Manila"): List<CategoryProduct> {
        val cacheKey = "${cityId.trim().lowercase(java.util.Locale.ROOT)}_${categoryName.trim().lowercase(java.util.Locale.ROOT)}"
        val cached = categoryProductsCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < 5 * 60 * 1000L) {
            return cached.second
        }

        // Active shops in the city whose cuisineTags contain categoryName (case-insensitive)
        val activeShops = _restaurants.value.filter { rest ->
            rest.status.equals("active", ignoreCase = true) &&
            rest.cuisineTags.any { tag -> tag.equals(categoryName, ignoreCase = true) }
        }

        val results = mutableListOf<CategoryProduct>()
        for (shop in activeShops) {
            val isOpen = com.example.util.BusinessHoursUtil.effectiveOpen(shop, timezone, now)
            val items = firestoreService.getMerchantAvailableItems(shop.id)
            val matchingItems = items.filter { item ->
                item.categoryName.equals(categoryName, ignoreCase = true) ||
                (item.categoryName.isBlank() && (item.name.contains(categoryName, ignoreCase = true) || item.description.contains(categoryName, ignoreCase = true)))
            }

            for (item in matchingItems) {
                val menuItem = MenuItem(
                    id = item.id,
                    restaurantId = shop.id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    category = if (item.categoryName.isNotBlank()) item.categoryName else categoryName,
                    emoji = if (item.name.contains("Burger", true)) "🍔"
                    else if (item.name.contains("Pizza", true)) "🍕"
                    else if (item.name.contains("Drink", true) || item.name.contains("Tea", true) || item.name.contains("Coffee", true)) "🥤"
                    else if (item.name.contains("Wing", true) || item.name.contains("Chicken", true)) "🍗"
                    else if (item.name.contains("Cake", true) || item.name.contains("Dessert", true)) "🍰"
                    else "🍽️",
                    imageUrl = item.imageUrl,
                    isPopular = item.price > 150.0,
                    discountBadge = if (!item.isAvailable) "Sold out" else null
                )
                results.add(
                    CategoryProduct(
                        menuItem = menuItem,
                        merchant = shop,
                        isMerchantOpen = isOpen
                    )
                )
            }
        }

        categoryProductsCache[cacheKey] = Pair(now, results)
        return results
    }

    val service: FirestoreService
        get() = firestoreService
}

data class CategoryProduct(
    val menuItem: MenuItem,
    val merchant: Restaurant,
    val isMerchantOpen: Boolean
) {
    val id: String get() = menuItem.id
    val name: String get() = menuItem.name
    val price: Double get() = menuItem.price
    val imageUrl: String get() = menuItem.imageUrl
    val categoryName: String get() = menuItem.category
    val merchantId: String get() = merchant.id
    val merchantName: String get() = merchant.name
}
