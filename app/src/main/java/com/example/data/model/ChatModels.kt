package com.example.data.model

import com.google.firebase.Timestamp

data class OrderChatThread(
    val chatId: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val channel: String = "merchant", // "merchant" | "rider"
    val customerId: String = "",
    val customerName: String = "", // first name only
    val merchantId: String? = null,
    val merchantName: String? = null,
    val riderId: String? = null,
    val riderName: String? = null,
    val createdAt: Long? = null,
    val lastMessageAt: Long? = null,
    val lastMessageText: String = "", // max 80 chars; "Photo" for images
    val lastSenderRole: String = "", // "customer" | "merchant" | "rider"
    val customerReadAt: Long? = null,
    val partnerReadAt: Long? = null,
    val expireAt: Long? = null
) {
    fun isUnreadForCustomer(): Boolean {
        if (lastSenderRole.isEmpty() || lastSenderRole == "customer") return false
        val lastMsg = lastMessageAt ?: return false
        val readAt = customerReadAt ?: return true
        return lastMsg > readAt
    }
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderRole: String = "customer", // "customer" | "merchant" | "rider"
    val type: String = "text", // "text" | "image"
    val text: String = "", // 1 to 1000 chars
    val image: String? = null, // JPEG data URI string, max 400000 chars
    val source: String? = null, // "camera" | "gallery"
    val createdAt: Long? = null,
    val expireAt: Long? = null
)

data class ChatArgs(
    val chatId: String,
    val orderId: String,
    val orderNumber: String,
    val channel: String, // "merchant" | "rider"
    val recipientName: String,
    val recipientRole: String, // "Merchant" | "Driver"
    val customerId: String,
    val customerName: String,
    val merchantId: String? = null,
    val merchantName: String? = null,
    val riderId: String? = null,
    val riderName: String? = null,
    val isOrderClosed: Boolean = false,
    val isDeletedAfter24h: Boolean = false,
    val canSendImages: Boolean = false // false for merchant, true for driver
)
