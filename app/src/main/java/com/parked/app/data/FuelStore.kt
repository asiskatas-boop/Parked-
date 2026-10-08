package com.parked.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class FuelLevelLog(
    val date: Long,
    val levelPct: Double,
    val odometer: Double,
)

data class Refuel(
    val date: Long,
    val litres: Double,
    val cost: Double,
    val odometer: Double,
    val tankFull: Boolean,
)

data class MonthlySpend(
    val year: Int,
    /** 0-based, like [Calendar.MONTH]. */
    val month: Int,
    val cost: Double,
    val litres: Double,
)

data class ServiceReminder(
    val id: Long,
    val name: String,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val lastDoneOdo: Double,
    val lastDoneAt: Long,
    /** [lastDoneAt] value for which the "due" alert was already shown. */
    val alertedFor: Long = 0L,
)

enum class ReminderStatus { Ok, Soon, Due }

data class ReminderProgress(
    val status: ReminderStatus,
    val kmLeft: Double?,
    val dueAt: Long?,
)

class FuelStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("parked_fuel", Context.MODE_PRIVATE)

    // The Activity and foreground service each hold a FuelStore instance. Keep
    // them in sync when either side updates SharedPreferences.
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "tankCapacity" -> tankCapacity = prefs.getFloat("tankCapacity", 0f).toDouble()
            "currency" -> currency = prefs.getString("currency", "€") ?: "€"
            "baselineOdo" -> baselineOdo = prefs.getFloat("baselineOdo", 0f).toDouble()
            "gpsKm" -> gpsKm = prefs.getFloat("gpsKm", 0f).toDouble()
            "levelPct" -> levelPct = prefs.getFloat("levelPct", 100f).toDouble()
            "odoForLevel" -> odoForLevel = prefs.getFloat("odoForLevel", 0f).toDouble()
            "levelUpdatedAt" -> levelUpdatedAt = prefs.getLong("levelUpdatedAt", 0L)
            "fuelLevelLogs" -> fuelLogs = loadFuelLogs()
            "refuels" -> refuels = loadRefuels()
            "accentName" -> accentName = canonicalAccentName(prefs.getString("accentName", "Blue") ?: "Blue")
            "notificationsEnabled" -> notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
            "serviceReminders" -> reminders = loadReminders()
        }
    }

    var tankCapacity: Double by mutableStateOf(prefs.getFloat("tankCapacity", 0f).toDouble())
        private set
    var currency: String by mutableStateOf(prefs.getString("currency", "€") ?: "€")
        private set

    var baselineOdo: Double by mutableStateOf(prefs.getFloat("baselineOdo", 0f).toDouble())
        private set
    var gpsKm: Double by mutableStateOf(prefs.getFloat("gpsKm", 0f).toDouble())
        private set

    var levelPct: Double by mutableStateOf(prefs.getFloat("levelPct", 100f).toDouble())
        private set
    var odoForLevel: Double by mutableStateOf(prefs.getFloat("odoForLevel", 0f).toDouble())
        private set
    var levelUpdatedAt: Long by mutableStateOf(prefs.getLong("levelUpdatedAt", 0L))
        private set

    /** Level checks saved by builds before 0.2.6. Shown read-only for upgraders. */
    var fuelLogs: List<FuelLevelLog> by mutableStateOf(loadFuelLogs())
        private set

    var refuels: List<Refuel> by mutableStateOf(loadRefuels())
        private set

    var accentName: String by mutableStateOf(canonicalAccentName(prefs.getString("accentName", "Blue") ?: "Blue"))
        private set

    var notificationsEnabled: Boolean by mutableStateOf(prefs.getBoolean("notificationsEnabled", true))
        private set

    var reminders: List<ServiceReminder> by mutableStateOf(loadReminders())
        private set

    init {
        migrateLegacyFuelMeterEntriesIfNeeded()
        backfillLevelUpdatedAtIfNeeded()
        normalizeSavedAccentIfNeeded()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        private val legacyMigrationLock = Any()
        private val reminderLock = Any()
        private val supportedAccentNames = setOf(
            "White", "Silver", "Gray", "Black", "Blue", "Red",
            "Green", "Yellow", "Orange", "Brown", "Purple"
        )
        private const val SOON_KM = 500.0
        private const val SOON_MS = 14L * 24 * 60 * 60 * 1000
    }

    private fun canonicalAccentName(raw: String): String = when (raw.trim()) {
        "Ocean Blue" -> "Blue"
        "Forest Green", "Racing Green" -> "Green"
        "Midnight" -> "Black"
        "Sunset" -> "Orange"
        "Match my car", "" -> "Blue"
        else -> raw.trim().takeIf { it in supportedAccentNames } ?: "Blue"
    }

    private fun backfillLevelUpdatedAtIfNeeded() {
        if (levelUpdatedAt > 0L) return
        val refuelThatCouldSetLevel = refuels.firstOrNull { it.tankFull || tankCapacity > 0 }?.date
        val inferred = listOfNotNull(
            fuelLogs.firstOrNull()?.date,
            refuelThatCouldSetLevel,
        ).maxOrNull() ?: return
        levelUpdatedAt = inferred
        prefs.edit().putLong("levelUpdatedAt", inferred).apply()
    }

    private fun normalizeSavedAccentIfNeeded() {
        val raw = prefs.getString("accentName", "Blue") ?: "Blue"
        val canonical = canonicalAccentName(raw)
        accentName = canonical
        if (raw != canonical || prefs.contains("customAccentStart") || prefs.contains("customAccentEnd")) {
            prefs.edit()
                .putString("accentName", canonical)
                .remove("customAccentStart")
                .remove("customAccentEnd")
                .apply()
        }
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    // gpsKm is only meaningful once the user has anchored it to the car's real
    // odometer. Without a baseline, raw GPS distance is not shown as an odometer.
    val currentOdo: Double get() = if (baselineOdo > 0) baselineOdo + gpsKm else 0.0

    fun addKm(km: Double) {
        if (baselineOdo <= 0) return
        if (!km.isFinite() || km <= 0 || km > 5) return
        gpsKm += km
        prefs.edit().putFloat("gpsKm", gpsKm.toFloat()).apply()
    }

    fun setOdometer(value: Double) {
        if (!value.isFinite() || value <= 0) return
        baselineOdo = value
        gpsKm = 0.0

        // An odometer edit is a calibration, not distance driven. Move the fuel
        // level's anchor with it so a correction cannot appear to consume fuel.
        if (levelUpdatedAt > 0L) {
            odoForLevel = value
        }

        val editor = prefs.edit()
            .putFloat("baselineOdo", value.toFloat())
            .putFloat("gpsKm", 0f)
        if (levelUpdatedAt > 0L) {
            editor.putFloat("odoForLevel", value.toFloat())
        }
        editor.apply()
    }

    fun updateTankCapacity(v: Double) {
        if (!v.isFinite() || v <= 0) return
        tankCapacity = v
        prefs.edit().putFloat("tankCapacity", v.toFloat()).apply()
    }

    fun setAccent(name: String) {
        val canonical = canonicalAccentName(name)
        accentName = canonical
        prefs.edit().putString("accentName", canonical).apply()
    }

    fun updateNotificationsEnabled(v: Boolean) {
        notificationsEnabled = v
        prefs.edit().putBoolean("notificationsEnabled", v).apply()
    }

    /** Update the current fuel level without creating a history row. */
    fun setLevel(pct: Double) {
        if (!pct.isFinite()) return
        levelPct = pct.coerceIn(0.0, 100.0)
        odoForLevel = currentOdo
        levelUpdatedAt = System.currentTimeMillis()
        prefs.edit()
            .putFloat("levelPct", levelPct.toFloat())
            .putFloat("odoForLevel", odoForLevel.toFloat())
            .putLong("levelUpdatedAt", levelUpdatedAt)
            .apply()
    }

    fun deleteFuelLog(date: Long) {
        val updated = fuelLogs.filterNot { it.date == date }
        if (updated.size == fuelLogs.size) return
        fuelLogs = updated
        saveFuelLogs(updated)
    }

    fun restoreFuelLog(log: FuelLevelLog) {
        if (fuelLogs.any { it.date == log.date }) return
        val updated = (fuelLogs + log).sortedByDescending { it.date }.take(300)
        fuelLogs = updated
        saveFuelLogs(updated)
    }

    /** Record an actual refuel. The stored format is unchanged since 0.2.0. */
    fun logRefuel(litres: Double, cost: Double, odometer: Double, tankFull: Boolean): Boolean {
        if (!litres.isFinite() || !cost.isFinite() || !odometer.isFinite()) return false
        if (litres <= 0 || cost < 0 || odometer < 0) return false
        // Refuels are timestamped "now", so an odometer below the current reading
        // would make history impossible and corrupt consumption. Allow 1 km for
        // rounding because the UI shows whole kilometres.
        if (currentOdo > 0 && odometer > 0 && odometer + 1.0 < currentOdo) return false
        val effectiveOdo = when {
            odometer > currentOdo && odometer > 0 -> odometer
            currentOdo > 0 -> currentOdo
            odometer > 0 -> odometer
            else -> 0.0
        }
        val levelBeforeRefuel = estimateLevelPctAtOdometer(effectiveOdo)
        // Only a reading the user typed is stored with the refuel. A GPS estimate
        // must never become a consumption anchor.
        val entry = Refuel(System.currentTimeMillis(), litres, cost, odometer.coerceAtLeast(0.0), tankFull)
        val newList = (listOf(entry) + refuels).take(200)
        refuels = newList
        saveRefuels(newList)

        // Only move the stored odometer forward; a typo must not roll it back.
        if (odometer > currentOdo && odometer > 0) {
            setOdometer(odometer)
        }

        if (tankFull) {
            setLevel(100.0)
        } else if (tankCapacity > 0) {
            setLevel(levelBeforeRefuel + litres / tankCapacity * 100.0)
        }
        return true
    }

    fun deleteRefuel(date: Long) {
        val updated = refuels.filterNot { it.date == date }
        if (updated.size == refuels.size) return
        refuels = updated
        saveRefuels(updated)
    }

    fun averageL100km(): Double? {
        // Full-to-full consumption needs real odometer readings on both anchors.
        val history = refuels.sortedByDescending { it.date }
        val fullIndices = history.indices
            .filter { history[it].tankFull && history[it].odometer.isFinite() && history[it].odometer > 0 }
            .take(6)
        if (fullIndices.size < 2) return null

        // Prefer the widest valid recent span, skipping malformed pairs.
        var bestDistance = 0.0
        var bestLitres = 0.0
        for (newerPos in 0 until fullIndices.lastIndex) {
            val newerIndex = fullIndices[newerPos]
            for (olderPos in newerPos + 1 until fullIndices.size) {
                val olderIndex = fullIndices[olderPos]
                val distanceKm = history[newerIndex].odometer - history[olderIndex].odometer
                if (!distanceKm.isFinite() || distanceKm <= 50.0 || distanceKm <= bestDistance) continue

                val litresUsed = history
                    .subList(newerIndex, olderIndex)
                    .sumOf { it.litres }
                if (!litresUsed.isFinite() || litresUsed <= 0.0) continue

                bestDistance = distanceKm
                bestLitres = litresUsed
            }
        }
        if (bestDistance <= 0.0 || bestLitres <= 0.0) return null
        return bestLitres / bestDistance * 100.0
    }

    fun estimatedRangeKm(): Double? {
        if (!tankCapacity.isFinite() || tankCapacity <= 0) return null
        val avg = averageL100km() ?: return null
        if (!avg.isFinite() || avg <= 0) return null
        return tankCapacity / avg * 100.0
    }

    private fun estimateLevelPctAtOdometer(odometer: Double): Double {
        val range = estimatedRangeKm() ?: return levelPct
        if (!range.isFinite() || range <= 0) return levelPct
        val driven = odometer - odoForLevel
        if (!driven.isFinite() || driven <= 0) return levelPct
        return (levelPct - driven / range * 100.0).coerceIn(0.0, 100.0)
    }

    /** Saved level minus the fuel used for the distance driven since it was set. */
    fun estimateLevelPct(): Double = estimateLevelPctAtOdometer(currentOdo)

    /** Kilometres driven since the level was last set, when that is known. */
    fun kmSinceLevelSet(): Double? {
        if (levelUpdatedAt <= 0L || odoForLevel <= 0 || currentOdo <= 0) return null
        return (currentOdo - odoForLevel).takeIf { it.isFinite() && it >= 1.0 }
    }

    fun latestPricePerLitre(): Double? =
        refuels.firstOrNull { it.litres > 0 && it.cost > 0 }?.let { it.cost / it.litres }

    /** Fuel spending per calendar month, newest first, for the last [months] months. */
    fun monthlySpend(months: Int = 6, now: Long = System.currentTimeMillis()): List<MonthlySpend> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val result = mutableListOf<MonthlySpend>()
        repeat(months) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val inMonth = refuels.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
            }
            result += MonthlySpend(y, m, inMonth.sumOf { it.cost }, inMonth.sumOf { it.litres })
            cal.add(Calendar.MONTH, -1)
        }
        return result
    }

    /** Refuel history as CSV, oldest first, for spreadsheets. */
    fun refuelsCsv(): String = buildString {
        append("date,litres,total_paid,currency,price_per_litre,odometer_km,full_tank\n")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        refuels.sortedBy { it.date }.forEach { r ->
            val ppl = if (r.litres > 0) r.cost / r.litres else 0.0
            append(fmt.format(java.util.Date(r.date))).append(',')
            append(String.format(Locale.US, "%.2f", r.litres)).append(',')
            append(String.format(Locale.US, "%.2f", r.cost)).append(',')
            append('"').append(currency.replace("\"", "\"\"")).append('"').append(',')
            append(String.format(Locale.US, "%.3f", ppl)).append(',')
            append(if (r.odometer > 0) String.format(Locale.US, "%.0f", r.odometer) else "").append(',')
            append(if (r.tankFull) "yes" else "no").append('\n')
        }
    }

    // ---------------------------------------------------------------------
    // Service reminders
    // ---------------------------------------------------------------------

    /**
     * The app and the AutoPark service each hold a FuelStore. Every reminder change
     * re-reads the stored list under a lock, so neither side overwrites the other.
     */
    private fun mutateReminders(change: (List<ServiceReminder>) -> List<ServiceReminder>) {
        synchronized(reminderLock) {
            saveReminders(change(loadReminders()))
        }
    }

    fun addReminder(name: String, intervalKm: Int?, intervalMonths: Int?, lastDoneOdo: Double, lastDoneAt: Long) {
        val reminder = ServiceReminder(
            id = System.currentTimeMillis(),
            name = name.trim().take(40),
            intervalKm = intervalKm?.takeIf { it > 0 },
            intervalMonths = intervalMonths?.takeIf { it > 0 },
            lastDoneOdo = lastDoneOdo.coerceAtLeast(0.0),
            lastDoneAt = lastDoneAt,
        )
        mutateReminders { it + reminder }
    }

    fun updateReminder(updated: ServiceReminder) {
        mutateReminders { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    /** "Done today" resets the interval from now and the current odometer. */
    fun markReminderDone(id: Long) {
        val now = System.currentTimeMillis()
        val odo = currentOdo
        mutateReminders { list ->
            list.map { if (it.id == id) it.copy(lastDoneOdo = odo, lastDoneAt = now, alertedFor = 0L) else it }
        }
    }

    fun deleteReminder(id: Long) = mutateReminders { list -> list.filterNot { it.id == id } }

    fun restoreReminder(reminder: ServiceReminder) = mutateReminders { list ->
        if (list.any { it.id == reminder.id }) list else (list + reminder).sortedBy { it.id }
    }

    fun progress(r: ServiceReminder, now: Long = System.currentTimeMillis()): ReminderProgress {
        val kmLeft = r.intervalKm?.let { interval ->
            if (currentOdo > 0 && r.lastDoneOdo > 0) r.lastDoneOdo + interval - currentOdo else null
        }
        val dueAt = r.intervalMonths?.let { months ->
            Calendar.getInstance().apply { timeInMillis = r.lastDoneAt; add(Calendar.MONTH, months) }.timeInMillis
        }
        val due = (kmLeft != null && kmLeft <= 0) || (dueAt != null && dueAt <= now)
        val soon = (kmLeft != null && kmLeft <= SOON_KM) || (dueAt != null && dueAt - now <= SOON_MS)
        val status = when {
            due -> ReminderStatus.Due
            soon -> ReminderStatus.Soon
            else -> ReminderStatus.Ok
        }
        return ReminderProgress(status, kmLeft, dueAt)
    }

    /**
     * Reminders that just became due and have not been announced yet. Marks them
     * as announced, so each due date produces at most one alert.
     */
    fun takeNewlyDueReminders(): List<ServiceReminder> {
        var newlyDue: List<ServiceReminder> = emptyList()
        mutateReminders { list ->
            newlyDue = list.filter { progress(it).status == ReminderStatus.Due && it.alertedFor != it.lastDoneAt }
            val ids = newlyDue.map { it.id }.toSet()
            list.map { if (it.id in ids) it.copy(alertedFor = it.lastDoneAt) else it }
        }
        return newlyDue
    }

    private fun saveReminders(list: List<ServiceReminder>) {
        reminders = list
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                if (r.intervalKm != null) put("km", r.intervalKm)
                if (r.intervalMonths != null) put("months", r.intervalMonths)
                put("lastOdo", r.lastDoneOdo)
                put("lastAt", r.lastDoneAt)
                put("alertedFor", r.alertedFor)
            })
        }
        prefs.edit().putString("serviceReminders", arr.toString()).apply()
    }

    private fun loadReminders(): List<ServiceReminder> {
        val raw = prefs.getString("serviceReminders", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    ServiceReminder(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        intervalKm = if (o.has("km")) o.getInt("km") else null,
                        intervalMonths = if (o.has("months")) o.getInt("months") else null,
                        lastDoneOdo = o.optDouble("lastOdo", 0.0).takeIf { it.isFinite() } ?: 0.0,
                        lastDoneAt = o.getLong("lastAt"),
                        alertedFor = o.optLong("alertedFor", 0L),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------------
    // Legacy migration and persistence
    // ---------------------------------------------------------------------

    /**
     * Version 1 stored every fuel-meter save in `refuels`. On the first 0.2.x
     * launch those rows are moved once into level checks so they do not count
     * as real refuels. If `fuelLevelLogs` already exists the user has run 0.2.x
     * and nothing is moved.
     */
    private fun migrateLegacyFuelMeterEntriesIfNeeded() {
        synchronized(legacyMigrationLock) {
            val key = "legacyFuelMeterMigratedV2"
            if (prefs.getBoolean(key, false)) return

            if (!prefs.contains("fuelLevelLogs") && refuels.isNotEmpty()) {
                val capacity = tankCapacity.takeIf { it.isFinite() && it > 0 } ?: 50.0
                val migrated = refuels.mapNotNull { legacy ->
                    if (!legacy.litres.isFinite() || legacy.litres < 0) return@mapNotNull null
                    FuelLevelLog(
                        date = legacy.date,
                        levelPct = (legacy.litres / capacity * 100.0).coerceIn(0.0, 100.0),
                        odometer = legacy.odometer.takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                    )
                }.take(300)

                val migratedJson = JSONArray().apply {
                    migrated.forEach { log ->
                        put(JSONObject().apply {
                            put("date", log.date)
                            put("levelPct", log.levelPct)
                            put("odo", log.odometer)
                        })
                    }
                }.toString()
                val legacyRaw = prefs.getString("refuels", "[]") ?: "[]"
                val latest = migrated.firstOrNull()

                // One synchronous transaction so the Activity and service cannot race
                // it; the exact v1 JSON is backed up before the bucket is cleared.
                val editor = prefs.edit()
                    .putString("legacyRefuelsBackupV1", legacyRaw)
                    .putString("fuelLevelLogs", migratedJson)
                    .putString("refuels", "[]")
                    .putBoolean(key, true)
                if (latest != null) {
                    editor
                        .putFloat("levelPct", latest.levelPct.toFloat())
                        .putFloat("odoForLevel", (latest.odometer.takeIf { it > 0 } ?: currentOdo).toFloat())
                        .putLong("levelUpdatedAt", latest.date)
                }
                editor.commit()

                fuelLogs = migrated
                refuels = emptyList()
                latest?.let {
                    levelPct = it.levelPct
                    odoForLevel = it.odometer.takeIf { value -> value > 0 } ?: currentOdo
                    levelUpdatedAt = it.date
                }
            } else {
                prefs.edit().putBoolean(key, true).commit()
            }
        }
    }

    private fun loadFuelLogs(): List<FuelLevelLog> {
        val raw = prefs.getString("fuelLevelLogs", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val level = o.getDouble("levelPct")
                    val odo = o.optDouble("odo", 0.0)
                    if (!level.isFinite() || !odo.isFinite()) return@runCatching null
                    FuelLevelLog(
                        date = o.getLong("date"),
                        levelPct = level.coerceIn(0.0, 100.0),
                        odometer = odo.coerceAtLeast(0.0),
                    )
                }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFuelLogs(list: List<FuelLevelLog>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("date", it.date)
                put("levelPct", it.levelPct)
                put("odo", it.odometer)
            })
        }
        prefs.edit().putString("fuelLevelLogs", arr.toString()).apply()
    }

    private fun loadRefuels(): List<Refuel> {
        val raw = prefs.getString("refuels", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val litres = o.getDouble("litres")
                    val cost = o.getDouble("cost")
                    val odo = o.optDouble("odo", 0.0)
                    if (!litres.isFinite() || !cost.isFinite() || !odo.isFinite() || litres <= 0 || cost < 0 || odo < 0) {
                        return@runCatching null
                    }
                    Refuel(
                        date = o.getLong("date"),
                        litres = litres,
                        cost = cost,
                        odometer = odo,
                        tankFull = o.optBoolean("tankFull", false),
                    )
                }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRefuels(list: List<Refuel>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("date", it.date)
            o.put("litres", it.litres)
            o.put("cost", it.cost)
            o.put("odo", it.odometer)
            o.put("tankFull", it.tankFull)
            arr.put(o)
        }
        prefs.edit().putString("refuels", arr.toString()).apply()
    }
}
