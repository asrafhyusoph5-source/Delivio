package com.example.util

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppDesignerServiceConfig
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.RateConfig
import com.example.data.model.Restaurant
import com.example.data.model.ServiceLayoutConfig
import java.util.Locale

data class CityResolutionResult(
    val resolvedCityId: String,
    val source: String, // "shop_exact" | "shop_normalized" | "customer_fallback" | "default_fallback"
    val reason: String? = null
)

object AppDesignerParser {
    const val LOG_TAG = "APPDESIGNER"
    const val DEFAULT_CONVENIENCE_COLUMNS = 2
    const val DEFAULT_SERVICE_LAYOUT_ROWS = 1
    const val DEFAULT_SERVICE_LAYOUT_PER_ROW = 5

    val SERVICE_IDS: List<String> = listOf(
        "food",
        "convenience",
        "parcel",
        "pabili",
        "taxi",
        "flight",
        "loans",
        "jobfair"
    )

    fun normalizeServiceId(rawId: String?): String {
        val cleaned = rawId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (cleaned) {
            "job_fair", "job-fair", "job fair" -> "jobfair"
            else -> cleaned
        }
    }

    fun defaultOrder(serviceId: String): Int {
        val idx = SERVICE_IDS.indexOf(normalizeServiceId(serviceId))
        return if (idx >= 0) idx else SERVICE_IDS.size
    }

    fun defaultServiceConfig(serviceId: String): AppDesignerServiceConfig {
        val key = normalizeServiceId(serviceId)
        return when (key) {
            "food" -> AppDesignerServiceConfig(id = "food", label = "Food", iconUrl = "", visible = true, placement = "home", order = 0)
            "convenience" -> AppDesignerServiceConfig(id = "convenience", label = "Convenience", iconUrl = "", visible = true, placement = "home", order = 1)
            "parcel" -> AppDesignerServiceConfig(id = "parcel", label = "Parcel", iconUrl = "", visible = true, placement = "home", order = 2)
            "pabili" -> AppDesignerServiceConfig(id = "pabili", label = "Pabili", iconUrl = "", visible = true, placement = "home", order = 3)
            "taxi" -> AppDesignerServiceConfig(id = "taxi", label = "Taxi", iconUrl = "", visible = true, placement = "home", order = 4)
            "flight" -> AppDesignerServiceConfig(id = "flight", label = "Flight", iconUrl = "", visible = true, placement = "more", order = 5)
            "loans" -> AppDesignerServiceConfig(id = "loans", label = "Loans", iconUrl = "", visible = true, placement = "more", order = 6)
            "jobfair" -> AppDesignerServiceConfig(id = "jobfair", label = "Job Fair", iconUrl = "", visible = true, placement = "more", order = 7)
            else -> AppDesignerServiceConfig(
                id = key,
                label = key.replaceFirstChar { it.uppercase(Locale.ROOT) },
                iconUrl = "",
                visible = true,
                placement = "more",
                order = 99
            )
        }
    }

    fun defaultServices(): Map<String, AppDesignerServiceConfig> {
        return SERVICE_IDS.associateWith { defaultServiceConfig(it) }
    }

    private fun parseBooleanField(raw: Any?, fallback: Boolean): Boolean = when (raw) {
        is Boolean -> raw
        is String -> when (raw.trim().lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
        else -> fallback
    }

    private fun parseOrderField(raw: Any?, fallback: Int): Int = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toDoubleOrNull()?.toInt() ?: fallback
        else -> fallback
    }

    private fun parseSingleService(rawId: String?, rawMap: Map<*, *>): AppDesignerServiceConfig? {
        val normalizedId = normalizeServiceId(
            rawId?.takeIf { it.isNotBlank() }
                ?: (rawMap["id"] as? String)
                ?: (rawMap["key"] as? String)
                ?: (rawMap["serviceId"] as? String)
        )
        if (normalizedId !in SERVICE_IDS) return null
        val def = defaultServiceConfig(normalizedId)
        val label = (rawMap["label"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: def.label
        val iconUrl = (rawMap["iconUrl"] as? String)?.trim() ?: ""
        val visible = parseBooleanField(rawMap["visible"], def.visible)
        val rawPlacement = (rawMap["placement"] as? String)?.trim()?.lowercase(Locale.ROOT)
        val placement = if (rawPlacement == "home" || rawPlacement == "more") rawPlacement else def.placement
        val order = parseOrderField(rawMap["order"], def.order)
        return AppDesignerServiceConfig(
            id = normalizedId,
            label = label,
            iconUrl = iconUrl,
            visible = visible,
            placement = placement,
            order = order
        )
    }

    fun parseServices(rawServices: Any?): Map<String, AppDesignerServiceConfig> {
        val result = defaultServices().toMutableMap()
        when (rawServices) {
            is Map<*, *> -> {
                rawServices.forEach { (k, v) ->
                    val entryMap = v as? Map<*, *> ?: return@forEach
                    val parsed = parseSingleService(k?.toString(), entryMap)
                    if (parsed != null) {
                        result[parsed.id] = parsed
                    }
                }
            }
            is List<*> -> {
                rawServices.forEach { item ->
                    val entryMap = item as? Map<*, *> ?: return@forEach
                    val parsed = parseSingleService(null, entryMap)
                    if (parsed != null) {
                        result[parsed.id] = parsed
                    }
                }
            }
        }
        return result
    }

    fun extractConfiguredServiceIds(rawServices: Any?): Set<String> {
        val ids = linkedSetOf<String>()
        when (rawServices) {
            is Map<*, *> -> {
                rawServices.forEach { (k, v) ->
                    val entryMap = v as? Map<*, *> ?: return@forEach
                    val id = normalizeServiceId(
                        k?.toString()?.takeIf { it.isNotBlank() }
                            ?: (entryMap["id"] as? String)
                            ?: (entryMap["key"] as? String)
                            ?: (entryMap["serviceId"] as? String)
                    )
                    if (id in SERVICE_IDS) ids.add(id)
                }
            }
            is List<*> -> {
                rawServices.forEach { item ->
                    val entryMap = item as? Map<*, *> ?: return@forEach
                    val id = normalizeServiceId(
                        (entryMap["id"] as? String)
                            ?: (entryMap["key"] as? String)
                            ?: (entryMap["serviceId"] as? String)
                    )
                    if (id in SERVICE_IDS) ids.add(id)
                }
            }
        }
        return ids
    }

    fun parseConvenienceColumns(rawValue: Any?): Int {
        val parsed = when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        return (parsed ?: DEFAULT_CONVENIENCE_COLUMNS).coerceIn(1, 3)
    }

    fun parseServiceLayout(rawLayout: Any?): ServiceLayoutConfig {
        val map = rawLayout as? Map<*, *> ?: return ServiceLayoutConfig()
        val parsedRows = when (val rawRows = map["rows"]) {
            is Number -> rawRows.toInt()
            is String -> rawRows.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        val parsedPerRow = when (val rawPerRow = map["perRow"]) {
            is Number -> rawPerRow.toInt()
            is String -> rawPerRow.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        val rows = (parsedRows ?: DEFAULT_SERVICE_LAYOUT_ROWS).coerceIn(1, 2)
        val perRow = (parsedPerRow ?: DEFAULT_SERVICE_LAYOUT_PER_ROW).coerceIn(4, 5)
        return ServiceLayoutConfig(rows = rows, perRow = perRow)
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
        val normalizedTarget = normalizeCityToken(cleanKey)

        // 1. Compare with city documents by ID (exact, then ignoring capitals and "City")
        cities.firstOrNull { it.id.trim().equals(cleanKey, ignoreCase = true) }?.let { return it }
        if (normalizedTarget.isNotEmpty()) {
            cities.firstOrNull { normalizeCityToken(it.id) == normalizedTarget }?.let { return it }
        }

        // 2. Compare with city documents by name (exact, then ignoring capitals and "City")
        cities.firstOrNull { it.name.trim().equals(cleanKey, ignoreCase = true) }?.let { return it }
        if (normalizedTarget.isNotEmpty()) {
            cities.firstOrNull { normalizeCityToken(it.name) == normalizedTarget }?.let { return it }
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
