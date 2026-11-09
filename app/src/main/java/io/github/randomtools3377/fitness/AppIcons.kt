package io.github.randomtools3377.fitness

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.randomtools3377.fitness.core.AppIconChoice

/**
 * The app's home-screen icon (0.17.0, copied from the checklist app). Each icon is a launcher entry
 * in the manifest (an activity alias), and only the chosen one is turned on.
 */
object AppIcons {
    private fun component(context: Context, icon: AppIconChoice) = ComponentName(context.packageName, "io.github.randomtools3377.fitness.${icon.alias}")

    /** Turns on [icon]'s launcher entry and turns the others off, without closing the app. */
    fun apply(context: Context, icon: AppIconChoice) {
        val pm = context.packageManager
        // The new one first, so there is never a moment with no icon at all.
        AppIconChoice.entries.sortedBy { it != icon }.forEach { each ->
            val wanted = if (each == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val name = component(context, each)
            val state = pm.getComponentEnabledSetting(name)
            // Terracotta is on from the manifest until something else is chosen.
            val alreadyOn = each == icon && each == AppIconChoice.TERRACOTTA && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (state != wanted && !alreadyOn) pm.setComponentEnabledSetting(name, wanted, PackageManager.DONT_KILL_APP)
        }
    }
}
