package com.parked.app.data

import android.content.Context
import android.net.Uri
import com.parked.app.BuildConfig
import com.parked.app.receiver.ParkingTimer
import com.parked.app.service.ParkingMonitorService
import com.parked.app.widget.ParkedWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A single JSON file with everything the user entered: car, spot, note,
 * timer, opt-in history, fuel, refuels, odometer and service reminders.
 * The user picks where it goes (Downloads, Drive, email...), so nothing is
 * uploaded by the app itself.
 */
object Backup {
    private const val APP = "parked"
    const val FORMAT = 1

    sealed interface ReadResult {
        data class Ok(val data: JSONObject, val exportedAt: Long) : ReadResult
        data object NotABackup : ReadResult
        data object TooNew : ReadResult
    }

    suspend fun build(parking: ParkingStore, fuel: FuelStore): JSONObject = JSONObject().apply {
        put("app", APP)
        put("format", FORMAT)
        put("appVersion", BuildConfig.VERSION_NAME)
        put("exportedAt", System.currentTimeMillis())
        put("parking", parking.exportJson())
        put("fuel", fuel.exportJson())
    }

    suspend fun write(context: Context, uri: Uri, parking: ParkingStore, fuel: FuelStore): Boolean {
        val text = build(parking, fuel).toString(2)
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }.isSuccess
        }
    }

    suspend fun read(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                // A real backup is a few hundred KB at most; refuse anything huge.
                val bytes = input.readBytes()
                if (bytes.size > 5_000_000) null else String(bytes)
            }
        }.getOrNull() ?: return@withContext ReadResult.NotABackup
        parse(text)
    }

    fun parse(text: String): ReadResult {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return ReadResult.NotABackup
        if (o.optString("app") != APP || !o.has("parking") || !o.has("fuel")) return ReadResult.NotABackup
        if (o.optInt("format", 0) > FORMAT) return ReadResult.TooNew
        return ReadResult.Ok(o, o.optLong("exportedAt", 0L))
    }

    /** Replaces all data with [data]. AutoPark is stopped; the timer is rescheduled. */
    suspend fun restore(context: Context, data: JSONObject, parking: ParkingStore, fuel: FuelStore) {
        ParkingMonitorService.stop(context)
        ParkingTimer.cancel(context)
        parking.importJson(data.getJSONObject("parking"))
        withContext(Dispatchers.IO) { fuel.importJson(data.getJSONObject("fuel")) }
        parking.current().timerEndsAt?.let { if (it > System.currentTimeMillis()) ParkingTimer.schedule(context, it) }
        ParkedWidget.refresh(context)
    }
}
