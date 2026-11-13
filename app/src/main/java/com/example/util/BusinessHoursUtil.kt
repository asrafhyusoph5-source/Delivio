package com.example.util

import com.example.data.model.HolidayEntry
import com.example.data.model.Restaurant
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object BusinessHoursUtil {

    private fun getDayKey(dayOfWeek: java.time.DayOfWeek): String {
        return when (dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "mon"
            java.time.DayOfWeek.TUESDAY -> "tue"
            java.time.DayOfWeek.WEDNESDAY -> "wed"
            java.time.DayOfWeek.THURSDAY -> "thu"
            java.time.DayOfWeek.FRIDAY -> "fri"
            java.time.DayOfWeek.SATURDAY -> "sat"
            java.time.DayOfWeek.SUNDAY -> "sun"
        }
    }

    private data class DayWindow(
        val closed: Boolean,
        val openMinutes: Int,
        val closeMinutes: Int,
        val rawOpen: String,
        val rawClose: String
    )

    private fun parseMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val parts = timeStr.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun extractDayWindow(hours: Map<String, Any>?, dayKey: String): DayWindow? {
        if (hours == null) return null
        val fullDayName = when (dayKey) {
            "mon" -> "monday"
            "tue" -> "tuesday"
            "wed" -> "wednesday"
            "thu" -> "thursday"
            "fri" -> "friday"
            "sat" -> "saturday"
            "sun" -> "sunday"
            else -> dayKey
        }
        val dayData = (hours[dayKey] ?: hours[fullDayName]) as? Map<*, *> ?: return null
        val isClosed = (dayData["closed"] as? Boolean)
            ?: (dayData["is_closed"] as? Boolean)
            ?: (dayData["isClosed"] as? Boolean)
            ?: false

        val openStr = (dayData["open"] as? String) ?: (dayData["start"] as? String) ?: "08:00"
        val closeStr = (dayData["close"] as? String) ?: (dayData["end"] as? String) ?: "22:00"
        val openMin = parseMinutes(openStr) ?: (8 * 60)
        val closeMin = parseMinutes(closeStr) ?: (22 * 60)

        return DayWindow(
            closed = isClosed,
            openMinutes = openMin,
            closeMinutes = closeMin,
            rawOpen = openStr,
            rawClose = closeStr
        )
    }

    private fun parseLocalDateSafe(raw: String?): LocalDate? {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return null
        return try {
            // Support ISO date ("YYYY-MM-DD") or prefix ("YYYY-MM-DDTHH:mm...")
            val datePart = if (clean.length >= 10) clean.substring(0, 10) else clean
            LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    fun findHolidayForDate(date: LocalDate, holidays: List<HolidayEntry>): HolidayEntry? {
        if (holidays.isEmpty()) return null
        val iso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        for (h in holidays) {
            val single = parseLocalDateSafe(h.date)
            if (single != null && single == date) return h
            if (h.date.trim() == iso) return h

            val from = parseLocalDateSafe(h.dateFrom)
            val to = parseLocalDateSafe(h.dateTo)
            if (from != null && to != null && !date.isBefore(from) && !date.isAfter(to)) return h
            if (from != null && to == null && date == from) return h
        }
        return null
    }

    private fun resolveZoneId(timezone: String): ZoneId {
        return try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }
    }

    fun effectiveOpen(
        status: String,
        isOpen: Boolean,
        isPaused: Boolean,
        pauseUntilTime: Long,
        closedUntil: Long? = null,
        openUntil: Long? = null,
        autoSchedule: Boolean,
        hours: Map<String, Any>?,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        holidays: List<HolidayEntry> = emptyList(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Boolean {
        // 1. Must be active
        if (!status.trim().equals("active", ignoreCase = true)) {
            return false
        }
        // 2. Manual closure: closedUntil in the future always means Closed
        if (closedUntil != null && nowMillis < closedUntil) {
            return false
        }
        // 3. Pause
        if (isPaused && nowMillis < pauseUntilTime) {
            return false
        }

        val zoneId = resolveZoneId(timezone)
        val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val todayDate = nowZoned.toLocalDate()
        val curMinutes = nowZoned.hour * 60 + nowZoned.minute

        // 4. Holidays (merchant holidays take precedence over platform holidays if both defined for date)
        val todayHoliday = findHolidayForDate(todayDate, holidays) ?: findHolidayForDate(todayDate, platformHolidays)
        if (todayHoliday != null) {
            if (todayHoliday.closed) {
                return false
            }
            val hOpen = parseMinutes(todayHoliday.open)
            val hClose = parseMinutes(todayHoliday.close)
            if (hOpen != null && hClose != null) {
                return if (hClose >= hOpen) {
                    curMinutes in hOpen..hClose
                } else {
                    curMinutes >= hOpen
                }
            }
        }

        // 5. Force open until
        if (openUntil != null && nowMillis < openUntil) {
            return true
        }

        // 6. Auto schedule + business hours
        if (autoSchedule && !hours.isNullOrEmpty()) {
            val todayDay = nowZoned.dayOfWeek
            val yesterdayDay = todayDay.minus(1)

            val todayKey = getDayKey(todayDay)
            val yesterdayKey = getDayKey(yesterdayDay)

            // Check yesterday's overnight window passing midnight into today (unless yesterday was a closed holiday)
            val yHoliday = findHolidayForDate(todayDate.minusDays(1), holidays)
                ?: findHolidayForDate(todayDate.minusDays(1), platformHolidays)
            if (yHoliday == null || !yHoliday.closed) {
                val yWindow = extractDayWindow(hours, yesterdayKey)
                if (yWindow != null && !yWindow.closed && yWindow.closeMinutes < yWindow.openMinutes) {
                    if (curMinutes <= yWindow.closeMinutes) {
                        return true
                    }
                }
            }

            // Check today's window
            val tWindow = extractDayWindow(hours, todayKey)
            if (tWindow != null && !tWindow.closed) {
                if (tWindow.closeMinutes >= tWindow.openMinutes) {
                    if (curMinutes in tWindow.openMinutes..tWindow.closeMinutes) {
                        return true
                    }
                } else {
                    // Window passes midnight into tomorrow
                    if (curMinutes >= tWindow.openMinutes) {
                        return true
                    }
                }
            }
            return false
        }

        // 7. Manual open/closed toggle fallback
        return isOpen
    }

    fun effectiveOpen(
        shop: Restaurant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Boolean {
        return effectiveOpen(
            status = shop.status,
            isOpen = shop.isOpen,
            isPaused = shop.isPaused,
            pauseUntilTime = shop.pauseUntilTime,
            closedUntil = shop.closedUntil,
            openUntil = shop.openUntil,
            autoSchedule = shop.autoSchedule,
            hours = shop.hours,
            timezone = timezone,
            nowMillis = nowMillis,
            holidays = shop.holidays,
            platformHolidays = platformHolidays
        )
    }

    fun effectiveOpen(
        merchant: com.example.data.model.Merchant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Boolean {
        return effectiveOpen(
            status = merchant.status,
            isOpen = merchant.isOpen,
            isPaused = merchant.isPaused,
            pauseUntilTime = merchant.pauseUntilTime,
            closedUntil = merchant.closedUntil,
            openUntil = merchant.openUntil,
            autoSchedule = merchant.autoSchedule,
            hours = merchant.hours,
            timezone = timezone,
            nowMillis = nowMillis,
            holidays = merchant.holidays,
            platformHolidays = platformHolidays
        )
    }

    fun getNextOpenText(
        shop: Restaurant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): String {
        if (effectiveOpen(shop, timezone, nowMillis, platformHolidays)) {
            return "Open"
        }
        val zoneId = resolveZoneId(timezone)
        val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val todayDate = nowZoned.toLocalDate()
        val todayHoliday = findHolidayForDate(todayDate, shop.holidays) ?: findHolidayForDate(todayDate, platformHolidays)

        val closedUntil = shop.closedUntil
        if (closedUntil != null && nowMillis < closedUntil) {
            val cuZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(closedUntil), zoneId)
            val hhmm = String.format(Locale.US, "%02d:%02d", cuZoned.hour, cuZoned.minute)
            return if (cuZoned.toLocalDate() == todayDate) {
                "Opens ${formatTime(hhmm)}"
            } else {
                val month = cuZoned.month.name.take(3).lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                "Closed until $month ${cuZoned.dayOfMonth}"
            }
        }

        if (todayHoliday != null && todayHoliday.closed) {
            return if (todayHoliday.name.isNotBlank()) "Closed (${todayHoliday.name})" else "Closed (Holiday)"
        }

        val hours = shop.hours
        if (shop.autoSchedule && !hours.isNullOrEmpty()) {
            val curMinutes = nowZoned.hour * 60 + nowZoned.minute
            val todayKey = getDayKey(nowZoned.dayOfWeek)
            val tWindow = extractDayWindow(hours, todayKey)
            if (tWindow != null && !tWindow.closed && curMinutes < tWindow.openMinutes) {
                return "Opens ${formatTime(tWindow.rawOpen)}"
            }

            for (i in 1..7) {
                val nextDate = todayDate.plusDays(i.toLong())
                val nextHoliday = findHolidayForDate(nextDate, shop.holidays) ?: findHolidayForDate(nextDate, platformHolidays)
                if (nextHoliday != null && nextHoliday.closed) continue
                val nextDay = nextDate.dayOfWeek
                val nextKey = getDayKey(nextDay)
                val nWindow = extractDayWindow(hours, nextKey)
                if (nWindow != null && !nWindow.closed) {
                    val dayName = if (i == 1) "tomorrow" else nextDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                    return "Opens $dayName ${formatTime(nWindow.rawOpen)}"
                }
            }
        }
        return shop.nextOpenTime.ifBlank { "Closed" }
    }

    fun formatTime(hhmm: String): String {
        val parts = hhmm.split(":")
        if (parts.size < 2) return hhmm
        val h = parts[0].toIntOrNull() ?: return hhmm
        val m = parts[1].toIntOrNull() ?: 0
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else (h % 12)
        return String.format(Locale.US, "%d:%02d %s", h12, m, amPm)
    }

    fun getTodayHoursSummary(
        shop: Restaurant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): String {
        val zoneId = resolveZoneId(timezone)
        val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val todayDate = nowZoned.toLocalDate()
        val todayHoliday = findHolidayForDate(todayDate, shop.holidays) ?: findHolidayForDate(todayDate, platformHolidays)

        val closedUntil = shop.closedUntil
        if (closedUntil != null && nowMillis < closedUntil) {
            return "Closed temporarily"
        }
        if (todayHoliday != null && todayHoliday.closed) {
            return if (todayHoliday.name.isNotBlank()) "Closed (${todayHoliday.name})" else "Closed (Holiday)"
        }

        val hours = shop.hours
        if (!hours.isNullOrEmpty()) {
            val todayKey = getDayKey(nowZoned.dayOfWeek)
            val tWindow = extractDayWindow(hours, todayKey)
            if (tWindow != null) {
                return if (tWindow.closed) {
                    "Closed today"
                } else {
                    "${formatTime(tWindow.rawOpen)} - ${formatTime(tWindow.rawClose)}"
                }
            }
        }
        return if (effectiveOpen(shop, timezone, nowMillis, platformHolidays)) {
            "Open now"
        } else {
            shop.nextOpenTime.ifBlank { "Closed" }
        }
    }

    fun isScheduleTimeValid(
        shop: Restaurant?,
        scheduledMillis: Long,
        timezone: String = "Asia/Manila",
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Pair<Boolean, String?> {
        if (shop == null) return Pair(true, null)

        val closedUntil = shop.closedUntil
        if (closedUntil != null && scheduledMillis < closedUntil) {
            return Pair(false, "Merchant is temporarily closed at the selected time.")
        }

        val zoneId = resolveZoneId(timezone)
        val targetZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(scheduledMillis), zoneId)
        val targetDate = targetZoned.toLocalDate()
        val targetDay = targetZoned.dayOfWeek
        val yesterdayDay = targetDay.minus(1)
        val targetMinutes = targetZoned.hour * 60 + targetZoned.minute

        val holiday = findHolidayForDate(targetDate, shop.holidays) ?: findHolidayForDate(targetDate, platformHolidays)
        if (holiday != null) {
            if (holiday.closed) {
                val label = holiday.name.ifBlank { "a holiday" }
                return Pair(false, "Merchant is closed on $label.")
            }
            val hOpen = parseMinutes(holiday.open)
            val hClose = parseMinutes(holiday.close)
            if (hOpen != null && hClose != null) {
                val valid = if (hClose >= hOpen) targetMinutes in hOpen..hClose else targetMinutes >= hOpen
                return if (valid) Pair(true, null) else Pair(
                    false,
                    "Selected time is outside holiday hours (${formatTime(holiday.open ?: "")} - ${formatTime(holiday.close ?: "")})."
                )
            }
        }

        val hours = shop.hours ?: return Pair(true, null)
        if (!shop.autoSchedule) return Pair(true, null)

        val yHoliday = findHolidayForDate(targetDate.minusDays(1), shop.holidays)
            ?: findHolidayForDate(targetDate.minusDays(1), platformHolidays)
        if (yHoliday == null || !yHoliday.closed) {
            val yWindow = extractDayWindow(hours, getDayKey(yesterdayDay))
            if (yWindow != null && !yWindow.closed && yWindow.closeMinutes < yWindow.openMinutes) {
                if (targetMinutes <= yWindow.closeMinutes) {
                    return Pair(true, null)
                }
            }
        }

        val tWindow = extractDayWindow(hours, getDayKey(targetDay))
        if (tWindow == null || tWindow.closed) {
            val dayName = targetDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
            return Pair(false, "Merchant is closed on ${dayName}s.")
        }

        if (tWindow.closeMinutes >= tWindow.openMinutes) {
            if (targetMinutes in tWindow.openMinutes..tWindow.closeMinutes) {
                return Pair(true, null)
            }
        } else {
            if (targetMinutes >= tWindow.openMinutes) {
                return Pair(true, null)
            }
        }
        return Pair(false, "Selected time is outside merchant business hours (${formatTime(tWindow.rawOpen)} - ${formatTime(tWindow.rawClose)}).")
    }
}
