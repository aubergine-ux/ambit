package io.aubergine.civops.ui.onboarding

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
import io.aubergine.civops.R
import io.aubergine.civops.ui.common.CivButton
import io.aubergine.civops.ui.common.CivPanel
import io.aubergine.civops.ui.theme.CivColors

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
            .background(CivColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = CivColors.Accent,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        CivPanel(title = stringResource(R.string.onboarding_what_title)) {
            Body(stringResource(R.string.onboarding_what_body))
        }

        CivPanel(
            title = stringResource(R.string.onboarding_cannot_title),
            borderColor = CivColors.Warn,
            titleColor = CivColors.Warn,
        ) {
            Body(stringResource(R.string.onboarding_cannot_body))
        }

        CivPanel(title = stringResource(R.string.onboarding_location_title)) {
            Body(stringResource(R.string.onboarding_location_body))
        }

        CivPanel(
            title = stringResource(R.string.onboarding_offline_title),
            borderColor = CivColors.Ok,
            titleColor = CivColors.Ok,
        ) {
            Body(stringResource(R.string.onboarding_offline_body))
        }

        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!permissionsGranted) {
                CivButton(
                    label = stringResource(R.string.action_grant_permissions),
                    onClick = onRequestPermissions,
                )
            }
            CivButton(
                label = stringResource(R.string.action_continue),
                onClick = onContinue,
                color = if (permissionsGranted) CivColors.Accent else CivColors.AccentDim,
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
            color = if (line.startsWith("  ")) CivColors.TextDim else CivColors.TextSecondary,
        )
    }
}
