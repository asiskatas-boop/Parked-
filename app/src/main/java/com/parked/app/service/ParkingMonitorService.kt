package com.parked.app.service

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.parked.app.MainActivity
import com.parked.app.R
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ParkingMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: ParkingStore
    private lateinit var fuel: FuelStore

    private var lastLocation: Location? = null
    private var trackingLocation = false
    private val parkingCaptureInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastParkingCaptureStartedAt = java.util.concurrent.atomic.AtomicLong(0L)

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (loc.accuracy > 50f) return
            val prev = lastLocation
            if (prev != null) {
                val d = prev.distanceTo(loc)
                val elapsedSeconds = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0
                val noiseFloorMeters = maxOf(15.0, prev.accuracy.toDouble() + loc.accuracy.toDouble())
                val speedMps = if (elapsedSeconds > 0) d / elapsedSeconds else Double.POSITIVE_INFINITY

                // Ignore stationary GPS wander and impossible jumps. A raw 10 m
                // threshold can slowly add kilometres while a parked car sits still.
                if (d > noiseFloorMeters && d <= 5000.0 && speedMps <= 70.0) {
                    fuel.addKm(d / 1000.0)
                }
            }
            lastLocation = loc
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = ParkingStore(this)
        fuel = FuelStore(this)
        createChannels()

        try {
            ServiceCompat.startForeground(
                this,
                1,
                buildNotification("Automatic parking on"),
                if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (_: Exception) {
            CoroutineScope(Dispatchers.IO).launch { store.setMonitoring(false) }
            stopSelf()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            CoroutineScope(Dispatchers.IO).launch { store.setMonitoring(false) }
            stopSelf()
            return
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // Bluetooth connection broadcasts originate outside our process, so the
        // receiver must explicitly allow system/privileged senders on modern Android.
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try { getSystemService(NotificationManager::class.java).cancel(1) } catch (_: Exception) {}
            stopLocationTracking()
            scope.launch {
                store.setMonitoring(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        // If Android reclaims the process while monitoring is enabled, ask it to
        // recreate the service. The selected device/monitoring state is persisted.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        stopLocationTracking()
        scope.cancel()
        fuel.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return

            scope.launch {
                val selected = store.state.first()
                if (!selected.monitoring) return@launch
                val address = runCatching { device.address }.getOrNull() ?: return@launch
                if (address != selected.deviceAddress) return@launch

                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // A real reconnect means a previous disconnect cycle is over.
                        // Clear the duplicate-event window only after any prior capture
                        // has completed, so a short legitimate trip can still park.
                        if (!parkingCaptureInProgress.get()) {
                            lastParkingCaptureStartedAt.set(0L)
                        }
                        notifyStatus("Automatic parking on")
                        startLocationTracking()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // Some car/head-unit stacks emit the same disconnect more than
                        // once. Guard both concurrent and immediately repeated events so
                        // one parking action cannot create duplicate saves/alerts.
                        val now = android.os.SystemClock.elapsedRealtime()
                        val previous = lastParkingCaptureStartedAt.get()
                        if (previous > 0L && now - previous < 15_000L) return@launch
                        if (!lastParkingCaptureStartedAt.compareAndSet(previous, now)) return@launch
                        if (!parkingCaptureInProgress.compareAndSet(false, true)) return@launch

                        // Keep the final in-car GPS sample as a fallback. Requesting a
                        // fresh fix can fail in garages exactly when it matters most.
                        val fallback = lastLocation
                        stopLocationTracking(clearLast = false)
                        captureLocation(selected.deviceName ?: "car", fallback)
                    }
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (trackingLocation) return
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return
        val priority = if (hasFine) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_LOW_POWER
        val request = LocationRequest.Builder(priority, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        try {
            trackingLocation = true
            lastLocation = null
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    // A request can fail asynchronously (for example if Location
                    // services are switched off). Do not leave the service believing
                    // it is tracking, otherwise a later reconnect will not retry.
                    trackingLocation = false
                }
        } catch (_: SecurityException) {
            trackingLocation = false
        }
    }

    private fun stopLocationTracking(clearLast: Boolean = true) {
        if (trackingLocation) {
            try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        }
        trackingLocation = false
        if (clearLast) lastLocation = null
    }

    private suspend fun captureLocation(deviceName: String, fallback: Location? = null) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            parkingCaptureInProgress.set(false)
            notifyStatus("Parking detected — location permission needed")
            return
        }

        // Prefer the final in-car sample first. This normally gives Parked! the
        // parking position without waking GPS right after Bluetooth disconnects.
        val fallbackRecent = fallback?.takeIf {
            System.currentTimeMillis() - it.time <= 5 * 60 * 1000L && it.accuracy <= 120f
        }
        if (fallbackRecent != null) {
            persistParking(deviceName, fallbackRecent)
            return
        }

        // Next try Android's fused cache. Only ask for a fresh balanced-power fix
        // when neither cached source is good enough.
        try {
            fused.lastLocation.addOnCompleteListener { cachedTask ->
                val cached = if (cachedTask.isSuccessful) cachedTask.result else null
                val cachedRecent = cached?.takeIf {
                    System.currentTimeMillis() - it.time <= 10 * 60 * 1000L && it.accuracy <= 150f
                }
                if (cachedRecent != null) {
                    persistParking(deviceName, cachedRecent)
                    return@addOnCompleteListener
                }

                try {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnCompleteListener { freshTask ->
                            val fresh = if (freshTask.isSuccessful) freshTask.result else null
                            if (fresh != null) {
                                persistParking(deviceName, fresh)
                            } else {
                                parkingCaptureInProgress.set(false)
                                notifyStatus("Couldn't save parking location")
                            }
                        }
                } catch (_: SecurityException) {
                    parkingCaptureInProgress.set(false)
                    notifyStatus("Parking detected — location permission needed")
                }
            }
        } catch (_: SecurityException) {
            parkingCaptureInProgress.set(false)
            notifyStatus("Parking detected — location permission needed")
        }
    }

    private fun persistParking(deviceName: String, loc: Location) {
        scope.launch { store.saveParking(loc.latitude, loc.longitude) }
        lastLocation = null
        parkingCaptureInProgress.set(false)

        val canNotify = android.os.Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (fuel.notificationsEnabled && canNotify) {
            val pending = PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val logFuelPending = PendingIntent.getActivity(
                this,
                3,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FUEL)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            getSystemService(NotificationManager::class.java).notify(
                2,
                NotificationCompat.Builder(this, ALERT_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Parking saved")
                    .setContentText("Your spot is saved. Log your fuel level when ready.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .addAction(R.drawable.ic_notification, "Log fuel", logFuelPending)
                    .build()
            )
        }
        notifyStatus("Parking spot saved")
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(MONITOR_CHANNEL, "Automatic parking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps Parked! ready to save your spot"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Parking alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Confirms when Parked! saves your car location"
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ParkingMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Parked!")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, "Stop", stopPending)
            .build()
    }

    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    companion object {
        const val MONITOR_CHANNEL = "parked_monitor"
        const val ALERT_CHANNEL = "parked_alerts"
        const val ACTION_STOP = "com.parked.app.STOP_MONITOR"
    }
}
