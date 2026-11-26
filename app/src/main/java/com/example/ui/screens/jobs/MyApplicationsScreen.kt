package com.example.ui.screens.jobs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JobApplication
import com.example.ui.theme.BadgeOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.JobFairViewModel
import java.util.Locale

@Composable
fun MyApplicationsScreen(
    jobFairViewModel: JobFairViewModel,
    onBack: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val applications by jobFairViewModel.myApplications.collectAsState()
    var confirmWithdrawTarget by remember { mutableStateOf<JobApplication?>(null) }

    LaunchedEffect(Unit) {
        jobFairViewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    MyApplicationsContent(
        applications = applications,
        onBack = onBack,
        onOpenJobDetail = onOpenJobDetail,
        onWithdrawRequest = { confirmWithdrawTarget = it },
        modifier = modifier
    )

    confirmWithdrawTarget?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmWithdrawTarget = null },
            title = { Text("Withdraw Application?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to withdraw your application for ${app.jobTitle} at ${app.companyName}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        jobFairViewModel.withdrawApplication(app.id)
                        confirmWithdrawTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_withdraw_button")
                ) {
                    Text("Withdraw", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdrawTarget = null }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
fun MyApplicationsContent(
    applications: List<JobApplication>,
    onBack: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    onWithdrawRequest: (JobApplication) -> Unit,
    modifier: Modifier = Modifier
) {
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
                            .testTag("my_applications_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "My Applications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (applications.isEmpty()) {
                JobEmptyState(
                    title = "No applications yet",
                    subtitle = "When you apply for jobs in Job Fair, your status updates will appear here.",
                    icon = Icons.Default.Assignment
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(applications, key = { it.id }) { app ->
                        ApplicationCard(
                            application = app,
                            onClick = { onOpenJobDetail(app.jobId) },
                            onWithdrawClick = { onWithdrawRequest(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(
    application: JobApplication,
    onClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    val (badgeBg, badgeFg) = when (application.status.trim().lowercase(Locale.ROOT)) {
        "new" -> YellowPrimary.copy(alpha = 0.22f) to Color(0xFF8C6D00)
        "viewed" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        "shortlisted" -> Color(0xFFEDE7F6) to Color(0xFF5E35B1)
        "interview" -> BadgeOrange.copy(alpha = 0.16f) to BadgeOrange
        "hired" -> SuccessGreen.copy(alpha = 0.16f) to SuccessGreen
        "rejected" -> ErrorRed.copy(alpha = 0.14f) to ErrorRed
        "withdrawn" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("application_card_${application.jobId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = application.jobTitle.ifBlank { "Job Application" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = application.companyName.ifBlank { "Employer" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    modifier = Modifier.testTag("application_status_${application.jobId}")
                ) {
                    Text(
                        text = application.statusLabel,
                        color = badgeFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    val appliedDate = JobFormatters.formatDate(application.createdAt)
                        .ifBlank { JobFormatters.formatRelativePostedTime(application.createdAt) }
                    Text(
                        text = "Applied $appliedDate",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (application.isWithdrawable) {
                    OutlinedButton(
                        onClick = onWithdrawClick,
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("withdraw_application_${application.jobId}")
                    ) {
                        Text("Withdraw", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
