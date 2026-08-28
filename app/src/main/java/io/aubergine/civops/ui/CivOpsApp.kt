package io.aubergine.civops.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aubergine.civops.R
import io.aubergine.civops.ui.alerts.AlertsScreen
import io.aubergine.civops.ui.common.CivChip
import io.aubergine.civops.ui.detail.DetailScreen
import io.aubergine.civops.ui.export.ExportScreen
import io.aubergine.civops.ui.detail.DetailViewModel
import io.aubergine.civops.ui.onboarding.OnboardingScreen
import io.aubergine.civops.ui.onboarding.Permissions
import io.aubergine.civops.ui.ops.OpsScreen
import io.aubergine.civops.ui.ops.OpsViewModel
import io.aubergine.civops.ui.settings.SettingsScreen
import io.aubergine.civops.ui.settings.SettingsViewModel
import io.aubergine.civops.ui.theme.CivColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val ONBOARDING = "onboarding"
    const val OPS = "ops"
    const val ALERTS = "alerts"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{address}"

    fun detail(address: String) = "detail/$address"
}

/**
 * Navigation shell.
 *
 * Three destinations across the bottom because that is what fits under a thumb on a
 * mounted phone. Device detail is pushed on top rather than being a fourth tab — it is
 * always reached from a row, never browsed to.
 */
@Composable
fun CivOpsApp(startOnAlerts: Boolean = false) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    var permissionsGranted by rememberSaveable {
        mutableStateOf(Permissions.missing(context).isEmpty())
    }
    var onboardingDone by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = Permissions.hasLocation(context)
    }

    val opsViewModel: OpsViewModel = hiltViewModel()
    val opsState by opsViewModel.state.collectAsStateWithLifecycle()
    val busy by opsViewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(opsViewModel) {
        opsViewModel.eventFlow.collect { event ->
            when (event) {
                is OpsViewModel.Event.Exported -> {
                    val artifact = event.artifact
                    snackbar.showSnackbar(
                        context.getString(
                            R.string.toast_export_saved,
                            artifact.file.name,
                            artifact.bytes,
                        ),
                    )
                }
                is OpsViewModel.Event.Share -> context.startActivity(event.intent)
                is OpsViewModel.Event.Message -> snackbar.showSnackbar(event.text)
                is OpsViewModel.Event.Failed -> snackbar.showSnackbar(event.text)
            }
        }
    }

    LaunchedEffect(startOnAlerts) {
        if (startOnAlerts) navController.navigate(Routes.ALERTS)
    }

    val start = if (permissionsGranted && onboardingDone) Routes.OPS else Routes.ONBOARDING

    Scaffold(
        containerColor = CivColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            if (route != null && route != Routes.ONBOARDING) {
                BottomBar(
                    current = route,
                    alertCount = opsState.alerts.size,
                    onSelect = { destination ->
                        navController.navigate(destination) {
                            popUpTo(Routes.OPS) { inclusive = destination == Routes.OPS }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = {
                        permissionLauncher.launch(Permissions.runtimePermissions().toTypedArray())
                    },
                    onContinue = {
                        onboardingDone = true
                        navController.navigate(Routes.OPS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                    contentPadding = padding,
                )
            }

            composable(Routes.OPS) {
                OpsScreen(
                    state = opsState,
                    busy = busy,
                    onStartWifi = opsViewModel::startWifi,
                    onStopWifi = opsViewModel::stopWifi,
                    onStartBle = opsViewModel::startBle,
                    onStopBle = opsViewModel::stopBle,
                    onToggleBleDetails = opsViewModel::toggleBleDetails,
                    onDeviceClick = { navController.navigate(Routes.detail(it.address)) },
                    onExportCsv = opsViewModel::exportDevicesCsv,
                    onExportJson = opsViewModel::exportJson,
                    onExportDebrief = { opsViewModel.exportDebrief() },
                    onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                    contentPadding = padding,
                )
            }

            composable(Routes.EXPORT) {
                val preview by opsViewModel.debriefPreview.collectAsStateWithLifecycle()
                val last by opsViewModel.lastArtifact.collectAsStateWithLifecycle()
                ExportScreen(
                    busy = busy,
                    debriefPreview = preview,
                    onExportDeviceCsv = opsViewModel::exportDevicesCsv,
                    onExportLogCsv = opsViewModel::exportObservationsCsv,
                    onExportJson = opsViewModel::exportJson,
                    onExportDebrief = { opsViewModel.exportDebrief() },
                    onPreviewDebrief = opsViewModel::previewDebrief,
                    onShareLast = opsViewModel::shareLastExport,
                    canShare = last != null,
                    contentPadding = padding,
                )
            }

            composable(Routes.ALERTS) {
                AlertsScreen(
                    alerts = opsState.alerts,
                    busy = busy,
                    onRunNow = opsViewModel::runCorrelatorNow,
                    onDismiss = opsViewModel::dismissAlert,
                    contentPadding = padding,
                )
            }

            composable(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                val settingsBusy by settingsViewModel.busy.collectAsStateWithLifecycle()

                LaunchedEffect(settingsState.message) {
                    settingsState.message?.let {
                        snackbar.showSnackbar(it)
                        settingsViewModel.consumeMessage()
                    }
                }

                SettingsScreen(
                    state = settingsState,
                    busy = settingsBusy,
                    onSetWifiInterval = settingsViewModel::setWifiInterval,
                    onSetListLimit = settingsViewModel::setListLimit,
                    onSetRetention = settingsViewModel::setRetentionDays,
                    onSetAudioAlerts = settingsViewModel::setAudioAlerts,
                    onSetAlertNew = settingsViewModel::setAlertOnNewDevice,
                    onSetAlertPriority = settingsViewModel::setAlertOnPriority,
                    onSetTrackerAlerts = settingsViewModel::setTrackerAlerts,
                    onSetKeepScreenOn = settingsViewModel::setKeepScreenOn,
                    onMarkSafeZone = {
                        settingsViewModel.markCurrentLocationSafe(
                            context.getString(R.string.default_known_place_label),
                        )
                    },
                    onDeleteSafeZone = settingsViewModel::deleteSafeZone,
                    onPrune = settingsViewModel::pruneNow,
                    onClearAll = settingsViewModel::clearAll,
                    contentPadding = padding,
                )
            }

            composable(Routes.DETAIL) {
                val detailViewModel: DetailViewModel = hiltViewModel()
                val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                DetailScreen(
                    state = detailState,
                    now = opsState.nowMillis,
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    current: String,
    alertCount: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CivColors.Panel)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab(stringResource(R.string.tab_ops), current.startsWith("ops") || current.startsWith("detail")) {
            onSelect(Routes.OPS)
        }
        Tab(
            label = if (alertCount > 0) {
                stringResource(R.string.tab_alerts_count, alertCount)
            } else {
                stringResource(R.string.tab_alerts)
            },
            selected = current == Routes.ALERTS,
            tint = if (alertCount > 0) CivColors.Danger else null,
        ) { onSelect(Routes.ALERTS) }
        Tab(stringResource(R.string.tab_export), current == Routes.EXPORT) {
            onSelect(Routes.EXPORT)
        }
        Tab(stringResource(R.string.tab_settings), current == Routes.SETTINGS) {
            onSelect(Routes.SETTINGS)
        }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val color = tint ?: if (selected) CivColors.Accent else CivColors.TextDim
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        CivChip(label = label, color = color)
    }
}
