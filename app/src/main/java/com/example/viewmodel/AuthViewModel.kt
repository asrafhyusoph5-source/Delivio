package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    // Sign Up fields
    val signUpName: String = "",
    val signUpPhone: String = "",
    val signUpEmail: String = "",
    val signUpPassword: String = "",
    val signUpConfirmPassword: String = "",
    val termsAccepted: Boolean = false,
    // Forgot Password
    val resetEmail: String = "",
    val resetSuccessMessage: String? = null,
    val isForgotPassDialogOpen: Boolean = false,
    val isSignUpMode: Boolean = false,
    // Common
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceRepository.isLoggedInFlow.collect { loggedIn ->
                if (loggedIn) {
                    authRepository.restoreSession()
                }
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = loggedIn,
                    currentUser = authRepository.currentUser.value
                )
            }
        }
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isLoggedIn = user != null
                )
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    fun setSignUpMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isSignUpMode = enabled, errorMessage = null)
    }

    fun onSignUpFieldChanged(
        name: String = _uiState.value.signUpName,
        phone: String = _uiState.value.signUpPhone,
        email: String = _uiState.value.signUpEmail,
        password: String = _uiState.value.signUpPassword,
        confirmPassword: String = _uiState.value.signUpConfirmPassword,
        termsAccepted: Boolean = _uiState.value.termsAccepted
    ) {
        _uiState.value = _uiState.value.copy(
            signUpName = name,
            signUpPhone = phone,
            signUpEmail = email,
            signUpPassword = password,
            signUpConfirmPassword = confirmPassword,
            termsAccepted = termsAccepted,
            errorMessage = null
        )
    }

    fun setForgotPassDialogOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(
            isForgotPassDialogOpen = open,
            resetSuccessMessage = null,
            errorMessage = null
        )
    }

    fun onResetEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(resetEmail = email, errorMessage = null)
    }

    fun login() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            result.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    currentUser = user,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Invalid email or password"
                )
            }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (state.signUpPassword != state.signUpConfirmPassword) {
            _uiState.value = state.copy(errorMessage = "Passwords do not match")
            return
        }
        if (!state.termsAccepted) {
            _uiState.value = state.copy(errorMessage = "Please accept the Terms of Service & Privacy Policy")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.signUp(
                name = state.signUpName,
                phone = state.signUpPhone,
                email = state.signUpEmail,
                password = state.signUpPassword
            )
            result.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    currentUser = user,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Sign up failed"
                )
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.resetEmail.ifEmpty { _uiState.value.email }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resetSuccessMessage = "Password reset instructions sent to $email. Please check your inbox."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Failed to send reset email"
                )
            }
        }
    }

    fun updateProfile(
        name: String,
        phone: String,
        secondaryPhone: String = "",
        gender: String = "",
        birthday: String = "",
        deliveryNotes: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = authRepository.updateProfile(
                name = name,
                phone = phone,
                secondaryPhone = secondaryPhone,
                gender = gender,
                birthday = birthday,
                deliveryNotes = deliveryNotes
            )
            result.onSuccess {
                _uiState.value = _uiState.value.copy(currentUser = it)
                onSuccess()
            }.onFailure {
                onError(it.message ?: "Failed to update profile")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
        }
    }
}
