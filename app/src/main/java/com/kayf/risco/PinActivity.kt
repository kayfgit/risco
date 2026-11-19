package com.kayf.risco

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.PinItemRequest
import android.os.Bundle
import android.util.Log

/**
 * Another app asking to put one of its shortcuts on home, such as Chrome's "Add to home screen" or
 * a chat pinned from a messaging app. Android sends this only to the default launcher. It asks
 * first, as stock launchers do, so no app can fill home on its own.
 *
 * Once accepted, Android tells [Apps] the app's shortcuts changed, and the new one lands at the end
 * of home, so nothing here has to reach the running home screen.
 */
class PinActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }
            .onFailure { Log.w(TAG, "unreadable pin request", it) }
            .getOrNull()
        val shortcut = request?.shortcutInfo
        if (request == null ||
            !request.isValid ||
            request.requestType != PinItemRequest.REQUEST_TYPE_SHORTCUT ||
            shortcut == null
        ) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_title)
            .setMessage(shortcut.shortLabel)
            .setPositiveButton(R.string.add) { _, _ ->
                if (!runCatching { request.accept() }.getOrDefault(false)) Log.w(TAG, "pin ${shortcut.id} refused")
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }

    private companion object {
        const val TAG = "risco"
    }
}
