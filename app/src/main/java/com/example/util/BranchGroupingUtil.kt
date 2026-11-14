package com.example.util

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.OsrmRoutingService
import com.example.data.model.Address
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.PlatformSettings
import com.example.data.model.Restaurant
import java.util.Locale
import kotlin.math.max

data class BranchWithDistance(
    val restaurant: Restaurant,
    val distanceKm: Double
)

object BranchGroupingUtil {

    private const val SHOPLIST_TAG = "SHOPLIST"

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d(SHOPLIST_TAG, message)
            } catch (_: Exception) {
                // Ignore in plain JVM environments if Log is not mocked
            }
        }
    }

    /**
     * Menu matching key rule: product/category/group name lowercased, trimmed, repeated spaces collapsed.
     */
    fun normalizeMenuMatchKey(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    /**
     * Format shop display name per OUTLETS DATA CONTRACT:
     * "<name> - <branchName>" (e.g. "Jollibee - Palawan Rizal"); when branchName is empty show just name.
     */
    fun formatShopDisplayName(name: String, branchName: String?): String {
        val cleanName = name.trim()
        val cleanBranch = branchName?.trim().orEmpty()
        if (cleanBranch.isEmpty()) return cleanName
        if (cleanName.isEmpty()) return cleanBranch
        val suffix = " - $cleanBranch"
        return if (cleanName.endsWith(suffix, ignoreCase = true)) {
            cleanName
        } else {
            "$cleanName$suffix"
        }
    }

    fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return OsrmRoutingService.calculateHaversineDistanceKm(lat1, lng1, lat2, lng2)
    }

    fun formatDistanceKm(distanceKm: Double): String {
        return String.format(Locale.US, "%.1f km", distanceKm.coerceAtLeast(0.0))
    }

    fun resolveCustomerCoordinates(currentAddress: Address?, currentCity: City): Pair<Double, Double> {
        if (currentAddress != null &&
            currentAddress.id != "addr_none" &&
            (currentAddress.lat != 0.0 || currentAddress.lng != 0.0)
        ) {
            return currentAddress.lat to currentAddress.lng
        }
        if (currentCity.lat != 0.0 || currentCity.lng != 0.0) {
            return currentCity.lat to currentCity.lng
        }
        if (currentAddress != null && (currentAddress.lat != 0.0 || currentAddress.lng != 0.0)) {
            return currentAddress.lat to currentAddress.lng
        }
        return 0.0 to 0.0
    }

    fun getEffectiveDeliveryRadiusKm(branch: Restaurant, defaultRadiusKm: Double): Double {
        val base = branch.deliveryRadiusKm?.takeIf { it > 0.0 }
            ?: defaultRadiusKm.takeIf { it > 0.0 }
            ?: 5.0
        val sr = branch.specialRadius
        return if (sr != null && sr.enabled && sr.radiusKm > 0.0) {
            max(base, sr.radiusKm)
        } else {
            base
        }
    }

    private fun resolveServiceKey(shop: Restaurant): String {
        return if (shop.isConvenience || shop.type.trim().equals("convenience", ignoreCase = true)) {
            "convenience"
        } else {
            "food"
        }
    }

    private fun findCityForShop(shop: Restaurant, cities: List<City>, fallbackCity: City?): City? {
        val shopCityKey = shop.cityId.trim()
        if (shopCityKey.isNotEmpty()) {
            val matched = cities.firstOrNull { c ->
                c.id.trim().equals(shopCityKey, ignoreCase = true) ||
                    c.name.trim().equals(shopCityKey, ignoreCase = true)
            }
            if (matched != null) return matched
        }
        if (cities.isEmpty() && fallbackCity != null) {
            if (shopCityKey.isEmpty() ||
                fallbackCity.id.trim().equals(shopCityKey, ignoreCase = true) ||
                fallbackCity.name.trim().equals(shopCityKey, ignoreCase = true)
            ) {
                return fallbackCity
            }
        }
        return null
    }

    /**
     * A branch counts as deliverable only if:
     * - status == "active"
     * - its city exists (when cities are provided) and city/platform service is on
     * - customer's delivery location is inside its delivery radius (including special radius);
     *   if location is empty (0.0, 0.0), falls back cleanly and never hides all shops.
     */
    fun isBranchDeliverable(
        branch: Restaurant,
        customerLat: Double,
        customerLng: Double,
        defaultRadiusKm: Double,
        serviceOn: Boolean
    ): Boolean {
        if (!branch.status.trim().equals("active", ignoreCase = true)) return false
        if (!serviceOn) return false
        val hasCustomerCoords = (customerLat != 0.0 || customerLng != 0.0)
        val hasShopCoords = (branch.lat != 0.0 || branch.lng != 0.0)
        if (!hasCustomerCoords || !hasShopCoords) return true
        val distKm = calculateDistanceKm(customerLat, customerLng, branch.lat, branch.lng)
        val maxRadius = getEffectiveDeliveryRadiusKm(branch, defaultRadiusKm)
        return distKm <= maxRadius
    }

    /**
     * Central eligibility + chain-grouping function used across Home, Services, and Search:
     * 1. Active status
     * 2. Shop's city exists and is active with the shop's service ("food" | "convenience") ON
     * 3. Delivery location (or city center fallback) is inside delivery radius (global or specialRadius)
     * 4. Groups chain branches by (chainId, serviceKey) so food & convenience shops never collide.
     * 5. Logs excluded shops under tag "SHOPLIST" in debug builds.
     */
    fun getEligibleGroupedShops(
        allShops: List<Restaurant>,
        cities: List<City>,
        platformSettings: PlatformSettings,
        defaultRadiusKm: Double,
        customerLat: Double,
        customerLng: Double,
        fallbackCity: City? = null,
        logExcluded: Boolean = false
    ): List<BranchWithDistance> {
        val deliverable = mutableListOf<BranchWithDistance>()

        for (shop in allShops) {
            if (!shop.status.trim().equals("active", ignoreCase = true)) {
                if (logExcluded) debugLog("Excluded shop '${shop.displayName}' (${shop.id}): status='${shop.status}' is not active")
                continue
            }

            val shopCity = findCityForShop(shop, cities, fallbackCity)
            if (shopCity == null) {
                if (logExcluded) debugLog("Excluded shop '${shop.displayName}' (${shop.id}): city '${shop.cityId}' not found in active cities")
                continue
            }
            if (!shopCity.status.trim().equals("active", ignoreCase = true)) {
                if (logExcluded) debugLog("Excluded shop '${shop.displayName}' (${shop.id}): city '${shopCity.id}' status='${shopCity.status}' is not active")
                continue
            }

            val serviceKey = resolveServiceKey(shop)
            val platformOn = platformSettings.services[serviceKey] != false
            val cityOn = shopCity.services[serviceKey] != false
            if (!platformOn || !cityOn) {
                if (logExcluded) debugLog("Excluded shop '${shop.displayName}' (${shop.id}): service '$serviceKey' disabled (platform=$platformOn, city:${shopCity.id}=$cityOn)")
                continue
            }

            val effCustLat: Double
            val effCustLng: Double
            if (customerLat != 0.0 || customerLng != 0.0) {
                effCustLat = customerLat
                effCustLng = customerLng
            } else {
                effCustLat = shopCity.lat
                effCustLng = shopCity.lng
            }

            val effShopLat: Double
            val effShopLng: Double
            if (shop.lat != 0.0 || shop.lng != 0.0) {
                effShopLat = shop.lat
                effShopLng = shop.lng
            } else {
                effShopLat = shopCity.lat
                effShopLng = shopCity.lng
            }

            val hasCustomerCoords = (effCustLat != 0.0 || effCustLng != 0.0)
            val hasShopCoords = (effShopLat != 0.0 || effShopLng != 0.0)
            val distKm = if (hasCustomerCoords && hasShopCoords) {
                calculateDistanceKm(effCustLat, effCustLng, effShopLat, effShopLng)
            } else {
                0.0
            }
            val maxRadius = getEffectiveDeliveryRadiusKm(shop, defaultRadiusKm)
            if (hasCustomerCoords && hasShopCoords && distKm > maxRadius) {
                if (logExcluded) {
                    debugLog(
                        "Excluded shop '${shop.displayName}' (${shop.id}, type=$serviceKey): outside delivery radius " +
                            "(${String.format(Locale.US, "%.2f", distKm)} km > ${String.format(Locale.US, "%.2f", maxRadius)} km)"
                    )
                }
                continue
            }

            deliverable.add(BranchWithDistance(restaurant = shop, distanceKm = distKm))
        }

        val (singleShops, chainShops) = deliverable.partition { it.restaurant.chainId.isBlank() }

        val chainWinners = chainShops
            .groupBy { Pair(it.restaurant.chainId.trim(), resolveServiceKey(it.restaurant)) }
            .values
            .mapNotNull { branches ->
                val winner = branches.minWithOrNull(
                    compareBy<BranchWithDistance> { it.distanceKm }
                        .thenByDescending { it.restaurant.averageRating }
                        .thenByDescending { it.restaurant.ratingCount }
                        .thenBy { it.restaurant.id }
                )
                if (logExcluded && winner != null && branches.size > 1) {
                    branches.filter { it.restaurant.id != winner.restaurant.id }.forEach { folded ->
                        debugLog(
                            "Folded branch '${folded.restaurant.displayName}' (${folded.restaurant.id}) under chain " +
                                "'${folded.restaurant.chainId}'; nearest winner is '${winner.restaurant.displayName}' (${winner.restaurant.id})"
                        )
                    }
                }
                winner
            }

        return (singleShops + chainWinners).sortedWith(
            compareBy<BranchWithDistance> { it.distanceKm }
                .thenByDescending { it.restaurant.averageRating }
                .thenByDescending { it.restaurant.ratingCount }
                .thenBy { it.restaurant.id }
        )
    }

    /**
     * Returns all deliverable branches for a given chainId sorted nearest first,
     * with ties broken by higher rating.
     */
    fun getDeliverableBranchesForChain(
        allShops: List<Restaurant>,
        chainId: String,
        customerLat: Double,
        customerLng: Double,
        defaultRadiusKm: Double,
        isServiceOnForType: (String) -> Boolean
    ): List<BranchWithDistance> {
        val cleanChainId = chainId.trim()
        if (cleanChainId.isEmpty()) return emptyList()

        return allShops
            .filter { shop ->
                shop.chainId.trim() == cleanChainId &&
                    isBranchDeliverable(
                        branch = shop,
                        customerLat = customerLat,
                        customerLng = customerLng,
                        defaultRadiusKm = defaultRadiusKm,
                        serviceOn = isServiceOnForType(resolveServiceKey(shop))
                    )
            }
            .map { shop ->
                val hasCust = (customerLat != 0.0 || customerLng != 0.0)
                val hasShop = (shop.lat != 0.0 || shop.lng != 0.0)
                val dist = if (hasCust && hasShop) calculateDistanceKm(customerLat, customerLng, shop.lat, shop.lng) else 0.0
                BranchWithDistance(
                    restaurant = shop,
                    distanceKm = dist
                )
            }
            .sortedWith(
                compareBy<BranchWithDistance> { it.distanceKm }
                    .thenByDescending { it.restaurant.averageRating }
                    .thenByDescending { it.restaurant.ratingCount }
                    .thenBy { it.restaurant.id }
            )
    }

    /**
     * Groups deliverable shops by chainId (and serviceKey):
     * - Single shops (chainId blank) are kept as-is if deliverable.
     * - Merchants with the same non-blank chainId produce ONE winner: the nearest deliverable branch
     *   (ties go to the higher rating).
     */
    fun groupByChainNearestBranchWithDistance(
        shops: List<Restaurant>,
        customerLat: Double,
        customerLng: Double,
        defaultRadiusKm: Double,
        isServiceOnForType: (String) -> Boolean
    ): List<BranchWithDistance> {
        val deliverable = shops.mapNotNull { shop ->
            val serviceKey = resolveServiceKey(shop)
            val serviceOn = isServiceOnForType(serviceKey)
            if (isBranchDeliverable(shop, customerLat, customerLng, defaultRadiusKm, serviceOn)) {
                val hasCust = (customerLat != 0.0 || customerLng != 0.0)
                val hasShop = (shop.lat != 0.0 || shop.lng != 0.0)
                val dist = if (hasCust && hasShop) calculateDistanceKm(customerLat, customerLng, shop.lat, shop.lng) else 0.0
                BranchWithDistance(
                    restaurant = shop,
                    distanceKm = dist
                )
            } else {
                null
            }
        }

        val (singleShops, chainShops) = deliverable.partition { it.restaurant.chainId.isBlank() }

        val chainWinners = chainShops
            .groupBy { Pair(it.restaurant.chainId.trim(), resolveServiceKey(it.restaurant)) }
            .values
            .mapNotNull { branches ->
                branches.minWithOrNull(
                    compareBy<BranchWithDistance> { it.distanceKm }
                        .thenByDescending { it.restaurant.averageRating }
                        .thenByDescending { it.restaurant.ratingCount }
                        .thenBy { it.restaurant.id }
                )
            }

        return (singleShops + chainWinners).sortedWith(
            compareBy<BranchWithDistance> { it.distanceKm }
                .thenByDescending { it.restaurant.averageRating }
                .thenByDescending { it.restaurant.ratingCount }
                .thenBy { it.restaurant.id }
        )
    }

    fun groupByChainNearestBranch(
        shops: List<Restaurant>,
        customerLat: Double,
        customerLng: Double,
        defaultRadiusKm: Double,
        isServiceOnForType: (String) -> Boolean
    ): List<Restaurant> {
        return groupByChainNearestBranchWithDistance(
            shops = shops,
            customerLat = customerLat,
            customerLng = customerLng,
            defaultRadiusKm = defaultRadiusKm,
            isServiceOnForType = isServiceOnForType
        ).map { it.restaurant }
    }

    fun estimateDeliveryFee(
        branch: Restaurant,
        distanceKm: Double,
        deliveryRates: DeliveryRates
    ): Double {
        val rate = if (branch.isConvenience || branch.type.trim().equals("convenience", ignoreCase = true)) {
            deliveryRates.convenience
        } else {
            deliveryRates.food
        }
        val extraKm = max(0.0, distanceKm - rate.includedKm)
        val base = (rate.baseFare + extraKm * rate.perKmRate).coerceIn(rate.minFee, rate.maxFee) + rate.surcharge
        val srExtra = if (branch.specialRadius?.enabled == true) branch.specialRadius.customerExtraFee else 0.0
        return FeeCalculator.round2((base + srExtra).coerceAtLeast(0.0))
    }
}
