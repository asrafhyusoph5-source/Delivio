package com.example.data.repository

import android.util.Log
import com.example.data.model.DelivioUser
import com.example.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val preferenceRepository: PreferenceRepository,
    private val firestoreService: FirestoreService
) {
    private val tag = "DelivioAuth"

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "FirebaseAuth not initialized: ${e.message}")
            null
        }
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    fun restoreSession() {
        val auth = firebaseAuth
        val fbUser = auth?.currentUser
        if (fbUser != null) {
            _currentUser.value = User(
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                name = fbUser.displayName ?: "Delivio Customer",
                phone = fbUser.phoneNumber ?: "",
                role = "customer",
                walletBalance = 0.0,
                memberTier = "Delivio Gold Member"
            )
        } else if (_currentUser.value == null) {
            // Restore default local session if no Firebase user logged in
            _currentUser.value = User(
                uid = "guest-customer-uid",
                email = "customer@delivio.com",
                name = "Delivio Customer",
                phone = "+63 917 123 4567",
                role = "customer",
                walletBalance = 0.0,
                memberTier = "Delivio Gold Member"
            )
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email and password"))
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }

        val auth = firebaseAuth
        if (auth == null) {
            // Offline/Sandbox fallback
            val user = User(
                uid = "cust-${System.currentTimeMillis() % 100000}",
                email = trimmedEmail,
                name = trimmedEmail.substringBefore("@").replace(".", " ").capitalizeWords(),
                phone = "+63 917 123 4567",
                role = "customer",
                walletBalance = 0.0
            )
            _currentUser.value = user
            preferenceRepository.setLoggedIn(true)
            return Result.success(user)
        }

        return try {
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            val fbUser = authResult.user ?: throw IllegalStateException("Authentication failed (null user)")

            // Fetch profile from Firestore
            val profile = firestoreService.getUserProfile(fbUser.uid)
            val user = User(
                uid = fbUser.uid,
                email = fbUser.email ?: trimmedEmail,
                name = profile?.name?.ifEmpty { null } ?: fbUser.displayName ?: trimmedEmail.substringBefore("@"),
                phone = profile?.phone?.ifEmpty { null } ?: "+63 917 123 4567",
                role = profile?.role ?: "customer",
                walletBalance = profile?.walletBalance ?: 0.0
            )

            _currentUser.value = user
            preferenceRepository.setLoggedIn(true)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "Login error", e)
            Result.failure(IllegalArgumentException(formatAuthError(e)))
        }
    }

    suspend fun signUp(
        name: String,
        phone: String,
        email: String,
        password: String
    ): Result<User> {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Full name is required"))
        }
        if (trimmedPhone.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number is required"))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (trimmedPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val auth = firebaseAuth
        if (auth == null) {
            // Local fallback
            val user = User(
                uid = "cust-${System.currentTimeMillis() % 100000}",
                email = trimmedEmail,
                name = trimmedName,
                phone = trimmedPhone,
                role = "customer",
                walletBalance = 0.0
            )
            _currentUser.value = user
            preferenceRepository.setLoggedIn(true)
            return Result.success(user)
        }

        return try {
            val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            val fbUser = authResult.user ?: throw IllegalStateException("Registration failed")

            // Create users/{uid} document with required fields:
            // role = "customer", status = "active", walletBalance = 0, createdAt
            val delivioUser = DelivioUser(
                uid = fbUser.uid,
                email = trimmedEmail,
                name = trimmedName,
                phone = trimmedPhone,
                role = "customer",
                status = "active",
                walletBalance = 0.0,
                createdAt = System.currentTimeMillis()
            )
            firestoreService.createUserProfile(delivioUser)

            val user = User(
                uid = fbUser.uid,
                email = trimmedEmail,
                name = trimmedName,
                phone = trimmedPhone,
                role = "customer",
                walletBalance = 0.0
            )

            _currentUser.value = user
            preferenceRepository.setLoggedIn(true)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(tag, "Sign up error", e)
            Result.failure(IllegalArgumentException(formatAuthError(e)))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address for reset"))
        }

        val auth = firebaseAuth ?: return Result.success(Unit)
        return try {
            auth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Password reset error", e)
            Result.failure(IllegalArgumentException(formatAuthError(e)))
        }
    }

    suspend fun updateProfile(
        name: String,
        phone: String,
        secondaryPhone: String = "",
        gender: String = "",
        birthday: String = "",
        deliveryNotes: String = ""
    ): Result<User> {
        val current = _currentUser.value ?: return Result.failure(IllegalStateException("No user logged in"))
        val updated = current.copy(
            name = name.trim().ifEmpty { current.name },
            phone = phone.trim().ifEmpty { current.phone },
            secondaryPhone = secondaryPhone.trim(),
            gender = gender.trim().ifEmpty { current.gender },
            birthday = birthday.trim().ifEmpty { current.birthday },
            deliveryNotes = deliveryNotes.trim()
            // Email is strictly read-only and preserved
        )

        _currentUser.value = updated
        // Also persist to Firestore
        firestoreService.updateUserProfile(current.uid, updated.name, updated.phone)
        return Result.success(updated)
    }

    suspend fun logout() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error signing out", e)
        }
        _currentUser.value = null
        preferenceRepository.setLoggedIn(false)
    }

    private fun formatAuthError(e: Exception): String {
        val msg = e.message ?: "Authentication failed"
        return when {
            msg.contains("ERROR_INVALID_CREDENTIAL", ignoreCase = true) ||
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) -> "Invalid email or password. Please try again."
            msg.contains("user-not-found", ignoreCase = true) -> "No account found with this email. Please sign up."
            msg.contains("email-already-in-use", ignoreCase = true) -> "This email is already registered. Please log in."
            msg.contains("weak-password", ignoreCase = true) -> "Password should be at least 6 characters."
            msg.contains("network", ignoreCase = true) -> "Network error. Please check your internet connection."
            else -> msg
        }
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
