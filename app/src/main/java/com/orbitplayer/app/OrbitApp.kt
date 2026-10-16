package com.orbitplayer.app

import android.app.Application
import android.util.Log

/**
 * Saves the stack trace from fatal crashes locally. Nothing is sent online.
 * If the player closes unexpectedly, a human-readable report is shown when the app is reopened.
 */
class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = Log.getStackTraceString(throwable).take(12_000)
                getSharedPreferences("orbit_diagnostics", MODE_PRIVATE)
                    .edit().putString("last_error", report).commit()
            } catch (_: Exception) {
                // The Android default handler still handles the crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
