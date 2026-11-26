package com.example.ui.screens.jobs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobPost
import com.example.data.model.JobProfile
import com.example.data.model.User
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary

const val APPLY_CONSENT_TEXT =
    "Your name, phone, email, and message will be visible to the employer and the admin only"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobApplySheet(
    job: JobPost,
    currentUser: User?,
    jobProfile: JobProfile?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        phone: String,
        email: String,
        message: String,
        skills: List<String>,
        experience: String,
        resumeUrl: String,
        saveForNextTime: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        JobApplySheetContent(
            job = job,
            currentUser = currentUser,
            jobProfile = jobProfile,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
            onSubmit = onSubmit
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobApplySheetContent(
    job: JobPost,
    currentUser: User?,
    jobProfile: JobProfile?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (
        name: String,
        phone: String,
        email: String,
        message: String,
        skills: List<String>,
        experience: String,
        resumeUrl: String,
        saveForNextTime: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialName = jobProfile?.name?.ifBlank { null } ?: currentUser?.name.orEmpty()
    val initialPhone = jobProfile?.phone?.ifBlank { null } ?: currentUser?.phone.orEmpty()
    val initialEmail = jobProfile?.email?.ifBlank { null } ?: currentUser?.email.orEmpty()

    var name by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var message by remember { mutableStateOf("") }
    val skills = remember(jobProfile) {
        mutableStateListOf<String>().apply {
            jobProfile?.skills?.forEach { add(it) }
        }
    }
    var skillInput by remember { mutableStateOf("") }
    var experience by remember(jobProfile) { mutableStateOf(jobProfile?.experience.orEmpty()) }
    var resumeUrl by remember(jobProfile) { mutableStateOf(jobProfile?.resumeUrl.orEmpty()) }
    var consentAccepted by remember { mutableStateOf(false) }
    var saveForNextTime by remember { mutableStateOf(true) }

    fun addSkill() {
        val clean = skillInput.trim()
        if (clean.isNotEmpty() && skills.none { it.equals(clean, ignoreCase = true) }) {
            skills.add(clean)
        }
        skillInput = ""
    }

    val isFormReady = name.isNotBlank() &&
        phone.isNotBlank() &&
        email.isNotBlank() &&
        message.isNotBlank() &&
        consentAccepted &&
        !isSubmitting

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Apply for ${job.title}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = job.companyName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Privacy notice banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(18.dp))
                Text(
                    text = APPLY_CONSENT_TEXT,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name *") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_name_input")
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_phone_input")
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_email_input")
        )

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Short Message / Cover Note *") },
            placeholder = { Text("Tell the employer why you're a great fit...") },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_message_input")
        )

        // Skills chip input
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = skillInput,
                    onValueChange = { skillInput = it },
                    label = { Text("Skills (add chip)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addSkill() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_skill_input")
                )
                IconButton(
                    onClick = { addSkill() },
                    enabled = skillInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("apply_add_skill_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add skill")
                }
            }
            if (skills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    skills.forEach { skill ->
                        InputChip(
                            selected = false,
                            onClick = { skills.remove(skill) },
                            label = { Text(skill, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = YellowPrimary.copy(alpha = 0.16f)
                            )
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = experience,
            onValueChange = { experience = it },
            label = { Text("Experience (optional)") },
            placeholder = { Text("e.g. 2 years customer service / delivery") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_experience_input")
        )

        OutlinedTextField(
            value = resumeUrl,
            onValueChange = { resumeUrl = it },
            label = { Text("Resume Link (optional URL)") },
            placeholder = { Text("https://...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_resume_input")
        )

        // Required privacy consent checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { consentAccepted = !consentAccepted }
                .padding(vertical = 4.dp)
                .testTag("apply_consent_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = consentAccepted,
                onCheckedChange = { consentAccepted = it },
                colors = CheckboxDefaults.colors(checkedColor = YellowPrimary, checkmarkColor = TextOnYellow),
                modifier = Modifier.testTag("apply_consent_checkbox")
            )
            Text(
                text = APPLY_CONSENT_TEXT,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        // Save these details for next time
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { saveForNextTime = !saveForNextTime }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = saveForNextTime,
                onCheckedChange = { saveForNextTime = it },
                colors = CheckboxDefaults.colors(checkedColor = YellowPrimary, checkmarkColor = TextOnYellow),
                modifier = Modifier.testTag("apply_save_profile_checkbox")
            )
            Text(
                text = "Save these details for next time",
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = ErrorRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("apply_error_text")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val finalSkills = skills.toMutableList()
                val pendingSkill = skillInput.trim()
                if (pendingSkill.isNotEmpty() && finalSkills.none { it.equals(pendingSkill, ignoreCase = true) }) {
                    finalSkills.add(pendingSkill)
                }
                onSubmit(name, phone, email, message, finalSkills, experience, resumeUrl, saveForNextTime)
            },
            enabled = isFormReady,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = YellowPrimary,
                contentColor = TextOnYellow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("apply_submit_button")
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnYellow, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending Application...", fontWeight = FontWeight.Bold)
            } else {
                Text("Submit Application", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}
