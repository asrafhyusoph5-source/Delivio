package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatPrice(amount: Double): String {
        return String.format(Locale.US, "₱%,.2f", amount)
    }

    fun formatItemSize(itemSize: String?): String {
        if (itemSize.isNullOrBlank()) return ""
        val clean = itemSize.trim()
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.US, "%,.2f", amount)
    }

    fun formatNumber(amount: Double): String {
        return String.format(Locale.US, "%,.2f", amount)
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        if (diffMs < 0) return formatTime(timestamp)
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHours = diffMin / 60
        val diffDays = diffHours / 24

        return when {
            diffMin < 1 -> "Just now"
            diffMin == 1L -> "1 min ago"
            diffMin < 60 -> "$diffMin min ago"
            diffHours == 1L -> "1 hour ago"
            diffHours < 24 -> "$diffHours hours ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7 -> "$diffDays days ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
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
