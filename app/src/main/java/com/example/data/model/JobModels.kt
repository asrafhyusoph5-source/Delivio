package com.example.data.model

import java.util.Locale

data class JobApplyInfo(
    val method: String = "in_app", // "in_app" | "phone" | "email" | "url"
    val phone: String = "",
    val email: String = "",
    val url: String = "",
    val instructions: String = ""
)

data class JobPost(
    val id: String = "",
    val title: String = "",
    val companyName: String = "",
    val merchantId: String? = null,
    val postedBy: String = "admin",
    val cityId: String = "",
    val cityName: String = "",
    val reach: String = "local", // "local" | "global"
    val remote: Boolean = false,
    val workType: String = "full_time", // "full_time" | "part_time" | "contract" | "freelance" | "internship"
    val category: String = "",
    val salaryMin: Double? = null,
    val salaryMax: Double? = null,
    val salaryPeriod: String = "monthly",
    val salaryCurrency: String = "₱",
    val hideSalary: Boolean = false,
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val benefits: List<String> = emptyList(),
    val schedule: String = "",
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val vacancies: Int? = null,
    val deadline: Long? = null,
    val featured: Boolean = false,
    val status: String = "open", // "open" | "closed" | "pending"
    val apply: JobApplyInfo = JobApplyInfo(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isExpired: Boolean
        get() = deadline != null && deadline > 0L && System.currentTimeMillis() > deadline

    val isAcceptingApplications: Boolean
        get() = status.trim().equals("open", ignoreCase = true) && !isExpired

    val isNewBadgeEligible: Boolean
        get() {
            if (createdAt <= 0L) return false
            val threeDaysMs = 3L * 24L * 60L * 60L * 1000L
            val age = System.currentTimeMillis() - createdAt
            return age in 0L..threeDaysMs
        }

    val normalizedApplyMethod: String
        get() = apply.method.trim().lowercase(Locale.ROOT).ifBlank { "in_app" }
}

data class JobApplication(
    val id: String = "",
    val jobId: String = "",
    val jobMerchantId: String? = null,
    val jobTitle: String = "",
    val companyName: String = "",
    val cityId: String = "",
    val cityName: String = "",
    val applicantId: String = "",
    val applicantName: String = "",
    val applicantPhone: String = "",
    val applicantEmail: String = "",
    val message: String = "",
    val skills: List<String> = emptyList(),
    val experience: String = "",
    val resumeUrl: String = "",
    val status: String = "new", // "new" | "viewed" | "shortlisted" | "interview" | "hired" | "rejected" | "withdrawn"
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isWithdrawable: Boolean
        get() = status.trim().lowercase(Locale.ROOT) in setOf("new", "viewed", "shortlisted", "interview")

    val statusLabel: String
        get() = when (status.trim().lowercase(Locale.ROOT)) {
            "new" -> "Sent"
            "viewed" -> "Viewed"
            "shortlisted" -> "Shortlisted"
            "interview" -> "Interview"
            "hired" -> "Hired"
            "rejected" -> "Not selected"
            "withdrawn" -> "Withdrawn"
            else -> status.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
}

data class SavedJobItem(
    val jobId: String = "",
    val title: String = "",
    val companyName: String = "",
    val cityId: String = "",
    val cityName: String = "",
    val remote: Boolean = false,
    val workType: String = "full_time",
    val category: String = "",
    val salaryMin: Double? = null,
    val salaryMax: Double? = null,
    val salaryPeriod: String = "monthly",
    val salaryCurrency: String = "₱",
    val hideSalary: Boolean = false,
    val createdAt: Long = 0L,
    val savedAt: Long = 0L
)

data class JobFilterState(
    val workType: String? = null,
    val category: String? = null,
    val minSalary: Double? = null,
    val remoteOnly: Boolean = false,
    val last7DaysOnly: Boolean = false,
    val cityId: String? = null
) {
    fun activeCount(includeCityFilter: Boolean): Int {
        var count = 0
        if (!workType.isNullOrBlank()) count++
        if (!category.isNullOrBlank()) count++
        if (minSalary != null && minSalary > 0.0) count++
        if (remoteOnly) count++
        if (last7DaysOnly) count++
        if (includeCityFilter && !cityId.isNullOrBlank()) count++
        return count
    }

    fun reset(keepCity: Boolean = false): JobFilterState = JobFilterState(
        cityId = if (keepCity) cityId else null
    )
}
