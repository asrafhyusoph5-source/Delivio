package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.JobApplication
import com.example.data.model.JobFilterState
import com.example.data.model.JobPost
import com.example.data.model.JobProfile
import com.example.data.model.SavedJobItem
import com.example.data.repository.JobRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class JobFairTab {
    LOCAL,
    GLOBAL
}

class JobFairViewModel(
    private val repository: JobRepository
) : ViewModel() {

    private val _customerId = MutableStateFlow("")
    val customerId: StateFlow<String> = _customerId.asStateFlow()

    private val _cityId = MutableStateFlow("puerto-princesa")
    val cityId: StateFlow<String> = _cityId.asStateFlow()

    private val _activeTab = MutableStateFlow(JobFairTab.LOCAL)
    val activeTab: StateFlow<JobFairTab> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(JobFilterState())
    val filterState: StateFlow<JobFilterState> = _filterState.asStateFlow()

    private val _localJobs = MutableStateFlow<List<JobPost>>(emptyList())
    val localJobs: StateFlow<List<JobPost>> = _localJobs.asStateFlow()

    private val _globalJobs = MutableStateFlow<List<JobPost>>(emptyList())
    val globalJobs: StateFlow<List<JobPost>> = _globalJobs.asStateFlow()

    private val _isLocalLoading = MutableStateFlow(true)
    val isLocalLoading: StateFlow<Boolean> = _isLocalLoading.asStateFlow()

    private val _isGlobalLoading = MutableStateFlow(true)
    val isGlobalLoading: StateFlow<Boolean> = _isGlobalLoading.asStateFlow()

    private val _savedJobs = MutableStateFlow<List<SavedJobItem>>(emptyList())
    val savedJobs: StateFlow<List<SavedJobItem>> = _savedJobs.asStateFlow()

    private val _myApplications = MutableStateFlow<List<JobApplication>>(emptyList())
    val myApplications: StateFlow<List<JobApplication>> = _myApplications.asStateFlow()

    private val _jobProfile = MutableStateFlow<JobProfile?>(null)
    val jobProfile: StateFlow<JobProfile?> = _jobProfile.asStateFlow()

    private val _selectedJob = MutableStateFlow<JobPost?>(null)
    val selectedJob: StateFlow<JobPost?> = _selectedJob.asStateFlow()

    private val _isSelectedJobLoading = MutableStateFlow(false)
    val isSelectedJobLoading: StateFlow<Boolean> = _isSelectedJobLoading.asStateFlow()

    private val _isSubmittingApplication = MutableStateFlow(false)
    val isSubmittingApplication: StateFlow<Boolean> = _isSubmittingApplication.asStateFlow()

    private val _isSubmittingReport = MutableStateFlow(false)
    val isSubmittingReport: StateFlow<Boolean> = _isSubmittingReport.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val savedJobIds: StateFlow<Set<String>> = _savedJobs
        .combine(_customerId) { saved, _ -> saved.map { it.jobId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val filteredLocalJobs: StateFlow<List<JobPost>> = combine(
        _localJobs,
        _searchQuery,
        _filterState
    ) { jobs, query, filters ->
        JobRepository.filterAndSortJobs(
            jobs = jobs,
            query = query,
            filters = filters,
            isGlobalTab = false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredGlobalJobs: StateFlow<List<JobPost>> = combine(
        _globalJobs,
        _searchQuery,
        _filterState
    ) { jobs, query, filters ->
        JobRepository.filterAndSortJobs(
            jobs = jobs,
            query = query,
            filters = filters,
            isGlobalTab = true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var jobsListenerJob: Job? = null
    private var userListenerJob: Job? = null
    private var detailListenerJob: Job? = null

    fun setCustomerId(uid: String) {
        val clean = uid.trim()
        if (_customerId.value == clean && userListenerJob != null) return
        _customerId.value = clean
        userListenerJob?.cancel()

        if (clean.isBlank()) {
            _savedJobs.value = emptyList()
            _myApplications.value = emptyList()
            _jobProfile.value = null
            return
        }

        userListenerJob = viewModelScope.launch {
            launch {
                repository.observeSavedJobs(clean).collect { list ->
                    _savedJobs.value = list
                }
            }
            launch {
                repository.observeMyApplications(clean).collect { list ->
                    _myApplications.value = list
                }
            }
            launch {
                _jobProfile.value = repository.loadJobProfile(clean)
            }
        }
        startJobsListeners(_cityId.value)
    }

    fun setCityId(cityId: String) {
        val clean = cityId.trim().ifBlank { "puerto-princesa" }
        if (_cityId.value == clean && jobsListenerJob != null) return
        _cityId.value = clean
        startJobsListeners(clean)
    }

    private fun startJobsListeners(cityId: String) {
        jobsListenerJob?.cancel()
        if (_customerId.value.isBlank()) {
            _isLocalLoading.value = false
            _isGlobalLoading.value = false
            return
        }
        _isLocalLoading.value = true
        _isGlobalLoading.value = true
        jobsListenerJob = viewModelScope.launch {
            launch {
                repository.observeLocalJobs(cityId).collect { jobs ->
                    _localJobs.value = jobs
                    _isLocalLoading.value = false
                }
            }
            launch {
                repository.observeGlobalJobs(cityId).collect { jobs ->
                    _globalJobs.value = jobs
                    _isGlobalLoading.value = false
                }
            }
        }
    }

    fun observeJobDetail(jobId: String) {
        val cleanId = jobId.trim()
        detailListenerJob?.cancel()
        if (cleanId.isBlank()) {
            _selectedJob.value = null
            _isSelectedJobLoading.value = false
            return
        }
        val cached = _localJobs.value.firstOrNull { it.id == cleanId }
            ?: _globalJobs.value.firstOrNull { it.id == cleanId }
        if (cached != null) {
            _selectedJob.value = cached
            _isSelectedJobLoading.value = false
        } else {
            _selectedJob.value = null
            _isSelectedJobLoading.value = true
        }
        detailListenerJob = viewModelScope.launch {
            repository.observeJob(cleanId).collect { liveJob ->
                _selectedJob.value = liveJob ?: cached
                _isSelectedJobLoading.value = false
            }
        }
    }

    fun selectTab(tab: JobFairTab) {
        _activeTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilters(filters: JobFilterState) {
        _filterState.value = filters
    }

    fun resetFilters() {
        _filterState.value = JobFilterState()
    }

    fun toggleSaveJob(job: JobPost) {
        val uid = _customerId.value
        val currentlySaved = savedJobIds.value.contains(job.id)
        viewModelScope.launch {
            val res = repository.toggleSaveJob(uid, job, currentlySaved)
            res.onSuccess { nowSaved ->
                _toastEvents.tryEmit(if (nowSaved) "Saved to your jobs" else "Removed from saved jobs")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not update saved job")
            }
        }
    }

    fun removeSavedJob(savedItem: SavedJobItem) {
        val placeholderJob = JobPost(
            id = savedItem.jobId,
            title = savedItem.title,
            companyName = savedItem.companyName,
            cityId = savedItem.cityId,
            cityName = savedItem.cityName,
            remote = savedItem.remote,
            workType = savedItem.workType,
            category = savedItem.category,
            salaryMin = savedItem.salaryMin,
            salaryMax = savedItem.salaryMax,
            salaryPeriod = savedItem.salaryPeriod,
            salaryCurrency = savedItem.salaryCurrency,
            hideSalary = savedItem.hideSalary,
            createdAt = savedItem.createdAt
        )
        viewModelScope.launch {
            repository.toggleSaveJob(_customerId.value, placeholderJob, currentlySaved = true)
            _toastEvents.tryEmit("Removed from saved jobs")
        }
    }

    fun getApplicationForJob(jobId: String): JobApplication? {
        return _myApplications.value.firstOrNull {
            it.jobId == jobId && !it.status.equals("withdrawn", ignoreCase = true)
        } ?: _myApplications.value.firstOrNull { it.jobId == jobId }
    }

    fun submitApplication(
        job: JobPost,
        name: String,
        phone: String,
        email: String,
        message: String,
        skills: List<String>,
        experience: String,
        resumeUrl: String,
        saveForNextTime: Boolean,
        onResult: (Result<String>) -> Unit
    ) {
        val uid = _customerId.value
        if (_isSubmittingApplication.value) return
        _isSubmittingApplication.value = true
        viewModelScope.launch {
            val res = repository.submitApplication(
                job = job,
                uid = uid,
                applicantName = name,
                applicantPhone = phone,
                applicantEmail = email,
                message = message,
                skills = skills,
                experience = experience,
                resumeUrl = resumeUrl,
                saveForNextTime = saveForNextTime
            )
            _isSubmittingApplication.value = false
            res.onSuccess {
                if (saveForNextTime) {
                    _jobProfile.value = JobProfile(
                        name = name.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        skills = skills,
                        experience = experience.trim(),
                        resumeUrl = resumeUrl.trim()
                    )
                }
                _toastEvents.tryEmit("Application sent!")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to submit application")
            }
            onResult(res)
        }
    }

    fun withdrawApplication(applicationId: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.withdrawApplication(applicationId)
            res.onSuccess {
                _toastEvents.tryEmit("Application withdrawn")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Failed to withdraw application")
            }
            onDone(res)
        }
    }

    fun reportJob(
        job: JobPost,
        reason: String,
        details: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        val uid = _customerId.value
        if (_isSubmittingReport.value) return
        _isSubmittingReport.value = true
        viewModelScope.launch {
            val res = repository.reportJob(
                jobId = job.id,
                jobTitle = job.title,
                merchantId = job.merchantId,
                reporterId = uid,
                reason = reason,
                details = details
            )
            _isSubmittingReport.value = false
            res.onSuccess {
                _toastEvents.tryEmit("Thank you. Report submitted for review.")
            }.onFailure { err ->
                _toastEvents.tryEmit(err.localizedMessage ?: "Could not submit report")
            }
            onDone(res)
        }
    }
}
