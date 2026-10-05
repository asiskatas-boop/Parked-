package com.parked.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

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

class FuelStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("parked_fuel", Context.MODE_PRIVATE)

    // The Activity and foreground service each hold a FuelStore instance. Keep
    // them in sync when either side updates SharedPreferences so odometer/fuel
    // state and notification preferences do not stay stale until a restart.
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "tankCapacity" -> tankCapacity = prefs.getFloat("tankCapacity", 0f).toDouble()
            "currency" -> currency = prefs.getString("currency", "€") ?: "€"
            "units" -> units = prefs.getString("units", "L/100km") ?: "L/100km"
            "baselineOdo" -> baselineOdo = prefs.getFloat("baselineOdo", 0f).toDouble()
            "gpsKm" -> gpsKm = prefs.getFloat("gpsKm", 0f).toDouble()
            "levelPct" -> levelPct = prefs.getFloat("levelPct", 100f).toDouble()
            "odoForLevel" -> odoForLevel = prefs.getFloat("odoForLevel", 0f).toDouble()
            "promptDismissedAtOdo" -> promptDismissedAtOdo = prefs.getFloat("promptDismissedAtOdo", 0f).toDouble()
            "fuelLevelLogs" -> fuelLogs = loadFuelLogs()
            "refuels" -> refuels = loadRefuels()
            "accentName" -> accentName = canonicalAccentName(prefs.getString("accentName", "Blue") ?: "Blue")
            "notificationsEnabled" -> notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
            "customFuelPrice" -> customFuelPrice = prefs.getFloat("customFuelPrice", 0f).toDouble()
            "customAccentStart" -> customAccentStart = prefs.getInt("customAccentStart", 0)
            "customAccentEnd" -> customAccentEnd = prefs.getInt("customAccentEnd", 0)
        }
    }

    var tankCapacity: Double by mutableStateOf(prefs.getFloat("tankCapacity", 0f).toDouble())
        private set
    var currency: String by mutableStateOf(prefs.getString("currency", "€") ?: "€")
        private set
    var units: String by mutableStateOf(prefs.getString("units", "L/100km") ?: "L/100km")
        private set

    var baselineOdo: Double by mutableStateOf(prefs.getFloat("baselineOdo", 0f).toDouble())
        private set
    var gpsKm: Double by mutableStateOf(prefs.getFloat("gpsKm", 0f).toDouble())
        private set

    var levelPct: Double by mutableStateOf(prefs.getFloat("levelPct", 100f).toDouble())
        private set
    var odoForLevel: Double by mutableStateOf(prefs.getFloat("odoForLevel", 0f).toDouble())
        private set
    var promptDismissedAtOdo: Double by mutableStateOf(prefs.getFloat("promptDismissedAtOdo", 0f).toDouble())
        private set

    // New routine fuel-level history. This intentionally uses a new preference
    // key so every existing refuel entry from previous Parked! versions stays
    // untouched during an in-place update.
    var fuelLogs: List<FuelLevelLog> by mutableStateOf(loadFuelLogs())
        private set

    var refuels: List<Refuel> by mutableStateOf(loadRefuels())
        private set

    var accentName: String by mutableStateOf(canonicalAccentName(prefs.getString("accentName", "Blue") ?: "Blue"))
        private set

    var notificationsEnabled: Boolean by mutableStateOf(prefs.getBoolean("notificationsEnabled", true))
        private set

    var customFuelPrice: Double by mutableStateOf(prefs.getFloat("customFuelPrice", 0f).toDouble())
        private set

    var customAccentStart: Int by mutableStateOf(prefs.getInt("customAccentStart", 0))
        private set
    var customAccentEnd: Int by mutableStateOf(prefs.getInt("customAccentEnd", 0))
        private set

    init {
        migrateLegacyFuelMeterEntriesIfNeeded()
        normalizeSavedAccentIfNeeded()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    companion object {
        private val legacyMigrationLock = Any()
        private val supportedAccentNames = setOf(
            "White", "Silver", "Gray", "Black", "Blue", "Red",
            "Green", "Yellow", "Orange", "Brown", "Purple"
        )
    }

    private fun canonicalAccentName(raw: String): String = when (raw.trim()) {
        "Ocean Blue" -> "Blue"
        "Forest Green", "Racing Green" -> "Green"
        "Midnight" -> "Black"
        "Sunset" -> "Orange"
        // Older builds could persist the label itself instead of a real colour,
        // which left the selector with no selected item and a blue fallback car.
        "Match my car", "" -> "Blue"
        else -> raw.trim().takeIf { it in supportedAccentNames } ?: "Blue"
    }

    private fun normalizeSavedAccentIfNeeded() {
        val raw = prefs.getString("accentName", "Blue") ?: "Blue"
        val canonical = canonicalAccentName(raw)
        accentName = canonical
        if (raw != canonical || customAccentStart != 0 || customAccentEnd != 0) {
            customAccentStart = 0
            customAccentEnd = 0
            prefs.edit()
                .putString("accentName", canonical)
                .putInt("customAccentStart", 0)
                .putInt("customAccentEnd", 0)
                .apply()
        }
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    val currentOdo: Double get() = baselineOdo + gpsKm

    val isConfigured: Boolean get() = tankCapacity > 0 && baselineOdo > 0

    fun addKm(km: Double) {
        if (!km.isFinite() || km <= 0 || km > 5) return
        gpsKm += km
        prefs.edit().putFloat("gpsKm", gpsKm.toFloat()).apply()
    }

    fun setOdometer(value: Double) {
        if (!value.isFinite() || value <= 0) return
        baselineOdo = value
        gpsKm = 0.0
        prefs.edit()
            .putFloat("baselineOdo", value.toFloat())
            .putFloat("gpsKm", 0f)
            .apply()
    }

    fun resetOdometerTracker() {
        val newBaseline = currentOdo
        baselineOdo = newBaseline
        gpsKm = 0.0
        prefs.edit()
            .putFloat("baselineOdo", newBaseline.toFloat())
            .putFloat("gpsKm", 0f)
            .apply()
    }

    fun updateTankCapacity(v: Double) {
        if (!v.isFinite() || v <= 0) return
        tankCapacity = v
        prefs.edit().putFloat("tankCapacity", v.toFloat()).apply()
    }

    fun updateCurrency(v: String) {
        currency = v
        prefs.edit().putString("currency", v).apply()
    }

    fun updateUnits(v: String) {
        units = v
        prefs.edit().putString("units", v).apply()
    }

    fun setAccent(name: String) {
        val canonical = canonicalAccentName(name)
        accentName = canonical
        customAccentStart = 0
        customAccentEnd = 0
        prefs.edit()
            .putString("accentName", canonical)
            .putInt("customAccentStart", 0)
            .putInt("customAccentEnd", 0)
            .apply()
    }

    fun updateNotificationsEnabled(v: Boolean) {
        notificationsEnabled = v
        prefs.edit().putBoolean("notificationsEnabled", v).apply()
    }

    fun updateCustomFuelPrice(v: Double) {
        if (!v.isFinite() || v < 0) return
        customFuelPrice = v
        prefs.edit().putFloat("customFuelPrice", v.toFloat()).apply()
    }

    @Deprecated("Custom accent gradients are no longer used by the native car colour picker")
    fun setCustomAccent(startArgb: Int, endArgb: Int) {
        // Keep binary/source compatibility with older callers without persisting
        // the invalid "Match my car" pseudo-colour again.
        setAccent("Blue")
    }

    /** Update the current fuel estimate without creating a history row. */
    fun setLevel(pct: Double) {
        if (!pct.isFinite()) return
        levelPct = pct.coerceIn(0.0, 100.0)
        odoForLevel = currentOdo
        prefs.edit()
            .putFloat("levelPct", levelPct.toFloat())
            .putFloat("odoForLevel", odoForLevel.toFloat())
            .apply()
    }

    /** Routine action: record the level that is currently visible on the car gauge. */
    fun logFuelLevel(pct: Double) {
        if (!pct.isFinite()) return
        val normalized = pct.coerceIn(0.0, 100.0)
        setLevel(normalized)
        val entry = FuelLevelLog(
            date = System.currentTimeMillis(),
            levelPct = normalized,
            odometer = currentOdo,
        )
        val updated = (listOf(entry) + fuelLogs).take(300)
        fuelLogs = updated
        saveFuelLogs(updated)
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

    /**
     * Occasional action: record an actual refuel. Existing versions already
     * persisted these under "refuels", so the format is kept compatible.
     */
    fun logRefuel(litres: Double, cost: Double, odometer: Double, tankFull: Boolean) {
        if (!litres.isFinite() || !cost.isFinite() || !odometer.isFinite()) return
        if (litres <= 0 || cost < 0 || odometer < 0) return
        // Estimate the level at the odometer reading for this fill-up. If the
        // car's displayed odometer is ahead of Parked's GPS-tracked value, using
        // the old currentOdo here would overstate the fuel remaining on a partial
        // refuel. Older readings are treated as historical and do not roll the
        // current estimate backwards.
        val effectiveOdo = when {
            odometer > currentOdo && odometer > 0 -> odometer
            currentOdo > 0 -> currentOdo
            odometer > 0 -> odometer
            else -> 0.0
        }
        val levelBeforeRefuel = estimateLevelPctAtOdometer(effectiveOdo)
        val recordedOdo = when {
            odometer > 0 -> odometer
            currentOdo > 0 -> currentOdo
            else -> 0.0
        }
        val entry = Refuel(System.currentTimeMillis(), litres, cost, recordedOdo, tankFull)
        val newList = (listOf(entry) + refuels).take(200)
        refuels = newList
        saveRefuels(newList)

        // Refuel odometer is optional. Only move the stored odometer forward;
        // a typo or older receipt must never roll the car backwards.
        if (odometer > currentOdo && odometer > 0) {
            setOdometer(odometer)
        }

        if (tankFull) {
            setLevel(100.0)
        } else if (tankCapacity > 0) {
            setLevel(levelBeforeRefuel + litres / tankCapacity * 100.0)
        }
    }

    fun deleteRefuel(date: Long) {
        val updated = refuels.filterNot { it.date == date }
        if (updated.size == refuels.size) return
        refuels = updated
        saveRefuels(updated)
    }

    fun averageL100km(): Double? {
        // Work between full-tank anchor points, but include every partial refuel
        // that happened between them. Refuels are stored newest-first.
        val fullIndices = refuels.indices.filter { refuels[it].tankFull }.take(6)
        if (fullIndices.size < 2) return null

        val newestFullIndex = fullIndices.first()
        val oldestFullIndex = fullIndices.last()
        val newestFull = refuels[newestFullIndex]
        val oldestFull = refuels[oldestFullIndex]
        val distanceKm = newestFull.odometer - oldestFull.odometer
        if (distanceKm <= 50.0) return null

        val litresUsed = refuels
            .subList(newestFullIndex, oldestFullIndex)
            .sumOf { it.litres }
        if (litresUsed <= 0.0) return null
        return litresUsed / distanceKm * 100.0
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

    fun estimateLevelPct(): Double = estimateLevelPctAtOdometer(currentOdo)

    fun estimatedRangeRemainingKm(): Double? {
        val range = estimatedRangeKm() ?: return null
        return range * estimateLevelPct() / 100.0
    }

    fun shouldShowPrompt(): Boolean {
        if (!isConfigured) return false
        if (refuels.isEmpty()) return false
        return currentOdo - promptDismissedAtOdo > 50
    }

    fun dismissPrompt() {
        promptDismissedAtOdo = currentOdo
        prefs.edit().putFloat("promptDismissedAtOdo", currentOdo.toFloat()).apply()
    }

    /**
     * Version 1 stored every fuel-meter save in the `refuels` array even though
     * that screen was being used as a routine tank-level log. On the first 0.2.x
     * launch, convert those legacy rows once so old user history keeps its date
     * and odometer but does not masquerade as a real refuel.
     *
     * If `fuelLevelLogs` already exists, the user has already run a 0.2.x build,
     * so any rows in `refuels` may be genuine new refuels and must not be moved.
     */
    private fun migrateLegacyFuelMeterEntriesIfNeeded() {
        synchronized(legacyMigrationLock) {
            val key = "legacyFuelMeterMigratedV2"
            if (prefs.getBoolean(key, false)) return

            // Presence of the new key is a stronger signal than an empty list: a
            // user may have created then deleted all 0.2.x fuel logs. In that case
            // genuine new Refuel entries must never be reclassified as legacy logs.
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

                // One synchronous transaction prevents the Activity and foreground
                // service from racing this one-time migration and guarantees that the
                // exact v1 JSON backup is on disk before the legacy Refuel bucket is
                // cleared. The stored backup is intentionally never deleted here.
                val editor = prefs.edit()
                    .putString("legacyRefuelsBackupV1", legacyRaw)
                    .putString("fuelLevelLogs", migratedJson)
                    .putString("refuels", "[]")
                    .putBoolean(key, true)
                if (latest != null) {
                    editor
                        .putFloat("levelPct", latest.levelPct.toFloat())
                        .putFloat("odoForLevel", (latest.odometer.takeIf { it > 0 } ?: currentOdo).toFloat())
                }
                editor.commit()

                fuelLogs = migrated
                refuels = emptyList()
                latest?.let {
                    levelPct = it.levelPct
                    odoForLevel = it.odometer.takeIf { value -> value > 0 } ?: currentOdo
                }
            } else {
                // No v1 rows to migrate, or the new fuel-log schema was already in
                // use. Mark the migration complete without touching any history.
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
