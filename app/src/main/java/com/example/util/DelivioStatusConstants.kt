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
    const val SERVICE_TAXI = "taxi"

    // Fulfillment Types
    const val FULFILLMENT_PICKUP = "pickup"
    const val FULFILLMENT_DELIVERY = "delivery"
    const val FULFILLMENT_SCHEDULED = "scheduled"

    // Common Statuses
    const val STATUS_PLACED_ORDER = "Placed Order"
    const val STATUS_CANCELLED = "Cancelled"
    const val STATUS_DELIVERED = "Delivered"
    const val STATUS_COMPLETED = "Completed"

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

    // Taxi Specific
    const val STATUS_LOOKING_FOR_DRIVER = "Looking for a driver..."
    const val STATUS_DRIVER_ON_THE_WAY = "Driver is on the way"
    const val STATUS_DRIVER_ARRIVED = "Driver arrived"
    const val STATUS_ON_TRIP = "On trip"

    // Progression flows
    val PICKUP_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_PREPARING,
        STATUS_READY,
        STATUS_COMPLETED
    )

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

    val TAXI_STATUS_FLOW = listOf(
        STATUS_PLACED_ORDER,
        STATUS_DRIVER_ON_THE_WAY,
        STATUS_DRIVER_ARRIVED,
        STATUS_ON_TRIP,
        STATUS_COMPLETED
    )

    fun getFlowForService(serviceType: String): List<String> = when (serviceType.lowercase()) {
        SERVICE_PARCEL -> PARCEL_STATUS_FLOW
        SERVICE_PABILI -> PABILI_STATUS_FLOW
        SERVICE_CONVENIENCE -> CONVENIENCE_STATUS_FLOW
        SERVICE_TAXI -> TAXI_STATUS_FLOW
        else -> FOOD_STATUS_FLOW
    }

    fun getFlowForFulfillment(fulfillment: String?, serviceType: String = SERVICE_FOOD): List<String> {
        val f = (fulfillment ?: FULFILLMENT_DELIVERY).lowercase()
        if (f == FULFILLMENT_PICKUP) return PICKUP_STATUS_FLOW
        return getFlowForService(serviceType)
    }

    fun formatScheduledTimestamp(timeMs: Long): String {
        val sdf = java.text.SimpleDateFormat("MM/dd/yyyy 'at' hh:mm a", java.util.Locale.US)
        return "Scheduled on " + sdf.format(java.util.Date(timeMs))
    }

    fun getFirstStepTitle(fulfillment: String?, scheduledFor: Long?): String {
        val f = (fulfillment ?: FULFILLMENT_DELIVERY).lowercase()
        if (f == FULFILLMENT_SCHEDULED && scheduledFor != null && System.currentTimeMillis() < scheduledFor) {
            return formatScheduledTimestamp(scheduledFor)
        }
        return STATUS_PLACED_ORDER
    }

    fun getReadySubtitle(fulfillment: String?): String {
        val f = (fulfillment ?: FULFILLMENT_DELIVERY).lowercase()
        if (f == FULFILLMENT_PICKUP) return "Ready for pick up"
        return "Packed and waiting for rider"
    }

    fun isRiderOnTheWay(serviceType: String, status: String): Boolean = when (serviceType.lowercase()) {
        SERVICE_PARCEL -> status in listOf(STATUS_RIDER_ON_THE_WAY_TO_SENDER, STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT)
        SERVICE_PABILI -> status in listOf(STATUS_RIDER_ON_THE_WAY_TO_VENDOR, STATUS_RIDER_ON_THE_WAY_TO_RECIPIENT)
        SERVICE_TAXI -> status in listOf(STATUS_DRIVER_ON_THE_WAY, STATUS_DRIVER_ARRIVED, STATUS_ON_TRIP)
        else -> status == STATUS_RIDER_ON_THE_WAY
    }

    fun isTerminal(status: String): Boolean {
        val s = status.trim().lowercase()
        return s in listOf("delivered", "completed", "cancelled", "canceled", "rejected")
    }

    fun canCancel(status: String): Boolean = status == STATUS_PLACED_ORDER

    fun canCancelTaxi(status: String): Boolean {
        val s = status.trim().lowercase()
        return s in listOf("placed order", "looking for a driver...", "placed", "pending", "driver is on the way")
    }
}
