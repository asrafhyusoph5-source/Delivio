package com.example.data.repository

import android.util.Log
import com.example.data.model.SendMoneyReceipt
import com.example.data.model.WalletActivityCategory
import com.example.data.model.WalletActivityItem
import com.example.data.model.WalletDirectoryEntry
import com.example.data.model.WalletTransfer
import com.example.util.Formatters
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.util.FirestoreErrorBus
import java.util.Collections
import java.util.Locale

class WalletRepository(
    private val firestoreService: FirestoreService
) {
    private val tag = "DelivioWallet"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "FirebaseFirestore not initialized: ${e.message}")
            null
        }
    }

    // Set of transfer IDs that have already been notified
    private val notifiedTransferIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var isInitialIncomingSnapshot = true

    private val _incomingTransferEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val incomingTransferEvents: SharedFlow<String> = _incomingTransferEvents.asSharedFlow()

    // ----------------------------------------------------
    // Normalization & Masking Utilities
    // ----------------------------------------------------

    /**
     * Normalizes a mobile number to 63 plus 10 digits (63XXXXXXXXXX).
     * Accepts:
     * - 09XXXXXXXXX (11 digits)
     * - +63XXXXXXXXXX (13 chars)
     * - 63XXXXXXXXXX (12 digits)
     * - 9XXXXXXXXX (10 digits)
     */
    fun normalizePhone(rawPhone: String?): String? {
        if (rawPhone.isNullOrBlank()) return null
        val cleaned = rawPhone.replace(Regex("[^0-9+]"), "")
        val digits = if (cleaned.startsWith("+")) cleaned.substring(1) else cleaned

        val normalized = when {
            digits.startsWith("63") && digits.length == 12 -> digits
            digits.startsWith("09") && digits.length == 11 -> "63" + digits.substring(1)
            digits.startsWith("9") && digits.length == 10 -> "63$digits"
            else -> null
        }
        return if (normalized != null && normalized.matches(Regex("^63\\d{10}$"))) normalized else null
    }

    /**
     * Display name formatted as first name plus last initial (e.g. "Juan D.").
     */
    fun formatDisplayName(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "Delivio Customer"
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> {
                val firstName = parts.first()
                val lastInitial = parts.last().first().uppercaseChar()
                "$firstName $lastInitial."
            }
            parts.size == 1 -> parts.first()
            else -> "Delivio Customer"
        }
    }

    /**
     * Masks an email address: e.g. "juan@gmail.com" -> "j***@gmail.com"
     */
    fun maskEmail(email: String?): String {
        if (email.isNullOrBlank() || !email.contains("@")) return ""
        val parts = email.trim().split("@", limit = 2)
        val user = parts[0]
        val domain = parts[1]
        val firstChar = if (user.isNotEmpty()) user.first().toString() else "u"
        return "$firstChar***@$domain"
    }

    /**
     * Masks a phone number: e.g. "639171234567" -> "•••• 4567"
     */
    fun maskPhone(phone: String?): String {
        val norm = normalizePhone(phone) ?: phone?.filter { it.isDigit() } ?: ""
        return if (norm.length >= 4) {
            "•••• " + norm.takeLast(4)
        } else {
            "••••"
        }
    }

    // ----------------------------------------------------
    // WALLET DIRECTORY
    // ----------------------------------------------------

    /**
     * Upserts walletDirectory documents:
     * 1. "{uid}"
     * 2. "email_{lowercase email}"
     * 3. "phone_63XXXXXXXXXX" (if phone is valid)
     * If a phone document already belongs to another uid, skips it and returns an error message.
     * Deletes the old phone document when phone changes.
     */
    suspend fun upsertWalletDirectory(
        uid: String,
        name: String,
        email: String,
        phone: String,
        oldPhone: String? = null
    ): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("UID cannot be empty"))

        val displayName = formatDisplayName(name)
        val emailClean = email.trim().lowercase(Locale.ROOT)
        val emailHint = maskEmail(emailClean)

        val newNormPhone = normalizePhone(phone)
        val oldNormPhone = normalizePhone(oldPhone)

        val dirColl = fs.collection("walletDirectory")

        try {
            // Check if phone document exists and belongs to someone else
            if (newNormPhone != null) {
                val phoneDocRef = dirColl.document("phone_$newNormPhone")
                val existingPhoneDoc = phoneDocRef.get().await()
                if (existingPhoneDoc.exists()) {
                    val existingUid = existingPhoneDoc.getString("uid")
                    if (!existingUid.isNullOrBlank() && existingUid != uid) {
                        return Result.failure(Exception("This mobile number is already registered to another account"))
                    }
                }
            }

            // If phone changed, delete old phone document if it belonged to this user
            if (oldNormPhone != null && oldNormPhone != newNormPhone) {
                try {
                    val oldDoc = dirColl.document("phone_$oldNormPhone").get().await()
                    if (oldDoc.exists() && oldDoc.getString("uid") == uid) {
                        dirColl.document("phone_$oldNormPhone").delete().await()
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to delete old phone directory doc: ${e.message}")
                }
            }

            val batch = fs.batch()

            // 1. document "{uid}"
            val uidDocRef = dirColl.document(uid)
            val uidHint = emailHint.ifEmpty { newNormPhone?.let { maskPhone(it) } ?: "" }
            batch.set(
                uidDocRef,
                mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "hint" to uidHint,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 2. document "email_{lowercase email}"
            if (emailClean.isNotBlank() && emailClean.contains("@")) {
                val emailDocRef = dirColl.document("email_$emailClean")
                batch.set(
                    emailDocRef,
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "hint" to emailHint,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // 3. document "phone_63XXXXXXXXXX"
            if (newNormPhone != null) {
                val phoneDocRef = dirColl.document("phone_$newNormPhone")
                batch.set(
                    phoneDocRef,
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName,
                        "hint" to maskPhone(newNormPhone),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upsert wallet directory: ${e.message}", e)
            FirestoreErrorBus.emitError("set", "walletDirectory/$uid", e)
            return Result.failure(e)
        }
    }

    /**
     * Looks up a recipient in walletDirectory using ONLY single-document reads (get).
     * Never queries or lists walletDirectory.
     */
    suspend fun lookupRecipient(query: String): Result<WalletDirectoryEntry> {
        val fs = firestore ?: return Result.failure(Exception("Firestore not initialized"))
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return Result.failure(Exception("Please enter a mobile number, email, or Delivio ID"))
        }

        val dirColl = fs.collection("walletDirectory")

        try {
            // Determine possible document IDs to try with single get()
            val candidateDocIds = mutableListOf<String>()

            if (trimmed.contains("@")) {
                // Email
                candidateDocIds.add("email_${trimmed.lowercase(Locale.ROOT)}")
            }

            val normPhone = normalizePhone(trimmed)
            if (normPhone != null) {
                // Phone
                candidateDocIds.add("phone_$normPhone")
            }

            // Direct UID or raw query
            candidateDocIds.add(trimmed)

            for (docId in candidateDocIds) {
                val doc = dirColl.document(docId).get().await()
                if (doc.exists()) {
                    val uid = doc.getString("uid") ?: ""
                    val displayName = doc.getString("displayName") ?: ""
                    val hint = doc.getString("hint") ?: ""
                    if (uid.isNotBlank()) {
                        return Result.success(
                            WalletDirectoryEntry(
                                uid = uid,
                                displayName = displayName.ifEmpty { "Delivio User" },
                                hint = hint
                            )
                        )
                    }
                }
            }

            val notFoundDocId = candidateDocIds.firstOrNull() ?: trimmed
            val notFoundMsg = "Not found. The person must have a Delivio account"
            FirestoreErrorBus.emitError("get", "walletDirectory/$notFoundDocId", notFoundMsg)
            return Result.failure(Exception(notFoundMsg))
        } catch (e: Exception) {
            Log.e(tag, "Error looking up recipient: ${e.message}", e)
            val docId = trimmed.ifEmpty { "unknown" }
            FirestoreErrorBus.emitError("get", "walletDirectory/$docId", e)
            return Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // SEND MONEY & QR PAY ATOMIC BATCH
    // ----------------------------------------------------

    /**
     * Atomically transfers funds:
     * 1. set walletTransfers/{newId} {fromUid, fromName, toUid, toName, amount, note, method, createdAt}
     * 2. update users/{fromUid} {walletBalance: FieldValue.increment(-amount)}
     * 3. update users/{toUid} {walletBalance: FieldValue.increment(+amount), lastTransferId: newId}
     */
    suspend fun sendMoney(
        fromUid: String,
        fromName: String,
        toUid: String,
        toName: String,
        amount: Double,
        note: String = "",
        method: String = "send" // "send" | "qr"
    ): Result<SendMoneyReceipt> {
        if (fromUid.isBlank()) {
            return Result.failure(Exception("Sender not authenticated"))
        }
        if (toUid.isBlank()) {
            return Result.failure(Exception("Recipient not found"))
        }
        if (fromUid == toUid) {
            return Result.failure(Exception("You cannot send money to yourself"))
        }
        if (amount < 1.0 || amount > 10000.0) {
            return Result.failure(Exception("Amount must be between ₱1.00 and ₱10,000.00"))
        }

        val fs = firestore ?: return Result.failure(Exception("Offline: Cannot process transfer"))

        return try {
            // Verify sender's current balance with a single get
            val senderDoc = fs.collection("users").document(fromUid).get().await()
            val currentSenderBalance = (senderDoc.get("walletBalance") as? Number)?.toDouble() ?: 0.0
            if (currentSenderBalance < amount) {
                return Result.failure(Exception("Insufficient wallet balance. Current balance: ₱${Formatters.formatCurrency(currentSenderBalance)}"))
            }

            val transferRef = fs.collection("walletTransfers").document()
            val newId = transferRef.id
            val senderRef = fs.collection("users").document(fromUid)
            val recipientRef = fs.collection("users").document(toUid)

            val transferData = mapOf(
                "fromUid" to fromUid,
                "fromName" to formatDisplayName(fromName),
                "toUid" to toUid,
                "toName" to formatDisplayName(toName),
                "amount" to amount,
                "note" to note.trim(),
                "method" to method,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = fs.batch()
            batch.set(transferRef, transferData)
            batch.update(senderRef, mapOf("walletBalance" to FieldValue.increment(-amount)))
            batch.update(
                recipientRef,
                mapOf(
                    "walletBalance" to FieldValue.increment(amount),
                    "lastTransferId" to newId
                )
            )

            batch.commit().await()

            val refCode = newId.take(8).uppercase(Locale.ROOT)
            val newBalance = (currentSenderBalance - amount).coerceAtLeast(0.0)

            Result.success(
                SendMoneyReceipt(
                    referenceCode = refCode,
                    toName = toName,
                    amount = amount,
                    newBalance = newBalance,
                    timestamp = System.currentTimeMillis(),
                    note = note.trim()
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Send money batch failed", e)
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // INCOMING TRANSFERS LISTENER
    // ----------------------------------------------------

    fun startListeningToIncomingTransfers(uid: String): ListenerRegistration? {
        val fs = firestore ?: return null
        if (uid.isBlank()) return null

        return fs.collection("walletTransfers")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                if (isInitialIncomingSnapshot) {
                    // Seed existing transfer IDs so we don't alert for past transfers
                    snapshot.documents.forEach { doc ->
                        notifiedTransferIds.add(doc.id)
                    }
                    isInitialIncomingSnapshot = false
                    return@addSnapshotListener
                }

                for (doc in snapshot.documents) {
                    val transferId = doc.id
                    if (!notifiedTransferIds.contains(transferId)) {
                        notifiedTransferIds.add(transferId)
                        val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                        val fromName = doc.getString("fromName") ?: "Someone"
                        val msg = "You received ₱${Formatters.formatCurrency(amount)} from $fromName"
                        _incomingTransferEvents.tryEmit(msg)
                    }
                }
            }
    }

    // ----------------------------------------------------
    // RECENT INCOMING TRANSFERS FLOW (For Receive Screen)
    // ----------------------------------------------------

    fun observeRecentIncomingTransfers(uid: String): Flow<List<WalletTransfer>> = callbackFlow {
        val fs = firestore
        if (fs == null || uid.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("walletTransfers")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val transfers = snapshot.documents.mapNotNull { doc ->
                    val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: (doc.get("createdAt") as? Number)?.toLong()
                        ?: System.currentTimeMillis()
                    WalletTransfer(
                        id = doc.id,
                        fromUid = doc.getString("fromUid") ?: "",
                        fromName = doc.getString("fromName") ?: "Delivio User",
                        toUid = doc.getString("toUid") ?: "",
                        toName = doc.getString("toName") ?: "",
                        amount = amount,
                        note = doc.getString("note") ?: "",
                        method = doc.getString("method") ?: "send",
                        createdAt = createdAt
                    )
                }.sortedByDescending { it.createdAt }

                trySend(transfers)
            }

        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------
    // UNIFIED TRANSACTION HISTORY
    // ----------------------------------------------------

    /**
     * Merges and sorts newest first:
     * - walletTransfers where fromUid == uid (Sent, negative)
     * - walletTransfers where toUid == uid (Received, positive)
     * - walletTransactions where customerId == uid (Top-ups and adjustments)
     * - orders where customerId == uid and paymentMethod == "wallet" (Payments, -fees.total)
     * - and those orders with walletRefunded == true (Refunds, +fees.total)
     */
    fun observeWalletActivity(uid: String): Flow<List<WalletActivityItem>> = callbackFlow {
        val fs = firestore
        if (fs == null || uid.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        var sentList = emptyList<WalletActivityItem>()
        var receivedList = emptyList<WalletActivityItem>()
        var topupsList = emptyList<WalletActivityItem>()
        var orderPaymentsList = emptyList<WalletActivityItem>()
        var orderRefundsList = emptyList<WalletActivityItem>()

        fun emitCombined() {
            val combined = (sentList + receivedList + topupsList + orderPaymentsList + orderRefundsList)
                .sortedByDescending { it.timestamp }
            trySend(combined)
        }

        // 1. Sent transfers (fromUid == uid)
        val sentReg = fs.collection("walletTransfers")
            .whereEqualTo("fromUid", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    sentList = snapshot.documents.mapNotNull { doc ->
                        val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                        val toName = doc.getString("toName") ?: "Recipient"
                        val timestamp = doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: (doc.get("createdAt") as? Number)?.toLong()
                            ?: System.currentTimeMillis()
                        WalletActivityItem(
                            id = "sent_${doc.id}",
                            category = WalletActivityCategory.SENT,
                            title = "Sent to ${formatDisplayName(toName)}",
                            timestamp = timestamp,
                            signedAmount = -amount,
                            referenceCode = doc.id.take(8).uppercase(Locale.ROOT),
                            note = doc.getString("note") ?: ""
                        )
                    }
                    emitCombined()
                }
            }

        // 2. Received transfers (toUid == uid)
        val recReg = fs.collection("walletTransfers")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    receivedList = snapshot.documents.mapNotNull { doc ->
                        val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                        val fromName = doc.getString("fromName") ?: "Sender"
                        val timestamp = doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: (doc.get("createdAt") as? Number)?.toLong()
                            ?: System.currentTimeMillis()
                        WalletActivityItem(
                            id = "recv_${doc.id}",
                            category = WalletActivityCategory.RECEIVED,
                            title = "Received from ${formatDisplayName(fromName)}",
                            timestamp = timestamp,
                            signedAmount = amount,
                            referenceCode = doc.id.take(8).uppercase(Locale.ROOT),
                            note = doc.getString("note") ?: ""
                        )
                    }
                    emitCombined()
                }
            }

        // 3. Top-ups & adjustments from walletTransactions (customerId == uid)
        val txReg = fs.collection("walletTransactions")
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    topupsList = snapshot.documents.mapNotNull { doc ->
                        val amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0
                        val type = doc.getString("type") ?: "cash_in"
                        val desc = doc.getString("description") ?: ""
                        val timestamp = doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: (doc.get("createdAt") as? Number)?.toLong()
                            ?: System.currentTimeMillis()
                        val isAdj = type.equals("adjustment", ignoreCase = true)
                        WalletActivityItem(
                            id = "top_${doc.id}",
                            category = WalletActivityCategory.TOP_UPS,
                            title = if (isAdj) "Adjustment" else "Top-up by Delivio",
                            timestamp = timestamp,
                            signedAmount = amount,
                            referenceCode = doc.id.take(8).uppercase(Locale.ROOT),
                            note = desc
                        )
                    }
                    emitCombined()
                }
            }

        // 4. Orders paid with wallet & refunds from orders (customerId == uid)
        val orderReg = fs.collection("orders")
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val payments = mutableListOf<WalletActivityItem>()
                    val refunds = mutableListOf<WalletActivityItem>()

                    for (doc in snapshot.documents) {
                        val paymentMethod = doc.getString("paymentMethod") ?: ""
                        val feesMap = doc.get("fees") as? Map<*, *>
                        val total = (feesMap?.get("total") as? Number)?.toDouble() ?: 0.0
                        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: (doc.get("createdAt") as? Number)?.toLong()
                            ?: System.currentTimeMillis()
                        val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: (doc.get("updatedAt") as? Number)?.toLong()
                            ?: createdAt
                        val walletRefunded = doc.getBoolean("walletRefunded") == true

                        val shortId = doc.id.takeLast(6).uppercase(Locale.ROOT)

                        if (paymentMethod.equals("wallet", ignoreCase = true)) {
                            payments.add(
                                WalletActivityItem(
                                    id = "pay_${doc.id}",
                                    category = WalletActivityCategory.PAYMENTS,
                                    title = "Payment for order #$shortId",
                                    timestamp = createdAt,
                                    signedAmount = -total,
                                    referenceCode = shortId
                                )
                            )
                        }

                        if (walletRefunded) {
                            refunds.add(
                                WalletActivityItem(
                                    id = "ref_${doc.id}",
                                    category = WalletActivityCategory.REFUNDS,
                                    title = "Refund",
                                    timestamp = updatedAt,
                                    signedAmount = total,
                                    referenceCode = shortId,
                                    note = "Order #$shortId refunded"
                                )
                            )
                        }
                    }

                    orderPaymentsList = payments
                    orderRefundsList = refunds
                    emitCombined()
                }
            }

        awaitClose {
            sentReg.remove()
            recReg.remove()
            txReg.remove()
            orderReg.remove()
        }
    }
}
