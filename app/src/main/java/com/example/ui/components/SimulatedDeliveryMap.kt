package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Delivery Tracking Map using osmdroid and OSRM routing.
 */
@Composable
fun SimulatedDeliveryMap(
    restaurantName: String,
    deliveryAddress: String,
    modifier: Modifier = Modifier,
    pickupLat: Double = 9.7392,
    pickupLng: Double = 118.7353,
    dropoffLat: Double = 9.7420,
    dropoffLng: Double = 118.7400,
    riderLat: Double? = null,
    riderLng: Double? = null,
    riderName: String? = null,
    orderStatus: String = "On the way"
) {
    OsmOrderTrackingMap(
        pickupName = restaurantName,
        pickupAddress = "Store Location",
        pickupLat = pickupLat,
        pickupLng = pickupLng,
        dropoffName = "Destination",
        dropoffAddress = deliveryAddress,
        dropoffLat = dropoffLat,
        dropoffLng = dropoffLng,
        riderLat = riderLat,
        riderLng = riderLng,
        riderName = riderName,
        orderStatus = orderStatus,
        modifier = modifier
    )
}
