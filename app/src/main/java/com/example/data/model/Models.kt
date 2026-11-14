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
    val discountBadge: String? = null,
    val hasSets: Boolean = false,
    val sets: List<ItemSet> = emptyList(),
    val menuIds: List<String> = emptyList(),
    val itemSize: String = "",
    val availability: ItemAvailability? = null,
    val salePrice: Double? = null,
    val saleStartsAt: Long? = null,
    val saleEndsAt: Long? = null,
    val saleActive: Boolean = false,
    val soldCount: Int = 0,
    val variationsEnabled: Boolean = false,
    val variations: List<ItemVariation> = emptyList(),
    val variants: List<ItemVariant> = emptyList(),
    val priceMax: Double = 0.0,
    val stockCount: Int = 0
) {
    val fromPrice: Double
        get() = com.example.util.PriceDropUtil.effectiveFromPrice(this)

    val regularFromPrice: Double
        get() = com.example.util.PriceDropUtil.regularFromPrice(this)
}

data class Restaurant(
    val id: String,
    val name: String,
    val category: String,
    val type: String = "food",
    val cuisineTags: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val etaMinutes: Int,
    val deliveryFee: Double,
    val minOrder: Double = 10.0,
    val priceTier: String = "₱₱",
    val address: String,
    val phone: String,
    val logoUrl: String = "",
    val imageUrl: String = "",
    val badge: String? = null,
    val isFastDelivery: Boolean = false,
    val isDeal: Boolean = false,
    val dealDescription: String? = null,
    val isConvenience: Boolean = false,
    val isOpen: Boolean = true,
    val nextOpenTime: String = "Opens 8:00 AM",
    val hours: Map<String, Any>? = null,
    val holidays: List<HolidayEntry> = emptyList(),
    val status: String = "active",
    val isPaused: Boolean = false,
    val pauseUntilTime: Long = 0L,
    val closedUntil: Long? = null,
    val openUntil: Long? = null,
    val autoSchedule: Boolean = false,
    val lat: Double = 9.7420,
    val lng: Double = 118.7400,
    val cityId: String = "puerto-princesa",
    val specialRadius: MerchantSpecialRadius? = null,
    val prepTimeMinutes: Int = 15,
    val ownerUid: String = "",
    val chainId: String = "",
    val chainName: String = "",
    val isBranch: Boolean = false,
    val branchName: String = "",
    val branchCode: String = "",
    val branchSetupDone: Boolean = false,
    val deliveryRadiusKm: Double? = null
) {
    val averageRating: Double
        get() = com.example.util.RatingFormatUtil.calculateAverage(ratingSum, ratingCount)

    val displayName: String
        get() = com.example.util.BranchGroupingUtil.formatShopDisplayName(name, branchName)
}

data class CartItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val menuItem: MenuItem,
    val restaurantId: String,
    val restaurantName: String,
    val quantity: Int,
    val specialInstructions: String = "",
    val selections: List<SelectedItemChoice> = emptyList(),
    val basePrice: Double = com.example.util.PriceDropUtil.effectivePrice(menuItem),
    val regularBasePrice: Double = menuItem.price,
    val variantKey: String? = null,
    val variantLabel: String? = null,
    val variantImageUrl: String? = null
) {
    val unitPrice: Double
        get() = basePrice + selections.sumOf { it.price }

    val regularUnitPrice: Double
        get() = regularBasePrice + selections.sumOf { it.price }

    val totalPrice: Double
        get() = unitPrice * quantity

    val regularTotalPrice: Double
        get() = regularUnitPrice * quantity

    val hasPriceDrop: Boolean
        get() = regularBasePrice > basePrice + 0.001

    val displayImageUrl: String
        get() = variantImageUrl?.takeIf { it.isNotBlank() } ?: menuItem.imageUrl

    fun hasSameConfiguration(
        otherMenuItemId: String,
        otherSelections: List<SelectedItemChoice>,
        otherVariantKey: String? = null
    ): Boolean {
        if (menuItem.id != otherMenuItemId) return false
        if ((variantKey ?: "") != (otherVariantKey ?: "")) return false
        if (selections.size != otherSelections.size) return false
        val s1 = selections.map { "${it.setId}:${it.choiceId}" }.sorted()
        val s2 = otherSelections.map { "${it.setId}:${it.choiceId}" }.sorted()
        return s1 == s2
    }
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
    val walletAdjustApplied: Double = 0.0,
    val lateTip: LateTip? = null,
    val orderNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val merchantId: String? = null,
    val merchantName: String? = null,
    val branchName: String? = null,
    val branchCode: String? = null,
    val chainId: String? = null,
    val riderId: String? = null,
    val riderName: String? = null,
    val deliveryOption: String? = null,
    val dispatch: Map<String, Any?>? = null,
    val splitOut: Any? = null,
    val isSplitChild: Boolean = false,
    val promoCode: String? = null,
    val promo: Map<String, Any?>? = null
) {
    val displayOrderNumber: String
        get() = com.example.util.OrderNumberUtil.formatOrderNumber(orderNumber, id)

    val displayMerchantName: String
        get() = restaurant.displayName
}

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
    val memberTier: String = "",
    val notifPrefs: com.example.data.model.NotificationPreferences = com.example.data.model.NotificationPreferences()
)
