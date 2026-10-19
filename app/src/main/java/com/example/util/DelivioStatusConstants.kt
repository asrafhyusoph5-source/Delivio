package com.example.util

/**
 * Universal Delivio Platform Status Constants
 * Shared specification across Delivio Customer, Delivio Partner, Delivio Rider, and Delivio Admin.
 */
object DelivioStatusConstants {
    // Service Types
    const val SERVICE_FOOD = "food"
    const val SERVICE_CONVENIENCE = "convenience"
    const val SERVICE_PARCEL = "parcel"
    const val SERVICE_PABILI = "pabili"

    // Common Statuses
    const val STATUS_PLACED_ORDER = "Placed Order"
    const val STATUS_CANCELLED = "Cancelled"
    const val STATUS_DELIVERED = "Delivered"

    // Food & Convenience Specific
    const val STATUS_PREPARING = "Preparing"
    const val STATUS_READY = "Ready"
    const val STATUS_RIDER_ON_THE_WAY = "Rider is on the way"
    const val STATUS_REJECTED = "Rejected"

    // Parcel Specific
    const val STATUS_RIDER_ON_THE_WAY_TO_SENDER = "Rider is on the way to sender"
    const val STATUS_ARRIVED_AT_SENDER = "Arrived at sender"
    const val STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT = "Rider is on the way to recipient"
    const val STATUS_ARRIVED_AT_RECIPIENT = "Arrived at recipient"

    // Pabili Specific
    const val STATUS_RIDER_ON_THE_WAY_TO_VENDOR = "Rider is on the way to vendor"
    const val STATUS_ARRIVED_AT_VENDOR = "Arrived at vendor"

    // Progression flows
    val FOOD_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_PREPARING,
        STATUS_READY,
        STATUS_RIDER_ON_THE_WAY,
        STATUS_DELIVERED
    )

    val CONVENIENCE_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_PREPARING,
        STATUS_READY,
        STATUS_RIDER_ON_THE_WAY,
        STATUS_DELIVERED
    )

    val PARCEL_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_RIDER_ON_THE_WAY_TO_SENDER,
        STATUS_ARRIVED_AT_SENDER,
        STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT,
        STATUS_ARRIVED_AT_RECIPIENT,
        STATUS_DELIVERED
    )

    val PABILI_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_RIDER_ON_THE_WAY_TO_VENDOR,
        STATUS_ARRIVED_AT_VENDOR,
        STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT,
        STATUS_ARRIVED_AT_RECIPIENT,
        STATUS_DELIVERED
    )

    fun getFlowForService(serviceType: String): List<String> = when (serviceType.lowercase()) {
        SERVICE_PARCEL -> PARCEL_STATUS_FLOW
        SERVICE_PABILI -> PABILI_STATUS_FLOW
        SERVICE_CONVENIENCE -> CONVENIENCE_STATUS_FLOW
        else -> FOOD_STATUS_FLOW
    }

    fun isRiderOnTheWay(serviceType: String, status: String): Boolean = when (serviceType.lowercase()) {
        SERVICE_PARCEL -> status in listOf(STATUS_RIDER_ON_THE_WAY_TO_SENDER, STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT)
        SERVICE_PABILI -> status in listOf(STATUS_RIDER_ON_THE_WAY_TO_VENDOR, STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT)
        else -> status == STATUS_RIDER_ON_THE_WAY
    }

    fun isTerminal(status: String): Boolean {
        val s = status.trim().lowercase()
        return s in listOf("delivered", "cancelled", "canceled", "rejected")
    }

    fun canCancel(status: String): Boolean = status == STATUS_PLACED_ORDER
}
