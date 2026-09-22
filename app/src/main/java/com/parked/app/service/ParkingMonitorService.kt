package com.parked.app.service

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.parked.app.MainActivity
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
        createChannel()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        try {
            startForeground(1, notification("Watching your car connection"))
        } catch (_: Exception) {
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        stopLocationTracking()
        scope.cancel()
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
                if (device.address != selected.deviceAddress) return@launch

                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        notifyStatus("Connected to ${selected.deviceName ?: "car"}")
                        startLocationTracking()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        stopLocationTracking()
                        captureLocation(selected.deviceName ?: "car")
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

    private fun stopLocationTracking() {
        if (!trackingLocation) return
        try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        trackingLocation = false
        lastLocation = null
    }

    private suspend fun captureLocation(deviceName: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    scope.launch { store.saveParking(loc.latitude, loc.longitude) }

                    if (fuel.notificationsEnabled) {
                        getSystemService(NotificationManager::class.java).notify(2,
                            NotificationCompat.Builder(this, CHANNEL)
                                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                                .setContentTitle("Parking location saved")
                                .setContentText("$deviceName disconnected — Parked! saved this spot.")
                                .setAutoCancel(true).build())
                    }
                    notifyStatus("Parking spot saved")
                }
            }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Parked! monitoring", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Parked!")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true).build()
    }

    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    companion object { const val CHANNEL = "parked_monitor" }
}
