package com.example.data.repository

import com.example.data.model.CartItem
import com.example.data.model.MenuItem
import com.example.data.model.Restaurant
import com.example.data.model.SelectedItemChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class CartRepository {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _currentRestaurant = MutableStateFlow<Restaurant?>(null)
    val currentRestaurant: StateFlow<Restaurant?> = _currentRestaurant.asStateFlow()

    private val _appliedPromoCode = MutableStateFlow<String?>(null)
    val appliedPromoCode: StateFlow<String?> = _appliedPromoCode.asStateFlow()

    private val _promoDiscount = MutableStateFlow(0.0)
    val promoDiscount: StateFlow<Double> = _promoDiscount.asStateFlow()

    private val _deliveryNotes = MutableStateFlow("")
    val deliveryNotes: StateFlow<String> = _deliveryNotes.asStateFlow()

    val freeDeliveryThreshold = 250.00
    val serviceFee = 10.00

    private val _unavailableChoicesCache = MutableStateFlow<Set<String>>(emptySet())
    val unavailableChoicesCache: StateFlow<Set<String>> = _unavailableChoicesCache.asStateFlow()

    fun markChoiceAvailability(itemId: String, setId: String, choiceId: String, available: Boolean) {
        val key = "$itemId:$setId:$choiceId"
        val current = _unavailableChoicesCache.value.toMutableSet()
        if (available) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _unavailableChoicesCache.value = current
    }

    fun isChoiceAvailable(item: CartItem, choice: SelectedItemChoice): Boolean {
        val key = "${item.menuItem.id}:${choice.setId}:${choice.choiceId}"
        if (_unavailableChoicesCache.value.contains(key)) return false

        val set = item.menuItem.sets.find { it.id == choice.setId }
        val itemChoice = set?.choices?.find { it.id == choice.choiceId }
        if (itemChoice != null) {
            return itemChoice.available
        }
        return true
    }

    fun addItem(
        menuItem: MenuItem,
        restaurant: Restaurant,
        quantity: Int = 1,
        selections: List<SelectedItemChoice> = emptyList(),
        specialInstructions: String = ""
    ): Boolean {
        if (!com.example.util.BusinessHoursUtil.effectiveOpen(restaurant)) {
            return false
        }
        val currentItems = _cartItems.value.toMutableList()

        // If cart has items from another restaurant, reset cart to new restaurant
        if (_currentRestaurant.value != null && _currentRestaurant.value?.id != restaurant.id) {
            currentItems.clear()
        }
        _currentRestaurant.value = restaurant

        val existingIndex = currentItems.indexOfFirst {
            it.hasSameConfiguration(menuItem.id, selections)
        }
        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            currentItems[existingIndex] = existing.copy(quantity = existing.quantity + quantity)
        } else {
            currentItems.add(
                CartItem(
                    menuItem = menuItem,
                    restaurantId = restaurant.id,
                    restaurantName = restaurant.name,
                    quantity = quantity,
                    specialInstructions = specialInstructions,
                    selections = selections,
                    basePrice = menuItem.price
                )
            )
        }
        _cartItems.value = currentItems
        recalculatePromo()
        return true
    }

    fun updateCartItemSelections(
        cartItemId: String,
        newSelections: List<SelectedItemChoice>,
        newQuantity: Int
    ) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == cartItemId }
        if (index >= 0) {
            val current = currentItems[index]
            currentItems[index] = current.copy(
                selections = newSelections,
                quantity = newQuantity
            )
            _cartItems.value = currentItems
            recalculatePromo()
        }
    }

    fun updateQuantity(id: String, newQuantity: Int) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            ?: currentItems.indexOfFirst { it.menuItem.id == id }
        if (index >= 0) {
            if (newQuantity <= 0) {
                currentItems.removeAt(index)
            } else {
                currentItems[index] = currentItems[index].copy(quantity = newQuantity)
            }
        }
        if (currentItems.isEmpty()) {
            _currentRestaurant.value = null
            _appliedPromoCode.value = null
            _promoDiscount.value = 0.0
        }
        _cartItems.value = currentItems
        recalculatePromo()
    }

    fun removeItem(id: String) {
        updateQuantity(id, 0)
    }

    fun setDeliveryNotes(notes: String) {
        _deliveryNotes.value = notes
    }

    fun applyPromoCode(code: String): Result<String> {
        val trimmed = code.trim().uppercase()
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) {
            return Result.failure(IllegalStateException("Cart is empty"))
        }

        return when (trimmed) {
            "SAVE20" -> {
                _appliedPromoCode.value = "SAVE20"
                _promoDiscount.value = subtotal * 0.20
                Result.success("Applied 20% discount!")
            }
            "WELCOME" -> {
                _appliedPromoCode.value = "WELCOME"
                _promoDiscount.value = minOf(5.0, subtotal)
                Result.success("Applied ₱5.00 off!")
            }
            "FREEFOOD" -> {
                _appliedPromoCode.value = "FREEFOOD"
                _promoDiscount.value = minOf(10.0, subtotal)
                Result.success("Applied ₱10.00 off!")
            }
            else -> {
                Result.failure(IllegalArgumentException("Invalid promo code. Try SAVE20 or WELCOME"))
            }
        }
    }

    fun removePromoCode() {
        _appliedPromoCode.value = null
        _promoDiscount.value = 0.0
    }

    private fun recalculatePromo() {
        val code = _appliedPromoCode.value ?: return
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) {
            _appliedPromoCode.value = null
            _promoDiscount.value = 0.0
            return
        }
        when (code) {
            "SAVE20" -> _promoDiscount.value = subtotal * 0.20
            "WELCOME" -> _promoDiscount.value = minOf(5.0, subtotal)
            "FREEFOOD" -> _promoDiscount.value = minOf(10.0, subtotal)
        }
    }

    fun getSubtotal(): Double {
        return _cartItems.value.sumOf { it.totalPrice }
    }

    fun getDeliveryFee(): Double {
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) return 0.0
        if (subtotal >= freeDeliveryThreshold) return 0.0
        return _currentRestaurant.value?.deliveryFee ?: 1.99
    }

    fun getTotal(): Double {
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) return 0.0
        val delivery = getDeliveryFee()
        val discount = _promoDiscount.value
        return max(0.0, subtotal + delivery + serviceFee - discount)
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _currentRestaurant.value = null
        _appliedPromoCode.value = null
        _promoDiscount.value = 0.0
        _deliveryNotes.value = ""
    }
}
