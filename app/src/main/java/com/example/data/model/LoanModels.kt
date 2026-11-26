package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

data class LoanReference(
    val name: String = "",
    val phone: String = "",
    val relation: String = ""
)

data class LoanApplicationForm(
    val fullName: String = "",
    val birthDate: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val employment: String = "",
    val employer: String = "",
    val monthlyIncome: Double = 0.0,
    val purpose: String = "",
    val references: List<LoanReference> = emptyList(),
    val consent: Boolean = false
)

/**
 * Editable UI form state before submission.
 */
data class LoanFormInput(
    val fullName: String = "",
    val birthDate: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val idType: String = "National ID (PhilSys)",
    val idNumber: String = "",
    val employment: String = "Employed",
    val employer: String = "",
    val monthlyIncomeText: String = "",
    val purpose: String = "",
    val ref1Name: String = "",
    val ref1Phone: String = "",
    val ref1Relation: String = "",
    val ref2Name: String = "",
    val ref2Phone: String = "",
    val ref2Relation: String = "",
    val consent: Boolean = false
)

/**
 * Customer loan offer stored at /loanOffers/{customerUid}.
 */
data class LoanOffer(
    val uid: String = "",
    val enabled: Boolean = false,
    val title: String = "Delivio Cash Loan",
    val maxAmount: Double = 0.0,
    val minAmount: Double = 0.0,
    val termsMonths: List<Int> = listOf(1, 3, 6, 12),
    val interestNote: String = "",
    val interestRatePercent: Double? = null,
    val reapplyAfter: Long? = null,
    val declineMessage: String = "",
    val updatedAt: Long? = null
) {
    val note: String
        get() = interestNote
}

/**
 * Customer loan application stored at /loanApplications/{customerUid}_{timestampMs}.
 */
data class LoanApplication(
    val id: String = "",
    val customerId: String = "",
    val status: String = "submitted", // submitted | approved | declined | accepted | withdrawn
    val form: LoanApplicationForm = LoanApplicationForm(),
    val approvedAmount: Double? = null,
    val termsOffered: List<Int> = emptyList(),
    val chosenAmount: Double? = null,
    val chosenTermMonths: Int? = null,
    val reapplyAfter: Long? = null,
    val adminNote: String = "",
    val interestNote: String = "",
    val interestRatePercent: Double? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val acceptedAt: Long? = null
)

/**
 * Active/historical loan stored at /loans/{id}.
 */
data class ActiveLoan(
    val id: String = "",
    val customerId: String = "",
    val applicationId: String = "",
    val amount: Double = 0.0,
    val termMonths: Int = 1,
    val status: String = "active", // active | paid | overdue
    val createdAt: Long = 0L
)

data class LoanPaymentEstimate(
    val principal: Double,
    val termMonths: Int,
    val monthlyRatePercent: Double?,
    val estimatedInterest: Double,
    val estimatedTotal: Double,
    val estimatedMonthly: Double
)

sealed class LoanPrimaryView {
    data object NoOffer : LoanPrimaryView()
    data object InviteAndForm : LoanPrimaryView()
    data class SubmittedUnderReview(val application: LoanApplication) : LoanPrimaryView()
    data class ApprovedSelection(val application: LoanApplication) : LoanPrimaryView()
    data class AcceptedPendingRelease(val application: LoanApplication) : LoanPrimaryView()
    data class DeclinedCooldown(
        val application: LoanApplication,
        val reapplyAfterMillis: Long?,
        val declineMessage: String,
        val isLocked: Boolean
    ) : LoanPrimaryView()
}

object LoanLogic {
    val DEFAULT_ID_TYPES = listOf(
        "National ID (PhilSys)",
        "Driver's License",
        "Passport",
        "UMID",
        "SSS / GSIS",
        "Postal ID",
        "Voter's ID"
    )

    val DEFAULT_EMPLOYMENT_STATUSES = listOf(
        "Employed",
        "Self-Employed",
        "Business Owner",
        "Freelancer",
        "Part-Time",
        "Other"
    )

    const val CONSENT_LABEL = "My details will be seen only by Delivio staff"

    private val rateRegex = Regex("""(\d+(?:\.\d+)?)\s*%""")

    fun resolveMonthlyRatePercent(explicitRate: Double?, interestNote: String): Double? {
        if (explicitRate != null && explicitRate >= 0.0) return explicitRate
        val match = rateRegex.find(interestNote) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()?.takeIf { it >= 0.0 }
    }

    fun calculatePaymentEstimate(
        amount: Double,
        termMonths: Int,
        explicitRate: Double?,
        interestNote: String
    ): LoanPaymentEstimate {
        val safeAmount = amount.coerceAtLeast(0.0)
        val safeTerm = termMonths.coerceAtLeast(1)
        val rate = resolveMonthlyRatePercent(explicitRate, interestNote)
        val interest = if (rate != null) {
            ((safeAmount * (rate / 100.0) * safeTerm) * 100.0).roundToLong() / 100.0
        } else {
            0.0
        }
        val total = ((safeAmount + interest) * 100.0).roundToLong() / 100.0
        val monthly = ((total / safeTerm) * 100.0).roundToLong() / 100.0
        return LoanPaymentEstimate(
            principal = safeAmount,
            termMonths = safeTerm,
            monthlyRatePercent = rate,
            estimatedInterest = interest,
            estimatedTotal = total,
            estimatedMonthly = monthly
        )
    }

    fun validateForm(input: LoanFormInput): String? {
        if (input.fullName.trim().length < 2) return "Please enter your full name."
        if (input.birthDate.trim().length < 6) return "Please enter your birth date."
        if (input.address.trim().length < 5) return "Please enter your current address."

        val phoneDigits = input.phone.filter { it.isDigit() }
        if (phoneDigits.length < 7) return "Please enter a valid mobile number."

        val email = input.email.trim()
        if (!email.contains("@") || !email.contains(".")) return "Please enter a valid email address."

        if (input.idType.trim().isEmpty()) return "Please choose an ID type."
        if (input.idNumber.trim().length < 4) return "Please enter your ID number."
        if (input.employment.trim().isEmpty()) return "Please choose your employment status."
        if (input.employer.trim().length < 2) return "Please enter your employer or business name."

        val income = input.monthlyIncomeText.replace(",", "").trim().toDoubleOrNull()
        if (income == null || income <= 0.0) return "Please enter a valid monthly income."

        if (input.purpose.trim().length < 3) return "Please enter the purpose of your loan."

        if (input.ref1Name.trim().length < 2) return "Please enter Reference 1 full name."
        if (input.ref1Phone.filter { it.isDigit() }.length < 7) return "Please enter a valid phone number for Reference 1."
        if (input.ref1Relation.trim().length < 2) return "Please enter Reference 1 relationship."

        if (input.ref2Name.trim().length < 2) return "Please enter Reference 2 full name."
        if (input.ref2Phone.filter { it.isDigit() }.length < 7) return "Please enter a valid phone number for Reference 2."
        if (input.ref2Relation.trim().length < 2) return "Please enter Reference 2 relationship."

        if (!input.consent) return "Please confirm the consent checkbox to proceed."
        return null
    }

    fun toSubmissionForm(input: LoanFormInput): LoanApplicationForm {
        val income = input.monthlyIncomeText.replace(",", "").trim().toDoubleOrNull() ?: 0.0
        return LoanApplicationForm(
            fullName = input.fullName.trim(),
            birthDate = input.birthDate.trim(),
            address = input.address.trim(),
            phone = input.phone.trim(),
            email = input.email.trim(),
            idType = input.idType.trim(),
            idNumber = input.idNumber.trim(),
            employment = input.employment.trim(),
            employer = input.employer.trim(),
            monthlyIncome = income,
            purpose = input.purpose.trim(),
            references = listOf(
                LoanReference(
                    name = input.ref1Name.trim(),
                    phone = input.ref1Phone.trim(),
                    relation = input.ref1Relation.trim()
                ),
                LoanReference(
                    name = input.ref2Name.trim(),
                    phone = input.ref2Phone.trim(),
                    relation = input.ref2Relation.trim()
                )
            ),
            consent = input.consent
        )
    }

    fun resolvePrimaryView(
        offer: LoanOffer?,
        applications: List<LoanApplication>,
        forceApplyForm: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): LoanPrimaryView {
        val sortedApps = applications.sortedByDescending { it.createdAt }
        val latestActiveApp = sortedApps.firstOrNull { !it.status.equals("withdrawn", ignoreCase = true) }

        if (latestActiveApp != null) {
            when (latestActiveApp.status.trim().lowercase(Locale.ROOT)) {
                "submitted" -> return LoanPrimaryView.SubmittedUnderReview(latestActiveApp)
                "approved" -> return LoanPrimaryView.ApprovedSelection(latestActiveApp)
                "accepted" -> return LoanPrimaryView.AcceptedPendingRelease(latestActiveApp)
                "declined" -> {
                    val reapplyAfter = listOfNotNull(latestActiveApp.reapplyAfter, offer?.reapplyAfter).maxOrNull()
                    val isLocked = reapplyAfter != null && nowMillis < reapplyAfter
                    val msg = latestActiveApp.adminNote.takeIf { it.isNotBlank() }
                        ?: offer?.declineMessage.orEmpty()
                    if (!forceApplyForm || isLocked) {
                        return LoanPrimaryView.DeclinedCooldown(
                            application = latestActiveApp,
                            reapplyAfterMillis = reapplyAfter,
                            declineMessage = msg,
                            isLocked = isLocked
                        )
                    }
                }
            }
        }

        if (offer == null || !offer.enabled) {
            return LoanPrimaryView.NoOffer
        }
        return LoanPrimaryView.InviteAndForm
    }

    fun formatDate(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Pending"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(millis))
    }

    fun formatDateTime(millis: Long?): String {
        if (millis == null || millis <= 0L) return "Just now"
        return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(millis))
    }

    fun formatTermLabel(months: Int): String = if (months == 1) "1 month" else "$months months"
}
