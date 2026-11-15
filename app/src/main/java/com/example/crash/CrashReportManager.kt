package com.example.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.BuildConfig
import com.example.util.CurrentScreenTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class DiagnosticReport(
    val id: String,
    val timestamp: Long,
    val appVersion: String,
    val threadName: String,
    val exceptionType: String,
    val message: String,
    val stackTrace: String,
    val isFatal: Boolean,
    val documentPath: String? = null,
    val screenName: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("appVersion", appVersion)
        put("threadName", threadName)
        put("exceptionType", exceptionType)
        put("message", message)
        put("stackTrace", stackTrace)
        put("isFatal", isFatal)
        if (!documentPath.isNullOrBlank()) {
            put("documentPath", documentPath)
        }
        put("screenName", screenName)
    }

    companion object {
        fun fromJson(obj: JSONObject): DiagnosticReport {
            val docPath = obj.optString("documentPath", "").ifBlank { null }
            return DiagnosticReport(
                id = obj.optString("id", UUID.randomUUID().toString()),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                appVersion = obj.optString("appVersion", "1.0"),
                threadName = obj.optString("threadName", "unknown"),
                exceptionType = obj.optString("exceptionType", "Exception"),
                message = obj.optString("message", ""),
                stackTrace = obj.optString("stackTrace", ""),
                isFatal = obj.optBoolean("isFatal", true),
                documentPath = docPath,
                screenName = obj.optString("screenName", "")
            )
        }
    }
}

object CrashReportManager {
    private const val TAG = "DelivioCrashManager"
    private const val REPORTS_FILE_NAME = "delivio_crash_reports.json"
    private const val MAX_REPORTS = 5

    const val EXTRA_REPORT_JSON = "extra_crash_report_json"

    @Volatile
    private var appContext: Context? = null

    private val isHandlingCrash = AtomicBoolean(false)
    private val fileLock = Any()

    private val _reports = MutableStateFlow<List<DiagnosticReport>>(emptyList())
    val reports: StateFlow<List<DiagnosticReport>> = _reports.asStateFlow()

    fun init(application: Application) {
        val ctx = application.applicationContext ?: application
        appContext = ctx
        if (isCrashProcess(ctx)) {
            refreshReports(ctx)
            return
        }
        installUncaughtExceptionHandler(ctx)
        refreshReports(ctx)
    }

    fun installUncaughtExceptionHandler(context: Context) {
        appContext = context.applicationContext ?: context
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (!isHandlingCrash.compareAndSet(false, true)) {
                previousHandler?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                val report = recordFatalCrash(context, thread, throwable)
                launchCrashReportActivity(context, report)
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling uncaught crash", t)
            } finally {
                terminateCrashedProcess()
            }
        }
    }

    fun recordFatalCrash(
        context: Context,
        thread: Thread,
        throwable: Throwable
    ): DiagnosticReport {
        val ctx = context.applicationContext ?: context
        val report = DiagnosticReport(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            appVersion = resolveAppVersion(ctx),
            threadName = thread.name.ifBlank { "unknown" },
            exceptionType = throwable.javaClass.name,
            message = throwable.message ?: "",
            stackTrace = Log.getStackTraceString(throwable).ifBlank { throwable.toString() },
            isFatal = true,
            documentPath = null,
            screenName = CurrentScreenTracker.currentScreen
        )
        saveReport(ctx, report)
        return report
    }

    fun recordNonFatalProblem(
        documentPath: String,
        throwable: Throwable,
        context: Context? = appContext,
        details: String? = null
    ): DiagnosticReport {
        val stack = Log.getStackTraceString(throwable).ifBlank { throwable.toString() }
        val msg = buildString {
            if (!details.isNullOrBlank()) {
                append(details)
                if (!throwable.message.isNullOrBlank()) {
                    append(": ")
                }
            }
            append(throwable.message ?: "")
        }.ifBlank { "Failed to parse document" }

        val ctx = context?.applicationContext ?: context ?: appContext
        val report = DiagnosticReport(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            appVersion = ctx?.let { resolveAppVersion(it) } ?: "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            threadName = Thread.currentThread().name.ifBlank { "unknown" },
            exceptionType = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name },
            message = msg,
            stackTrace = stack,
            isFatal = false,
            documentPath = documentPath,
            screenName = CurrentScreenTracker.currentScreen
        )

        Log.w(TAG, "Non-fatal parse error at $documentPath: $msg", throwable)
        saveReport(ctx, report)
        return report
    }

    fun getReports(context: Context? = appContext): List<DiagnosticReport> {
        val ctx = context?.applicationContext ?: context ?: appContext ?: return _reports.value
        synchronized(fileLock) {
            val loaded = readReportsLocked(ctx)
            _reports.value = loaded
            return loaded
        }
    }

    fun refreshReports(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: context ?: appContext ?: return
        synchronized(fileLock) {
            _reports.value = readReportsLocked(ctx)
        }
    }

    fun clearReports(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: context ?: appContext
        synchronized(fileLock) {
            if (ctx != null) {
                try {
                    val file = File(ctx.filesDir, REPORTS_FILE_NAME)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear crash reports file: ${e.message}")
                }
            }
            _reports.value = emptyList()
        }
    }

    fun firstLines(stackTrace: String, maxLines: Int = 40): String {
        val normalized = stackTrace.trim()
        if (normalized.isBlank()) return "No stack trace available."
        return normalized.lines().take(maxLines).joinToString("\n")
    }

    fun formatFullReport(report: DiagnosticReport): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(report.timestamp))
        return buildString {
            appendLine(if (report.isFatal) "Delivio Crash Report" else "Delivio Diagnostic Report (Non-fatal)")
            appendLine("Time: $dateStr")
            appendLine("App Version: ${report.appVersion}")
            appendLine("Thread: ${report.threadName}")
            if (report.screenName.isNotBlank()) {
                appendLine("Screen: ${report.screenName}")
            }
            if (!report.documentPath.isNullOrBlank()) {
                appendLine("Document Path: ${report.documentPath}")
            }
            appendLine("Exception: ${report.exceptionType}${if (report.message.isNotBlank()) ": ${report.message}" else ""}")
            appendLine()
            appendLine("Stack Trace:")
            append(report.stackTrace.ifBlank { "No stack trace available." })
        }
    }

    internal fun saveReport(context: Context?, report: DiagnosticReport) {
        synchronized(fileLock) {
            val current = if (context != null) readReportsLocked(context) else _reports.value
            val updated = (listOf(report) + current).take(MAX_REPORTS)
            if (context != null) {
                writeReportsLocked(context, updated)
            }
            _reports.value = updated
        }
    }

    private fun readReportsLocked(context: Context): List<DiagnosticReport> {
        return try {
            val file = File(context.filesDir, REPORTS_FILE_NAME)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val list = mutableListOf<DiagnosticReport>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(DiagnosticReport.fromJson(obj))
            }
            list.take(MAX_REPORTS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading crash reports: ${e.message}")
            emptyList()
        }
    }

    private fun writeReportsLocked(context: Context, reports: List<DiagnosticReport>) {
        try {
            val file = File(context.filesDir, REPORTS_FILE_NAME)
            val array = JSONArray()
            reports.take(MAX_REPORTS).forEach { array.put(it.toJson()) }
            val payload = array.toString()
            val temp = File(context.filesDir, "$REPORTS_FILE_NAME.tmp")
            temp.writeText(payload)
            if (!temp.renameTo(file)) {
                file.writeText(payload)
                temp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing crash reports: ${e.message}", e)
        }
    }

    private fun launchCrashReportActivity(context: Context, report: DiagnosticReport) {
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(EXTRA_REPORT_JSON, report.toJson().toString())
        }
        context.startActivity(intent)
    }

    private fun terminateCrashedProcess() {
        try {
            Process.killProcess(Process.myPid())
        } catch (_: Throwable) {
        }
        try {
            exitProcess(10)
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAppVersion(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
            "$vName ($vCode)"
        } catch (_: Exception) {
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
    }

    fun isCrashProcess(context: Context): Boolean {
        val processName = getCurrentProcessName(context) ?: return false
        return processName.endsWith(":crash")
    }

    private fun getCurrentProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val name = Application.getProcessName()
            if (!name.isNullOrBlank()) return name
        }
        try {
            val pid = Process.myPid()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val running = am?.runningAppProcesses?.firstOrNull { it.pid == pid }
            if (running != null && !running.processName.isNullOrBlank()) {
                return running.processName
            }
        } catch (_: Exception) {
        }
        return try {
            File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
        } catch (_: Exception) {
            null
        }
    }
}
