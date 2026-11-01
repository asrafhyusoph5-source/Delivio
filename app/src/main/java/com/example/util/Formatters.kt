package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatPrice(amount: Double): String {
        return String.format(Locale.US, "₱%.2f", amount)
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }

    fun formatNumber(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0) return "--:--"
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTimestamp(timestamp: Long): String {
        return formatDate(timestamp)
    }

    fun formatDisplayName(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "Delivio Customer"
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> {
                val firstName = parts.first()
                val lastInitial = parts.last().first().uppercaseChar()
                "$firstName $lastInitial."
            }
            parts.size == 1 -> parts.first()
            else -> "Delivio Customer"
        }
    }
}
