package com.parked.app.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parked.app.data.ParkingStore
import com.parked.app.service.ParkingMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores AutoPark and the parking timer after a reboot or an app update (both
 * clear alarms and stop services), and re-sets the timer as an exact alarm once
 * the user allows exact alarms.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val boot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        val exactAllowed = action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!boot && !exactAllowed) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ParkingStore(app)
                val state = store.current()
                state.timerEndsAt?.let { endsAt ->
                    if (endsAt > System.currentTimeMillis()) ParkingTimer.schedule(app, endsAt)
                }
                if (boot && state.monitoring && state.deviceAddress != null) {
                    if (!ParkingMonitorService.start(app, fromBackground = true)) store.setMonitoring(false)
                }
            } catch (_: Exception) {
                // Nothing to restore if storage can't be read; never crash at boot.
            } finally {
                pending.finish()
            }
        }
    }
}
