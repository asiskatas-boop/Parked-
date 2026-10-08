package com.parked.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.parked.app.MainActivity
import com.parked.app.R
import com.parked.app.data.ParkingStore
import com.parked.app.ui.parkedAtLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: when you parked, plus Save spot and Find car buttons. */
class ParkedWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget. Call after the saved spot changes. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(app, ParkedWidget::class.java)) }
                .getOrNull() ?: return
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { render(app, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val state = ParkingStore(context).current()
            val status = if (state.hasSpot && state.parkedAt != null) {
                parkedAtLabel(context, state.parkedAt)
            } else {
                context.getString(R.string.widget_no_spot)
            }
            val views = RemoteViews(context.packageName, R.layout.widget_parked).apply {
                setTextViewText(R.id.widget_status, status)
                setTextViewText(R.id.widget_note, state.note ?: "")
                setViewVisibility(R.id.widget_note, if (state.note.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_save, action(context, MainActivity.ACTION_SAVE_SPOT, 30))
                setOnClickPendingIntent(R.id.widget_find, action(context, MainActivity.ACTION_FIND_CAR, 31))
                setOnClickPendingIntent(R.id.widget_root, action(context, MainActivity.ACTION_FIND_CAR, 32))
            }
            ids.forEach { runCatching { manager.updateAppWidget(it, views) } }
        }

        private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
