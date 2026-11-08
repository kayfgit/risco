package com.kayf.risco

import android.content.Context

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("risco", Context.MODE_PRIVATE)

    val pins: MutableList<String> =
        prefs.getString("pins", "")!!.split('\n').filter { it.isNotEmpty() }.toMutableList()
    val hidden: MutableSet<String> = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()

    /** The rail last used, which is the one drawn. */
    var railSide: Side
        get() = if (prefs.getBoolean("railLeft", false)) Side.LEFT else Side.RIGHT
        set(value) = prefs.edit().putBoolean("railLeft", value == Side.LEFT).apply()

    /** Set once the first app opens, which retires the welcome text. */
    var welcomed: Boolean
        get() = prefs.getBoolean("welcomed", false)
        set(value) = prefs.edit().putBoolean("welcomed", value).apply()

    /** One of [Command.CLOCK]'s choices. */
    var clock: String
        get() = prefs.getString("clock", "off")!!
        set(value) = prefs.edit().putString("clock", value).apply()

    /** The keyboard's height last time it was up, including the navigation bar under it. 0 until then. */
    var keyboardHeight: Int
        get() = prefs.getInt("keyboardHeight", 0)
        set(value) = prefs.edit().putInt("keyboardHeight", value).apply()

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
