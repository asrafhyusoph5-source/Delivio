package com.example.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Standalone plain light-mode crash report activity running in its own ":crash" process.
 * Deliberately uses zero Compose, MaterialTheme, ViewModel, or Firebase dependencies so it
 * can reliably display even when the main application crashes at startup.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = loadReport()
        val fullReport = if (report != null) {
            CrashReportManager.formatFullReport(report)
        } else {
            "Delivio Crash Report\nNo diagnostic details available."
        }

        val exceptionHeaderText = if (report != null) {
            if (report.message.isNotBlank()) {
                "${report.exceptionType}: ${report.message}"
            } else {
                report.exceptionType
            }
        } else {
            "Unexpected error"
        }

        val metaText = if (report != null) {
            val formattedTime = SimpleDateFormat("MMM dd, yyyy • hh:mm:ss a", Locale.US)
                .format(Date(report.timestamp))
            "Version ${report.appVersion} • Thread: ${report.threadName} • $formattedTime"
        } else {
            ""
        }

        val stackPreview = if (report != null) {
            CrashReportManager.firstLines(report.stackTrace, maxLines = 40)
        } else {
            "No stack trace available."
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
            setPadding(dp(20), dp(24), dp(20), dp(20))
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val titleView = TextView(this).apply {
            text = "The app closed unexpectedly"
            setTextColor(Color.parseColor("#111827"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(titleView)

        // Metadata line (version, thread, time)
        if (metaText.isNotBlank()) {
            val metaView = TextView(this).apply {
                text = metaText
                setTextColor(Color.parseColor("#6B7280"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            }
            root.addView(metaView)
        }

        // Exception type and message in bold
        val exceptionView = TextView(this).apply {
            text = exceptionHeaderText
            setTextColor(Color.parseColor("#B91C1C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(14), 0, dp(12))
        }
        root.addView(exceptionView)

        // Scrollable monospace stack trace box (first 40 lines)
        val stackBoxBg = GradientDrawable().apply {
            setColor(Color.parseColor("#F3F4F6"))
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), Color.parseColor("#E5E7EB"))
        }

        val verticalScroll = ScrollView(this).apply {
            background = stackBoxBg
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val horizontalScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val stackTextView = TextView(this).apply {
            text = stackPreview
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#1F2937"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

        horizontalScroll.addView(stackTextView)
        verticalScroll.addView(horizontalScroll)
        root.addView(verticalScroll)

        // Action Buttons Row (Copy report + Share report)
        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }

        val copyBtn = createActionButton(
            label = "Copy report",
            bgColor = Color.parseColor("#FFFFFF"),
            textColor = Color.parseColor("#111827"),
            strokeColor = Color.parseColor("#D1D5DB")
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                copyReportToClipboard(fullReport)
            }
        }

        val shareBtn = createActionButton(
            label = "Share report",
            bgColor = Color.parseColor("#FFFFFF"),
            textColor = Color.parseColor("#111827"),
            strokeColor = Color.parseColor("#D1D5DB")
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener {
                shareReport(fullReport)
            }
        }

        secondaryRow.addView(copyBtn)
        secondaryRow.addView(shareBtn)
        root.addView(secondaryRow)

        // Primary Restart App Button
        val restartBtn = createActionButton(
            label = "Restart app",
            bgColor = Color.parseColor("#111827"),
            textColor = Color.parseColor("#FFFFFF"),
            strokeColor = null
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                restartMainApp()
            }
        }
        root.addView(restartBtn)

        setContentView(root)
    }

    private fun loadReport(): DiagnosticReport? {
        val jsonExtra = intent?.getStringExtra(CrashReportManager.EXTRA_REPORT_JSON)
        if (!jsonExtra.isNullOrBlank()) {
            try {
                return DiagnosticReport.fromJson(JSONObject(jsonExtra))
            } catch (_: Exception) {
            }
        }
        return CrashReportManager.getReports(this).firstOrNull { it.isFatal }
            ?: CrashReportManager.getReports(this).firstOrNull()
    }

    private fun copyReportToClipboard(fullReport: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Delivio Crash Report", fullReport))
            Toast.makeText(this, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to copy report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareReport(fullReport: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Delivio Crash Report")
                putExtra(Intent.EXTRA_TEXT, fullReport)
            }
            startActivity(Intent.createChooser(shareIntent, "Share crash report"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app available to share report", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartMainApp() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        } catch (_: Exception) {
        }
        finish()
        try {
            Process.killProcess(Process.myPid())
            exitProcess(0)
        } catch (_: Throwable) {
        }
    }

    private fun createActionButton(
        label: String,
        bgColor: Int,
        textColor: Int,
        strokeColor: Int?
    ): Button {
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(10).toFloat()
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            background = bg
            stateListAnimator = null
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
