package com.example.data.repository

import android.util.Log
import com.example.data.model.Address
import com.example.data.model.CartItem
import com.example.data.model.LocationPoint
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderDocument
import com.example.data.model.OrderFees
import com.example.data.model.OrderItem
import com.example.data.model.OrderStatus
import com.example.data.model.PabiliDetails
import com.example.data.model.PabiliInfo
import com.example.data.model.PabiliItem
import com.example.data.model.PabiliItemInfo
import com.example.data.model.ParcelDetails
import com.example.data.model.ParcelInfo
import com.example.data.model.PricingSnapshot
import com.example.data.model.Restaurant
import com.example.data.model.ReviewDocument
import com.example.data.model.Rider
import com.example.data.model.RiderLocation
import com.example.data.model.ServiceType
import com.example.data.model.StatusHistoryEntry
import com.example.util.DelivioStatusConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrackerDisplayState {
    EXPANDED,
    COLLAPSED,
    BUBBLE
}

data class MerchantCancellationAlert(
    val orderId: String,
    val merchantName: String,
    val reason: String?
)

class OrderRepository(
    val firestoreService: FirestoreService,
    val preferenceRepository: PreferenceRepository? = null
) {
    private val tag = "DelivioOrderRepo"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _activeOrder = MutableStateFlow<Order?>(null)
    val activeOrder: StateFlow<Order?> = _activeOrder.asStateFlow()

    private val _activeOrders = MutableStateFlow<List<Order>>(emptyList())
    val activeOrders: StateFlow<List<Order>> = _activeOrders.asStateFlow()

    private val _orderHistory = MutableStateFlow<List<Order>>(emptyList())
    val orderHistory: StateFlow<List<Order>> = _orderHistory.asStateFlow()

    private val _trackerState = MutableStateFlow(TrackerDisplayState.COLLAPSED)
    val trackerState: StateFlow<TrackerDisplayState> = _trackerState.asStateFlow()

    private val _isMerchantCollapsed = MutableStateFlow(false)
    val isMerchantCollapsed: StateFlow<Boolean> = _isMerchantCollapsed.asStateFlow()

    private val _isRiderCollapsed = MutableStateFlow(false)
    val isRiderCollapsed: StateFlow<Boolean> = _isRiderCollapsed.asStateFlow()

    private val _statusNotificationEvent = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val statusNotificationEvent: SharedFlow<String> = _statusNotificationEvent.asSharedFlow()

    private val _merchantCancellationAlert = MutableStateFlow<MerchantCancellationAlert?>(null)
    val merchantCancellationAlert: StateFlow<MerchantCancellationAlert?> = _merchantCancellationAlert.asStateFlow()

    private val _pendingReviewOrder = MutableStateFlow<Order?>(null)
    val pendingReviewOrder: StateFlow<Order?> = _pendingReviewOrder.asStateFlow()
    private val _customerReviews = MutableStateFlow<Map<String, ReviewDocument>>(emptyMap())
    val customerReviews: StateFlow<Map<String, ReviewDocument>> = _customerReviews.asStateFlow()
    private val handledReviewOrderIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val locallyCancelledOrderIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun dismissMerchantCancellationAlert() {
        _merchantCancellationAlert.value = null
    }

    fun dismissReview(orderId: String) {
        handledReviewOrderIds.add(orderId)
        preferenceRepository?.markOrderReviewHandled(orderId)
        if (_pendingReviewOrder.value?.id == orderId) {
            _pendingReviewOrder.value = null
        }
    }

    suspend fun submitReview(
        order: Order,
        ratingMerchant: Int?,
        ratingRider: Int?,
        comment: String,
        tipAmount: Double = 0.0
    ): Result<Unit> {
        val serviceTypeStr = when (order.serviceType) {
            ServiceType.CONVENIENCE -> "convenience"
            ServiceType.PARCEL -> "parcel"
            ServiceType.PABILI -> "pabili"
            ServiceType.TAXI -> "taxi"
            else -> "food"
        }

        val resolvedOrderNumber = order.orderNumber.ifBlank { order.id.takeLast(6).uppercase() }
        val resolvedMerchantId = if (serviceTypeStr in listOf("food", "convenience")) order.merchantId else null
        val resolvedRiderId = if (order.fulfillment != "pickup") order.riderId else null

        val res = firestoreService.submitReviewAndTipBatch(
            orderId = order.id,
            orderNumber = resolvedOrderNumber,
            serviceType = serviceTypeStr,
            customerId = order.customerId,
            merchantId = resolvedMerchantId,
            riderId = resolvedRiderId,
            ratingMerchant = ratingMerchant,
            ratingRider = ratingRider,
            comment = comment,
            tipAmount = tipAmount
        )

        if (res.isSuccess) {
            handledReviewOrderIds.add(order.id)
            preferenceRepository?.markOrderReviewHandled(order.id)
            val newReview = ReviewDocument(
                orderId = order.id,
                orderNumber = resolvedOrderNumber,
                serviceType = serviceTypeStr,
                customerId = order.customerId,
                merchantId = resolvedMerchantId,
                riderId = resolvedRiderId,
                ratingMerchant = ratingMerchant,
                ratingRider = ratingRider,
                comment = comment.trim().take(300),
                createdAt = System.currentTimeMillis()
            )
            _customerReviews.update { current -> current + (order.id to newReview) }
            if (_pendingReviewOrder.value?.id == order.id) {
                _pendingReviewOrder.value = null
            }
        }
        return res
    }

    private var ordersListenerJob: Job? = null
    private var reviewsListenerJob: Job? = null
    private var currentCustomerId: String = ""
    private val orderListeners = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private val _isRefreshingOrders = MutableStateFlow(false)
    val isRefreshingOrders: StateFlow<Boolean> = _isRefreshingOrders.asStateFlow()

    private fun syncActiveOrderListeners(orders: List<Order>) {
        val currentIds = orders.map { it.id }.filter { it.isNotEmpty() }.toSet()
        // Cancel removed order listeners
        val iterator = orderListeners.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentIds.contains(entry.key)) {
                entry.value.cancel()
                iterator.remove()
            }
        }
        // Launch background snapshot listeners for every active order
        for (order in orders) {
            val orderId = order.id
            if (orderId.isNotEmpty() && !orderListeners.containsKey(orderId)) {
                val job = scope.launch {
                    firestoreService.getOrderFlow(orderId).collect { doc ->
                        if (doc != null) {
                            val rawSt = doc.status
                            val isTerm = rawSt.equals(DelivioStatusConstants.STATUS_REJECTED, ignoreCase = true) ||
                                    rawSt.equals(DelivioStatusConstants.STATUS_CANCELLED, ignoreCase = true) ||
                                    rawSt.equals("canceled", ignoreCase = true)
                            if (doc.paymentMethod.equals("wallet", ignoreCase = true) && isTerm && !doc.walletRefunded) {
                                scope.launch {
                                    val res = firestoreService.processWalletRefund(doc.id, doc.customerId, doc.fees.total)
                                    res.onSuccess { msg ->
                                        if (msg != "Already processed") {
                                            _statusNotificationEvent.tryEmit(msg)
                                        }
                                    }.onFailure { err ->
                                        _statusNotificationEvent.tryEmit(err.message ?: "Refund error")
                                    }
                                }
                            }
                            if (doc.paymentMethod.equals("wallet", ignoreCase = true) && doc.walletAdjustDue > 0.0 && doc.walletAdjustApplied <= 0.0) {
                                scope.launch {
                                    val res = firestoreService.processOrderEditWalletRefund(doc.id, doc.customerId, doc.walletAdjustDue)
                                    res.onSuccess { msg ->
                                        if (msg.isNotEmpty()) {
                                            _statusNotificationEvent.tryEmit("₱${String.format(java.util.Locale.US, "%.2f", doc.walletAdjustDue)} refunded to your wallet for the updated order")
                                        }
                                    }
                                }
                            }
                            val uiOrder = mapDocToOrder(doc)
                            val isLocallyCancelled = locallyCancelledOrderIds.contains(orderId)
                            if (isLocallyCancelled || DelivioStatusConstants.isTerminal(uiOrder.rawStatus) || DelivioStatusConstants.isTerminal(uiOrder.status.title)) {
                                _activeOrders.update { current -> current.filter { it.id != orderId } }
                                orderListeners.remove(orderId)?.cancel()
                                if (_activeOrder.value?.id == orderId) {
                                    val remaining = _activeOrders.value
                                    if (remaining.isNotEmpty()) {
                                        _activeOrder.value = remaining.first()
                                    } else {
                                        _activeOrder.value = null
                                        _trackerState.value = TrackerDisplayState.COLLAPSED
                                    }
                                }
                            } else {
                                _activeOrders.update { current ->
                                    if (current.any { it.id == orderId }) {
                                        current.map { if (it.id == orderId) uiOrder else it }
                                    } else {
                                        listOf(uiOrder) + current
                                    }
                                }
                                if (_activeOrder.value?.id == orderId) {
                                    _activeOrder.value = uiOrder
                                }
                            }
                        }
                    }
                }
                orderListeners[orderId] = job
            }
        }
    }

    // Listen live to orders/{orderId}
    fun listenToOrder(orderId: String) {
        if (orderId.isEmpty()) return
        syncActiveOrderListeners(_activeOrders.value)
    }

    fun selectActiveOrder(orderId: String) {
        val found = _activeOrders.value.find { it.id == orderId }
        if (found != null) {
            _activeOrder.value = found
        }
    }

    fun refreshOrders() {
        val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        val targetId = fbUid?.takeIf { it.isNotBlank() } ?: currentCustomerId
        if (targetId.isBlank()) {
            _isRefreshingOrders.value = false
            return
        }
        _isRefreshingOrders.value = true
        setCustomerId(targetId)
        scope.launch {
            kotlinx.coroutines.delay(1500L)
            _isRefreshingOrders.value = false
        }
    }

    fun setCustomerId(customerId: String) {
        currentCustomerId = customerId
        ordersListenerJob?.cancel()
        reviewsListenerJob?.cancel()
        if (customerId.isEmpty()) {
            orderListeners.values.forEach { it.cancel() }
            orderListeners.clear()
            _customerReviews.value = emptyMap()
            _isRefreshingOrders.value = false
            return
        }

        reviewsListenerJob = scope.launch {
            firestoreService.getCustomerReviewsFlow(customerId).collect { reviewsMap ->
                _customerReviews.value = reviewsMap
            }
        }

        ordersListenerJob = scope.launch {
            firestoreService.getCustomerOrdersFlow(customerId).collect { documents ->
                _isRefreshingOrders.value = false
                // Never list orders with isSplitChild
                val validDocs = documents.filter { !it.isSplitChild }
                val allOrders = validDocs.map { mapDocToOrder(it) }
                _orderHistory.value = allOrders

                // 1. REJECTED / CANCELLED ORDERS DISAPPEAR FROM TRACKING
                // Tracker and active orders show ONLY orders where status is NOT Delivered, Rejected, or Cancelled.
                val currentActive = allOrders.filter {
                    !DelivioStatusConstants.isTerminal(it.rawStatus) &&
                    !DelivioStatusConstants.isTerminal(it.status.title)
                }
                _activeOrders.value = currentActive
                syncActiveOrderListeners(currentActive)

                // Manage currently tracked active order (can switch between multiple active orders)
                val currentTrackedId = _activeOrder.value?.id
                if (currentActive.isEmpty()) {
                    _activeOrder.value = null
                    _trackerState.value = TrackerDisplayState.COLLAPSED
                } else if (currentTrackedId != null) {
                    val matching = currentActive.find { it.id == currentTrackedId }
                    if (matching != null) {
                        _activeOrder.value = matching
                    } else {
                        _activeOrder.value = currentActive.first()
                    }
                } else {
                    _activeOrder.value = currentActive.first()
                }

                // 2. CHECK FOR UNANNOUNCED MERCHANT CANCELLATIONS (ONE-TIME DIALOG)
                // When merchant cancels/rejects, notify user with dialog once and save order ID locally
                for (order in allOrders) {
                    val isTerminalCancel = order.status == OrderStatus.REJECTED ||
                            order.status == OrderStatus.CANCELLED ||
                            order.rawStatus.equals(DelivioStatusConstants.STATUS_REJECTED, ignoreCase = true) ||
                            order.rawStatus.equals(DelivioStatusConstants.STATUS_CANCELLED, ignoreCase = true) ||
                            order.rawStatus.equals("canceled", ignoreCase = true)

                    if (isTerminalCancel && !order.cancelledByCustomer) {
                        val alreadyAnnounced = preferenceRepository?.isOrderCancellationAnnounced(order.id) ?: false
                        if (!alreadyAnnounced) {
                            preferenceRepository?.markOrderCancellationAnnounced(order.id)
                            val reason = order.rejectReason?.ifBlank { null } ?: order.cancelReason?.ifBlank { null }
                            _merchantCancellationAlert.value = MerchantCancellationAlert(
                                orderId = order.id,
                                merchantName = order.restaurant.name,
                                reason = reason
                            )
                            // If this was the active order, switch to next active order or close tracker
                            if (_activeOrder.value?.id == order.id) {
                                val remaining = currentActive.filter { it.id != order.id }
                                if (remaining.isNotEmpty()) {
                                    _activeOrder.value = remaining.first()
                                } else {
                                    _activeOrder.value = null
                                    _trackerState.value = TrackerDisplayState.COLLAPSED
                                }
                            }
                        }
                    }
                }

                // 3. AUTOMATIC WALLET REFUND (app start and live status updates)
                for (doc in documents) {
                    val rawSt = doc.status
                    val isTerm = rawSt.equals(DelivioStatusConstants.STATUS_REJECTED, ignoreCase = true) ||
                            rawSt.equals(DelivioStatusConstants.STATUS_CANCELLED, ignoreCase = true) ||
                            rawSt.equals("canceled", ignoreCase = true)
                    if (doc.paymentMethod.equals("wallet", ignoreCase = true) && isTerm && !doc.walletRefunded) {
                        scope.launch {
                            val res = firestoreService.processWalletRefund(doc.id, customerId, doc.fees.total)
                            res.onSuccess { msg ->
                                if (msg != "Already processed") {
                                    _statusNotificationEvent.tryEmit(msg)
                                }
                            }.onFailure { err ->
                                _statusNotificationEvent.tryEmit(err.message ?: "Refund error")
                            }
                        }
                    }
                }

                // 4. CHECK FOR DELIVERED / COMPLETED ORDERS TO REVIEW (ONE RATING SCREEN PER ORDER)
                for (order in allOrders) {
                    val isDeliveredOrCompleted = order.rawStatus.equals(DelivioStatusConstants.STATUS_DELIVERED, ignoreCase = true) ||
                            order.rawStatus.equals(DelivioStatusConstants.STATUS_COMPLETED, ignoreCase = true) ||
                            order.status == OrderStatus.DELIVERED ||
                            order.status == OrderStatus.COMPLETED

                    if (isDeliveredOrCompleted && !handledReviewOrderIds.contains(order.id)) {
                        if (preferenceRepository?.isOrderReviewHandled(order.id) == true) {
                            handledReviewOrderIds.add(order.id)
                        } else {
                            scope.launch {
                                val exists = _customerReviews.value.containsKey(order.id) ||
                                    firestoreService.reviewExists(order.id, customerId)
                                if (exists) {
                                    handledReviewOrderIds.add(order.id)
                                    preferenceRepository?.markOrderReviewHandled(order.id)
                                } else {
                                    if (_pendingReviewOrder.value == null) {
                                        _pendingReviewOrder.value = order
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    fun setTrackerState(state: TrackerDisplayState) {
        _trackerState.value = state
    }

    fun toggleMerchantCollapsed() {
        _isMerchantCollapsed.value = !_isMerchantCollapsed.value
    }

    fun toggleRiderCollapsed() {
        _isRiderCollapsed.value = !_isRiderCollapsed.value
    }

    fun dismissActiveOrder(orderId: String? = null) {
        val targetId = orderId ?: _activeOrder.value?.id
        if (targetId != null) {
            val remaining = _activeOrders.value.filter { it.id != targetId }
            _activeOrders.value = remaining
            orderListeners.remove(targetId)?.cancel()
            if (remaining.isNotEmpty()) {
                _activeOrder.value = remaining.first()
            } else {
                _activeOrder.value = null
                _trackerState.value = TrackerDisplayState.COLLAPSED
            }
        } else {
            _activeOrder.value = null
            _activeOrders.value = emptyList()
            orderListeners.values.forEach { it.cancel() }
            orderListeners.clear()
            _trackerState.value = TrackerDisplayState.COLLAPSED
        }
    }

    // Cancel order in Firestore (only permitted if status is "Placed Order")
    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> {
        val result = firestoreService.cancelOrder(orderId, reason)
        if (result.isSuccess) {
            locallyCancelledOrderIds.add(orderId)
            preferenceRepository?.markOrderCancelledByCustomer(orderId)
            preferenceRepository?.markOrderCancellationAnnounced(orderId)
            orderListeners.remove(orderId)?.cancel()
            _activeOrders.update { current -> current.filter { it.id != orderId } }
            if (_activeOrder.value?.id == orderId) {
                val remaining = _activeOrders.value
                if (remaining.isNotEmpty()) {
                    _activeOrder.value = remaining.first()
                } else {
                    _activeOrder.value = null
                    _trackerState.value = TrackerDisplayState.COLLAPSED
                }
            }
            _statusNotificationEvent.tryEmit("Order cancelled")
        }
        return result
    }

    // Create Order in Firestore (Food / Convenience)
    suspend fun placeFoodOrConvenienceOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        restaurant: Restaurant,
        items: List<CartItem>,
        deliveryAddress: Address?,
        deliveryLat: Double,
        deliveryLng: Double,
        deliveryFee: Double,
        serviceFee: Double,
        discount: Double,
        subtotal: Double,
        total: Double,
        cityId: String,
        deliveryNotes: String,
        paymentMethod: String = "cod",
        fulfillment: String = DelivioStatusConstants.FULFILLMENT_DELIVERY,
        scheduledFor: Long? = null,
        tip: Double = 0.0,
        orderFees: OrderFees? = null,
        deliveryOption: String? = null,
        promoCode: String? = null,
        promoOldUsedCount: Int? = null,
        dispatch: Map<String, Any?>? = null,
        pricing: PricingSnapshot? = null
    ): Result<String> {
        val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }

        val serviceTypeStr = if (restaurant.isConvenience) DelivioStatusConstants.SERVICE_CONVENIENCE else DelivioStatusConstants.SERVICE_FOOD

        val isPickup = fulfillment.equals(DelivioStatusConstants.FULFILLMENT_PICKUP, ignoreCase = true)
        val finalDeliveryFee = if (isPickup) 0.0 else (orderFees?.deliveryFee ?: deliveryFee)
        val finalTip = if (isPickup) 0.0 else (orderFees?.tip ?: tip)
        val finalServiceFee = orderFees?.serviceFee ?: serviceFee
        val finalDiscount = orderFees?.discount ?: discount
        val finalVat = orderFees?.vat ?: 0.0
        val finalVatMode = orderFees?.vatMode ?: "added"

        val computedTotal = if (isPickup) {
            val base = subtotal + finalServiceFee - finalDiscount + (if (finalVatMode == "added") finalVat else 0.0)
            com.example.util.FeeCalculator.round2(base.coerceAtLeast(0.0))
        } else {
            orderFees?.total ?: total
        }

        val resolvedFees = orderFees?.copy(
            subtotal = com.example.util.FeeCalculator.round2(subtotal),
            deliveryFee = finalDeliveryFee,
            serviceFee = finalServiceFee,
            tip = finalTip,
            discount = finalDiscount,
            total = computedTotal
        ) ?: OrderFees(
            subtotal = subtotal,
            deliveryFee = finalDeliveryFee,
            serviceFee = finalServiceFee,
            discount = finalDiscount,
            tip = finalTip,
            total = computedTotal
        )

        val resolvedPricing = (pricing ?: com.example.util.FeeCalculator.buildPricingSnapshot(
            schema = 2,
            ratesVersion = 0,
            serviceKey = serviceTypeStr,
            vehicle = null,
            deliveryFee = finalDeliveryFee,
            serviceFee = finalServiceFee,
            tip = finalTip,
            vat = finalVat,
            itemsSubtotal = com.example.util.FeeCalculator.round2(subtotal),
            total = computedTotal,
            payer = "customer",
            merchantCommissionRate = restaurant.commissionRate
        )).let { snap ->
            if (isPickup) {
                snap.copy(deliveryFee = 0.0, tip = 0.0, total = computedTotal)
            } else {
                snap
            }
        }

        val isWallet = paymentMethod.equals("wallet", ignoreCase = true)
        val provider = if (isWallet) "wallet" else null
        val status = if (isWallet) "paid" else "unpaid"
        val cashCollect = if (isWallet) 0.0 else computedTotal

        val combinedDropoffNote = if (deliveryAddress == null) {
            deliveryNotes.trim()
        } else {
            listOf(deliveryAddress.details.trim(), deliveryNotes.trim())
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" • ")
        }
        val dropoffPoint = if (isPickup || deliveryAddress == null) null else LocationPoint(
            label = deliveryAddress.title,
            address = deliveryAddress.fullAddress,
            lat = deliveryLat,
            lng = deliveryLng,
            contactName = customerName,
            contactPhone = customerPhone,
            details = deliveryAddress.details,
            name = customerName,
            phone = customerPhone,
            note = combinedDropoffNote
        )

        val orderNum = com.example.util.OrderNumberUtil.generateOrderNumber()
        val orderDoc = OrderDocument(
            orderNumber = orderNum,
            serviceType = serviceTypeStr,
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = restaurant.id,
            merchantName = restaurant.name,
            branchName = restaurant.branchName.ifBlank { null },
            branchCode = restaurant.branchCode.ifBlank { null },
            chainId = restaurant.chainId.ifBlank { null },
            riderId = null,
            riderName = null,
            riderPhone = null,
            riderLocation = null,
            status = DelivioStatusConstants.STATUS_PLACED_ORDER,
            statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, System.currentTimeMillis())),
            items = items.map {
                OrderItem(
                    itemId = it.menuItem.id,
                    name = it.menuItem.name,
                    qty = it.quantity,
                    basePrice = it.basePrice,
                    price = it.unitPrice,
                    unitPrice = it.unitPrice,
                    regularUnitPrice = it.regularUnitPrice,
                    selections = it.selections,
                    notes = it.specialInstructions,
                    variantKey = it.variantKey,
                    variantLabel = it.variantLabel,
                    variantImageUrl = it.variantImageUrl
                )
            },
            pickup = LocationPoint(
                label = restaurant.displayName,
                address = restaurant.address,
                lat = restaurant.lat,
                lng = restaurant.lng,
                contactName = restaurant.displayName,
                contactPhone = restaurant.phone,
                name = restaurant.displayName,
                phone = restaurant.phone,
                note = ""
            ),
            dropoff = dropoffPoint,
            fees = resolvedFees,
            pricing = resolvedPricing,
            paymentMethod = if (isWallet) "wallet" else "cod",
            paymentProvider = provider,
            paymentStatus = status,
            cashToCollect = cashCollect,
            cashCollectAt = null,
            walletRefunded = false,
            cityId = cityId,
            fulfillment = fulfillment,
            scheduledFor = scheduledFor,
            note = deliveryNotes.trim(),
            deliveryOption = if (isPickup) null else deliveryOption,
            promoCode = promoCode,
            promo = if (promoCode != null) {
                mapOf(
                    "code" to promoCode,
                    "discount" to resolvedFees.discount,
                    "fundedBy" to "merchant",
                    "merchantId" to restaurant.id
                )
            } else null,
            dispatch = if (isPickup) null else dispatch
        )

        val result = firestoreService.createOrder(orderDoc, promoOldUsedCount)
        result.onSuccess { newId ->
            val savedDoc = orderDoc.copy(id = newId)
            val newOrder = mapDocToOrder(savedDoc)
            _activeOrders.update { current ->
                listOf(newOrder) + current.filter { it.id != newId }
            }
            _activeOrder.value = newOrder
            listenToOrder(newId)
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Order placed! Tracking your delivery now.")
        }
        return result
    }

    // Create Parcel Order in Firestore
    suspend fun bookParcelOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        parcelDetails: ParcelDetails,
        deliveryFee: Double,
        serviceFee: Double = 15.0,
        cityId: String = "puerto-princesa",
        feePayer: String = "sender",
        paymentMethod: String = "cod",
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm",
        vat: Double = 0.0,
        vatPercent: Double = 0.0,
        vatMode: String = "added",
        vatBase: Double = 0.0,
        serviceFeeType: String = "flat",
        serviceFeePercent: Double = 0.0,
        ratesVersion: Int = 0
    ): Result<String> {
        val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }

        val roundedDelivery = com.example.util.FeeCalculator.round2(deliveryFee)
        val roundedService = com.example.util.FeeCalculator.round2(serviceFee)
        val roundedVat = com.example.util.FeeCalculator.round2(vat)
        val total = com.example.util.FeeCalculator.round2(
            roundedDelivery + roundedService + (if (vatMode.equals("added", ignoreCase = true)) roundedVat else 0.0)
        )
        val isWallet = paymentMethod.equals("wallet", ignoreCase = true)
        val isOnline = paymentMethod.equals("online", ignoreCase = true)
        val isNoCash = isWallet || isOnline
        val provider = if (isWallet) "wallet" else if (isOnline) "online" else null
        val status = if (isNoCash) "paid" else "unpaid"
        val normalizedFeePayer = if (feePayer.trim().equals("recipient", ignoreCase = true)) "recipient" else "sender"
        val cashCollect = if (isNoCash) 0.0 else total
        val cashCollectAt = if (isNoCash) null else normalizedFeePayer
        val normalizedVehicleType = com.example.util.ParcelFeeCalculator.normalizeParcelVehicleType(parcelDetails.vehicleType)

        val orderNum = com.example.util.OrderNumberUtil.generateOrderNumber()
        val orderDoc = OrderDocument(
            orderNumber = orderNum,
            serviceType = DelivioStatusConstants.SERVICE_PARCEL,
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = null,
            merchantName = null,
            vehicleType = normalizedVehicleType,
            riderId = null,
            status = DelivioStatusConstants.STATUS_PLACED_ORDER,
            statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, System.currentTimeMillis())),
            items = emptyList(),
            pickup = LocationPoint(
                label = "Sender: ${parcelDetails.senderName}",
                address = parcelDetails.senderAddress,
                lat = parcelDetails.pickupLat.toDouble(),
                lng = parcelDetails.pickupLng.toDouble(),
                contactName = parcelDetails.senderName,
                contactPhone = parcelDetails.senderPhone,
                name = parcelDetails.senderName,
                phone = parcelDetails.senderPhone,
                note = ""
            ),
            dropoff = LocationPoint(
                label = "Recipient: ${parcelDetails.recipientName}",
                address = parcelDetails.recipientAddress,
                lat = parcelDetails.dropoffLat.toDouble(),
                lng = parcelDetails.dropoffLng.toDouble(),
                contactName = parcelDetails.recipientName,
                contactPhone = parcelDetails.recipientPhone,
                name = parcelDetails.recipientName,
                phone = parcelDetails.recipientPhone,
                note = ""
            ),
            parcel = ParcelInfo(
                size = parcelDetails.packageSize,
                weightKg = parcelDetails.weightKg,
                description = parcelDetails.itemDescription,
                fragile = parcelDetails.isFragile,
                feePayer = normalizedFeePayer
            ),
            fees = OrderFees(
                subtotal = 0.0,
                deliveryFee = roundedDelivery,
                serviceFee = roundedService,
                serviceFeeType = serviceFeeType,
                serviceFeePercent = serviceFeePercent,
                discount = 0.0,
                vat = roundedVat,
                vatPercent = vatPercent,
                vatMode = vatMode,
                vatBase = com.example.util.FeeCalculator.round2(vatBase),
                total = total,
                distanceKm = distanceKm,
                distanceMethod = distanceMethod
            ),
            pricing = com.example.util.FeeCalculator.buildPricingSnapshot(
                schema = 2,
                ratesVersion = ratesVersion,
                serviceKey = "parcel",
                vehicle = normalizedVehicleType,
                deliveryFee = roundedDelivery,
                serviceFee = roundedService,
                tip = 0.0,
                vat = roundedVat,
                itemsSubtotal = 0.0,
                total = total,
                payer = "customer",
                merchantCommissionRate = null
            ),
            paymentMethod = if (isWallet) "wallet" else "cod",
            paymentProvider = provider,
            paymentStatus = status,
            cashToCollect = cashCollect,
            cashCollectAt = cashCollectAt,
            walletRefunded = false,
            cityId = cityId
        )

        val result = firestoreService.createOrder(orderDoc)
        result.onSuccess { newId ->
            val savedDoc = orderDoc.copy(id = newId)
            val newOrder = mapDocToOrder(savedDoc)
            val updated = listOf(newOrder) + _activeOrders.value.filter { it.id != newId }
            _activeOrders.value = updated
            _activeOrder.value = newOrder
            listenToOrder(newId)
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Parcel order placed! Tracking your order.")
        }
        return result
    }

    // Create Pabili Order in Firestore
    suspend fun bookPabiliOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        pabiliDetails: PabiliDetails,
        itemTotal: Double,
        deliveryFee: Double,
        serviceFee: Double = 20.0,
        cityId: String = "puerto-princesa",
        distanceKm: Double = 0.0,
        distanceMethod: String = "osrm",
        budget: Double = 500.0,
        vat: Double = 0.0,
        vatPercent: Double = 0.0,
        vatMode: String = "added",
        vatBase: Double = 0.0,
        serviceFeeType: String = "flat",
        serviceFeePercent: Double = 0.0,
        ratesVersion: Int = 0
    ): Result<String> {
        val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }

        val roundedDelivery = com.example.util.FeeCalculator.round2(deliveryFee)
        val roundedService = com.example.util.FeeCalculator.round2(serviceFee)
        val roundedVat = com.example.util.FeeCalculator.round2(vat)
        val totalFees = com.example.util.FeeCalculator.round2(
            roundedDelivery + roundedService + (if (vatMode.equals("added", ignoreCase = true)) roundedVat else 0.0)
        )

        val storePickupPoint = LocationPoint(
            label = "Vendor",
            address = pabiliDetails.storeAddress,
            lat = pabiliDetails.storeLat.toDouble(),
            lng = pabiliDetails.storeLng.toDouble(),
            contactName = "",
            contactPhone = "",
            name = pabiliDetails.storeName,
            phone = null,
            note = pabiliDetails.riderNotes
        )

        val orderNum = com.example.util.OrderNumberUtil.generateOrderNumber()
        val orderDoc = OrderDocument(
            orderNumber = orderNum,
            serviceType = DelivioStatusConstants.SERVICE_PABILI,
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = null,
            merchantName = null,
            riderId = null,
            status = DelivioStatusConstants.STATUS_PLACED_ORDER,
            statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, System.currentTimeMillis())),
            items = pabiliDetails.items.map { OrderItem(name = it.name, qty = it.quantity, price = it.estimatedPrice, notes = "") },
            pickup = storePickupPoint,
            dropoff = LocationPoint(
                label = "Recipient: ${pabiliDetails.recipientName}",
                address = pabiliDetails.deliveryAddress,
                lat = pabiliDetails.deliveryLat.toDouble(),
                lng = pabiliDetails.deliveryLng.toDouble(),
                contactName = pabiliDetails.recipientName,
                contactPhone = pabiliDetails.recipientPhone,
                details = pabiliDetails.riderNotes,
                name = pabiliDetails.recipientName,
                phone = pabiliDetails.recipientPhone,
                note = pabiliDetails.riderNotes
            ),
            pabili = PabiliInfo(
                vendorName = pabiliDetails.storeName,
                items = pabiliDetails.items.map { PabiliItemInfo(it.name, it.quantity, it.estimatedPrice) },
                budget = budget,
                finalReceiptTotal = null,
                pickup = storePickupPoint
            ),
            fees = OrderFees(
                subtotal = itemTotal,
                deliveryFee = roundedDelivery,
                serviceFee = roundedService,
                serviceFeeType = serviceFeeType,
                serviceFeePercent = serviceFeePercent,
                discount = 0.0,
                vat = roundedVat,
                vatPercent = vatPercent,
                vatMode = vatMode,
                vatBase = com.example.util.FeeCalculator.round2(vatBase),
                total = totalFees,
                estimatedItemsTotal = itemTotal,
                distanceKm = distanceKm,
                distanceMethod = distanceMethod
            ),
            pricing = com.example.util.FeeCalculator.buildPricingSnapshot(
                schema = 2,
                ratesVersion = ratesVersion,
                serviceKey = "pabili",
                vehicle = null,
                deliveryFee = roundedDelivery,
                serviceFee = roundedService,
                tip = 0.0,
                vat = roundedVat,
                itemsSubtotal = com.example.util.FeeCalculator.round2(itemTotal),
                total = totalFees,
                payer = "customer",
                merchantCommissionRate = null
            ),
            paymentMethod = "cod",
            paymentProvider = null,
            paymentStatus = "unpaid",
            cashToCollect = totalFees,
            cashCollectAt = "recipient",
            walletRefunded = false,
            cityId = cityId,
            note = pabiliDetails.riderNotes,
            budgetLimit = budget
        )

        val result = firestoreService.createOrder(orderDoc)
        result.onSuccess { newId ->
            val savedDoc = orderDoc.copy(id = newId)
            val newOrder = mapDocToOrder(savedDoc)
            val updated = listOf(newOrder) + _activeOrders.value.filter { it.id != newId }
            _activeOrders.value = updated
            _activeOrder.value = newOrder
            listenToOrder(newId)
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Pabili errand booked! Tracking your order.")
        }
        return result
    }

    internal fun buildTaxiOrderDocument(
        id: String = "",
        customerId: String,
        customerName: String,
        customerPhone: String,
        vehicleType: String,
        cityId: String,
        pickup: LocationPoint,
        dropoff: LocationPoint,
        stops: List<com.example.data.model.TaxiStop>,
        distanceKm: Double,
        rideFare: Double,
        serviceFee: Double,
        total: Double,
        paymentMethod: String,
        note: String,
        vat: Double = 0.0,
        vatPercent: Double = 0.0,
        vatMode: String = "added",
        vatBase: Double = 0.0,
        serviceFeeType: String = "flat",
        serviceFeePercent: Double = 0.0,
        ratesVersion: Int = 0
    ): OrderDocument {
        val isWallet = paymentMethod.equals("wallet", ignoreCase = true)
        val trimmedName = customerName.trim()
        val trimmedPhone = customerPhone.trim()
        val trimmedNote = note.trim()

        val enrichedPickup = pickup.copy(
            contactName = pickup.contactName.ifBlank { trimmedName },
            contactPhone = pickup.contactPhone.ifBlank { trimmedPhone },
            name = pickup.name.ifBlank { trimmedName },
            phone = pickup.phone?.takeIf { it.isNotBlank() } ?: trimmedPhone,
            note = pickup.note.ifBlank { pickup.details.ifBlank { trimmedNote } }
        )
        val enrichedDropoff = dropoff.copy(
            contactName = dropoff.contactName.ifBlank { trimmedName },
            contactPhone = dropoff.contactPhone.ifBlank { trimmedPhone },
            name = dropoff.name.ifBlank { trimmedName },
            phone = dropoff.phone?.takeIf { it.isNotBlank() } ?: trimmedPhone,
            note = dropoff.note.ifBlank { dropoff.details.ifBlank { trimmedNote } }
        )

        val orderNum = com.example.util.OrderNumberUtil.generateOrderNumber()
        return OrderDocument(
            id = id,
            orderNumber = orderNum,
            serviceType = "taxi",
            vehicleType = vehicleType,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = null,
            merchantName = null,
            riderId = null,
            status = "Placed Order",
            statusHistory = listOf(StatusHistoryEntry("Placed Order", System.currentTimeMillis())),
            cityId = cityId,
            pickup = enrichedPickup,
            dropoff = enrichedDropoff,
            stops = stops,
            fees = OrderFees(
                subtotal = com.example.util.FeeCalculator.round2(rideFare),
                serviceFee = com.example.util.FeeCalculator.round2(serviceFee),
                serviceFeeType = serviceFeeType,
                serviceFeePercent = serviceFeePercent,
                discount = 0.0,
                vat = com.example.util.FeeCalculator.round2(vat),
                vatPercent = vatPercent,
                vatMode = vatMode,
                vatBase = com.example.util.FeeCalculator.round2(vatBase),
                total = com.example.util.FeeCalculator.round2(total),
                distanceKm = distanceKm,
                distanceMethod = "osrm"
            ),
            pricing = com.example.util.FeeCalculator.buildPricingSnapshot(
                schema = 2,
                ratesVersion = ratesVersion,
                serviceKey = "taxi",
                vehicle = vehicleType,
                deliveryFee = com.example.util.FeeCalculator.round2(rideFare),
                serviceFee = com.example.util.FeeCalculator.round2(serviceFee),
                tip = 0.0,
                vat = com.example.util.FeeCalculator.round2(vat),
                itemsSubtotal = 0.0,
                total = com.example.util.FeeCalculator.round2(total),
                payer = "customer",
                merchantCommissionRate = null
            ),
            paymentMethod = paymentMethod,
            paymentStatus = if (isWallet) "paid" else "unpaid",
            cashToCollect = if (isWallet) 0.0 else com.example.util.FeeCalculator.round2(total),
            fulfillment = "delivery",
            note = note,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun placeTaxiOrder(
        customerId: String,
        customerName: String,
        customerPhone: String,
        vehicleType: String,
        cityId: String,
        pickup: LocationPoint,
        dropoff: LocationPoint,
        stops: List<com.example.data.model.TaxiStop>,
        distanceKm: Double,
        rideFare: Double,
        serviceFee: Double,
        total: Double,
        paymentMethod: String,
        note: String,
        vat: Double = 0.0,
        vatPercent: Double = 0.0,
        vatMode: String = "added",
        vatBase: Double = 0.0,
        serviceFeeType: String = "flat",
        serviceFeePercent: Double = 0.0,
        ratesVersion: Int = 0
    ): Result<String> {
        val fbUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }
        val effectiveCustomerId = fbUid ?: customerId.ifEmpty { "unauthenticated" }
        val orderDoc = buildTaxiOrderDocument(
            id = "",
            customerId = effectiveCustomerId,
            customerName = customerName,
            customerPhone = customerPhone,
            vehicleType = vehicleType,
            cityId = cityId,
            pickup = pickup,
            dropoff = dropoff,
            stops = stops,
            distanceKm = distanceKm,
            rideFare = rideFare,
            serviceFee = serviceFee,
            total = total,
            paymentMethod = paymentMethod,
            note = note,
            vat = vat,
            vatPercent = vatPercent,
            vatMode = vatMode,
            vatBase = vatBase,
            serviceFeeType = serviceFeeType,
            serviceFeePercent = serviceFeePercent,
            ratesVersion = ratesVersion
        )

        val result = firestoreService.createTaxiOrder(orderDoc)
        result.onSuccess { newId ->
            val savedDoc = orderDoc.copy(id = newId)
            val newOrder = mapDocToOrder(savedDoc)
            val updated = listOf(newOrder) + _activeOrders.value.filter { it.id != newId }
            _activeOrders.value = updated
            _activeOrder.value = newOrder
            listenToOrder(newId)
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Taxi ride booked! Searching for nearby driver.")
        }
        return result
    }

    internal fun mapDocToOrder(doc: OrderDocument): Order {
        val serviceType = when (doc.serviceType.lowercase()) {
            "parcel" -> ServiceType.PARCEL
            "pabili" -> ServiceType.PABILI
            "convenience" -> ServiceType.CONVENIENCE
            "taxi" -> ServiceType.TAXI
            else -> ServiceType.FOOD
        }

        val rawStatus = doc.status
        val isRejected = rawStatus.equals(DelivioStatusConstants.STATUS_REJECTED, ignoreCase = true)
        val isCancelled = rawStatus.equals(DelivioStatusConstants.STATUS_CANCELLED, ignoreCase = true) ||
                rawStatus.equals("canceled", ignoreCase = true)
        val isCompleted = rawStatus.equals(DelivioStatusConstants.STATUS_COMPLETED, ignoreCase = true)
        val isDelivered = rawStatus.equals(DelivioStatusConstants.STATUS_DELIVERED, ignoreCase = true)

        val orderStatus = when {
            isRejected -> OrderStatus.REJECTED
            isCancelled -> OrderStatus.CANCELLED
            isCompleted -> OrderStatus.COMPLETED
            isDelivered -> OrderStatus.DELIVERED
            rawStatus.equals(DelivioStatusConstants.STATUS_PREPARING, ignoreCase = true) -> OrderStatus.PREPARING
            rawStatus.equals(DelivioStatusConstants.STATUS_READY, ignoreCase = true) -> OrderStatus.READY
            rawStatus.equals(DelivioStatusConstants.STATUS_ON_ANOTHER_DELIVERY, ignoreCase = true) -> OrderStatus.ON_ANOTHER_DELIVERY
            DelivioStatusConstants.isRiderOnTheWay(doc.serviceType, rawStatus) ||
                    rawStatus.equals(DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY, ignoreCase = true) -> OrderStatus.ON_THE_WAY
            else -> OrderStatus.PLACED
        }

        val isCustomerCancelled = preferenceRepository?.isOrderCancelledByCustomer(doc.id) == true ||
                doc.cancelReason?.contains("by you", ignoreCase = true) == true ||
                (doc.cancelReason?.contains("customer", ignoreCase = true) == true && !isRejected)

        val timestamps = doc.statusHistory.associate { entry ->
            val s = when (entry.status) {
                DelivioStatusConstants.STATUS_PREPARING -> OrderStatus.PREPARING
                DelivioStatusConstants.STATUS_READY -> OrderStatus.READY
                DelivioStatusConstants.STATUS_ON_ANOTHER_DELIVERY -> OrderStatus.ON_ANOTHER_DELIVERY
                DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY -> OrderStatus.ON_THE_WAY
                DelivioStatusConstants.STATUS_COMPLETED -> OrderStatus.COMPLETED
                DelivioStatusConstants.STATUS_DELIVERED -> OrderStatus.DELIVERED
                else -> OrderStatus.PLACED
            }
            s to entry.at
        }

        val isParcelService = serviceType == ServiceType.PARCEL
        val isPabiliService = serviceType == ServiceType.PABILI
        val hideMerchantIdentity = isPabiliService || isParcelService

        val rider = if (!doc.riderId.isNullOrBlank()) {
            val rv = doc.riderVehicle
            val vehicleLabel = if (isParcelService) {
                com.example.util.ParcelFeeCalculator.formatParcelVehicleDisplay(doc.vehicleType)
            } else {
                val rvBrand = rv?.brand?.trim().orEmpty()
                val rvModel = rv?.model?.trim().orEmpty()
                val driverModelOrBrand = when {
                    rvBrand.isNotEmpty() && rvModel.isNotEmpty() ->
                        if (rvModel.contains(rvBrand, ignoreCase = true)) rvModel else "$rvBrand $rvModel"
                    rvModel.isNotEmpty() -> rvModel
                    rvBrand.isNotEmpty() -> rvBrand
                    else -> ""
                }
                driverModelOrBrand.ifEmpty {
                    val rawType = doc.vehicleType?.takeIf { it.isNotBlank() } ?: rv?.vehicleType
                    com.example.ui.components.formatTaxiVehicleName(rawType).ifEmpty {
                        if (doc.serviceType.equals("taxi", ignoreCase = true)) "Taxi" else "Motorcycle"
                    }
                }
            }
            val plate = rv?.plate?.trim()?.ifEmpty { "N/A" } ?: "N/A"
            Rider(
                id = doc.riderId,
                name = doc.riderName ?: "Assigned Rider",
                phone = doc.riderPhone ?: "+63 917 555 1234",
                vehicle = vehicleLabel,
                licensePlate = plate,
                rating = 4.9,
                avatarEmoji = if (doc.serviceType.lowercase() == "taxi") "🚕" else "🛵"
            )
        } else null

        val merchantFallback = firestoreService.defaultMerchants.find { it.id == doc.merchantId }

        val resolvedBranchName = doc.branchName?.takeIf { it.isNotBlank() } ?: merchantFallback?.branchName ?: ""
        val resolvedBranchCode = doc.branchCode?.takeIf { it.isNotBlank() } ?: merchantFallback?.branchCode ?: ""
        val resolvedChainId = doc.chainId?.takeIf { it.isNotBlank() } ?: merchantFallback?.chainId ?: ""

        val prepMinutes = merchantFallback?.prepTimeMinutes ?: 15
        val orderDistanceKm = doc.fees.distanceKm.takeIf { it > 0.0 }
            ?: run {
                val pLat = if (doc.pickup.lat != 0.0) doc.pickup.lat else (merchantFallback?.lat ?: 0.0)
                val pLng = if (doc.pickup.lng != 0.0) doc.pickup.lng else (merchantFallback?.lng ?: 0.0)
                val dLat = doc.dropoff?.lat ?: 0.0
                val dLng = doc.dropoff?.lng ?: 0.0
                if ((pLat != 0.0 || pLng != 0.0) && (dLat != 0.0 || dLng != 0.0)) {
                    com.example.util.BranchGroupingUtil.calculateDistanceKm(pLat, pLng, dLat, dLng)
                } else 0.0
            }
        val estimatedOrderEta = com.example.util.EtaCalculator.estimateMinutes(prepMinutes, orderDistanceKm)

        val restaurant = Restaurant(
            id = doc.merchantId ?: "service_partner",
            name = when {
                isPabiliService -> "Pabili Service"
                isParcelService -> "Parcel Service"
                else -> doc.merchantName ?: doc.pickup.label.ifEmpty { if (serviceType == ServiceType.TAXI) "Delivio Taxi" else "Delivio Service" }
            },
            category = serviceType.title,
            rating = if (hideMerchantIdentity) 0.0 else (merchantFallback?.averageRating ?: 0.0),
            reviewCount = if (hideMerchantIdentity) 0 else (merchantFallback?.ratingCount ?: 0),
            ratingSum = if (hideMerchantIdentity) 0.0 else (merchantFallback?.ratingSum ?: 0.0),
            ratingCount = if (hideMerchantIdentity) 0 else (merchantFallback?.ratingCount ?: 0),
            etaMinutes = estimatedOrderEta,
            deliveryFee = doc.fees.deliveryFee,
            address = if (hideMerchantIdentity) "" else doc.pickup.address,
            phone = if (hideMerchantIdentity) "" else doc.pickup.contactPhone,
            logoUrl = if (hideMerchantIdentity) "" else (merchantFallback?.logoUrl ?: ""),
            imageUrl = if (hideMerchantIdentity) "" else (merchantFallback?.imageUrl ?: ""),
            bannerUrl = if (hideMerchantIdentity) "" else (merchantFallback?.bannerUrl ?: ""),
            isConvenience = serviceType == ServiceType.CONVENIENCE,
            isOpen = merchantFallback?.isOpen ?: true,
            status = merchantFallback?.status ?: "active",
            isPaused = merchantFallback?.isPaused ?: false,
            pauseUntilTime = merchantFallback?.pauseUntilTime ?: 0L,
            closedUntil = merchantFallback?.closedUntil,
            openUntil = merchantFallback?.openUntil,
            autoSchedule = merchantFallback?.autoSchedule ?: false,
            hours = merchantFallback?.hours,
            holidays = merchantFallback?.holidays ?: emptyList(),
            lat = if (doc.pickup.lat != 0.0) doc.pickup.lat else (merchantFallback?.lat ?: 9.7420),
            lng = if (doc.pickup.lng != 0.0) doc.pickup.lng else (merchantFallback?.lng ?: 118.7400),
            prepTimeMinutes = prepMinutes,
            ownerUid = merchantFallback?.ownerUid ?: "",
            chainId = resolvedChainId,
            chainName = merchantFallback?.chainName ?: "",
            isBranch = merchantFallback?.isBranch ?: resolvedBranchName.isNotBlank(),
            branchName = resolvedBranchName,
            branchCode = resolvedBranchCode,
            branchSetupDone = merchantFallback?.branchSetupDone ?: false
        )

        val deliveryAddress = doc.dropoff?.let { dropoff ->
            Address(
                id = "dropoff",
                title = dropoff.label.ifEmpty { "Drop-off Location" },
                fullAddress = dropoff.address,
                details = dropoff.details,
                lat = if (dropoff.lat != 0.0) dropoff.lat else 9.7420,
                lng = if (dropoff.lng != 0.0) dropoff.lng else 118.7400
            )
        }

        val pickupAddress = Address(
            id = "pickup",
            title = doc.pickup.label.ifEmpty { "Pick-up Location" },
            fullAddress = doc.pickup.address,
            details = doc.pickup.details,
            lat = if (doc.pickup.lat != 0.0) doc.pickup.lat else 9.7392,
            lng = if (doc.pickup.lng != 0.0) doc.pickup.lng else 118.7353
        )

        val parcelDetails = if (doc.parcel != null) {
            ParcelDetails(
                senderName = doc.pickup.contactName.ifBlank { doc.pickup.name },
                senderPhone = doc.pickup.contactPhone.ifBlank { doc.pickup.phone.orEmpty() },
                senderAddress = doc.pickup.address,
                pickupLat = doc.pickup.lat.toFloat(),
                pickupLng = doc.pickup.lng.toFloat(),
                recipientName = doc.dropoff?.contactName?.ifBlank { doc.dropoff.name } ?: "",
                recipientPhone = doc.dropoff?.contactPhone?.ifBlank { doc.dropoff.phone.orEmpty() } ?: "",
                recipientAddress = doc.dropoff?.address ?: "",
                dropoffLat = doc.dropoff?.lat?.toFloat() ?: 0.75f,
                dropoffLng = doc.dropoff?.lng?.toFloat() ?: 0.70f,
                packageSize = doc.parcel.size,
                weightKg = doc.parcel.weightKg,
                itemDescription = doc.parcel.description,
                isFragile = doc.parcel.fragile,
                vehicleType = doc.vehicleType?.takeIf { it.isNotBlank() } ?: "motorcycle"
            )
        } else null

        val pabiliDetails = if (doc.pabili != null) {
            PabiliDetails(
                storeName = doc.pabili.vendorName.ifBlank { doc.pickup.name },
                storeAddress = doc.pickup.address,
                storeLat = doc.pickup.lat.toFloat(),
                storeLng = doc.pickup.lng.toFloat(),
                items = doc.pabili.items.map { PabiliItem(it.name, it.quantity, it.estimatedPrice) },
                recipientName = doc.dropoff?.contactName?.ifBlank { doc.dropoff.name } ?: "",
                recipientPhone = doc.dropoff?.contactPhone?.ifBlank { doc.dropoff.phone.orEmpty() } ?: "",
                deliveryAddress = doc.dropoff?.address ?: "",
                deliveryLat = doc.dropoff?.lat?.toFloat() ?: 0.75f,
                deliveryLng = doc.dropoff?.lng?.toFloat() ?: 0.75f,
                riderNotes = doc.dropoff?.note?.ifBlank { doc.note } ?: doc.note
            )
        } else null

        return Order(
            id = doc.id,
            restaurant = restaurant,
            items = doc.items.map { itItem ->
                val matchingItem = merchantFallback?.items?.find {
                    (itItem.itemId.isNotBlank() && it.id == itItem.itemId) ||
                    it.name.equals(itItem.name, ignoreCase = true)
                }
                val basePriceVal = if (itItem.basePrice > 0.0) itItem.basePrice else (itItem.unitPrice - itItem.selections.sumOf { it.price }).coerceAtLeast(0.0)
                val regularBasePriceVal = (itItem.regularUnitPrice - itItem.selections.sumOf { it.price }).coerceAtLeast(basePriceVal)
                CartItem(
                    id = "${itItem.itemId.ifBlank { itItem.name }}:${itItem.variantKey ?: ""}",
                    menuItem = MenuItem(
                        id = itItem.itemId.ifBlank { itItem.name },
                        restaurantId = doc.merchantId ?: "",
                        name = itItem.name,
                        description = itItem.notes,
                        price = regularBasePriceVal,
                        category = "Item",
                        emoji = "🍽️",
                        imageUrl = itItem.variantImageUrl?.takeIf { u -> u.isNotBlank() } ?: (matchingItem?.imageUrl ?: ""),
                        hasSets = itItem.selections.isNotEmpty() || (matchingItem?.hasSets == true),
                        sets = matchingItem?.sets ?: emptyList(),
                        variationsEnabled = !itItem.variantKey.isNullOrBlank() || (matchingItem?.variationsEnabled == true),
                        variations = matchingItem?.variations ?: emptyList(),
                        variants = matchingItem?.variants ?: emptyList(),
                        priceMax = matchingItem?.priceMax ?: 0.0,
                        stockCount = matchingItem?.stockCount ?: 0
                    ),
                    restaurantId = doc.merchantId ?: "",
                    restaurantName = restaurant.displayName,
                    quantity = itItem.qty,
                    specialInstructions = itItem.notes,
                    selections = itItem.selections,
                    basePrice = basePriceVal,
                    regularBasePrice = regularBasePriceVal,
                    variantKey = itItem.variantKey,
                    variantLabel = itItem.variantLabel,
                    variantImageUrl = itItem.variantImageUrl
                )
            },
            status = orderStatus,
            createdAt = doc.createdAt,
            statusTimestamps = timestamps,
            subtotal = doc.fees.subtotal,
            deliveryFee = doc.fees.deliveryFee,
            serviceFee = doc.fees.serviceFee,
            discount = doc.fees.discount,
            tip = doc.fees.tip,
            total = doc.fees.total,
            deliveryAddress = deliveryAddress,
            rider = rider,
            serviceType = serviceType,
            parcelDetails = parcelDetails,
            pabiliDetails = pabiliDetails,
            pickupAddress = pickupAddress,
            finalReceiptTotal = doc.pabili?.finalReceiptTotal,
            riderLat = doc.riderLocation?.lat,
            riderLng = doc.riderLocation?.lng,
            rawStatus = rawStatus,
            cancelReason = doc.cancelReason,
            rejectReason = doc.rejectReason,
            cancelledByCustomer = isCustomerCancelled,
            paymentMethod = doc.paymentMethod,
            paymentProvider = doc.paymentProvider,
            paymentStatus = doc.paymentStatus,
            cashToCollect = doc.cashToCollect,
            cashCollectAt = doc.cashCollectAt,
            walletRefunded = doc.walletRefunded,
            fulfillment = doc.fulfillment,
            scheduledFor = doc.scheduledFor,
            stops = doc.stops,
            vehicleType = doc.vehicleType,
            riderVehicle = doc.riderVehicle,
            note = doc.note,
            fees = doc.fees,
            pricing = doc.pricing,
            editedAt = doc.editedAt,
            originalItems = doc.originalItems,
            editNote = doc.editNote,
            walletAdjustDue = doc.walletAdjustDue,
            walletAdjustApplied = doc.walletAdjustApplied,
            lateTip = doc.lateTip,
            orderNumber = if (doc.orderNumber.isNotBlank()) doc.orderNumber else doc.id.takeLast(6).uppercase(),
            customerId = doc.customerId,
            customerName = doc.customerName,
            merchantId = doc.merchantId,
            merchantName = doc.merchantName ?: restaurant.name,
            branchName = doc.branchName?.takeIf { it.isNotBlank() } ?: restaurant.branchName.ifBlank { null },
            branchCode = doc.branchCode?.takeIf { it.isNotBlank() } ?: restaurant.branchCode.ifBlank { null },
            chainId = doc.chainId?.takeIf { it.isNotBlank() } ?: restaurant.chainId.ifBlank { null },
            riderId = doc.riderId,
            riderName = doc.riderName ?: rider?.name,
            deliveryOption = doc.deliveryOption,
            dispatch = doc.dispatch,
            splitOut = doc.splitOut,
            isSplitChild = doc.isSplitChild,
            promoCode = doc.promoCode,
            arrivedAtDropoffAt = doc.arrivedAtDropoffAt,
            budgetLimit = doc.budgetLimit ?: doc.pabili?.budget,
            paidOnMerchant = doc.paidOnMerchant
        )
    }

    suspend fun getWalletHistory(customerId: String): List<com.example.data.model.WalletHistoryItem> {
        val items = mutableListOf<com.example.data.model.WalletHistoryItem>()
        // 1. walletTransactions where customerId == uid (cash_in, adjustment)
        val txs = firestoreService.getWalletTransactions(customerId)
        for (tx in txs) {
            val typeTitle = if (tx.type.equals("cash_in", ignoreCase = true)) "Cash In / Top-up" else "Wallet Adjustment"
            items.add(
                com.example.data.model.WalletHistoryItem(
                    id = tx.id,
                    type = typeTitle,
                    signedAmount = tx.amount,
                    timestamp = tx.createdAt,
                    referenceNumber = "TX-${tx.id.takeLast(6).uppercase()}",
                    description = tx.description.ifEmpty { "Delivio credit" }
                )
            )
        }

        // 2. orders where customerId == uid and paymentMethod == "wallet" (payment, -fees.total)
        // 3. and those orders with walletRefunded == true (refund, +fees.total)
        val orders = _orderHistory.value
        for (order in orders) {
            if (order.paymentMethod.equals("wallet", ignoreCase = true)) {
                items.add(
                    com.example.data.model.WalletHistoryItem(
                        id = "pay-${order.id}",
                        type = "Order Payment",
                        signedAmount = -order.total,
                        timestamp = order.createdAt,
                        referenceNumber = "ORD-${order.id.takeLast(6).uppercase()}",
                        description = order.restaurant.name
                    )
                )

                if (order.walletRefunded) {
                    val refundTime = order.statusTimestamps[OrderStatus.CANCELLED]
                        ?: order.statusTimestamps[OrderStatus.REJECTED]
                        ?: (order.createdAt + 1000)
                    items.add(
                        com.example.data.model.WalletHistoryItem(
                            id = "ref-${order.id}",
                            type = "Wallet Refund",
                            signedAmount = +order.total,
                            timestamp = refundTime,
                            referenceNumber = "REF-${order.id.takeLast(6).uppercase()}",
                            description = "Refund for ${order.restaurant.name}"
                        )
                    )
                }

                if (order.walletAdjustDue > 0.0) {
                    val editTime = order.editedAt ?: (order.createdAt + 1500)
                    items.add(
                        com.example.data.model.WalletHistoryItem(
                            id = "ref-edit-${order.id}",
                            type = "Refund (order updated)",
                            signedAmount = +order.walletAdjustDue,
                            timestamp = editTime,
                            referenceNumber = "REF-ED-${order.id.takeLast(6).uppercase()}",
                            description = "Refund for updated order (${order.restaurant.name})"
                        )
                    )
                }
            }
        }

        return items.sortedWith(
            compareByDescending<com.example.data.model.WalletHistoryItem> { it.timestamp != null }
                .thenByDescending { it.timestamp ?: Long.MIN_VALUE }
        )
    }
}
