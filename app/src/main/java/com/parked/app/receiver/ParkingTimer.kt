package com.parked.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.parked.app.MainActivity
import com.parked.app.R
import com.parked.app.util.Channels

/**
 * Parking timer alarms. Two alarms are set: a heads-up [WARNING_MINUTES] before
 * the end (when there is time for one) and one when the time is up.
 */
object ParkingTimer {
    const val WARNING_MINUTES = 10
    private const val ACTION_WARNING = "com.parked.app.TIMER_WARNING"
    private const val ACTION_END = "com.parked.app.TIMER_END"

    /** True when Android will deliver the alarm at the exact minute. */
    fun canBeExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, endsAt: Long) {
        cancel(context)
        val now = System.currentTimeMillis()
        val warnAt = endsAt - WARNING_MINUTES * 60_000L
        if (warnAt > now + 60_000L) set(context, warnAt, ACTION_WARNING, endsAt)
        if (endsAt > now) set(context, endsAt, ACTION_END, endsAt)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context, ACTION_WARNING, 0L))
        am.cancel(pending(context, ACTION_END, 0L))
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(Channels.ID_TIMER)
    }

    private fun set(context: Context, at: Long, action: String, endsAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, action, endsAt)
        runCatching {
            if (canBeExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // Without the exact-alarm permission, ask for delivery inside a
                // short window instead of letting Android defer it indefinitely.
                am.setWindow(AlarmManager.RTC_WAKEUP, at - 60_000L, 60_000L, pi)
            }
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, action: String, endsAt: Long): PendingIntent {
        val intent = Intent(context, ParkingTimerReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ENDS_AT, endsAt)
        return PendingIntent.getBroadcast(
            context,
            if (action == ACTION_WARNING) 10 else 11,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    internal const val EXTRA_ENDS_AT = "ends_at"
    internal fun isWarning(action: String?) = action == ACTION_WARNING
    internal fun isTimerAction(action: String?) = action == ACTION_WARNING || action == ACTION_END
}

class ParkingTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ParkingTimer.isTimerAction(intent.action)) return
        Channels.ensure(context)
        if (!Channels.canPost(context, Channels.TIMER)) return

        val warning = ParkingTimer.isWarning(intent.action)
        val open = PendingIntent.getActivity(
            context, 20,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_FIND_CAR)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (warning) {
            context.getString(R.string.timer_warning_title, ParkingTimer.WARNING_MINUTES)
        } else {
            context.getString(R.string.timer_end_title)
        }
        val notification = NotificationCompat.Builder(context, Channels.TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.timer_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.notify(Channels.ID_TIMER, notification)
        }
    }
}
