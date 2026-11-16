package io.aubergine.ambit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.aubergine.ambit.analysis.Confidence
import io.aubergine.ambit.data.db.TrackerAlertEntity
import io.aubergine.ambit.ui.alerts.AlertsScreen
import io.aubergine.ambit.ui.theme.AmbitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertsScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun alert(family: String, key: String = "k-$family") = TrackerAlertEntity(
        signatureKey = key,
        family = family,
        companyId = null,
        distinctLocations = 4,
        addressesSeen = 3,
        spreadMeters = 4200.0,
        closestRssi = -58,
        firstSeen = 1_700_000_000_000,
        lastSeen = 1_700_010_000_000,
        severity = "HIGH",
        notifiedAt = 1_700_010_000_000,
        confidence = 82,
        reasons = Confidence(
            82,
            listOf(
                Confidence.Reason(Confidence.Factor.PLACES, 28, 4),
                Confidence.Reason(Confidence.Factor.SEPARATED, 10, 0),
            ),
        ).encode(),
    )

    private val trusted = mutableListOf<String>()
    private val located = mutableListOf<String>()

    private fun show(vararg alerts: TrackerAlertEntity) = compose.setContent {
        AmbitTheme {
            AlertsScreen(
                alerts = alerts.toList(),
                busy = false,
                onRunNow = {},
                onDismiss = {},
                onLocate = { located += it.signatureKey },
                onGuide = {},
                onTrust = { trusted += it.signatureKey },
                onEvidence = {},
                onHelp = {},
            )
        }
    }

    @Test
    fun `an alert shows its score and the reasons behind it`() {
        show(alert("APPLE_FIND_MY"))
        compose.onNodeWithText("Apple Find My", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Confidence: 82/100").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seen at 4 separate places", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Said it is away from its owner", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `this is mine trusts the alert's identity`() {
        show(alert("SAMSUNG_SMARTTAG", key = "tag-1"))
        compose.onNodeWithText("THIS IS MINE").performScrollTo().performClick()
        assertEquals(listOf("tag-1"), trusted)
    }

    @Test
    fun `wifi alerts explain themselves and offer no locate`() {
        show(alert("WIFI_NETWORK"))
        compose.onNodeWithText("hotspot on someone", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("LOCATE").assertDoesNotExist()
    }

    @Test
    fun `bluetooth alerts can be located`() {
        show(alert("GOOGLE_FIND_MY", key = "g"))
        compose.onNodeWithText("LOCATE").performScrollTo().performClick()
        assertEquals(listOf("g"), located)
    }
}
