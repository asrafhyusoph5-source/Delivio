package com.example.util

object CurrentScreenTracker {
    @Volatile
    var currentScreen: String = "App Launch"
}
