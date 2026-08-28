package io.aubergine.civops

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.aubergine.civops.data.repo.SettingsRepository
import io.aubergine.civops.ui.CivOpsApp
import io.aubergine.civops.ui.theme.CivOpsTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The app is used on a dashboard mount where the screen locking mid-sweep costs
        // the operator the readout they mounted it for.
        lifecycleScope.launch {
            settings.settings
                .map { it.keepScreenOn }
                .distinctUntilChanged()
                .collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }

        val startOnAlerts = intent?.action == ACTION_SHOW_ALERTS

        setContent {
            CivOpsTheme {
                CivOpsApp(startOnAlerts = startOnAlerts)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_ALERTS = "io.aubergine.civops.SHOW_ALERTS"
    }
}
