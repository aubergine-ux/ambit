package io.aubergine.ambit.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.backup.AppRestart
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.alerts.AlertsScreen
import io.aubergine.ambit.ui.common.AmbitChip
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.detail.DetailScreen
import io.aubergine.ambit.ui.detail.DetailViewModel
import io.aubergine.ambit.ui.evidence.EvidenceScreen
import io.aubergine.ambit.ui.evidence.EvidenceViewModel
import io.aubergine.ambit.ui.expert.ExpertScreen
import io.aubergine.ambit.ui.expert.ExpertViewModel
import io.aubergine.ambit.ui.export.ExportScreen
import io.aubergine.ambit.ui.guide.GuideScreen
import io.aubergine.ambit.ui.help.HelpScreen
import io.aubergine.ambit.ui.locate.LocateScreen
import io.aubergine.ambit.ui.locate.LocateViewModel
import io.aubergine.ambit.ui.onboarding.OnboardingScreen
import io.aubergine.ambit.ui.onboarding.Permissions
import io.aubergine.ambit.ui.ops.OpsScreen
import io.aubergine.ambit.ui.ops.OpsViewModel
import io.aubergine.ambit.ui.settings.SafetyCallbacks
import io.aubergine.ambit.ui.settings.SettingsScreen
import io.aubergine.ambit.ui.settings.SettingsViewModel
import io.aubergine.ambit.ui.theme.AmbitColors

private object Routes {
    const val ONBOARDING = "onboarding"
    const val OPS = "ops"
    const val ALERTS = "alerts"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{address}"
    const val LOCATE = "locate?key={key}&family={family}&address={address}"
    const val GUIDE = "guide/{family}"
    const val EVIDENCE = "evidence?key={key}"
    const val HELP = "help"
    const val EXPERT = "expert"

    fun detail(address: String) = "detail/$address"

    fun locateTracker(key: String, family: String) = "locate?key=${Uri.encode(key)}&family=$family&address="

    fun locateAddress(address: String) = "locate?key=&family=&address=${Uri.encode(address)}"

    fun guide(family: String) = "guide/$family"

    fun evidence(key: String) = "evidence?key=${Uri.encode(key)}"
}

/**
 * Navigation shell.
 *
 * Three destinations across the bottom because that is what fits under a thumb on a
 * mounted phone. Device detail is pushed on top rather than being a fourth tab — it is
 * always reached from a row, never browsed to.
 */
@Composable
fun AmbitApp(
    launch: Intent? = null,
    onLaunchHandled: () -> Unit = {},
    onQuickExit: (() -> Unit)? = null,
) {
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

    // Notification taps, including ones that arrive while the app is already open.
    LaunchedEffect(launch) {
        val intent = launch ?: return@LaunchedEffect
        val key = intent.getStringExtra(MainActivity.EXTRA_KEY).orEmpty()
        when (intent.action) {
            MainActivity.ACTION_SHOW_ALERTS -> navController.navigate(Routes.ALERTS)
            MainActivity.ACTION_OPEN_LOCATE -> if (key.isNotEmpty()) {
                navController.navigate(Routes.locateTracker(key, intent.getStringExtra(MainActivity.EXTRA_FAMILY).orEmpty()))
            }
            MainActivity.ACTION_OPEN_EVIDENCE -> if (key.isNotEmpty()) navController.navigate(Routes.evidence(key))
        }
        onLaunchHandled()
    }

    val start = if (permissionsGranted && onboardingDone) Routes.OPS else Routes.ONBOARDING

    Scaffold(
        containerColor = AmbitColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { onQuickExit?.let { QuickExitBar(it) } },
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
                    onExportGpx = opsViewModel::exportGpx,
                    onExportKml = opsViewModel::exportKml,
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
                    onLocate = { navController.navigate(Routes.locateTracker(it.signatureKey, it.family)) },
                    onGuide = { navController.navigate(Routes.guide(it)) },
                    onTrust = { opsViewModel.trustAlert(it.signatureKey, it.family) },
                    onEvidence = { navController.navigate(Routes.evidence(it.signatureKey)) },
                    onHelp = { navController.navigate(Routes.HELP) },
                    contentPadding = padding,
                )
            }

            composable(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                val settingsBusy by settingsViewModel.busy.collectAsStateWithLifecycle()
                val restartNeeded by settingsViewModel.restartNeeded.collectAsStateWithLifecycle()
                LaunchedEffect(restartNeeded) {
                    if (restartNeeded) (context as? Activity)?.let(AppRestart::restart)
                }

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
                    onSetSensitivity = settingsViewModel::setSensitivity,
                    onSetWifiFollow = settingsViewModel::setWifiFollowAlerts,
                    onSetGlasses = settingsViewModel::setGlassesAlerts,
                    onSetKeepScreenOn = settingsViewModel::setKeepScreenOn,
                    onMarkSafeZone = {
                        settingsViewModel.markCurrentLocationSafe(
                            context.getString(R.string.default_known_place_label),
                        )
                    },
                    onDeleteSafeZone = settingsViewModel::deleteSafeZone,
                    onUntrust = settingsViewModel::untrust,
                    safety = SafetyCallbacks(
                        onSetPin = settingsViewModel::setPin,
                        onDisableLock = settingsViewModel::disableLock,
                        onBiometric = settingsViewModel::setBiometric,
                        onHideInRecents = settingsViewModel::setHideInRecents,
                        onDisguise = settingsViewModel::setDisguised,
                        onQuickExit = settingsViewModel::setQuickExit,
                        onShakeToExit = settingsViewModel::setShakeToExit,
                        onDiscreet = settingsViewModel::setDiscreet,
                        onImportMap = settingsViewModel::importMap,
                        onRemoveMap = settingsViewModel::removeMap,
                        onOpenHelp = { navController.navigate(Routes.HELP) },
                    ),
                    importedMap = settingsViewModel.importedMap.collectAsStateWithLifecycle().value,
                    reliability = settingsViewModel.reliability.collectAsStateWithLifecycle().value,
                    onRefreshReliability = settingsViewModel::refreshReliability,
                    onOpenExpert = { navController.navigate(Routes.EXPERT) },
                    encryption = settingsViewModel.encryption,
                    onBackup = settingsViewModel::backup,
                    onRestore = settingsViewModel::restore,
                    onWipe = settingsViewModel::wipeEverything,
                    onStartTravel = settingsViewModel::startTravel,
                    onEndTravel = settingsViewModel::endTravel,
                    onMarkTemporaryPlace = {
                        settingsViewModel.markTemporaryPlace(context.getString(R.string.default_temporary_place_label))
                    },
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
                    onLocate = { navController.navigate(Routes.locateAddress(it)) },
                    contentPadding = padding,
                )
            }

            composable(
                route = Routes.LOCATE,
                arguments = listOf(
                    navArgument(LocateViewModel.ARG_KEY) { defaultValue = "" },
                    navArgument(LocateViewModel.ARG_FAMILY) { defaultValue = "" },
                    navArgument(LocateViewModel.ARG_ADDRESS) { defaultValue = "" },
                ),
            ) {
                val locateViewModel: LocateViewModel = hiltViewModel()
                val locateState by locateViewModel.state.collectAsStateWithLifecycle()
                LocateScreen(
                    state = locateState,
                    onVisible = locateViewModel::setVisible,
                    onHaptics = locateViewModel::setHaptics,
                    onAudio = locateViewModel::setAudio,
                    onPlaySound = locateViewModel::playSound,
                    onStopSound = locateViewModel::stopSound,
                    onOpenGuide = { navController.navigate(Routes.guide(locateState.family.name)) },
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                )
            }

            composable(Routes.GUIDE) { entry ->
                val family = entry.arguments?.getString("family")
                    ?.let { runCatching { TrackerFamily.valueOf(it) }.getOrNull() }
                    ?: TrackerFamily.UNKNOWN
                GuideScreen(
                    initialFamily = family,
                    onBack = { navController.popBackStack() },
                    onHelp = { navController.navigate(Routes.HELP) },
                    contentPadding = padding,
                )
            }

            composable(
                route = Routes.EVIDENCE,
                arguments = listOf(navArgument(EvidenceViewModel.ARG_KEY) { defaultValue = "" }),
            ) {
                val evidenceViewModel: EvidenceViewModel = hiltViewModel()
                val evidenceState by evidenceViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(evidenceViewModel) {
                    evidenceViewModel.shareRequests.collect { context.startActivity(it) }
                }
                EvidenceScreen(
                    state = evidenceState,
                    onExportPdf = evidenceViewModel::exportPdf,
                    onExportGpx = { evidenceViewModel.exportTrail(gpx = true) },
                    onExportKml = { evidenceViewModel.exportTrail(gpx = false) },
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                )
            }

            composable(Routes.EXPERT) {
                val expertViewModel: ExpertViewModel = hiltViewModel()
                val expertState by expertViewModel.state.collectAsStateWithLifecycle()
                ExpertScreen(
                    state = expertState,
                    onPause = expertViewModel::setPaused,
                    onFilter = expertViewModel::setFilter,
                    onTrackersOnly = expertViewModel::setTrackersOnly,
                    onClear = expertViewModel::clear,
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                )
            }

            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() }, contentPadding = padding)
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
            .background(AmbitColors.Panel)
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
            tint = if (alertCount > 0) AmbitColors.Danger else null,
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
    val color = tint ?: if (selected) AmbitColors.Accent else AmbitColors.TextDim
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        AmbitChip(label = label, color = color)
    }
}

/** One tap back to the home screen. Shown only when the user turned it on. */
@Composable
private fun QuickExitBar(onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        AmbitOutlineButton(
            label = stringResource(R.string.action_quick_exit),
            onClick = onExit,
            color = AmbitColors.TextSecondary,
        )
    }
}
