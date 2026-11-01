package com.example.util

import com.example.data.model.Restaurant
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        // false when: status != "active"
        if (!status.equals("active", ignoreCase = true)) {
            return false
        }
        // false when: closedUntil is set and now < closedUntil
        if (closedUntil != null && nowMillis < closedUntil) {
            return false
        }
        // false when: isPaused and now < pauseUntilTime
        if (isPaused && nowMillis < pauseUntilTime) {
            return false
        }
        // Otherwise true when openUntil is set and now < openUntil
        if (openUntil != null && nowMillis < openUntil) {
            return true
        }
        // otherwise, when autoSchedule is true and hours exist, true only inside today's window
        if (autoSchedule && !hours.isNullOrEmpty()) {
            val zoneId = try {
                ZoneId.of(timezone.ifBlank { "Asia/Manila" })
            } catch (_: Exception) {
                ZoneId.of("Asia/Manila")
            }
            val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
            val todayDay = nowZoned.dayOfWeek
            val yesterdayDay = todayDay.minus(1)
            val curMinutes = nowZoned.hour * 60 + nowZoned.minute

            val todayKey = getDayKey(todayDay)
            val yesterdayKey = getDayKey(yesterdayDay)

            // 1. Check yesterday's window that passed midnight into today
            val yWindow = extractDayWindow(hours, yesterdayKey)
            if (yWindow != null && !yWindow.closed) {
                if (yWindow.closeMinutes < yWindow.openMinutes) {
                    if (curMinutes <= yWindow.closeMinutes) {
                        return true
                    }
                }
            }

            // 2. Check today's window
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
        // otherwise isOpen
        return isOpen
    }

    fun effectiveOpen(
        shop: Restaurant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis()
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
            nowMillis = nowMillis
        )
    }

    fun effectiveOpen(
        merchant: com.example.data.model.Merchant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis()
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
            nowMillis = nowMillis
        )
    }

    fun getNextOpenText(
        shop: Restaurant,
        timezone: String = "Asia/Manila",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (effectiveOpen(shop, timezone, nowMillis)) {
            return "Open"
        }
        val hours = shop.hours
        if (shop.autoSchedule && !hours.isNullOrEmpty()) {
            val zoneId = try {
                ZoneId.of(timezone.ifBlank { "Asia/Manila" })
            } catch (_: Exception) {
                ZoneId.of("Asia/Manila")
            }
            val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
            val curMinutes = nowZoned.hour * 60 + nowZoned.minute

            val todayKey = getDayKey(nowZoned.dayOfWeek)
            val tWindow = extractDayWindow(hours, todayKey)
            if (tWindow != null && !tWindow.closed && curMinutes < tWindow.openMinutes) {
                return "Opens ${formatTime(tWindow.rawOpen)}"
            }

            for (i in 1..7) {
                val nextDay = nowZoned.dayOfWeek.plus(i.toLong())
                val nextKey = getDayKey(nextDay)
                val nWindow = extractDayWindow(hours, nextKey)
                if (nWindow != null && !nWindow.closed) {
                    val dayName = if (i == 1) "tomorrow" else nextDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
                    return "Opens $dayName ${formatTime(nWindow.rawOpen)}"
                }
            }
        }
        return shop.nextOpenTime.ifBlank { "Opens soon" }
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

    fun isScheduleTimeValid(
        shop: Restaurant?,
        scheduledMillis: Long,
        timezone: String = "Asia/Manila"
    ): Pair<Boolean, String?> {
        if (shop == null) return Pair(true, null)
        val hours = shop.hours ?: return Pair(true, null)
        if (!shop.autoSchedule) return Pair(true, null)

        val zoneId = try {
            ZoneId.of(timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }
        val targetZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(scheduledMillis), zoneId)
        val targetDay = targetZoned.dayOfWeek
        val yesterdayDay = targetDay.minus(1)
        val targetMinutes = targetZoned.hour * 60 + targetZoned.minute

        val yWindow = extractDayWindow(hours, getDayKey(yesterdayDay))
        if (yWindow != null && !yWindow.closed && yWindow.closeMinutes < yWindow.openMinutes) {
            if (targetMinutes <= yWindow.closeMinutes) {
                return Pair(true, null)
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
