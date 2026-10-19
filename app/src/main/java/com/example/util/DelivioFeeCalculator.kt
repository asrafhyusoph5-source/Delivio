package com.example.util

import com.example.data.model.OrderFees
import com.example.data.model.RateConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object DelivioFeeCalculator {

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = r * c
        return if (dist < 0.2) 1.5 else ((dist * 10).toInt() / 10.0)
    }

    fun computeFees(
        rateConfig: RateConfig,
        subtotal: Double,
        distanceKm: Double,
        discount: Double = 0.0
    ): Pair<OrderFees, FeeBreakdown> {
        val baseFare = rateConfig.baseFare
        val includedKm = rateConfig.includedKm
        val extraKm = max(0.0, distanceKm - includedKm)
        val extraFare = extraKm * rateConfig.perKmRate

        val computedDeliveryFee = (baseFare + extraFare)
            .coerceIn(rateConfig.minFee, rateConfig.maxFee) + rateConfig.surcharge
        val serviceFee = rateConfig.serviceFee
        val total = max(0.0, subtotal + computedDeliveryFee + serviceFee - discount)

        val fees = OrderFees(
            subtotal = subtotal,
            deliveryFee = computedDeliveryFee,
            serviceFee = serviceFee,
            discount = discount,
            total = total
        )

        val breakdown = FeeBreakdown(
            distanceKm = distanceKm,
            baseFare = baseFare,
            includedKm = includedKm,
            extraKm = extraKm,
            perKmRate = rateConfig.perKmRate,
            serviceFee = serviceFee,
            surcharge = rateConfig.surcharge,
            finalDeliveryFee = computedDeliveryFee
        )

        return fees to breakdown
    }
}

data class FeeBreakdown(
    val distanceKm: Double,
    val baseFare: Double,
    val includedKm: Double,
    val extraKm: Double,
    val perKmRate: Double,
    val serviceFee: Double,
    val surcharge: Double,
    val finalDeliveryFee: Double
)
