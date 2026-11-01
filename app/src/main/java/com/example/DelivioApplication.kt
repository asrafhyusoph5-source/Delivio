package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.util.CurrentScreenTracker

class DelivioApplication : Application() {

    override fun onCreate() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val prefs = getSharedPreferences("delivio_crash_prefs", Context.MODE_PRIVATE)
                val stackTrace = Log.getStackTraceString(throwable)
                val time = System.currentTimeMillis()
                val screen = CurrentScreenTracker.currentScreen

                prefs.edit()
                    .putBoolean("has_crash", true)
                    .putString("crash_stack_trace", stackTrace)
                    .putLong("crash_time", time)
                    .putString("crash_screen", screen)
                    .commit()
            } catch (e: Exception) {
                Log.e("DelivioApp", "Error saving uncaught crash report", e)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate()
    }
}
