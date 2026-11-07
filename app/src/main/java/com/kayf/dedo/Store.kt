package com.kayf.dedo

import android.content.Context

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("dedo", Context.MODE_PRIVATE)

    val pins: MutableList<String> =
        prefs.getString("pins", "")!!.split('\n').filter { it.isNotEmpty() }.toMutableList()
    val hidden: MutableSet<String> = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()

    var railOnLeft: Boolean
        get() = prefs.getBoolean("railOnLeft", false)
        set(value) = prefs.edit().putBoolean("railOnLeft", value).apply()

    var edge: Boolean
        get() = prefs.getBoolean("edge", false)
        set(value) = prefs.edit().putBoolean("edge", value).apply()

    fun pin(key: String) {
        if (key !in pins) pins += key
        savePins()
    }

    fun unpin(key: String) {
        pins -= key
        savePins()
    }

    fun hide(key: String) {
        hidden += key
        pins -= key
        savePins()
        saveHidden()
    }

    fun unhide(key: String) {
        hidden -= key
        saveHidden()
    }

    fun launches(key: String) = prefs.getInt("n:$key", 0)

    fun launched(key: String) = prefs.edit().putInt("n:$key", launches(key) + 1).apply()

    private fun savePins() = prefs.edit().putString("pins", pins.joinToString("\n")).apply()

    private fun saveHidden() = prefs.edit().putStringSet("hidden", hidden.toSet()).apply()
}
