package com.example.util

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dynamic ETA estimation & shop header aspect-ratio utilities.
 */
object EtaCalculator {
    const val FAST_DELIVERY_MAX_MINUTES = 25
    private const val AVERAGE_SPEED_KMH = 20.0
    private const val HANDOFF_BUFFER_MINUTES = 5
    private const val RANGE_SPAN_MINUTES = 10

    /**
     * Estimate: prepTimeMinutes + round(distanceKm / 20 km/h * 60) + 5 minutes.
     */
    fun estimateMinutes(prepTimeMinutes: Int, distanceKm: Double): Int {
        val safePrep = prepTimeMinutes.coerceAtLeast(1)
        val safeKm = distanceKm.coerceAtLeast(0.0)
        val travelMinutes = ((safeKm / AVERAGE_SPEED_KMH) * 60.0).roundToInt()
        return (safePrep + travelMinutes + HANDOFF_BUFFER_MINUTES).coerceAtLeast(5)
    }

    /**
     * Convenience overload calculating distance from customer and shop coordinates.
     */
    fun estimateMinutesForCoords(
        prepTimeMinutes: Int,
        customerLat: Double,
        customerLng: Double,
        shopLat: Double,
        shopLng: Double
    ): Int {
        val hasCustomerCoords = (customerLat != 0.0 || customerLng != 0.0)
        val hasShopCoords = (shopLat != 0.0 || shopLng != 0.0)
        val distanceKm = if (hasCustomerCoords && hasShopCoords) {
            BranchGroupingUtil.calculateDistanceKm(customerLat, customerLng, shopLat, shopLng)
        } else {
            0.0
        }
        return estimateMinutes(prepTimeMinutes, distanceKm)
    }

    /**
     * Format an estimated delivery time as a 10-minute range, e.g. "25-35 mins".
     */
    fun formatRange(estimatedMinutes: Int): String {
        val minEta = estimatedMinutes.coerceAtLeast(5)
        val maxEta = minEta + RANGE_SPAN_MINUTES
        return "$minEta-$maxEta mins"
    }

    fun isFastDelivery(estimatedMinutes: Int): Boolean {
        return estimatedMinutes <= FAST_DELIVERY_MAX_MINUTES
    }

    /**
     * Returns true when image dimensions are roughly square (ratio within 1.4x),
     * so shop logos are shown centered as a rounded square rather than stretched as a cover banner.
     */
    fun isRoughlySquareImage(width: Float, height: Float): Boolean {
        if (width <= 0f || height <= 0f) return true
        val ratio = max(width / height, height / width)
        return ratio <= 1.4f
    }
}
