package io.github.randomtools3377.fitness

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Keeps the app's preview out of the recent-apps screen. On Android 13 and later that is all it
 * does. Older phones can only hide it by also blocking screenshots, so there it is a switch in
 * Settings, on unless the user turns it off. The switch is kept on this phone only.
 */
object Privacy {
    private const val PREFS = "privacy"
    private const val HIDE_IN_RECENTS = "hide_in_recents"

    fun hideInRecents(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HIDE_IN_RECENTS, true)

    fun setHideInRecents(context: Context, hide: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HIDE_IN_RECENTS, hide).apply()
    }

    fun apply(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(false)
        } else if (hideInRecents(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
