package io.aubergine.ambit.security

import android.app.Activity
import android.content.Intent

/**
 * Leaves the app at once and shows the home screen, for when someone walks in. The task is
 * removed so the app's last screen does not appear in recent apps.
 */
object QuickExit {
    fun exit(activity: Activity) {
        activity.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        activity.finishAndRemoveTask()
    }
}
