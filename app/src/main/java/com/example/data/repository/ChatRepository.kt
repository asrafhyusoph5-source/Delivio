package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Order
import com.example.data.model.OrderChatThread
import com.example.data.model.OrderDocument
import com.example.data.model.OrderStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class ChatRepository(
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
) {
    private val tag = "ChatRepository"

    companion object {
        private var lastCleanupTimeMs: Long = 0L
        private val cleanupLock = Any()
        private const val CLEANUP_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes

        fun isOrderClosed(status: String?): Boolean {
            if (status.isNullOrBlank()) return false
            val s = status.trim().lowercase()
            return s == "delivered" || s == "completed" || s == "cancelled" || s == "canceled" || s == "rejected"
        }

        fun getOrderClosedTime(order: Order): Long {
            // Find timestamp from statusTimestamps for terminal status, or fallback to latest timestamp / createdAt
            val terminalTime = order.statusTimestamps[order.status]
                ?: order.statusTimestamps[OrderStatus.DELIVERED]
                ?: order.statusTimestamps[OrderStatus.COMPLETED]
                ?: order.statusTimestamps[OrderStatus.CANCELLED]
                ?: order.statusTimestamps[OrderStatus.REJECTED]
            if (terminalTime != null && terminalTime > 0L) return terminalTime
            return order.statusTimestamps.values.maxOrNull() ?: order.createdAt
        }

        fun getOrderClosedTime(orderDoc: OrderDocument): Long {
            val terminalEntry = orderDoc.statusHistory.lastOrNull { entry ->
                isOrderClosed(entry.status)
            }
            return terminalEntry?.at ?: orderDoc.updatedAt
        }

        fun isDeletedAfter24h(order: Order): Boolean {
            if (!isOrderClosed(order.rawStatus) && !isOrderClosed(order.status.title)) return false
            val closedTime = getOrderClosedTime(order)
            if (closedTime <= 0L) return false
            return (System.currentTimeMillis() - closedTime) >= 24 * 60 * 60 * 1000L
        }

        fun isDeletedAfter24h(orderDoc: OrderDocument): Boolean {
            if (!isOrderClosed(orderDoc.status)) return false
            val closedTime = getOrderClosedTime(orderDoc)
            if (closedTime <= 0L) return false
            return (System.currentTimeMillis() - closedTime) >= 24 * 60 * 60 * 1000L
        }

        fun getCustomerFirstName(fullName: String?): String {
            if (fullName.isNullOrBlank()) return "Customer"
            return fullName.trim().split("\\s+".toRegex()).firstOrNull() ?: fullName
        }

        fun createMerchantChatArgs(
            order: Order,
            customerId: String,
            customerName: String
        ): com.example.data.model.ChatArgs {
            val closed = isOrderClosed(order.rawStatus)
            val deleted = isDeletedAfter24h(order)
            val firstName = getCustomerFirstName(customerName)
            return com.example.data.model.ChatArgs(
                chatId = "${order.id}_merchant",
                orderId = order.id,
                orderNumber = order.displayOrderNumber.ifEmpty { order.orderNumber },
                channel = "merchant",
                recipientName = order.restaurant.name,
                recipientRole = "Merchant",
                customerId = customerId,
                customerName = firstName,
                merchantId = order.restaurant.id,
                merchantName = order.restaurant.name,
                isOrderClosed = closed,
                isDeletedAfter24h = deleted,
                canSendImages = false
            )
        }

        fun createDriverChatArgs(
            order: Order,
            customerId: String,
            customerName: String
        ): com.example.data.model.ChatArgs {
            val closed = isOrderClosed(order.rawStatus)
            val deleted = isDeletedAfter24h(order)
            val firstName = getCustomerFirstName(customerName)
            val riderId = order.rider?.id ?: ""
            val riderName = order.rider?.name ?: "Driver"
            val chatId = if (riderId.isNotBlank()) "${order.id}_rider_$riderId" else "${order.id}_rider"
            return com.example.data.model.ChatArgs(
                chatId = chatId,
                orderId = order.id,
                orderNumber = order.displayOrderNumber.ifEmpty { order.orderNumber },
                channel = "rider",
                recipientName = riderName,
                recipientRole = "Driver",
                customerId = customerId,
                customerName = firstName,
                riderId = riderId,
                riderName = riderName,
                isOrderClosed = closed,
                isDeletedAfter24h = deleted,
                canSendImages = true
            )
        }
    }

    /**
     * Listen to all customer threads (for unread red dot indicators).
     * Uses equality filter only: customerId == uid.
     * Sorted on the device.
     */
    fun getCustomerThreadsFlow(customerId: String): Flow<List<OrderChatThread>> = callbackFlow {
        val fs = firestore
        if (fs == null || customerId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("orderChats")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to customer chats: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val threads = snapshot.documents.mapNotNull { doc ->
                    parseThread(doc.id, doc.data)
                }.sortedByDescending { it.lastMessageAt ?: 0L }
                trySend(threads)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Listen to a specific thread's document.
     */
    fun getThreadFlow(chatId: String): Flow<OrderChatThread?> = callbackFlow {
        val fs = firestore
        if (fs == null || chatId.isBlank()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("orderChats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to thread $chatId: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(parseThread(snapshot.id, snapshot.data))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Listen to all messages in a thread.
     * Equality/document path only, sorted on device by createdAt ascending.
     */
    fun getMessagesFlow(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val fs = firestore
        if (fs == null || chatId.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("orderChats").document(chatId)
            .collection("chatMessages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error listening to messages for $chatId: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val msgs = snapshot.documents.mapNotNull { doc ->
                    parseMessage(doc.id, doc.data)
                }.sortedBy { it.createdAt ?: 0L }
                trySend(msgs)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Sending a message is ONE batch:
     * - create the thread document if it does not exist yet;
     *   otherwise update only {lastMessageAt, lastMessageText, lastSenderRole, expireAt (now + 10 days)}
     * - create the message.
     */
    suspend fun sendMessage(
        chatId: String,
        orderId: String,
        orderNumber: String,
        channel: String, // "merchant" | "rider"
        customerId: String,
        customerName: String,
        merchantId: String?,
        merchantName: String?,
        riderId: String?,
        riderName: String?,
        senderId: String,
        senderRole: String = "customer",
        type: String, // "text" | "image"
        text: String,
        image: String? = null,
        source: String? = null
    ): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        if (chatId.isBlank()) return Result.failure(IllegalArgumentException("chatId is blank"))

        return try {
            val threadRef = fs.collection("orderChats").document(chatId)
            val threadSnap = threadRef.get().await()

            val tenDaysLater = Timestamp(Date(System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000L))
            val previewText = if (type == "image") "Photo" else text.take(80)

            val batch = fs.batch()

            if (!threadSnap.exists()) {
                val threadData = hashMapOf<String, Any?>(
                    "orderId" to orderId,
                    "orderNumber" to orderNumber,
                    "channel" to channel,
                    "customerId" to customerId,
                    "customerName" to getCustomerFirstName(customerName),
                    "merchantId" to merchantId,
                    "merchantName" to merchantName,
                    "riderId" to riderId,
                    "riderName" to riderName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageText" to previewText,
                    "lastSenderRole" to senderRole,
                    "customerReadAt" to FieldValue.serverTimestamp(),
                    "partnerReadAt" to null,
                    "expireAt" to tenDaysLater
                )
                batch.set(threadRef, threadData)
            } else {
                val threadUpdates = hashMapOf<String, Any?>(
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageText" to previewText,
                    "lastSenderRole" to senderRole,
                    "expireAt" to tenDaysLater
                )
                batch.update(threadRef, threadUpdates)
            }

            // Create message document
            val messageRef = threadRef.collection("chatMessages").document()
            val messageData = hashMapOf<String, Any?>(
                "senderId" to senderId,
                "senderRole" to senderRole,
                "type" to type,
                "text" to (if (type == "image") (text.ifBlank { "Photo" }) else text),
                "image" to image,
                "source" to source,
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to tenDaysLater
            )
            batch.set(messageRef, messageData)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error sending message in thread $chatId", e)
            Result.failure(e)
        }
    }

    /**
     * Marking as read: updates only customerReadAt (or partnerReadAt).
     */
    suspend fun markAsRead(chatId: String): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore not initialized"))
        if (chatId.isBlank()) return Result.success(Unit)

        return try {
            val threadRef = fs.collection("orderChats").document(chatId)
            threadRef.update("customerReadAt", FieldValue.serverTimestamp()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Error marking thread $chatId as read: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseThread(id: String, data: Map<String, Any?>?): OrderChatThread? {
        if (data == null) return null
        return try {
            fun parseTime(v: Any?): Long? {
                return when (v) {
                    is Timestamp -> v.toDate().time
                    is Number -> v.toLong()
                    else -> null
                }
            }

            OrderChatThread(
                chatId = id,
                orderId = (data["orderId"] as? String) ?: "",
                orderNumber = (data["orderNumber"] as? String) ?: "",
                channel = (data["channel"] as? String) ?: "merchant",
                customerId = (data["customerId"] as? String) ?: "",
                customerName = (data["customerName"] as? String) ?: "",
                merchantId = data["merchantId"] as? String,
                merchantName = data["merchantName"] as? String,
                riderId = data["riderId"] as? String,
                riderName = data["riderName"] as? String,
                createdAt = parseTime(data["createdAt"]),
                lastMessageAt = parseTime(data["lastMessageAt"]),
                lastMessageText = (data["lastMessageText"] as? String) ?: "",
                lastSenderRole = (data["lastSenderRole"] as? String) ?: "",
                customerReadAt = parseTime(data["customerReadAt"]),
                partnerReadAt = parseTime(data["partnerReadAt"]),
                expireAt = parseTime(data["expireAt"])
            )
        } catch (e: Exception) {
            Log.w(tag, "Error parsing chat thread $id", e)
            null
        }
    }

    private fun parseMessage(id: String, data: Map<String, Any?>?): ChatMessage? {
        if (data == null) return null
        return try {
            fun parseTime(v: Any?): Long? {
                return when (v) {
                    is Timestamp -> v.toDate().time
                    is Number -> v.toLong()
                    else -> null
                }
            }

            ChatMessage(
                id = id,
                senderId = (data["senderId"] as? String) ?: "",
                senderRole = (data["senderRole"] as? String) ?: "customer",
                type = (data["type"] as? String) ?: "text",
                text = (data["text"] as? String) ?: "",
                image = data["image"] as? String,
                source = data["source"] as? String,
                createdAt = parseTime(data["createdAt"]),
                expireAt = parseTime(data["expireAt"])
            )
        } catch (e: Exception) {
            Log.w(tag, "Error parsing chat message $id", e)
            null
        }
    }

    /**
     * Automatic cleanup of expired chats (no server needed).
     * For each thread belonging to the signed-in user:
     * - Query chatMessages where expireAt < now (limit 100) and delete in a batch.
     * - If the thread's own expireAt < now, delete the thread.
     * Throttled to run at most once every 30 minutes. Runs in background, errors silently ignored.
     */
    suspend fun cleanupExpiredChatsForCustomer(customerId: String, force: Boolean = false) {
        val fs = firestore ?: return
        if (customerId.isBlank()) return

        val nowMs = System.currentTimeMillis()
        synchronized(cleanupLock) {
            if (!force && (nowMs - lastCleanupTimeMs) < CLEANUP_INTERVAL_MS) {
                return
            }
            lastCleanupTimeMs = nowMs
        }

        try {
            val nowTimestamp = Timestamp(Date(nowMs))
            val threadsSnapshot = fs.collection("orderChats")
                .whereEqualTo("customerId", customerId)
                .get()
                .await()

            for (threadDoc in threadsSnapshot.documents) {
                cleanupSingleThreadDoc(threadDoc, nowTimestamp, nowMs)
            }
        } catch (e: Exception) {
            // Silently ignore any errors per requirements
            Log.d(tag, "Silent chat cleanup skipped or partial: ${e.message}")
        }
    }

    suspend fun cleanupSingleThreadIfExpired(chatId: String) {
        val fs = firestore ?: return
        if (chatId.isBlank()) return
        try {
            val nowMs = System.currentTimeMillis()
            val nowTimestamp = Timestamp(Date(nowMs))
            val threadDoc = fs.collection("orderChats").document(chatId).get().await()
            if (threadDoc != null && threadDoc.exists()) {
                cleanupSingleThreadDoc(threadDoc, nowTimestamp, nowMs)
            }
        } catch (e: Exception) {
            // Silently ignore any errors
            Log.d(tag, "Silent thread cleanup skipped: ${e.message}")
        }
    }

    private suspend fun cleanupSingleThreadDoc(
        threadDoc: com.google.firebase.firestore.DocumentSnapshot,
        nowTimestamp: Timestamp,
        nowMs: Long
    ) {
        val fs = firestore ?: return
        try {
            val threadRef = threadDoc.reference
            val expiredMessagesSnap = threadRef.collection("chatMessages")
                .whereLessThan("expireAt", nowTimestamp)
                .limit(100)
                .get()
                .await()

            if (!expiredMessagesSnap.isEmpty) {
                val batch = fs.batch()
                for (msgDoc in expiredMessagesSnap.documents) {
                    batch.delete(msgDoc.reference)
                }
                batch.commit().await()
            }

            // Check if thread's own expireAt < now
            val threadExpireAt = threadDoc.getTimestamp("expireAt")
            val threadExpireMs = threadExpireAt?.toDate()?.time
                ?: (threadDoc.get("expireAt") as? Number)?.toLong()

            if (threadExpireMs != null && threadExpireMs < nowMs) {
                threadRef.delete().await()
            }
        } catch (e: Exception) {
            // Silently ignore any errors
            Log.d(tag, "Silent thread doc cleanup skipped for ${threadDoc.id}: ${e.message}")
        }
    }
}
