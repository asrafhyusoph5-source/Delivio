package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.datasource.MockData
import com.example.data.model.Address
import com.example.data.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val route: String, val title: String) {
    SHOP("shop", "Shop"),
    SEARCH("search", "Search"),
    HOME("home", "Home"),
    CART("cart", "Cart"),
    PROFILE("profile", "Profile")
}

class MainViewModel(
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    val defaultPlaceholderAddress = Address(
        id = "addr_none",
        title = "None",
        fullAddress = "No address selected. Tap to set or pin location",
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

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            preferenceRepository.isDarkModeFlow.collect { isDark ->
                _isDarkMode.value = isDark
            }
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

        if (_currentAddress.value.id == id) {
            _currentAddress.value = _savedAddresses.value.firstOrNull() ?: defaultPlaceholderAddress
        }
        showSnackbar("Address '${target?.title ?: "Address"}' deleted")
    }

    fun showSnackbar(message: String) {
        _snackbarMessages.tryEmit(message)
    }
}
