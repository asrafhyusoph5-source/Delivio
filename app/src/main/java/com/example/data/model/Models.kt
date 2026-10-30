package com.example.data.model

enum class ServiceType(val title: String, val emoji: String = "") {
    ALL("All", ""),
    FOOD("Food", ""),
    CONVENIENCE("Convenience", ""),
    PARCEL("Parcel", ""),
    PABILI("Pabili", ""),
    TAXI("Taxi", "")
}

data class ParcelDetails(
    val senderName: String,
    val senderPhone: String,
    val senderAddress: String,
    val pickupLat: Float = 0.25f,
    val pickupLng: Float = 0.30f,
    val recipientName: String,
    val recipientPhone: String,
    val recipientAddress: String,
    val dropoffLat: Float = 0.75f,
    val dropoffLng: Float = 0.70f,
    val packageSize: String, // "Small / Pouch", "Medium / Box", "Large / Cargo"
    val weightKg: Double,
    val itemDescription: String,
    val isFragile: Boolean = false,
    val vehicleType: String = "Motorcycle"
)

data class PabiliItem(
    val name: String,
    val quantity: Int = 1,
    val estimatedPrice: Double = 0.0
)

data class PabiliDetails(
    val storeName: String,
    val storeAddress: String,
    val storeLat: Float = 0.35f,
    val storeLng: Float = 0.35f,
    val items: List<PabiliItem>,
    val recipientName: String,
    val recipientPhone: String,
    val deliveryAddress: String,
    val deliveryLat: Float = 0.80f,
    val deliveryLng: Float = 0.75f,
    val riderNotes: String = ""
)

data class Rider(
    val id: String,
    val name: String,
    val phone: String,
    val vehicle: String,
    val licensePlate: String,
    val rating: Double,
    val totalDeliveries: Int = 1240,
    val avatarEmoji: String = ""
)

data class Address(
    val id: String,
    val title: String,
    val fullAddress: String,
    val details: String = "",
    val isDefault: Boolean = false,
    val lat: Double = 9.7392,
    val lng: Double = 118.7353
)

data class MenuItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val price: Double,
    val category: String,
    val emoji: String,
    val imageUrl: String = "",
    val isPopular: Boolean = false,
    val discountBadge: String? = null
)

data class Restaurant(
    val id: String,
    val name: String,
    val category: String,
    val type: String = "food",
    val cuisineTags: List<String> = emptyList(),
    val rating: Double,
    val reviewCount: Int,
    val etaMinutes: Int,
    val deliveryFee: Double,
    val minOrder: Double = 10.0,
    val priceTier: String = "₱₱",
    val address: String,
    val phone: String,
    val bannerEmoji: String,
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val imageUrl: String = "",
    val badge: String? = null,
    val isFastDelivery: Boolean = false,
    val isDeal: Boolean = false,
    val dealDescription: String? = null,
    val isConvenience: Boolean = false,
    val isOpen: Boolean = true,
    val nextOpenTime: String = "Opens 8:00 AM",
    val hours: Map<String, Any>? = null,
    val status: String = "active",
    val isPaused: Boolean = false,
    val pauseUntilTime: Long = 0L,
    val autoSchedule: Boolean = false,
    val lat: Double = 9.7420,
    val lng: Double = 118.7400
)

data class CartItem(
    val menuItem: MenuItem,
    val restaurantId: String,
    val restaurantName: String,
    val quantity: Int,
    val specialInstructions: String = ""
) {
    val totalPrice: Double
        get() = menuItem.price * quantity
}

data class Order(
    val id: String,
    val restaurant: Restaurant,
    val items: List<CartItem>,
    val status: OrderStatus,
    val createdAt: Long,
    val statusTimestamps: Map<OrderStatus, Long> = emptyMap(),
    val subtotal: Double,
    val deliveryFee: Double,
    val serviceFee: Double,
    val discount: Double,
    val tip: Double = 0.0,
    val total: Double,
    val deliveryAddress: Address? = null,
    val rider: Rider?,
    val deliveryNotes: String = "",
    val serviceType: ServiceType = ServiceType.FOOD,
    val parcelDetails: ParcelDetails? = null,
    val pabiliDetails: PabiliDetails? = null,
    val pickupAddress: Address? = null,
    val finalReceiptTotal: Double? = null,
    val riderLat: Double? = null,
    val riderLng: Double? = null,
    val rawStatus: String = "Placed Order",
    val cancelReason: String? = null,
    val rejectReason: String? = null,
    val cancelledByCustomer: Boolean = false,
    val paymentMethod: String = "cod",
    val paymentProvider: String? = null,
    val paymentStatus: String = "unpaid",
    val cashToCollect: Double = 0.0,
    val cashCollectAt: String? = null,
    val walletRefunded: Boolean = false,
    val fulfillment: String = "delivery",
    val scheduledFor: Long? = null,
    val stops: List<TaxiStop> = emptyList(),
    val vehicleType: String? = null,
    val riderVehicle: RiderVehicleInfo? = null,
    val note: String = "",
    val fees: OrderFees = OrderFees(),
    val editedAt: Long? = null,
    val originalItems: List<OrderItem> = emptyList(),
    val editNote: String? = null,
    val walletAdjustDue: Double = 0.0,
    val walletAdjustApplied: Double = 0.0
)

data class User(
    val uid: String = "",
    val email: String,
    val name: String,
    val phone: String,
    val role: String = "customer",
    val walletBalance: Double = 0.0,
    val secondaryPhone: String = "",
    val gender: String = "Prefer not to say",
    val birthday: String = "",
    val deliveryNotes: String = "",
    val memberTier: String = ""
)
