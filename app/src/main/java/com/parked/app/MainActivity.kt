package com.parked.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.parked.app.ui.AppAction
import com.parked.app.ui.ParkedRoot
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    /** An action from a widget, tile or notification, waiting for the UI to handle it. */
    private var pendingAction by mutableStateOf<AppAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't replay a widget/notification action after a configuration change.
        if (savedInstanceState == null) pendingAction = actionFrom(intent)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "Parked/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
        }
        setContent {
            ParkedRoot(
                pendingAction = pendingAction,
                onActionHandled = { pendingAction = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = actionFrom(intent)
    }

    private fun actionFrom(intent: Intent?): AppAction? {
        // Reopening from Recents replays the launching intent; a save must not repeat.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return actionFor(intent)
    }

    private fun actionFor(intent: Intent): AppAction? = when (intent.action) {
        ACTION_SAVE_SPOT -> {
            // A "tap to save" notification tapped long after parking would save
            // wherever the phone is now, not where the car is. Just open Home then.
            val requestedAt = intent.getLongExtra(EXTRA_REQUESTED_AT, 0L)
            val stale = requestedAt > 0L && System.currentTimeMillis() - requestedAt > SAVE_REQUEST_MAX_AGE_MS
            if (stale) AppAction.FindCar else AppAction.SaveSpot
        }
        ACTION_FIND_CAR -> AppAction.FindCar
        ACTION_OPEN_SETTINGS -> AppAction.OpenSettings
        ACTION_OPEN_SERVICE -> AppAction.OpenService
        else -> null
    }

    companion object {
        const val ACTION_SAVE_SPOT = "com.parked.app.SAVE_SPOT"
        const val ACTION_FIND_CAR = "com.parked.app.FIND_CAR"
        const val ACTION_OPEN_SETTINGS = "com.parked.app.OPEN_SETTINGS"
        const val ACTION_OPEN_SERVICE = "com.parked.app.OPEN_SERVICE"
        const val EXTRA_REQUESTED_AT = "com.parked.app.REQUESTED_AT"
        private const val SAVE_REQUEST_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
