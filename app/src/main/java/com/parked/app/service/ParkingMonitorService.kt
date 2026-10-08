package com.parked.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.parked.app.MainActivity
import com.parked.app.R
import com.parked.app.data.FuelStore
import com.parked.app.data.ParkingStore
import com.parked.app.receiver.ParkingTimer
import com.parked.app.util.Channels
import com.parked.app.util.hasBluetoothPermission
import com.parked.app.util.hasLocationPermission
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** How AutoPark is currently able to run. */
enum class AutoParkMode {
    /** Not running. */
    Off,
    /** Listening for the car and allowed to read location: fully automatic. */
    Full,
    /**
     * Listening for the car, but Android did not allow location access because the
     * service was started in the background (after a reboot, for example). When
     * the car disconnects the user gets a "tap to save" notification instead.
     * Opening the app upgrades the service to [Full].
     */
    Limited,
}

class ParkingMonitorService : Service() {
    // A storage error must not take the whole app down with it.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> parkingCaptureInProgress.set(false) }
    )
    private lateinit var store: ParkingStore
    private lateinit var fuel: FuelStore

    private var lastLocation: Location? = null
    private var trackingLocation = false
    private val parkingCaptureInProgress = AtomicBoolean(false)
    private val lastParkingCaptureStartedAt = AtomicLong(0L)
    private var receiverRegistered = false

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
                // Ignore stationary GPS wander and impossible jumps.
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
        Channels.ensure(this)

        // Enter foreground first: a service started with startForegroundService
        // must do so promptly even if it is about to stop again.
        val mode = enterForeground(preferFull = true)
        if (mode == AutoParkMode.Off || !hasBluetoothPermission(this)) {
            reportProblem()
            stopSelf()
            return
        }
        _mode.value = mode

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // Bluetooth broadcasts come from the system, so the receiver must be exported.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true

        // If AutoPark starts while the phone is already connected to the car (it
        // was switched on mid-drive, or the service was restarted), no CONNECTED
        // broadcast will arrive. Check the audio profiles the car uses instead so
        // the in-car location is still tracked for the parking save.
        checkAlreadyConnected()
    }

    /**
     * Starts foreground with location access when Android allows it, otherwise as
     * a connected-device service that can still hear the car disconnect.
     */
    private fun enterForeground(preferFull: Boolean): AutoParkMode {
        val notification = buildNotification(getString(R.string.autopark_ready))
        if (Build.VERSION.SDK_INT < 29) {
            return runCatching { startForeground(Channels.ID_MONITOR, notification); AutoParkMode.Full }
                .getOrDefault(AutoParkMode.Off)
        }
        val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (preferFull && hasLocationPermission(this)) {
            val full = runCatching {
                ServiceCompat.startForeground(this, Channels.ID_MONITOR, notification, location or connected)
            }
            if (full.isSuccess) return AutoParkMode.Full
        }
        val limited = runCatching {
            ServiceCompat.startForeground(this, Channels.ID_MONITOR, notification, connected)
        }
        if (limited.isSuccess) {
            notifyStatus(getString(R.string.autopark_limited))
            return AutoParkMode.Limited
        }
        return AutoParkMode.Off
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopLocationTracking()
            _mode.value = AutoParkMode.Off
            scope.launch {
                store.setMonitoring(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        // Started again while the app is visible: Android now allows location, so
        // upgrade a limited service to fully automatic.
        if (intent?.getBooleanExtra(EXTRA_FROM_FOREGROUND, false) == true && _mode.value == AutoParkMode.Limited) {
            if (enterForeground(preferFull = true) == AutoParkMode.Full) {
                _mode.value = AutoParkMode.Full
                notifyStatus(getString(R.string.autopark_ready))
                checkAlreadyConnected()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
        stopLocationTracking()
        scope.cancel()
        fuel.close()
        _mode.value = AutoParkMode.Off
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return

            scope.launch {
                val selected = store.current()
                if (!selected.monitoring) return@launch
                val address = runCatching { device.address }.getOrNull() ?: return@launch
                if (address != selected.deviceAddress) return@launch

                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> onCarConnected()
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // Some head units send the same disconnect more than once.
                        val now = SystemClock.elapsedRealtime()
                        val previous = lastParkingCaptureStartedAt.get()
                        if (previous > 0L && now - previous < 15_000L) return@launch
                        if (!lastParkingCaptureStartedAt.compareAndSet(previous, now)) return@launch
                        if (!parkingCaptureInProgress.compareAndSet(false, true)) return@launch

                        // Keep the last in-car fix: a fresh fix often fails in garages.
                        val fallback = lastLocation
                        stopLocationTracking(clearLast = false)
                        captureLocation(fallback)
                    }
                }
            }
        }
    }

    private fun onCarConnected() {
        // A reconnect ends the previous disconnect cycle, so a short trip can park again.
        if (!parkingCaptureInProgress.get()) lastParkingCaptureStartedAt.set(0L)
        notifyStatus(getString(if (_mode.value == AutoParkMode.Limited) R.string.autopark_limited else R.string.autopark_ready))
        startLocationTracking()
    }

    private fun checkAlreadyConnected() {
        scope.launch {
            val address = store.current().deviceAddress ?: return@launch
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return@launch
            if (!hasBluetoothPermission(this@ParkingMonitorService)) return@launch
            for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
                runCatching {
                    adapter.getProfileProxy(this@ParkingMonitorService, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                            val connected = runCatching { proxy.connectedDevices.any { it.address == address } }
                                .getOrDefault(false)
                            runCatching { adapter.closeProfileProxy(p, proxy) }
                            if (connected) onCarConnected()
                        }

                        override fun onServiceDisconnected(p: Int) = Unit
                    }, profile)
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (trackingLocation || _mode.value != AutoParkMode.Full) return
        if (!hasLocationPermission(this)) return
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFine) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_LOW_POWER
        val request = LocationRequest.Builder(priority, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        try {
            trackingLocation = true
            lastLocation = null
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener { trackingLocation = false }
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

    private fun captureLocation(fallback: Location?) {
        // Without location access in the background, don't guess: ask right away.
        if (_mode.value != AutoParkMode.Full || !hasLocationPermission(this)) {
            parkingCaptureInProgress.set(false)
            askUserToSave()
            return
        }

        // The last in-car sample is usually the parking position.
        val fallbackRecent = fallback?.takeIf {
            System.currentTimeMillis() - it.time <= 5 * 60 * 1000L && it.accuracy <= 120f
        }
        if (fallbackRecent != null) {
            persistParking(fallbackRecent)
            return
        }

        // Next Android's cached fix; only then a fresh balanced-power fix.
        try {
            fused.lastLocation.addOnCompleteListener { cachedTask ->
                val cached = if (cachedTask.isSuccessful) cachedTask.result else null
                val cachedRecent = cached?.takeIf {
                    System.currentTimeMillis() - it.time <= 2 * 60 * 1000L && it.accuracy <= 100f
                }
                if (cachedRecent != null) {
                    persistParking(cachedRecent)
                    return@addOnCompleteListener
                }
                try {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnCompleteListener { freshTask ->
                            val fresh = if (freshTask.isSuccessful) freshTask.result else null
                            if (fresh != null) persistParking(fresh) else {
                                parkingCaptureInProgress.set(false)
                                askUserToSave()
                            }
                        }
                } catch (_: SecurityException) {
                    parkingCaptureInProgress.set(false)
                    askUserToSave()
                }
            }
        } catch (_: SecurityException) {
            parkingCaptureInProgress.set(false)
            askUserToSave()
        }
    }

    private fun persistParking(loc: Location) {
        lastLocation = null
        scope.launch {
            try {
                val previous = store.saveParking(loc.latitude, loc.longitude)
                // No undo for automatic saves, so the old spot's photo can go now.
                store.deletePhotoFile(previous.photoPath)
                if (previous.timerEndsAt != null) ParkingTimer.cancel(this@ParkingMonitorService)
                com.parked.app.widget.ParkedWidget.refresh(this@ParkingMonitorService)
                announceDueReminders()
            } finally {
                parkingCaptureInProgress.set(false)
            }
        }

        if (fuel.notificationsEnabled && Channels.canPost(this, Channels.ALERTS)) {
            post(
                Channels.ID_PARKED,
                NotificationCompat.Builder(this, Channels.ALERTS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.notif_parked_title))
                    .setContentText(getString(R.string.notif_parked_body))
                    .setContentIntent(activityIntent(MainActivity.ACTION_FIND_CAR, 2))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        }
        notifyStatus(getString(R.string.autopark_saved))
    }

    /** Location was not available, so let the user save with one tap instead. */
    private fun askUserToSave() {
        if (!Channels.canPost(this, Channels.ALERTS)) return
        post(
            Channels.ID_PARKED,
            NotificationCompat.Builder(this, Channels.ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_tap_to_save_title))
                .setContentText(getString(R.string.notif_tap_to_save_body))
                .setContentIntent(activityIntent(MainActivity.ACTION_SAVE_SPOT, 5))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private fun announceDueReminders() {
        if (!Channels.canPost(this, Channels.SERVICE)) return
        fuel.takeNewlyDueReminders().forEach { r ->
            post(
                Channels.ID_REMINDER_BASE + (r.id % 1000).toInt(),
                NotificationCompat.Builder(this, Channels.SERVICE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.notif_service_due_title, r.name))
                    .setContentText(getString(R.string.notif_service_due_body))
                    .setContentIntent(activityIntent(MainActivity.ACTION_OPEN_SERVICE, 6))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun reportProblem() {
        _mode.value = AutoParkMode.Off
        notifyAutoParkStopped(this)
    }

    private fun activityIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this, requestCode,
        Intent(this, MainActivity::class.java)
            .setAction(action)
            // Lets the app ignore a "tap to save" tapped long after parking, when
            // the phone is no longer next to the car.
            .putExtra(MainActivity.EXTRA_REQUESTED_AT, System.currentTimeMillis())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(text: String): Notification {
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, ParkingMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Channels.MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(activityIntent(MainActivity.ACTION_FIND_CAR, 0))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stopPending)
            .build()
    }

    private fun notifyStatus(text: String) = post(Channels.ID_MONITOR, buildNotification(text))

    private fun post(id: Int, notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java)?.notify(id, notification) }
    }

    companion object {
        const val ACTION_STOP = "com.parked.app.STOP_MONITOR"
        private const val EXTRA_FROM_FOREGROUND = "from_foreground"

        private val _mode = MutableStateFlow(AutoParkMode.Off)
        val mode: StateFlow<AutoParkMode> = _mode

        /**
         * Starts (or upgrades) AutoPark. Returns false when Android refused to
         * start it; the user is then told with a notification.
         */
        fun start(context: Context, fromBackground: Boolean = false): Boolean {
            // Without Bluetooth access the service could not enter the foreground,
            // and Android crashes an app whose foreground service never does.
            if (!hasBluetoothPermission(context)) {
                if (fromBackground) notifyAutoParkStopped(context)
                return false
            }
            val intent = Intent(context, ParkingMonitorService::class.java)
                .putExtra(EXTRA_FROM_FOREGROUND, !fromBackground)
            val ok = runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
            if (!ok && fromBackground) notifyAutoParkStopped(context)
            return ok
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ParkingMonitorService::class.java)) }
            _mode.value = AutoParkMode.Off
        }

        /** Tells the user AutoPark is not running instead of failing silently. */
        fun notifyAutoParkStopped(context: Context) {
            Channels.ensure(context)
            if (!Channels.canPost(context, Channels.ALERTS)) return
            val open = PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.notify(
                    Channels.ID_AUTOPARK_PROBLEM,
                    NotificationCompat.Builder(context, Channels.ALERTS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.notif_autopark_stopped_title))
                        .setContentText(context.getString(R.string.notif_autopark_stopped_body))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }
    }
}
