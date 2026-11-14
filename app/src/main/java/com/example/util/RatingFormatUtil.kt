package com.example.util

import java.util.Locale

/**
 * Real ratings helpers following the Delivio DATA CONTRACT:
 * - Computed strictly from ratingSum / ratingCount when ratingCount > 0.
 * - Ignore legacy merchant rating/ratingAvg/reviewCount fields.
 * - Count display: "100+" for 100..999, "1k+" for >= 1000, exact number for 1..99.
 * - When ratingCount == 0: show a "New" chip with no stars or number.
 */
object RatingFormatUtil {

    fun calculateAverage(ratingSum: Double, ratingCount: Int): Double {
        if (ratingCount <= 0) return 0.0
        val avg = ratingSum / ratingCount.toDouble()
        return avg.coerceIn(0.0, 5.0)
    }

    fun formatAverage(ratingSum: Double, ratingCount: Int): String {
        if (ratingCount <= 0) return ""
        return String.format(Locale.US, "%.1f", calculateAverage(ratingSum, ratingCount))
    }

    fun formatCount(ratingCount: Int): String {
        return when {
            ratingCount <= 0 -> ""
            ratingCount >= 1000 -> "1k+"
            ratingCount >= 100 -> "100+"
            else -> ratingCount.toString()
        }
    }

    fun formatCountBracketed(ratingCount: Int): String {
        val label = formatCount(ratingCount)
        return if (label.isEmpty()) "" else "($label)"
    }
}
