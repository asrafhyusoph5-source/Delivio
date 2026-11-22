package com.example.util

import com.example.data.model.OrderFees
import com.example.data.model.PricingSnapshot
import com.example.data.model.RateConfig
import com.example.data.model.RideRateConfig
import com.example.data.model.Schema2PickupConfig
import com.example.data.model.Schema2ServiceRates
import com.example.data.model.VatConfig
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.round

object FeeCalculator {

    fun round2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    data class FeeCalculationResult(
        val subtotal: Double,
        val deliveryFee: Double,
        val serviceFee: Double,
        val serviceFeeType: String,
        val serviceFeePercent: Double,
        val tip: Double,
        val discount: Double,
        val vat: Double,
        val vatPercent: Double,
        val vatMode: String,
        val vatBase: Double,
        val total: Double,
        val fees: OrderFees
    )

    fun calculateFees(
        subtotal: Double,
        deliveryFee: Double,
        serviceFeeType: String = "flat",
        serviceFeeValue: Double = 10.0,
        serviceFeeMin: Double? = null,
        serviceFeeMax: Double? = null,
        tip: Double = 0.0,
        discount: Double = 0.0,
        vatConfig: VatConfig = VatConfig(),
        isPickup: Boolean = false,
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm",
        serviceFeeOnItemsPlusDelivery: Boolean = true,
        vatSubtotal: Double = subtotal
    ): FeeCalculationResult {
        val effectiveDeliveryFee = if (isPickup) 0.0 else maxOf(0.0, deliveryFee)
        val effectiveTip = if (isPickup) 0.0 else maxOf(0.0, tip)

        // 1. Calculate service fee
        val finalServiceFee: Double
        val finalServiceFeePercent: Double
        if (serviceFeeType.equals("percent", ignoreCase = true)) {
            finalServiceFeePercent = serviceFeeValue
            val percentBase = if (serviceFeeOnItemsPlusDelivery) {
                subtotal + effectiveDeliveryFee
            } else {
                effectiveDeliveryFee
            }
            var computed = percentBase * (serviceFeeValue / 100.0)
            computed = round2(computed)
            if (serviceFeeMin != null) computed = maxOf(computed, serviceFeeMin)
            if (serviceFeeMax != null) computed = minOf(computed, serviceFeeMax)
            finalServiceFee = round2(computed)
        } else {
            finalServiceFeePercent = 0.0
            var flat = serviceFeeValue
            if (serviceFeeMin != null) flat = maxOf(flat, serviceFeeMin)
            if (serviceFeeMax != null) flat = minOf(flat, serviceFeeMax)
            finalServiceFee = round2(flat)
        }

        // 2. VAT Calculation (supports all 3 base options: serviceFee, fees/delivery+service, total/order)
        var vatAmount = 0.0
        var vatBase = 0.0
        val vatMode = vatConfig.mode.lowercase()
        val vatPercent = vatConfig.percent

        if (vatConfig.enabled && vatPercent > 0.0) {
            val normalizedBase = vatConfig.base.trim().lowercase().replace(" ", "").replace("-", "_")
            vatBase = when (normalizedBase) {
                "servicefee", "service_fee", "service", "service_only", "servicefeeonly" -> {
                    round2(finalServiceFee)
                }
                "fees", "delivery_and_service", "delivery+service", "delivery_service", "deliveryandservice", "serviceanddelivery" -> {
                    round2(effectiveDeliveryFee + finalServiceFee)
                }
                else -> {
                    // "total" / "order" / "entire_order" / "all"
                    round2((vatSubtotal + effectiveDeliveryFee + finalServiceFee - discount).coerceAtLeast(0.0))
                }
            }

            if (vatMode == "added") {
                vatAmount = round2(vatBase * vatPercent / 100.0)
            } else if (vatMode == "included") {
                vatAmount = round2(vatBase * vatPercent / (100.0 + vatPercent))
            }
        }

        // 3. Total calculation
        // total = subtotal + deliveryFee + serviceFee + tip - discount + (vat when mode is "added")
        val rawTotal = subtotal + effectiveDeliveryFee + finalServiceFee + effectiveTip - discount +
                (if (vatConfig.enabled && vatMode == "added") vatAmount else 0.0)
        val finalTotal = round2(rawTotal.coerceAtLeast(0.0))

        val orderFees = OrderFees(
            subtotal = round2(subtotal),
            deliveryFee = round2(effectiveDeliveryFee),
            serviceFee = round2(finalServiceFee),
            serviceFeeType = if (serviceFeeType.equals("percent", ignoreCase = true)) "percent" else "flat",
            serviceFeePercent = finalServiceFeePercent,
            tip = round2(effectiveTip),
            discount = round2(discount),
            vat = round2(vatAmount),
            vatPercent = vatPercent,
            vatMode = vatMode,
            vatBase = round2(vatBase),
            total = finalTotal,
            estimatedItemsTotal = round2(subtotal),
            distanceKm = distanceKm,
            distanceMethod = distanceMethod
        )

        return FeeCalculationResult(
            subtotal = round2(subtotal),
            deliveryFee = round2(effectiveDeliveryFee),
            serviceFee = round2(finalServiceFee),
            serviceFeeType = if (serviceFeeType.equals("percent", ignoreCase = true)) "percent" else "flat",
            serviceFeePercent = finalServiceFeePercent,
            tip = round2(effectiveTip),
            discount = round2(discount),
            vat = round2(vatAmount),
            vatPercent = vatPercent,
            vatMode = vatMode,
            vatBase = round2(vatBase),
            total = finalTotal,
            fees = orderFees
        )
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

    fun isSchema2TimeWindowActive(
        enabled: Boolean,
        startTime: String?,
        endTime: String?,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (!enabled) return false
        val start = parseLocalTime(startTime)
        val end = parseLocalTime(endTime)
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

    /**
     * Schema 2 Delivery Fee formula:
     * clamp(baseFare + max(0, km - includedKm) * perKm, minFee, maxFee)
     *   * (peak.multiplier if peak active)
     *   + (night.surcharge if night active)
     *   + extraStops * extraStopFee
     *   + specialRadiusExtra
     *   + optionAdjustment
     */
    fun calculateSchema2DeliveryFee(
        rate: Schema2ServiceRates,
        distanceKm: Double,
        extraStops: Int = 0,
        specialRadiusExtra: Double = 0.0,
        optionAdjustment: Double = 0.0,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Double {
        val d = rate.delivery
        val safeKm = max(0.0, distanceKm)
        val raw = d.baseFare + max(0.0, safeKm - d.includedKm) * d.perKm
        val clamped = if (d.maxFee > 0.0 && d.maxFee >= d.minFee) {
            raw.coerceIn(d.minFee, d.maxFee)
        } else {
            max(raw, d.minFee)
        }
        val peakActive = isSchema2TimeWindowActive(
            enabled = rate.peak.enabled && rate.peak.multiplier > 0.0,
            startTime = rate.peak.startTime,
            endTime = rate.peak.endTime,
            nowMillis = nowMillis,
            timezone = timezone
        )
        val afterPeak = if (peakActive) clamped * rate.peak.multiplier else clamped
        val nightActive = isSchema2TimeWindowActive(
            enabled = rate.night.enabled && rate.night.surcharge > 0.0,
            startTime = rate.night.startTime,
            endTime = rate.night.endTime,
            nowMillis = nowMillis,
            timezone = timezone
        )
        val nightAdd = if (nightActive) max(0.0, rate.night.surcharge) else 0.0
        val stopsAdd = max(0, extraStops) * max(0.0, rate.extraStopFee)
        return round2((afterPeak + nightAdd + stopsAdd + max(0.0, specialRadiusExtra) + optionAdjustment).coerceAtLeast(0.0))
    }

    /**
     * Schema 2 Pick Up Fee formula (platform-paid rider fee, never charged to customers):
     * clamp(baseFee + max(0, km - freeKm) * perKm, 0, maxFee)
     */
    fun calculateSchema2PickupFee(
        pickup: Schema2PickupConfig,
        distanceKm: Double
    ): Double {
        val safeKm = max(0.0, distanceKm)
        val raw = pickup.baseFee + max(0.0, safeKm - pickup.freeKm) * pickup.perKm
        val clamped = if (pickup.maxFee > 0.0) {
            raw.coerceIn(0.0, pickup.maxFee)
        } else {
            max(0.0, raw)
        }
        return round2(clamped)
    }

    /**
     * Schema 2 Service Fee formula:
     * flat -> value
     * percent -> value% of (itemsSubtotal + deliveryFee) for food/convenience/pabili,
     *            or value% of deliveryFee for parcel/taxi/instant
     */
    fun calculateSchema2ServiceFee(
        rate: Schema2ServiceRates,
        serviceKey: String,
        itemsSubtotal: Double,
        effectiveDeliveryFee: Double
    ): Double {
        val sf = rate.serviceFee
        val mode = sf.mode.trim().lowercase()
        var result = if (mode == "percent" || mode == "percentage") {
            val includeItems = serviceKey.lowercase() in setOf("food", "convenience", "pabili")
            val base = if (includeItems) itemsSubtotal + effectiveDeliveryFee else effectiveDeliveryFee
            round2(base * (sf.value / 100.0))
        } else {
            round2(sf.value)
        }
        sf.min?.let { result = maxOf(result, it) }
        sf.max?.let { result = minOf(result, it) }
        return round2(result.coerceAtLeast(0.0))
    }

    fun calculateForRateConfig(
        subtotal: Double,
        deliveryFee: Double,
        rateConfig: RateConfig,
        tip: Double = 0.0,
        discount: Double = 0.0,
        vatConfig: VatConfig = VatConfig(),
        isPickup: Boolean = false,
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm",
        serviceFeeOnItemsPlusDelivery: Boolean = true,
        vatSubtotal: Double = subtotal
    ): FeeCalculationResult {
        return calculateFees(
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            serviceFeeType = rateConfig.serviceFeeType,
            serviceFeeValue = if (rateConfig.serviceFeeType.equals("percent", ignoreCase = true) && rateConfig.serviceFeePercent > 0.0) rateConfig.serviceFeePercent else rateConfig.serviceFee,
            serviceFeeMin = rateConfig.serviceFeeMin,
            serviceFeeMax = rateConfig.serviceFeeMax,
            tip = tip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = isPickup,
            distanceKm = distanceKm,
            distanceMethod = distanceMethod,
            serviceFeeOnItemsPlusDelivery = serviceFeeOnItemsPlusDelivery,
            vatSubtotal = vatSubtotal
        )
    }

    fun calculateForSchema2Service(
        itemsSubtotal: Double,
        effectiveDeliveryFee: Double,
        rate: Schema2ServiceRates,
        serviceKey: String,
        tip: Double = 0.0,
        discount: Double = 0.0,
        vatConfig: VatConfig = VatConfig(),
        isPickup: Boolean = false,
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm",
        vatSubtotal: Double = itemsSubtotal
    ): FeeCalculationResult {
        val includeItems = serviceKey.lowercase() in setOf("food", "convenience", "pabili")
        return calculateFees(
            subtotal = itemsSubtotal,
            deliveryFee = effectiveDeliveryFee,
            serviceFeeType = rate.serviceFee.mode,
            serviceFeeValue = rate.serviceFee.value,
            serviceFeeMin = rate.serviceFee.min,
            serviceFeeMax = rate.serviceFee.max,
            tip = tip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = isPickup,
            distanceKm = distanceKm,
            distanceMethod = distanceMethod,
            serviceFeeOnItemsPlusDelivery = includeItems,
            vatSubtotal = vatSubtotal
        )
    }

    fun calculateForRideRateConfig(
        fare: Double,
        rideRateConfig: RideRateConfig,
        tip: Double = 0.0,
        discount: Double = 0.0,
        vatConfig: VatConfig = VatConfig(),
        distanceKm: Double = 0.0
    ): FeeCalculationResult {
        return calculateFees(
            subtotal = 0.0,
            deliveryFee = fare,
            serviceFeeType = rideRateConfig.serviceFeeType,
            serviceFeeValue = if (rideRateConfig.serviceFeeType.equals("percent", ignoreCase = true) && rideRateConfig.serviceFeePercent > 0.0) rideRateConfig.serviceFeePercent else rideRateConfig.serviceFee,
            serviceFeeMin = rideRateConfig.serviceFeeMin,
            serviceFeeMax = rideRateConfig.serviceFeeMax,
            tip = tip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = false,
            distanceKm = distanceKm,
            serviceFeeOnItemsPlusDelivery = false,
            vatSubtotal = 0.0
        )
    }

    fun buildPricingSnapshot(
        schema: Int = 2,
        ratesVersion: Int = 0,
        serviceKey: String,
        vehicle: String? = null,
        deliveryFee: Double,
        serviceFee: Double,
        tip: Double,
        vat: Double,
        itemsSubtotal: Double,
        total: Double,
        payer: String = "customer",
        merchantCommissionRate: Double? = null
    ): PricingSnapshot {
        return PricingSnapshot(
            schema = schema,
            ratesVersion = ratesVersion,
            serviceKey = serviceKey,
            vehicle = vehicle,
            deliveryFee = round2(deliveryFee),
            serviceFee = round2(serviceFee),
            tip = round2(tip),
            vat = round2(vat),
            itemsSubtotal = round2(itemsSubtotal),
            total = round2(total),
            payer = payer,
            merchantCommissionRate = merchantCommissionRate
        )
    }
}
