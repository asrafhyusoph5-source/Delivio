package com.example.util

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FirestoreErrorInfo(
    val operation: String,       // e.g. "update", "get", "set", "query", "commit"
    val path: String,            // e.g. "users/ABC", "merchants", "walletDirectory/phone_639171234567"
    val code: String? = null,    // e.g. "PERMISSION_DENIED", "NOT_FOUND"
    val message: String          // e.g. "PERMISSION_DENIED" or "Not found. The person must have a Delivio account"
) {
    val displayFormattedText: String
        get() = buildString {
            append("$operation $path")
            val codeStr = code?.trim()
            val msgStr = message.trim()
            if (!codeStr.isNullOrEmpty() && (msgStr.isEmpty() || msgStr.equals(codeStr, ignoreCase = true))) {
                append(": $codeStr")
            } else if (!codeStr.isNullOrEmpty() && msgStr.isNotEmpty()) {
                if (msgStr.contains(codeStr, ignoreCase = true)) {
                    append(": $msgStr")
                } else {
                    append(": $codeStr - $msgStr")
                }
            } else if (msgStr.isNotEmpty()) {
                append(": $msgStr")
            }
        }
}

object FirestoreErrorBus {
    private val _currentError = MutableStateFlow<FirestoreErrorInfo?>(null)
    val currentError: StateFlow<FirestoreErrorInfo?> = _currentError.asStateFlow()

    fun emitError(
        operation: String,
        path: String,
        throwable: Throwable,
        customMessage: String? = null
    ) {
        val (code, msg) = if (throwable is FirebaseFirestoreException) {
            val c = throwable.code.name
            val m = customMessage ?: (throwable.message ?: c)
            c to m
        } else {
            null to (customMessage ?: (throwable.message ?: throwable.javaClass.simpleName))
        }

        val currentUser = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }

        if (currentUser == null) {
            android.util.Log.w("FirestoreErrorBus", "Ignoring query failure before login for $operation $path: $msg")
            return
        }

        _currentError.value = FirestoreErrorInfo(
            operation = operation,
            path = path,
            code = code,
            message = msg
        )
    }

    fun emitError(
        operation: String,
        path: String,
        message: String,
        code: String? = null
    ) {
        val currentUser = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }

        if (currentUser == null) {
            android.util.Log.w("FirestoreErrorBus", "Ignoring query failure before login for $operation $path: $message")
            return
        }

        _currentError.value = FirestoreErrorInfo(
            operation = operation,
            path = path,
            code = code,
            message = message
        )
    }

    fun dismiss() {
        _currentError.value = null
    }
}
