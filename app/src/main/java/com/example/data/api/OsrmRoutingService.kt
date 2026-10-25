package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteResult(
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val durationMinutes: Int,
    val isFallback: Boolean = false
)

object OsrmRoutingService {
    private const val BASE_URL = "https://router.project-osrm.org"
    private const val USER_AGENT = "DelivioApp/1.0 (com.delivio.app)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Calculates driving route between start and end.
     * Falls back to straight line if offline, timed out, or route not found.
     * Never throws exceptions or crashes.
     */
    suspend fun calculateRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val straightDistKm = calculateHaversineDistanceKm(startLat, startLng, endLat, endLng)
        val fallbackMinutes = ((straightDistKm / 25.0) * 60.0).toInt().coerceAtLeast(3)
        val fallbackRoute = RouteResult(
            points = listOf(GeoPoint(startLat, startLng), GeoPoint(endLat, endLng)),
            distanceKm = (straightDistKm * 10.0).toInt() / 10.0,
            durationMinutes = fallbackMinutes,
            isFallback = true
        )

        // Validate coordinates
        if (startLat == 0.0 || startLng == 0.0 || endLat == 0.0 || endLng == 0.0) {
            return@withContext fallbackRoute
        }

        try {
            // OSRM expects: /route/v1/driving/{lng1},{lat1};{lng2},{lat2}
            val url = "$BASE_URL/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext fallbackRoute
                }
                val body = response.body?.string() ?: return@withContext fallbackRoute
                val json = JSONObject(body)
                val code = json.optString("code", "")
                if (code != "Ok") {
                    return@withContext fallbackRoute
                }

                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    return@withContext fallbackRoute
                }

                val primaryRoute = routes.getJSONObject(0)
                val distanceMeters = primaryRoute.optDouble("distance", straightDistKm * 1000.0)
                val durationSeconds = primaryRoute.optDouble("duration", fallbackMinutes * 60.0)

                val geometry = primaryRoute.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                val geoPoints = mutableListOf<GeoPoint>()

                if (coordinates != null && coordinates.length() > 0) {
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        geoPoints.add(GeoPoint(lat, lon))
                    }
                } else {
                    geoPoints.add(GeoPoint(startLat, startLng))
                    geoPoints.add(GeoPoint(endLat, endLng))
                }

                val distKm = ((distanceMeters / 1000.0) * 10.0).toInt() / 10.0
                val durationMins = (durationSeconds / 60.0).toInt().coerceAtLeast(1)

                RouteResult(
                    points = geoPoints,
                    distanceKm = distKm,
                    durationMinutes = durationMins,
                    isFallback = false
                )
            }
        } catch (_: Exception) {
            fallbackRoute
        }
    }

    /**
     * Calculates driving route through multiple waypoints in order.
     * Uses OSRM multi-coordinate route API with straight-line fallback.
     */
    suspend fun calculateMultiPointRoute(
        points: List<Pair<Double, Double>> // Pair(lat, lng)
    ): RouteResult = withContext(Dispatchers.IO) {
        val validPoints = points.filter { it.first != 0.0 && it.second != 0.0 }
        if (validPoints.size < 2) {
            val single = validPoints.firstOrNull() ?: Pair(9.7392, 118.7353)
            return@withContext RouteResult(
                points = listOf(GeoPoint(single.first, single.second)),
                distanceKm = 0.0,
                durationMinutes = 0,
                isFallback = true
            )
        }

        // Calculate straight-line fallback distance across segments
        var straightDistKm = 0.0
        for (i in 0 until validPoints.size - 1) {
            straightDistKm += calculateHaversineDistanceKm(
                validPoints[i].first, validPoints[i].second,
                validPoints[i + 1].first, validPoints[i + 1].second
            )
        }
        val fallbackMinutes = ((straightDistKm / 25.0) * 60.0).toInt().coerceAtLeast(3)
        val fallbackPoints = validPoints.map { GeoPoint(it.first, it.second) }
        val fallbackRoute = RouteResult(
            points = fallbackPoints,
            distanceKm = (straightDistKm * 10.0).toInt() / 10.0,
            durationMinutes = fallbackMinutes,
            isFallback = true
        )

        try {
            // OSRM coordinates format: lng,lat;lng,lat;...
            val coordsParam = validPoints.joinToString(";") { "${it.second},${it.first}" }
            val url = "$BASE_URL/route/v1/driving/$coordsParam?overview=full&geometries=geojson"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext fallbackRoute
                val body = response.body?.string() ?: return@withContext fallbackRoute
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext fallbackRoute

                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) return@withContext fallbackRoute

                val primaryRoute = routes.getJSONObject(0)
                val distanceMeters = primaryRoute.optDouble("distance", straightDistKm * 1000.0)
                val durationSeconds = primaryRoute.optDouble("duration", fallbackMinutes * 60.0)

                val geometry = primaryRoute.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                val geoPoints = mutableListOf<GeoPoint>()

                if (coordinates != null && coordinates.length() > 0) {
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        geoPoints.add(GeoPoint(lat, lon))
                    }
                } else {
                    geoPoints.addAll(fallbackPoints)
                }

                val distKm = ((distanceMeters / 1000.0) * 10.0).toInt() / 10.0
                val durationMins = (durationSeconds / 60.0).toInt().coerceAtLeast(1)

                RouteResult(
                    points = geoPoints,
                    distanceKm = distKm,
                    durationMinutes = durationMins,
                    isFallback = false
                )
            }
        } catch (_: Exception) {
            fallbackRoute
        }
    }

    /**
     * Standard Great-Circle (Haversine) distance calculation in km
     */
    fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
