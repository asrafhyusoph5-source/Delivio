package com.example.data.api

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class NominatimResult(
    val placeId: Long,
    val displayName: String,
    val shortName: String,
    val lat: Double,
    val lon: Double
)

object NominatimService {
    private const val BASE_URL = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "DelivioApp/1.0 (com.delivio.app; support@delivio.app)"
    
    // Strict rate limiter: Nominatim Usage Policy requires maximum 1 request per second
    private val rateLimitMutex = Mutex()
    private var lastRequestTimestamp = 0L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun enforceRateLimit() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTimestamp
            if (timeSinceLast < 1000L) {
                delay(1000L - timeSinceLast)
            }
            lastRequestTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Search for places in the Philippines with countrycodes=ph.
     * Returns empty list on offline / error, never crashes.
     */
    suspend fun searchPlaces(query: String): Result<List<NominatimResult>> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) {
            return@withContext Result.success(emptyList())
        }

        try {
            enforceRateLimit()

            val encodedQuery = Uri.encode(query.trim())
            val url = "$BASE_URL/search?q=$encodedQuery&format=json&addressdetails=1&countrycodes=ph&limit=8"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9,fil;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val jsonArray = JSONArray(body)
                val results = mutableListOf<NominatimResult>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val placeId = obj.optLong("place_id", i.toLong())
                    val displayName = obj.optString("display_name", "")
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)

                    // Derive a readable short title
                    val addressObj = obj.optJSONObject("address")
                    val shortName = when {
                        obj.has("name") && obj.getString("name").isNotBlank() -> obj.getString("name")
                        addressObj != null && addressObj.has("road") -> {
                            val road = addressObj.optString("road", "")
                            val suburb = addressObj.optString("suburb", addressObj.optString("city", ""))
                            if (suburb.isNotBlank()) "$road, $suburb" else road
                        }
                        else -> displayName.split(",").take(2).joinToString(", ").trim()
                    }

                    if (lat != 0.0 && lon != 0.0) {
                        results.add(
                            NominatimResult(
                                placeId = placeId,
                                displayName = displayName,
                                shortName = shortName,
                                lat = lat,
                                lon = lon
                            )
                        )
                    }
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reverse geocodes latitude and longitude into human-readable address.
     * Returns a formatted address or fallback, never crashes.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): Result<String> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = "$BASE_URL/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9,fil;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.success("Location at (${String.format("%.4f", lat)}, ${String.format("%.4f", lon)})")
                val json = JSONObject(body)
                val formatted = formatReverseGeocodeJson(json)
                Result.success(formatted.ifBlank { "Location (${String.format("%.4f", lat)}, ${String.format("%.4f", lon)})" })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun formatReverseGeocodeJson(json: JSONObject): String {
        val displayName = json.optString("display_name", "").trim()
        val addressObj = json.optJSONObject("address") ?: return displayName

        fun firstNonBlank(obj: JSONObject, vararg keys: String): String {
            for (k in keys) {
                val v = obj.optString(k, "").trim()
                if (v.isNotEmpty()) return v
            }
            return ""
        }

        val houseNumber = firstNonBlank(addressObj, "house_number")
        val road = firstNonBlank(
            addressObj,
            "road", "pedestrian", "residential", "street", "footway", "highway", "path", "cycleway", "track"
        )
        val street = if (houseNumber.isNotEmpty() && road.isNotEmpty()) "$houseNumber $road" else road
        val placeName = firstNonBlank(json, "name").ifBlank {
            firstNonBlank(
                addressObj,
                "amenity", "building", "shop", "mall", "tourism", "leisure", "office", "commercial", "industrial"
            )
        }
        val barangay = firstNonBlank(
            addressObj,
            "neighbourhood", "suburb", "quarter", "village", "hamlet", "city_district", "district"
        )
        val city = firstNonBlank(addressObj, "city", "municipality", "town", "county")
        val state = firstNonBlank(addressObj, "state", "province", "region")

        val leadingSegment = displayName.split(",")
            .map { it.trim() }
            .firstOrNull { seg ->
                seg.isNotEmpty() &&
                    !seg.equals(barangay, ignoreCase = true) &&
                    !seg.equals(city, ignoreCase = true) &&
                    !seg.equals(state, ignoreCase = true) &&
                    !seg.equals("Philippines", ignoreCase = true) &&
                    seg.any { it.isLetter() }
            }
            .orEmpty()

        val firstLine = when {
            street.isNotEmpty() -> street
            placeName.isNotEmpty() && !placeName.equals(barangay, ignoreCase = true) -> placeName
            else -> leadingSegment
        }

        val orderedParts = mutableListOf<String>()
        listOf(firstLine, barangay, city, state).forEach { part ->
            if (part.isNotBlank() && orderedParts.none { it.equals(part, ignoreCase = true) }) {
                orderedParts.add(part)
            }
        }
        return if (orderedParts.isNotEmpty()) orderedParts.joinToString(", ") else displayName
    }
}
