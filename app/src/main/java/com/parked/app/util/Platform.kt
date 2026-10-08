package com.parked.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parked.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Channels {
    const val MONITOR = "parked_monitor"
    const val ALERTS = "parked_alerts"
    const val TIMER = "parked_timer"
    const val SERVICE = "parked_service"

    // Notification ids
    const val ID_MONITOR = 1
    const val ID_PARKED = 2
    const val ID_TIMER = 3
    const val ID_AUTOPARK_PROBLEM = 4
    const val ID_REMINDER_BASE = 100

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_alerts_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_timer_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_service_desc)
            }
        )
    }

    fun canPost(context: Context, channel: String? = null): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtime || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (channel != null) {
            val ch = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channel)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasBluetoothPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

fun isBluetoothEnabled(context: Context): Boolean {
    if (!hasBluetoothPermission(context)) return false
    return runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)
}

fun isLocationServicesEnabled(context: Context): Boolean {
    val lm = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= 28) {
        lm.isLocationEnabled
    } else {
        runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }
}

/** Great-circle distance in metres. */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
