package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Address
import com.example.data.model.DelivioAddress
import com.example.data.repository.FirestoreService
import com.example.data.repository.PreferenceRepository
import com.example.ui.theme.YellowPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val route: String, val title: String) {
    INBOX("inbox", "Inbox"),
    WALLET("wallet", "Wallet"),
    HOME("home", "Home"),
    CART("cart", "Cart"),
    PROFILE("profile", "Profile")
}

data class MapPickerArgs(
    val title: String,
    val stopType: String, // "pickup", "dropoff", "stop", "cart_delivery_address", "home_delivery_address"
    val stopIndex: Int = 0,
    val initialAddress: String = "",
    val initialLat: Double = 9.7392,
    val initialLng: Double = 118.7353,
    val pinColor: Color = YellowPrimary
)

data class MapPickerResult(
    val stopType: String,
    val stopIndex: Int,
    val address: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(
    val preferenceRepository: PreferenceRepository,
    private val firestoreService: FirestoreService? = null
) : ViewModel() {

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _mapPickerArgs = MutableStateFlow<MapPickerArgs?>(null)
    val mapPickerArgs: StateFlow<MapPickerArgs?> = _mapPickerArgs.asStateFlow()

    private val _mapPickerResult = MutableStateFlow<MapPickerResult?>(null)
    val mapPickerResult: StateFlow<MapPickerResult?> = _mapPickerResult.asStateFlow()

    private var currentCustomerId: String = ""
    private var addressesJob: Job? = null

    fun openMapPicker(
        title: String,
        stopType: String,
        stopIndex: Int = 0,
        initialAddress: String = "",
        initialLat: Double = 9.7392,
        initialLng: Double = 118.7353,
        pinColor: Color = YellowPrimary
    ) {
        _mapPickerArgs.value = MapPickerArgs(
            title = title,
            stopType = stopType,
            stopIndex = stopIndex,
            initialAddress = initialAddress,
            initialLat = initialLat,
            initialLng = initialLng,
            pinColor = pinColor
        )
    }

    fun confirmMapPicker(address: String, lat: Double, lng: Double) {
        val args = _mapPickerArgs.value
        if (args != null) {
            _mapPickerResult.value = MapPickerResult(
                stopType = args.stopType,
                stopIndex = args.stopIndex,
                address = address,
                lat = lat,
                lng = lng
            )
        }
        _mapPickerArgs.value = null
    }

    fun dismissMapPicker() {
        _mapPickerArgs.value = null
    }

    fun clearMapPickerResult() {
        _mapPickerResult.value = null
    }

    val defaultPlaceholderAddress = Address(
        id = "addr_none",
        title = "None",
        fullAddress = "Tap to set or pin location",
        details = "",
        isDefault = false,
        lat = 9.7392,
        lng = 118.7353
    )

    private val _savedAddresses = MutableStateFlow<List<Address>>(emptyList())
    val savedAddresses: StateFlow<List<Address>> = _savedAddresses.asStateFlow()

    private val _currentAddress = MutableStateFlow<Address>(defaultPlaceholderAddress)
    val currentAddress: StateFlow<Address> = _currentAddress.asStateFlow()

    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _lastCityId = MutableStateFlow<String?>(null)
    val lastCityId: StateFlow<String?> = _lastCityId.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            preferenceRepository.isDarkModeFlow.collect { isDark ->
                _isDarkMode.value = isDark
            }
        }
        viewModelScope.launch {
            preferenceRepository.lastCityIdFlow.collect { cityId ->
                _lastCityId.value = cityId
            }
        }
    }

    fun setCustomerId(uid: String) {
        if (uid == currentCustomerId) return
        currentCustomerId = uid
        addressesJob?.cancel()
        if (uid.isBlank()) {
            _savedAddresses.value = emptyList()
            _currentAddress.value = defaultPlaceholderAddress
            return
        }
        val fs = firestoreService ?: return
        addressesJob = viewModelScope.launch {
            fs.getAddressesFlow(uid).collect { remoteList ->
                val mapped = remoteList.map { d ->
                    Address(
                        id = d.id,
                        title = d.label.ifBlank { "Home" },
                        fullAddress = d.address,
                        details = d.details,
                        isDefault = d.isDefault,
                        lat = d.lat,
                        lng = d.lng
                    )
                }.take(3)
                _savedAddresses.value = mapped
                val cur = _currentAddress.value
                if (cur.id == "addr_none") {
                    if (mapped.isNotEmpty()) {
                        _currentAddress.value = mapped.firstOrNull { it.isDefault } ?: mapped.first()
                    }
                } else if (!cur.id.startsWith("addr_order_")) {
                    val refreshed = mapped.find { it.id == cur.id }
                    if (refreshed != null) {
                        _currentAddress.value = refreshed
                    } else if (mapped.none { it.id == cur.id } && mapped.isNotEmpty()) {
                        // Keep ephemeral or manual unsynced addresses, unless a synced address was deleted
                    }
                }
            }
        }
    }

    fun setLastCityId(cityId: String) {
        viewModelScope.launch {
            _lastCityId.value = cityId
            preferenceRepository.setLastCityId(cityId)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            _isDarkMode.value = enabled
            preferenceRepository.setDarkMode(enabled)
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setAddress(address: Address) {
        _currentAddress.value = address
        showSnackbar("Delivery address set to ${address.title}")
    }

    /**
     * Applies a confirmed address from the "Delivery details" bottom sheet.
     * Writes to users/{uid}/addresses ONLY when saveToMyAddresses is true and fewer than 3
     * addresses are saved; either way sets this address as the active delivery address.
     */
    fun confirmDeliveryDetails(
        addressText: String,
        label: String,
        landmarkOrUnit: String = "",
        lat: Double = 9.7392,
        lng: Double = 118.7353,
        saveToMyAddresses: Boolean = true
    ): Address {
        val cleanAddress = addressText.trim()
        val cleanLabel = label.trim().ifEmpty { "Home" }
        val cleanDetails = landmarkOrUnit.trim()
        val canSave = saveToMyAddresses && _savedAddresses.value.size < 3

        if (canSave) {
            val newId = "addr_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
            val isFirst = _savedAddresses.value.isEmpty()
            val savedAddress = Address(
                id = newId,
                title = cleanLabel,
                fullAddress = cleanAddress,
                details = cleanDetails,
                isDefault = isFirst,
                lat = lat,
                lng = lng
            )
            _savedAddresses.value = (_savedAddresses.value + savedAddress).take(3)
            _currentAddress.value = savedAddress
            val uid = currentCustomerId
            val fs = firestoreService
            if (fs != null && uid.isNotBlank()) {
                viewModelScope.launch {
                    fs.saveAddress(
                        uid,
                        DelivioAddress(
                            id = newId,
                            label = cleanLabel,
                            address = cleanAddress,
                            details = cleanDetails,
                            lat = lat,
                            lng = lng,
                            isDefault = isFirst
                        )
                    )
                }
            }
            showSnackbar("Address '${savedAddress.title}' saved and set as delivery address")
            return savedAddress
        } else {
            val orderAddress = Address(
                id = "addr_order_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}",
                title = cleanLabel,
                fullAddress = cleanAddress,
                details = cleanDetails,
                isDefault = false,
                lat = lat,
                lng = lng
            )
            _currentAddress.value = orderAddress
            showSnackbar("Delivery address set to ${orderAddress.title}")
            return orderAddress
        }
    }

    /**
     * Adds an address to saved addresses list.
     * Enforces a hard maximum of 3 saved addresses.
     */
    fun addManualAddress(
        title: String,
        fullAddress: String,
        details: String = "",
        setAsActive: Boolean = true,
        lat: Double = 9.7392,
        lng: Double = 118.7353
    ): Address? {
        if (_savedAddresses.value.size >= 3) {
            showSnackbar("Maximum of 3 saved addresses reached. Please edit or delete an existing address.")
            return null
        }

        val newAddress = Address(
            id = "addr_manual_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}",
            title = title.trim().ifEmpty { "Saved Address" },
            fullAddress = fullAddress.trim(),
            details = details.trim(),
            isDefault = setAsActive,
            lat = lat,
            lng = lng
        )

        // Automatically save into Saved Addresses (capped at 3)
        _savedAddresses.value = (_savedAddresses.value + newAddress).take(3)
        val uid = currentCustomerId
        val fs = firestoreService
        if (fs != null && uid.isNotBlank()) {
            viewModelScope.launch {
                fs.saveAddress(
                    uid,
                    DelivioAddress(
                        id = newAddress.id,
                        label = newAddress.title,
                        address = newAddress.fullAddress,
                        details = newAddress.details,
                        lat = newAddress.lat,
                        lng = newAddress.lng,
                        isDefault = newAddress.isDefault
                    )
                )
            }
        }

        if (setAsActive || _savedAddresses.value.size == 1) {
            _currentAddress.value = newAddress
            showSnackbar("Address '${newAddress.title}' saved to your addresses & set as active!")
        } else {
            showSnackbar("Address '${newAddress.title}' saved to your addresses")
        }
        return newAddress
    }

    /**
     * Updates an existing saved address by ID.
     */
    fun updateAddress(
        id: String,
        title: String,
        fullAddress: String,
        details: String = "",
        setAsActive: Boolean = false,
        lat: Double = 9.7392,
        lng: Double = 118.7353
    ) {
        val updated = Address(
            id = id,
            title = title.trim().ifEmpty { "Saved Address" },
            fullAddress = fullAddress.trim(),
            details = details.trim(),
            isDefault = setAsActive,
            lat = lat,
            lng = lng
        )

        _savedAddresses.value = _savedAddresses.value.map { existing ->
            if (existing.id == id) updated else existing
        }
        val uid = currentCustomerId
        val fs = firestoreService
        if (fs != null && uid.isNotBlank()) {
            viewModelScope.launch {
                fs.saveAddress(
                    uid,
                    DelivioAddress(
                        id = updated.id,
                        label = updated.title,
                        address = updated.fullAddress,
                        details = updated.details,
                        lat = updated.lat,
                        lng = updated.lng,
                        isDefault = updated.isDefault
                    )
                )
            }
        }

        if (setAsActive || _currentAddress.value.id == id) {
            _currentAddress.value = updated
        }
        showSnackbar("Address '${updated.title}' updated successfully!")
    }

    /**
     * Removes an address from saved addresses list.
     */
    fun deleteAddress(id: String) {
        val target = _savedAddresses.value.find { it.id == id }
        _savedAddresses.value = _savedAddresses.value.filterNot { it.id == id }
        val uid = currentCustomerId
        val fs = firestoreService
        if (fs != null && uid.isNotBlank()) {
            viewModelScope.launch {
                fs.deleteAddress(uid, id)
            }
        }

        if (_currentAddress.value.id == id) {
            _currentAddress.value = _savedAddresses.value.firstOrNull() ?: defaultPlaceholderAddress
        }
        showSnackbar("Address '${target?.title ?: "Address"}' deleted")
    }

    fun showSnackbar(message: String) {
        _snackbarMessages.tryEmit(message)
    }
}
