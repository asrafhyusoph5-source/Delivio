package com.example.util

import com.example.data.model.MenuSchedule
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantMenu
import com.example.data.model.MenuItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object MenuAvailabilityUtil {

    fun parseMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val clean = timeStr.trim()
        val isAm = clean.contains("am", ignoreCase = true)
        val isPm = clean.contains("pm", ignoreCase = true)
        val timeOnly = clean.replace(Regex("(?i)[a-z]+"), "").trim()
        val parts = timeOnly.split(":")
        if (parts.isEmpty()) return null
        var h = parts[0].toIntOrNull() ?: return null
        val m = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        if (isPm && h < 12) h += 12
        if (isAm && h == 12) h = 0
        return (h * 60 + m).coerceIn(0, 1439)
    }

    fun isDayInSchedule(dayOfWeek: DayOfWeek, scheduleDays: List<String>): Boolean {
        if (scheduleDays.isEmpty()) return true
        val dayLower = dayOfWeek.name.lowercase(Locale.ROOT)
        val dayAbbr = dayLower.take(3)
        val dayNum = dayOfWeek.value.toString()
        return scheduleDays.any { s ->
            val target = s.trim().lowercase(Locale.ROOT)
            target == dayLower || target == dayAbbr || target == dayNum
        }
    }

    /**
     * availableNow(menu): active and (no schedule, or today is in schedule.days and now
     * (settings/platform.timezone, default "Asia/Manila") is in [start, end]; overnight windows also check yesterday).
     */
    fun isMenuAvailable(
        menu: MerchantMenu,
        targetMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (!menu.active) return false
        val schedule = menu.schedule
        if (schedule == null || (schedule.start.isBlank() && schedule.end.isBlank())) {
            // no schedule
            return true
        }

        val startMinutes = parseMinutes(schedule.start)
        val endMinutes = parseMinutes(schedule.end)
        if (startMinutes == null || endMinutes == null) {
            return true
        }

        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMillis), zoneId)
        val curMinutes = zonedDateTime.hour * 60 + zonedDateTime.minute
        val today = zonedDateTime.dayOfWeek
        val yesterday = today.minus(1)

        return if (startMinutes <= endMinutes) {
            // Same-day window [start, end]
            isDayInSchedule(today, schedule.days) && curMinutes in startMinutes..endMinutes
        } else {
            // Overnight window (e.g. 22:00 to 04:00)
            val todayMatch = isDayInSchedule(today, schedule.days) && curMinutes >= startMinutes
            val yesterdayMatch = isDayInSchedule(yesterday, schedule.days) && curMinutes <= endMinutes
            todayMatch || yesterdayMatch
        }
    }

    /**
     * An item is orderable when it is in stock and any of its menuIds
     * (missing or empty means ["main"]; a shop with no menus at all means everything is available) is available now.
     */
    fun isItemOrderable(
        item: MerchantItem,
        menus: List<MerchantMenu>,
        targetMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (!item.inStock || !item.isAvailable) return false
        if (menus.isEmpty()) return true

        val effectiveMenuIds = if (item.menuIds.isEmpty()) listOf("main") else item.menuIds
        return effectiveMenuIds.any { menuId ->
            menus.any { menu ->
                (menu.id.equals(menuId, ignoreCase = true) || menu.name.equals(menuId, ignoreCase = true)) &&
                isMenuAvailable(menu, targetMillis, timezone)
            }
        }
    }

    fun isMenuItemOrderable(
        item: MenuItem,
        menus: List<MerchantMenu>,
        targetMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (menus.isEmpty()) return true

        val effectiveMenuIds = if (item.menuIds.isEmpty()) listOf("main") else item.menuIds
        return effectiveMenuIds.any { menuId ->
            menus.any { menu ->
                (menu.id.equals(menuId, ignoreCase = true) || menu.name.equals(menuId, ignoreCase = true)) &&
                isMenuAvailable(menu, targetMillis, timezone)
            }
        }
    }

    private fun formatDaysText(days: List<String>): String {
        if (days.isEmpty() || days.size >= 7) return ""
        val normalized = days.map { it.trim().lowercase(Locale.ROOT).take(3) }
        val weekdays = listOf("mon", "tue", "wed", "thu", "fri")
        val weekends = listOf("sat", "sun")

        if (normalized.size == 5 && normalized.containsAll(weekdays)) {
            return "Mon-Fri"
        }
        if (normalized.size == 2 && normalized.containsAll(weekends)) {
            return "Sat-Sun"
        }
        return normalized.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun getAvailabilityLabel(
        menuIds: List<String>,
        menus: List<MerchantMenu>
    ): String {
        val effectiveMenuIds = if (menuIds.isEmpty()) listOf("main") else menuIds
        val matchingMenu = menus.firstOrNull { menu ->
            effectiveMenuIds.any { id -> menu.id.equals(id, ignoreCase = true) || menu.name.equals(id, ignoreCase = true) }
        }
        val schedule = matchingMenu?.schedule
        if (schedule == null || schedule.start.isBlank() || schedule.end.isBlank()) {
            return "Currently unavailable"
        }
        val startFormatted = BusinessHoursUtil.formatTime(schedule.start)
        val endFormatted = BusinessHoursUtil.formatTime(schedule.end)
        val timeText = "$startFormatted - $endFormatted"
        val daysText = formatDaysText(schedule.days)
        return if (daysText.isBlank()) {
            "Available $timeText"
        } else {
            "Available $daysText $timeText"
        }
    }

    fun getAvailabilityLabel(item: MerchantItem, menus: List<MerchantMenu>): String {
        return getAvailabilityLabel(item.menuIds, menus)
    }

    fun getAvailabilityLabel(item: MenuItem, menus: List<MerchantMenu>): String {
        return getAvailabilityLabel(item.menuIds, menus)
    }
}
