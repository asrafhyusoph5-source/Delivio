package com.example.util

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.RateConfig
import com.example.data.model.Restaurant
import java.util.Locale

data class CityResolutionResult(
    val resolvedCityId: String,
    val source: String, // "shop_exact" | "shop_normalized" | "customer_fallback" | "default_fallback"
    val reason: String? = null
)

object AppDesignerParser {
    const val LOG_TAG = "APPDESIGNER"
    const val DEFAULT_CONVENIENCE_COLUMNS = 2

    fun parseConvenienceColumns(rawValue: Any?): Int {
        val parsed = when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        return (parsed ?: DEFAULT_CONVENIENCE_COLUMNS).coerceIn(1, 3)
    }

    fun logRead(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d(LOG_TAG, message)
            } catch (_: Exception) {
                // Ignore in non-Android unit test JVMs
            }
        }
    }
}

object CityDeliveryRateResolver {
    const val LOG_TAG = "DeliveryRates"

    fun normalizeCityToken(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[-_]+"), " ")
            .replace(Regex("\\bcity\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun findMatchingCity(rawCityKey: String?, cities: List<City>): City? {
        val cleanKey = rawCityKey?.trim().orEmpty()
        if (cleanKey.isEmpty()) return null

        // 1. Exact ID match (case-insensitive)
        cities.firstOrNull { it.id.trim().equals(cleanKey, ignoreCase = true) }?.let { return it }
        // 2. Exact name match (case-insensitive)
        cities.firstOrNull { it.name.trim().equals(cleanKey, ignoreCase = true) }?.let { return it }

        // 3. Normalized match ignoring capitals & word "City"
        val normalizedTarget = normalizeCityToken(cleanKey)
        if (normalizedTarget.isNotEmpty()) {
            cities.firstOrNull { c ->
                normalizeCityToken(c.id) == normalizedTarget ||
                    normalizeCityToken(c.name) == normalizedTarget
            }?.let { return it }
        }
        return null
    }

    /**
     * Resolves cityId for deliveryRates lookup:
     * 1. Use shop's cityId if it matches an available deliveryRates document ID or city ID.
     * 2. Compare shop cityId against cities names & ids ignoring capitals and the word "City".
     * 3. Fall back to customer's delivery location city (matched against cities).
     * 4. Log reason if any fallback happens; never use a silent 0 fee.
     */
    fun resolveDeliveryRateCityId(
        shopCityId: String?,
        cities: List<City>,
        customerCityId: String?,
        existingRateDocIds: Set<String>? = null
    ): CityResolutionResult {
        val rawShopCity = shopCityId?.trim().orEmpty()
        val hasExistingSet = existingRateDocIds != null

        fun rateDocExists(id: String): Boolean {
            if (!hasExistingSet) return true
            return existingRateDocIds!!.any { it.equals(id, ignoreCase = true) }
        }

        if (rawShopCity.isNotEmpty()) {
            // Exact ID check
            val exactDocId = existingRateDocIds?.firstOrNull { it.equals(rawShopCity, ignoreCase = true) }
            if (exactDocId != null || (!hasExistingSet && cities.any { it.id.equals(rawShopCity, ignoreCase = true) })) {
                return CityResolutionResult(
                    resolvedCityId = exactDocId ?: rawShopCity,
                    source = "shop_exact"
                )
            }

            // Normalized city comparison against city names & ids (ignoring capitals and "City")
            val matchedCity = findMatchingCity(rawShopCity, cities)
            if (matchedCity != null && rateDocExists(matchedCity.id)) {
                val matchedDoc = existingRateDocIds?.firstOrNull { it.equals(matchedCity.id, ignoreCase = true) } ?: matchedCity.id
                val reason = "No direct deliveryRates doc for '$rawShopCity'; resolved via city '${matchedCity.name}' (${matchedCity.id})"
                logWarn(reason)
                return CityResolutionResult(
                    resolvedCityId = matchedDoc,
                    source = "shop_normalized",
                    reason = reason
                )
            }

            // Normalized comparison directly against rate doc keys if cities list is incomplete
            if (existingRateDocIds != null) {
                val normShop = normalizeCityToken(rawShopCity)
                val normalizedDoc = existingRateDocIds.firstOrNull { normalizeCityToken(it) == normShop }
                if (normalizedDoc != null) {
                    val reason = "No exact deliveryRates doc for '$rawShopCity'; matched normalized doc '$normalizedDoc'"
                    logWarn(reason)
                    return CityResolutionResult(
                        resolvedCityId = normalizedDoc,
                        source = "shop_normalized",
                        reason = reason
                    )
                }
            }
        }

        // Fallback to customer's delivery location city
        val rawCustomerCity = customerCityId?.trim().orEmpty()
        if (rawCustomerCity.isNotEmpty()) {
            val customerCityMatch = findMatchingCity(rawCustomerCity, cities)
            val customerTargetId = customerCityMatch?.id ?: rawCustomerCity
            if (rateDocExists(customerTargetId)) {
                val matchedDoc = existingRateDocIds?.firstOrNull { it.equals(customerTargetId, ignoreCase = true) } ?: customerTargetId
                val reason = if (rawShopCity.isEmpty()) {
                    "Shop cityId missing; using customer delivery city '$matchedDoc'"
                } else {
                    "No deliveryRates doc found for shop city '$rawShopCity'; falling back to customer city '$matchedDoc'"
                }
                logWarn(reason)
                return CityResolutionResult(
                    resolvedCityId = matchedDoc,
                    source = "customer_fallback",
                    reason = reason
                )
            }
        }

        val fallbackId = rawCustomerCity.ifBlank { rawShopCity }.ifBlank { "puerto-princesa" }
        val reason = "No deliveryRates doc found for shop city '$rawShopCity' or customer city '$rawCustomerCity'; using default rates fallback for '$fallbackId'"
        logWarn(reason)
        return CityResolutionResult(
            resolvedCityId = fallbackId,
            source = "default_fallback",
            reason = reason
        )
    }

    /**
     * Guards against a silent 0 fee for delivery orders by falling back to non-zero defaults
     * if rate config has zero/missing base & minimum fees.
     */
    fun ensureNonZeroDeliveryRate(
        rateConfig: RateConfig,
        fallbackRate: RateConfig,
        contextLabel: String
    ): RateConfig {
        if (rateConfig.baseFare <= 0.0 && rateConfig.minFee <= 0.0) {
            logWarn("Rate config for $contextLabel had zero baseFare & minFee; using non-zero default (${fallbackRate.baseFare})")
            return fallbackRate
        }
        return rateConfig
    }

    fun resolveShopDeliveryFee(
        shop: Restaurant,
        distanceKm: Double,
        deliveryRates: DeliveryRates,
        defaultRates: DeliveryRates
    ): Double {
        val isConv = shop.isConvenience || shop.type.trim().equals("convenience", ignoreCase = true)
        val rawRate = if (isConv) deliveryRates.convenience else deliveryRates.food
        val fallbackRate = if (isConv) defaultRates.convenience else defaultRates.food
        val safeRate = ensureNonZeroDeliveryRate(rawRate, fallbackRate, "${shop.displayName} (${shop.cityId})")
        val sanitizedRates = if (isConv) {
            deliveryRates.copy(convenience = safeRate)
        } else {
            deliveryRates.copy(food = safeRate)
        }
        return BranchGroupingUtil.estimateDeliveryFee(shop, distanceKm, sanitizedRates)
    }

    fun logWarn(message: String) {
        try {
            Log.w(LOG_TAG, message)
        } catch (_: Exception) {
            // Ignore in plain JVM environments without Android Log mocked
        }
    }

    fun logInfo(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.i(LOG_TAG, message)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}
