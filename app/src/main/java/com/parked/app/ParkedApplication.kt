package com.parked.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the details of the last crash on the phone, so it can be shared from
 * Settings. Nothing is sent anywhere automatically.
 */
class ParkedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                crashFile(this).writeText(
                    buildString {
                        append("Parked! ").append(BuildConfig.VERSION_NAME)
                        append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                        append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
                        append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                        append("Thread: ").append(thread.name).append("\n\n")
                        append(trace.take(20_000))
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(context: Context): File = File(context.applicationContext.filesDir, "last_crash.txt")
    }
}
