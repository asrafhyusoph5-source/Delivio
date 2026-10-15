package com.example.data.model

enum class OrderStatus(
    val title: String,
    val subtitle: String,
    val stepIndex: Int
) {
    PLACED("Placed Order", "Merchant received your order", 0),
    PREPARING("Preparing", "Kitchen is preparing your food", 1),
    READY("Ready", "Packed and waiting for rider", 2),
    ON_THE_WAY("Rider is on the way", "Rider is en route to you", 3),
    DELIVERED("Delivered", "Delivered safely! Enjoy your meal", 4);

    fun isCallEnabled(): Boolean {
        // Call icons are enabled only once the order has been placed
        return true
    }

    fun hasRider(): Boolean {
        // Rider assigned from Ready onward
        return this >= READY
    }

    fun showMap(): Boolean {
        return this == ON_THE_WAY
    }
}
