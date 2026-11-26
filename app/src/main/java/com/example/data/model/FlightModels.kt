package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TripType(val label: String) {
    ONE_WAY("One Way"),
    ROUND_TRIP("Round Trip"),
    MULTI_CITY("Multi City")
}

enum class CabinClass(val label: String) {
    ECONOMY("Economy"),
    PREMIUM_ECONOMY("Premium Economy"),
    BUSINESS("Business"),
    FIRST("First")
}

data class Airport(
    val code: String,
    val name: String,
    val city: String,
    val country: String,
    val isDomestic: Boolean = true
) {
    val displayTitle: String
        get() = "$code • $city"

    val displaySubtitle: String
        get() = if (country.equals("Philippines", ignoreCase = true)) name else "$name, $country"
}

object AirportDirectory {
    val philippineAirports: List<Airport> = listOf(
        Airport("PPS", "Puerto Princesa International Airport", "Puerto Princesa", "Philippines", true),
        Airport("MNL", "Ninoy Aquino International Airport", "Manila", "Philippines", true),
        Airport("CEB", "Mactan-Cebu International Airport", "Cebu", "Philippines", true),
        Airport("DVO", "Francisco Bangoy International Airport", "Davao", "Philippines", true),
        Airport("ILO", "Iloilo International Airport", "Iloilo", "Philippines", true),
        Airport("KLO", "Kalibo International Airport", "Kalibo", "Philippines", true),
        Airport("TAG", "Bohol-Panglao International Airport", "Tagbilaran", "Philippines", true),
        Airport("CRK", "Clark International Airport", "Clark", "Philippines", true),
        Airport("MPH", "Godofredo P. Ramos Airport", "Caticlan (Boracay)", "Philippines", true),
        Airport("ENI", "El Nido Airport", "El Nido", "Philippines", true),
        Airport("USU", "Francisco B. Reyes Airport", "Coron (Busuanga)", "Philippines", true),
        Airport("CGY", "Laguindingan Airport", "Cagayan de Oro", "Philippines", true),
        Airport("BCD", "Bacolod-Silay Airport", "Bacolod", "Philippines", true),
        Airport("TAC", "Daniel Z. Romualdez Airport", "Tacloban", "Philippines", true),
        Airport("ZAM", "Zamboanga International Airport", "Zamboanga", "Philippines", true),
        Airport("GES", "General Santos International Airport", "General Santos", "Philippines", true),
        Airport("DRP", "Bicol International Airport", "Legazpi (Daraga)", "Philippines", true),
        Airport("LAO", "Laoag International Airport", "Laoag", "Philippines", true)
    )

    val internationalAirports: List<Airport> = listOf(
        Airport("SIN", "Singapore Changi Airport", "Singapore", "Singapore", false),
        Airport("HKG", "Hong Kong International Airport", "Hong Kong", "Hong Kong", false),
        Airport("ICN", "Incheon International Airport", "Seoul", "South Korea", false),
        Airport("NRT", "Narita International Airport", "Tokyo", "Japan", false),
        Airport("KIX", "Kansai International Airport", "Osaka", "Japan", false),
        Airport("TPE", "Taiwan Taoyuan International Airport", "Taipei", "Taiwan", false),
        Airport("BKK", "Suvarnabhumi Airport", "Bangkok", "Thailand", false),
        Airport("KUL", "Kuala Lumpur International Airport", "Kuala Lumpur", "Malaysia", false),
        Airport("DPS", "Ngurah Rai International Airport", "Bali (Denpasar)", "Indonesia", false),
        Airport("DXB", "Dubai International Airport", "Dubai", "United Arab Emirates", false),
        Airport("SYD", "Sydney Kingsford Smith Airport", "Sydney", "Australia", false),
        Airport("LAX", "Los Angeles International Airport", "Los Angeles", "United States", false)
    )

    val allAirports: List<Airport> = philippineAirports + internationalAirports

    fun findByCode(code: String?): Airport? {
        val clean = code?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (clean.isEmpty()) return null
        return allAirports.firstOrNull { it.code.equals(clean, ignoreCase = true) }
    }

    fun search(query: String): List<Airport> {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return allAirports
        return allAirports.filter { airport ->
            airport.code.lowercase(Locale.ROOT).contains(clean) ||
                airport.city.lowercase(Locale.ROOT).contains(clean) ||
                airport.name.lowercase(Locale.ROOT).contains(clean) ||
                airport.country.lowercase(Locale.ROOT).contains(clean)
        }
    }
}

data class FlightLeg(
    val id: String,
    val origin: Airport? = null,
    val destination: Airport? = null,
    val departureDateMillis: Long? = null
)

data class TravelersCount(
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0
) {
    val total: Int
        get() = adults + children + infants

    fun formatSummary(): String {
        val parts = mutableListOf<String>()
        parts += if (adults == 1) "1 Adult" else "$adults Adults"
        if (children > 0) {
            parts += if (children == 1) "1 Child" else "$children Children"
        }
        if (infants > 0) {
            parts += if (infants == 1) "1 Infant" else "$infants Infants"
        }
        return parts.joinToString(", ")
    }
}

data class FlightSearchUiState(
    val tripType: TripType = TripType.ONE_WAY,
    val origin: Airport? = AirportDirectory.findByCode("PPS"),
    val destination: Airport? = AirportDirectory.findByCode("MNL"),
    val departureDateMillis: Long? = null,
    val returnDateMillis: Long? = null,
    val multiCityLegs: List<FlightLeg> = listOf(
        FlightLeg(id = "leg_1", origin = AirportDirectory.findByCode("PPS"), destination = AirportDirectory.findByCode("MNL")),
        FlightLeg(id = "leg_2", origin = AirportDirectory.findByCode("MNL"), destination = AirportDirectory.findByCode("CEB"))
    ),
    val travelers: TravelersCount = TravelersCount(),
    val cabinClass: CabinClass = CabinClass.ECONOMY,
    val recentAirportCodes: List<String> = emptyList(),
    val validationError: String? = null,
    val showComingSoonResult: Boolean = false
)

object FlightDateUtil {
    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

    fun localTodayUtcMidnightMillis(): Long {
        val local = Calendar.getInstance()
        return utcMidnightFor(
            year = local.get(Calendar.YEAR),
            monthZeroBased = local.get(Calendar.MONTH),
            dayOfMonth = local.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun utcMidnightFor(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        return Calendar.getInstance(utcTz).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun normalizeToUtcMidnight(millis: Long): Long {
        val cal = Calendar.getInstance(utcTz).apply { timeInMillis = millis }
        return utcMidnightFor(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDate(utcMillis: Long?): String {
        if (utcMillis == null || utcMillis <= 0L) return "Choose Date"
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply {
            timeZone = utcTz
        }.format(Date(utcMillis))
    }

    fun formatShortDate(utcMillis: Long?): String {
        if (utcMillis == null || utcMillis <= 0L) return "Choose Date"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
            timeZone = utcTz
        }.format(Date(utcMillis))
    }
}

object FlightFormValidator {
    const val MAX_TRAVELERS = 9
    const val MAX_MULTI_CITY_LEGS = 5

    fun canIncrementAdults(t: TravelersCount): Boolean = t.total < MAX_TRAVELERS
    fun canDecrementAdults(t: TravelersCount): Boolean = t.adults > 1
    fun canIncrementChildren(t: TravelersCount): Boolean = t.total < MAX_TRAVELERS
    fun canDecrementChildren(t: TravelersCount): Boolean = t.children > 0
    fun canIncrementInfants(t: TravelersCount): Boolean = t.total < MAX_TRAVELERS && t.infants < t.adults
    fun canDecrementInfants(t: TravelersCount): Boolean = t.infants > 0

    fun incrementAdults(t: TravelersCount): TravelersCount =
        if (canIncrementAdults(t)) t.copy(adults = t.adults + 1) else t

    fun decrementAdults(t: TravelersCount): TravelersCount {
        if (!canDecrementAdults(t)) return t
        val nextAdults = t.adults - 1
        return t.copy(adults = nextAdults, infants = minOf(t.infants, nextAdults))
    }

    fun incrementChildren(t: TravelersCount): TravelersCount =
        if (canIncrementChildren(t)) t.copy(children = t.children + 1) else t

    fun decrementChildren(t: TravelersCount): TravelersCount =
        if (canDecrementChildren(t)) t.copy(children = t.children - 1) else t

    fun incrementInfants(t: TravelersCount): TravelersCount =
        if (canIncrementInfants(t)) t.copy(infants = t.infants + 1) else t

    fun decrementInfants(t: TravelersCount): TravelersCount =
        if (canDecrementInfants(t)) t.copy(infants = t.infants - 1) else t

    fun validate(
        state: FlightSearchUiState,
        todayUtcMidnightMillis: Long = FlightDateUtil.localTodayUtcMidnightMillis()
    ): String? {
        if (state.travelers.adults < 1 || state.travelers.total > MAX_TRAVELERS) {
            return "Travelers must be between 1 and 9."
        }
        if (state.travelers.infants > state.travelers.adults) {
            return "Infants cannot exceed the number of adults."
        }

        return when (state.tripType) {
            TripType.ONE_WAY -> {
                val orig = state.origin ?: return "Please select a departure airport."
                val dest = state.destination ?: return "Please select a destination airport."
                if (orig.code.equals(dest.code, ignoreCase = true)) {
                    return "Origin and destination airports cannot be the same."
                }
                val dep = state.departureDateMillis ?: return "Please choose a departure date."
                if (FlightDateUtil.normalizeToUtcMidnight(dep) < todayUtcMidnightMillis) {
                    return "Departure date cannot be in the past."
                }
                null
            }
            TripType.ROUND_TRIP -> {
                val orig = state.origin ?: return "Please select a departure airport."
                val dest = state.destination ?: return "Please select a destination airport."
                if (orig.code.equals(dest.code, ignoreCase = true)) {
                    return "Origin and destination airports cannot be the same."
                }
                val dep = state.departureDateMillis ?: return "Please choose a departure date."
                val normDep = FlightDateUtil.normalizeToUtcMidnight(dep)
                if (normDep < todayUtcMidnightMillis) {
                    return "Departure date cannot be in the past."
                }
                val ret = state.returnDateMillis ?: return "Please choose a return date."
                val normRet = FlightDateUtil.normalizeToUtcMidnight(ret)
                if (normRet <= normDep) {
                    return "Return date must be after departure date."
                }
                null
            }
            TripType.MULTI_CITY -> {
                if (state.multiCityLegs.isEmpty()) {
                    return "Add at least one flight leg."
                }
                if (state.multiCityLegs.size > MAX_MULTI_CITY_LEGS) {
                    return "Maximum of $MAX_MULTI_CITY_LEGS flight legs allowed."
                }
                state.multiCityLegs.forEachIndexed { idx, leg ->
                    val label = "Flight ${idx + 1}"
                    val orig = leg.origin ?: return "Select Origin for $label."
                    val dest = leg.destination ?: return "Select Destination for $label."
                    if (orig.code.equals(dest.code, ignoreCase = true)) {
                        return "$label Origin and Destination cannot be the same."
                    }
                    val date = leg.departureDateMillis ?: return "Choose Departure Date for $label."
                    if (FlightDateUtil.normalizeToUtcMidnight(date) < todayUtcMidnightMillis) {
                        return "$label date cannot be in the past."
                    }
                }
                null
            }
        }
    }
}
