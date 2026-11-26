package com.example.data.model

/**
 * Customer loan offer stored at /loanOffers/{uid}.
 * Visibility in More services follows both platform/city switches and enabled == true on the customer's offer.
 */
data class LoanOffer(
    val uid: String = "",
    val enabled: Boolean = false,
    val title: String = "Delivio Cash Loan",
    val maxAmount: Double = 0.0,
    val minAmount: Double = 0.0,
    val termsMonths: List<Int> = emptyList(),
    val interestRatePercent: Double? = null,
    val reapplyAfter: Long? = null,
    val note: String = ""
)

data class LoanApplicationSummary(
    val id: String = "",
    val customerId: String = "",
    val status: String = "submitted",
    val requestedAmount: Double = 0.0,
    val approvedAmount: Double? = null,
    val chosenAmount: Double? = null,
    val chosenTermMonths: Int? = null,
    val createdAt: Long = 0L
)
