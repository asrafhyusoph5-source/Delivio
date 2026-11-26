package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.City
import com.example.data.model.DeliveryRates
import com.example.data.model.User
import com.example.data.model.JobApplication
import com.example.data.model.JobApplyInfo
import com.example.data.model.JobFilterState
import com.example.data.model.JobPost
import com.example.data.model.JobProfile
import com.example.data.model.PlatformSettings
import com.example.data.model.Schema2ServiceRates
import com.example.data.repository.JobRepository
import com.example.ui.components.AvailabilityWarningCard
import com.example.ui.screens.jobs.APPLY_CONSENT_TEXT
import com.example.ui.screens.jobs.JobApplySheetContent
import com.example.ui.screens.jobs.JobCard
import com.example.ui.screens.jobs.JobDetailContent
import com.example.ui.screens.jobs.MyApplicationsContent
import com.example.ui.theme.DelivioTheme
import com.example.util.AvailabilityLocation
import com.example.util.ServiceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ServiceAvailabilityAndJobFairTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val puertoPrincesa = City(
        id = "puerto-princesa",
        name = "Puerto Princesa",
        province = "Palawan",
        status = "active",
        lat = 9.7392,
        lng = 118.7353,
        radiusKm = 25.0,
        services = mapOf(
            "food" to true,
            "convenience" to true,
            "parcel" to true,
            "pabili" to true,
            "taxi" to true,
            "jobfair" to true
        ),
        active = true
    )

    private val schema2Rates = DeliveryRates(
        id = "puerto-princesa",
        cityId = "puerto-princesa",
        schema = 2,
        fromFirestore = true,
        ratesDocExists = true,
        servicesSchema2 = mapOf(
            "food" to Schema2ServiceRates(),
            "convenience" to Schema2ServiceRates(),
            "pabili" to Schema2ServiceRates()
        ),
        parcelSchema2 = mapOf(
            "motorcycle" to Schema2ServiceRates(active = true),
            "sedan" to Schema2ServiceRates(active = true)
        ),
        taxiSchema2 = mapOf(
            "motorcycle" to Schema2ServiceRates(active = true),
            "tricycle" to Schema2ServiceRates(active = true)
        )
    )

    @Test
    fun resolveCity_matchesByIdThenByNameIgnoringCaseAndCityWord() {
        val cities = listOf(
            puertoPrincesa,
            City(id = "el-nido", name = "El Nido", status = "active", active = true)
        )

        assertEquals("puerto-princesa", ServiceAvailability.resolveCity("puerto-princesa", cities)?.id)
        assertEquals("puerto-princesa", ServiceAvailability.resolveCity("PUERTO-PRINCESA", cities)?.id)
        assertEquals("puerto-princesa", ServiceAvailability.resolveCity("Puerto Princesa City", cities)?.id)
        assertEquals("puerto-princesa", ServiceAvailability.resolveCity("puerto princesa", cities)?.id)
        assertEquals("el-nido", ServiceAvailability.resolveCity("El Nido City", cities)?.id)
    }

    @Test
    fun availability_passesWhenMissingKeysCountAsOn_andSchema2RatesPresent() {
        val sparsePlatform = PlatformSettings(services = emptyMap(), taxiVehicles = emptyMap())
        val sparseCity = puertoPrincesa.copy(services = emptyMap())

        val result = ServiceAvailability.availability(
            city = sparseCity,
            service = "taxi",
            vehicle = "motorcycle",
            location = AvailabilityLocation(
                pickupLat = 9.7400,
                pickupLng = 118.7360,
                dropoffLat = 9.7500,
                dropoffLng = 118.7450
            ),
            platformSettings = sparsePlatform,
            deliveryRates = schema2Rates,
            logResult = false
        )

        assertTrue(result.ok)
        assertEquals("", result.reason)
        assertTrue(result.checks.all { it.passed })
    }

    @Test
    fun availability_reportsSpecificReasonForCityOffVehicleOffMissingRatesAndRadius() {
        // City service switched off
        val taxiCityOff = ServiceAvailability.availability(
            city = puertoPrincesa.copy(services = mapOf("taxi" to false)),
            service = "taxi",
            vehicle = "motorcycle",
            platformSettings = PlatformSettings(),
            deliveryRates = schema2Rates,
            logResult = false
        )
        assertFalse(taxiCityOff.ok)
        assertEquals("Delivio Taxi is switched off in Puerto Princesa", taxiCityOff.reason)

        // Vehicle rates not set up yet (car4 missing in taxiSchema2)
        val missingVehicleRates = ServiceAvailability.availability(
            city = puertoPrincesa,
            service = "taxi",
            vehicle = "car4",
            platformSettings = PlatformSettings(),
            deliveryRates = schema2Rates,
            logResult = false
        )
        assertFalse(missingVehicleRates.ok)
        assertEquals("4-Seater Car rides are not set up in your area yet", missingVehicleRates.reason)

        // Vehicle active=false in rates
        val disabledVehicle = ServiceAvailability.availability(
            city = puertoPrincesa,
            service = "taxi",
            vehicle = "motorcycle",
            platformSettings = PlatformSettings(),
            deliveryRates = schema2Rates.copy(
                taxiSchema2 = mapOf("motorcycle" to Schema2ServiceRates(active = false))
            ),
            logResult = false
        )
        assertFalse(disabledVehicle.ok)
        assertEquals("Motorcycle rides are switched off in Puerto Princesa", disabledVehicle.reason)

        // No fares set for pabili when rates doc is missing
        val missingDocRates = ServiceAvailability.availability(
            city = puertoPrincesa,
            service = "pabili",
            platformSettings = PlatformSettings(),
            deliveryRates = DeliveryRates(cityId = "puerto-princesa", fromFirestore = true, ratesDocExists = false),
            logResult = false
        )
        assertFalse(missingDocRates.ok)
        assertEquals("No fares are set for this service", missingDocRates.reason)

        // Pickup pin > 25 km outside Puerto Princesa
        val farPickup = ServiceAvailability.availability(
            city = puertoPrincesa,
            service = "taxi",
            vehicle = "motorcycle",
            location = AvailabilityLocation(
                pickupLat = 10.02, // ~31+ km north of 9.7392
                pickupLng = 118.7353,
                dropoffLat = 9.74,
                dropoffLng = 118.74
            ),
            platformSettings = PlatformSettings(),
            deliveryRates = schema2Rates,
            logResult = false
        )
        assertFalse(farPickup.ok)
        assertTrue(farPickup.reason.contains("Your pickup point is"))
        assertTrue(farPickup.reason.contains("limit 25 km"))
    }

    @Test
    fun availabilityWarningCard_displaysRealReasonAndOpensWhyDialogWithChecks() {
        val failed = ServiceAvailability.availability(
            city = puertoPrincesa.copy(services = mapOf("taxi" to false)),
            service = "taxi",
            vehicle = "motorcycle",
            platformSettings = PlatformSettings(),
            deliveryRates = schema2Rates,
            logResult = false
        )

        composeRule.setContent {
            DelivioTheme {
                AvailabilityWarningCard(result = failed)
            }
        }

        composeRule.onNodeWithText("Delivio Taxi is switched off in Puerto Princesa").assertIsDisplayed()
        composeRule.onNodeWithTag("availability_why_link").performClick()

        composeRule.onNodeWithTag("availability_why_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("City service (taxi)").assertIsDisplayed()
        composeRule.onNodeWithText("false", substring = true).assertIsDisplayed()
    }

    @Test
    fun jobRepository_mergeFilterAndSort_ordersFeaturedFirstThenNewestAndDeduplicates() {
        val now = 1_700_000_000_000L
        val localOld = JobPost(
            id = "job_1",
            title = "Delivery Rider",
            companyName = "Palawan Express",
            cityId = "puerto-princesa",
            cityName = "Puerto Princesa",
            reach = "local",
            workType = "full_time",
            category = "Logistics",
            salaryMin = 15000.0,
            salaryMax = 18000.0,
            featured = false,
            createdAt = now - 10 * 24 * 60 * 60 * 1000L
        )
        val globalNew = JobPost(
            id = "job_2",
            title = "Remote Support Specialist",
            companyName = "Delivio HQ",
            cityId = "manila",
            cityName = "Manila",
            reach = "global",
            remote = true,
            workType = "part_time",
            category = "Customer Support",
            salaryMin = 25000.0,
            salaryMax = 30000.0,
            featured = false,
            createdAt = now - 1 * 24 * 60 * 60 * 1000L
        )
        val localFeatured = JobPost(
            id = "job_3",
            title = "Store Supervisor",
            companyName = "Puerto Mart",
            cityId = "puerto-princesa",
            cityName = "Puerto Princesa",
            reach = "global",
            workType = "full_time",
            category = "Retail",
            salaryMin = 22000.0,
            salaryMax = 26000.0,
            featured = true,
            createdAt = now - 5 * 24 * 60 * 60 * 1000L
        )

        // Merged deduplicates localFeatured appearing in both lists
        val merged = JobRepository.mergeAndSortJobs(
            listOf(globalNew, localFeatured),
            listOf(localOld, localFeatured)
        )
        assertEquals(listOf("job_3", "job_2", "job_1"), merged.map { it.id })

        // Filter remoteOnly + last 7 days + minSalary
        val filtered = JobRepository.filterAndSortJobs(
            jobs = merged,
            query = "support",
            filters = JobFilterState(
                remoteOnly = true,
                last7DaysOnly = true,
                minSalary = 20000.0
            ),
            isGlobalTab = true,
            nowMillis = now
        )
        assertEquals(listOf("job_2"), filtered.map { it.id })
    }

    @Test
    fun jobCard_hidesEmployerContactDetails_evenWhenPresentInModel() {
        val job = JobPost(
            id = "job_phone_1",
            title = "Barista",
            companyName = "Bayview Cafe",
            cityId = "puerto-princesa",
            cityName = "Puerto Princesa",
            workType = "full_time",
            featured = true,
            hideSalary = true,
            apply = JobApplyInfo(
                method = "phone",
                phone = "+639171234567",
                email = "secret-hr@bayview.test"
            )
        )

        composeRule.setContent {
            DelivioTheme {
                JobCard(
                    job = job,
                    isSaved = false,
                    hasApplied = false,
                    onClick = {},
                    onToggleSave = {}
                )
            }
        }

        composeRule.onNodeWithText("Barista").assertIsDisplayed()
        composeRule.onNodeWithText("Salary not shown").assertIsDisplayed()
        composeRule.onNodeWithText("Featured").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("+639171234567", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("secret-hr@bayview.test", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun jobDetailAndApplySheet_enforceApplyMethodsConsentAndSafetyNotice() {
        val phoneJob = JobPost(
            id = "job_phone_2",
            title = "Front Desk Associate",
            companyName = "Palawan Suites",
            cityId = "puerto-princesa",
            cityName = "Puerto Princesa",
            status = "open",
            apply = JobApplyInfo(method = "phone", phone = "09171234567")
        )

        var dialedNumber = ""
        composeRule.setContent {
            DelivioTheme {
                JobDetailContent(
                    job = phoneJob,
                    isLoading = false,
                    isSaved = false,
                    existingApplication = null,
                    onBack = {},
                    onToggleSave = {},
                    onShare = {},
                    onOpenApplySheet = {},
                    onCallEmployer = { dialedNumber = it },
                    onEmailEmployer = { _, _ -> },
                    onOpenApplyUrl = {},
                    onOpenReport = {}
                )
            }
        }

        composeRule.onNodeWithText("Never pay money to get a job. Report suspicious posts.").assertIsDisplayed()
        composeRule.onNodeWithTag("job_detail_cta_button").performClick()
        assertEquals("09171234567", dialedNumber)
    }

    @Test
    fun jobApplySheet_requiresConsentAndFieldsBeforeEnablingSubmit() {
        val inAppJob = JobPost(
            id = "job_in_app_1",
            title = "Cashier",
            companyName = "Puerto Fresh",
            status = "open"
        )
        var submittedName = ""

        composeRule.setContent {
            DelivioTheme {
                JobApplySheetContent(
                    job = inAppJob,
                    currentUser = User(
                        uid = "user_1",
                        name = "Maria Santos",
                        phone = "09175551234",
                        email = "maria@example.com"
                    ),
                    jobProfile = JobProfile(skills = listOf("POS")),
                    isSubmitting = false,
                    errorMessage = null,
                    onSubmit = { name, _, _, _, _, _, _, _ -> submittedName = name }
                )
            }
        }

        composeRule.onAllNodesWithText(APPLY_CONSENT_TEXT)[0].assertIsDisplayed()
        composeRule.onNodeWithTag("apply_submit_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("apply_message_input").performScrollTo().performTextInput("I have 3 years cashier experience.")
        // Still disabled until consent checkbox checked
        composeRule.onNodeWithTag("apply_submit_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("apply_consent_row").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("apply_submit_button").performScrollTo().assertIsEnabled().performClick()

        assertEquals("Maria Santos", submittedName)
    }

    @Test
    fun myApplicationsContent_displaysReadableStatusLabelsAndWithdrawForActiveOnly() {
        val apps = listOf(
            JobApplication(
                id = "job_a_u1",
                jobId = "job_a",
                jobTitle = "Kitchen Crew",
                companyName = "Island Grill",
                applicantId = "u1",
                status = "new",
                createdAt = System.currentTimeMillis()
            ),
            JobApplication(
                id = "job_b_u1",
                jobId = "job_b",
                jobTitle = "Driver",
                companyName = "Bay Logistics",
                applicantId = "u1",
                status = "rejected",
                createdAt = System.currentTimeMillis() - 86_400_000L
            )
        )

        var requestedWithdrawId = ""
        composeRule.setContent {
            DelivioTheme {
                MyApplicationsContent(
                    applications = apps,
                    onBack = {},
                    onOpenJobDetail = {},
                    onWithdrawRequest = { requestedWithdrawId = it.id }
                )
            }
        }

        composeRule.onNodeWithText("Sent").assertIsDisplayed()
        composeRule.onNodeWithText("Not selected").assertIsDisplayed()
        composeRule.onNodeWithTag("withdraw_application_job_a").assertIsDisplayed()
        assertNotNull(requestedWithdrawId)
        composeRule.onNodeWithTag("withdraw_application_job_a").performClick()
        assertEquals("job_a_u1", requestedWithdrawId)
    }
}
