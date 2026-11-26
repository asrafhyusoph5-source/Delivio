package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.ActiveLoan
import com.example.data.model.LoanApplication
import com.example.data.model.LoanApplicationForm
import com.example.data.model.LoanFormInput
import com.example.data.model.LoanLogic
import com.example.data.model.LoanOffer
import com.example.data.model.LoanPrimaryView
import com.example.data.model.LoanReference
import com.example.ui.screens.more.LoansContent
import com.example.ui.screens.more.MoreServicesContent
import com.example.ui.theme.DelivioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DelivioLoansTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val enabledOffer = LoanOffer(
        uid = "cust_1",
        enabled = true,
        maxAmount = 10000.0,
        termsMonths = listOf(1, 3, 6, 12),
        interestNote = "2.5% monthly add-on interest"
    )

    private fun validFormInput() = LoanFormInput(
        fullName = "Juan Dela Cruz",
        birthDate = "1995-05-12",
        address = "Rizal Ave, Puerto Princesa",
        phone = "09171234567",
        email = "juan@example.com",
        idType = "National ID (PhilSys)",
        idNumber = "1234-5678-9012",
        employment = "Employed",
        employer = "Palawan Trading",
        monthlyIncomeText = "28000",
        purpose = "Emergency medical bills",
        ref1Name = "Maria Cruz",
        ref1Phone = "09181112222",
        ref1Relation = "Sister",
        ref2Name = "Pedro Reyes",
        ref2Phone = "09193334444",
        ref2Relation = "Coworker",
        consent = true
    )

    @Test
    fun noOffer_hidesLoansInMoreServices_andResolvesNoOffer() {
        assertTrue(
            LoanLogic.resolvePrimaryView(offer = null, applications = emptyList()) is LoanPrimaryView.NoOffer
        )
        assertTrue(
            LoanLogic.resolvePrimaryView(offer = enabledOffer.copy(enabled = false), applications = emptyList()) is LoanPrimaryView.NoOffer
        )

        composeRule.setContent {
            DelivioTheme {
                MoreServicesContent(
                    showFlight = true,
                    showLoans = false,
                    showJobFair = true,
                    loanOffer = null,
                    onBack = {},
                    onTileClick = {}
                )
            }
        }
        assertTrue(composeRule.onAllNodesWithTag("more_tile_loans").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun formValidation_andPaymentEstimate_followContractRules() {
        val missingConsent = validFormInput().copy(consent = false)
        assertNotNull(LoanLogic.validateForm(missingConsent))

        val badRef = validFormInput().copy(ref2Phone = "123")
        assertNotNull(LoanLogic.validateForm(badRef))

        val valid = validFormInput()
        assertNull(LoanLogic.validateForm(valid))

        val submission = LoanLogic.toSubmissionForm(valid)
        assertEquals(2, submission.references.size)
        assertEquals(LoanReference("Maria Cruz", "09181112222", "Sister"), submission.references[0])
        assertEquals(28000.0, submission.monthlyIncome, 0.01)
        assertTrue(submission.consent)

        // 10,000 * 2.5% * 3 months = 750 interest -> 10,750 total -> 3,583.33 monthly
        val est = LoanLogic.calculatePaymentEstimate(
            amount = 10000.0,
            termMonths = 3,
            explicitRate = null,
            interestNote = "2.5% monthly add-on interest"
        )
        assertEquals(2.5, est.monthlyRatePercent!!, 0.001)
        assertEquals(750.0, est.estimatedInterest, 0.01)
        assertEquals(10750.0, est.estimatedTotal, 0.01)
        assertEquals(3583.33, est.estimatedMonthly, 0.01)
    }

    @Test
    fun inviteAndFormScreen_rendersInviteAndConsent_andSubmitsValidForm() {
        var submitted = false
        composeRule.setContent {
            DelivioTheme {
                var input by remember { mutableStateOf(validFormInput().copy(consent = false)) }
                var error by remember { mutableStateOf<String?>(null) }
                LoansContent(
                    offer = enabledOffer,
                    applications = emptyList(),
                    loans = emptyList(),
                    formInput = input,
                    onFormChange = { input = it },
                    errorMessage = error,
                    onBack = {},
                    onSubmitApplication = {
                        val err = LoanLogic.validateForm(input)
                        if (err != null) error = err else submitted = true
                    },
                    onWithdrawApplication = {},
                    onAcceptOffer = { _, _, _ -> },
                    onReapplyClick = {}
                )
            }
        }

        composeRule.onNodeWithText("You're invited to Delivio Loans").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_submit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("loan_form_error").performScrollTo().assertIsDisplayed()
        assertTrue(!submitted)

        composeRule.onNodeWithTag("loan_consent_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("loan_submit_button").performScrollTo().performClick()
        assertTrue(submitted)
    }

    @Test
    fun submittedScreen_showsUnderReviewAndWithdrawAction() {
        var withdrawnId = ""
        val app = LoanApplication(
            id = "cust_1_1700000000000",
            customerId = "cust_1",
            status = "submitted",
            form = LoanApplicationForm(fullName = "Juan Dela Cruz", monthlyIncome = 28000.0, purpose = "Bills"),
            createdAt = 1_700_000_000_000L
        )

        composeRule.setContent {
            DelivioTheme {
                LoansContent(
                    offer = enabledOffer,
                    applications = listOf(app),
                    loans = emptyList(),
                    formInput = LoanFormInput(),
                    onFormChange = {},
                    onBack = {},
                    onSubmitApplication = {},
                    onWithdrawApplication = { withdrawnId = it },
                    onAcceptOffer = { _, _, _ -> },
                    onReapplyClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Under review").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_submitted_date").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_withdraw_button").performClick()
        assertEquals("cust_1_1700000000000", withdrawnId)
    }

    @Test
    fun approvedAndAcceptedScreens_handleSelectionEstimateAcceptAndPendingRelease() {
        val approvedApp = LoanApplication(
            id = "cust_1_1700000000001",
            customerId = "cust_1",
            status = "approved",
            approvedAmount = 8000.0,
            termsOffered = listOf(1, 3, 6),
            interestNote = "2% monthly interest",
            createdAt = 1_700_000_000_000L
        )

        var acceptedApp by mutableStateOf(approvedApp)
        var capturedAmount = 0.0
        var capturedTerm = 0

        composeRule.setContent {
            DelivioTheme {
                LoansContent(
                    offer = enabledOffer,
                    applications = listOf(acceptedApp),
                    loans = emptyList(),
                    formInput = LoanFormInput(),
                    onFormChange = {},
                    onBack = {},
                    onSubmitApplication = {},
                    onWithdrawApplication = {},
                    onAcceptOffer = { _, amt, term ->
                        capturedAmount = amt
                        capturedTerm = term
                        acceptedApp = acceptedApp.copy(
                            status = "accepted",
                            chosenAmount = amt,
                            chosenTermMonths = term,
                            acceptedAt = 1_700_000_100_000L
                        )
                    },
                    onReapplyClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Congratulations! You can borrow up to ₱8,000").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_amount_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_term_3").performClick()
        composeRule.onNodeWithTag("loan_estimate_card").assertIsDisplayed()

        composeRule.onNodeWithTag("loan_accept_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(capturedAmount > 0.0 && capturedAmount <= 8000.0)
        assertEquals(3, capturedTerm)
        composeRule.onNodeWithText("Accepted. Delivio will contact you to release the funds").assertIsDisplayed()
    }

    @Test
    fun declinedScreen_locksApplyUntilReapplyAfterAndUnlocksAfterward() {
        val now = 1_700_000_000_000L
        val futureReapply = now + 7 * 86_400_000L
        val declinedApp = LoanApplication(
            id = "cust_1_1699000000000",
            customerId = "cust_1",
            status = "declined",
            reapplyAfter = futureReapply,
            adminNote = "Please build more completed order history first.",
            createdAt = 1_699_000_000_000L
        )

        var currentNow by mutableStateOf(now)
        var reapplyClicked = false

        composeRule.setContent {
            DelivioTheme {
                LoansContent(
                    offer = enabledOffer,
                    applications = listOf(declinedApp),
                    loans = emptyList(),
                    formInput = LoanFormInput(),
                    onFormChange = {},
                    nowMillis = currentNow,
                    onBack = {},
                    onSubmitApplication = {},
                    onWithdrawApplication = {},
                    onAcceptOffer = { _, _, _ -> },
                    onReapplyClick = { reapplyClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("You're not eligible yet to use Delivio Loans").assertIsDisplayed()
        composeRule.onNodeWithText("Please build more completed order history first.").assertIsDisplayed()
        composeRule.onNodeWithTag("loan_reapply_button").assertIsNotEnabled()

        // Advance time past reapplyAfter -> button unlocks
        currentNow = futureReapply + 1000L
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("loan_reapply_button").assertIsEnabled().performClick()
        assertTrue(reapplyClicked)
    }

    @Test
    fun activeLoanCard_showsAmountTermStatusAndComingSoonPayments() {
        val activeLoan = ActiveLoan(
            id = "loan_123",
            customerId = "cust_1",
            applicationId = "cust_1_1700000000001",
            amount = 5000.0,
            termMonths = 3,
            status = "active",
            createdAt = 1_700_000_200_000L
        )

        composeRule.setContent {
            DelivioTheme {
                LoansContent(
                    offer = enabledOffer,
                    applications = emptyList(),
                    loans = listOf(activeLoan),
                    formInput = LoanFormInput(),
                    onFormChange = {},
                    onBack = {},
                    onSubmitApplication = {},
                    onWithdrawApplication = {},
                    onAcceptOffer = { _, _, _ -> },
                    onReapplyClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("active_loan_card_loan_123").assertIsDisplayed()
        composeRule.onNodeWithText("₱5,000").assertIsDisplayed()
        composeRule.onNodeWithText("Term: 3 months").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("Coming soon").assertIsDisplayed()
    }
}
