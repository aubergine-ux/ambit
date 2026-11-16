package io.aubergine.ambit.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.data.db.toEntity
import io.aubergine.ambit.locate.Proximity
import io.aubergine.ambit.locate.Trend
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.alerts.AlertsScreen
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.evidence.EvidenceScreen
import io.aubergine.ambit.ui.evidence.EvidenceViewModel
import io.aubergine.ambit.ui.locate.LocateScreen
import io.aubergine.ambit.ui.locate.LocateViewModel
import io.aubergine.ambit.ui.radar.RadarView
import io.aubergine.ambit.ui.theme.AmbitColors
import io.aubergine.ambit.ui.theme.AmbitTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the README screenshots from the real screens with sample data. Skipped unless
 * run with -Pambit.screenshots (see app/build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h820dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadmeScreenshots {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = System.getProperty("ambit.screenshots").orEmpty()
    private val now = 1_794_816_000_000L
    private val minute = 60_000L

    @Before
    fun only_when_asked() = assumeTrue(outDir.isNotBlank())

    private fun save(name: String, content: @Composable () -> Unit) {
        compose.setContent { AmbitTheme { content() } }
        compose.waitForIdle()
        // Software draw of the whole window. captureToImage() waits for a hardware frame
        // that Robolectric never produces.
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(outDir).mkdirs()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** An AirTag riding along on a commute from Oakland across the Bay Bridge into San Francisco. */
    private fun trail(): TrackerCorrelator.Alert {
        val stops = listOf(37.8044 to -122.2712, 37.8160 to -122.3150, 37.8000 to -122.3700, 37.7897 to -122.3942, 37.7793 to -122.4193)
        val sightings = stops.flatMapIndexed { i, (lat, lon) ->
            (0 until 6).map { k ->
                Observation(
                    radio = RadioType.BLE,
                    address = "C1:23:45:67:89:AB",
                    name = null,
                    rssi = -55 - k,
                    companyId = 0x004C,
                    manufacturerDataHex = "121910" + "AB".repeat(22) + "0100",
                    latitude = lat + k * 0.0002,
                    longitude = lon,
                    accuracyMeters = 8f,
                    timestamp = now - (100 - i * 20) * minute + k * minute,
                )
            }
        }
        return TrackerCorrelator(SignatureExtractor()).analyse(sightings, now = now).single()
    }

    /** The launcher icon at 512 px for F-Droid and the README, drawn from the real drawables. */
    @Test
    fun icon() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(io.aubergine.ambit.R.color.ic_launcher_background)
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.22f, size * 0.22f, paint)
        // Adaptive icon foregrounds are 108dp with the visible area in the middle 72dp.
        val foreground = checkNotNull(context.getDrawable(io.aubergine.ambit.R.drawable.ic_launcher_foreground))
        val inset = (size * 18f / 72f).toInt()
        foreground.setBounds(-inset, -inset, size + inset, size + inset)
        foreground.draw(canvas)
        File(outDir).mkdirs()
        File(outDir, "icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun alerts() = save("alerts") {
        AlertsScreen(
            alerts = listOf(trail().toEntity(notifiedAt = now)),
            busy = false, onRunNow = {}, onDismiss = {}, onLocate = {}, onGuide = {},
            onTrust = {}, onEvidence = {}, onHelp = {},
        )
    }

    @Test
    fun locate() = save("locate") {
        LocateScreen(
            state = LocateViewModel.UiState(
                family = TrackerFamily.APPLE_FIND_MY,
                smoothedRssi = -57.0,
                lastRssi = -56,
                strength = 0.72f,
                proximity = Proximity.HOT,
                trend = Trend.STRONGER,
                lastHeardAt = now - 1_000,
                now = now,
                address = "C1:23:45:67:89:AB",
                hapticsAvailable = true,
                sound = LocateViewModel.Sound.Ready,
            ),
            onVisible = {}, onHaptics = {}, onAudio = {}, onPlaySound = {}, onStopSound = {}, onOpenGuide = {}, onBack = {},
        )
    }

    @Test
    fun evidence() = save("evidence") {
        EvidenceScreen(
            state = EvidenceViewModel.UiState(loading = false, alert = trail()),
            onExportPdf = {}, onExportGpx = {}, onExportKml = {}, onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "w400dp-h420dp-xxhdpi")
    fun radar() = save("radar") {
        val devices = (0 until 18).map { i ->
            Device(
                address = "%02X:11:22:33:44:%02X".format(i * 13, i),
                radio = if (i % 3 == 0) RadioType.BLE else RadioType.WIFI,
                name = "net-$i",
                vendor = null,
                category = DeviceCategory.LEGEND[i % DeviceCategory.LEGEND.size],
                lastRssi = -45 - (i * 3) % 45,
                bestRssi = -45,
                frequencyMhz = null,
                capabilities = null,
                firstSeen = now - 600_000,
                lastSeen = now,
                observationCount = 10,
            )
        }
        Column(Modifier.fillMaxSize().background(AmbitColors.Background).padding(10.dp)) {
            AmbitPanel(title = "RADAR") { RadarView(devices = devices, sweepEnabled = false) }
        }
    }
}
