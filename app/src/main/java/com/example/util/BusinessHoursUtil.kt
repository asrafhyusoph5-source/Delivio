package com.example.util

import com.example.data.model.HolidayEntry
import com.example.data.model.Merchant
import com.example.data.model.Restaurant
import com.example.data.model.StoreManualOverride
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StoreStatus(
    val open: Boolean,
    val reason: String? = null,
    val nextChange: Long? = null
)

object BusinessHoursUtil {

    private const val DEFAULT_TIMEZONE = "Asia/Manila"

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
        if (h < 0 || h > 24 || m < 0 || m >= 60) return null
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
        val explicitClosed = (dayData["closed"] as? Boolean)
            ?: (dayData["is_closed"] as? Boolean)
            ?: (dayData["isClosed"] as? Boolean)
        val isClosed = explicitClosed
            ?: ((dayData["enabled"] as? Boolean) == false)
            ?: ((dayData["isOpen"] as? Boolean) == false)

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
            ZoneId.of(timezone.ifBlank { DEFAULT_TIMEZONE })
        } catch (_: Exception) {
            ZoneId.of(DEFAULT_TIMEZONE)
        }
    }

    private fun toEpochMillis(date: LocalDate, minutesFromMidnight: Int, zoneId: ZoneId): Long {
        val clamped = minutesFromMidnight.coerceIn(0, 24 * 60)
        return if (clamped >= 24 * 60) {
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            date.atTime(clamped / 60, clamped % 60).atZone(zoneId).toInstant().toEpochMilli()
        }
    }

    private fun isWithinNormalWindow(curMinutes: Int, openMinutes: Int, closeMinutes: Int): Boolean {
        if (openMinutes == closeMinutes) return false
        return if (closeMinutes >= 23 * 60 + 59) {
            curMinutes >= openMinutes
        } else {
            curMinutes in openMinutes until closeMinutes
        }
    }

    private fun findNextOpeningMillis(
        startDate: LocalDate,
        startDayOffset: Int,
        hours: Map<String, Any>?,
        holidays: List<HolidayEntry>,
        platformHolidays: List<HolidayEntry>,
        zoneId: ZoneId
    ): Long? {
        if (hours.isNullOrEmpty()) return null
        for (i in startDayOffset..7) {
            val date = startDate.plusDays(i.toLong())
            val holiday = findHolidayForDate(date, holidays) ?: findHolidayForDate(date, platformHolidays)
            if (holiday != null) {
                if (holiday.closed) continue
                val hOpen = parseMinutes(holiday.open)
                if (hOpen != null) {
                    return toEpochMillis(date, hOpen, zoneId)
                }
            }
            val window = extractDayWindow(hours, getDayKey(date.dayOfWeek))
            if (window != null && !window.closed) {
                return toEpochMillis(date, window.openMinutes, zoneId)
            }
        }
        return null
    }

    /**
     * Single source-of-truth store status evaluator used across shop lists, branch sheets,
     * shop pages, cart, and checkout.
     *
     * Priority order:
     * 1. Merchant suspended (or non-active): closed.
     * 2. adminLock true: closed ("Closed by Delivio").
     * 3. manualOverride { state: "open" | "closed", until } while now < until: use its state.
     * 4. Auto-schedule on: open only inside today's hours in Asia/Manila (overnight windows supported),
     *    otherwise closed with next opening time.
     * 5. Auto-schedule off: the manual switch (isOpen) decides.
     */
    fun storeStatus(
        status: String,
        isOpen: Boolean,
        adminLock: Boolean = false,
        manualOverride: StoreManualOverride? = null,
        isPaused: Boolean = false,
        pauseUntilTime: Long = 0L,
        closedUntil: Long? = null,
        openUntil: Long? = null,
        autoSchedule: Boolean = false,
        hours: Map<String, Any>? = null,
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        holidays: List<HolidayEntry> = emptyList(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): StoreStatus {
        val normStatus = status.trim().lowercase(Locale.ROOT)

        // 1. Merchant suspended / inactive
        if (normStatus == "suspended") {
            return StoreStatus(open = false, reason = "Store is suspended", nextChange = null)
        }
        if (normStatus != "active") {
            return StoreStatus(open = false, reason = "Closed", nextChange = null)
        }

        // 2. adminLock true: "Closed by Delivio"
        if (adminLock) {
            return StoreStatus(open = false, reason = "Closed by Delivio", nextChange = null)
        }

        // 3. manualOverride { state: "open" | "closed", until } while now is before until
        if (manualOverride != null && manualOverride.until != null && nowMillis < manualOverride.until) {
            when (manualOverride.state.trim().lowercase(Locale.ROOT)) {
                "open" -> return StoreStatus(open = true, reason = null, nextChange = manualOverride.until)
                "closed" -> return StoreStatus(open = false, reason = "Closed", nextChange = manualOverride.until)
            }
        }
        // Legacy override fields fallback when manualOverride is absent
        if (closedUntil != null && nowMillis < closedUntil) {
            return StoreStatus(open = false, reason = "Closed", nextChange = closedUntil)
        }
        if (isPaused && nowMillis < pauseUntilTime) {
            return StoreStatus(open = false, reason = "Closed", nextChange = pauseUntilTime)
        }
        if (openUntil != null && nowMillis < openUntil) {
            return StoreStatus(open = true, reason = null, nextChange = openUntil)
        }

        // 4. Auto-schedule on: open only inside today's hours in Asia/Manila (overnight supported)
        if (autoSchedule) {
            val zoneId = resolveZoneId(timezone.ifBlank { DEFAULT_TIMEZONE })
            val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
            val todayDate = nowZoned.toLocalDate()
            val curMinutes = nowZoned.hour * 60 + nowZoned.minute

            val todayHoliday = findHolidayForDate(todayDate, holidays) ?: findHolidayForDate(todayDate, platformHolidays)
            if (todayHoliday != null) {
                if (todayHoliday.closed) {
                    val holidayReason = if (todayHoliday.name.isNotBlank()) {
                        "Closed (${todayHoliday.name})"
                    } else {
                        "Closed (Holiday)"
                    }
                    val nextOpen = findNextOpeningMillis(todayDate, 1, hours, holidays, platformHolidays, zoneId)
                    return StoreStatus(open = false, reason = holidayReason, nextChange = nextOpen)
                }
                val hOpen = parseMinutes(todayHoliday.open)
                val hClose = parseMinutes(todayHoliday.close)
                if (hOpen != null && hClose != null) {
                    return if (hClose >= hOpen) {
                        if (isWithinNormalWindow(curMinutes, hOpen, hClose)) {
                            StoreStatus(open = true, reason = null, nextChange = toEpochMillis(todayDate, hClose, zoneId))
                        } else if (curMinutes < hOpen) {
                            StoreStatus(open = false, reason = "Closed", nextChange = toEpochMillis(todayDate, hOpen, zoneId))
                        } else {
                            val nextOpen = findNextOpeningMillis(todayDate, 1, hours, holidays, platformHolidays, zoneId)
                            StoreStatus(open = false, reason = "Closed", nextChange = nextOpen)
                        }
                    } else {
                        if (curMinutes >= hOpen) {
                            StoreStatus(
                                open = true,
                                reason = null,
                                nextChange = toEpochMillis(todayDate.plusDays(1), hClose, zoneId)
                            )
                        } else {
                            StoreStatus(
                                open = false,
                                reason = "Closed",
                                nextChange = toEpochMillis(todayDate, hOpen, zoneId)
                            )
                        }
                    }
                }
            }

            if (hours.isNullOrEmpty()) {
                return StoreStatus(open = false, reason = "Closed", nextChange = null)
            }

            // Check yesterday's overnight window carrying into today (unless yesterday was a closed holiday)
            val yesterdayDate = todayDate.minusDays(1)
            val yHoliday = findHolidayForDate(yesterdayDate, holidays)
                ?: findHolidayForDate(yesterdayDate, platformHolidays)
            if (yHoliday == null || !yHoliday.closed) {
                val yOpenClose = if (yHoliday != null && !yHoliday.open.isNullOrBlank() && !yHoliday.close.isNullOrBlank()) {
                    val yo = parseMinutes(yHoliday.open)
                    val yc = parseMinutes(yHoliday.close)
                    if (yo != null && yc != null) DayWindow(false, yo, yc, yHoliday.open!!, yHoliday.close!!) else null
                } else {
                    extractDayWindow(hours, getDayKey(yesterdayDate.dayOfWeek))
                }
                if (yOpenClose != null && !yOpenClose.closed && yOpenClose.closeMinutes < yOpenClose.openMinutes) {
                    if (curMinutes < yOpenClose.closeMinutes) {
                        return StoreStatus(
                            open = true,
                            reason = null,
                            nextChange = toEpochMillis(todayDate, yOpenClose.closeMinutes, zoneId)
                        )
                    }
                }
            }

            // Check today's window
            val tWindow = extractDayWindow(hours, getDayKey(todayDate.dayOfWeek))
            if (tWindow != null && !tWindow.closed) {
                if (tWindow.closeMinutes >= tWindow.openMinutes) {
                    if (isWithinNormalWindow(curMinutes, tWindow.openMinutes, tWindow.closeMinutes)) {
                        return StoreStatus(
                            open = true,
                            reason = null,
                            nextChange = toEpochMillis(todayDate, tWindow.closeMinutes, zoneId)
                        )
                    } else if (curMinutes < tWindow.openMinutes) {
                        return StoreStatus(
                            open = false,
                            reason = "Closed",
                            nextChange = toEpochMillis(todayDate, tWindow.openMinutes, zoneId)
                        )
                    }
                } else {
                    // Window passes midnight into tomorrow
                    if (curMinutes >= tWindow.openMinutes) {
                        return StoreStatus(
                            open = true,
                            reason = null,
                            nextChange = toEpochMillis(todayDate.plusDays(1), tWindow.closeMinutes, zoneId)
                        )
                    } else {
                        return StoreStatus(
                            open = false,
                            reason = "Closed",
                            nextChange = toEpochMillis(todayDate, tWindow.openMinutes, zoneId)
                        )
                    }
                }
            }

            val nextOpen = findNextOpeningMillis(todayDate, 1, hours, holidays, platformHolidays, zoneId)
            return StoreStatus(open = false, reason = "Closed", nextChange = nextOpen)
        }

        // 5. Auto-schedule off: the manual switch decides
        return if (isOpen) {
            StoreStatus(open = true, reason = null, nextChange = null)
        } else {
            StoreStatus(open = false, reason = "Closed", nextChange = null)
        }
    }

    fun storeStatus(
        merchant: Restaurant,
        now: Long = System.currentTimeMillis(),
        timezone: String = DEFAULT_TIMEZONE,
        platformHolidays: List<HolidayEntry> = emptyList()
    ): StoreStatus {
        return storeStatus(
            status = merchant.status,
            isOpen = merchant.isOpen,
            adminLock = merchant.adminLock,
            manualOverride = merchant.manualOverride,
            isPaused = merchant.isPaused,
            pauseUntilTime = merchant.pauseUntilTime,
            closedUntil = merchant.closedUntil,
            openUntil = merchant.openUntil,
            autoSchedule = merchant.autoSchedule,
            hours = merchant.hours,
            timezone = timezone,
            nowMillis = now,
            holidays = merchant.holidays,
            platformHolidays = platformHolidays
        )
    }

    fun storeStatus(
        merchant: Merchant,
        now: Long = System.currentTimeMillis(),
        timezone: String = DEFAULT_TIMEZONE,
        platformHolidays: List<HolidayEntry> = emptyList()
    ): StoreStatus {
        return storeStatus(
            status = merchant.status,
            isOpen = merchant.isOpen,
            adminLock = merchant.adminLock,
            manualOverride = merchant.manualOverride,
            isPaused = merchant.isPaused,
            pauseUntilTime = merchant.pauseUntilTime,
            closedUntil = merchant.closedUntil,
            openUntil = merchant.openUntil,
            autoSchedule = merchant.autoSchedule,
            hours = merchant.hours,
            timezone = timezone,
            nowMillis = now,
            holidays = merchant.holidays,
            platformHolidays = platformHolidays
        )
    }

    fun allowsOrderForLater(
        status: String,
        adminLock: Boolean,
        allowScheduledOrders: Boolean
    ): Boolean {
        return status.trim().equals("active", ignoreCase = true) && !adminLock && allowScheduledOrders
    }

    fun allowsOrderForLater(shop: Restaurant): Boolean {
        return allowsOrderForLater(
            status = shop.status,
            adminLock = shop.adminLock,
            allowScheduledOrders = shop.allowScheduledOrders
        )
    }

    fun allowsOrderForLater(merchant: Merchant): Boolean {
        return allowsOrderForLater(
            status = merchant.status,
            adminLock = merchant.adminLock,
            allowScheduledOrders = merchant.allowScheduledOrders
        )
    }

    fun formatOpensTime(
        nextChangeMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = DEFAULT_TIMEZONE
    ): String? {
        if (nextChangeMillis == null) return null
        val zoneId = resolveZoneId(timezone)
        val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val nextZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextChangeMillis), zoneId)
        val today = nowZoned.toLocalDate()
        val nextDate = nextZoned.toLocalDate()
        val hhmm = String.format(Locale.US, "%02d:%02d", nextZoned.hour, nextZoned.minute)
        val clock = formatTime(hhmm)
        return when {
            nextDate == today -> clock
            nextDate == today.plusDays(1) -> "tomorrow $clock"
            nextDate.isBefore(today.plusDays(7)) -> {
                val dayName = nextZoned.dayOfWeek.name.take(3).lowercase(Locale.ROOT)
                    .replaceFirstChar { it.uppercase() }
                "$dayName $clock"
            }
            else -> {
                val month = nextZoned.month.name.take(3).lowercase(Locale.ROOT)
                    .replaceFirstChar { it.uppercase() }
                "$month ${nextZoned.dayOfMonth} $clock"
            }
        }
    }

    fun formatClosedOrderForLaterLabel(
        shop: Restaurant,
        status: StoreStatus,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String = DEFAULT_TIMEZONE
    ): String? {
        if (status.open || !allowsOrderForLater(shop)) return null
        val opensTime = formatOpensTime(status.nextChange, nowMillis, timezone)
        return if (!opensTime.isNullOrBlank()) {
            "Order for later - Opens $opensTime"
        } else {
            "Order for later"
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
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        holidays: List<HolidayEntry> = emptyList(),
        platformHolidays: List<HolidayEntry> = emptyList(),
        adminLock: Boolean = false,
        manualOverride: StoreManualOverride? = null
    ): Boolean {
        return storeStatus(
            status = status,
            isOpen = isOpen,
            adminLock = adminLock,
            manualOverride = manualOverride,
            isPaused = isPaused,
            pauseUntilTime = pauseUntilTime,
            closedUntil = closedUntil,
            openUntil = openUntil,
            autoSchedule = autoSchedule,
            hours = hours,
            timezone = timezone,
            nowMillis = nowMillis,
            holidays = holidays,
            platformHolidays = platformHolidays
        ).open
    }

    fun effectiveOpen(
        shop: Restaurant,
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Boolean {
        return storeStatus(shop, nowMillis, timezone, platformHolidays).open
    }

    fun effectiveOpen(
        merchant: Merchant,
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Boolean {
        return storeStatus(merchant, nowMillis, timezone, platformHolidays).open
    }

    fun getNextOpenText(
        shop: Restaurant,
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): String {
        val status = storeStatus(shop, nowMillis, timezone, platformHolidays)
        if (status.open) return "Open"
        if (shop.adminLock) return "Closed by Delivio"
        if (shop.status.trim().equals("suspended", ignoreCase = true)) return "Store is suspended"
        val opensTime = formatOpensTime(status.nextChange, nowMillis, timezone)
        if (!opensTime.isNullOrBlank()) {
            return "Opens $opensTime"
        }
        return status.reason?.takeIf { it.isNotBlank() } ?: shop.nextOpenTime.ifBlank { "Closed" }
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
        timezone: String = DEFAULT_TIMEZONE,
        nowMillis: Long = System.currentTimeMillis(),
        platformHolidays: List<HolidayEntry> = emptyList()
    ): String {
        val status = storeStatus(shop, nowMillis, timezone, platformHolidays)
        if (shop.status.trim().equals("suspended", ignoreCase = true)) {
            return "Store is suspended"
        }
        if (shop.adminLock) {
            return "Closed by Delivio"
        }
        val zoneId = resolveZoneId(timezone)
        val nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val todayDate = nowZoned.toLocalDate()
        val todayHoliday = findHolidayForDate(todayDate, shop.holidays) ?: findHolidayForDate(todayDate, platformHolidays)

        val closedOverride = (shop.manualOverride != null &&
            shop.manualOverride.until != null &&
            nowMillis < shop.manualOverride.until &&
            shop.manualOverride.state.trim().equals("closed", ignoreCase = true)) ||
            (shop.closedUntil != null && nowMillis < shop.closedUntil)
        if (closedOverride) {
            return "Closed temporarily"
        }
        if (shop.autoSchedule && todayHoliday != null && todayHoliday.closed) {
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
        return if (status.open) {
            "Open now"
        } else {
            status.reason ?: "Closed"
        }
    }

    fun isScheduleTimeValid(
        shop: Restaurant?,
        scheduledMillis: Long,
        timezone: String = DEFAULT_TIMEZONE,
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Pair<Boolean, String?> {
        if (shop == null) return Pair(true, null)

        val normStatus = shop.status.trim().lowercase(Locale.ROOT)
        if (normStatus == "suspended") {
            return Pair(false, "Store is suspended.")
        }
        if (normStatus != "active") {
            return Pair(false, "Store is currently unavailable.")
        }
        if (shop.adminLock) {
            return Pair(false, "Closed by Delivio")
        }
        if (!shop.allowScheduledOrders) {
            return Pair(false, "This store is not accepting scheduled orders.")
        }

        val override = shop.manualOverride
        if (override != null && override.until != null && scheduledMillis < override.until) {
            val st = override.state.trim().lowercase(Locale.ROOT)
            if (st == "closed") {
                return Pair(false, "Merchant is temporarily closed at the selected time.")
            }
            if (st == "open") {
                return Pair(true, null)
            }
        }

        val closedUntil = shop.closedUntil
        if (closedUntil != null && scheduledMillis < closedUntil) {
            return Pair(false, "Merchant is temporarily closed at the selected time.")
        }
        if (shop.isPaused && scheduledMillis < shop.pauseUntilTime) {
            return Pair(false, "Merchant is temporarily paused at the selected time.")
        }
        val openUntil = shop.openUntil
        if (openUntil != null && scheduledMillis < openUntil) {
            return Pair(true, null)
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
                val valid = if (hClose >= hOpen) {
                    isWithinNormalWindow(targetMinutes, hOpen, hClose)
                } else {
                    targetMinutes >= hOpen
                }
                return if (valid) Pair(true, null) else Pair(
                    false,
                    "Selected time is outside holiday hours (${formatTime(holiday.open ?: "")} - ${formatTime(holiday.close ?: "")})."
                )
            }
        }

        if (!shop.autoSchedule) return Pair(true, null)
        val hours = shop.hours
        if (hours.isNullOrEmpty()) return Pair(false, "Merchant hours are unavailable.")

        val yHoliday = findHolidayForDate(targetDate.minusDays(1), shop.holidays)
            ?: findHolidayForDate(targetDate.minusDays(1), platformHolidays)
        if (yHoliday == null || !yHoliday.closed) {
            val yWindow = extractDayWindow(hours, getDayKey(yesterdayDay))
            if (yWindow != null && !yWindow.closed && yWindow.closeMinutes < yWindow.openMinutes) {
                if (targetMinutes < yWindow.closeMinutes) {
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
            if (isWithinNormalWindow(targetMinutes, tWindow.openMinutes, tWindow.closeMinutes)) {
                return Pair(true, null)
            }
        } else {
            if (targetMinutes >= tWindow.openMinutes) {
                return Pair(true, null)
            }
        }
        return Pair(false, "Selected time is outside merchant business hours (${formatTime(tWindow.rawOpen)} - ${formatTime(tWindow.rawClose)}).")
    }

    fun isScheduleTimeValid(
        merchant: Merchant,
        scheduledMillis: Long,
        timezone: String = DEFAULT_TIMEZONE,
        platformHolidays: List<HolidayEntry> = emptyList()
    ): Pair<Boolean, String?> {
        val asRestaurant = Restaurant(
            id = merchant.id,
            name = merchant.name,
            category = merchant.type,
            type = merchant.type,
            rating = merchant.averageRating,
            reviewCount = merchant.ratingCount,
            etaMinutes = 20,
            deliveryFee = 39.0,
            address = merchant.address,
            phone = merchant.phone,
            isOpen = merchant.isOpen,
            adminLock = merchant.adminLock,
            manualOverride = merchant.manualOverride,
            allowScheduledOrders = merchant.allowScheduledOrders,
            hours = merchant.hours,
            holidays = merchant.holidays,
            status = merchant.status,
            isPaused = merchant.isPaused,
            pauseUntilTime = merchant.pauseUntilTime,
            closedUntil = merchant.closedUntil,
            openUntil = merchant.openUntil,
            autoSchedule = merchant.autoSchedule
        )
        return isScheduleTimeValid(asRestaurant, scheduledMillis, timezone, platformHolidays)
    }
}
