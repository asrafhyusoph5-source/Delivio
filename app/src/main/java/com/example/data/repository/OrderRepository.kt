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
import com.example.data.model.Restaurant
import com.example.data.model.Rider
import com.example.data.model.RiderLocation
import com.example.data.model.ServiceType
import com.example.data.model.StatusHistoryEntry
import com.example.util.DelivioStatusConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TrackerDisplayState {
    EXPANDED,
    COLLAPSED,
    BUBBLE
}

class OrderRepository(
    val firestoreService: FirestoreService
) {
    private val tag = "DelivioOrderRepo"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _activeOrder = MutableStateFlow<Order?>(null)
    val activeOrder: StateFlow<Order?> = _activeOrder.asStateFlow()

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

    private var ordersListenerJob: Job? = null

    fun setCustomerId(customerId: String) {
        ordersListenerJob?.cancel()
        if (customerId.isEmpty()) return

        ordersListenerJob = scope.launch {
            firestoreService.getCustomerOrdersFlow(customerId).collect { documents ->
                val allOrders = documents.map { mapDocToOrder(it) }
                // Active order is the first non-terminal order
                val active = allOrders.firstOrNull { !DelivioStatusConstants.isTerminal(it.status.title) }
                _activeOrder.value = active
                _orderHistory.value = allOrders
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

    fun dismissActiveOrder() {
        _activeOrder.value = null
        _trackerState.value = TrackerDisplayState.COLLAPSED
    }

    // Cancel order in Firestore (only permitted if status is "Placed Order")
    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> {
        val result = firestoreService.cancelOrder(orderId, reason)
        if (result.isSuccess) {
            _activeOrder.value = null
            _statusNotificationEvent.tryEmit("Order Cancelled")
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
        deliveryAddress: Address,
        deliveryLat: Double,
        deliveryLng: Double,
        deliveryFee: Double,
        serviceFee: Double,
        discount: Double,
        subtotal: Double,
        total: Double,
        cityId: String,
        deliveryNotes: String
    ): Result<String> {
        val serviceTypeStr = if (restaurant.isConvenience) DelivioStatusConstants.SERVICE_CONVENIENCE else DelivioStatusConstants.SERVICE_FOOD

        val orderDoc = OrderDocument(
            serviceType = serviceTypeStr,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = restaurant.id,
            merchantName = restaurant.name,
            riderId = null,
            riderName = null,
            riderPhone = null,
            riderLocation = null,
            status = DelivioStatusConstants.STATUS_PLACED_ORDER,
            statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, System.currentTimeMillis())),
            items = items.map { OrderItem(it.menuItem.name, it.quantity, it.menuItem.price, it.specialInstructions) },
            pickup = LocationPoint(
                label = restaurant.name,
                address = restaurant.address,
                lat = 9.7420,
                lng = 118.7400,
                contactName = restaurant.name,
                contactPhone = restaurant.phone
            ),
            dropoff = LocationPoint(
                label = deliveryAddress.title,
                address = deliveryAddress.fullAddress,
                lat = deliveryLat,
                lng = deliveryLng,
                contactName = customerName,
                contactPhone = customerPhone
            ),
            fees = OrderFees(subtotal, deliveryFee, serviceFee, discount, total),
            paymentMethod = "cod",
            cityId = cityId
        )

        val result = firestoreService.createOrder(orderDoc)
        result.onSuccess { newId ->
            val uiOrder = mapDocToOrder(orderDoc.copy(id = newId))
            _activeOrder.value = uiOrder
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Order placed! Merchant is notified.")
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
        cityId: String = "puerto-princesa"
    ): Result<String> {
        val total = deliveryFee + serviceFee

        val orderDoc = OrderDocument(
            serviceType = DelivioStatusConstants.SERVICE_PARCEL,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = null,
            merchantName = null,
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
                contactPhone = parcelDetails.senderPhone
            ),
            dropoff = LocationPoint(
                label = "Recipient: ${parcelDetails.recipientName}",
                address = parcelDetails.recipientAddress,
                lat = parcelDetails.dropoffLat.toDouble(),
                lng = parcelDetails.dropoffLng.toDouble(),
                contactName = parcelDetails.recipientName,
                contactPhone = parcelDetails.recipientPhone
            ),
            parcel = ParcelInfo(
                size = parcelDetails.packageSize,
                weightKg = parcelDetails.weightKg,
                description = parcelDetails.itemDescription,
                fragile = parcelDetails.isFragile,
                feePayer = "Sender"
            ),
            fees = OrderFees(subtotal = 0.0, deliveryFee = deliveryFee, serviceFee = serviceFee, discount = 0.0, total = total),
            paymentMethod = "cod",
            cityId = cityId
        )

        val result = firestoreService.createOrder(orderDoc)
        result.onSuccess { newId ->
            val uiOrder = mapDocToOrder(orderDoc.copy(id = newId))
            _activeOrder.value = uiOrder
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Parcel order placed! Looking for nearby rider.")
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
        budget: Double = 1000.0
    ): Result<String> {
        val total = itemTotal + deliveryFee + serviceFee

        val storePickupPoint = LocationPoint(
            label = pabiliDetails.storeName,
            address = pabiliDetails.storeAddress,
            lat = pabiliDetails.storeLat.toDouble(),
            lng = pabiliDetails.storeLng.toDouble(),
            contactName = pabiliDetails.storeName,
            contactPhone = ""
        )

        val orderDoc = OrderDocument(
            serviceType = DelivioStatusConstants.SERVICE_PABILI,
            customerId = customerId,
            customerName = customerName,
            customerPhone = customerPhone,
            merchantId = null,
            merchantName = pabiliDetails.storeName,
            riderId = null,
            status = DelivioStatusConstants.STATUS_PLACED_ORDER,
            statusHistory = listOf(StatusHistoryEntry(DelivioStatusConstants.STATUS_PLACED_ORDER, System.currentTimeMillis())),
            items = emptyList(),
            pickup = storePickupPoint,
            dropoff = LocationPoint(
                label = "Recipient: ${pabiliDetails.recipientName}",
                address = pabiliDetails.deliveryAddress,
                lat = pabiliDetails.deliveryLat.toDouble(),
                lng = pabiliDetails.deliveryLng.toDouble(),
                contactName = pabiliDetails.recipientName,
                contactPhone = pabiliDetails.recipientPhone
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
                deliveryFee = deliveryFee,
                serviceFee = serviceFee,
                discount = 0.0,
                total = total,
                estimatedItemsTotal = itemTotal,
                distanceKm = distanceKm,
                distanceMethod = distanceMethod
            ),
            paymentMethod = "cod",
            cityId = cityId
        )

        val result = firestoreService.createOrder(orderDoc)
        result.onSuccess { newId ->
            val uiOrder = mapDocToOrder(orderDoc.copy(id = newId))
            _activeOrder.value = uiOrder
            _trackerState.value = TrackerDisplayState.COLLAPSED
            _statusNotificationEvent.tryEmit("Pabili errand booked! Rider will buy your items.")
        }
        return result
    }

    private fun mapDocToOrder(doc: OrderDocument): Order {
        val serviceType = when (doc.serviceType.lowercase()) {
            "parcel" -> ServiceType.PARCEL
            "pabili" -> ServiceType.PABILI
            "convenience" -> ServiceType.CONVENIENCE
            else -> ServiceType.FOOD
        }

        val orderStatus = when (doc.status) {
            DelivioStatusConstants.STATUS_PREPARING -> OrderStatus.PREPARING
            DelivioStatusConstants.STATUS_READY -> OrderStatus.READY
            DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY,
            DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY_TO_SENDER,
            DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY_TO_VENDOR,
            DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT -> OrderStatus.ON_THE_WAY
            DelivioStatusConstants.STATUS_DELIVERED -> OrderStatus.DELIVERED
            else -> OrderStatus.PLACED
        }

        val timestamps = doc.statusHistory.associate { entry ->
            val s = when (entry.status) {
                DelivioStatusConstants.STATUS_PREPARING -> OrderStatus.PREPARING
                DelivioStatusConstants.STATUS_READY -> OrderStatus.READY
                DelivioStatusConstants.STATUS_RIDER_ON_THE_WAY -> OrderStatus.ON_THE_WAY
                DelivioStatusConstants.STATUS_DELIVERED -> OrderStatus.DELIVERED
                else -> OrderStatus.PLACED
            }
            s to entry.at
        }

        val rider = if (doc.riderId != null) {
            Rider(
                id = doc.riderId,
                name = doc.riderName ?: "Assigned Rider",
                phone = doc.riderPhone ?: "+63 917 555 1234",
                vehicle = "Motorcycle",
                licensePlate = "PAL-8891",
                rating = 4.9
            )
        } else null

        val restaurant = Restaurant(
            id = doc.merchantId ?: "service_partner",
            name = doc.merchantName ?: doc.pickup.label.ifEmpty { "Delivio Service" },
            category = serviceType.title,
            rating = 4.8,
            reviewCount = 120,
            etaMinutes = 25,
            deliveryFee = doc.fees.deliveryFee,
            address = doc.pickup.address,
            phone = doc.pickup.contactPhone,
            bannerEmoji = serviceType.emoji,
            isConvenience = serviceType == ServiceType.CONVENIENCE
        )

        val deliveryAddress = Address(
            id = "dropoff",
            title = doc.dropoff.label,
            fullAddress = doc.dropoff.address,
            lat = if (doc.dropoff.lat != 0.0) doc.dropoff.lat else 9.7420,
            lng = if (doc.dropoff.lng != 0.0) doc.dropoff.lng else 118.7400
        )

        val pickupAddress = Address(
            id = "pickup",
            title = doc.pickup.label,
            fullAddress = doc.pickup.address,
            lat = if (doc.pickup.lat != 0.0) doc.pickup.lat else 9.7392,
            lng = if (doc.pickup.lng != 0.0) doc.pickup.lng else 118.7353
        )

        val parcelDetails = if (doc.parcel != null) {
            ParcelDetails(
                senderName = doc.pickup.contactName,
                senderPhone = doc.pickup.contactPhone,
                senderAddress = doc.pickup.address,
                recipientName = doc.dropoff.contactName,
                recipientPhone = doc.dropoff.contactPhone,
                recipientAddress = doc.dropoff.address,
                packageSize = doc.parcel.size,
                weightKg = doc.parcel.weightKg,
                itemDescription = doc.parcel.description,
                isFragile = doc.parcel.fragile
            )
        } else null

        val pabiliDetails = if (doc.pabili != null) {
            PabiliDetails(
                storeName = doc.pabili.vendorName,
                storeAddress = doc.pickup.address,
                storeLat = doc.pickup.lat.toFloat(),
                storeLng = doc.pickup.lng.toFloat(),
                items = doc.pabili.items.map { PabiliItem(it.name, it.quantity, it.estimatedPrice) },
                recipientName = doc.dropoff.contactName,
                recipientPhone = doc.dropoff.contactPhone,
                deliveryAddress = doc.dropoff.address,
                deliveryLat = doc.dropoff.lat.toFloat(),
                deliveryLng = doc.dropoff.lng.toFloat()
            )
        } else null

        return Order(
            id = doc.id,
            restaurant = restaurant,
            items = doc.items.map {
                CartItem(
                    menuItem = MenuItem(id = it.name, restaurantId = doc.merchantId ?: "", name = it.name, description = it.notes, price = it.price, category = "Item", emoji = "🍽️"),
                    restaurantId = doc.merchantId ?: "",
                    restaurantName = doc.merchantName ?: "",
                    quantity = it.qty,
                    specialInstructions = it.notes
                )
            },
            status = orderStatus,
            createdAt = doc.createdAt,
            statusTimestamps = timestamps,
            subtotal = doc.fees.subtotal,
            deliveryFee = doc.fees.deliveryFee,
            serviceFee = doc.fees.serviceFee,
            discount = doc.fees.discount,
            total = doc.fees.total,
            deliveryAddress = deliveryAddress,
            rider = rider,
            serviceType = serviceType,
            parcelDetails = parcelDetails,
            pabiliDetails = pabiliDetails,
            pickupAddress = pickupAddress,
            finalReceiptTotal = doc.pabili?.finalReceiptTotal,
            riderLat = doc.riderLocation?.lat,
            riderLng = doc.riderLocation?.lng
        )
    }
}
