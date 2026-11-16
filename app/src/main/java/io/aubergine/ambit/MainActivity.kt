package io.aubergine.ambit

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.security.AppLock
import io.aubergine.ambit.security.Disguise
import io.aubergine.ambit.security.QuickExit
import io.aubergine.ambit.security.ShakeDetector
import io.aubergine.ambit.ui.AmbitApp
import io.aubergine.ambit.ui.lock.LockScreen
import io.aubergine.ambit.ui.theme.AmbitColors
import io.aubergine.ambit.ui.theme.AmbitTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// FragmentActivity rather than ComponentActivity because BiometricPrompt needs one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var appLock: AppLock

    private var latest = SettingsRepository.Settings()
    private val shake = ShakeDetector()
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!latest.shakeToExit) return
            val v = event.values
            if (shake.onSample(v[0], v[1], v[2], SystemClock.elapsedRealtime())) QuickExit.exit(this@MainActivity)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            settings.settings.collect { s ->
                val previous = latest
                latest = s
                // The app is used on a dashboard mount where the screen locking mid-sweep
                // costs the operator the readout they mounted it for.
                window.setFlag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, s.keepScreenOn)
                // Hides the app's content in recent apps; it also blocks screenshots.
                window.setFlag(WindowManager.LayoutParams.FLAG_SECURE, s.hideInRecents)
                if (s.disguised != previous.disguised) Disguise.apply(this@MainActivity, s.disguised)
            }
        }

        val startOnAlerts = intent?.action == ACTION_SHOW_ALERTS
        val settingsOrNull = settings.settings
            .map<SettingsRepository.Settings, SettingsRepository.Settings?> { it }
            .distinctUntilChanged()

        setContent {
            AmbitTheme {
                val current by settingsOrNull.collectAsStateWithLifecycle(initialValue = null)
                val locked by appLock.locked.collectAsStateWithLifecycle()
                val s = current
                when {
                    // Settings not read yet: show nothing rather than flash unlocked content.
                    s == null -> Box(Modifier.fillMaxSize().background(AmbitColors.Background))
                    s.appLock && locked -> LockScreen(
                        onPin = appLock::tryPin,
                        biometricAvailable = s.biometricUnlock && canUseBiometrics(),
                        onBiometric = ::promptBiometric,
                        onQuickExit = if (s.quickExitButton) ({ QuickExit.exit(this) }) else null,
                    )
                    else -> AmbitApp(
                        startOnAlerts = startOnAlerts,
                        onQuickExit = if (s.quickExitButton) ({ QuickExit.exit(this) }) else null,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sensors = ContextCompat.getSystemService(this, SensorManager::class.java)
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        ContextCompat.getSystemService(this, SensorManager::class.java)?.unregisterListener(shakeListener)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Relock whenever the app leaves the screen, unless it is just rotating.
        if (!isChangingConfigurations) appLock.lock()
    }

    private fun canUseBiometrics(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appLock.unlock()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setNegativeButtonText(getString(R.string.lock_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    private fun android.view.Window.setFlag(flag: Int, on: Boolean) {
        if (on) addFlags(flag) else clearFlags(flag)
    }

    companion object {
        const val ACTION_SHOW_ALERTS = "io.aubergine.ambit.SHOW_ALERTS"
    }
}
