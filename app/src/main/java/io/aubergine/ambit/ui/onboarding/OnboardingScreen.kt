package io.aubergine.ambit.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.theme.AmbitColors

/**
 * First run. Says what the app does, what it cannot do, and why it needs location — before
 * the system dialog appears, so the request is not the first the user hears of it.
 */
@Composable
fun OnboardingScreen(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = AmbitColors.Accent,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        AmbitPanel(title = stringResource(R.string.onboarding_what_title)) {
            Body(stringResource(R.string.onboarding_what_body))
        }

        AmbitPanel(
            title = stringResource(R.string.onboarding_cannot_title),
            borderColor = AmbitColors.Warn,
            titleColor = AmbitColors.Warn,
        ) {
            Body(stringResource(R.string.onboarding_cannot_body))
        }

        AmbitPanel(title = stringResource(R.string.onboarding_location_title)) {
            Body(stringResource(R.string.onboarding_location_body))
        }

        AmbitPanel(
            title = stringResource(R.string.onboarding_offline_title),
            borderColor = AmbitColors.Ok,
            titleColor = AmbitColors.Ok,
        ) {
            Body(stringResource(R.string.onboarding_offline_body))
        }

        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!permissionsGranted) {
                AmbitButton(
                    label = stringResource(R.string.action_grant_permissions),
                    onClick = onRequestPermissions,
                )
            }
            AmbitButton(
                label = stringResource(R.string.action_continue),
                onClick = onContinue,
                color = if (permissionsGranted) AmbitColors.Accent else AmbitColors.AccentDim,
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    text.split("\n").forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.startsWith("  ")) AmbitColors.TextDim else AmbitColors.TextSecondary,
        )
    }
}
