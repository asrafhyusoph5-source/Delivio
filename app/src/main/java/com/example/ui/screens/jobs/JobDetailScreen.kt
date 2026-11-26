package com.example.ui.screens.jobs

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.JobApplication
import com.example.data.model.JobPost
import com.example.data.model.JobProfile
import com.example.data.model.User
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.util.OsmConfig
import com.example.viewmodel.JobFairViewModel
import java.util.Locale

@Composable
fun JobDetailScreen(
    jobId: String,
    jobFairViewModel: JobFairViewModel,
    currentUser: User?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val job by jobFairViewModel.selectedJob.collectAsState()
    val isLoading by jobFairViewModel.isSelectedJobLoading.collectAsState()
    val savedJobIds by jobFairViewModel.savedJobIds.collectAsState()
    val myApplications by jobFairViewModel.myApplications.collectAsState()
    val jobProfile by jobFairViewModel.jobProfile.collectAsState()
    val isSubmittingApplication by jobFairViewModel.isSubmittingApplication.collectAsState()
    val isSubmittingReport by jobFairViewModel.isSubmittingReport.collectAsState()

    var showApplySheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(jobId) {
        jobFairViewModel.observeJobDetail(jobId)
    }

    LaunchedEffect(Unit) {
        jobFairViewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val existingApplication = remember(myApplications, jobId) {
        myApplications.firstOrNull {
            it.jobId == jobId && !it.status.equals("withdrawn", ignoreCase = true)
        }
    }

    JobDetailContent(
        job = job,
        isLoading = isLoading,
        isSaved = job?.let { savedJobIds.contains(it.id) } ?: false,
        existingApplication = existingApplication,
        onBack = onBack,
        onToggleSave = { job?.let { jobFairViewModel.toggleSaveJob(it) } },
        onShare = { target ->
            val salaryInfo = JobFormatters.formatSalary(
                target.salaryMin,
                target.salaryMax,
                target.salaryPeriod,
                target.salaryCurrency,
                target.hideSalary
            )
            val shareText = buildString {
                append("${target.title} at ${target.companyName}\n")
                append("Location: ${target.cityName.ifBlank { target.cityId }}")
                if (target.remote) append(" (Remote)")
                append("\n$salaryInfo\n")
                append("Find this job on Delivio Job Fair!")
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${target.title} - Delivio Job Fair")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            try {
                context.startActivity(Intent.createChooser(sendIntent, "Share job"))
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open share dialog", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenApplySheet = {
            applyError = null
            showApplySheet = true
        },
        onCallEmployer = { phone ->
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            try {
                context.startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "No phone dialer found", Toast.LENGTH_SHORT).show()
            }
        },
        onEmailEmployer = { email, title ->
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "Application for $title")
            }
            try {
                context.startActivity(emailIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenApplyUrl = { url ->
            val normalizedUrl = if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                url
            } else {
                "https://$url"
            }
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)))
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open application link", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenReport = { showReportDialog = true },
        modifier = modifier
    )

    if (showApplySheet && job != null) {
        val targetJob = job!!
        JobApplySheet(
            job = targetJob,
            currentUser = currentUser,
            jobProfile = jobProfile,
            isSubmitting = isSubmittingApplication,
            errorMessage = applyError,
            onDismiss = { showApplySheet = false },
            onSubmit = { name, phone, email, message, skills, experience, resumeUrl, saveForNextTime ->
                applyError = null
                jobFairViewModel.submitApplication(
                    job = targetJob,
                    name = name,
                    phone = phone,
                    email = email,
                    message = message,
                    skills = skills,
                    experience = experience,
                    resumeUrl = resumeUrl,
                    saveForNextTime = saveForNextTime
                ) { res ->
                    if (res.isSuccess) {
                        showApplySheet = false
                    } else {
                        applyError = res.exceptionOrNull()?.localizedMessage
                            ?: res.exceptionOrNull()?.message
                            ?: "Could not submit application."
                    }
                }
            }
        )
    }

    if (showReportDialog && job != null) {
        val targetJob = job!!
        ReportJobDialog(
            isSubmitting = isSubmittingReport,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                jobFairViewModel.reportJob(targetJob, reason, details) { res ->
                    if (res.isSuccess) {
                        showReportDialog = false
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobDetailContent(
    job: JobPost?,
    isLoading: Boolean,
    isSaved: Boolean,
    existingApplication: JobApplication?,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: (JobPost) -> Unit,
    onOpenApplySheet: () -> Unit,
    onCallEmployer: (String) -> Unit,
    onEmailEmployer: (String, String) -> Unit,
    onOpenApplyUrl: (String) -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Job Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (job != null) {
                        IconButton(
                            onClick = { onShare(job) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("job_detail_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share job")
                        }
                        IconButton(
                            onClick = onToggleSave,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("job_detail_save_button")
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "Remove saved job" else "Save job",
                                tint = if (isSaved) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (job != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val method = job.normalizedApplyMethod
                        val alreadyApplied = existingApplication != null
                        val accepting = job.isAcceptingApplications

                        when {
                            alreadyApplied -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Application Status",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessGreen.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = existingApplication!!.statusLabel,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("You already applied", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                            !accepting -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Text("No longer accepting applications", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                            method == "phone" -> {
                                Button(
                                    onClick = {
                                        if (job.apply.phone.isNotBlank()) {
                                            onCallEmployer(job.apply.phone)
                                        }
                                    },
                                    enabled = job.apply.phone.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Call", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                            method == "email" -> {
                                Button(
                                    onClick = {
                                        if (job.apply.email.isNotBlank()) {
                                            onEmailEmployer(job.apply.email, job.title)
                                        }
                                    },
                                    enabled = job.apply.email.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Email", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                            method == "url" -> {
                                Button(
                                    onClick = {
                                        if (job.apply.url.isNotBlank()) {
                                            onOpenApplyUrl(job.apply.url)
                                        }
                                    },
                                    enabled = job.apply.url.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Online", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onOpenApplySheet,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = YellowPrimary,
                                        contentColor = TextOnYellow
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("job_detail_cta_button")
                                ) {
                                    Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            isLoading && job == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YellowPrimary)
                }
            }
            job == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    JobEmptyState(
                        title = "Job post not found",
                        subtitle = "This opening may have been removed or closed."
                    )
                }
            }
            else -> {
                val salaryText = JobFormatters.formatSalary(
                    job.salaryMin,
                    job.salaryMax,
                    job.salaryPeriod,
                    job.salaryCurrency,
                    job.hideSalary
                )
                val cityLabel = job.cityName.ifBlank {
                    job.cityId.replace("-", " ").replaceFirstChar { it.uppercase(Locale.ROOT) }
                }.ifBlank { "Philippines" }
                val applyMethod = job.normalizedApplyMethod

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Header Summary Card
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (job.featured || job.isNewBadgeEligible) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (job.featured) {
                                            Surface(shape = RoundedCornerShape(8.dp), color = YellowPrimary) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = TextOnYellow, modifier = Modifier.size(12.dp))
                                                    Text("Featured", color = TextOnYellow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                        }
                                        if (job.isNewBadgeEligible) {
                                            Surface(shape = RoundedCornerShape(8.dp), color = SuccessGreen.copy(alpha = 0.14f)) {
                                                Text(
                                                    text = "New",
                                                    color = SuccessGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = job.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = job.companyName.ifBlank { "Verified Employer" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    JobMetaChip(
                                        icon = Icons.Default.LocationOn,
                                        text = cityLabel,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                    JobMetaChip(
                                        icon = if (job.remote) Icons.Default.Public else null,
                                        text = if (job.remote) "Remote" else "Local",
                                        containerColor = if (job.remote) Color(0xFFE3F2FD) else BadgeOrange.copy(alpha = 0.14f),
                                        contentColor = if (job.remote) Color(0xFF1565C0) else BadgeOrange
                                    )
                                    JobMetaChip(
                                        text = JobFormatters.formatWorkType(job.workType),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (job.category.isNotBlank()) {
                                        JobMetaChip(
                                            text = job.category,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = salaryText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Text(
                                        text = JobFormatters.formatRelativePostedTime(job.createdAt),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Key Facts Card: Schedule, Vacancies, Deadline, Apply Method
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Job Overview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (job.schedule.isNotBlank()) {
                                    JobFactRow(Icons.Default.Schedule, "Schedule", job.schedule)
                                }
                                if (job.vacancies != null && job.vacancies > 0) {
                                    JobFactRow(Icons.Default.Group, "Vacancies", "${job.vacancies} opening${if (job.vacancies == 1) "" else "s"}")
                                }
                                val deadlineText = JobFormatters.formatDate(job.deadline)
                                if (deadlineText.isNotBlank()) {
                                    JobFactRow(
                                        Icons.Default.Event,
                                        "Application Deadline",
                                        if (job.isExpired) "$deadlineText (Expired)" else deadlineText
                                    )
                                }
                                val applyLabel = when (applyMethod) {
                                    "phone" -> "Call employer directly"
                                    "email" -> "Email employer directly"
                                    "url" -> "External application link"
                                    else -> "In-app Delivio application"
                                }
                                JobFactRow(Icons.Default.Work, "How to Apply", applyLabel)
                                if (job.apply.instructions.isNotBlank()) {
                                    Text(
                                        text = job.apply.instructions,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Show employer phone/email ONLY here on details AND ONLY for phone/email apply methods
                                if (applyMethod == "phone" && job.apply.phone.isNotBlank()) {
                                    JobFactRow(Icons.Default.Call, "Employer Phone", job.apply.phone)
                                }
                                if (applyMethod == "email" && job.apply.email.isNotBlank()) {
                                    JobFactRow(Icons.Default.Email, "Employer Email", job.apply.email)
                                }
                            }
                        }
                    }

                    // Description
                    if (job.description.isNotBlank()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Job Description", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        text = job.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Requirements
                    if (job.requirements.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Requirements", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    job.requirements.forEach { req ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("•", fontWeight = FontWeight.Bold, color = YellowPrimary)
                                            Text(req, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Benefits
                    if (job.benefits.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Benefits & Perks", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        job.benefits.forEach { benefit ->
                                            JobMetaChip(
                                                icon = Icons.Default.CheckCircle,
                                                text = benefit,
                                                containerColor = SuccessGreen.copy(alpha = 0.12f),
                                                contentColor = SuccessGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Location + Map Preview + "Open in maps"
                    if (job.address.isNotBlank() || (job.lat != null && job.lng != null)) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Workplace Location", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (job.address.isNotBlank()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = YellowPrimary, modifier = Modifier.size(18.dp))
                                            Text(job.address, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (job.lat != null && job.lng != null && (job.lat != 0.0 || job.lng != 0.0)) {
                                        JobOsmMapPreview(
                                            lat = job.lat,
                                            lng = job.lng,
                                            label = job.companyName.ifBlank { job.title },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val uri = Uri.parse(
                                                    "geo:${job.lat},${job.lng}?q=${job.lat},${job.lng}(${Uri.encode(job.address.ifBlank { job.title })})"
                                                )
                                                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                                try {
                                                    context.startActivity(mapIntent)
                                                } catch (_: Exception) {
                                                    val browserUri = Uri.parse("https://www.openstreetmap.org/?mlat=${job.lat}&mlon=${job.lng}#map=16/${job.lat}/${job.lng}")
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("job_detail_open_maps_button")
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open in maps", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Safety Notice + Report this job
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ErrorRed.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("job_detail_safety_banner")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Never pay money to get a job. Report suspicious posts.",
                                        color = ErrorRed,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenReport,
                                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    modifier = Modifier.testTag("job_detail_report_button")
                                ) {
                                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Report this job", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobFactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YellowPrimary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun JobOsmMapPreview(
    lat: Double,
    lng: Double,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isSystemInDarkTheme()
    val mapView = remember {
        OsmConfig.init(context.applicationContext)
        org.osmdroid.views.MapView(context).apply {
            setMultiTouchControls(true)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { mv ->
            val point = org.osmdroid.util.GeoPoint(lat, lng)
            mv.controller.setZoom(15.5)
            mv.controller.setCenter(point)
            mv.overlays.clear()
            val marker = org.osmdroid.views.overlay.Marker(mv).apply {
                position = point
                title = label
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            mv.overlays.add(marker)
            if (isDark) {
                val invertMatrix = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                mv.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(invertMatrix))
            } else {
                mv.overlayManager.tilesOverlay.setColorFilter(null)
            }
            mv.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun ReportJobDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf(
        "Asked for payment or registration fee",
        "Fake or misleading job post",
        "Scam or suspicious contact details",
        "Inappropriate or offensive content",
        "Wrong location or salary details"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text("Report this job", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select a reason so our safety team can review this post:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = YellowPrimary)
                        )
                        Text(
                            text = reason,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional details (optional)") },
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("job_report_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
