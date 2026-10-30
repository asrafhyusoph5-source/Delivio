package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Restaurant
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CartViewModel(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    val cartItems: StateFlow<List<CartItem>> = cartRepository.cartItems
    val currentRestaurant: StateFlow<Restaurant?> = cartRepository.currentRestaurant
    val appliedPromoCode: StateFlow<String?> = cartRepository.appliedPromoCode
    val promoDiscount: StateFlow<Double> = cartRepository.promoDiscount
    val deliveryNotes: StateFlow<String> = cartRepository.deliveryNotes

    private val _promoInput = MutableStateFlow("")
    val promoInput: StateFlow<String> = _promoInput.asStateFlow()

    private val _promoMessage = MutableStateFlow<String?>(null)
    val promoMessage: StateFlow<String?> = _promoMessage.asStateFlow()

    private val _fulfillment = MutableStateFlow(com.example.util.DelivioStatusConstants.FULFILLMENT_DELIVERY)
    val fulfillment: StateFlow<String> = _fulfillment.asStateFlow()

    private val _scheduledFor = MutableStateFlow<Long?>(null)
    val scheduledFor: StateFlow<Long?> = _scheduledFor.asStateFlow()

    private val _tipAmount = MutableStateFlow(0.0)
    val tipAmount: StateFlow<Double> = _tipAmount.asStateFlow()

    val freeDeliveryThreshold = cartRepository.freeDeliveryThreshold
    val serviceFee = cartRepository.serviceFee

    private val _deliveryRates = MutableStateFlow(orderRepository.firestoreService.defaultDeliveryRates)
    val deliveryRates: StateFlow<com.example.data.model.DeliveryRates> = _deliveryRates.asStateFlow()

    private val _platformSettings = MutableStateFlow(com.example.data.model.PlatformSettings())
    val platformSettings: StateFlow<com.example.data.model.PlatformSettings> = _platformSettings.asStateFlow()

    private val _feesResult = MutableStateFlow(
        com.example.util.FeeCalculator.calculateFees(
            subtotal = 0.0,
            deliveryFee = 0.0
        )
    )
    val feesResult: StateFlow<com.example.util.FeeCalculator.FeeCalculationResult> = _feesResult.asStateFlow()

    init {
        viewModelScope.launch {
            orderRepository.firestoreService.getDeliveryRatesFlow("puerto-princesa").collect { rates ->
                _deliveryRates.value = rates
                recalculateFees()
            }
        }
        viewModelScope.launch {
            orderRepository.firestoreService.getPlatformSettingsFlow().collect { settings ->
                _platformSettings.value = settings
                recalculateFees()
            }
        }
        viewModelScope.launch {
            cartRepository.cartItems.collect { recalculateFees() }
        }
        viewModelScope.launch {
            cartRepository.promoDiscount.collect { recalculateFees() }
        }
        viewModelScope.launch {
            cartRepository.currentRestaurant.collect { recalculateFees() }
        }
    }

    private fun recalculateFees() {
        val subtotal = cartRepository.getSubtotal()
        val isPickup = _fulfillment.value == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP
        val rawDeliveryFee = if (isPickup) 0.0 else cartRepository.getDeliveryFee()
        val rawTip = if (isPickup) 0.0 else _tipAmount.value
        val discount = promoDiscount.value
        val isConv = currentRestaurant.value?.isConvenience == true
        val rates = _deliveryRates.value
        val rateConfig = if (isConv) rates.convenience else rates.food
        val vatConfig = _platformSettings.value.vat

        val res = com.example.util.FeeCalculator.calculateForRateConfig(
            subtotal = subtotal,
            deliveryFee = rawDeliveryFee,
            rateConfig = rateConfig,
            tip = rawTip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = isPickup
        )
        _feesResult.value = res
    }

    fun setFulfillment(mode: String) {
        _fulfillment.value = mode
        if (mode == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP) {
            _tipAmount.value = 0.0
        }
        recalculateFees()
    }

    fun setScheduledFor(timeMs: Long?) {
        _scheduledFor.value = timeMs
    }

    fun setTip(amount: Double) {
        if (_fulfillment.value != com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP) {
            _tipAmount.value = amount.coerceIn(0.0, 500.0)
        } else {
            _tipAmount.value = 0.0
        }
        recalculateFees()
    }

    fun onPromoInputChanged(code: String) {
        _promoInput.value = code
        _promoMessage.value = null
    }

    fun applyPromo() {
        val result = cartRepository.applyPromoCode(_promoInput.value)
        result.onSuccess { msg ->
            _promoMessage.value = msg
            recalculateFees()
        }.onFailure { err ->
            _promoMessage.value = err.message
        }
    }

    fun removePromo() {
        cartRepository.removePromoCode()
        _promoInput.value = ""
        _promoMessage.value = null
        recalculateFees()
    }

    fun onNotesChanged(notes: String) {
        cartRepository.setDeliveryNotes(notes)
    }

    fun addItem(item: MenuItem, restaurant: Restaurant, quantity: Int = 1) {
        cartRepository.addItem(item, restaurant, quantity)
        recalculateFees()
    }

    fun updateQuantity(menuItemId: String, newQuantity: Int) {
        cartRepository.updateQuantity(menuItemId, newQuantity)
        recalculateFees()
    }

    fun removeItem(menuItemId: String) {
        cartRepository.removeItem(menuItemId)
        recalculateFees()
    }

    fun clearCart() {
        cartRepository.clearCart()
        _promoInput.value = ""
        _promoMessage.value = null
        _tipAmount.value = 0.0
        _scheduledFor.value = null
        _fulfillment.value = com.example.util.DelivioStatusConstants.FULFILLMENT_DELIVERY
        recalculateFees()
    }

    fun getSubtotal(): Double = cartRepository.getSubtotal()

    fun getDeliveryFee(): Double {
        if (_fulfillment.value == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP) return 0.0
        return _feesResult.value.deliveryFee
    }

    fun getTotal(): Double {
        return _feesResult.value.total
    }

    suspend fun placeOrder(
        address: Address?,
        customerId: String = "",
        customerName: String = "Delivio Customer",
        customerPhone: String = "",
        cityId: String = "puerto-princesa",
        paymentMethod: String = "cod"
    ): Result<String> {
        val restaurant = currentRestaurant.value
            ?: return Result.failure(IllegalStateException("No merchant selected"))
        val items = cartItems.value
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("Your cart is empty"))
        }

        val mode = _fulfillment.value
        val isPickup = mode == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP

        if (!isPickup && (address == null || address.id == "addr_none")) {
            return Result.failure(IllegalStateException("Please set or pin your delivery address first!"))
        }

        if (mode == com.example.util.DelivioStatusConstants.FULFILLMENT_SCHEDULED) {
            val sched = _scheduledFor.value
            if (sched == null) {
                return Result.failure(IllegalStateException("Please select a date and time for scheduled delivery."))
            }
            val minTime = System.currentTimeMillis() + 45 * 60 * 1000 - 60000 // allow small grace
            if (sched < minTime) {
                return Result.failure(IllegalStateException("Scheduled time must be at least 45 minutes ahead."))
            }
        }

        recalculateFees()
        val currentFees = _feesResult.value

        val fbUid = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }

        val result = orderRepository.placeFoodOrConvenienceOrder(
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            restaurant = restaurant,
            items = items,
            deliveryAddress = if (isPickup) null else address,
            deliveryLat = if (isPickup) 0.0 else (address?.lat ?: 0.0),
            deliveryLng = if (isPickup) 0.0 else (address?.lng ?: 0.0),
            deliveryFee = currentFees.deliveryFee,
            serviceFee = currentFees.serviceFee,
            discount = currentFees.discount,
            subtotal = currentFees.subtotal,
            total = currentFees.total,
            cityId = cityId,
            deliveryNotes = deliveryNotes.value,
            paymentMethod = paymentMethod,
            fulfillment = mode,
            scheduledFor = _scheduledFor.value,
            tip = currentFees.tip,
            orderFees = currentFees.fees
        )

        // Show the Firestore error message in a snackbar if the write fails (for example permission denied),
        // and do not clear the cart or show "Order placed" until the write succeeds.
        if (result.isSuccess) {
            clearCart()
        }
        return result
    }
}
