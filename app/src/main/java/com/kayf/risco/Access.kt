package com.kayf.risco

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** How TalkBack reads a rail letter. `#` holds names that don't start with A to Z, so it's read as "Other". */
fun letterDescription(letter: Char, apps: Int): String {
    val name = if (letter == '#') "Other" else letter.toString()
    return "$name, $apps ${if (apps == 1) "app" else "apps"}"
}

/**
 * Something home does on a swipe or a hold. TalkBack and Switch Access can't swipe or hold on
 * home, so these go in their actions menu on everything home shows.
 */
class HomeAction(id: Int, label: String, val run: () -> Unit) {
    val action = AccessibilityNodeInfo.AccessibilityAction(id, label)
}

/** Runs [action] if it's one of [actions], for a view or a virtual one that offers them. */
fun List<HomeAction>.perform(action: Int): Boolean {
    val match = firstOrNull { it.action.id == action } ?: return false
    match.run()
    return true
}

/** Adds [actions] to what TalkBack and Switch Access offer on this view. */
fun View.offer(actions: List<HomeAction>) {
    accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            actions.forEach { info.addAction(it.action) }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?) =
            actions.perform(action) || super.performAccessibilityAction(host, action, args)
    }
}
