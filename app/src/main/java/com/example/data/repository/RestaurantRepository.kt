package com.example.data.repository

import com.example.data.model.HolidayEntry
import com.example.data.model.MainCategoriesConfig
import com.example.data.model.MenuItem
import com.example.data.model.Merchant
import com.example.data.model.Restaurant
import com.example.data.model.ServiceType
import com.example.util.MainCategoriesUtil
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
    private val firestoreService: FirestoreService,
    private val preferenceRepository: PreferenceRepository? = null
) {
    val service: FirestoreService get() = firestoreService

    fun getFavoritesFlow(uid: String): kotlinx.coroutines.flow.Flow<Set<String>> = firestoreService.getFavoritesFlow(uid)

    suspend fun toggleFavorite(uid: String, shopId: String): Result<Boolean> = firestoreService.toggleFavorite(uid, shopId)

    suspend fun isFavorite(uid: String, shopId: String): Boolean = firestoreService.isFavorite(uid, shopId)
    private val scope = CoroutineScope(Dispatchers.Default)

    private val initialMerchants = com.example.data.datasource.MockData.restaurants

    private val _restaurants = MutableStateFlow<List<Restaurant>>(initialMerchants)
    val restaurants: StateFlow<List<Restaurant>> = _restaurants.asStateFlow()

    private val _mainCategories = MutableStateFlow(
        preferenceRepository?.getCachedMainCategories() ?: MainCategoriesUtil.defaultConfig()
    )
    val mainCategories: StateFlow<MainCategoriesConfig> = _mainCategories.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentCityId = MutableStateFlow("puerto-princesa")
    val currentCityId: StateFlow<String> = _currentCityId.asStateFlow()

    private val _diagnostic = MutableStateFlow(MerchantsDiagnostic())
    val diagnostic: StateFlow<MerchantsDiagnostic> = _diagnostic.asStateFlow()

    private var merchantsJob: Job? = null
    private var mainCategoriesJob: Job? = null

    fun stopAndClear() {
        merchantsJob?.cancel()
        merchantsJob = null
        mainCategoriesJob?.cancel()
        mainCategoriesJob = null
        _restaurants.value = emptyList()
        _isLoading.value = false
        _diagnostic.value = MerchantsDiagnostic()
    }

    private fun startMainCategoriesListener() {
        mainCategoriesJob?.cancel()
        mainCategoriesJob = scope.launch {
            firestoreService.getMainCategoriesFlow().collect { liveConfig ->
                if (liveConfig != null) {
                    _mainCategories.value = liveConfig
                    preferenceRepository?.saveCachedMainCategories(liveConfig)
                }
            }
        }
    }

    fun loadCityMerchants(cityId: String) {
        val targetCity = cityId.trim()
        _currentCityId.value = targetCity
        merchantsJob?.cancel()
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser == null) {
            _restaurants.value = emptyList()
            _isLoading.value = false
            return
        }
        startMainCategoriesListener()
        _isLoading.value = true
        merchantsJob = scope.launch {
            firestoreService.getMerchantsFlow().collect { allMerchants ->
                _isLoading.value = false
                val totalLoaded = allMerchants.size
                val notActive = allMerchants.count { it.status.trim().lowercase(java.util.Locale.ROOT) != "active" }
                val inOtherCities = allMerchants.count {
                    it.status.trim().lowercase(java.util.Locale.ROOT) == "active" &&
                    it.cityId.isNotBlank() &&
                    !it.cityId.trim().equals(targetCity, ignoreCase = true)
                }

                _diagnostic.value = MerchantsDiagnostic(
                    totalLoaded = totalLoaded,
                    inOtherCities = inOtherCities,
                    notActive = notActive
                )

                // Map ALL loaded merchants so downstream BranchGroupingUtil can evaluate
                // active status, city/service toggles, and delivery radius & log any exclusions under "SHOPLIST".
                val mapped = allMerchants.map { m ->
                    val normalizedType = m.type.trim().lowercase(java.util.Locale.ROOT).ifBlank { "food" }
                    val isConv = normalizedType == "convenience"
                    Restaurant(
                        id = m.id,
                        name = m.name,
                        category = if (isConv) {
                            m.cuisineTags.firstOrNull() ?: "Convenience"
                        } else {
                            m.cuisineTags.firstOrNull() ?: "Food"
                        },
                        type = normalizedType,
                        cuisineTags = m.cuisineTags,
                        rating = m.averageRating,
                        reviewCount = m.ratingCount,
                        ratingSum = m.ratingSum,
                        ratingCount = m.ratingCount,
                        etaMinutes = com.example.util.EtaCalculator.estimateMinutes(m.prepTimeMinutes, 0.0),
                        deliveryFee = 39.0,
                        address = m.address,
                        phone = m.phone.ifBlank { "+63 917 555 0199" },
                        logoUrl = m.logoUrl,
                        imageUrl = m.imageUrl,
                        bannerUrl = m.bannerUrl,
                        isOpen = m.isOpen,
                        isConvenience = isConv,
                        dealDescription = if (m.isOpen) "Delivio Certified Partner" else null,
                        hours = m.hours,
                        holidays = m.holidays,
                        status = m.status.trim().lowercase(java.util.Locale.ROOT),
                        isPaused = m.isPaused,
                        pauseUntilTime = m.pauseUntilTime,
                        closedUntil = m.closedUntil,
                        openUntil = m.openUntil,
                        autoSchedule = m.autoSchedule,
                        lat = m.lat,
                        lng = m.lng,
                        cityId = m.cityId.trim(),
                        specialRadius = m.specialRadius,
                        prepTimeMinutes = m.prepTimeMinutes,
                        ownerUid = m.ownerUid,
                        chainId = m.chainId,
                        chainName = m.chainName,
                        isBranch = m.isBranch,
                        branchName = m.branchName,
                        branchCode = m.branchCode,
                        branchSetupDone = m.branchSetupDone,
                        deliveryRadiusKm = m.deliveryRadiusKm
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
                discountBadge = if (!item.isAvailable) "Sold out" else null,
                itemSize = item.itemSize,
                availability = item.availability,
                salePrice = item.salePrice,
                saleStartsAt = item.saleStartsAt,
                saleEndsAt = item.saleEndsAt,
                saleActive = item.saleActive,
                soldCount = item.soldCount,
                variationsEnabled = item.variationsEnabled,
                variations = item.variations,
                variants = item.variants,
                priceMax = item.priceMax,
                stockCount = item.stockCount
            )
        }
    }

    suspend fun updateMerchantShopProfile(
        merchantId: String,
        name: String,
        address: String,
        logoUrl: String,
        imageUrl: String
    ): Result<Unit> {
        val res = firestoreService.updateMerchantProfile(
            merchantId = merchantId,
            name = name,
            address = address,
            logoUrl = logoUrl,
            imageUrl = imageUrl
        )
        _restaurants.value = _restaurants.value.map { r ->
            if (r.id == merchantId) {
                r.copy(
                    name = name.ifBlank { r.name },
                    address = address.ifBlank { r.address },
                    logoUrl = logoUrl.trim(),
                    imageUrl = imageUrl.trim()
                )
            } else r
        }
        return res
    }

    fun getMerchantMenusFlow(merchantId: String): kotlinx.coroutines.flow.Flow<List<com.example.data.model.MerchantMenu>> {
        return firestoreService.getMerchantMenusFlow(merchantId)
    }

    suspend fun getMerchantMenus(merchantId: String): List<com.example.data.model.MerchantMenu> {
        return firestoreService.getMerchantMenus(merchantId)
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
            ServiceType.FOOD -> _restaurants.value.filter { !it.isConvenience && !it.type.equals("convenience", ignoreCase = true) }
            ServiceType.CONVENIENCE -> _restaurants.value.filter { it.isConvenience || it.type.equals("convenience", ignoreCase = true) }
            else -> _restaurants.value
        }
    }

    fun filterRestaurants(
        category: String = "All",
        minRating: Double? = null,
        fastestOnly: Boolean = false,
        freeDeliveryOnly: Boolean = false
    ): List<Restaurant> {
        val config = _mainCategories.value
        return _restaurants.value.filter { rest ->
            val matchesCategory = MainCategoriesUtil.shopMatchesCategoryNameOrId(rest, category, config)
            val matchesRating = if (minRating != null) rest.ratingCount > 0 && rest.averageRating >= minRating else true
            val matchesFastest = if (fastestOnly) rest.etaMinutes <= 25 else true
            val matchesFreeDelivery = if (freeDeliveryOnly) rest.deliveryFee == 0.0 else true

            matchesCategory && matchesRating && matchesFastest && matchesFreeDelivery
        }
    }

    fun search(query: String): Pair<List<Restaurant>, List<MenuItem>> {
        val q = MainCategoriesUtil.normalizeKey(query)
        if (q.isEmpty()) return emptyList<Restaurant>() to emptyList<MenuItem>()
        val config = _mainCategories.value

        val matchingRestaurants = _restaurants.value.filter {
            MainCategoriesUtil.shopMatchesSearchQuery(it, query, config)
        }

        val allItems = _restaurants.value.flatMap { getMenuItems(it.id) }
        val matchingItems = allItems.filter { item ->
            MainCategoriesUtil.normalizeKey(item.name).contains(q) ||
                MainCategoriesUtil.normalizeKey(item.description).contains(q) ||
                MainCategoriesUtil.normalizeKey(item.category).contains(q)
        }

        return matchingRestaurants to matchingItems
    }

    private val categoryProductsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<CategoryProduct>>>()

    suspend fun getCategoryProducts(
        cityId: String,
        categoryName: String,
        timezone: String = "Asia/Manila",
        platformHolidays: List<HolidayEntry> = emptyList()
    ): List<CategoryProduct> {
        val cacheKey = "${cityId.trim().lowercase(java.util.Locale.ROOT)}_${MainCategoriesUtil.normalizeKey(categoryName)}"
        val cached = categoryProductsCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < 5 * 60 * 1000L) {
            return cached.second
        }

        val config = _mainCategories.value
        val activeShops = _restaurants.value.filter { rest ->
            rest.status.equals("active", ignoreCase = true) &&
                MainCategoriesUtil.shopMatchesCategoryNameOrId(rest, categoryName, config)
        }

        val results = mutableListOf<CategoryProduct>()
        for (shop in activeShops) {
            val isOpen = com.example.util.BusinessHoursUtil.effectiveOpen(shop, timezone, now, platformHolidays)
            val shopType = if (shop.isConvenience || shop.type.equals("convenience", ignoreCase = true)) "convenience" else "food"
            val activeCatsOfType = MainCategoriesUtil.activeForType(config, shopType)
            val items = firestoreService.getMerchantAvailableItems(shop.id)
            val menus = firestoreService.getMerchantMenus(shop.id)
            val matchingItems = items.filter { item ->
                com.example.util.MenuAvailabilityUtil.isItemOrderable(item, menus, now, timezone) && (
                    MainCategoriesUtil.itemMatchesCategoryQuery(item.categoryName, categoryName, activeCatsOfType) ||
                        (item.categoryName.isBlank() && (
                            MainCategoriesUtil.normalizeKey(item.name).contains(MainCategoriesUtil.normalizeKey(categoryName)) ||
                                MainCategoriesUtil.normalizeKey(item.description).contains(MainCategoriesUtil.normalizeKey(categoryName))
                            ))
                    )
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
                    discountBadge = if (!item.isAvailable) "Sold out" else null,
                    hasSets = item.hasSets,
                    sets = item.sets,
                    menuIds = item.menuIds,
                    itemSize = item.itemSize,
                    availability = item.availability,
                    salePrice = item.salePrice,
                    saleStartsAt = item.saleStartsAt,
                    saleEndsAt = item.saleEndsAt,
                    saleActive = item.saleActive,
                    soldCount = item.soldCount,
                    variationsEnabled = item.variationsEnabled,
                    variations = item.variations,
                    variants = item.variants,
                    priceMax = item.priceMax,
                    stockCount = item.stockCount
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
    val merchantName: String get() = merchant.displayName
    val hasSets: Boolean get() = menuItem.hasSets
    val fromPrice: Double get() = menuItem.fromPrice
    val sets: List<com.example.data.model.ItemSet> get() = menuItem.sets
    val itemSize: String get() = menuItem.itemSize
    val availability: com.example.data.model.ItemAvailability? get() = menuItem.availability
}
