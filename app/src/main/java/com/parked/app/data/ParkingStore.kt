package com.parked.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.dataStore by preferencesDataStore("parked_prefs")

data class ParkingState(
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val parkedLat: Double? = null,
    val parkedLng: Double? = null,
    val parkedAt: Long? = null,
    val monitoring: Boolean = false,
    val note: String? = null,
    val photoPath: String? = null,
    val timerEndsAt: Long? = null,
    val historyEnabled: Boolean = false,
    val history: List<ParkingHistoryEntry> = emptyList(),
) {
    val hasSpot: Boolean
        get() = parkedLat != null && parkedLng != null && !(parkedLat == 0.0 && parkedLng == 0.0)
}

data class ParkingHistoryEntry(
    val lat: Double,
    val lng: Double,
    val at: Long,
    val note: String?,
)

/** Everything about the current spot, so a save can be undone exactly. */
data class SavedSpot(
    val lat: Double?,
    val lng: Double?,
    val at: Long?,
    val note: String?,
    val photoPath: String?,
    val timerEndsAt: Long?,
)

class ParkingStore(context: Context) {
    private val context = context.applicationContext

    private object Keys {
        val deviceName = stringPreferencesKey("device_name")
        val deviceAddress = stringPreferencesKey("device_address")
        val parkedLat = doublePreferencesKey("parked_lat")
        val parkedLng = doublePreferencesKey("parked_lng")
        val parkedAt = longPreferencesKey("parked_at")
        val monitoring = booleanPreferencesKey("monitoring")
        val note = stringPreferencesKey("spot_note")
        val photoPath = stringPreferencesKey("spot_photo")
        val timerEndsAt = longPreferencesKey("timer_ends_at")
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val history = stringPreferencesKey("history_json")
    }

    val state: Flow<ParkingState> = this.context.dataStore.data.map { p ->
        ParkingState(
            deviceName = p[Keys.deviceName],
            deviceAddress = p[Keys.deviceAddress],
            parkedLat = p[Keys.parkedLat],
            parkedLng = p[Keys.parkedLng],
            parkedAt = p[Keys.parkedAt],
            monitoring = p[Keys.monitoring] ?: false,
            note = p[Keys.note],
            photoPath = p[Keys.photoPath],
            timerEndsAt = p[Keys.timerEndsAt],
            historyEnabled = p[Keys.historyEnabled] ?: false,
            history = parseHistory(p[Keys.history]),
        )
    }

    suspend fun current(): ParkingState = state.first()

    suspend fun selectDevice(name: String, address: String) = context.dataStore.edit {
        it[Keys.deviceName] = name
        it[Keys.deviceAddress] = address
    }

    suspend fun setMonitoring(enabled: Boolean) = context.dataStore.edit {
        it[Keys.monitoring] = enabled
    }

    /**
     * Saves a new spot. The note, photo and timer belonged to the previous spot,
     * so they are cleared. Returns the previous spot so the caller can offer Undo.
     */
    suspend fun saveParking(lat: Double, lng: Double, at: Long = System.currentTimeMillis()): SavedSpot {
        var previous: SavedSpot? = null
        context.dataStore.edit {
            previous = SavedSpot(
                lat = it[Keys.parkedLat], lng = it[Keys.parkedLng], at = it[Keys.parkedAt],
                note = it[Keys.note], photoPath = it[Keys.photoPath], timerEndsAt = it[Keys.timerEndsAt],
            )
            it[Keys.parkedLat] = lat
            it[Keys.parkedLng] = lng
            it[Keys.parkedAt] = at
            it.remove(Keys.note)
            it.remove(Keys.photoPath)
            it.remove(Keys.timerEndsAt)
            if (it[Keys.historyEnabled] == true) {
                val updated = listOf(ParkingHistoryEntry(lat, lng, at, null)) + parseHistory(it[Keys.history])
                it[Keys.history] = encodeHistory(updated.take(MAX_HISTORY))
            }
        }
        return previous!!
    }

    /** Puts back a spot captured by [saveParking]; used by Undo. */
    suspend fun restore(spot: SavedSpot) {
        var replacedPhoto: String? = null
        context.dataStore.edit {
            replacedPhoto = it[Keys.photoPath]
            val replacedAt = it[Keys.parkedAt]
            if (spot.lat != null && spot.lng != null && spot.at != null) {
                it[Keys.parkedLat] = spot.lat
                it[Keys.parkedLng] = spot.lng
                it[Keys.parkedAt] = spot.at
            } else {
                it.remove(Keys.parkedLat); it.remove(Keys.parkedLng); it.remove(Keys.parkedAt)
            }
            if (spot.note != null) it[Keys.note] = spot.note else it.remove(Keys.note)
            if (spot.photoPath != null) it[Keys.photoPath] = spot.photoPath else it.remove(Keys.photoPath)
            if (spot.timerEndsAt != null) it[Keys.timerEndsAt] = spot.timerEndsAt else it.remove(Keys.timerEndsAt)
            // The undone save must not stay in history either.
            if (replacedAt != null) {
                val history = parseHistory(it[Keys.history])
                if (history.firstOrNull()?.at == replacedAt) it[Keys.history] = encodeHistory(history.drop(1))
            }
        }
        // A photo taken for the undone spot belongs to nothing now.
        if (replacedPhoto != spot.photoPath) deletePhotoFile(replacedPhoto)
    }

    /** Deletes photos left behind by an interrupted camera session or crash. */
    fun deleteOrphanPhotos(keep: Set<String>) {
        runCatching {
            photoDir(context).listFiles()?.forEach { f -> if (f.absolutePath !in keep) f.delete() }
        }
    }

    /** Deletes a photo file that is no longer referenced by the current spot. */
    fun deletePhotoFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.parentFile == photoDir(context) }?.delete() }
    }

    suspend fun setNote(note: String?) = context.dataStore.edit {
        val clean = note?.trim()?.take(140)
        if (clean.isNullOrEmpty()) it.remove(Keys.note) else it[Keys.note] = clean
        // Keep the newest history row in sync so the note is remembered there too.
        val history = parseHistory(it[Keys.history])
        val at = it[Keys.parkedAt]
        if (at != null && history.firstOrNull()?.at == at) {
            it[Keys.history] = encodeHistory(listOf(history.first().copy(note = clean?.ifEmpty { null })) + history.drop(1))
        }
    }

    suspend fun setPhotoPath(path: String?) {
        var old: String? = null
        context.dataStore.edit {
            old = it[Keys.photoPath]
            if (path == null) it.remove(Keys.photoPath) else it[Keys.photoPath] = path
        }
        if (old != path) deletePhotoFile(old)
    }

    suspend fun setTimerEndsAt(endsAt: Long?) = context.dataStore.edit {
        if (endsAt == null) it.remove(Keys.timerEndsAt) else it[Keys.timerEndsAt] = endsAt
    }

    suspend fun setHistoryEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.historyEnabled] = enabled
        if (!enabled) it.remove(Keys.history)
    }

    suspend fun clearHistory() = context.dataStore.edit { it.remove(Keys.history) }

    suspend fun deleteHistoryEntry(at: Long) = context.dataStore.edit {
        it[Keys.history] = encodeHistory(parseHistory(it[Keys.history]).filterNot { e -> e.at == at })
    }

    private fun parseHistory(raw: String?): List<ParkingHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    ParkingHistoryEntry(
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng"),
                        at = o.getLong("at"),
                        note = o.optString("note").ifBlank { null },
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeHistory(list: List<ParkingHistoryEntry>): String = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("lat", e.lat); put("lng", e.lng); put("at", e.at)
                if (e.note != null) put("note", e.note)
            })
        }
    }.toString()

    companion object {
        const val MAX_HISTORY = 50

        fun photoDir(context: Context): File =
            File(context.applicationContext.filesDir, "spot_photos").apply { mkdirs() }
    }
}
