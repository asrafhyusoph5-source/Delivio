package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Restaurant
import com.example.data.model.SelectedItemChoice
import com.example.data.repository.CartRepository
import com.example.data.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomizeSheetState(
    val item: MenuItem,
    val restaurant: Restaurant,
    val initialSelections: List<SelectedItemChoice> = emptyList(),
    val initialQuantity: Int = 1,
    val editingCartItemId: String? = null
)

data class VariationSelectorState(
    val item: MenuItem,
    val restaurant: Restaurant,
    val initialSelections: Map<String, String> = emptyMap(),
    val initialQuantity: Int = 1,
    val editingCartItemId: String? = null,
    val asBottomSheet: Boolean = true
)

data class ReorderResult(
    val addedCount: Int,
    val unavailableMessages: List<String>,
    val priceUpdatedCount: Int
)

class CartViewModel(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    val cartItems: StateFlow<List<CartItem>> = cartRepository.cartItems
    val currentRestaurant: StateFlow<Restaurant?> = cartRepository.currentRestaurant
    val appliedPromoCode: StateFlow<String?> = cartRepository.appliedPromoCode
    val promoDiscount: StateFlow<Double> = cartRepository.promoDiscount
    val deliveryNotes: StateFlow<String> = cartRepository.deliveryNotes
    val priceUpdatedCartItemIds: StateFlow<Set<String>> = cartRepository.priceUpdatedCartItemIds
    val unavailableVariantMessages: StateFlow<Map<String, String>> = cartRepository.unavailableVariantMessages
    val firestoreService: com.example.data.repository.FirestoreService get() = orderRepository.firestoreService

    fun getMerchantMenusFlow(merchantId: String): kotlinx.coroutines.flow.Flow<List<com.example.data.model.MerchantMenu>> {
        return firestoreService.getMerchantMenusFlow(merchantId)
    }

    private val _customizeState = MutableStateFlow<CustomizeSheetState?>(null)
    val customizeState: StateFlow<CustomizeSheetState?> = _customizeState.asStateFlow()

    private val _variationSelectorState = MutableStateFlow<VariationSelectorState?>(null)
    val variationSelectorState: StateFlow<VariationSelectorState?> = _variationSelectorState.asStateFlow()

    fun openCustomize(
        item: MenuItem,
        restaurant: Restaurant,
        initialSelections: List<SelectedItemChoice> = emptyList(),
        initialQuantity: Int = 1,
        editingCartItemId: String? = null
    ) {
        if (com.example.util.VariationUtil.isVariationItem(item)) {
            openVariationSelector(
                item = item,
                restaurant = restaurant,
                initialQuantity = initialQuantity,
                editingCartItemId = editingCartItemId,
                asBottomSheet = false
            )
            return
        }
        _customizeState.value = CustomizeSheetState(
            item = item,
            restaurant = restaurant,
            initialSelections = initialSelections,
            initialQuantity = initialQuantity,
            editingCartItemId = editingCartItemId
        )
    }

    fun closeCustomize() {
        _customizeState.value = null
    }

    fun openVariationSelector(
        item: MenuItem,
        restaurant: Restaurant,
        initialSelections: Map<String, String> = emptyMap(),
        initialQuantity: Int = 1,
        editingCartItemId: String? = null,
        asBottomSheet: Boolean = true
    ) {
        _variationSelectorState.value = VariationSelectorState(
            item = item,
            restaurant = restaurant,
            initialSelections = initialSelections,
            initialQuantity = initialQuantity,
            editingCartItemId = editingCartItemId,
            asBottomSheet = asBottomSheet
        )
    }

    fun openVariationSelectorForCartItem(
        cartItem: CartItem,
        restaurant: Restaurant,
        liveMerchantItems: List<com.example.data.model.MerchantItem> = emptyList(),
        asBottomSheet: Boolean = true
    ) {
        val live = liveMerchantItems.firstOrNull { it.id == cartItem.menuItem.id }
        val resolvedItem = if (live != null) {
            cartItem.menuItem.copy(
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
        } else {
            cartItem.menuItem
        }
        val initialSelections = com.example.util.VariationUtil.initialSelectionsForCartItem(resolvedItem, cartItem)
        openVariationSelector(
            item = resolvedItem,
            restaurant = restaurant,
            initialSelections = initialSelections,
            initialQuantity = cartItem.quantity,
            editingCartItemId = cartItem.id,
            asBottomSheet = asBottomSheet
        )
    }

    fun closeVariationSelector() {
        _variationSelectorState.value = null
    }

    fun isChoiceAvailable(item: CartItem, choice: SelectedItemChoice): Boolean {
        return cartRepository.isChoiceAvailable(item, choice)
    }

    fun markChoiceAvailability(itemId: String, setId: String, choiceId: String, available: Boolean) {
        cartRepository.markChoiceAvailability(itemId, setId, choiceId, available)
    }

    fun getFirstUnavailableChoice(item: CartItem): SelectedItemChoice? {
        return item.selections.firstOrNull { !isChoiceAvailable(item, it) }
    }

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

    private val _dispatchSettings = MutableStateFlow(com.example.data.model.DispatchSettings())
    val dispatchSettings: StateFlow<com.example.data.model.DispatchSettings> = _dispatchSettings.asStateFlow()

    private val _checkoutSettings = MutableStateFlow(com.example.data.model.CheckoutSettings())
    val checkoutSettings: StateFlow<com.example.data.model.CheckoutSettings> = _checkoutSettings.asStateFlow()

    private val _appDesignerSettings = MutableStateFlow(com.example.data.model.AppDesignerSettings())
    val appDesignerSettings: StateFlow<com.example.data.model.AppDesignerSettings> = _appDesignerSettings.asStateFlow()

    private val _selectedDeliveryOption = MutableStateFlow("priority")
    val selectedDeliveryOption: StateFlow<String> = _selectedDeliveryOption.asStateFlow()

    private val _customerCityId = MutableStateFlow<String?>(null)
    private val _customerLat = MutableStateFlow(0.0)
    private val _customerLng = MutableStateFlow(0.0)
    private var deliveryRatesJob: kotlinx.coroutines.Job? = null
    private var lastRatesContextKey: String? = null

    private val _feesResult = MutableStateFlow(
        com.example.util.FeeCalculator.calculateFees(
            subtotal = 0.0,
            deliveryFee = 0.0
        )
    )
    val feesResult: StateFlow<com.example.util.FeeCalculator.FeeCalculationResult> = _feesResult.asStateFlow()

    init {
        observeDeliveryRatesForContext()
        viewModelScope.launch {
            orderRepository.firestoreService.getPlatformSettingsFlow().collect { settings ->
                _platformSettings.value = settings
                recalculateFees()
            }
        }
        viewModelScope.launch {
            orderRepository.firestoreService.getDispatchSettingsFlow().collect { settings ->
                _dispatchSettings.value = settings
                if (isMartBatchingRestricted()) {
                    _selectedDeliveryOption.value = "priority"
                } else {
                    val def = settings.defaultOption.ifBlank { "priority" }
                    val cur = _selectedDeliveryOption.value
                    val curEnabled = settings.options[cur]?.enabled == true
                    if (!curEnabled) {
                        _selectedDeliveryOption.value = if (settings.options[def]?.enabled == true) def else {
                            settings.options.entries.firstOrNull { it.value.enabled }?.key ?: "priority"
                        }
                    }
                }
                recalculateFees()
            }
        }
        viewModelScope.launch {
            orderRepository.firestoreService.getCheckoutSettingsFlow().collect { settings ->
                _checkoutSettings.value = settings
            }
        }
        viewModelScope.launch {
            orderRepository.firestoreService.getAppDesignerFlow().collect { settings ->
                _appDesignerSettings.value = settings
            }
        }
        viewModelScope.launch {
            cartRepository.cartItems.collect { recalculateFees() }
        }
        viewModelScope.launch {
            cartRepository.promoDiscount.collect { recalculateFees() }
        }
        viewModelScope.launch {
            cartRepository.currentRestaurant.collect {
                if (isMartBatchingRestricted()) {
                    _selectedDeliveryOption.value = "priority"
                }
                observeDeliveryRatesForContext()
                recalculateFees()
            }
        }
    }

    fun updateCustomerDeliveryContext(cityId: String?, customerLat: Double, customerLng: Double) {
        val cleanCity = cityId?.trim()?.takeIf { it.isNotEmpty() }
        val changed = cleanCity != _customerCityId.value ||
            kotlin.math.abs(customerLat - _customerLat.value) > 0.00001 ||
            kotlin.math.abs(customerLng - _customerLng.value) > 0.00001
        if (!changed) return
        _customerCityId.value = cleanCity
        _customerLat.value = customerLat
        _customerLng.value = customerLng
        observeDeliveryRatesForContext()
        recalculateFees()
    }

    private fun observeDeliveryRatesForContext() {
        val shopCityId = currentRestaurant.value?.cityId?.trim().orEmpty()
        val custCityId = _customerCityId.value?.trim()?.takeIf { it.isNotEmpty() }
        val contextKey = "$shopCityId|${custCityId.orEmpty()}"
        if (contextKey == lastRatesContextKey && deliveryRatesJob?.isActive == true) return
        lastRatesContextKey = contextKey
        deliveryRatesJob?.cancel()
        deliveryRatesJob = viewModelScope.launch {
            orderRepository.firestoreService
                .getDeliveryRatesForShopFlow(shopCityId = shopCityId, customerCityId = custCityId)
                .collect { rates ->
                    _deliveryRates.value = rates
                    recalculateFees()
                }
        }
    }

    private fun resolveDistanceKmForCurrentShop(): Double {
        val rest = currentRestaurant.value ?: return 0.0
        val hasShopCoords = rest.lat != 0.0 || rest.lng != 0.0
        val cLat = _customerLat.value
        val cLng = _customerLng.value
        val hasCustCoords = cLat != 0.0 || cLng != 0.0
        if (!hasShopCoords || !hasCustCoords) {
            com.example.util.CityDeliveryRateResolver.logWarn(
                "Missing coordinates for distance calculation (shop=${rest.id}, shopCoords=($hasShopCoords), custCoords=($hasCustCoords)); using 0.0 km base fare"
            )
            return 0.0
        }
        return com.example.util.BranchGroupingUtil.calculateDistanceKm(cLat, cLng, rest.lat, rest.lng)
    }

    fun isMartBatchingRestricted(): Boolean {
        val rest = currentRestaurant.value ?: return false
        val isConv = rest.isConvenience || rest.type.equals("convenience", ignoreCase = true)
        return isConv && !_dispatchSettings.value.martBatching
    }

    fun getEffectiveDeliveryOption(): String {
        return if (isMartBatchingRestricted()) "priority" else _selectedDeliveryOption.value
    }

    fun selectDeliveryOption(option: String) {
        _selectedDeliveryOption.value = if (isMartBatchingRestricted()) {
            "priority"
        } else {
            option.lowercase().trim()
        }
        recalculateFees()
    }

    fun getOptionDeliveryFee(optionKey: String): Double {
        if (_fulfillment.value == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP) return 0.0
        val rest = currentRestaurant.value
        val rates = _deliveryRates.value
        val defaults = orderRepository.firestoreService.defaultDeliveryRates
        val isConv = rest?.let { it.isConvenience || it.type.equals("convenience", ignoreCase = true) } == true
        val rate = com.example.util.CityDeliveryRateResolver.ensureNonZeroDeliveryRate(
            rateConfig = if (isConv) rates.convenience else rates.food,
            fallbackRate = if (isConv) defaults.convenience else defaults.food,
            contextLabel = "${rest?.displayName ?: "cart"} (${rest?.cityId ?: _customerCityId.value ?: "unknown"})"
        )
        val distanceKm = resolveDistanceKmForCurrentShop()
        val extraKm = kotlin.math.max(0.0, distanceKm - rate.includedKm)
        val maxBound = rate.maxFee.coerceAtLeast(rate.minFee)
        val baseDeliveryFee = com.example.util.FeeCalculator.round2(
            (rate.baseFare + extraKm * rate.perKmRate).coerceIn(rate.minFee, maxBound) + rate.surcharge
        )

        val optKey = optionKey.lowercase().trim()
        val config = _dispatchSettings.value.options[optKey]
            ?: when (optKey) {
                "priority" -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 20.0, holdMinutes = 0, maxBatch = 1)
                "saver" -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = -10.0, holdMinutes = 12, maxBatch = 3)
                else -> com.example.data.model.DispatchOptionConfig(enabled = true, feeAdjustType = "flat", feeAdjustValue = 0.0, holdMinutes = 5, maxBatch = 2)
            }
        val adjustment = if (config.feeAdjustType.equals("percent", ignoreCase = true)) {
            baseDeliveryFee * (config.feeAdjustValue / 100.0)
        } else {
            config.feeAdjustValue
        }
        val adjustedBase = (baseDeliveryFee + adjustment)
        val nonZeroAdjusted = if (adjustedBase > 0.0) adjustedBase else {
            com.example.util.CityDeliveryRateResolver.logWarn(
                "Dispatch option '$optKey' adjustment ($adjustment) reduced fee <= 0 (base=$baseDeliveryFee); clamping to baseDeliveryFee"
            )
            baseDeliveryFee
        }
        val customerExtra = if (rest?.specialRadius?.enabled == true) {
            rest.specialRadius.customerExtraFee
        } else {
            0.0
        }
        return com.example.util.FeeCalculator.round2((nonZeroAdjusted + customerExtra).coerceAtLeast(1.0))
    }

    fun getOptionEtaText(optionKey: String): String {
        val rest = currentRestaurant.value
        val distanceKm = resolveDistanceKmForCurrentShop()
        val baseEta = com.example.util.EtaCalculator.estimateMinutes(
            prepTimeMinutes = rest?.prepTimeMinutes ?: 15,
            distanceKm = distanceKm
        )
        val optKey = optionKey.lowercase().trim()
        val config = _dispatchSettings.value.options[optKey]
        val holdMinutes = config?.holdMinutes ?: when (optKey) {
            "priority" -> 0
            "saver" -> 12
            else -> 5
        }
        val totalEta = when (optKey) {
            "priority" -> baseEta
            "saver" -> baseEta + holdMinutes
            else -> baseEta + kotlin.math.round(holdMinutes / 2.0).toInt()
        }
        return com.example.util.EtaCalculator.formatRange(totalEta)
    }

    fun getOptionHoldMinutes(optionKey: String): Int {
        val optKey = optionKey.lowercase().trim()
        return _dispatchSettings.value.options[optKey]?.holdMinutes ?: when (optKey) {
            "priority" -> 0
            "saver" -> 12
            else -> 5
        }
    }

    private fun recalculateFees() {
        val subtotal = cartRepository.getSubtotal()
        val isPickup = _fulfillment.value == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP
        val rawDeliveryFee = if (isPickup) 0.0 else getOptionDeliveryFee(getEffectiveDeliveryOption())
        val rawTip = if (isPickup) 0.0 else _tipAmount.value
        val discount = promoDiscount.value
        val isConv = currentRestaurant.value?.let { it.isConvenience || it.type.equals("convenience", ignoreCase = true) } == true
        val rates = _deliveryRates.value
        val defaults = orderRepository.firestoreService.defaultDeliveryRates
        val rateConfig = com.example.util.CityDeliveryRateResolver.ensureNonZeroDeliveryRate(
            rateConfig = if (isConv) rates.convenience else rates.food,
            fallbackRate = if (isConv) defaults.convenience else defaults.food,
            contextLabel = currentRestaurant.value?.displayName ?: "cart"
        )
        val vatConfig = _platformSettings.value.vat

        val res = com.example.util.FeeCalculator.calculateForRateConfig(
            subtotal = subtotal,
            deliveryFee = rawDeliveryFee,
            rateConfig = rateConfig,
            tip = rawTip,
            discount = discount,
            vatConfig = vatConfig,
            isPickup = isPickup,
            distanceKm = resolveDistanceKmForCurrentShop()
        )
        _feesResult.value = res
    }

    fun setFulfillment(mode: String) {
        _fulfillment.value = mode
        if (mode == com.example.util.DelivioStatusConstants.FULFILLMENT_PICKUP) {
            _tipAmount.value = 0.0
        }
        val promo = cartRepository.appliedPromo.value
        if (promo != null && !promo.fulfillment.isNullOrBlank()) {
            val req = promo.fulfillment.lowercase().trim()
            val cur = mode.lowercase().trim()
            if (!cur.contains(req)) {
                removePromo()
                val reqLabel = if (req.contains("pickup")) "pick-up" else "delivery"
                _promoMessage.value = "Voucher removed: valid for $reqLabel orders only"
            }
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

    suspend fun validateAndApplyPromo(
        promo: com.example.data.model.PromoCode,
        uid: String? = null,
        serviceType: String = "food"
    ): Boolean {
        val subtotal = cartRepository.getSubtotal()
        if (subtotal <= 0.0) {
            _promoMessage.value = "Cart is empty"
            return false
        }

        // 1. Check shop / merchantId
        val rest = currentRestaurant.value
        if (!promo.merchantId.isNullOrBlank() && (rest == null || promo.merchantId != rest.id)) {
            _promoMessage.value = "This voucher is for another shop"
            return false
        }

        // 2. Check active
        if (!promo.active) {
            _promoMessage.value = "This voucher is no longer active"
            return false
        }

        // 3. Check startsAt and expiresAt
        val now = System.currentTimeMillis()
        if (promo.startsAt != null && promo.startsAt > now) {
            _promoMessage.value = "This voucher has not started yet"
            return false
        }
        if (promo.expiresAt != null && promo.expiresAt < now) {
            _promoMessage.value = "This voucher has expired"
            return false
        }

        // 4. Check usedCount < usageLimit
        if (promo.usageLimit != null && promo.usedCount >= promo.usageLimit) {
            _promoMessage.value = "This voucher has reached its maximum usage limit"
            return false
        }

        // 5. Check perUserLimit (query promoRedemptions where customerId == me and code == CODE)
        val effectiveUid = uid ?: try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (promo.perUserLimit != null && !effectiveUid.isNullOrEmpty()) {
            val userUsed = firestoreService.getUserPromoUsageCount(promo.code, effectiveUid)
            if (userUsed >= promo.perUserLimit) {
                _promoMessage.value = "You have reached the maximum usage limit for this voucher"
                return false
            }
        }

        // 6. Check fulfillment (delivery or pickup)
        if (!promo.fulfillment.isNullOrBlank()) {
            val curFulfillment = _fulfillment.value.lowercase().trim()
            val reqFulfillment = promo.fulfillment.lowercase().trim()
            if (!curFulfillment.contains(reqFulfillment)) {
                val reqLabel = if (reqFulfillment.contains("pickup")) "pick-up" else "delivery"
                _promoMessage.value = "This voucher is valid for $reqLabel orders only"
                return false
            }
        }

        // 7. Check services
        val currentService = if (rest?.isConvenience == true) "convenience" else serviceType
        if (promo.services.isNotEmpty() && !promo.services.any { it.equals(currentService, ignoreCase = true) }) {
            _promoMessage.value = "This voucher is not valid for $currentService orders"
            return false
        }

        // 8. If appliesTo is "items", the discount applies only to the subtotal of the listed itemIds
        // (the voucher needs at least one of them in the cart)
        val eligibleSubtotal = if (promo.appliesTo.equals("items", ignoreCase = true)) {
            val matchingItems = cartItems.value.filter { it.menuItem.id in promo.itemIds }
            if (matchingItems.isEmpty()) {
                _promoMessage.value = "This voucher applies only to specific items not in your cart"
                return false
            }
            matchingItems.sumOf { it.totalPrice }
        } else {
            subtotal
        }

        // 9. Check minSubtotal: "Add P120 more to use this voucher"
        if (subtotal < promo.minSubtotal) {
            val diff = promo.minSubtotal - subtotal
            val diffStr = if (diff % 1.0 == 0.0) "${diff.toInt()}" else String.format(java.util.Locale.US, "%.2f", diff)
            _promoMessage.value = "Add ₱$diffStr more to use this voucher"
            return false
        }

        // 10. Percent discounts respect maxDiscount
        val calculatedDiscount = if (promo.type.equals("percent", ignoreCase = true)) {
            val pctDiscount = eligibleSubtotal * (promo.value / 100.0)
            if (promo.maxDiscount != null && promo.maxDiscount > 0.0) {
                minOf(pctDiscount, promo.maxDiscount)
            } else {
                pctDiscount
            }
        } else {
            minOf(promo.value, eligibleSubtotal)
        }

        cartRepository.setAppliedPromo(promo, calculatedDiscount)
        _promoInput.value = promo.code
        _promoMessage.value = if (promo.type.equals("percent", ignoreCase = true)) {
            "Applied ${promo.value.toInt()}% off!"
        } else {
            "Applied ₱${String.format(java.util.Locale.US, "%.2f", promo.value)} off!"
        }
        recalculateFees()
        return true
    }

    fun applyShopVoucher(
        voucher: com.example.data.model.PromoCode,
        uid: String? = null,
        serviceType: String = "food"
    ) {
        viewModelScope.launch {
            validateAndApplyPromo(voucher, uid, serviceType)
        }
    }

    fun applyPromo(uid: String? = null, serviceType: String = "food") {
        val rawInput = _promoInput.value.trim()
        if (rawInput.isEmpty()) {
            _promoMessage.value = "Please enter a promo code"
            return
        }

        val subtotal = cartRepository.getSubtotal()
        if (subtotal <= 0.0) {
            _promoMessage.value = "Cart is empty"
            return
        }

        viewModelScope.launch {
            val codeUpper = rawInput.uppercase()
            // 1. Read directly by its uppercase id (promoCodes/{CODE}, never list all codes)
            val promo = firestoreService.getPromoCode(codeUpper)
            if (promo != null) {
                validateAndApplyPromo(promo, uid, serviceType)
            } else {
                // Fallback to local built-in codes (SAVE20, WELCOME, FREEFOOD) for tests/offline
                val fallbackRes = cartRepository.applyPromoCode(rawInput)
                fallbackRes.onSuccess { msg ->
                    _promoMessage.value = msg
                    recalculateFees()
                }.onFailure { err ->
                    _promoMessage.value = err.message ?: "Invalid promo code"
                }
            }
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

    fun addItem(
        item: MenuItem,
        restaurant: Restaurant,
        quantity: Int = 1,
        selections: List<SelectedItemChoice> = emptyList(),
        specialInstructions: String = ""
    ) {
        cartRepository.addItem(item, restaurant, quantity, selections, specialInstructions)
        recalculateFees()
    }

    fun addVariantItem(
        item: MenuItem,
        restaurant: Restaurant,
        variant: com.example.data.model.ItemVariant,
        quantity: Int = 1
    ) {
        cartRepository.addVariantItem(item, restaurant, variant, quantity)
        recalculateFees()
    }

    fun updateCartItemVariant(
        cartItemId: String,
        item: MenuItem,
        variant: com.example.data.model.ItemVariant,
        quantity: Int
    ) {
        cartRepository.updateCartItemVariant(cartItemId, item, variant, quantity)
        recalculateFees()
    }

    fun updateCartItemSelections(
        cartItemId: String,
        newSelections: List<SelectedItemChoice>,
        newQuantity: Int
    ) {
        cartRepository.updateCartItemSelections(cartItemId, newSelections, newQuantity)
        recalculateFees()
    }

    fun syncCartWithLiveMerchantItems(
        merchantItems: List<com.example.data.model.MerchantItem>,
        enforceMissingItemAsUnavailable: Boolean = true
    ): Map<String, String> {
        val result = cartRepository.syncWithLiveMerchantItems(merchantItems, enforceMissingItemAsUnavailable)
        recalculateFees()
        return result
    }

    suspend fun reorderOrder(order: Order): ReorderResult {
        val rest = order.restaurant
        val liveItems = try {
            firestoreService.getMerchantAvailableItems(rest.id)
        } catch (_: Exception) {
            emptyList()
        }
        val liveById = liveItems.associateBy { it.id }
        val liveByName = liveItems.associateBy { it.name.trim().lowercase() }

        cartRepository.clearCart()
        var addedCount = 0
        var priceUpdatedCount = 0
        val unavailableMessages = mutableListOf<String>()

        for (line in order.items) {
            val live = liveById[line.menuItem.id] ?: liveByName[line.menuItem.name.trim().lowercase()]
            val vKey = line.variantKey

            if (!vKey.isNullOrBlank()) {
                val variantDisplay = line.variantLabel?.ifBlank { null } ?: vKey
                val unavailMsg = "${line.menuItem.name} ($variantDisplay) is no longer available"
                if (liveItems.isNotEmpty() && (live == null || !live.isAvailable || !live.variationsEnabled)) {
                    unavailableMessages.add(unavailMsg)
                    continue
                }
                val resolvedItem = if (live != null) {
                    line.menuItem.copy(
                        id = live.id,
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
                } else {
                    line.menuItem
                }
                val liveVariant = com.example.util.VariationUtil.findVariantByKey(resolvedItem.variants, vKey)
                if (liveItems.isNotEmpty() && (liveVariant == null || liveVariant.stock <= 0)) {
                    unavailableMessages.add(unavailMsg)
                    continue
                }
                if (liveVariant != null && liveVariant.stock <= 0) {
                    unavailableMessages.add(unavailMsg)
                    continue
                }

                if (liveVariant != null) {
                    val currentEff = com.example.util.VariationUtil.effectiveVariantPrice(liveVariant, resolvedItem)
                    val qty = line.quantity.coerceIn(1, liveVariant.stock.coerceAtLeast(1))
                    cartRepository.addVariantItem(
                        menuItem = resolvedItem,
                        restaurant = rest,
                        variant = liveVariant,
                        quantity = qty,
                        bypassHoursCheck = true
                    )
                    if (kotlin.math.abs(currentEff - line.basePrice) > 0.001) {
                        priceUpdatedCount++
                        cartRepository.cartItems.value.lastOrNull {
                            it.hasSameConfiguration(resolvedItem.id, emptyList(), liveVariant.key)
                        }?.let { cartRepository.markPriceUpdated(it.id) }
                    }
                    addedCount++
                } else {
                    // Offline fallback without embedded variants
                    cartRepository.addItem(
                        menuItem = resolvedItem,
                        restaurant = rest,
                        quantity = line.quantity.coerceAtLeast(1),
                        selections = line.selections,
                        specialInstructions = line.specialInstructions,
                        variantKey = line.variantKey,
                        variantLabel = line.variantLabel,
                        variantImageUrl = line.variantImageUrl,
                        variantEffectivePrice = line.basePrice,
                        variantRegularPrice = line.regularBasePrice,
                        bypassHoursCheck = true
                    )
                    addedCount++
                }
            } else {
                if (liveItems.isNotEmpty() && live != null && (!live.isAvailable || !live.inStock)) {
                    unavailableMessages.add("${line.menuItem.name} is no longer available")
                    continue
                }
                val resolvedItem = if (live != null) {
                    line.menuItem.copy(
                        id = live.id,
                        name = live.name,
                        price = live.price,
                        imageUrl = live.imageUrl,
                        itemSize = live.itemSize,
                        availability = live.availability,
                        salePrice = live.salePrice,
                        saleStartsAt = live.saleStartsAt,
                        saleEndsAt = live.saleEndsAt,
                        saleActive = live.saleActive,
                        hasSets = live.hasSets,
                        sets = live.sets
                    )
                } else {
                    line.menuItem
                }
                cartRepository.addItem(
                    menuItem = resolvedItem,
                    restaurant = rest,
                    quantity = line.quantity.coerceAtLeast(1),
                    selections = line.selections,
                    specialInstructions = line.specialInstructions,
                    bypassHoursCheck = true
                )
                addedCount++
            }
        }

        recalculateFees()
        return ReorderResult(
            addedCount = addedCount,
            unavailableMessages = unavailableMessages,
            priceUpdatedCount = priceUpdatedCount
        )
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
        paymentMethod: String = "cod",
        fulfillmentOverride: String? = null
    ): Result<String> {
        val restaurant = currentRestaurant.value
            ?: return Result.failure(IllegalStateException("No merchant selected"))
        val items = cartItems.value
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("Your cart is empty"))
        }

        val unavailableChoice = items.firstNotNullOfOrNull { item ->
            getFirstUnavailableChoice(item)
        }
        if (unavailableChoice != null) {
            return Result.failure(IllegalStateException("${unavailableChoice.choiceName} is no longer available"))
        }

        val rawMode = (fulfillmentOverride ?: _fulfillment.value).lowercase().trim()
        val mode = when {
            rawMode == "pickup" || rawMode.contains("pick") -> "pickup"
            rawMode == "scheduled" || rawMode.contains("sched") -> "scheduled"
            else -> "delivery"
        }
        val isPickup = mode == "pickup"

        // Store closed / holiday check
        if (mode != "scheduled" && !com.example.util.BusinessHoursUtil.effectiveOpen(
                shop = restaurant,
                timezone = _platformSettings.value.timezone,
                platformHolidays = _platformSettings.value.holidays
            )
        ) {
            return Result.failure(IllegalStateException("This store just closed and is not accepting orders."))
        }

        if (!isPickup && (address == null || address.id == "addr_none")) {
            return Result.failure(IllegalStateException("Please set or pin your delivery address first!"))
        }

        if (mode == "scheduled") {
            val sched = _scheduledFor.value
            if (sched == null) {
                return Result.failure(IllegalStateException("Please select a date and time for scheduled delivery."))
            }
            val minTime = System.currentTimeMillis() + 45 * 60 * 1000 - 60000 // allow small grace
            if (sched < minTime) {
                return Result.failure(IllegalStateException("Scheduled time must be at least 45 minutes ahead."))
            }
            val (scheduleValid, reason) = com.example.util.BusinessHoursUtil.isScheduleTimeValid(
                shop = restaurant,
                scheduledMillis = sched,
                timezone = _platformSettings.value.timezone,
                platformHolidays = _platformSettings.value.holidays
            )
            if (!scheduleValid) {
                return Result.failure(IllegalStateException(reason ?: "Store is closed at scheduled time"))
            }
        }

        val checkTimeMillis = if (mode == "scheduled") (_scheduledFor.value ?: System.currentTimeMillis()) else System.currentTimeMillis()
        val shopMenus = firestoreService.getMerchantMenus(restaurant.id)
        val unavailableItem = items.firstOrNull { item ->
            !com.example.util.MenuAvailabilityUtil.isMenuItemOrderable(item.menuItem, shopMenus, checkTimeMillis, _platformSettings.value.timezone)
        }
        if (unavailableItem != null) {
            val msg = if (mode == "scheduled") {
                "${unavailableItem.menuItem.name} is not available at the scheduled time"
            } else {
                "${unavailableItem.menuItem.name} is not available right now"
            }
            return Result.failure(IllegalStateException(msg))
        }

        // Re-check variant availability and latest prices against live merchant items
        val liveItems = try {
            firestoreService.getMerchantAvailableItems(restaurant.id)
        } catch (_: Exception) {
            emptyList()
        }
        val liveItemMap = liveItems.associateBy { it.id }
        val hasAnyLiveVariantItemInCart = items.any { !it.variantKey.isNullOrBlank() }
        if (hasAnyLiveVariantItemInCart) {
            // Sync against liveItems if any were returned (and enforce missing items only when live catalog is non-empty and matches shop)
            val syncErrors = syncCartWithLiveMerchantItems(
                merchantItems = liveItems,
                enforceMissingItemAsUnavailable = liveItems.isNotEmpty()
            )
            if (syncErrors.isNotEmpty()) {
                return Result.failure(IllegalStateException(syncErrors.values.first()))
            }
            // Extra guard: if liveItemMap contains the item, ensure quantity <= stock and stock > 0
            for (cartItem in cartItems.value) {
                val vKey = cartItem.variantKey ?: continue
                val liveItem = liveItemMap[cartItem.menuItem.id]
                val variant = (liveItem?.variants ?: cartItem.menuItem.variants).let {
                    com.example.util.VariationUtil.findVariantByKey(it, vKey)
                }
                if (variant != null && variant.stock <= 0) {
                    val vLabel = cartItem.variantLabel?.ifBlank { null } ?: vKey
                    return Result.failure(IllegalStateException("${cartItem.menuItem.name} ($vLabel) is no longer available"))
                }
            }
        }

        val updatedItemsForOrder = cartItems.value

        if (isPickup) {
            _tipAmount.value = 0.0
        }

        recalculateFees()
        val currentFees = _feesResult.value

        val finalDeliveryFee = if (isPickup) 0.0 else currentFees.deliveryFee
        val finalTip = if (isPickup) 0.0 else currentFees.tip
        val finalTotal = if (isPickup) {
            val base = currentFees.subtotal + currentFees.serviceFee - currentFees.discount + (if (currentFees.vatMode == "added") currentFees.vat else 0.0)
            com.example.util.FeeCalculator.round2(base.coerceAtLeast(0.0))
        } else {
            currentFees.total
        }
        val finalFees = currentFees.fees.copy(
            subtotal = currentFees.subtotal,
            deliveryFee = finalDeliveryFee,
            serviceFee = currentFees.serviceFee,
            tip = finalTip,
            discount = currentFees.discount,
            total = finalTotal
        )

        val fbUid = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }

        val optKey = getEffectiveDeliveryOption().lowercase().trim().ifBlank { "priority" }
        val optConfig = _dispatchSettings.value.options[optKey] ?: com.example.data.model.DispatchOptionConfig(
            endsWhenReady = if (optKey.equals("saver", ignoreCase = true)) false else true
        )
        val merchantSr = restaurant.specialRadius
        val srMap: Map<String, Any?>? = if (merchantSr != null) {
            mapOf(
                "enabled" to merchantSr.enabled,
                "customerExtra" to merchantSr.customerExtraFee,
                "riderExtra" to merchantSr.riderExtraFee,
                "radiusKm" to merchantSr.radiusKm
            )
        } else null

        val effectiveSearchRadiusKm = if (merchantSr?.enabled == true && merchantSr.radiusKm > 0.0) {
            merchantSr.radiusKm
        } else {
            _dispatchSettings.value.searchRadiusKm
        }

        val dispatchMap: Map<String, Any?> = mapOf(
            "option" to optKey,
            "holdMinutes" to optConfig.holdMinutes,
            "maxBatch" to optConfig.maxBatch,
            "poolable" to (optConfig.maxBatch > 1),
            "searchRadiusKm" to effectiveSearchRadiusKm,
            "specialRadius" to srMap,
            "endsWhenReady" to optConfig.endsWhenReady
        )

        val result = orderRepository.placeFoodOrConvenienceOrder(
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            restaurant = restaurant,
            items = updatedItemsForOrder,
            deliveryAddress = if (isPickup) null else address,
            deliveryLat = if (isPickup) 0.0 else (address?.lat ?: 0.0),
            deliveryLng = if (isPickup) 0.0 else (address?.lng ?: 0.0),
            deliveryFee = finalDeliveryFee,
            serviceFee = currentFees.serviceFee,
            discount = currentFees.discount,
            subtotal = currentFees.subtotal,
            total = finalTotal,
            cityId = cityId,
            deliveryNotes = deliveryNotes.value,
            paymentMethod = paymentMethod,
            fulfillment = mode,
            scheduledFor = _scheduledFor.value,
            tip = finalTip,
            orderFees = finalFees,
            deliveryOption = if (isPickup) null else optKey,
            promoCode = appliedPromoCode.value,
            promoOldUsedCount = cartRepository.appliedPromo.value?.usedCount,
            dispatch = if (isPickup) null else dispatchMap
        )

        // Show the Firestore error message in a snackbar if the write fails (for example permission denied),
        // and do not clear the cart or show "Order placed" until the write succeeds.
        if (result.isSuccess) {
            clearCart()
        } else {
            val err = result.exceptionOrNull()
            if (err?.message?.contains("This code is no longer available", ignoreCase = true) == true) {
                removePromo()
            }
        }
        return result
    }
}
