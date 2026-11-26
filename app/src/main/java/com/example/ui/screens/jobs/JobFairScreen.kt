package com.example.ui.screens.jobs

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.City
import com.example.data.model.JobFilterState
import com.example.ui.theme.TextOnYellow
import com.example.ui.theme.YellowPrimary
import com.example.viewmodel.JobFairTab
import com.example.viewmodel.JobFairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFairScreen(
    jobFairViewModel: JobFairViewModel,
    cityId: String,
    cityName: String,
    cities: List<City>,
    onBack: () -> Unit,
    onOpenJobDetail: (String) -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenMyApplications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTab by jobFairViewModel.activeTab.collectAsState()
    val searchQuery by jobFairViewModel.searchQuery.collectAsState()
    val filterState by jobFairViewModel.filterState.collectAsState()
    val filteredLocalJobs by jobFairViewModel.filteredLocalJobs.collectAsState()
    val filteredGlobalJobs by jobFairViewModel.filteredGlobalJobs.collectAsState()
    val allLocalJobs by jobFairViewModel.localJobs.collectAsState()
    val allGlobalJobs by jobFairViewModel.globalJobs.collectAsState()
    val isLocalLoading by jobFairViewModel.isLocalLoading.collectAsState()
    val isGlobalLoading by jobFairViewModel.isGlobalLoading.collectAsState()
    val savedJobs by jobFairViewModel.savedJobs.collectAsState()
    val savedJobIds by jobFairViewModel.savedJobIds.collectAsState()
    val myApplications by jobFairViewModel.myApplications.collectAsState()

    var showFiltersSheet by remember { mutableStateOf(false) }

    LaunchedEffect(cityId) {
        jobFairViewModel.setCityId(cityId)
    }

    LaunchedEffect(Unit) {
        jobFairViewModel.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val currentJobs = if (activeTab == JobFairTab.LOCAL) filteredLocalJobs else filteredGlobalJobs
    val isLoading = if (activeTab == JobFairTab.LOCAL) isLocalLoading else isGlobalLoading
    val activeFilterCount = filterState.activeCount(includeCityFilter = activeTab == JobFairTab.GLOBAL)
    val appliedJobIds = remember(myApplications) {
        myApplications
            .filter { !it.status.equals("withdrawn", ignoreCase = true) }
            .map { it.jobId }
            .toSet()
    }
    val availableCategories = remember(allLocalJobs, allGlobalJobs) {
        (allLocalJobs + allGlobalJobs)
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header bar: Back + Title "Job Fair" + Saved heart + My applications icon
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
                                .testTag("jobfair_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Job Fair",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (activeTab == JobFairTab.LOCAL) {
                                    "Jobs in ${cityName.ifBlank { cityId }}"
                                } else {
                                    "Remote & global opportunities"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Saved jobs heart icon
                        IconButton(
                            onClick = onOpenSavedJobs,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("jobfair_saved_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (savedJobs.isNotEmpty()) {
                                        Badge(containerColor = YellowPrimary, contentColor = TextOnYellow) {
                                            Text(savedJobs.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Saved jobs",
                                    tint = Color(0xFFE53935)
                                )
                            }
                        }

                        // My applications icon
                        IconButton(
                            onClick = onOpenMyApplications,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("jobfair_applications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (myApplications.isNotEmpty()) {
                                        Badge(containerColor = YellowPrimary, contentColor = TextOnYellow) {
                                            Text(myApplications.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = "My applications",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Search box + Filter button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { jobFairViewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search jobs, companies, skills...", fontSize = 13.sp, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { jobFairViewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("jobfair_search_input")
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (activeFilterCount > 0) YellowPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (activeFilterCount > 0) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showFiltersSheet = true }
                                .testTag("jobfair_filter_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filters",
                                    modifier = Modifier.size(20.dp)
                                )
                                if (activeFilterCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(TextOnYellow),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = activeFilterCount.toString(),
                                            color = YellowPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Local / Global tabs
                    TabRow(
                        selectedTabIndex = if (activeTab == JobFairTab.LOCAL) 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        indicator = { tabPositions ->
                            val idx = if (activeTab == JobFairTab.LOCAL) 0 else 1
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                                color = YellowPrimary,
                                height = 3.dp
                            )
                        }
                    ) {
                        Tab(
                            selected = activeTab == JobFairTab.LOCAL,
                            onClick = { jobFairViewModel.selectTab(JobFairTab.LOCAL) },
                            text = {
                                Text(
                                    text = "Local",
                                    fontWeight = if (activeTab == JobFairTab.LOCAL) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("jobfair_tab_local")
                        )
                        Tab(
                            selected = activeTab == JobFairTab.GLOBAL,
                            onClick = { jobFairViewModel.selectTab(JobFairTab.GLOBAL) },
                            text = {
                                Text(
                                    text = "Global",
                                    fontWeight = if (activeTab == JobFairTab.GLOBAL) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("jobfair_tab_global")
                        )
                    }
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
            when {
                isLoading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item { JobCardSkeletonList() }
                    }
                }
                currentJobs.isEmpty() -> {
                    val hasSearchOrFilter = searchQuery.isNotBlank() || activeFilterCount > 0
                    JobEmptyState(
                        title = if (hasSearchOrFilter) "No matching jobs found" else if (activeTab == JobFairTab.LOCAL) "No local openings right now" else "No global openings right now",
                        subtitle = if (hasSearchOrFilter) {
                            "Try clearing a filter or searching with different keywords."
                        } else if (activeTab == JobFairTab.LOCAL) {
                            "Check the Global tab for remote jobs or check back soon."
                        } else {
                            "New remote and global roles are added regularly."
                        },
                        actionButton = if (hasSearchOrFilter) {
                            {
                                OutlinedButton(
                                    onClick = {
                                        jobFairViewModel.updateSearchQuery("")
                                        jobFairViewModel.resetFilters()
                                    }
                                ) {
                                    Text("Clear Search & Filters", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else null
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(currentJobs, key = { it.id }) { job ->
                            JobCard(
                                job = job,
                                isSaved = savedJobIds.contains(job.id),
                                hasApplied = appliedJobIds.contains(job.id),
                                onClick = { onOpenJobDetail(job.id) },
                                onToggleSave = { jobFairViewModel.toggleSaveJob(job) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        JobFiltersBottomSheet(
            initialState = filterState,
            isGlobalTab = activeTab == JobFairTab.GLOBAL,
            availableCategories = availableCategories,
            cities = cities,
            onDismiss = { showFiltersSheet = false },
            onApply = { updated ->
                jobFairViewModel.updateFilters(updated)
                showFiltersSheet = false
            },
            onReset = {
                jobFairViewModel.resetFilters()
                showFiltersSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun JobFiltersBottomSheet(
    initialState: JobFilterState,
    isGlobalTab: Boolean,
    availableCategories: List<String>,
    cities: List<City>,
    onDismiss: () -> Unit,
    onApply: (JobFilterState) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var workType by remember { mutableStateOf(initialState.workType) }
    var category by remember { mutableStateOf(initialState.category) }
    var minSalaryText by remember {
        mutableStateOf(initialState.minSalary?.takeIf { it > 0.0 }?.toLong()?.toString().orEmpty())
    }
    var remoteOnly by remember { mutableStateOf(initialState.remoteOnly) }
    var last7DaysOnly by remember { mutableStateOf(initialState.last7DaysOnly) }
    var selectedCityId by remember { mutableStateOf(initialState.cityId) }

    val workTypeOptions = listOf(
        "full_time" to "Full-time",
        "part_time" to "Part-time",
        "contract" to "Contract",
        "freelance" to "Freelance",
        "internship" to "Internship"
    )
    val defaultCategories = listOf(
        "Delivery & Logistics",
        "Retail & Sales",
        "Food & Hospitality",
        "Customer Support",
        "Tech & Admin",
        "Skilled Trades"
    )
    val categoryChoices = remember(availableCategories) {
        (availableCategories + defaultCategories).distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Jobs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("jobfair_filters_reset_button")
                ) {
                    Text("Reset", fontWeight = FontWeight.Bold)
                }
            }

            // Work Type
            Text(text = "Work Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                workTypeOptions.forEach { (key, label) ->
                    val selected = workType.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { workType = if (selected) null else key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YellowPrimary,
                            selectedLabelColor = TextOnYellow
                        )
                    )
                }
            }

            // Category
            Text(text = "Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categoryChoices.forEach { cat ->
                    val selected = category.equals(cat, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { category = if (selected) null else cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YellowPrimary,
                            selectedLabelColor = TextOnYellow
                        )
                    )
                }
            }

            // Minimum Salary
            OutlinedTextField(
                value = minSalaryText,
                onValueChange = { raw -> minSalaryText = raw.filter { it.isDigit() } },
                label = { Text("Minimum Salary (optional)") },
                placeholder = { Text("e.g. 15000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jobfair_filter_min_salary")
            )

            // Remote Only toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Remote only", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "Show work-from-home and remote positions only",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = remoteOnly,
                    onCheckedChange = { remoteOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextOnYellow,
                        checkedTrackColor = YellowPrimary
                    ),
                    modifier = Modifier.testTag("jobfair_filter_remote_switch")
                )
            }

            // Posted in the last 7 days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Posted in the last 7 days", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "Only show fresh openings from this week",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = last7DaysOnly,
                    onCheckedChange = { last7DaysOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextOnYellow,
                        checkedTrackColor = YellowPrimary
                    ),
                    modifier = Modifier.testTag("jobfair_filter_7days_switch")
                )
            }

            // City picker (for Global tab)
            if (isGlobalTab && cities.isNotEmpty()) {
                HorizontalDivider()
                Text(text = "City", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCityId.isNullOrBlank(),
                        onClick = { selectedCityId = null },
                        label = { Text("All Cities", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YellowPrimary,
                            selectedLabelColor = TextOnYellow
                        )
                    )
                    cities.forEach { city ->
                        val selected = selectedCityId.equals(city.id, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCityId = if (selected) null else city.id },
                            label = { Text(city.name.ifBlank { city.id }, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YellowPrimary,
                                selectedLabelColor = TextOnYellow
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    onApply(
                        JobFilterState(
                            workType = workType,
                            category = category,
                            minSalary = minSalaryText.toDoubleOrNull()?.takeIf { it > 0.0 },
                            remoteOnly = remoteOnly,
                            last7DaysOnly = last7DaysOnly,
                            cityId = if (isGlobalTab) selectedCityId else null
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YellowPrimary,
                    contentColor = TextOnYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("jobfair_filters_apply_button")
            ) {
                Text("Apply Filters", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}
