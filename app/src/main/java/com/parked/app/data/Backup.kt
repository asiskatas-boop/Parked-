package com.parked.app.data

import android.content.Context
import android.net.Uri
import com.parked.app.BuildConfig
import com.parked.app.receiver.ParkingTimer
import com.parked.app.service.ParkingMonitorService
import com.parked.app.widget.ParkedWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * A single JSON file with everything the user entered: car, spot, note,
 * timer, opt-in history, fuel, refuels, odometer and service reminders.
 * The user picks where it goes (Downloads, Drive, email...), so nothing is
 * uploaded by the app itself.
 *
 * Import is two steps: [parse] checks the whole file and turns it into plain
 * typed values, and only then [restore] touches anything. A broken or
 * hand-edited file is refused before any data changes.
 */
object Backup {
    private const val APP = "parked"
    const val FORMAT = 1
    private const val MAX_BYTES = 5_000_000

    data class Parsed(
        val parking: ParkingBackup,
        val fuel: Map<String, Any>,
        /** 0 when the file doesn't say. */
        val exportedAt: Long,
    )

    /** What was on the phone before an import, so Undo puts all of it back. */
    data class Snapshot(val data: Parsed, val photoPath: String?, val monitoring: Boolean)

    sealed interface ReadResult {
        data class Ok(val backup: Parsed) : ReadResult
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
        val text = runCatching { build(parking, fuel).toString(2) }.getOrNull() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                // "w" rather than "wt": some document providers reject "wt", and
                // the picker has just created this file, so there is nothing to truncate.
                context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(text.toByteArray()) }
            }.isSuccess
        }
    }

    suspend fun read(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                // Stop reading past the limit instead of loading a huge file first.
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return@use null
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull() ?: return@withContext ReadResult.NotABackup
        parse(text)
    }

    fun parse(text: String): ReadResult {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return ReadResult.NotABackup
        if (o.optString("app") != APP) return ReadResult.NotABackup
        if (o.optInt("format", 0) > FORMAT) return ReadResult.TooNew
        return runCatching {
            val parking = parseParking(o.getJSONObject("parking"))
            val fuel = parseFuel(o.getJSONObject("fuel"))
            ReadResult.Ok(Parsed(parking, fuel, o.optLong("exportedAt", 0L).coerceAtLeast(0L)))
        }.getOrElse { ReadResult.NotABackup }
    }

    /** A JSON string value, treating a missing key or JSON null as absent. */
    private fun JSONObject.text(key: String, max: Int): String? =
        if (!has(key) || isNull(key)) null else getString(key).trim().take(max).ifEmpty { null }

    private fun JSONObject.finite(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key).also { require(it.isFinite()) }

    private fun JSONObject.long(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun parseParking(p: JSONObject): ParkingBackup {
        val lat = p.finite("parked_lat")
        val lng = p.finite("parked_lng")
        val hasSpot = lat != null && lng != null
        if (hasSpot) require(lat!! in -90.0..90.0 && lng!! in -180.0..180.0)
        val historyOn = p.optBoolean("history_enabled", false)
        val history = if (!historyOn) emptyList() else {
            val arr = p.optJSONArray("history") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val e = arr.getJSONObject(i)
                ParkingHistoryEntry(
                    lat = e.getDouble("lat").also { require(it.isFinite()) },
                    lng = e.getDouble("lng").also { require(it.isFinite()) },
                    at = e.getLong("at"),
                    note = e.text("note", 140),
                )
            }.take(ParkingStore.MAX_HISTORY)
        }
        return ParkingBackup(
            deviceName = p.text("device_name", 100),
            deviceAddress = p.text("device_address", 40),
            lat = if (hasSpot) lat else null,
            lng = if (hasSpot) lng else null,
            at = if (hasSpot) p.long("parked_at") else null,
            note = p.text("spot_note", 140),
            timerEndsAt = p.long("timer_ends_at"),
            historyEnabled = historyOn,
            history = history,
            source = if (hasSpot) p.text("spot_source", 10)?.takeIf { it == ParkingStore.SOURCE_AUTO || it == ParkingStore.SOURCE_MANUAL } else null,
            accuracy = if (hasSpot) p.finite("spot_accuracy")?.takeIf { it in 0.0..10_000.0 }?.toFloat() else null,
        )
    }

    /** Only known keys, each with the type the app stores it as. */
    private fun parseFuel(f: JSONObject): Map<String, Any> = buildMap {
        FuelStore.BACKUP_KEYS.forEach { (key, type) ->
            val e = f.optJSONObject(key) ?: return@forEach
            require(e.optString("t") == type.toString())
            val v: Any = when (type) {
                'f' -> e.getDouble("v").also { require(it.isFinite()) }.toFloat()
                'l' -> e.getLong("v")
                'b' -> e.getBoolean("v")
                else -> e.getString("v").also { if (key in FuelStore.BACKUP_JSON_ARRAYS) JSONArray(it) }
            }
            put(key, v)
        }
    }

    /** What the phone holds now, in the same checked form as a parsed file. */
    suspend fun snapshot(parking: ParkingStore, fuel: FuelStore): Snapshot? {
        val current = parking.current()
        val parsed = runCatching { parse(build(parking, fuel).toString()) }.getOrNull() as? ReadResult.Ok ?: return null
        return Snapshot(parsed.backup, current.photoPath, current.monitoring)
    }

    /**
     * Replaces all data with [data]. AutoPark is stopped (and started again only
     * when [monitoring] is true, for Undo). Runs to the end even if the screen
     * that started it goes away.
     */
    suspend fun restore(
        context: Context,
        data: Parsed,
        parking: ParkingStore,
        fuel: FuelStore,
        photoPath: String? = null,
        monitoring: Boolean = false,
    ) = withContext(NonCancellable) {
        ParkingMonitorService.stop(context)
        ParkingTimer.cancel(context)
        parking.importBackup(data.parking, photoPath, monitoring)
        withContext(Dispatchers.IO) { fuel.importValues(data.fuel) }
        data.parking.timerEndsAt?.let { if (it > System.currentTimeMillis()) ParkingTimer.schedule(context, it) }
        if (monitoring && !ParkingMonitorService.start(context)) parking.setMonitoring(false)
        ParkedWidget.refresh(context)
    }
}
