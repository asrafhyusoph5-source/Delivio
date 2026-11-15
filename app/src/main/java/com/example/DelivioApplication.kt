package com.example

import android.app.Application
import com.example.crash.CrashReportManager

class DelivioApplication : Application() {

    override fun onCreate() {
        CrashReportManager.init(this)
        super.onCreate()
    }
}
