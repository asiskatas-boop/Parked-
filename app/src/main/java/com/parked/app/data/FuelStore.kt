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

    var accentName: String by mutableStateOf(prefs.getString("accentName", "Electric") ?: "Electric")
        private set

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

    fun setLevel(pct: Double) {
        levelPct = pct.coerceIn(0.0, 100.0)
        odoForLevel = currentOdo
        prefs.edit()
            .putFloat("levelPct", levelPct.toFloat())
            .putFloat("odoForLevel", odoForLevel.toFloat())
            .apply()
    }

    fun logRefuel(litres: Double, cost: Double, odometer: Double, tankFull: Boolean) {
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
        }
    }

    /** Average L/100km from the last few full-tank fills. Null if not enough data. */
    fun averageL100km(): Double? {
        val full = refuels.filter { it.tankFull }.take(6)
        if (full.size < 2) return null
        val newest = full.first()
        val oldest = full.last()
        val dist = newest.odometer - oldest.odometer
        if (dist <= 50) return null
        val litres = full.dropLast(1).sumOf { it.litres }
        return litres / dist * 100.0
    }

    fun estimatedRangeKm(): Double? {
        val avg = averageL100km() ?: return null
        if (avg <= 0) return null
        return tankCapacity / avg * 100.0
    }

    /** Live estimate of current fuel level (0-100) */
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
