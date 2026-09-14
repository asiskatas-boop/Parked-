package com.parked.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("parked_prefs")

data class ParkingState(
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val parkedLat: Double? = null,
    val parkedLng: Double? = null,
    val parkedAt: Long? = null,
    val monitoring: Boolean = false,
)

class ParkingStore(private val context: Context) {
    private object Keys {
        val deviceName = stringPreferencesKey("device_name")
        val deviceAddress = stringPreferencesKey("device_address")
        val parkedLat = doublePreferencesKey("parked_lat")
        val parkedLng = doublePreferencesKey("parked_lng")
        val parkedAt = longPreferencesKey("parked_at")
        val monitoring = booleanPreferencesKey("monitoring")
    }

    val state: Flow<ParkingState> = context.dataStore.data.map { p ->
        ParkingState(
            deviceName = p[Keys.deviceName],
            deviceAddress = p[Keys.deviceAddress],
            parkedLat = p[Keys.parkedLat],
            parkedLng = p[Keys.parkedLng],
            parkedAt = p[Keys.parkedAt],
            monitoring = p[Keys.monitoring] ?: false,
        )
    }

    suspend fun selectDevice(name: String, address: String) = context.dataStore.edit {
        it[Keys.deviceName] = name
        it[Keys.deviceAddress] = address
    }

    suspend fun setMonitoring(enabled: Boolean) = context.dataStore.edit {
        it[Keys.monitoring] = enabled
    }

    suspend fun saveParking(lat: Double, lng: Double, at: Long = System.currentTimeMillis()) = context.dataStore.edit {
        it[Keys.parkedLat] = lat
        it[Keys.parkedLng] = lng
        it[Keys.parkedAt] = at
    }

    suspend fun clearParking() = context.dataStore.edit {
        it.remove(Keys.parkedLat)
        it.remove(Keys.parkedLng)
        it.remove(Keys.parkedAt)
    }
}
