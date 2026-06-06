package com.example.videolibrarymanager

import android.app.Application
import com.example.videolibrarymanager.util.BugLogger

/**
 * Application subclass — the single guaranteed early-init point.
 * BugLogger is initialized here so every component (Activity, Service,
 * ViewModel, etc.) can log from the very first line of code that runs.
 *
 * Registered in AndroidManifest.xml via android:name=".VlmApplication".
 */
class VlmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        BugLogger.init(this)
        BugLogger.info(TAG, "VlmApplication.onCreate — process started")
        BugLogger.info(TAG, "Device API level: ${android.os.Build.VERSION.SDK_INT}")
        BugLogger.info(TAG, "Device model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        BugLogger.warn(TAG, "onLowMemory — system is under memory pressure")
    }

    companion object {
        private const val TAG = "VlmApplication"
    }
}
