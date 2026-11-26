package com.example.data.repository

import android.util.Log
import com.example.data.model.JobApplication
import com.example.data.model.JobApplyInfo
import com.example.data.model.JobFilterState
import com.example.data.model.JobPost
import com.example.data.model.JobProfile
import com.example.data.model.SavedJobItem
import com.example.util.FirestoreErrorBus
import com.example.util.FirestoreTimeUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class JobRepository {
    private val tag = "DelivioJobs"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "FirebaseFirestore not initialized: ${e.message}")
            null
        }
    }

    // In-memory store for offline/test unit verification
    internal val inMemoryJobs = ConcurrentHashMap<String, JobPost>()
    internal val inMemorySavedJobs = ConcurrentHashMap<String, SavedJobItem>()
    internal val inMemoryApplications = ConcurrentHashMap<String, JobApplication>()
    internal val inMemoryReports = mutableListOf<Map<String, Any?>>()
    private val inMemoryUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

        fun sortJobs(jobs: List<JobPost>): List<JobPost> {
            return jobs.sortedWith(
                compareByDescending<JobPost> { it.featured }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
        }

        fun mergeAndSortJobs(vararg lists: List<JobPost>): List<JobPost> {
            val byId = LinkedHashMap<String, JobPost>()
            lists.forEach { list ->
                list.forEach { job ->
                    if (job.id.isNotBlank()) {
                        byId[job.id] = job
                    }
                }
            }
            return sortJobs(byId.values.toList())
        }

        fun filterAndSortJobs(
            jobs: List<JobPost>,
            query: String,
            filters: JobFilterState,
            isGlobalTab: Boolean,
            nowMillis: Long = System.currentTimeMillis()
        ): List<JobPost> {
            val cleanQuery = query.trim().lowercase(Locale.ROOT)
            val filterWorkType = filters.workType?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
            val filterCategory = filters.category?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
            val filterMinSalary = filters.minSalary?.takeIf { it > 0.0 }
            val filterCityId = if (isGlobalTab) filters.cityId?.trim()?.ifBlank { null } else null

            val filtered = jobs.filter { job ->
                val matchesSearch = if (cleanQuery.isEmpty()) {
                    true
                } else {
                    job.title.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        job.companyName.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        job.cityName.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        job.category.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        job.description.lowercase(Locale.ROOT).contains(cleanQuery)
                }

                val matchesWorkType = filterWorkType == null ||
                    job.workType.trim().lowercase(Locale.ROOT) == filterWorkType

                val matchesCategory = filterCategory == null ||
                    job.category.trim().lowercase(Locale.ROOT) == filterCategory

                val matchesRemote = !filters.remoteOnly || job.remote

                val matchesRecent = !filters.last7DaysOnly ||
                    (job.createdAt > 0L && (nowMillis - job.createdAt) in 0L..SEVEN_DAYS_MS)

                val matchesSalary = if (filterMinSalary == null) {
                    true
                } else if (job.hideSalary) {
                    false
                } else {
                    val effectiveMax = job.salaryMax ?: job.salaryMin ?: 0.0
                    effectiveMax >= filterMinSalary
                }

                val matchesCity = filterCityId == null ||
                    job.cityId.equals(filterCityId, ignoreCase = true)

                matchesSearch &&
                    matchesWorkType &&
                    matchesCategory &&
                    matchesRemote &&
                    matchesRecent &&
                    matchesSalary &&
                    matchesCity
            }

            return sortJobs(filtered)
        }
    }

    internal fun parseJobDocument(doc: DocumentSnapshot): JobPost? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return try {
            val applyMap = data["apply"] as? Map<*, *>
            val applyMethodRaw = (applyMap?.get("method") as? String)
                ?: (data["applyMethod"] as? String)
                ?: "in_app"
            val apply = JobApplyInfo(
                method = applyMethodRaw.trim().lowercase(Locale.ROOT).ifBlank { "in_app" },
                phone = ((applyMap?.get("phone") as? String) ?: (data["applyPhone"] as? String) ?: "").trim(),
                email = ((applyMap?.get("email") as? String) ?: (data["applyEmail"] as? String) ?: "").trim(),
                url = ((applyMap?.get("url") as? String) ?: (data["applyUrl"] as? String) ?: "").trim(),
                instructions = ((applyMap?.get("instructions") as? String) ?: "").trim()
            )

            @Suppress("UNCHECKED_CAST")
            val reqs = parseStringList(data["requirements"])
            val benefits = parseStringList(data["benefits"])

            val locationMap = data["location"] as? Map<*, *>
            val lat = (data["lat"] as? Number)?.toDouble()
                ?: (locationMap?.get("lat") as? Number)?.toDouble()
            val lng = (data["lng"] as? Number)?.toDouble()
                ?: (locationMap?.get("lng") as? Number)?.toDouble()

            JobPost(
                id = doc.id,
                title = (data["title"] as? String).orEmpty().trim(),
                companyName = ((data["companyName"] as? String) ?: (data["company"] as? String)).orEmpty().trim(),
                merchantId = (data["merchantId"] as? String)?.trim()?.ifBlank { null },
                postedBy = (data["postedBy"] as? String).orEmpty().trim().ifBlank { "admin" },
                cityId = (data["cityId"] as? String).orEmpty().trim(),
                cityName = (data["cityName"] as? String).orEmpty().trim(),
                reach = (data["reach"] as? String).orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "local" },
                remote = (data["remote"] as? Boolean) ?: false,
                workType = (data["workType"] as? String).orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "full_time" },
                category = (data["category"] as? String).orEmpty().trim(),
                salaryMin = (data["salaryMin"] as? Number)?.toDouble(),
                salaryMax = (data["salaryMax"] as? Number)?.toDouble(),
                salaryPeriod = (data["salaryPeriod"] as? String).orEmpty().trim().ifBlank { "monthly" },
                salaryCurrency = (data["salaryCurrency"] as? String).orEmpty().trim().ifBlank { "₱" },
                hideSalary = (data["hideSalary"] as? Boolean) ?: false,
                description = (data["description"] as? String).orEmpty().trim(),
                requirements = reqs,
                benefits = benefits,
                schedule = (data["schedule"] as? String).orEmpty().trim(),
                address = (data["address"] as? String).orEmpty().trim(),
                lat = lat,
                lng = lng,
                vacancies = (data["vacancies"] as? Number)?.toInt(),
                deadline = FirestoreTimeUtil.readTimeMillis(data["deadline"]),
                featured = (data["featured"] as? Boolean) ?: false,
                status = (data["status"] as? String).orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "open" },
                apply = apply,
                createdAt = FirestoreTimeUtil.readTimeMillis(doc, "createdAt") ?: 0L,
                updatedAt = FirestoreTimeUtil.readTimeMillis(doc, "updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed parsing job ${doc.id}: ${e.message}")
            null
        }
    }

    private fun parseStringList(raw: Any?): List<String> = when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is String -> raw.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    internal fun parseJobApplicationDocument(doc: DocumentSnapshot): JobApplication? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return try {
            // Note: employerNote is intentionally never read or surfaced to customer UI.
            JobApplication(
                id = doc.id,
                jobId = (data["jobId"] as? String).orEmpty(),
                jobMerchantId = (data["jobMerchantId"] as? String)?.trim()?.ifBlank { null },
                jobTitle = (data["jobTitle"] as? String).orEmpty(),
                companyName = (data["companyName"] as? String).orEmpty(),
                cityId = (data["cityId"] as? String).orEmpty(),
                cityName = (data["cityName"] as? String).orEmpty(),
                applicantId = (data["applicantId"] as? String).orEmpty(),
                applicantName = (data["applicantName"] as? String).orEmpty(),
                applicantPhone = (data["applicantPhone"] as? String).orEmpty(),
                applicantEmail = (data["applicantEmail"] as? String).orEmpty(),
                message = (data["message"] as? String).orEmpty(),
                skills = parseStringList(data["skills"]),
                experience = (data["experience"] as? String).orEmpty(),
                resumeUrl = (data["resumeUrl"] as? String).orEmpty(),
                status = (data["status"] as? String).orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "new" },
                createdAt = FirestoreTimeUtil.readTimeMillis(doc, "createdAt") ?: 0L,
                updatedAt = FirestoreTimeUtil.readTimeMillis(doc, "updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed parsing jobApplication ${doc.id}: ${e.message}")
            null
        }
    }

    private fun parseSavedJobDocument(doc: DocumentSnapshot): SavedJobItem? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return try {
            SavedJobItem(
                jobId = (data["jobId"] as? String)?.ifBlank { null } ?: doc.id,
                title = (data["title"] as? String).orEmpty(),
                companyName = (data["companyName"] as? String).orEmpty(),
                cityId = (data["cityId"] as? String).orEmpty(),
                cityName = (data["cityName"] as? String).orEmpty(),
                remote = (data["remote"] as? Boolean) ?: false,
                workType = (data["workType"] as? String).orEmpty().ifBlank { "full_time" },
                category = (data["category"] as? String).orEmpty(),
                salaryMin = (data["salaryMin"] as? Number)?.toDouble(),
                salaryMax = (data["salaryMax"] as? Number)?.toDouble(),
                salaryPeriod = (data["salaryPeriod"] as? String).orEmpty().ifBlank { "monthly" },
                salaryCurrency = (data["salaryCurrency"] as? String).orEmpty().ifBlank { "₱" },
                hideSalary = (data["hideSalary"] as? Boolean) ?: false,
                createdAt = FirestoreTimeUtil.readTimeMillis(data["createdAt"]) ?: 0L,
                savedAt = FirestoreTimeUtil.readTimeMillis(doc, "savedAt") ?: 0L
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed parsing savedJob ${doc.id}: ${e.message}")
            null
        }
    }

    /**
     * Local jobs: equality query where status == "open" and cityId == myCityId; sorted on device.
     */
    fun observeLocalJobs(cityId: String): Flow<List<JobPost>> = callbackFlow {
        val fs = firestore
        val cleanCityId = cityId.trim()
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null || cleanCityId.isBlank()) {
            trySend(sortJobs(inMemoryJobs.values.filter {
                it.status.equals("open", ignoreCase = true) && it.cityId.equals(cleanCityId, ignoreCase = true)
            }))
            awaitClose {}
            return@callbackFlow
        }
        if (currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = fs.collection("jobs")
            .whereEqualTo("status", "open")
            .whereEqualTo("cityId", cleanCityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "observeLocalJobs error: ${error.code.name}")
                    if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        FirestoreErrorBus.showNonFatalBanner("jobs", error.code.name)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents?.mapNotNull { parseJobDocument(it) }.orEmpty()
                trySend(sortJobs(parsed))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Global jobs: merges two equality queries (no composite index / no orderBy):
     *   1) status == "open" && reach == "global"
     *   2) status == "open" && cityId == myCityId
     */
    fun observeGlobalJobs(myCityId: String): Flow<List<JobPost>> = callbackFlow {
        val fs = firestore
        val cleanCityId = myCityId.trim()
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (fs == null) {
            trySend(sortJobs(inMemoryJobs.values.filter {
                it.status.equals("open", ignoreCase = true) &&
                    (it.reach.equals("global", ignoreCase = true) || it.cityId.equals(cleanCityId, ignoreCase = true))
            }))
            awaitClose {}
            return@callbackFlow
        }
        if (currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        var globalList: List<JobPost> = emptyList()
        var localList: List<JobPost> = emptyList()

        fun emitMerged() {
            trySend(mergeAndSortJobs(globalList, localList))
        }

        val globalListener: ListenerRegistration = fs.collection("jobs")
            .whereEqualTo("status", "open")
            .whereEqualTo("reach", "global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "observeGlobalJobs(global) error: ${error.code.name}")
                    emitMerged()
                    return@addSnapshotListener
                }
                globalList = snapshot?.documents?.mapNotNull { parseJobDocument(it) }.orEmpty()
                emitMerged()
            }

        val localListener: ListenerRegistration? = if (cleanCityId.isNotBlank()) {
            fs.collection("jobs")
                .whereEqualTo("status", "open")
                .whereEqualTo("cityId", cleanCityId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "observeGlobalJobs(local) error: ${error.code.name}")
                        emitMerged()
                        return@addSnapshotListener
                    }
                    localList = snapshot?.documents?.mapNotNull { parseJobDocument(it) }.orEmpty()
                    emitMerged()
                }
        } else null

        awaitClose {
            globalListener.remove()
            localListener?.remove()
        }
    }

    fun observeJob(jobId: String): Flow<JobPost?> = callbackFlow {
        val cleanId = jobId.trim()
        val fs = firestore
        if (cleanId.isBlank()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        if (fs == null) {
            trySend(inMemoryJobs[cleanId])
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("jobs").document(cleanId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "observeJob($cleanId) error: ${error.code.name}")
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(if (snapshot != null && snapshot.exists()) parseJobDocument(snapshot) else null)
            }
        awaitClose { listener.remove() }
    }

    fun observeSavedJobs(uid: String): Flow<List<SavedJobItem>> = callbackFlow {
        val cleanUid = uid.trim()
        val fs = firestore
        if (cleanUid.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        if (fs == null) {
            trySend(inMemorySavedJobs.values.sortedByDescending { it.savedAt })
            awaitClose {}
            return@callbackFlow
        }
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("users").document(cleanUid).collection("savedJobs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "observeSavedJobs error: ${error.code.name}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents
                    ?.mapNotNull { parseSavedJobDocument(it) }
                    ?.sortedByDescending { it.savedAt }
                    .orEmpty()
                trySend(parsed)
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleSaveJob(uid: String, job: JobPost, currentlySaved: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank() || job.id.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please sign in to save jobs."))
        }
        val fs = firestore
        if (fs == null) {
            return@withContext if (currentlySaved) {
                inMemorySavedJobs.remove(job.id)
                inMemoryUpdates.tryEmit(Unit)
                Result.success(false)
            } else {
                inMemorySavedJobs[job.id] = SavedJobItem(
                    jobId = job.id,
                    title = job.title,
                    companyName = job.companyName,
                    cityId = job.cityId,
                    cityName = job.cityName,
                    remote = job.remote,
                    workType = job.workType,
                    category = job.category,
                    salaryMin = job.salaryMin,
                    salaryMax = job.salaryMax,
                    salaryPeriod = job.salaryPeriod,
                    salaryCurrency = job.salaryCurrency,
                    hideSalary = job.hideSalary,
                    createdAt = job.createdAt,
                    savedAt = System.currentTimeMillis()
                )
                inMemoryUpdates.tryEmit(Unit)
                Result.success(true)
            }
        }
        try {
            val docRef = fs.collection("users").document(cleanUid)
                .collection("savedJobs").document(job.id)
            if (currentlySaved) {
                docRef.delete().await()
                Result.success(false)
            } else {
                val data = hashMapOf<String, Any?>(
                    "jobId" to job.id,
                    "title" to job.title,
                    "companyName" to job.companyName,
                    "cityId" to job.cityId,
                    "cityName" to job.cityName,
                    "remote" to job.remote,
                    "workType" to job.workType,
                    "category" to job.category,
                    "salaryMin" to job.salaryMin,
                    "salaryMax" to job.salaryMax,
                    "salaryPeriod" to job.salaryPeriod,
                    "salaryCurrency" to job.salaryCurrency,
                    "hideSalary" to job.hideSalary,
                    "createdAt" to job.createdAt,
                    "savedAt" to FirestoreTimeUtil.serverTs()
                )
                docRef.set(data).await()
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "toggleSaveJob failed", e)
            Result.failure(e)
        }
    }

    fun observeMyApplications(uid: String): Flow<List<JobApplication>> = callbackFlow {
        val cleanUid = uid.trim()
        val fs = firestore
        if (cleanUid.isBlank()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        if (fs == null) {
            trySend(inMemoryApplications.values.filter { it.applicantId == cleanUid }.sortedByDescending { it.createdAt })
            awaitClose {}
            return@callbackFlow
        }
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val listener = fs.collection("jobApplications")
            .whereEqualTo("applicantId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "observeMyApplications error: ${error.code.name}")
                    if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        FirestoreErrorBus.showNonFatalBanner("job applications", error.code.name)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents
                    ?.mapNotNull { parseJobApplicationDocument(it) }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()
                trySend(parsed)
            }
        awaitClose { listener.remove() }
    }

    suspend fun loadJobProfile(uid: String): JobProfile? = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val fs = firestore ?: return@withContext null
        if (cleanUid.isBlank()) return@withContext null
        try {
            val snap = fs.collection("users").document(cleanUid).get().await()
            val jp = snap.get("jobProfile") as? Map<*, *> ?: return@withContext null
            JobProfile(
                name = (jp["name"] as? String).orEmpty(),
                phone = (jp["phone"] as? String).orEmpty(),
                email = (jp["email"] as? String).orEmpty(),
                skills = parseStringList(jp["skills"]),
                experience = (jp["experience"] as? String).orEmpty(),
                resumeUrl = (jp["resumeUrl"] as? String).orEmpty()
            )
        } catch (e: Exception) {
            Log.w(tag, "loadJobProfile failed: ${e.message}")
            null
        }
    }

    suspend fun saveJobProfile(uid: String, profile: JobProfile): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        val fs = firestore ?: return@withContext Result.success(Unit)
        if (cleanUid.isBlank()) return@withContext Result.failure(IllegalStateException("User not signed in"))
        try {
            val map = mapOf(
                "name" to profile.name.trim(),
                "phone" to profile.phone.trim(),
                "email" to profile.email.trim(),
                "skills" to profile.skills,
                "experience" to profile.experience.trim(),
                "resumeUrl" to profile.resumeUrl.trim(),
                "updatedAt" to FirestoreTimeUtil.serverTs()
            )
            fs.collection("users").document(cleanUid)
                .update(mapOf("jobProfile" to map))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "saveJobProfile failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun submitApplication(
        job: JobPost,
        uid: String,
        applicantName: String,
        applicantPhone: String,
        applicantEmail: String,
        message: String,
        skills: List<String>,
        experience: String,
        resumeUrl: String,
        saveForNextTime: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please sign in to apply."))
        }
        if (!job.isAcceptingApplications) {
            return@withContext Result.failure(IllegalStateException("No longer accepting applications"))
        }

        val docId = "${job.id}_$cleanUid"
        val fs = firestore
        if (fs == null) {
            if (inMemoryApplications.containsKey(docId)) {
                return@withContext Result.failure(IllegalStateException("You already applied"))
            }
            val now = System.currentTimeMillis()
            inMemoryApplications[docId] = JobApplication(
                id = docId,
                jobId = job.id,
                jobMerchantId = job.merchantId,
                jobTitle = job.title,
                companyName = job.companyName,
                cityId = job.cityId,
                cityName = job.cityName,
                applicantId = cleanUid,
                applicantName = applicantName.trim(),
                applicantPhone = applicantPhone.trim(),
                applicantEmail = applicantEmail.trim(),
                message = message.trim(),
                skills = skills,
                experience = experience.trim(),
                resumeUrl = resumeUrl.trim(),
                status = "new",
                createdAt = now,
                updatedAt = now
            )
            return@withContext Result.success(docId)
        }

        try {
            // 1. Verify job is still open & not expired
            val liveJobSnap = fs.collection("jobs").document(job.id).get().await()
            val liveJob = parseJobDocument(liveJobSnap)
                ?: return@withContext Result.failure(IllegalStateException("No longer accepting applications"))
            if (!liveJob.isAcceptingApplications) {
                return@withContext Result.failure(IllegalStateException("No longer accepting applications"))
            }

            // 2. Prevent duplicate application per deterministic doc id
            val appRef = fs.collection("jobApplications").document(docId)
            val existingSnap = try { appRef.get().await() } catch (_: Exception) { null }
            if (existingSnap != null && existingSnap.exists()) {
                return@withContext Result.failure(IllegalStateException("You already applied"))
            }

            // 3. Build payload aligned with firestore.rules:
            //    jobApplications/{aid}: jobId, jobMerchantId == job.merchantId, applicantId == uid, status == 'new', createdAt == request.time
            val payload = hashMapOf<String, Any?>(
                "jobId" to liveJob.id,
                "jobMerchantId" to liveJob.merchantId,
                "jobTitle" to liveJob.title,
                "companyName" to liveJob.companyName,
                "cityId" to liveJob.cityId,
                "cityName" to liveJob.cityName,
                "applicantId" to cleanUid,
                "applicantName" to applicantName.trim(),
                "applicantPhone" to applicantPhone.trim(),
                "applicantEmail" to applicantEmail.trim(),
                "message" to message.trim(),
                "skills" to skills,
                "experience" to experience.trim(),
                "resumeUrl" to resumeUrl.trim(),
                "status" to "new",
                "createdAt" to FirestoreTimeUtil.serverTs(),
                "updatedAt" to FirestoreTimeUtil.serverTs()
            )
            appRef.set(payload).await()

            if (saveForNextTime) {
                saveJobProfile(
                    uid = cleanUid,
                    profile = JobProfile(
                        name = applicantName.trim(),
                        phone = applicantPhone.trim(),
                        email = applicantEmail.trim(),
                        skills = skills,
                        experience = experience.trim(),
                        resumeUrl = resumeUrl.trim()
                    )
                )
            }
            Result.success(docId)
        } catch (e: Exception) {
            Log.e(tag, "submitApplication failed", e)
            val errMsg = if (e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                "You already applied or this job is no longer accepting applications."
            } else {
                e.localizedMessage ?: e.message ?: "Failed to submit application."
            }
            Result.failure(Exception(errMsg, e))
        }
    }

    suspend fun withdrawApplication(applicationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = applicationId.trim()
        if (cleanId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Missing application id"))
        val fs = firestore
        if (fs == null) {
            inMemoryApplications[cleanId]?.let { existing ->
                inMemoryApplications[cleanId] = existing.copy(status = "withdrawn", updatedAt = System.currentTimeMillis())
            }
            return@withContext Result.success(Unit)
        }
        try {
            fs.collection("jobApplications").document(cleanId)
                .update(
                    mapOf(
                        "status" to "withdrawn",
                        "updatedAt" to FirestoreTimeUtil.serverTs()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "withdrawApplication failed", e)
            Result.failure(e)
        }
    }

    suspend fun reportJob(
        jobId: String,
        jobTitle: String,
        merchantId: String?,
        reporterId: String,
        reason: String,
        details: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanReporterId = reporterId.trim()
        if (cleanReporterId.isBlank() || jobId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Please sign in to report a job post."))
        }
        val payload = hashMapOf<String, Any?>(
            "jobId" to jobId.trim(),
            "jobTitle" to jobTitle.trim(),
            "jobMerchantId" to merchantId,
            "reporterId" to cleanReporterId,
            "reason" to reason.trim(),
            "details" to details.trim(),
            "status" to "open",
            "createdAt" to FirestoreTimeUtil.serverTs()
        )
        val fs = firestore
        if (fs == null) {
            inMemoryReports.add(payload)
            return@withContext Result.success(Unit)
        }
        try {
            fs.collection("jobReports").document().set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "reportJob failed", e)
            Result.failure(e)
        }
    }
}
