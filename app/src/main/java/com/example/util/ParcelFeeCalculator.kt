package com.example.util

import com.example.data.model.ParcelPeakNightRule
import com.example.data.model.ParcelVehicleRate
import com.example.data.model.RateConfig
import com.example.data.model.Schema2ServiceRates
import com.example.data.model.SurchargesConfig
import com.example.data.model.VatConfig
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max

data class ParcelQuote(
    val vehicleType: String,
    val distanceKm: Double,
    val baseDeliveryFee: Double,
    val peakFee: Double,
    val nightFee: Double,
    val surchargeFee: Double,
    val deliveryFee: Double,
    val serviceFee: Double,
    val vat: Double = 0.0,
    val vatPercent: Double = 0.0,
    val vatMode: String = "added",
    val vatBase: Double = 0.0,
    val totalFee: Double,
    val maxWeightKg: Double,
    val isWeightExceeded: Boolean,
    val serviceFeeType: String = "flat",
    val serviceFeePercent: Double = 0.0
)

object ParcelFeeCalculator {

    const val VEHICLE_MOTORCYCLE = "motorcycle"
    const val VEHICLE_SEDAN = "sedan"
    const val DEFAULT_MOTORCYCLE_MAX_WEIGHT_KG = 20.0
    const val DEFAULT_SEDAN_MAX_WEIGHT_KG = 100.0

    fun normalizeParcelVehicleType(raw: String?): String {
        return when (raw?.trim()?.lowercase(Locale.US)) {
            VEHICLE_SEDAN -> VEHICLE_SEDAN
            VEHICLE_MOTORCYCLE -> VEHICLE_MOTORCYCLE
            else -> VEHICLE_MOTORCYCLE
        }
    }

    /**
     * Strict Parcel vehicle display from order's vehicleType field.
     * Never falls back to a default from another service (e.g., never defaults to a car).
     */
    fun formatParcelVehicleDisplay(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        return when (clean.lowercase(Locale.US)) {
            VEHICLE_MOTORCYCLE -> "Motorcycle"
            VEHICLE_SEDAN -> "Sedan"
            else -> ""
        }
    }

    fun formatWeightLimitKg(weightKg: Double): String {
        return if (weightKg % 1.0 == 0.0) {
            weightKg.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", weightKg)
        }
    }

    private fun readDouble(raw: Any?): Double? = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.trim().toDoubleOrNull()
        else -> null
    }

    private fun readBoolean(raw: Any?): Boolean? = when (raw) {
        is Boolean -> raw
        is String -> when (raw.trim().lowercase(Locale.US)) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun parsePeakNightRule(
        map: Map<*, *>,
        nestedKey: String,
        flatFeeKeys: List<String>,
        flatEnabledKeys: List<String>,
        fallbackDocFee: Double
    ): ParcelPeakNightRule {
        val nested = map[nestedKey] as? Map<*, *>
        val surchargesNested = (map["surcharges"] as? Map<*, *>)?.get(nestedKey)

        if (nested != null) {
            val multiplier = readDouble(nested["multiplier"]) ?: 1.0
            val fee = readDouble(nested["fee"])
                ?: readDouble(nested["amount"])
                ?: readDouble(nested["value"])
                ?: readDouble(nested["surcharge"])
                ?: 0.0
            val enabled = readBoolean(nested["enabled"])
                ?: readBoolean(nested["active"])
                ?: (fee > 0.0 || multiplier > 1.0)
            val start = (nested["start"] as? String ?: nested["startTime"] as? String)?.trim()?.ifBlank { null }
            val end = (nested["end"] as? String ?: nested["endTime"] as? String)?.trim()?.ifBlank { null }
            return ParcelPeakNightRule(
                enabled = enabled,
                fee = max(0.0, fee),
                multiplier = if (multiplier > 0.0) multiplier else 1.0,
                startTime = start,
                endTime = end
            )
        }

        val surNestedMap = surchargesNested as? Map<*, *>
        if (surNestedMap != null) {
            val multiplier = readDouble(surNestedMap["multiplier"]) ?: 1.0
            val fee = readDouble(surNestedMap["fee"])
                ?: readDouble(surNestedMap["amount"])
                ?: readDouble(surNestedMap["value"])
                ?: readDouble(surNestedMap["surcharge"])
                ?: 0.0
            val enabled = readBoolean(surNestedMap["enabled"])
                ?: readBoolean(surNestedMap["active"])
                ?: (fee > 0.0 || multiplier > 1.0)
            val start = (surNestedMap["start"] as? String ?: surNestedMap["startTime"] as? String)?.trim()?.ifBlank { null }
            val end = (surNestedMap["end"] as? String ?: surNestedMap["endTime"] as? String)?.trim()?.ifBlank { null }
            return ParcelPeakNightRule(
                enabled = enabled,
                fee = max(0.0, fee),
                multiplier = if (multiplier > 0.0) multiplier else 1.0,
                startTime = start,
                endTime = end
            )
        }

        val surNestedNumber = readDouble(surchargesNested)
        val flatFee = flatFeeKeys.firstNotNullOfOrNull { readDouble(map[it]) }
            ?: surNestedNumber
            ?: fallbackDocFee
        val explicitEnabled = flatEnabledKeys.firstNotNullOfOrNull { readBoolean(map[it]) }
        val enabled = explicitEnabled ?: (flatFee > 0.0)
        return ParcelPeakNightRule(
            enabled = enabled,
            fee = max(0.0, flatFee)
        )
    }

    fun fromSchema2ServiceRates(
        s2: Schema2ServiceRates,
        defaultMaxWeightKg: Double
    ): ParcelVehicleRate {
        val sfMode = if (s2.serviceFee.mode.trim().lowercase(Locale.US) in setOf("percent", "percentage")) {
            "percent"
        } else {
            "flat"
        }
        return ParcelVehicleRate(
            enabled = s2.active,
            baseFare = s2.delivery.baseFare,
            includedKm = s2.delivery.includedKm,
            perKm = s2.delivery.perKm,
            minFee = s2.delivery.minFee,
            maxFee = s2.delivery.maxFee,
            serviceFee = s2.serviceFee.value,
            serviceFeeType = sfMode,
            serviceFeePercent = if (sfMode == "percent") s2.serviceFee.value else 0.0,
            serviceFeeMin = s2.serviceFee.min,
            serviceFeeMax = s2.serviceFee.max,
            driverShare = null,
            maxWeightKg = (s2.maxWeightKg ?: defaultMaxWeightKg).coerceAtLeast(0.5),
            surcharge = 0.0,
            peak = ParcelPeakNightRule(
                enabled = s2.peak.enabled,
                fee = 0.0,
                multiplier = s2.peak.multiplier,
                startTime = s2.peak.startTime,
                endTime = s2.peak.endTime
            ),
            night = ParcelPeakNightRule(
                enabled = s2.night.enabled,
                fee = s2.night.surcharge,
                multiplier = 1.0,
                startTime = s2.night.startTime,
                endTime = s2.night.endTime
            )
        )
    }

    fun parseVehicleRateMap(
        m: Map<*, *>,
        defaultMaxWeightKg: Double,
        fallbackBase: RateConfig? = null,
        docSurcharges: SurchargesConfig = SurchargesConfig()
    ): ParcelVehicleRate {
        val deliveryMap = m["delivery"] as? Map<*, *>
        val enabled = readBoolean(m["enabled"]) ?: readBoolean(m["active"]) ?: true
        val baseFare = readDouble(deliveryMap?.get("baseFare"))
            ?: readDouble(m["baseFare"])
            ?: fallbackBase?.baseFare
            ?: 50.0
        val includedKm = readDouble(deliveryMap?.get("includedKm"))
            ?: readDouble(m["includedKm"])
            ?: fallbackBase?.includedKm
            ?: 3.0
        val perKm = readDouble(deliveryMap?.get("perKm"))
            ?: readDouble(deliveryMap?.get("perKmRate"))
            ?: readDouble(m["perKm"])
            ?: readDouble(m["perKmRate"])
            ?: fallbackBase?.perKmRate
            ?: 14.0
        val minFee = readDouble(deliveryMap?.get("minFee"))
            ?: readDouble(m["minFee"])
            ?: fallbackBase?.minFee
            ?: baseFare
        val maxFee = readDouble(deliveryMap?.get("maxFee"))
            ?: readDouble(m["maxFee"])
            ?: fallbackBase?.maxFee
            ?: 350.0

        val platformServiceFeeMap = m["serviceFee"] as? Map<*, *>
            ?: m["platformServiceFee"] as? Map<*, *>
            ?: m["serviceFeeConfig"] as? Map<*, *>
        val rawMode = (platformServiceFeeMap?.get("mode") as? String
            ?: platformServiceFeeMap?.get("type") as? String
            ?: m["serviceFeeMode"] as? String
            ?: m["serviceFeeType"] as? String
            ?: fallbackBase?.serviceFeeType
            ?: "flat").trim().lowercase(Locale.US)
        val mode = if (rawMode == "percent" || rawMode == "percentage") "percent" else "flat"

        val sFeeValue = readDouble(platformServiceFeeMap?.get("value"))
            ?: readDouble(platformServiceFeeMap?.get("amount"))
            ?: readDouble(m["serviceFeeValue"])
            ?: readDouble(m["serviceFee"])
            ?: fallbackBase?.serviceFee
            ?: 15.0
        val sFeePercent = readDouble(platformServiceFeeMap?.get("percent"))
            ?: readDouble(m["serviceFeePercent"])
            ?: if (mode == "percent") sFeeValue else 0.0
        val sFeeMin = readDouble(platformServiceFeeMap?.get("min"))
            ?: readDouble(m["serviceFeeMin"])
            ?: fallbackBase?.serviceFeeMin
        val sFeeMax = readDouble(platformServiceFeeMap?.get("max"))
            ?: readDouble(m["serviceFeeMax"])
            ?: fallbackBase?.serviceFeeMax

        val driverShare = readDouble(m["driverShare"])
            ?: readDouble(m["riderShare"])
        val maxWeightKg = (readDouble(m["maxWeightKg"]) ?: defaultMaxWeightKg).coerceAtLeast(0.5)
        val surcharge = readDouble(m["surcharge"]) ?: fallbackBase?.surcharge ?: 0.0

        val peak = parsePeakNightRule(
            map = m,
            nestedKey = "peak",
            flatFeeKeys = listOf("peakFee", "peakSurcharge", "peak"),
            flatEnabledKeys = listOf("peakEnabled"),
            fallbackDocFee = docSurcharges.peak
        )
        val night = parsePeakNightRule(
            map = m,
            nestedKey = "night",
            flatFeeKeys = listOf("nightFee", "nightSurcharge", "night"),
            flatEnabledKeys = listOf("nightEnabled"),
            fallbackDocFee = docSurcharges.night
        )

        return ParcelVehicleRate(
            enabled = enabled,
            baseFare = baseFare,
            includedKm = includedKm,
            perKm = perKm,
            minFee = minFee,
            maxFee = maxFee,
            serviceFee = sFeeValue,
            serviceFeeType = mode,
            serviceFeePercent = sFeePercent,
            serviceFeeMin = sFeeMin,
            serviceFeeMax = sFeeMax,
            driverShare = driverShare,
            maxWeightKg = maxWeightKg,
            surcharge = surcharge,
            peak = peak,
            night = night
        )
    }

    /**
     * Resolves deliveryRates/{cityId}.parcel.motorcycle and deliveryRates/{cityId}.parcel.sedan.
     * - If parcel.motorcycle does not exist yet: falls back to legacy parcel values (max 20kg).
     * - If parcel.sedan does not exist yet: returns null (Sedan stays disabled and NEVER uses motorcycle fare).
     */
    fun parseParcelRatesFromParcelMap(
        parcelMap: Map<*, *>?,
        legacyParcel: RateConfig,
        docSurcharges: SurchargesConfig = SurchargesConfig()
    ): Pair<ParcelVehicleRate, ParcelVehicleRate?> {
        val motoRaw = parcelMap?.get(VEHICLE_MOTORCYCLE) as? Map<*, *>
        val sedanRaw = parcelMap?.get(VEHICLE_SEDAN) as? Map<*, *>

        val legacyBaseFromMap = if (parcelMap != null && (
                parcelMap.containsKey("baseFare") ||
                parcelMap.containsKey("perKm") ||
                parcelMap.containsKey("perKmRate") ||
                parcelMap.containsKey("minFee") ||
                parcelMap.containsKey("delivery")
            )
        ) {
            parseVehicleRateMap(
                m = parcelMap,
                defaultMaxWeightKg = DEFAULT_MOTORCYCLE_MAX_WEIGHT_KG,
                fallbackBase = legacyParcel,
                docSurcharges = docSurcharges
            )
        } else {
            ParcelVehicleRate(
                enabled = true,
                baseFare = legacyParcel.baseFare,
                includedKm = legacyParcel.includedKm,
                perKm = legacyParcel.perKmRate,
                minFee = legacyParcel.minFee,
                maxFee = legacyParcel.maxFee,
                serviceFee = legacyParcel.serviceFee,
                serviceFeeType = legacyParcel.serviceFeeType,
                serviceFeePercent = legacyParcel.serviceFeePercent,
                serviceFeeMin = legacyParcel.serviceFeeMin,
                serviceFeeMax = legacyParcel.serviceFeeMax,
                driverShare = null,
                maxWeightKg = DEFAULT_MOTORCYCLE_MAX_WEIGHT_KG,
                surcharge = legacyParcel.surcharge,
                peak = ParcelPeakNightRule(enabled = docSurcharges.peak > 0.0, fee = docSurcharges.peak),
                night = ParcelPeakNightRule(enabled = docSurcharges.night > 0.0, fee = docSurcharges.night)
            )
        }

        val motorcycleRate = if (motoRaw != null) {
            parseVehicleRateMap(
                m = motoRaw,
                defaultMaxWeightKg = DEFAULT_MOTORCYCLE_MAX_WEIGHT_KG,
                fallbackBase = legacyParcel,
                docSurcharges = docSurcharges
            )
        } else {
            legacyBaseFromMap
        }

        val sedanRate = if (sedanRaw != null) {
            parseVehicleRateMap(
                m = sedanRaw,
                defaultMaxWeightKg = DEFAULT_SEDAN_MAX_WEIGHT_KG,
                fallbackBase = null,
                docSurcharges = docSurcharges
            )
        } else {
            null
        }

        return motorcycleRate to sedanRate
    }

    private fun parseLocalTime(raw: String?): LocalTime? {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val parts = clean.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        return try {
            LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
        } catch (_: Exception) {
            null
        }
    }

    fun isRuleActiveAt(
        rule: ParcelPeakNightRule,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (!rule.enabled || (rule.fee <= 0.0 && rule.multiplier <= 1.0)) return false
        val start = parseLocalTime(rule.startTime)
        val end = parseLocalTime(rule.endTime)
        if (start == null || end == null) return true
        val zone = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }
        val current = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
        return if (!start.isAfter(end)) {
            !current.isBefore(start) && !current.isAfter(end)
        } else {
            !current.isBefore(start) || !current.isAfter(end)
        }
    }

    fun calculateParcelQuote(
        vehicleType: String,
        rate: ParcelVehicleRate,
        distanceKm: Double,
        weightKg: Double,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila",
        vatConfig: VatConfig = VatConfig()
    ): ParcelQuote {
        val safeDist = max(0.0, distanceKm)
        val extraKm = max(0.0, safeDist - rate.includedKm)
        val rawDelivery = rate.baseFare + (extraKm * rate.perKm)
        val clampedDelivery = if (rate.maxFee > 0.0 && rate.maxFee >= rate.minFee) {
            rawDelivery.coerceIn(rate.minFee, rate.maxFee)
        } else {
            max(rawDelivery, rate.minFee)
        }

        val peakActive = isRuleActiveAt(rate.peak, nowMillis, timezone)
        val afterPeak = if (peakActive && rate.peak.multiplier > 1.0) {
            FeeCalculator.round2(clampedDelivery * rate.peak.multiplier)
        } else {
            FeeCalculator.round2(clampedDelivery)
        }
        val peakFee = if (peakActive) {
            if (rate.peak.multiplier > 1.0) {
                FeeCalculator.round2((afterPeak - clampedDelivery).coerceAtLeast(0.0) + rate.peak.fee)
            } else {
                FeeCalculator.round2(rate.peak.fee)
            }
        } else {
            0.0
        }
        val nightFee = if (isRuleActiveAt(rate.night, nowMillis, timezone)) FeeCalculator.round2(rate.night.fee) else 0.0
        val surchargeFee = FeeCalculator.round2(max(0.0, rate.surcharge))
        val deliveryFee = FeeCalculator.round2(clampedDelivery + peakFee + nightFee + surchargeFee)

        val feeResult = FeeCalculator.calculateFees(
            subtotal = 0.0,
            deliveryFee = deliveryFee,
            serviceFeeType = rate.serviceFeeType,
            serviceFeeValue = if (rate.serviceFeeType.equals("percent", ignoreCase = true) && rate.serviceFeePercent > 0.0) {
                rate.serviceFeePercent
            } else {
                rate.serviceFee
            },
            serviceFeeMin = rate.serviceFeeMin,
            serviceFeeMax = rate.serviceFeeMax,
            vatConfig = vatConfig,
            distanceKm = safeDist,
            serviceFeeOnItemsPlusDelivery = false,
            vatSubtotal = 0.0
        )

        return ParcelQuote(
            vehicleType = normalizeParcelVehicleType(vehicleType),
            distanceKm = safeDist,
            baseDeliveryFee = FeeCalculator.round2(clampedDelivery),
            peakFee = peakFee,
            nightFee = nightFee,
            surchargeFee = surchargeFee,
            deliveryFee = deliveryFee,
            serviceFee = feeResult.serviceFee,
            vat = feeResult.vat,
            vatPercent = feeResult.vatPercent,
            vatMode = feeResult.vatMode,
            vatBase = feeResult.vatBase,
            totalFee = feeResult.total,
            maxWeightKg = rate.maxWeightKg,
            isWeightExceeded = weightKg > rate.maxWeightKg + 0.0001,
            serviceFeeType = feeResult.serviceFeeType,
            serviceFeePercent = feeResult.serviceFeePercent
        )
    }
}
