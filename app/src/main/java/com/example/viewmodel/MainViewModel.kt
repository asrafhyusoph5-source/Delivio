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

    private val _currentAddress = MutableStateFlow(MockData.mockAddresses.first())
    val currentAddress: StateFlow<Address> = _currentAddress.asStateFlow()

    private val _savedAddresses = MutableStateFlow<List<Address>>(MockData.mockAddresses)
    val savedAddresses: StateFlow<List<Address>> = _savedAddresses.asStateFlow()

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

    fun addManualAddress(
        title: String,
        fullAddress: String,
        details: String = "",
        setAsActive: Boolean = true
    ): Address {
        val newAddress = Address(
            id = "addr_manual_${System.currentTimeMillis()}",
            title = title.trim().ifEmpty { "Manual Address" },
            fullAddress = fullAddress.trim(),
            details = details.trim(),
            isDefault = setAsActive
        )
        // Add to saved addresses list
        _savedAddresses.value = listOf(newAddress) + _savedAddresses.value
        if (setAsActive) {
            _currentAddress.value = newAddress
            showSnackbar("Address '${newAddress.title}' saved and set as active!")
        } else {
            showSnackbar("Address '${newAddress.title}' saved to your addresses")
        }
        return newAddress
    }

    fun showSnackbar(message: String) {
        _snackbarMessages.tryEmit(message)
    }
}
