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

    /** Icons beside app names. Off by default: names are enough once the thumb knows the way. */
    var icons: Boolean
        get() = prefs.getBoolean("icons", false)
        set(value) = prefs.edit().putBoolean("icons", value).apply()

    /** The widgets on home, top to bottom, by their system id. */
    val widgets: MutableList<Int> = decodeIds(prefs.getString("widgets", "")!!).toMutableList()

    /** The keyboard's height last time it was up, including the navigation bar under it. 0 until then. */
    var keyboardHeight: Int
        get() = prefs.getInt("keyboardHeight", 0)
        set(value) = prefs.edit().putInt("keyboardHeight", value).apply()

    fun pin(key: String) {
        if (key !in pins) pins += key
        savePins()
    }

    /** Adds pins made elsewhere, such as a shortcut another app pinned, to the end of home. */
    fun pinNew(keys: List<String>) {
        val next = withNewPins(pins, keys)
        if (next.size == pins.size) return
        pins.clear()
        pins += next
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

    fun addWidget(id: Int) {
        if (id !in widgets) widgets += id
        saveWidgets()
    }

    fun removeWidget(id: Int) {
        widgets -= id
        saveWidgets()
        prefs.edit().remove("wh:$id").apply()
    }

    /** Moves a widget [by] places, up for negative. */
    fun moveWidget(id: Int, by: Int) {
        val next = moved(widgets, id, by)
        widgets.clear()
        widgets += next
        saveWidgets()
    }

    /** The height someone gave a widget, in dp so it survives a display size change, or 0 for its own. */
    fun widgetHeight(id: Int) = prefs.getInt("wh:$id", 0)

    fun setWidgetHeight(id: Int, dp: Int) = prefs.edit().putInt("wh:$id", dp).apply()

    /** The name someone gave an app or shortcut, or null for its own. */
    fun name(key: String): String? = prefs.getString("name:$key", null)

    fun rename(key: String, name: String?) = prefs.edit().apply {
        if (name == null) remove("name:$key") else putString("name:$key", name)
    }.apply()

    fun launches(key: String) = prefs.getInt("n:$key", 0)

    fun launched(key: String) = prefs.edit().putInt("n:$key", launches(key) + 1).apply()

    private fun savePins() = prefs.edit().putString("pins", pins.joinToString("\n")).apply()

    private fun saveWidgets() = prefs.edit().putString("widgets", encodeIds(widgets)).apply()

    private fun saveHidden() = prefs.edit().putStringSet("hidden", hidden.toSet()).apply()
}

/** What an app or shortcut is called: the name someone gave it, if any, or its own. */
fun displayLabel(original: String, custom: String?) = custom?.trim()?.takeIf { it.isNotEmpty() } ?: original

fun encodeIds(ids: List<Int>) = ids.joinToString(",")

/** Anything that isn't a number is skipped, so a damaged list loses only what's damaged. */
fun decodeIds(text: String) = text.split(',').mapNotNull { it.trim().toIntOrNull() }
