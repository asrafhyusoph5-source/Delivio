package com.example.util

import android.util.Log
import com.example.data.api.OsrmRoutingService
import com.example.data.model.AppDesignerSettings
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.PlatformSettings
import java.util.Locale
import kotlin.math.roundToInt

data class AvailabilityLocation(
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val checkPickup: Boolean = true,
    val checkDropoff: Boolean = true
)

data class AvailabilityCheck(
    val key: String,
    val label: String,
    val passed: Boolean,
    val value: String
)

data class AvailabilityResult(
    val ok: Boolean,
    val reason: String,
    val checks: List<AvailabilityCheck> = emptyList(),
    val resolvedCity: City? = null,
    val service: String = "",
    val vehicle: String? = null
)

/**
 * Single source-of-truth service availability check shared by every Delivio service
 * (`food`, `convenience`, `parcel`, `pabili`, `taxi`, `jobfair`).
 */
object ServiceAvailability {
    const val TAG = "AVAIL"

    fun resolveCity(
        selectedCityId: String?,
        cities: List<City>
    ): City? {
        return CityDeliveryRateResolver.findMatchingCity(selectedCityId, cities)
    }

    fun availabilityForCityId(
        selectedCityId: String?,
        cities: List<City>,
        service: String,
        vehicle: String? = null,
        location: AvailabilityLocation? = null,
        platformSettings: PlatformSettings = PlatformSettings(),
        deliveryRates: DeliveryRates? = null,
        logResult: Boolean = true
    ): AvailabilityResult {
        val resolvedCity = resolveCity(selectedCityId, cities)
        return availability(
            city = resolvedCity,
            service = service,
            vehicle = vehicle,
            location = location,
            platformSettings = platformSettings,
            deliveryRates = deliveryRates,
            selectedCityLabel = selectedCityId,
            logResult = logResult
        )
    }

    fun availability(
        city: City?,
        service: String,
        vehicle: String? = null,
        location: AvailabilityLocation? = null,
        platformSettings: PlatformSettings = PlatformSettings(),
        deliveryRates: DeliveryRates? = null,
        selectedCityLabel: String? = null,
        logResult: Boolean = true,
        appDesignerSettings: AppDesignerSettings? = null
    ): AvailabilityResult {
        val svc = service.trim().lowercase(Locale.ROOT)
        val rawVehicle = vehicle?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
        val effectiveVehicle = when {
            svc == "parcel" -> (rawVehicle ?: "motorcycle")
            svc == "taxi" -> rawVehicle
            else -> null
        }

        val checks = mutableListOf<AvailabilityCheck>()
        var firstFailureReason: String? = null

        fun fail(reason: String) {
            if (firstFailureReason == null) firstFailureReason = reason
        }

        // 1. City exists and is active
        val cityExists = city != null
        val cityName = city?.name?.trim()?.ifBlank { city.id.trim() }
            ?: selectedCityLabel?.trim()?.ifBlank { null }
            ?: "your city"
        checks.add(
            AvailabilityCheck(
                key = "city_exists",
                label = "City exists",
                passed = cityExists,
                value = if (city != null) "${city.name.ifBlank { city.id }} (${city.id})" else "Not found (${selectedCityLabel ?: "none"})"
            )
        )
        if (!cityExists) {
            fail("Your selected city is not available in Delivio yet")
        }

        val cityActive = city != null && city.active && city.status.trim().equals("active", ignoreCase = true)
        checks.add(
            AvailabilityCheck(
                key = "city_active",
                label = "City active",
                passed = cityActive,
                value = when {
                    city == null -> "missing"
                    !city.active -> "active=false"
                    else -> city.status
                }
            )
        )
        if (cityExists && !cityActive) {
            fail("Delivio is currently switched off in $cityName")
        }

        // 1b. App Designer service visibility (settings/appDesigner.services.<service>.visible)
        if (appDesignerSettings != null && svc in AppDesignerParser.SERVICE_IDS) {
            val designerVisible = appDesignerSettings.isServiceVisible(svc)
            checks.add(
                AvailabilityCheck(
                    key = "app_designer_visible",
                    label = "App Designer visible ($svc)",
                    passed = designerVisible,
                    value = designerVisible.toString()
                )
            )
            if (!designerVisible) {
                fail("${serviceDisplayName(svc)} is currently unavailable")
            }
        }

        // 2. settings/platform.services.<service> is not false (missing counts as on)
        val platformHasKey = platformSettings.services.containsKey(svc)
        val platformSvcOn = platformSettings.services[svc] != false
        checks.add(
            AvailabilityCheck(
                key = "platform_service",
                label = "Platform service ($svc)",
                passed = platformSvcOn,
                value = if (platformHasKey) platformSvcOn.toString() else "true (default)"
            )
        )
        if (!platformSvcOn) {
            fail("${serviceDisplayName(svc)} is currently switched off platform-wide")
        }

        // 3. city.services.<service> is not false (missing counts as on)
        val cityHasKey = city?.services?.containsKey(svc) == true
        val citySvcOn = city == null || city.services[svc] != false
        checks.add(
            AvailabilityCheck(
                key = "city_service",
                label = "City service ($svc)",
                passed = city != null && citySvcOn,
                value = when {
                    city == null -> "unknown"
                    cityHasKey -> citySvcOn.toString()
                    else -> "true (default)"
                }
            )
        )
        if (city != null && !citySvcOn) {
            fail("${serviceDisplayName(svc)} is switched off in $cityName")
        }

        // 4 & 5. Rates exist for the service and vehicle (Schema 2 or Schema 1) & vehicle active flag for taxi
        val isRateRequiredService = svc in setOf("food", "convenience", "parcel", "pabili", "taxi", "instant")
        if (isRateRequiredService) {
            val ratesPresent = hasRatesFor(deliveryRates, svc, effectiveVehicle)
            val rateValueSummary = describeRatesStatus(deliveryRates, svc, effectiveVehicle)
            checks.add(
                AvailabilityCheck(
                    key = "delivery_rates",
                    label = if (effectiveVehicle != null) "Rates ($svc / $effectiveVehicle)" else "Rates ($svc)",
                    passed = ratesPresent,
                    value = rateValueSummary
                )
            )
            if (!ratesPresent) {
                if (effectiveVehicle != null && svc == "taxi") {
                    fail("${vehicleDisplayName(effectiveVehicle)} rides are not set up in your area yet")
                } else if (effectiveVehicle != null && svc == "parcel") {
                    fail("${vehicleDisplayName(effectiveVehicle)} parcel rates are not set up in your area yet")
                } else {
                    fail("No fares are set for this service")
                }
            }

            if (svc == "taxi" && effectiveVehicle != null) {
                val platformVehicleOn = platformSettings.taxiVehicles[effectiveVehicle] != false
                val cityVehicleActive = resolveTaxiVehicleActive(deliveryRates, effectiveVehicle)
                val vehicleEnabled = platformVehicleOn && cityVehicleActive
                val activeDisplay = when {
                    !platformVehicleOn -> "false (platform)"
                    deliveryRates?.isSchema2 == true -> {
                        val s2 = deliveryRates.getSchema2TaxiVehicle(effectiveVehicle)
                        if (s2 == null) "true (default)" else s2.active.toString()
                    }
                    deliveryRates != null -> {
                        val r = deliveryRates.rideRates[effectiveVehicle]
                        if (r == null) "true (default)" else r.enabled.toString()
                    }
                    else -> "unknown"
                }
                checks.add(
                    AvailabilityCheck(
                        key = "vehicle_active",
                        label = "${vehicleDisplayName(effectiveVehicle)} active",
                        passed = vehicleEnabled,
                        value = activeDisplay
                    )
                )
                if (ratesPresent && !vehicleEnabled) {
                    fail("${vehicleDisplayName(effectiveVehicle)} rides are switched off in $cityName")
                }
            } else if (svc == "parcel" && effectiveVehicle != null) {
                val parcelVehEnabled = resolveParcelVehicleEnabled(deliveryRates, effectiveVehicle)
                checks.add(
                    AvailabilityCheck(
                        key = "vehicle_active",
                        label = "${vehicleDisplayName(effectiveVehicle)} active",
                        passed = parcelVehEnabled,
                        value = parcelVehEnabled.toString()
                    )
                )
                if (ratesPresent && !parcelVehEnabled) {
                    fail("${vehicleDisplayName(effectiveVehicle)} parcel delivery is switched off in $cityName")
                }
            }
        }

        // 6. Pickup and drop-off pins are inside the city radius
        val radiusKm = city?.radiusKm?.takeIf { it > 0.0 } ?: 25.0
        if (city != null && location != null) {
            val pLat = location.pickupLat
            val pLng = location.pickupLng
            if (location.checkPickup && pLat != null && pLng != null && (pLat != 0.0 || pLng != 0.0)) {
                val pickupDistKm = OsrmRoutingService.calculateHaversineDistanceKm(pLat, pLng, city.lat, city.lng)
                val pickupOk = pickupDistKm <= radiusKm
                checks.add(
                    AvailabilityCheck(
                        key = "pickup_radius",
                        label = "Pickup within radius",
                        passed = pickupOk,
                        value = "${formatKm(pickupDistKm)} km / ${formatKm(radiusKm)} km limit"
                    )
                )
                if (!pickupOk) {
                    fail("Your pickup point is ${formatKm(pickupDistKm)} km from $cityName (limit ${formatKm(radiusKm)} km)")
                }
            }

            val dLat = location.dropoffLat
            val dLng = location.dropoffLng
            if (location.checkDropoff && dLat != null && dLng != null && (dLat != 0.0 || dLng != 0.0)) {
                val dropoffDistKm = OsrmRoutingService.calculateHaversineDistanceKm(dLat, dLng, city.lat, city.lng)
                val dropoffOk = dropoffDistKm <= radiusKm
                checks.add(
                    AvailabilityCheck(
                        key = "dropoff_radius",
                        label = "Drop-off within radius",
                        passed = dropoffOk,
                        value = "${formatKm(dropoffDistKm)} km / ${formatKm(radiusKm)} km limit"
                    )
                )
                if (!dropoffOk) {
                    fail("Your drop-off point is ${formatKm(dropoffDistKm)} km from $cityName (limit ${formatKm(radiusKm)} km)")
                }
            }
        }

        val ok = firstFailureReason == null && checks.all { it.passed }
        val result = AvailabilityResult(
            ok = ok,
            reason = firstFailureReason ?: "",
            checks = checks,
            resolvedCity = city,
            service = svc,
            vehicle = effectiveVehicle
        )

        if (logResult) {
            logAvailability(result)
        }
        return result
    }

    private fun hasRatesFor(
        rates: DeliveryRates?,
        svc: String,
        vehicle: String?
    ): Boolean {
        if (rates == null) return false
        if (rates.fromFirestore && !rates.ratesDocExists) return false

        // When parsed from an actual Firestore document, check exact presence in Schema 2 or Schema 1 maps
        if (rates.fromFirestore) {
            if (rates.isSchema2) {
                return when (svc) {
                    "food", "convenience", "pabili", "instant" -> rates.servicesSchema2.containsKey(svc)
                    "parcel" -> {
                        val v = vehicle ?: "motorcycle"
                        rates.parcelSchema2.containsKey(v) || rates.configuredParcelVehicles.contains(v)
                    }
                    "taxi" -> {
                        if (vehicle.isNullOrBlank()) rates.taxiSchema2.isNotEmpty()
                        else rates.taxiSchema2.containsKey(vehicle)
                    }
                    else -> true
                }
            } else {
                return when (svc) {
                    "food", "convenience", "pabili", "instant" -> rates.configuredServices.contains(svc)
                    "parcel" -> {
                        val v = vehicle ?: "motorcycle"
                        rates.configuredServices.contains("parcel") && rates.configuredParcelVehicles.contains(v)
                    }
                    "taxi" -> {
                        if (vehicle.isNullOrBlank()) rates.configuredTaxiVehicles.isNotEmpty()
                        else rates.configuredTaxiVehicles.contains(vehicle)
                    }
                    else -> true
                }
            }
        }

        // In-memory / test DeliveryRates object
        if (rates.id.isBlank() && rates.cityId.isBlank() &&
            rates.servicesSchema2.isEmpty() && rates.parcelSchema2.isEmpty() && rates.taxiSchema2.isEmpty()
        ) {
            return false
        }
        return if (rates.isSchema2) {
            when (svc) {
                "food", "convenience", "pabili", "instant" -> rates.servicesSchema2.containsKey(svc)
                "parcel" -> rates.parcelSchema2.containsKey(vehicle ?: "motorcycle")
                "taxi" -> if (vehicle.isNullOrBlank()) rates.taxiSchema2.isNotEmpty() else rates.taxiSchema2.containsKey(vehicle)
                else -> true
            }
        } else {
            when (svc) {
                "food" -> rates.food.baseFare > 0.0 || rates.food.minFee > 0.0
                "convenience" -> rates.convenience.baseFare > 0.0 || rates.convenience.minFee > 0.0
                "pabili" -> rates.pabili.baseFare > 0.0 || rates.pabili.minFee > 0.0
                "parcel" -> {
                    val v = vehicle ?: "motorcycle"
                    if (v == "sedan") rates.parcelSedan != null else (rates.parcelMotorcycle != null || rates.parcel.baseFare > 0.0)
                }
                "taxi" -> {
                    if (vehicle.isNullOrBlank()) rates.rideRates.isNotEmpty()
                    else rates.rideRates.containsKey(vehicle)
                }
                else -> true
            }
        }
    }

    private fun describeRatesStatus(
        rates: DeliveryRates?,
        svc: String,
        vehicle: String?
    ): String {
        if (rates == null) return "missing"
        if (rates.fromFirestore && !rates.ratesDocExists) return "missing doc (deliveryRates/${rates.cityId.ifBlank { "?" }})"
        val schemaTag = if (rates.isSchema2) "schema 2" else "schema 1"
        val present = hasRatesFor(rates, svc, vehicle)
        return if (present) {
            "found ($schemaTag, ${rates.id.ifBlank { rates.cityId }})"
        } else {
            "missing ($schemaTag)"
        }
    }

    private fun resolveTaxiVehicleActive(rates: DeliveryRates?, vehicle: String): Boolean {
        if (rates == null) return true
        return if (rates.isSchema2) {
            rates.getSchema2TaxiVehicle(vehicle)?.active != false
        } else {
            rates.rideRates[vehicle]?.enabled != false
        }
    }

    private fun resolveParcelVehicleEnabled(rates: DeliveryRates?, vehicle: String): Boolean {
        if (rates == null) return true
        return if (rates.isSchema2) {
            rates.getSchema2ParcelVehicle(vehicle)?.active != false
        } else {
            if (vehicle == "sedan") rates.parcelSedan?.enabled ?: false
            else rates.getParcelMotorcycleRate().enabled
        }
    }

    private fun serviceDisplayName(svc: String): String = when (svc) {
        "food" -> "Delivio Food"
        "convenience" -> "Delivio Convenience"
        "parcel" -> "Delivio Parcel"
        "pabili" -> "Delivio Pabili"
        "taxi" -> "Delivio Taxi"
        "jobfair" -> "Job Fair"
        "flight" -> "Delivio Flight"
        "loans" -> "Delivio Loans"
        else -> "This service"
    }

    private fun vehicleDisplayName(vehicle: String): String = when (vehicle.lowercase(Locale.ROOT)) {
        "motorcycle" -> "Motorcycle"
        "tricycle" -> "Tricycle"
        "car4" -> "4-Seater Car"
        "car8" -> "8-Seater Car"
        "sedan" -> "Sedan"
        else -> vehicle.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    private fun formatKm(km: Double): String {
        val rounded = km.roundToInt()
        return if (kotlin.math.abs(km - rounded.toDouble()) < 0.25 || km >= 10.0) {
            rounded.toString()
        } else {
            String.format(Locale.US, "%.1f", km)
        }
    }

    fun logAvailability(result: AvailabilityResult) {
        val checkSummary = result.checks.joinToString("; ") { c ->
            "${c.key}=${c.passed}(${c.value})"
        }
        val msg = "ok=${result.ok} service=${result.service} vehicle=${result.vehicle ?: "none"} " +
            "city=${result.resolvedCity?.id ?: "null"} reason=\"${result.reason}\" checks=[$checkSummary]"
        try {
            if (result.ok) {
                Log.d(TAG, msg)
            } else {
                Log.w(TAG, msg)
            }
        } catch (_: Exception) {
            // Safe no-op in plain JVM unit test environments
        }
    }
}
