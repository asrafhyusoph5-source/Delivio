package com.example.data.repository

import com.example.data.model.CartItem
import com.example.data.model.ItemVariant
import com.example.data.model.MenuItem
import com.example.data.model.MerchantItem
import com.example.data.model.Restaurant
import com.example.data.model.SelectedItemChoice
import com.example.util.VariationUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

class CartRepository {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _currentRestaurant = MutableStateFlow<Restaurant?>(null)
    val currentRestaurant: StateFlow<Restaurant?> = _currentRestaurant.asStateFlow()

    private val _appliedPromoCode = MutableStateFlow<String?>(null)
    val appliedPromoCode: StateFlow<String?> = _appliedPromoCode.asStateFlow()

    private val _appliedPromo = MutableStateFlow<com.example.data.model.PromoCode?>(null)
    val appliedPromo: StateFlow<com.example.data.model.PromoCode?> = _appliedPromo.asStateFlow()

    private val _promoDiscount = MutableStateFlow(0.0)
    val promoDiscount: StateFlow<Double> = _promoDiscount.asStateFlow()

    private val _deliveryNotes = MutableStateFlow("")
    val deliveryNotes: StateFlow<String> = _deliveryNotes.asStateFlow()

    private val _priceUpdatedCartItemIds = MutableStateFlow<Set<String>>(emptySet())
    val priceUpdatedCartItemIds: StateFlow<Set<String>> = _priceUpdatedCartItemIds.asStateFlow()

    private val _unavailableVariantMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val unavailableVariantMessages: StateFlow<Map<String, String>> = _unavailableVariantMessages.asStateFlow()

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
        specialInstructions: String = "",
        variantKey: String? = null,
        variantLabel: String? = null,
        variantImageUrl: String? = null,
        variantEffectivePrice: Double? = null,
        variantRegularPrice: Double? = null,
        bypassHoursCheck: Boolean = false
    ): Boolean {
        if (!bypassHoursCheck && !com.example.util.BusinessHoursUtil.effectiveOpen(restaurant)) {
            return false
        }
        val currentItems = _cartItems.value.toMutableList()

        // If cart has items from another restaurant, reset cart to new restaurant
        if (_currentRestaurant.value != null && _currentRestaurant.value?.id != restaurant.id) {
            currentItems.clear()
            _priceUpdatedCartItemIds.value = emptySet()
            _unavailableVariantMessages.value = emptyMap()
        }
        _currentRestaurant.value = restaurant

        val matchedVariant = if (!variantKey.isNullOrBlank()) {
            VariationUtil.findVariantByKey(menuItem.variants, variantKey)
        } else null
        val maxVariantStock = matchedVariant?.stock?.takeIf { it > 0 }

        val existingIndex = currentItems.indexOfFirst {
            it.hasSameConfiguration(menuItem.id, selections, variantKey)
        }
        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            val combinedQty = existing.quantity + quantity
            val cappedQty = if (maxVariantStock != null) combinedQty.coerceAtMost(maxVariantStock) else combinedQty
            currentItems[existingIndex] = existing.copy(
                menuItem = menuItem,
                quantity = cappedQty,
                basePrice = variantEffectivePrice ?: existing.basePrice,
                regularBasePrice = variantRegularPrice ?: existing.regularBasePrice,
                variantLabel = variantLabel ?: existing.variantLabel,
                variantImageUrl = variantImageUrl ?: existing.variantImageUrl
            )
        } else {
            val effectiveBase = variantEffectivePrice ?: com.example.util.PriceDropUtil.effectivePrice(menuItem)
            val regularBase = variantRegularPrice ?: menuItem.price
            val cappedQty = if (maxVariantStock != null) quantity.coerceIn(1, maxVariantStock) else quantity.coerceAtLeast(1)
            currentItems.add(
                CartItem(
                    menuItem = menuItem,
                    restaurantId = restaurant.id,
                    restaurantName = restaurant.displayName,
                    quantity = cappedQty,
                    specialInstructions = specialInstructions,
                    selections = selections,
                    basePrice = effectiveBase,
                    regularBasePrice = regularBase,
                    variantKey = variantKey,
                    variantLabel = variantLabel,
                    variantImageUrl = variantImageUrl
                )
            )
        }
        _cartItems.value = currentItems
        recalculatePromo()
        return true
    }

    fun addVariantItem(
        menuItem: MenuItem,
        restaurant: Restaurant,
        variant: ItemVariant,
        quantity: Int = 1,
        bypassHoursCheck: Boolean = false
    ): Boolean {
        val effectivePrice = VariationUtil.effectiveVariantPrice(variant, menuItem)
        val regularPrice = variant.price
        val variantImg = VariationUtil.resolveVariantImageUrl(menuItem.imageUrl, menuItem.variations, variant)
        return addItem(
            menuItem = menuItem,
            restaurant = restaurant,
            quantity = quantity,
            selections = emptyList(),
            variantKey = variant.key,
            variantLabel = variant.label,
            variantImageUrl = variantImg,
            variantEffectivePrice = effectivePrice,
            variantRegularPrice = regularPrice,
            bypassHoursCheck = bypassHoursCheck
        )
    }

    fun updateCartItemVariant(
        cartItemId: String,
        updatedMenuItem: MenuItem?,
        variant: ItemVariant,
        newQuantity: Int
    ) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == cartItemId }
        if (index < 0) return

        val current = currentItems[index]
        val resolvedItem = updatedMenuItem ?: current.menuItem
        val effPrice = VariationUtil.effectiveVariantPrice(variant, resolvedItem)
        val regPrice = variant.price
        val imgUrl = VariationUtil.resolveVariantImageUrl(resolvedItem.imageUrl, resolvedItem.variations, variant)
        val cappedQty = newQuantity.coerceIn(1, variant.stock.coerceAtLeast(1))

        val duplicateIdx = currentItems.indexOfFirst {
            it.id != cartItemId && it.hasSameConfiguration(resolvedItem.id, emptyList(), variant.key)
        }
        if (duplicateIdx >= 0) {
            val dup = currentItems[duplicateIdx]
            val mergedQty = (dup.quantity + cappedQty).coerceAtMost(variant.stock.coerceAtLeast(1))
            currentItems[duplicateIdx] = dup.copy(
                menuItem = resolvedItem,
                quantity = mergedQty,
                basePrice = effPrice,
                regularBasePrice = regPrice,
                variantLabel = variant.label,
                variantImageUrl = imgUrl
            )
            currentItems.removeAt(index)
        } else {
            currentItems[index] = current.copy(
                menuItem = resolvedItem,
                quantity = cappedQty,
                basePrice = effPrice,
                regularBasePrice = regPrice,
                variantKey = variant.key,
                variantLabel = variant.label,
                variantImageUrl = imgUrl
            )
        }
        _priceUpdatedCartItemIds.value = _priceUpdatedCartItemIds.value - cartItemId
        _unavailableVariantMessages.value = _unavailableVariantMessages.value - cartItemId
        _cartItems.value = currentItems
        recalculatePromo()
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
                val removedId = currentItems[index].id
                currentItems.removeAt(index)
                _priceUpdatedCartItemIds.value = _priceUpdatedCartItemIds.value - removedId
                _unavailableVariantMessages.value = _unavailableVariantMessages.value - removedId
            } else {
                val target = currentItems[index]
                val maxStock = if (!target.variantKey.isNullOrBlank()) {
                    VariationUtil.findVariantByKey(target.menuItem.variants, target.variantKey)?.stock
                } else null
                val finalQty = if (maxStock != null && maxStock > 0) newQuantity.coerceAtMost(maxStock) else newQuantity
                currentItems[index] = target.copy(quantity = finalQty)
            }
        }
        if (currentItems.isEmpty()) {
            _currentRestaurant.value = null
            _appliedPromoCode.value = null
            _promoDiscount.value = 0.0
            _priceUpdatedCartItemIds.value = emptySet()
            _unavailableVariantMessages.value = emptyMap()
        }
        _cartItems.value = currentItems
        recalculatePromo()
    }

    fun removeItem(id: String) {
        updateQuantity(id, 0)
    }

    fun markPriceUpdated(cartItemId: String) {
        _priceUpdatedCartItemIds.value = _priceUpdatedCartItemIds.value + cartItemId
    }

    /**
     * Re-evaluates all cart items against live merchant items from Firestore:
     * - Detects missing items, missing variants, or variants with stock <= 0 ("<item> (<variant>) is no longer available")
     * - Detects price/salePrice updates and updates cart pricing + flags "Price updated"
     */
    fun syncWithLiveMerchantItems(
        merchantItems: List<MerchantItem>,
        enforceMissingItemAsUnavailable: Boolean = true
    ): Map<String, String> {
        val currentItems = _cartItems.value
        if (currentItems.isEmpty()) {
            _unavailableVariantMessages.value = emptyMap()
            return emptyMap()
        }

        val liveMap = merchantItems.associateBy { it.id }
        val newUnavailable = mutableMapOf<String, String>()
        val newPriceUpdated = _priceUpdatedCartItemIds.value.toMutableSet()
        var listModified = false
        val updatedItems = currentItems.map { cartItem ->
            val variantKey = cartItem.variantKey
            if (variantKey.isNullOrBlank()) {
                // Non-variant item: refresh prices if live item available
                val live = liveMap[cartItem.menuItem.id]
                if (live != null) {
                    val newEff = com.example.util.PriceDropUtil.effectivePrice(live)
                    val newReg = live.price
                    if (abs(newEff - cartItem.basePrice) > 0.001 || abs(newReg - cartItem.regularBasePrice) > 0.001) {
                        newPriceUpdated.add(cartItem.id)
                        listModified = true
                        cartItem.copy(
                            basePrice = newEff,
                            regularBasePrice = newReg,
                            menuItem = cartItem.menuItem.copy(
                                price = live.price,
                                salePrice = live.salePrice,
                                saleStartsAt = live.saleStartsAt,
                                saleEndsAt = live.saleEndsAt,
                                saleActive = live.saleActive,
                                sets = live.sets,
                                hasSets = live.hasSets
                            )
                        )
                    } else cartItem
                } else cartItem
            } else {
                val variantDisplayLabel = cartItem.variantLabel?.ifBlank { null } ?: variantKey
                val unavailMsg = "${cartItem.menuItem.name} ($variantDisplayLabel) is no longer available"
                val live = liveMap[cartItem.menuItem.id]

                if (live == null) {
                    if (merchantItems.isNotEmpty() && enforceMissingItemAsUnavailable) {
                        newUnavailable[cartItem.id] = unavailMsg
                    } else {
                        // Offline or pre-loaded: validate against embedded variants if any
                        val localVar = VariationUtil.findVariantByKey(cartItem.menuItem.variants, variantKey)
                        if (cartItem.menuItem.variants.isNotEmpty() && (localVar == null || localVar.stock <= 0)) {
                            newUnavailable[cartItem.id] = unavailMsg
                        }
                    }
                    cartItem
                } else if (!live.isAvailable || !live.variationsEnabled) {
                    newUnavailable[cartItem.id] = unavailMsg
                    cartItem
                } else {
                    val liveVariant = VariationUtil.findVariantByKey(live.variants, variantKey)
                    val updatedMenuItem = cartItem.menuItem.copy(
                        name = live.name,
                        description = live.description,
                        price = live.price,
                        imageUrl = live.imageUrl,
                        itemSize = live.itemSize,
                        availability = live.availability,
                        salePrice = live.salePrice,
                        saleStartsAt = live.saleStartsAt,
                        saleEndsAt = live.saleEndsAt,
                        saleActive = live.saleActive,
                        variationsEnabled = live.variationsEnabled,
                        variations = live.variations,
                        variants = live.variants,
                        priceMax = live.priceMax,
                        stockCount = live.stockCount
                    )
                    if (liveVariant == null || liveVariant.stock <= 0) {
                        newUnavailable[cartItem.id] = unavailMsg
                        listModified = true
                        cartItem.copy(menuItem = updatedMenuItem)
                    } else {
                        val newEff = VariationUtil.effectiveVariantPrice(liveVariant, live)
                        val newReg = liveVariant.price
                        val newImg = VariationUtil.resolveVariantImageUrl(live.imageUrl, live.variations, liveVariant)
                        val newQty = cartItem.quantity.coerceAtMost(liveVariant.stock).coerceAtLeast(1)
                        val priceChanged = abs(newEff - cartItem.basePrice) > 0.001 || abs(newReg - cartItem.regularBasePrice) > 0.001
                        if (priceChanged) {
                            newPriceUpdated.add(cartItem.id)
                        }
                        if (priceChanged || newQty != cartItem.quantity || liveVariant.label != cartItem.variantLabel || updatedMenuItem != cartItem.menuItem) {
                            listModified = true
                            cartItem.copy(
                                menuItem = updatedMenuItem,
                                quantity = newQty,
                                basePrice = newEff,
                                regularBasePrice = newReg,
                                variantLabel = liveVariant.label.ifBlank { cartItem.variantLabel },
                                variantImageUrl = newImg.ifBlank { cartItem.variantImageUrl }
                            )
                        } else {
                            cartItem
                        }
                    }
                }
            }
        }

        _unavailableVariantMessages.value = newUnavailable
        _priceUpdatedCartItemIds.value = newPriceUpdated
        if (listModified) {
            _cartItems.value = updatedItems
            recalculatePromo()
        }
        return newUnavailable
    }

    fun setDeliveryNotes(notes: String) {
        _deliveryNotes.value = notes
    }

    fun setAppliedPromo(promo: com.example.data.model.PromoCode, discountAmount: Double) {
        _appliedPromo.value = promo
        _appliedPromoCode.value = promo.code
        _promoDiscount.value = com.example.util.FeeCalculator.round2(discountAmount)
    }

    fun applyPromoCode(code: String): Result<String> {
        val trimmed = code.trim().uppercase()
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) {
            return Result.failure(IllegalStateException("Cart is empty"))
        }

        return when (trimmed) {
            "SAVE20" -> {
                val promo = com.example.data.model.PromoCode(code = "SAVE20", type = "percent", value = 20.0)
                setAppliedPromo(promo, subtotal * 0.20)
                Result.success("Applied 20% discount!")
            }
            "WELCOME" -> {
                val promo = com.example.data.model.PromoCode(code = "WELCOME", type = "fixed", value = 5.0)
                setAppliedPromo(promo, minOf(5.0, subtotal))
                Result.success("Applied ₱5.00 off!")
            }
            "FREEFOOD" -> {
                val promo = com.example.data.model.PromoCode(code = "FREEFOOD", type = "fixed", value = 10.0)
                setAppliedPromo(promo, minOf(10.0, subtotal))
                Result.success("Applied ₱10.00 off!")
            }
            else -> {
                Result.failure(IllegalArgumentException("Invalid promo code. Try SAVE20 or WELCOME"))
            }
        }
    }

    fun removePromoCode() {
        _appliedPromoCode.value = null
        _appliedPromo.value = null
        _promoDiscount.value = 0.0
    }

    private fun recalculatePromo() {
        val promo = _appliedPromo.value
        val code = _appliedPromoCode.value ?: return
        val subtotal = getSubtotal()
        if (subtotal <= 0.0) {
            _appliedPromoCode.value = null
            _appliedPromo.value = null
            _promoDiscount.value = 0.0
            return
        }
        if (promo != null) {
            val currentRestId = _currentRestaurant.value?.id
            if (!promo.merchantId.isNullOrBlank() && (currentRestId == null || promo.merchantId != currentRestId)) {
                _appliedPromoCode.value = null
                _appliedPromo.value = null
                _promoDiscount.value = 0.0
                return
            }
            if (subtotal < promo.minSubtotal) {
                _appliedPromoCode.value = null
                _appliedPromo.value = null
                _promoDiscount.value = 0.0
                return
            }
            val eligibleSubtotal = if (promo.appliesTo.equals("items", ignoreCase = true)) {
                val matchingItems = _cartItems.value.filter { it.menuItem.id in promo.itemIds }
                if (matchingItems.isEmpty()) {
                    _appliedPromoCode.value = null
                    _appliedPromo.value = null
                    _promoDiscount.value = 0.0
                    return
                }
                matchingItems.sumOf { it.totalPrice }
            } else {
                subtotal
            }
            val calculated = if (promo.type.equals("percent", ignoreCase = true)) {
                val pct = eligibleSubtotal * (promo.value / 100.0)
                if (promo.maxDiscount != null && promo.maxDiscount > 0.0) minOf(pct, promo.maxDiscount) else pct
            } else {
                minOf(promo.value, eligibleSubtotal)
            }
            _promoDiscount.value = com.example.util.FeeCalculator.round2(calculated)
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
        val rest = _currentRestaurant.value
        if (rest == null) {
            com.example.util.CityDeliveryRateResolver.logWarn("CartRepository.getDeliveryFee: currentRestaurant is null; using non-zero base fallback 39.0")
            return 39.0
        }
        val fee = rest.deliveryFee
        if (fee <= 0.0) {
            com.example.util.CityDeliveryRateResolver.logWarn("CartRepository.getDeliveryFee: shop '${rest.id}' has fee=$fee; using non-zero fallback 39.0")
            return 39.0
        }
        return fee
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
        _priceUpdatedCartItemIds.value = emptySet()
        _unavailableVariantMessages.value = emptyMap()
    }
}

