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

    val freeDeliveryThreshold = cartRepository.freeDeliveryThreshold
    val serviceFee = cartRepository.serviceFee

    fun onPromoInputChanged(code: String) {
        _promoInput.value = code
        _promoMessage.value = null
    }

    fun applyPromo() {
        val result = cartRepository.applyPromoCode(_promoInput.value)
        result.onSuccess { msg ->
            _promoMessage.value = msg
        }.onFailure { err ->
            _promoMessage.value = err.message
        }
    }

    fun removePromo() {
        cartRepository.removePromoCode()
        _promoInput.value = ""
        _promoMessage.value = null
    }

    fun onNotesChanged(notes: String) {
        cartRepository.setDeliveryNotes(notes)
    }

    fun addItem(item: MenuItem, restaurant: Restaurant, quantity: Int = 1) {
        cartRepository.addItem(item, restaurant, quantity)
    }

    fun updateQuantity(menuItemId: String, newQuantity: Int) {
        cartRepository.updateQuantity(menuItemId, newQuantity)
    }

    fun removeItem(menuItemId: String) {
        cartRepository.removeItem(menuItemId)
    }

    fun clearCart() {
        cartRepository.clearCart()
        _promoInput.value = ""
        _promoMessage.value = null
    }

    fun getSubtotal(): Double = cartRepository.getSubtotal()
    fun getDeliveryFee(): Double = cartRepository.getDeliveryFee()
    fun getTotal(): Double = cartRepository.getTotal()

    suspend fun placeOrder(
        address: Address,
        customerId: String = "",
        customerName: String = "Delivio Customer",
        customerPhone: String = "+63 917 123 4567",
        cityId: String = "puerto-princesa"
    ): Result<String> {
        val restaurant = currentRestaurant.value
            ?: return Result.failure(IllegalStateException("No merchant selected"))
        val items = cartItems.value
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("Your cart is empty"))
        }

        val subtotal = getSubtotal()
        val deliveryFee = getDeliveryFee()
        val discount = promoDiscount.value
        val total = getTotal()
        val notes = deliveryNotes.value

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
            deliveryAddress = address,
            deliveryLat = address.lat,
            deliveryLng = address.lng,
            deliveryFee = deliveryFee,
            serviceFee = serviceFee,
            discount = discount,
            subtotal = subtotal,
            total = total,
            cityId = cityId,
            deliveryNotes = notes
        )

        // Show the Firestore error message in a snackbar if the write fails (for example permission denied),
        // and do not clear the cart or show "Order placed" until the write succeeds.
        if (result.isSuccess) {
            clearCart()
        }
        return result
    }
}
