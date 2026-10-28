package com.example.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.util.AuthConstants
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by authViewModel.uiState.collectAsState()

    if (uiState.isLoggedIn) {
        onLoginSuccess()
    }

    var showPasswordConfirm by remember { mutableStateOf(false) }

    // Forgot Password Dialog
    if (uiState.isForgotPassDialogOpen) {
        AlertDialog(
            onDismissRequest = { authViewModel.setForgotPassDialogOpen(false) },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your registered email address and we'll send you a link to reset your password.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.resetEmail,
                        onValueChange = { authViewModel.onResetEmailChanged(it) },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.resetSuccessMessage != null) {
                        Text(
                            text = uiState.resetSuccessMessage!!,
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = ErrorRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { authViewModel.sendPasswordReset() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = TextOnYellow, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send Reset Link")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { authViewModel.setForgotPassDialogOpen(false) }) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Brand Logo
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.delivio),
                    contentDescription = "Delivio Logo",
                    modifier = Modifier.size(76.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Delivio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (uiState.isSignUpMode) "Create your Customer Account" else "Fast food, groceries, parcels & pabili delivered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Mode Selector (Log In / Sign Up Tabs)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!uiState.isSignUpMode) YellowPrimary else Color.Transparent)
                            .clickable { authViewModel.setSignUpMode(false) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (!uiState.isSignUpMode) TextOnYellow else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (uiState.isSignUpMode) YellowPrimary else Color.Transparent)
                            .clickable { authViewModel.setSignUpMode(true) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign Up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (uiState.isSignUpMode) TextOnYellow else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!uiState.isSignUpMode) {
                // ==================== LOGIN FORM ====================
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { authViewModel.onEmailChanged(it) },
                    label = { Text("Email Address") },
                    placeholder = { Text("your.email@example.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = "Email", tint = YellowPrimary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { authViewModel.onPasswordChanged(it) },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = "Password", tint = YellowPrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { authViewModel.togglePasswordVisibility() }, modifier = Modifier.testTag("toggle_password_visibility")) {
                            Icon(
                                imageVector = if (uiState.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { authViewModel.login() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YellowPrimary,
                        focusedLabelColor = YellowPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                // Forgot Password link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Forgot Password?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = YellowPrimary,
                        modifier = Modifier
                            .clickable {
                                authViewModel.onResetEmailChanged(uiState.email)
                                authViewModel.setForgotPassDialogOpen(true)
                            }
                            .padding(4.dp)
                            .testTag("forgot_password_button")
                    )
                }
            } else {
                // ==================== SIGN UP FORM ====================
                // Full Name
                OutlinedTextField(
                    value = uiState.signUpName,
                    onValueChange = { authViewModel.onSignUpFieldChanged(name = it) },
                    label = { Text("Full Name *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowPrimary, focusedLabelColor = YellowPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("signup_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone
                OutlinedTextField(
                    value = uiState.signUpPhone,
                    onValueChange = { authViewModel.onSignUpFieldChanged(phone = it) },
                    label = { Text("Mobile Phone Number *") },
                    placeholder = { Text("+63 917 123 4567") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowPrimary, focusedLabelColor = YellowPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("signup_phone_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Email
                OutlinedTextField(
                    value = uiState.signUpEmail,
                    onValueChange = { authViewModel.onSignUpFieldChanged(email = it) },
                    label = { Text("Email Address *") },
                    placeholder = { Text("you@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = YellowPrimary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowPrimary, focusedLabelColor = YellowPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("signup_email_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Password
                OutlinedTextField(
                    value = uiState.signUpPassword,
                    onValueChange = { authViewModel.onSignUpFieldChanged(password = it) },
                    label = { Text("Password (min 6 characters) *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = YellowPrimary) },
                    trailingIcon = {
                        IconButton(onClick = { authViewModel.togglePasswordVisibility() }) {
                            Icon(
                                imageVector = if (uiState.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowPrimary, focusedLabelColor = YellowPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("signup_password_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm Password
                OutlinedTextField(
                    value = uiState.signUpConfirmPassword,
                    onValueChange = { authViewModel.onSignUpFieldChanged(confirmPassword = it) },
                    label = { Text("Confirm Password *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = YellowPrimary) },
                    visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { authViewModel.signUp() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowPrimary, focusedLabelColor = YellowPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("signup_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Terms of Service Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            authViewModel.onSignUpFieldChanged(termsAccepted = !uiState.termsAccepted)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.termsAccepted,
                        onCheckedChange = { authViewModel.onSignUpFieldChanged(termsAccepted = it) },
                        colors = CheckboxDefaults.colors(checkedColor = YellowPrimary, checkmarkColor = TextOnYellow),
                        modifier = Modifier.testTag("terms_checkbox")
                    )
                    Text(
                        text = "I agree to Delivio's Terms of Service and Privacy Policy.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error Message Display
            if (uiState.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = uiState.errorMessage!!,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("auth_error_message")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Action Button (Log In or Sign Up)
            Button(
                onClick = {
                    if (uiState.isSignUpMode) {
                        authViewModel.signUp()
                    } else {
                        authViewModel.login()
                    }
                },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(if (uiState.isSignUpMode) "signup_button" else "login_button")
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = TextOnYellow,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (uiState.isSignUpMode) "Create Delivio Account" else "Log In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle mode footnote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isSignUpMode) "Already have an account? " else "Don't have an account? ",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (uiState.isSignUpMode) "Log In" else "Sign Up",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = YellowPrimary,
                    modifier = Modifier
                        .clickable { authViewModel.setSignUpMode(!uiState.isSignUpMode) }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
