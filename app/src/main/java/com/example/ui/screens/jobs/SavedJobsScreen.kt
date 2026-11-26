package com.example.ui.screens.jobs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.JobPost
import com.example.viewmodel.JobFairViewModel

@Composable
fun SavedJobsScreen(
    jobFairViewModel: JobFairViewModel,
    onBack: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedJobs by jobFairViewModel.savedJobs.collectAsState()
    val myApplications by jobFairViewModel.myApplications.collectAsState()
    val appliedJobIds = remember(myApplications) {
        myApplications
            .filter { !it.status.equals("withdrawn", ignoreCase = true) }
            .map { it.jobId }
            .toSet()
    }

    LaunchedEffect(Unit) {
        jobFairViewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

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
                            .testTag("saved_jobs_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Saved Jobs",
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
            if (savedJobs.isEmpty()) {
                JobEmptyState(
                    title = "No saved jobs yet",
                    subtitle = "Tap the heart icon on any job card to bookmark it for later.",
                    icon = Icons.Default.FavoriteBorder
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(savedJobs, key = { it.jobId }) { saved ->
                        val previewJob = JobPost(
                            id = saved.jobId,
                            title = saved.title,
                            companyName = saved.companyName,
                            cityId = saved.cityId,
                            cityName = saved.cityName,
                            remote = saved.remote,
                            workType = saved.workType,
                            category = saved.category,
                            salaryMin = saved.salaryMin,
                            salaryMax = saved.salaryMax,
                            salaryPeriod = saved.salaryPeriod,
                            salaryCurrency = saved.salaryCurrency,
                            hideSalary = saved.hideSalary,
                            createdAt = saved.createdAt
                        )
                        JobCard(
                            job = previewJob,
                            isSaved = true,
                            hasApplied = appliedJobIds.contains(saved.jobId),
                            onClick = { onOpenJobDetail(saved.jobId) },
                            onToggleSave = { jobFairViewModel.removeSavedJob(saved) }
                        )
                    }
                }
            }
        }
    }
}
