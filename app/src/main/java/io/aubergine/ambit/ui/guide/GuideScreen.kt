package io.aubergine.ambit.ui.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.theme.AmbitColors

/** Offline content for one tracker family. Text lives in strings.xml so it can be translated. */
data class TrackerGuide(
    val family: TrackerFamily,
    @DrawableRes val illustration: Int,
    @StringRes val looksLike: Int,
    @StringRes val disable: Int,
)

val TRACKER_GUIDES: List<TrackerGuide> = listOf(
    TrackerGuide(TrackerFamily.APPLE_FIND_MY, R.drawable.ic_tracker_disc, R.string.guide_apple_looks, R.string.guide_apple_disable),
    TrackerGuide(TrackerFamily.SAMSUNG_SMARTTAG, R.drawable.ic_tracker_tag, R.string.guide_samsung_looks, R.string.guide_samsung_disable),
    TrackerGuide(TrackerFamily.GOOGLE_FIND_MY, R.drawable.ic_tracker_card, R.string.guide_google_looks, R.string.guide_google_disable),
    TrackerGuide(TrackerFamily.TILE, R.drawable.ic_tracker_square, R.string.guide_tile_looks, R.string.guide_tile_disable),
    TrackerGuide(TrackerFamily.CHIPOLO, R.drawable.ic_tracker_round, R.string.guide_chipolo_looks, R.string.guide_chipolo_disable),
    TrackerGuide(TrackerFamily.PEBBLEBEE, R.drawable.ic_tracker_card, R.string.guide_pebblebee_looks, R.string.guide_pebblebee_disable),
    TrackerGuide(TrackerFamily.DULT, R.drawable.ic_tracker_generic, R.string.guide_dult_looks, R.string.guide_dult_disable),
    TrackerGuide(TrackerFamily.WIFI_NETWORK, R.drawable.ic_tracker_generic, R.string.guide_wifi_looks, R.string.guide_wifi_disable),
    TrackerGuide(TrackerFamily.UNKNOWN, R.drawable.ic_tracker_generic, R.string.guide_unknown_looks, R.string.guide_unknown_disable),
)

fun guideFor(family: TrackerFamily): TrackerGuide =
    TRACKER_GUIDES.firstOrNull { it.family == family } ?: TRACKER_GUIDES.last()

@StringRes
fun TrackerFamily.labelRes(): Int = when (this) {
    TrackerFamily.APPLE_FIND_MY -> R.string.family_apple
    TrackerFamily.SAMSUNG_SMARTTAG -> R.string.family_samsung
    TrackerFamily.GOOGLE_FIND_MY -> R.string.family_google
    TrackerFamily.TILE -> R.string.family_tile
    TrackerFamily.CHIPOLO -> R.string.family_chipolo
    TrackerFamily.DULT -> R.string.family_dult
    TrackerFamily.PEBBLEBEE -> R.string.family_pebblebee
    TrackerFamily.WIFI_NETWORK -> R.string.family_wifi
    TrackerFamily.UNKNOWN -> R.string.family_unknown
}

@Composable
fun TrackerFamily.label(): String = stringResource(labelRes())

/**
 * How to find a tracker and what to do about it.
 *
 * Safety comes first on purpose. Switching a tracker off can tell whoever planted it that
 * it was found, and the tracker itself can be evidence; someone in danger should weigh
 * that before pulling the battery, not after.
 */
@Composable
fun GuideScreen(
    initialFamily: TrackerFamily,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var family by rememberSaveable { mutableStateOf(initialFamily) }
    val guide = guideFor(family)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmbitPanel(
            title = stringResource(R.string.guide_safety_title),
            borderColor = AmbitColors.Warn,
            titleColor = AmbitColors.Warn,
        ) {
            Body(stringResource(R.string.guide_safety_body))
        }

        AmbitPanel(title = guide.family.label()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(guide.illustration),
                    contentDescription = stringResource(R.string.guide_illustration_description, guide.family.label()),
                    colorFilter = ColorFilter.tint(AmbitColors.Accent),
                    modifier = Modifier.size(72.dp).padding(end = 12.dp),
                )
                Text(
                    text = stringResource(R.string.guide_illustration_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmbitColors.TextDim,
                )
            }
            Heading(stringResource(R.string.guide_looks_heading))
            Body(stringResource(guide.looksLike))
            Heading(stringResource(R.string.guide_disable_heading))
            Steps(stringResource(guide.disable))
        }

        AmbitPanel(title = stringResource(R.string.guide_hiding_title)) {
            Steps(stringResource(R.string.guide_hiding_body))
        }

        AmbitPanel(title = stringResource(R.string.guide_search_title)) {
            Steps(stringResource(R.string.guide_search_body))
        }

        AmbitPanel(title = stringResource(R.string.guide_other_families)) {
            TRACKER_GUIDES.filter { it.family != family }.forEach { other ->
                AmbitOutlineButton(
                    label = other.family.label(),
                    onClick = { family = other.family },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
        }

        AmbitOutlineButton(
            stringResource(R.string.action_back),
            onBack,
            color = AmbitColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AmbitColors.Accent,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() },
    )
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = AmbitColors.TextPrimary)
}

/** Strings hold one step per line so translators keep the structure without markup. */
@Composable
private fun Steps(text: String) {
    text.split("\n").filter { it.isNotBlank() }.forEachIndexed { index, step ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmbitColors.Accent,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(text = step.trim(), style = MaterialTheme.typography.bodyMedium, color = AmbitColors.TextPrimary)
        }
    }
}
