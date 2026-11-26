package com.example.util

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun nowTs(): Timestamp = Timestamp.now()

/**
 * Fault-tolerant date/time reader and Timestamp write helper for Firestore documents.
 * Never throws on unexpected types (Timestamp, Date, Number in ms or seconds,
 * ISO-8601 or numeric strings, serialized {seconds, nanoseconds} maps, or missing).
 */
object FirestoreTimeUtil {
    fun nowTs(): Timestamp = Timestamp.now()

    fun serverTs(): FieldValue = FieldValue.serverTimestamp()

    fun tsFromMillis(millis: Long): Timestamp = Timestamp(Date(millis))

    fun statusHistoryEntry(status: String, at: Timestamp = nowTs()): Map<String, Any> = mapOf(
        "status" to status,
        "at" to at
    )

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")
    private val numericRegex = Regex("""^[+-]?\d+(\.\d+)?([eE][+-]?\d+)?$""")

    private val spaceDateTimeFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    )

    private val legacyStringPatterns = listOf(
        "EEE MMM dd HH:mm:ss zzz yyyy",
        "EEE, dd MMM yyyy HH:mm:ss zzz"
    )

    fun readTime(snapshot: DocumentSnapshot?, field: String): Date? {
        if (snapshot == null || field.isBlank()) return null
        val raw = try {
            snapshot.get(field)
        } catch (_: Exception) {
            null
        }
        return readTime(raw)
    }

    fun readTimeMillis(snapshot: DocumentSnapshot?, field: String): Long? {
        return readTime(snapshot, field)?.time
    }

    fun readTimestamp(snapshot: DocumentSnapshot?, field: String): Timestamp? {
        return readTime(snapshot, field)?.let { Timestamp(it) }
    }

    fun readTimeMillis(value: Any?): Long? {
        return readTime(value)?.time
    }

    fun readTime(value: Any?): Date? {
        return when (value) {
            null -> null
            is Date -> value
            is Timestamp -> try {
                value.toDate()
            } catch (_: Exception) {
                null
            }
            is Number -> numberToDate(value.toDouble())
            is String -> parseStringDate(value)
            is Map<*, *> -> parseTimestampMap(value)
            else -> null
        }
    }

    private fun numberToDate(number: Double): Date? {
        if (!number.isFinite()) return null
        val millisDouble = if (abs(number) < 100_000_000_000.0) {
            number * 1000.0
        } else {
            number
        }
        if (millisDouble < Long.MIN_VALUE.toDouble() || millisDouble > Long.MAX_VALUE.toDouble()) {
            return null
        }
        return Date(millisDouble.roundToLong())
    }

    private fun parseTimestampMap(map: Map<*, *>): Date? {
        val secRaw = map["seconds"] ?: map["_seconds"]
        val nanoRaw = map["nanoseconds"] ?: map["_nanoseconds"]
        val sec = when (secRaw) {
            is Number -> secRaw.toDouble()
            is String -> secRaw.trim().toDoubleOrNull()
            else -> null
        } ?: return null
        val nanos = when (nanoRaw) {
            is Number -> nanoRaw.toDouble()
            is String -> nanoRaw.trim().toDoubleOrNull()
            else -> null
        } ?: 0.0
        if (!sec.isFinite() || !nanos.isFinite()) return null
        val totalMillis = sec * 1000.0 + (nanos / 1_000_000.0)
        if (totalMillis < Long.MIN_VALUE.toDouble() || totalMillis > Long.MAX_VALUE.toDouble()) {
            return null
        }
        return Date(totalMillis.roundToLong())
    }

    private fun parseStringDate(raw: String): Date? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        if (numericRegex.matches(s)) {
            val numeric = s.toDoubleOrNull()
            if (numeric != null) {
                return numberToDate(numeric)
            }
        }

        try {
            return Date.from(Instant.parse(s))
        } catch (_: Exception) {
        }

        try {
            return Date.from(OffsetDateTime.parse(s).toInstant())
        } catch (_: Exception) {
        }

        try {
            return Date.from(ZonedDateTime.parse(s).toInstant())
        } catch (_: Exception) {
        }

        try {
            val ldt = LocalDateTime.parse(s)
            return Date.from(ldt.atZone(manilaZone).toInstant())
        } catch (_: Exception) {
        }

        try {
            val ld = LocalDate.parse(s)
            return Date.from(ld.atStartOfDay(manilaZone).toInstant())
        } catch (_: Exception) {
        }

        for (formatter in spaceDateTimeFormatters) {
            try {
                val ldt = LocalDateTime.parse(s, formatter)
                return Date.from(ldt.atZone(manilaZone).toInstant())
            } catch (_: Exception) {
            }
            try {
                val ld = LocalDate.parse(s, formatter)
                return Date.from(ld.atStartOfDay(manilaZone).toInstant())
            } catch (_: Exception) {
            }
        }

        for (pattern in legacyStringPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = true
                }
                val parsed = sdf.parse(s)
                if (parsed != null) return parsed
            } catch (_: Exception) {
            }
        }

        return null
    }
}
