package com.example.util

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

object OsmConfig {
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            val config = Configuration.getInstance()
            // Set user agent to app package name as requested
            config.userAgentValue = context.packageName.ifEmpty { "com.delivio.app" }
            
            // Configure local cache directory
            val basePath = File(context.cacheDir, "osmdroid")
            if (!basePath.exists()) basePath.mkdirs()
            val tileCache = File(basePath, "tiles")
            if (!tileCache.exists()) tileCache.mkdirs()
            
            config.osmdroidBasePath = basePath
            config.osmdroidTileCache = tileCache
            initialized = true
        } catch (_: Exception) {}
    }
}
