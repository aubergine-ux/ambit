package io.aubergine.ambit.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Follows the system's Battery Saver switch, so scanning backs off when the user asks the phone to. */
@Singleton
class PowerState @Inject constructor(context: Context) {

    private val manager = ContextCompat.getSystemService(context, PowerManager::class.java)
    private val _saving = MutableStateFlow(manager?.isPowerSaveMode == true)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        ContextCompat.registerReceiver(
            context.applicationContext,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    _saving.value = manager?.isPowerSaveMode == true
                }
            },
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun isIgnoringOptimizations(packageName: String): Boolean =
        manager?.isIgnoringBatteryOptimizations(packageName) == true
}
