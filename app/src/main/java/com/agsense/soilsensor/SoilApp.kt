package com.agsense.soilsensor

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/** Catches any crash and opens CrashActivity (separate process) showing the stack trace, so it can be copied/shared. */
class SoilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("SoilSensorReader", "Uncaught exception", throwable)
                val trace = Log.getStackTraceString(throwable)
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .putExtra(CrashActivity.EXTRA_TRACE, "v${BuildConfig.VERSION_NAME}\n\n$trace")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
