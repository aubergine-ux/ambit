package io.aubergine.ambit.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Swaps the launcher entry between the real icon and a plain "Notes" one.
 *
 * This hides the app from a glance at the home screen, not from someone who opens the
 * system's app list or settings, where the real name still appears. The settings screen
 * says so.
 */
object Disguise {
    private const val DEFAULT = "io.aubergine.ambit.LauncherDefault"
    private const val DISGUISED = "io.aubergine.ambit.LauncherDisguised"

    fun apply(context: Context, disguised: Boolean) {
        val pm = context.packageManager
        set(pm, context, DISGUISED, disguised)
        set(pm, context, DEFAULT, !disguised)
    }

    private fun set(pm: PackageManager, context: Context, alias: String, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        runCatching {
            pm.setComponentEnabledSetting(ComponentName(context.packageName, alias), state, PackageManager.DONT_KILL_APP)
        }
    }
}
