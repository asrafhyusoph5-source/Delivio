package com.example.data.repository

import android.util.Log
import com.example.data.model.InboxLink
import com.example.data.model.InboxMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InboxRepository(
    private val firestore: FirebaseFirestore? = null
) {
    private val tag = "DelivioInboxRepo"

    private fun getFs(): FirebaseFirestore? {
        return firestore ?: try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firestore not available: ${e.message}")
            null
        }
    }

    private fun parseInboxDoc(doc: DocumentSnapshot): InboxMessage {
        val linkMap = doc.get("link") as? Map<*, *>
        val link = if (linkMap != null) {
            InboxLink(
                merchantId = linkMap["merchantId"]?.toString(),
                category = linkMap["category"]?.toString()
            )
        } else null

        val createdAtRaw = doc.get("createdAt")
        val timestamp = when (createdAtRaw) {
            is Timestamp -> createdAtRaw
            is java.util.Date -> Timestamp(createdAtRaw)
            is Number -> Timestamp(createdAtRaw.toLong() / 1000, 0)
            else -> null
        }

        return InboxMessage(
            id = doc.id,
            customerId = doc.getString("customerId"),
            audience = doc.getString("audience"),
            title = doc.getString("title") ?: "",
            body = doc.getString("body") ?: "",
            category = (doc.getString("category") ?: "system").lowercase(),
            imageUrl = doc.getString("imageUrl"),
            link = link,
            createdAt = timestamp
        )
    }

    // Live query 1: messages where customerId == uid
    private fun getCustomerMessagesFlow(uid: String): Flow<List<InboxMessage>> = callbackFlow {
        val fs = getFs()
        if (fs == null || uid.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = fs.collection("inbox")
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error loading customer inbox messages: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try { parseInboxDoc(doc) } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    // Live query 2: messages where audience == "all_customers"
    private fun getAudienceMessagesFlow(): Flow<List<InboxMessage>> = callbackFlow {
        val fs = getFs()
        if (fs == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = fs.collection("inbox")
            .whereEqualTo("audience", "all_customers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error loading audience inbox messages: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try { parseInboxDoc(doc) } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    // Live query 3: user's inboxReads subcollection (users/{uid}/inboxReads)
    fun getReadMessageIdsFlow(uid: String): Flow<Set<String>> = callbackFlow {
        val fs = getFs()
        if (fs == null || uid.isBlank()) {
            trySend(emptySet())
            awaitClose {}
            return@callbackFlow
        }

        val listener: ListenerRegistration = fs.collection("users")
            .document(uid)
            .collection("inboxReads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Error loading inboxReads: ${error.message}")
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val readIds = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                trySend(readIds)
            }

        awaitClose { listener.remove() }
    }

    // Merged live queries: customerId == uid + audience == all_customers, sorted newest first in code
    fun getMergedInboxFlow(uid: String): Flow<List<InboxMessage>> {
        val customerFlow = getCustomerMessagesFlow(uid)
        val audienceFlow = getAudienceMessagesFlow()

        return combine(customerFlow, audienceFlow) { customerMsgs, audienceMsgs ->
            val combined = (customerMsgs + audienceMsgs).distinctBy { it.id }
            combined.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
    }

    // Mark single message as read: writes users/{uid}/inboxReads/{messageId} {readAt}
    suspend fun markMessageAsRead(uid: String, messageId: String) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || messageId.isBlank()) return@withContext
        val fs = getFs() ?: return@withContext
        try {
            fs.collection("users")
                .document(uid)
                .collection("inboxReads")
                .document(messageId)
                .set(mapOf("readAt" to FieldValue.serverTimestamp()))
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to mark message $messageId as read", e)
        }
    }

    // Mark all as read: writes all unread IDs in batch to users/{uid}/inboxReads/{messageId}
    suspend fun markAllAsRead(uid: String, messageIds: List<String>) = withContext(Dispatchers.IO) {
        if (uid.isBlank() || messageIds.isEmpty()) return@withContext
        val fs = getFs() ?: return@withContext
        try {
            val batch = fs.batch()
            messageIds.forEach { mid ->
                val docRef = fs.collection("users")
                    .document(uid)
                    .collection("inboxReads")
                    .document(mid)
                batch.set(docRef, mapOf("readAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to mark all as read", e)
        }
    }
}
