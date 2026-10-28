package com.example.data.repository

import android.util.Log
import com.example.data.model.DelivioUser
import com.example.data.model.User
import com.example.util.AuthConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val preferenceRepository: PreferenceRepository,
    private val firestoreService: FirestoreService,
    private val walletRepository: WalletRepository? = null
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

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    private var profileListenerJob: kotlinx.coroutines.Job? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private fun startProfileListener(uid: String) {
        profileListenerJob?.cancel()
        if (uid.isEmpty()) return
        profileListenerJob = scope.launch {
            firestoreService.observeUserProfile(uid).collect { profile ->
                if (profile != null) {
                    val current = _currentUser.value
                    _currentUser.value = (current ?: User(
                        uid = uid,
                        email = profile.email,
                        name = profile.name,
                        phone = profile.phone
                    )).copy(
                        name = profile.name.ifEmpty { current?.name ?: "" },
                        phone = profile.phone,
                        email = profile.email.ifEmpty { current?.email ?: "" },
                        role = profile.role,
                        walletBalance = profile.walletBalance,
                        memberTier = ""
                    )
                }
            }
        }
    }

    fun restoreSession() {
        val auth = firebaseAuth
        val fbUser = auth?.currentUser
        if (fbUser != null) {
            val user = User(
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                name = fbUser.displayName ?: "",
                phone = fbUser.phoneNumber ?: "",
                role = "customer",
                walletBalance = 0.0,
                memberTier = ""
            )
            _currentUser.value = user
            startProfileListener(fbUser.uid)
            scope.launch {
                walletRepository?.upsertWalletDirectory(user.uid, user.name, user.email, user.phone)
            }
        } else if (_currentUser.value == null) {
            // Restore default local session if no Firebase user logged in
            val user = User(
                uid = "guest-customer-uid",
                email = "customer@delivio.com",
                name = "Delivio Customer",
                phone = "",
                role = "customer",
                walletBalance = 0.0,
                memberTier = ""
            )
            _currentUser.value = user
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
            val authResult = try {
                auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            } catch (signInEx: Exception) {
                if (trimmedEmail.equals(AuthConstants.DEMO_EMAIL, ignoreCase = true) && trimmedPassword == AuthConstants.DEMO_PASSWORD) {
                    try {
                        val createResult = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                        val newUser = createResult.user
                        if (newUser != null) {
                            val delivioUser = DelivioUser(
                                uid = newUser.uid,
                                email = trimmedEmail,
                                name = AuthConstants.DEMO_USER_NAME,
                                phone = AuthConstants.DEMO_USER_PHONE,
                                role = "customer",
                                status = "active",
                                walletBalance = 0.0,
                                createdAt = System.currentTimeMillis()
                            )
                            firestoreService.createUserProfile(delivioUser)
                        }
                        createResult
                    } catch (createEx: Exception) {
                        Log.w(tag, "Auto-provision demo account note: ${createEx.message}")
                        throw signInEx
                    }
                } else {
                    throw signInEx
                }
            }
            val fbUser = authResult.user ?: throw IllegalStateException("Authentication failed (null user)")

            // Fetch profile from Firestore
            val profile = firestoreService.getUserProfile(fbUser.uid)
            val user = User(
                uid = fbUser.uid,
                email = fbUser.email ?: trimmedEmail,
                name = profile?.name?.ifEmpty { null } ?: fbUser.displayName ?: trimmedEmail.substringBefore("@"),
                phone = profile?.phone ?: fbUser.phoneNumber ?: "",
                role = profile?.role ?: "customer",
                walletBalance = profile?.walletBalance ?: 0.0
            )

            _currentUser.value = user
            preferenceRepository.setLoggedIn(true)
            startProfileListener(fbUser.uid)
            scope.launch {
                walletRepository?.upsertWalletDirectory(user.uid, user.name, user.email, user.phone)
            }
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
            startProfileListener(fbUser.uid)
            scope.launch {
                walletRepository?.upsertWalletDirectory(user.uid, user.name, user.email, user.phone)
            }
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
    ): Result<Pair<User, String?>> {
        val current = _currentUser.value ?: return Result.failure(IllegalStateException("No user logged in"))
        val trimmedName = name.trim().ifEmpty { current.name }
        val trimmedPhone = phone.trim()
        val oldPhone = current.phone

        // 1. One update on users/{uid} containing ONLY the changed fields among name, phone, and updatedAt
        val changedFields = mutableMapOf<String, Any>()
        if (trimmedName != current.name) {
            changedFields["name"] = trimmedName
        }
        if (trimmedPhone != current.phone) {
            changedFields["phone"] = trimmedPhone
        }
        changedFields["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

        val updateResult = firestoreService.updateUserProfileFields(current.uid, changedFields)
        if (updateResult.isFailure) {
            val err = updateResult.exceptionOrNull() ?: Exception("Failed to update profile")
            return Result.failure(err)
        }

        // Profile save succeeded
        val updated = current.copy(
            name = trimmedName,
            phone = trimmedPhone.ifEmpty { current.phone },
            secondaryPhone = secondaryPhone.trim(),
            gender = gender.trim().ifEmpty { current.gender },
            birthday = birthday.trim().ifEmpty { current.birthday },
            deliveryNotes = deliveryNotes.trim()
        )
        _currentUser.value = updated

        // 2. Run the walletDirectory upsert AFTER the profile save succeeds, in its own try/catch.
        // If it fails (permission denied, or the phone already belongs to another account),
        // still show "Profile saved" plus a small warning "Wallet lookup not updated: <reason>".
        // It must never block or fail the profile save.
        var walletWarning: String? = null
        if (walletRepository != null) {
            try {
                val dirResult = walletRepository.upsertWalletDirectory(
                    uid = current.uid,
                    name = updated.name,
                    email = updated.email,
                    phone = updated.phone,
                    oldPhone = oldPhone
                )
                if (dirResult.isFailure) {
                    val reason = dirResult.exceptionOrNull()?.message ?: "Permission denied or conflict"
                    walletWarning = "Wallet lookup not updated: $reason"
                }
            } catch (e: Exception) {
                val reason = e.message ?: "Permission denied or conflict"
                walletWarning = "Wallet lookup not updated: $reason"
            }
        }

        return Result.success(Pair(updated, walletWarning))
    }

    suspend fun logout() {
        profileListenerJob?.cancel()
        profileListenerJob = null
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error signing out", e)
        }
        _currentUser.value = null
        preferenceRepository.setLoggedIn(false)
    }

    private fun formatAuthError(e: Exception): String {
        if (e is FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password. If you don't have an account yet, switch to 'Sign Up' to create one."
        }
        if (e is FirebaseAuthInvalidUserException) {
            return "No account found with this email. Please switch to 'Sign Up' to register."
        }
        if (e is FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Please log in or reset your password."
        }
        if (e is FirebaseAuthWeakPasswordException) {
            return "Password should be at least 6 characters."
        }
        val msg = e.message ?: "Authentication failed"
        return when {
            msg.contains("ERROR_INVALID_CREDENTIAL", ignoreCase = true) ||
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("credential is incorrect", ignoreCase = true) ||
            msg.contains("malformed", ignoreCase = true) -> "Incorrect email or password. If you don't have an account yet, switch to 'Sign Up' to create one."
            msg.contains("user-not-found", ignoreCase = true) -> "No account found with this email. Please switch to 'Sign Up' to register."
            msg.contains("email-already-in-use", ignoreCase = true) -> "This email is already registered. Please log in."
            msg.contains("weak-password", ignoreCase = true) -> "Password should be at least 6 characters."
            msg.contains("network", ignoreCase = true) -> "Network error. Please check your internet connection."
            msg.contains("too-many-requests", ignoreCase = true) -> "Too many failed attempts. Please try again in a few moments."
            else -> "Authentication failed. Please verify your email and password or sign up."
        }
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
