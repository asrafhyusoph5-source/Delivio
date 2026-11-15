package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.OsrmRoutingService
import com.example.data.model.LocationPoint
import com.example.data.model.TaxiStop
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaxiUiState(
    val cityId: String = "puerto-princesa",
    val selectedVehicleType: String = "motorcycle",
    val pickupPoint: LocationPoint? = null,
    val dropoffPoint: LocationPoint? = null,
    val nextStops: List<TaxiStop> = emptyList(),
    val routeDistanceKm: Double = 0.0,
    val isCalculatingRoute: Boolean = false,
    val paymentMethod: String = "cod",
    val driverNote: String = "",
    val isBooking: Boolean = false
)

class TaxiViewModel(
    initialCityId: String = "puerto-princesa"
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TaxiUiState(cityId = initialCityId.ifBlank { "puerto-princesa" })
    )
    val uiState: StateFlow<TaxiUiState> = _uiState.asStateFlow()

    private var routeCalcJob: Job? = null

    fun ensureVehicleSupported(availableTypes: List<String>) {
        if (availableTypes.isEmpty()) return
        val current = _uiState.value.selectedVehicleType
        if (availableTypes.none { it == current }) {
            _uiState.update { it.copy(selectedVehicleType = availableTypes.first()) }
        }
    }

    fun selectVehicle(vehicleType: String) {
        _uiState.update { it.copy(selectedVehicleType = vehicleType) }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun setDriverNote(note: String) {
        _uiState.update { it.copy(driverNote = note) }
    }

    fun setBooking(booking: Boolean) {
        _uiState.update { it.copy(isBooking = booking) }
    }

    fun removeStop(index: Int) {
        _uiState.update { state ->
            if (index !in state.nextStops.indices) return@update state
            val updated = state.nextStops.toMutableList().apply { removeAt(index) }
                .mapIndexed { idx, stop -> stop.copy(number = idx + 3) }
            state.copy(nextStops = updated)
        }
        recalculateRoute()
    }

    fun applyPickedLocation(
        stopType: String,
        stopIndex: Int,
        address: String,
        lat: Double,
        lng: Double
    ) {
        val cleanAddress = address.ifBlank { "Pinned location" }
        _uiState.update { state ->
            when (stopType.lowercase().trim()) {
                "pickup" -> {
                    val updatedPickup = (state.pickupPoint ?: LocationPoint(label = "Pick Up")).copy(
                        label = "Pick Up",
                        address = cleanAddress,
                        lat = lat,
                        lng = lng
                    )
                    state.copy(pickupPoint = updatedPickup)
                }
                "dropoff" -> {
                    val updatedDropoff = (state.dropoffPoint ?: LocationPoint(label = "Drop off")).copy(
                        label = "Drop off",
                        address = cleanAddress,
                        lat = lat,
                        lng = lng
                    )
                    state.copy(dropoffPoint = updatedDropoff)
                }
                "stop" -> {
                    val mutableStops = state.nextStops.toMutableList()
                    if (stopIndex in mutableStops.indices) {
                        mutableStops[stopIndex] = mutableStops[stopIndex].copy(
                            address = cleanAddress,
                            lat = lat,
                            lng = lng
                        )
                    } else if (mutableStops.size < 4) {
                        val newNum = mutableStops.size + 3
                        mutableStops.add(
                            TaxiStop(
                                number = newNum,
                                label = "Next Stop",
                                address = cleanAddress,
                                lat = lat,
                                lng = lng
                            )
                        )
                    }
                    state.copy(nextStops = mutableStops)
                }
                else -> state
            }
        }
        recalculateRoute()
    }

    fun recalculateRoute() {
        val state = _uiState.value
        val p = state.pickupPoint
        val d = state.dropoffPoint
        if (p == null || d == null || p.lat == 0.0 || d.lat == 0.0) {
            routeCalcJob?.cancel()
            _uiState.update { it.copy(routeDistanceKm = 0.0, isCalculatingRoute = false) }
            return
        }

        val points = mutableListOf<Pair<Double, Double>>()
        points.add(p.lat to p.lng)
        points.add(d.lat to d.lng)
        state.nextStops.forEach { stop ->
            if (stop.lat != 0.0 && stop.lng != 0.0) {
                points.add(stop.lat to stop.lng)
            }
        }

        routeCalcJob?.cancel()
        _uiState.update { it.copy(isCalculatingRoute = true) }
        routeCalcJob = viewModelScope.launch {
            val res = try {
                OsrmRoutingService.calculateMultiPointRoute(points)
            } catch (_: Exception) {
                null
            }
            val dist = res?.distanceKm ?: 0.0
            _uiState.update { it.copy(routeDistanceKm = dist, isCalculatingRoute = false) }
        }
    }

    class Factory(private val cityId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaxiViewModel(cityId) as T
        }
    }
}
