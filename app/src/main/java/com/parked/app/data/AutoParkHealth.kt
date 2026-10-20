package com.parked.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app (not the user) had to switch AutoPark off: a permission was
 * removed, the car is missing, or the service couldn't start after a reboot.
 * Observable, so Home shows the warning the moment it happens, and kept in
 * preferences so it survives a restart until the user deals with it.
 */
object AutoParkHealth {
    private const val PREFS = "parked_ui"
    private const val KEY = "autopark_lost"
    private val state = MutableStateFlow(false)
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lost(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            state.value = prefs(context).getBoolean(KEY, false)
            loaded = true
        }
        return state
    }

    fun markLost(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
        state.value = true
        loaded = true
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        state.value = false
        loaded = true
    }
}
