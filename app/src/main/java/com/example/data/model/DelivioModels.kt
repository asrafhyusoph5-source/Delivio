package com.example.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Delivio Customer Data Models
 * Aligned with Delivio 4-Product Firestore Schema
 */

data class DelivioUser(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "customer",
    val status: String = "active",
    val walletBalance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class DelivioAddress(
    val id: String = "",
    val label: String = "Home", // "Home", "Work", "Other"
    val address: String = "",
    val details: String = "",
    val lat: Double = 9.7392,
    val lng: Double = 118.7353,
    @get:PropertyName("isDefault")
    val isDefault: Boolean = false
)

data class City(
    val id: String = "",
    val name: String = "",
    val province: String = "",
    val status: String = "active",
    val lat: Double = 9.7392,
    val lng: Double = 118.7353,
    val radiusKm: Double = 25.0
)

data class RateConfig(
    val baseFare: Double = 39.0,
    val includedKm: Double = 2.0,
    val perKmRate: Double = 10.0,
    val minFee: Double = 39.0,
    val maxFee: Double = 250.0,
    val serviceFee: Double = 10.0,
    val surcharge: Double = 0.0
)

data class PlatformSettings(
    val services: Map<String, Boolean> = mapOf(
        "food" to true,
        "convenience" to true,
        "parcel" to true,
        "pabili" to true,
        "taxi" to true
    ),
    val taxiVehicles: Map<String, Boolean> = mapOf(
        "motorcycle" to true,
        "tricycle" to true,
        "car4" to true,
        "car8" to true
    )
)

data class RideRateConfig(
    val enabled: Boolean = true,
    val baseFare: Double = 50.0,
    val includedKm: Double = 2.0,
    val perKm: Double = 15.0,
    val minFare: Double = 50.0,
    val perStopFee: Double = 20.0,
    val serviceFee: Double = 10.0
)

data class SurchargesConfig(
    val peak: Double = 0.0,
    val night: Double = 0.0
)

data class DeliveryRates(
    val id: String = "",
    val cityId: String = "",
    val food: RateConfig = RateConfig(39.0, 2.0, 10.0, 39.0, 250.0, 10.0, 0.0),
    val convenience: RateConfig = RateConfig(45.0, 2.0, 12.0, 45.0, 280.0, 12.0, 0.0),
    val parcel: RateConfig = RateConfig(50.0, 3.0, 14.0, 50.0, 350.0, 15.0, 0.0),
    val pabili: RateConfig = RateConfig(55.0, 2.0, 15.0, 55.0, 400.0, 20.0, 0.0),
    val rideRates: Map<String, RideRateConfig> = mapOf(
        "motorcycle" to RideRateConfig(enabled = true, baseFare = 50.0, includedKm = 2.0, perKm = 15.0, minFare = 50.0, perStopFee = 20.0, serviceFee = 10.0),
        "tricycle" to RideRateConfig(enabled = true, baseFare = 40.0, includedKm = 2.0, perKm = 12.0, minFare = 40.0, perStopFee = 15.0, serviceFee = 8.0),
        "car4" to RideRateConfig(enabled = true, baseFare = 90.0, includedKm = 2.0, perKm = 22.0, minFare = 90.0, perStopFee = 30.0, serviceFee = 15.0),
        "car8" to RideRateConfig(enabled = true, baseFare = 140.0, includedKm = 2.0, perKm = 28.0, minFare = 140.0, perStopFee = 40.0, serviceFee = 20.0)
    ),
    val surcharges: SurchargesConfig = SurchargesConfig()
) {
    fun getRateFor(serviceType: String): RateConfig = when (serviceType.lowercase()) {
        "convenience" -> convenience
        "parcel" -> parcel
        "pabili" -> pabili
        else -> food
    }
}

data class BannerLink(
    val merchantId: String? = null,
    val category: String? = null
)

data class Banner(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val status: String = "active",
    val priority: Int = 1,
    val startAt: String = "",
    val endAt: String = "",
    val link: BannerLink = BannerLink()
)

data class MerchantCategory(
    val id: String = "",
    val name: String = "",
    val order: Int = 1
)

data class MerchantItem(
    val id: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val imageUrl: String = "",
    @get:PropertyName("isAvailable")
    val isAvailable: Boolean = true
)

data class Merchant(
    val id: String = "",
    val name: String = "",
    val type: String = "food", // "food", "convenience"
    val status: String = "active", // "active", "pending", "suspended"
    val cityId: String = "",
    @get:PropertyName("isOpen")
    val isOpen: Boolean = true,
    val rating: Double = 4.8,
    val deliveryTime: String = "20-30 min",
    val address: String = "",
    val lat: Double = 9.7420,
    val lng: Double = 118.7400,
    val cuisineTags: List<String> = emptyList(),
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val imageUrl: String = "",
    val categories: List<MerchantCategory> = emptyList(),
    val items: List<MerchantItem> = emptyList(),
    val hours: Map<String, Any>? = null
)

data class RiderLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class StatusHistoryEntry(
    val status: String = "",
    val at: Long = System.currentTimeMillis()
)

data class OrderItem(
    val name: String = "",
    val qty: Int = 1,
    val price: Double = 0.0,
    val notes: String = ""
)

data class LocationPoint(
    val label: String = "",
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val contactName: String = "",
    val contactPhone: String = "",
    val details: String = ""
)

data class TaxiStop(
    val number: Int = 3,
    val label: String = "Next Stop",
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val details: String = "",
    val completed: Boolean = false
)

data class RiderVehicleInfo(
    val model: String = "",
    val color: String = "",
    val plate: String = "",
    val photoUrl: String = "",
    val vehicleType: String = "motorcycle"
)

data class ParcelInfo(
    val size: String = "Medium / Box", // S, M, L, XL
    val weightKg: Double = 1.0,
    val description: String = "",
    val fragile: Boolean = false,
    val feePayer: String = "Sender", // "Sender" or "Recipient"
    val photoUrl: String? = null
)

data class PabiliItemInfo(
    val name: String = "",
    val quantity: Int = 1,
    val estimatedPrice: Double = 0.0
)

data class PabiliInfo(
    val vendorName: String = "",
    val items: List<PabiliItemInfo> = emptyList(),
    val budget: Double = 1000.0,
    val finalReceiptTotal: Double? = null,
    val pickup: LocationPoint? = null
)

data class OrderFees(
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val serviceFee: Double = 0.0,
    val discount: Double = 0.0,
    val tip: Double = 0.0,
    val total: Double = 0.0,
    val estimatedItemsTotal: Double = 0.0,
    val distanceKm: Double = 0.0,
    val distanceMethod: String = "osrm"
)

data class OrderDocument(
    val id: String = "",
    val serviceType: String = "food", // "food", "convenience", "parcel", "pabili", "taxi"
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val merchantId: String? = null,
    val merchantName: String? = null,
    val riderId: String? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderLocation: RiderLocation? = null,
    val riderVehicle: RiderVehicleInfo? = null,
    val vehicleType: String? = null,
    val status: String = "Placed Order",
    val statusHistory: List<StatusHistoryEntry> = emptyList(),
    val items: List<OrderItem> = emptyList(),
    val pickup: LocationPoint = LocationPoint(),
    val dropoff: LocationPoint? = LocationPoint(),
    val stops: List<TaxiStop> = emptyList(),
    val parcel: ParcelInfo? = null,
    val pabili: PabiliInfo? = null,
    val fees: OrderFees = OrderFees(),
    val paymentMethod: String = "cod", // "cod" | "wallet" | "online"
    val paymentProvider: String? = null, // "wallet" | "gcash" | "maya" | "bank" | "card" | null
    val paymentStatus: String = "unpaid", // "unpaid" | "paid"
    val cashToCollect: Double = 0.0,
    val cashCollectAt: String? = null, // "sender" | "recipient" for parcel COD
    val walletRefunded: Boolean = false,
    val cityId: String = "",
    val cancelReason: String? = null,
    val rejectReason: String? = null,
    val fulfillment: String = "delivery", // "pickup" | "delivery" | "scheduled"
    val scheduledFor: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class WalletTransaction(
    val id: String = "",
    val customerId: String = "",
    val type: String = "cash_in", // "cash_in", "adjustment"
    val amount: Double = 0.0,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class WalletHistoryItem(
    val id: String,
    val type: String, // "Top-up / Cash In", "Adjustment", "Order Payment", "Wallet Refund"
    val signedAmount: Double,
    val timestamp: Long,
    val referenceNumber: String,
    val description: String = ""
)

data class WalletDirectoryEntry(
    val uid: String = "",
    val displayName: String = "",
    val hint: String = ""
)

data class WalletTransfer(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val toName: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val method: String = "send", // "send" | "qr"
    val createdAt: Long = System.currentTimeMillis()
)

enum class WalletActivityCategory(val title: String) {
    ALL("All"),
    SENT("Sent"),
    RECEIVED("Received"),
    PAYMENTS("Payments"),
    TOP_UPS("Top-ups"),
    REFUNDS("Refunds")
}

data class WalletActivityItem(
    val id: String,
    val category: WalletActivityCategory,
    val title: String,
    val timestamp: Long,
    val signedAmount: Double,
    val referenceCode: String = "",
    val note: String = ""
)

data class SendMoneyReceipt(
    val referenceCode: String,
    val toName: String,
    val amount: Double,
    val newBalance: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

