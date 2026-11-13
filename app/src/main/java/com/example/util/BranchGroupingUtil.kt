package com.example.util

import com.example.data.api.OsrmRoutingService
import com.example.data.model.Address
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.Restaurant
import java.util.Locale
import kotlin.math.max

data class BranchWithDistance(
    val restaurant: Restaurant,
    val distanceKm: Double
)

object BranchGroupingUtil {

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
        return currentCity.lat to currentCity.lng
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

    /**
     * A branch counts as deliverable only if:
     * - status == "active"
     * - its city service is on
     * - customer's delivery location is inside its delivery radius (including special radius)
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
        val distKm = calculateDistanceKm(customerLat, customerLng, branch.lat, branch.lng)
        val maxRadius = getEffectiveDeliveryRadiusKm(branch, defaultRadiusKm)
        return distKm <= maxRadius
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
                        serviceOn = isServiceOnForType(
                            if (shop.isConvenience || shop.type.equals("convenience", ignoreCase = true)) "convenience" else "food"
                        )
                    )
            }
            .map { shop ->
                BranchWithDistance(
                    restaurant = shop,
                    distanceKm = calculateDistanceKm(customerLat, customerLng, shop.lat, shop.lng)
                )
            }
            .sortedWith(
                compareBy<BranchWithDistance> { it.distanceKm }
                    .thenByDescending { it.restaurant.rating }
                    .thenBy { it.restaurant.id }
            )
    }

    /**
     * Groups deliverable shops by chainId:
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
            val serviceKey = if (shop.isConvenience || shop.type.equals("convenience", ignoreCase = true)) "convenience" else "food"
            val serviceOn = isServiceOnForType(serviceKey)
            if (isBranchDeliverable(shop, customerLat, customerLng, defaultRadiusKm, serviceOn)) {
                BranchWithDistance(
                    restaurant = shop,
                    distanceKm = calculateDistanceKm(customerLat, customerLng, shop.lat, shop.lng)
                )
            } else {
                null
            }
        }

        val (singleShops, chainShops) = deliverable.partition { it.restaurant.chainId.isBlank() }

        val chainWinners = chainShops
            .groupBy { it.restaurant.chainId.trim() }
            .values
            .mapNotNull { branches ->
                branches.minWithOrNull(
                    compareBy<BranchWithDistance> { it.distanceKm }
                        .thenByDescending { it.restaurant.rating }
                        .thenBy { it.restaurant.id }
                )
            }

        return (singleShops + chainWinners).sortedWith(
            compareBy<BranchWithDistance> { it.distanceKm }
                .thenByDescending { it.restaurant.rating }
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
        val rate = if (branch.isConvenience || branch.type.equals("convenience", ignoreCase = true)) {
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
