package com.example.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveLoan
import com.example.data.model.LoanApplication
import com.example.data.model.LoanFormInput
import com.example.data.model.LoanLogic
import com.example.data.model.LoanOffer
import com.example.data.model.LoanPrimaryView
import com.example.data.model.User
import com.example.data.repository.RestaurantRepository
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun LoansScreen(
    restaurantRepository: RestaurantRepository,
    currentUid: String,
    currentUser: User? = null,
    initialAddress: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appDesignerSettings by restaurantRepository.appDesignerSettings.collectAsState()
    val offer by restaurantRepository.service.getLoanOfferFlow(currentUid).collectAsState(initial = null)
    val applications by restaurantRepository.service.getMyLoanApplicationsFlow(currentUid).collectAsState(initial = emptyList())
    val loans by restaurantRepository.service.getMyLoansFlow(currentUid).collectAsState(initial = emptyList())
    val loansVisible = appDesignerSettings.isServiceVisible("loans")

    val scope = rememberCoroutineScope()
    var formInput by remember(currentUid) {
        mutableStateOf(
            LoanFormInput(
                fullName = currentUser?.name.orEmpty(),
                birthDate = currentUser?.birthday.orEmpty(),
                address = initialAddress,
                phone = currentUser?.phone.orEmpty(),
                email = currentUser?.email.orEmpty()
            )
        )
    }
    var isBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var forceApplyForm by remember { mutableStateOf(false) }

    LoansContent(
        offer = if (loansVisible) offer else null,
        applications = if (loansVisible) applications else emptyList(),
        loans = if (loansVisible) loans else emptyList(),
        formInput = formInput,
        onFormChange = {
            formInput = it
            if (actionError != null) actionError = null
        },
        forceApplyForm = forceApplyForm,
        isBusy = isBusy,
        errorMessage = actionError,
        onBack = onBack,
        onSubmitApplication = {
            val validationErr = LoanLogic.validateForm(formInput)
            if (validationErr != null) {
                actionError = validationErr
                return@LoansContent
            }
            isBusy = true
            actionError = null
            scope.launch {
                val res = restaurantRepository.service.submitLoanApplication(
                    uid = currentUid,
                    form = LoanLogic.toSubmissionForm(formInput)
                )
                isBusy = false
                res.onSuccess {
                    forceApplyForm = false
                }.onFailure { err ->
                    actionError = err.message ?: "Unable to submit loan application. Please try again."
                }
            }
        },
        onWithdrawApplication = { applicationId ->
            isBusy = true
            actionError = null
            scope.launch {
                val res = restaurantRepository.service.withdrawLoanApplication(currentUid, applicationId)
                isBusy = false
                res.onFailure { err ->
                    actionError = err.message ?: "Could not withdraw application."
                }
            }
        },
        onAcceptOffer = { applicationId, chosenAmount, chosenTermMonths ->
            isBusy = true
            actionError = null
            scope.launch {
                val res = restaurantRepository.service.acceptLoanApplication(
                    uid = currentUid,
                    applicationId = applicationId,
                    chosenAmount = chosenAmount,
                    chosenTermMonths = chosenTermMonths
                )
                isBusy = false
                res.onFailure { err ->
                    actionError = err.message ?: "Could not accept loan offer."
                }
            }
        },
        onReapplyClick = {
            forceApplyForm = true
            actionError = null
        },
        modifier = modifier
    )
}

@Composable
fun LoansContent(
    offer: LoanOffer?,
    applications: List<LoanApplication>,
    loans: List<ActiveLoan>,
    formInput: LoanFormInput,
    onFormChange: (LoanFormInput) -> Unit,
    forceApplyForm: Boolean = false,
    isBusy: Boolean = false,
    errorMessage: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
    onBack: () -> Unit,
    onSubmitApplication: () -> Unit,
    onWithdrawApplication: (String) -> Unit,
    onAcceptOffer: (String, Double, Int) -> Unit,
    onReapplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryView = remember(offer, applications, forceApplyForm, nowMillis) {
        LoanLogic.resolvePrimaryView(
            offer = offer,
            applications = applications,
            forceApplyForm = forceApplyForm,
            nowMillis = nowMillis
        )
    }

    val acceptedAppReleasedAsLoan = remember(primaryView, loans) {
        primaryView is LoanPrimaryView.AcceptedPendingRelease &&
            loans.any { it.applicationId.isNotBlank() && it.applicationId == primaryView.application.id }
    }
    val hasActiveLoan = remember(loans) {
        loans.any { it.status.equals("active", true) || it.status.equals("overdue", true) }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                color = YellowPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("loans_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnYellow
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Delivio Loans",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (loans.isNotEmpty()) {
                items(loans, key = { it.id }) { loan ->
                    ActiveLoanCard(loan = loan)
                }
            }

            if (!acceptedAppReleasedAsLoan && !hasActiveLoan) {
                item {
                    when (primaryView) {
                        is LoanPrimaryView.NoOffer -> {
                            NoLoanOfferCard(onBack = onBack)
                        }
                        is LoanPrimaryView.InviteAndForm -> {
                            val activeOffer = offer ?: LoanOffer()
                            val cooldownLocked = activeOffer.reapplyAfter != null && nowMillis < activeOffer.reapplyAfter
                            LoanInviteAndFormSection(
                                offer = activeOffer,
                                formInput = formInput,
                                onFormChange = onFormChange,
                                isCooldownLocked = cooldownLocked,
                                cooldownUntilMillis = activeOffer.reapplyAfter,
                                isSubmitting = isBusy,
                                errorMessage = errorMessage,
                                onSubmit = onSubmitApplication
                            )
                        }
                        is LoanPrimaryView.SubmittedUnderReview -> {
                            SubmittedReviewCard(
                                application = primaryView.application,
                                isBusy = isBusy,
                                errorMessage = errorMessage,
                                onWithdraw = { onWithdrawApplication(primaryView.application.id) }
                            )
                        }
                        is LoanPrimaryView.ApprovedSelection -> {
                            ApprovedLoanSelectionCard(
                                application = primaryView.application,
                                offer = offer,
                                isBusy = isBusy,
                                errorMessage = errorMessage,
                                onAccept = { amount, term ->
                                    onAcceptOffer(primaryView.application.id, amount, term)
                                }
                            )
                        }
                        is LoanPrimaryView.AcceptedPendingRelease -> {
                            AcceptedLoanCard(application = primaryView.application)
                        }
                        is LoanPrimaryView.DeclinedCooldown -> {
                            DeclinedLoanCard(
                                view = primaryView,
                                onReapply = onReapplyClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveLoanCard(loan: ActiveLoan) {
    val fmtWhole = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
    val normalizedStatus = loan.status.trim().lowercase(Locale.ROOT)
    val (statusLabel, statusColor) = when (normalizedStatus) {
        "paid" -> "Paid" to SuccessGreen
        "overdue" -> "Overdue" to ErrorRed
        else -> "Active" to SuccessGreen
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_loan_card_${loan.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = statusColor
                        )
                    }
                    Column {
                        Text(
                            text = "Your Delivio Loan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Term: ${LoanLogic.formatTermLabel(loan.termMonths)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = "₱${fmtWhole.format(loan.amount.roundToLong())}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Payments",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadgeOrange.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Coming soon",
                        color = BadgeOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmittedReviewCard(
    application: LoanApplication,
    isBusy: Boolean,
    errorMessage: String?,
    onWithdraw: () -> Unit
) {
    val fmtWhole = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, YellowPrimary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_under_review_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(YellowPrimary.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = TextOnYellow
                    )
                }
                Column {
                    Text(
                        text = "Under review",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Sent ${LoanLogic.formatDateTime(application.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("loan_submitted_date")
                    )
                }
            }

            Text(
                text = "Our Delivio team is reviewing your verification details. Updates appear here automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (application.form.fullName.isNotBlank()) {
                        DetailRow("Applicant", application.form.fullName)
                    }
                    if (application.form.purpose.isNotBlank()) {
                        DetailRow("Purpose", application.form.purpose)
                    }
                    if (application.form.monthlyIncome > 0.0) {
                        DetailRow("Monthly Income", "₱${fmtWhole.format(application.form.monthlyIncome.roundToLong())}")
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onWithdraw,
                enabled = !isBusy,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("loan_withdraw_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Withdraw",
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovedLoanSelectionCard(
    application: LoanApplication,
    offer: LoanOffer?,
    isBusy: Boolean,
    errorMessage: String?,
    onAccept: (Double, Int) -> Unit
) {
    val fmtWhole = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }
    val fmtDecimal = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    val maxApproved = (application.approvedAmount ?: offer?.maxAmount ?: 1000.0).coerceAtLeast(100.0)
    val minBorrow = minOf(500.0, maxApproved).coerceAtLeast(1.0)
    val availableTerms = remember(application.termsOffered, offer?.termsMonths) {
        application.termsOffered
            .ifEmpty { offer?.termsMonths.orEmpty() }
            .ifEmpty { listOf(1, 3, 6, 12) }
    }

    var selectedAmount by remember(application.id, maxApproved) {
        mutableDoubleStateOf(maxApproved.coerceIn(minBorrow, maxApproved))
    }
    var selectedTerm by remember(application.id, availableTerms) {
        mutableIntStateOf(availableTerms.first())
    }

    val effectiveInterestNote = application.interestNote.ifBlank { offer?.interestNote.orEmpty() }
    val effectiveRate = application.interestRatePercent ?: offer?.interestRatePercent
    val estimate = remember(selectedAmount, selectedTerm, effectiveRate, effectiveInterestNote) {
        LoanLogic.calculatePaymentEstimate(
            amount = selectedAmount,
            termMonths = selectedTerm,
            explicitRate = effectiveRate,
            interestNote = effectiveInterestNote
        )
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_approved_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Celebration,
                        contentDescription = null,
                        tint = SuccessGreen
                    )
                }
                Text(
                    text = "Congratulations! You can borrow up to ₱${fmtWhole.format(maxApproved.roundToLong())}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (application.adminNote.isNotBlank()) {
                Text(
                    text = application.adminNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Amount",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₱${fmtWhole.format(selectedAmount.roundToLong())}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("loan_chosen_amount_label")
                    )
                }

                if (maxApproved > minBorrow) {
                    Slider(
                        value = selectedAmount.toFloat().coerceIn(minBorrow.toFloat(), maxApproved.toFloat()),
                        onValueChange = { raw ->
                            val step = if (maxApproved >= 2000.0) 100.0 else 50.0
                            val snapped = ((raw / step).roundToInt() * step)
                                .coerceIn(minBorrow, maxApproved)
                            selectedAmount = snapped
                        },
                        valueRange = minBorrow.toFloat()..maxApproved.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = YellowPrimary,
                            activeTrackColor = YellowPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_amount_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "₱${fmtWhole.format(minBorrow.roundToLong())}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₱${fmtWhole.format(maxApproved.roundToLong())}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Term Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select Term",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTerms.forEach { months ->
                        val isSelected = selectedTerm == months
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (isSelected) BorderStroke(1.5.dp, TextOnYellow.copy(alpha = 0.4f)) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTerm = months }
                                .testTag("loan_term_$months")
                        ) {
                            Text(
                                text = LoanLogic.formatTermLabel(months),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // Display-only Estimate Summary
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loan_estimate_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailRow(
                        label = "Estimated Monthly Payment",
                        value = "₱${fmtDecimal.format(estimate.estimatedMonthly)} / mo"
                    )
                    DetailRow(
                        label = "Estimated Total Payment",
                        value = "₱${fmtDecimal.format(estimate.estimatedTotal)}"
                    )
                    if (estimate.monthlyRatePercent != null) {
                        DetailRow(
                            label = "Rate",
                            value = "${estimate.monthlyRatePercent}% / month"
                        )
                    }
                    if (effectiveInterestNote.isNotBlank()) {
                        Text(
                            text = effectiveInterestNote,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        text = "Estimate shown for display only.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = { onAccept(selectedAmount, selectedTerm) },
                enabled = !isBusy && selectedAmount > 0.0 && selectedAmount <= maxApproved,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("loan_accept_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = TextOnYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Accept",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun AcceptedLoanCard(application: LoanApplication) {
    val fmtWhole = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 0 }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.09f)),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_accepted_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Accepted. Delivio will contact you to release the funds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    application.chosenAmount?.let { amt ->
                        DetailRow("Chosen Amount", "₱${fmtWhole.format(amt.roundToLong())}")
                    }
                    application.chosenTermMonths?.let { term ->
                        DetailRow("Chosen Term", LoanLogic.formatTermLabel(term))
                    }
                    if (application.acceptedAt != null) {
                        DetailRow("Accepted", LoanLogic.formatDateTime(application.acceptedAt))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeclinedLoanCard(
    view: LoanPrimaryView.DeclinedCooldown,
    onReapply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_declined_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (view.isLocked) Icons.Default.Lock else Icons.Default.Info,
                        contentDescription = null,
                        tint = ErrorRed
                    )
                }
                Text(
                    text = "You're not eligible yet to use Delivio Loans",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (view.declineMessage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRed.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = view.declineMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val guidanceText = if (view.reapplyAfterMillis != null) {
                "You can try applying again on ${LoanLogic.formatDate(view.reapplyAfterMillis)}. To increase your chances, make sure to shop frequently and maintain good payment habits."
            } else {
                "To increase your chances, make sure to shop frequently and maintain good payment habits."
            }
            Text(
                text = guidanceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onReapply,
                enabled = !view.isLocked,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("loan_reapply_button")
            ) {
                Text(
                    text = if (view.isLocked && view.reapplyAfterMillis != null) {
                        "Locked until ${LoanLogic.formatDate(view.reapplyAfterMillis)}"
                    } else {
                        "Apply Again"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun NoLoanOfferCard(onBack: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_no_offer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Delivio Loans is currently unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This service is available by invitation only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Back", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
