package com.parked.app.service

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.MainActivity
import com.parked.app.data.ParkingStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ParkingMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: ParkingStore

    override fun onCreate() {
        super.onCreate()
        store = ParkingStore(this)
        createChannel()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        startForeground(1, notification("Watching your car connection"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
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
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        captureLocation(selected.deviceName ?: "car")
                    }
                }
            }
        }
    }

    private suspend fun captureLocation(deviceName: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    scope.launch { store.saveParking(loc.latitude, loc.longitude) }
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(2, NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setContentTitle("Parking location saved")
                        .setContentText("$deviceName disconnected — Parked! saved this spot.")
                        .setAutoCancel(true)
                        .build())
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
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    companion object { const val CHANNEL = "parked_monitor" }
}
