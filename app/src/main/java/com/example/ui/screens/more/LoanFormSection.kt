package com.example.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LoanFormInput
import com.example.data.model.LoanLogic
import com.example.data.model.LoanOffer
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoanInviteAndFormSection(
    offer: LoanOffer,
    formInput: LoanFormInput,
    onFormChange: (LoanFormInput) -> Unit,
    isCooldownLocked: Boolean,
    cooldownUntilMillis: Long?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fmtWhole = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Invite Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = YellowPrimary.copy(alpha = 0.16f)),
            border = BorderStroke(1.dp, YellowPrimary.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("loan_invite_banner")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(YellowPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = TextOnYellow
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "You're invited to Delivio Loans",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (offer.maxAmount > 0.0) {
                            Text(
                                text = "Borrow up to ₱${fmtWhole.format(offer.maxAmount.roundToLong())}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }

                val termsToShow = offer.termsMonths.ifEmpty { listOf(1, 3, 6, 12) }
                Text(
                    text = "Available terms: ${termsToShow.joinToString(", ") { LoanLogic.formatTermLabel(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (offer.interestNote.isNotBlank()) {
                    Text(
                        text = offer.interestNote,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (isCooldownLocked && cooldownUntilMillis != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ErrorRed.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ErrorRed)
                    Text(
                        text = "You can try applying again on ${LoanLogic.formatDate(cooldownUntilMillis)}.",
                        color = ErrorRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Form-only verification card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Verification Form",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "Form-only verification. No photo upload or payment required right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Personal Information
                SectionHeader("Personal Details")
                LoanTextField(
                    value = formInput.fullName,
                    onValueChange = { onFormChange(formInput.copy(fullName = it)) },
                    label = "Full Name",
                    placeholder = "Juan Dela Cruz",
                    tag = "loan_full_name_input"
                )
                LoanTextField(
                    value = formInput.birthDate,
                    onValueChange = { onFormChange(formInput.copy(birthDate = it)) },
                    label = "Birth Date",
                    placeholder = "YYYY-MM-DD (e.g. 1995-04-18)",
                    tag = "loan_birth_date_input"
                )
                LoanTextField(
                    value = formInput.address,
                    onValueChange = { onFormChange(formInput.copy(address = it)) },
                    label = "Current Address",
                    placeholder = "Street, Barangay, City",
                    singleLine = false,
                    tag = "loan_address_input"
                )
                LoanTextField(
                    value = formInput.phone,
                    onValueChange = { onFormChange(formInput.copy(phone = it)) },
                    label = "Mobile Number",
                    placeholder = "09XX XXX XXXX",
                    keyboardType = KeyboardType.Phone,
                    tag = "loan_phone_input"
                )
                LoanTextField(
                    value = formInput.email,
                    onValueChange = { onFormChange(formInput.copy(email = it)) },
                    label = "Email Address",
                    placeholder = "name@example.com",
                    keyboardType = KeyboardType.Email,
                    tag = "loan_email_input"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Government ID
                SectionHeader("Identity Details")
                Text(
                    text = "ID Type",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LoanLogic.DEFAULT_ID_TYPES.forEach { idType ->
                        SelectableLoanChip(
                            label = idType,
                            selected = formInput.idType == idType,
                            onClick = { onFormChange(formInput.copy(idType = idType)) }
                        )
                    }
                }
                LoanTextField(
                    value = formInput.idNumber,
                    onValueChange = { onFormChange(formInput.copy(idNumber = it)) },
                    label = "ID Number",
                    placeholder = "Enter ID number",
                    tag = "loan_id_number_input"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Employment & Income
                SectionHeader("Employment & Purpose")
                Text(
                    text = "Employment Status",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LoanLogic.DEFAULT_EMPLOYMENT_STATUSES.forEach { emp ->
                        SelectableLoanChip(
                            label = emp,
                            selected = formInput.employment == emp,
                            onClick = { onFormChange(formInput.copy(employment = emp)) }
                        )
                    }
                }
                LoanTextField(
                    value = formInput.employer,
                    onValueChange = { onFormChange(formInput.copy(employer = it)) },
                    label = "Employer or Business Name",
                    placeholder = "Company or store name",
                    tag = "loan_employer_input"
                )
                LoanTextField(
                    value = formInput.monthlyIncomeText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '.' }
                        onFormChange(formInput.copy(monthlyIncomeText = cleaned))
                    },
                    label = "Monthly Income (₱)",
                    placeholder = "25000",
                    keyboardType = KeyboardType.Number,
                    tag = "loan_income_input"
                )
                LoanTextField(
                    value = formInput.purpose,
                    onValueChange = { onFormChange(formInput.copy(purpose = it)) },
                    label = "Loan Purpose",
                    placeholder = "e.g. Bills, Emergency, Business",
                    tag = "loan_purpose_input"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Two Character References
                SectionHeader("Character Reference 1")
                LoanTextField(
                    value = formInput.ref1Name,
                    onValueChange = { onFormChange(formInput.copy(ref1Name = it)) },
                    label = "Reference 1 Full Name",
                    placeholder = "Full name",
                    tag = "loan_ref1_name_input"
                )
                LoanTextField(
                    value = formInput.ref1Phone,
                    onValueChange = { onFormChange(formInput.copy(ref1Phone = it)) },
                    label = "Reference 1 Phone",
                    placeholder = "09XX XXX XXXX",
                    keyboardType = KeyboardType.Phone,
                    tag = "loan_ref1_phone_input"
                )
                LoanTextField(
                    value = formInput.ref1Relation,
                    onValueChange = { onFormChange(formInput.copy(ref1Relation = it)) },
                    label = "Reference 1 Relationship",
                    placeholder = "e.g. Sibling, Coworker, Parent",
                    tag = "loan_ref1_relation_input"
                )

                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader("Character Reference 2")
                LoanTextField(
                    value = formInput.ref2Name,
                    onValueChange = { onFormChange(formInput.copy(ref2Name = it)) },
                    label = "Reference 2 Full Name",
                    placeholder = "Full name",
                    tag = "loan_ref2_name_input"
                )
                LoanTextField(
                    value = formInput.ref2Phone,
                    onValueChange = { onFormChange(formInput.copy(ref2Phone = it)) },
                    label = "Reference 2 Phone",
                    placeholder = "09XX XXX XXXX",
                    keyboardType = KeyboardType.Phone,
                    tag = "loan_ref2_phone_input"
                )
                LoanTextField(
                    value = formInput.ref2Relation,
                    onValueChange = { onFormChange(formInput.copy(ref2Relation = it)) },
                    label = "Reference 2 Relationship",
                    placeholder = "e.g. Friend, Relative, Manager",
                    tag = "loan_ref2_relation_input"
                )

                // Consent Checkbox
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onFormChange(formInput.copy(consent = !formInput.consent)) }
                        .testTag("loan_consent_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = formInput.consent,
                            onCheckedChange = { checked ->
                                onFormChange(formInput.copy(consent = checked))
                            },
                            colors = CheckboxDefaults.colors(checkedColor = YellowPrimary, checkmarkColor = TextOnYellow)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = LoanLogic.CONSENT_LABEL,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRed.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_form_error")
                    ) {
                        Text(
                            text = errorMessage,
                            color = ErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = onSubmit,
                    enabled = !isSubmitting && !isCooldownLocked,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = TextOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("loan_submit_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = TextOnYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Submit Application",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SelectableLoanChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (selected) BorderStroke(1.5.dp, TextOnYellow.copy(alpha = 0.35f)) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun LoanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    tag: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    )
}
