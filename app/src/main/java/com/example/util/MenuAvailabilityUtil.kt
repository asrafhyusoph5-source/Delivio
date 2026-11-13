package com.example.util

import com.example.data.model.ItemAvailability
import com.example.data.model.MerchantItem
import com.example.data.model.MerchantMenu
import com.example.data.model.MenuItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

    fun toDayKey(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "mon"
            DayOfWeek.TUESDAY -> "tue"
            DayOfWeek.WEDNESDAY -> "wed"
            DayOfWeek.THURSDAY -> "thu"
            DayOfWeek.FRIDAY -> "fri"
            DayOfWeek.SATURDAY -> "sat"
            DayOfWeek.SUNDAY -> "sun"
        }
    }

    fun isDayInSchedule(dayOfWeek: DayOfWeek, scheduleDays: List<String>): Boolean {
        if (scheduleDays.isEmpty()) return true
        val key = toDayKey(dayOfWeek)
        val full = dayOfWeek.name.lowercase(Locale.ROOT)
        return scheduleDays.any { s ->
            val target = s.trim().lowercase(Locale.ROOT)
            target == key || target == full
        }
    }

    fun parseLocalDate(value: Any?, zoneId: ZoneId): LocalDate? {
        if (value == null) return null
        if (value is LocalDate) return value
        if (value is com.google.firebase.Timestamp) {
            return value.toDate().toInstant().atZone(zoneId).toLocalDate()
        }
        if (value is java.util.Date) {
            return value.toInstant().atZone(zoneId).toLocalDate()
        }
        if (value is Number) {
            return Instant.ofEpochMilli(value.toLong()).atZone(zoneId).toLocalDate()
        }
        val s = value.toString().trim()
        if (s.isBlank()) return null

        // Try yyyy-MM-dd
        try {
            return LocalDate.parse(s.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {}

        // Try yyyy/MM/dd
        try {
            return LocalDate.parse(s.take(10), DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US))
        } catch (_: Exception) {}

        // Try MMM d, yyyy (e.g. Nov 20, 2026)
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
        } catch (_: Exception) {}

        // Try MMM d (e.g. Nov 20) with current year
        try {
            val parsed = MonthDay.parse(s, DateTimeFormatter.ofPattern("MMM d", Locale.US))
            val currentYear = LocalDate.now(zoneId).year
            return parsed.atYear(currentYear)
        } catch (_: Exception) {}

        return null
    }

    fun isItemAvailabilitySatisfied(
        availability: ItemAvailability?,
        targetMillis: Long,
        timezone: String
    ): Boolean {
        if (availability == null) return true

        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMillis), zoneId)
        val todayDate = zonedDateTime.toLocalDate()
        val todayDayOfWeek = zonedDateTime.dayOfWeek
        val curMinutes = zonedDateTime.hour * 60 + zonedDateTime.minute

        fun isDateInside(date: LocalDate): Boolean {
            val fromDate = parseLocalDate(availability.dateFrom, zoneId)
            val toDate = parseLocalDate(availability.dateTo, zoneId)
            if (fromDate != null && date.isBefore(fromDate)) return false
            if (toDate != null && date.isAfter(toDate)) return false
            return true
        }

        val startMinutes = parseMinutes(availability.start)
        val endMinutes = parseMinutes(availability.end)

        return if (startMinutes != null && endMinutes != null) {
            if (startMinutes <= endMinutes) {
                // Same-day window
                isDateInside(todayDate) &&
                isDayInSchedule(todayDayOfWeek, availability.days) &&
                curMinutes in startMinutes..endMinutes
            } else {
                // Overnight window (e.g. 22:00 to 04:00)
                val todayMatch = isDateInside(todayDate) &&
                    isDayInSchedule(todayDayOfWeek, availability.days) &&
                    curMinutes >= startMinutes
                val yesterdayDate = todayDate.minusDays(1)
                val yesterdayDayOfWeek = todayDayOfWeek.minus(1)
                val yesterdayMatch = isDateInside(yesterdayDate) &&
                    isDayInSchedule(yesterdayDayOfWeek, availability.days) &&
                    curMinutes <= endMinutes
                todayMatch || yesterdayMatch
            }
        } else {
            // No time window specified, check date range and days of week
            isDateInside(todayDate) && isDayInSchedule(todayDayOfWeek, availability.days)
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
        val schedule = menu.schedule ?: return true

        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMillis), zoneId)
        val today = zonedDateTime.dayOfWeek
        val yesterday = today.minus(1)

        val startMinutes = parseMinutes(schedule.start)
        val endMinutes = parseMinutes(schedule.end)
        if (startMinutes == null || endMinutes == null) {
            return isDayInSchedule(today, schedule.days)
        }

        val curMinutes = zonedDateTime.hour * 60 + zonedDateTime.minute

        return if (startMinutes <= endMinutes) {
            isDayInSchedule(today, schedule.days) && curMinutes in startMinutes..endMinutes
        } else {
            val todayMatch = isDayInSchedule(today, schedule.days) && curMinutes >= startMinutes
            val yesterdayMatch = isDayInSchedule(yesterday, schedule.days) && curMinutes <= endMinutes
            todayMatch || yesterdayMatch
        }
    }

    /**
     * An item is orderable when it is in stock and any of its menuIds is available now,
     * and its item availability (days, hours, date range) is satisfied.
     */
    fun isItemOrderable(
        item: MerchantItem,
        menus: List<MerchantMenu>,
        targetMillis: Long = System.currentTimeMillis(),
        timezone: String = "Asia/Manila"
    ): Boolean {
        if (!item.inStock || !item.isAvailable) return false

        // 1. Check item-level availability
        if (!isItemAvailabilitySatisfied(item.availability, targetMillis, timezone)) {
            return false
        }

        // 2. Check menu-level availability
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
        // 1. Check item-level availability
        if (!isItemAvailabilitySatisfied(item.availability, targetMillis, timezone)) {
            return false
        }

        // 2. Check menu-level availability
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

    private fun formatDateRangeLabel(dateFrom: String?, dateTo: String?, zoneId: ZoneId): String {
        val from = parseLocalDate(dateFrom, zoneId)
        val to = parseLocalDate(dateTo, zoneId)
        return when {
            from != null && to != null -> {
                if (from.year == to.year) {
                    val f = DateTimeFormatter.ofPattern("MMM d", Locale.US).format(from)
                    val t = DateTimeFormatter.ofPattern("MMM d", Locale.US).format(to)
                    "$f - $t"
                } else {
                    val f = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(from)
                    val t = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(to)
                    "$f - $t"
                }
            }
            from != null -> {
                val f = DateTimeFormatter.ofPattern("MMM d", Locale.US).format(from)
                "from $f"
            }
            to != null -> {
                val t = DateTimeFormatter.ofPattern("MMM d", Locale.US).format(to)
                "until $t"
            }
            else -> ""
        }
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
        if (schedule == null || (schedule.start.isBlank() && schedule.end.isBlank() && schedule.days.isEmpty())) {
            return "Currently unavailable"
        }
        val daysText = formatDaysText(schedule.days)
        val timeText = if (schedule.start.isNotBlank() && schedule.end.isNotBlank()) {
            val startFormatted = BusinessHoursUtil.formatTime(schedule.start)
            val endFormatted = BusinessHoursUtil.formatTime(schedule.end)
            "$startFormatted - $endFormatted"
        } else ""

        return when {
            daysText.isNotBlank() && timeText.isNotBlank() -> "Available $daysText $timeText"
            daysText.isNotBlank() -> "Available $daysText"
            timeText.isNotBlank() -> "Available $timeText"
            else -> "Currently unavailable"
        }
    }

    fun getAvailabilityLabel(
        item: MerchantItem,
        menus: List<MerchantMenu>,
        timezone: String = "Asia/Manila"
    ): String {
        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        if (item.availability != null) {
            val dateRangeText = formatDateRangeLabel(item.availability.dateFrom, item.availability.dateTo, zoneId)
            val daysText = formatDaysText(item.availability.days)
            val timeText = if (item.availability.start.isNotBlank() && item.availability.end.isNotBlank()) {
                val startFormatted = BusinessHoursUtil.formatTime(item.availability.start)
                val endFormatted = BusinessHoursUtil.formatTime(item.availability.end)
                "$startFormatted - $endFormatted"
            } else ""

            val parts = mutableListOf<String>()
            if (dateRangeText.isNotBlank()) parts.add(dateRangeText)
            if (daysText.isNotBlank()) parts.add(daysText)
            if (timeText.isNotBlank()) parts.add(timeText)

            if (parts.isNotEmpty()) {
                return "Available ${parts.joinToString(", ")}"
            }
        }

        return getAvailabilityLabel(item.menuIds, menus)
    }

    fun getAvailabilityLabel(
        item: MenuItem,
        menus: List<MerchantMenu>,
        timezone: String = "Asia/Manila"
    ): String {
        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        if (item.availability != null) {
            val dateRangeText = formatDateRangeLabel(item.availability.dateFrom, item.availability.dateTo, zoneId)
            val daysText = formatDaysText(item.availability.days)
            val timeText = if (item.availability.start.isNotBlank() && item.availability.end.isNotBlank()) {
                val startFormatted = BusinessHoursUtil.formatTime(item.availability.start)
                val endFormatted = BusinessHoursUtil.formatTime(item.availability.end)
                "$startFormatted - $endFormatted"
            } else ""

            val parts = mutableListOf<String>()
            if (dateRangeText.isNotBlank()) parts.add(dateRangeText)
            if (daysText.isNotBlank()) parts.add(daysText)
            if (timeText.isNotBlank()) parts.add(timeText)

            if (parts.isNotEmpty()) {
                return "Available ${parts.joinToString(", ")}"
            }
        }

        return getAvailabilityLabel(item.menuIds, menus)
    }

    fun getAvailabilityLabel(
        availability: ItemAvailability?,
        timezone: String = "Asia/Manila"
    ): String? {
        if (availability == null) return null
        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }

        val dateRangeText = formatDateRangeLabel(availability.dateFrom, availability.dateTo, zoneId)
        val daysText = formatDaysText(availability.days)
        val timeText = if (availability.start.isNotBlank() && availability.end.isNotBlank()) {
            val startFormatted = BusinessHoursUtil.formatTime(availability.start)
            val endFormatted = BusinessHoursUtil.formatTime(availability.end)
            "$startFormatted - $endFormatted"
        } else ""

        val parts = mutableListOf<String>()
        if (dateRangeText.isNotBlank()) parts.add(dateRangeText)
        if (daysText.isNotBlank()) parts.add(daysText)
        if (timeText.isNotBlank()) parts.add(timeText)

        return if (parts.isNotEmpty()) "Available ${parts.joinToString(", ")}" else null
    }
}
