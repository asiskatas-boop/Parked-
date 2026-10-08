package com.parked.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parked.app.data.ParkingStore
import com.parked.app.service.ParkingMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores AutoPark and the parking timer after a reboot or an app update.
 * Android clears alarms and stops services in both cases.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = ParkingStore(app).current()
                state.timerEndsAt?.let { endsAt ->
                    if (endsAt > System.currentTimeMillis()) ParkingTimer.schedule(app, endsAt)
                }
                if (state.monitoring && state.deviceAddress != null) {
                    ParkingMonitorService.start(app, fromBackground = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
