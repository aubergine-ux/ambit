package io.aubergine.ambit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.aubergine.ambit.locate.Proximity
import io.aubergine.ambit.locate.Trend
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.locate.LocateScreen
import io.aubergine.ambit.ui.locate.LocateViewModel
import io.aubergine.ambit.ui.theme.AmbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocateScreenTest {

    @get:Rule val compose = createComposeRule()

    private var played = 0

    private fun show(state: LocateViewModel.UiState) = compose.setContent {
        AmbitTheme {
            LocateScreen(
                state = state,
                onVisible = {},
                onHaptics = {},
                onAudio = {},
                onPlaySound = { played++ },
                onStopSound = {},
                onOpenGuide = {},
                onBack = {},
            )
        }
    }

    private val now = 1_000_000L

    private fun heard(family: TrackerFamily, sound: LocateViewModel.Sound) = LocateViewModel.UiState(
        family = family,
        smoothedRssi = -58.0,
        lastRssi = -57,
        strength = 0.7f,
        proximity = Proximity.HOT,
        trend = Trend.STRONGER,
        lastHeardAt = now - 1_000,
        now = now,
        address = "C1:23:45:67:89:AB",
        sound = sound,
    )

    @Test
    fun `before anything is heard the meter says it is listening`() {
        show(LocateViewModel.UiState(family = TrackerFamily.APPLE_FIND_MY, now = now))
        compose.onNodeWithText("LISTENING").assertIsDisplayed()
        compose.onNodeWithText("signal strength, not direction", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a strong signal reads as hot and getting stronger`() {
        show(heard(TrackerFamily.APPLE_FIND_MY, LocateViewModel.Sound.Ready))
        compose.onNodeWithText("HOT").assertIsDisplayed()
        compose.onNodeWithText("Getting stronger").assertIsDisplayed()
    }

    @Test
    fun `play sound asks first and only plays after confirmation`() {
        show(heard(TrackerFamily.APPLE_FIND_MY, LocateViewModel.Sound.Ready))
        compose.onNodeWithText("PLAY SOUND ON TRACKER").performScrollTo().performClick()
        compose.onNodeWithText("Make the tracker beep?").assertIsDisplayed()
        assertEquals(0, played)
        compose.onNodeWithText("Connect and beep").performClick()
        assertEquals(1, played)
    }

    @Test
    fun `networks that cannot be rung say so instead of offering a button`() {
        show(heard(TrackerFamily.TILE, LocateViewModel.Sound.Unsupported))
        compose.onNodeWithText("doesn't let other people make it beep", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PLAY SOUND ON TRACKER").assertDoesNotExist()
    }

    @Test
    fun `play sound is disabled once the signal is lost`() {
        show(heard(TrackerFamily.APPLE_FIND_MY, LocateViewModel.Sound.Ready).copy(lastHeardAt = now - 60_000))
        compose.onNodeWithText("PLAY SOUND ON TRACKER").performScrollTo().assertIsNotEnabled()
    }
}
