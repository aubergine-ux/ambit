package io.aubergine.ambit.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.theme.AmbitColors

/**
 * Offline guidance for someone who thinks they are being tracked. It names no country,
 * service or phone number, because those differ everywhere and a wrong number in a crisis
 * is worse than none; it tells people what kind of help to look for instead.
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmbitPanel(title = stringResource(R.string.help_danger_title), borderColor = AmbitColors.Danger, titleColor = AmbitColors.Danger) {
            Body(stringResource(R.string.help_danger_body))
        }
        Section(R.string.help_support_title, R.string.help_support_body)
        Section(R.string.help_evidence_title, R.string.help_evidence_body)
        Section(R.string.help_phone_title, R.string.help_phone_body)
        Section(R.string.help_plan_title, R.string.help_plan_body)
        Section(R.string.help_app_title, R.string.help_app_body)
        AmbitOutlineButton(stringResource(R.string.action_back), onBack, color = AmbitColors.TextSecondary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Section(title: Int, body: Int) {
    AmbitPanel(title = stringResource(title)) {
        stringResource(body).split("\n").filter { it.isNotBlank() }.forEach { line ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", color = AmbitColors.Accent, modifier = Modifier.padding(end = 8.dp))
                Body(line.trim())
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AmbitColors.TextPrimary)
}
