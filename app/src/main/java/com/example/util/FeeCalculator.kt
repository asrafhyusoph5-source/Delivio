package com.example.util

import com.example.data.model.OrderFees
import com.example.data.model.RateConfig
import com.example.data.model.RideRateConfig
import com.example.data.model.VatConfig
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
        distanceMethod: String = "osrm"
    ): FeeCalculationResult {
        val effectiveDeliveryFee = if (isPickup) 0.0 else maxOf(0.0, deliveryFee)
        val effectiveTip = if (isPickup) 0.0 else maxOf(0.0, tip)

        // 1. Calculate service fee
        val finalServiceFee: Double
        val finalServiceFeePercent: Double
        if (serviceFeeType.equals("percent", ignoreCase = true)) {
            finalServiceFeePercent = serviceFeeValue
            var computed = (subtotal + effectiveDeliveryFee) * (serviceFeeValue / 100.0)
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

        // 2. VAT Calculation
        var vatAmount = 0.0
        var vatBase = 0.0
        val vatMode = vatConfig.mode.lowercase()
        val vatPercent = vatConfig.percent

        if (vatConfig.enabled && vatPercent > 0.0) {
            vatBase = if (vatConfig.base.equals("fees", ignoreCase = true)) {
                round2(effectiveDeliveryFee + finalServiceFee)
            } else {
                round2((subtotal + effectiveDeliveryFee + finalServiceFee - discount).coerceAtLeast(0.0))
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

    fun calculateForRateConfig(
        subtotal: Double,
        deliveryFee: Double,
        rateConfig: RateConfig,
        tip: Double = 0.0,
        discount: Double = 0.0,
        vatConfig: VatConfig = VatConfig(),
        isPickup: Boolean = false,
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm"
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
            distanceMethod = distanceMethod
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
            subtotal = fare,
            deliveryFee = 0.0,
            serviceFeeType = rideRateConfig.serviceFeeType,
            serviceFeeValue = if (rideRateConfig.serviceFeeType.equals("percent", ignoreCase = true) && rideRateConfig.serviceFeePercent > 0.0) rideRateConfig.serviceFeePercent else rideRateConfig.serviceFee,
            serviceFeeMin = rideRateConfig.serviceFeeMin,
            serviceFeeMax = rideRateConfig.serviceFeeMax,
            tip = tip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = false,
            distanceKm = distanceKm
        )
    }
}
