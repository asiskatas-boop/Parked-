package com.parked.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/** Layoutlib has no SharedPreferences; this keeps them in memory for previews. */
class MemoryPrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String?, defValue: String?) = map[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = map[key] as MutableSet<String>? ?: defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as Boolean? ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        var clear = false
        override fun putString(k: String?, v: String?) = apply { pending[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { pending[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { pending[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { pending[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { pending[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { pending[k!!] = v }
        override fun remove(k: String?) = apply { pending[k!!] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }
}

class PrefsContext(base: Context) : ContextWrapper(base) {
    private val prefs = mutableMapOf<String, MemoryPrefs>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs.getOrPut(name ?: "") { MemoryPrefs() }
}
