package com.kayf.risco

/** About what a stock launcher shows when an app is held. */
const val MAX_MENU_SHORTCUTS = 4

/**
 * The shortcuts holding an app offers: the ones it declares first, then the ones it adds while
 * running (recent chats, say), each in the order the app ranked them. When there are too many, the
 * added ones go first, as on a stock launcher.
 */
fun <T> menuShortcuts(all: List<T>, declared: (T) -> Boolean, rank: (T) -> Int): List<T> =
    all.sortedWith(compareBy({ !declared(it) }, { rank(it) })).take(MAX_MENU_SHORTCUTS)

/** A pinned shortcut's place in home's list, beside app keys. Ids are only unique within an app and a profile. */
fun shortcutKey(packageName: String, id: String, userSerial: Long) = "s:$packageName/$id@$userSerial"

fun isShortcutKey(key: String) = key.startsWith("s:")

/**
 * Home's list with any shortcut the system says is pinned, but home doesn't know of yet, added at
 * the end. That is how one pinned from another app ("Add to home screen") lands. A pin the system
 * stops reporting keeps its place, like an app that's briefly gone while it updates.
 */
fun withNewPins(pins: List<String>, pinnedShortcuts: List<String>) = pins + pinnedShortcuts.filter { it !in pins }
