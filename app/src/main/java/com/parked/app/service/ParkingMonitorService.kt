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

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (loc.accuracy > 50f) return
            val prev = lastLocation
            if (prev != null) {
                val d = prev.distanceTo(loc)
                if (d in 10.0..5000.0) {
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
            try { getSystemService(NotificationManager::class.java).cancelAll() } catch (_: Exception) {}
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
                val address = runCatching { device.address }.getOrNull() ?: return@launch
                if (address != selected.deviceAddress) return@launch

                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        notifyStatus("Automatic parking on")
                        startLocationTracking()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            trackingLocation = true
            lastLocation = null
        } catch (_: SecurityException) {}
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
        ) return

        val priority = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        fused.getCurrentLocation(priority, null).addOnCompleteListener { task ->
            val fresh = if (task.isSuccessful) task.result else null
            val chosen = fresh ?: fallback
            if (chosen != null) {
                persistParking(deviceName, chosen)
            } else {
                // One final inexpensive fallback to the fused provider cache.
                fused.lastLocation.addOnSuccessListener { cached ->
                    val recent = cached != null && System.currentTimeMillis() - cached.time <= 10 * 60 * 1000L
                    if (recent && cached != null) persistParking(deviceName, cached)
                    else notifyStatus("Couldn't save parking location")
                }
            }
        }
    }

    private fun persistParking(deviceName: String, loc: Location) {
        scope.launch { store.saveParking(loc.latitude, loc.longitude) }
        lastLocation = null

        val canNotify = android.os.Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (fuel.notificationsEnabled && canNotify) {
            val pending = PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            getSystemService(NotificationManager::class.java).notify(
                2,
                NotificationCompat.Builder(this, ALERT_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Parking saved")
                    .setContentText("$deviceName disconnected — your spot is saved.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
