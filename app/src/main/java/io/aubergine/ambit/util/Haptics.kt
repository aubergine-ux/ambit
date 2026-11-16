package io.aubergine.ambit.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/** Short vibration pulses for Locate mode, so the phone can be used without looking at it. */
@Singleton
class Haptics @Inject constructor(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getSystemService(context, VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }

    val available: Boolean get() = vibrator?.hasVibrator() == true

    fun pulse(durationMillis: Long = 40L) {
        val v = vibrator ?: return
        // Some OEM builds throw when the vibrator service is restarting. A dropped pulse is fine.
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
