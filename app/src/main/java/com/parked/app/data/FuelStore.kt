package com.parked.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

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
            "refuels" -> refuels = loadRefuels()
            "accentName" -> accentName = prefs.getString("accentName", "Ocean Blue") ?: "Ocean Blue"
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

    var refuels: List<Refuel> by mutableStateOf(loadRefuels())
        private set

    var accentName: String by mutableStateOf(prefs.getString("accentName", "Ocean Blue") ?: "Ocean Blue")
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
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    val currentOdo: Double get() = baselineOdo + gpsKm

    val isConfigured: Boolean get() = tankCapacity > 0 && baselineOdo > 0

    fun addKm(km: Double) {
        if (km <= 0 || km > 5) return
        gpsKm += km
        prefs.edit().putFloat("gpsKm", gpsKm.toFloat()).apply()
    }

    fun setOdometer(value: Double) {
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
        accentName = name
        prefs.edit().putString("accentName", name).apply()
    }

    fun updateNotificationsEnabled(v: Boolean) {
        notificationsEnabled = v
        prefs.edit().putBoolean("notificationsEnabled", v).apply()
    }

    fun updateCustomFuelPrice(v: Double) {
        customFuelPrice = v
        prefs.edit().putFloat("customFuelPrice", v.toFloat()).apply()
    }

    fun setCustomAccent(startArgb: Int, endArgb: Int) {
        customAccentStart = startArgb
        customAccentEnd = endArgb
        accentName = "Match my car"
        prefs.edit()
            .putInt("customAccentStart", startArgb)
            .putInt("customAccentEnd", endArgb)
            .putString("accentName", "Match my car")
            .apply()
    }

    fun setLevel(pct: Double) {
        levelPct = pct.coerceIn(0.0, 100.0)
        odoForLevel = currentOdo
        prefs.edit()
            .putFloat("levelPct", levelPct.toFloat())
            .putFloat("odoForLevel", odoForLevel.toFloat())
            .apply()
    }

    fun logRefuel(litres: Double, cost: Double, odometer: Double, tankFull: Boolean) {
        val levelBeforeRefuel = estimateLevelPct()
        val entry = Refuel(System.currentTimeMillis(), litres, cost, odometer, tankFull)
        val newList = (listOf(entry) + refuels).take(200)
        refuels = newList
        saveRefuels(newList)

        setOdometer(odometer)

        if (tankFull) {
            levelPct = 100.0
            odoForLevel = odometer
            prefs.edit()
                .putFloat("levelPct", 100f)
                .putFloat("odoForLevel", odometer.toFloat())
                .apply()
        } else if (tankCapacity > 0) {
            // The fuel screen records the volume added, not the current tank
            // contents. Partial refuels therefore need to raise the estimated
            // level instead of leaving it unchanged.
            setLevel(levelBeforeRefuel + litres / tankCapacity * 100.0)
        }
    }

    fun averageL100km(): Double? {
        // Work between full-tank anchor points, but include every partial refuel
        // that happened between them. The previous implementation filtered to
        // full-tank rows first, which silently under-counted fuel whenever a
        // partial top-up occurred. Refuels are stored newest-first.
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
        val avg = averageL100km() ?: return null
        if (avg <= 0) return null
        return tankCapacity / avg * 100.0
    }

    fun estimateLevelPct(): Double {
        val range = estimatedRangeKm() ?: return levelPct
        val driven = currentOdo - odoForLevel
        if (driven <= 0) return levelPct
        return (levelPct - driven / range * 100.0).coerceIn(0.0, 100.0)
    }

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

    private fun loadRefuels(): List<Refuel> {
        val raw = prefs.getString("refuels", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Refuel(
                    date = o.getLong("date"),
                    litres = o.getDouble("litres"),
                    cost = o.getDouble("cost"),
                    odometer = o.getDouble("odo"),
                    tankFull = o.getBoolean("tankFull"),
                )
            }
        } catch (_: Exception) { emptyList() }
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
