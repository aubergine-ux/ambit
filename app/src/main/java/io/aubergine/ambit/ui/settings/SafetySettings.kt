package io.aubergine.ambit.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.map.MbTiles
import io.aubergine.ambit.security.PinHasher
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.common.ToggleRow
import io.aubergine.ambit.ui.theme.AmbitColors

class SafetyCallbacks(
    val onSetPin: (String) -> Unit,
    val onDisableLock: () -> Unit,
    val onBiometric: (Boolean) -> Unit,
    val onHideInRecents: (Boolean) -> Unit,
    val onDisguise: (Boolean) -> Unit,
    val onQuickExit: (Boolean) -> Unit,
    val onShakeToExit: (Boolean) -> Unit,
    val onDiscreet: (Boolean) -> Unit,
    val onImportMap: (Uri) -> Unit,
    val onRemoveMap: () -> Unit,
    val onOpenHelp: () -> Unit,
)

/** Settings for someone who may be watched: lock, disguise, quick exit, quiet alerts. */
@Composable
fun SafetyPanel(settings: SettingsRepository.Settings, callbacks: SafetyCallbacks) {
    var choosingPin by remember { mutableStateOf(false) }

    AmbitPanel(title = stringResource(R.string.panel_safety)) {
        ToggleRow(
            label = stringResource(R.string.settings_app_lock),
            checked = settings.appLock,
            onChange = { on -> if (on) choosingPin = true else callbacks.onDisableLock() },
        )
        Note(stringResource(R.string.settings_app_lock_note))
        if (settings.appLock) {
            ToggleRow(stringResource(R.string.settings_biometric), settings.biometricUnlock, callbacks.onBiometric)
        }
        ToggleRow(stringResource(R.string.settings_hide_recents), settings.hideInRecents, callbacks.onHideInRecents)
        Note(stringResource(R.string.settings_hide_recents_note))
        ToggleRow(stringResource(R.string.settings_disguise), settings.disguised, callbacks.onDisguise)
        Note(stringResource(R.string.settings_disguise_note))
        ToggleRow(stringResource(R.string.settings_quick_exit), settings.quickExitButton, callbacks.onQuickExit)
        ToggleRow(stringResource(R.string.settings_shake_exit), settings.shakeToExit, callbacks.onShakeToExit)
        Note(stringResource(R.string.settings_quick_exit_note))
        ToggleRow(stringResource(R.string.settings_discreet), settings.discreetNotifications, callbacks.onDiscreet)
        Note(stringResource(R.string.settings_discreet_note))
        AmbitButton(
            label = stringResource(R.string.action_help),
            onClick = callbacks.onOpenHelp,
            color = AmbitColors.Warn,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }

    if (choosingPin) {
        PinDialog(
            onDismiss = { choosingPin = false },
            onConfirm = { pin ->
                choosingPin = false
                callbacks.onSetPin(pin)
            },
        )
    }
}

@Composable
fun OfflineMapPanel(imported: MbTiles.Metadata?, onImport: (Uri) -> Unit, onRemove: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    AmbitPanel(title = stringResource(R.string.panel_offline_map)) {
        Note(stringResource(R.string.settings_map_note))
        if (imported != null) {
            FieldLine(stringResource(R.string.settings_map_current), imported.name ?: stringResource(R.string.settings_map_unnamed))
            FieldLine(stringResource(R.string.settings_map_zoom), "${imported.minZoom}–${imported.maxZoom}")
            AmbitOutlineButton(
                stringResource(R.string.action_remove_map),
                onRemove,
                color = AmbitColors.Danger,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            FieldLine(stringResource(R.string.settings_map_current), stringResource(R.string.settings_map_bundled))
        }
        AmbitButton(
            stringResource(R.string.action_import_map),
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = PinHasher.isAcceptable(pin) && pin == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_dialog_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.pin_dialog_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = again,
                    onValueChange = { again = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.pin_dialog_again)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = again.isNotEmpty() && again != pin,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text(stringResource(R.string.pin_dialog_set)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = AmbitColors.PanelRaised,
        titleContentColor = AmbitColors.TextPrimary,
        textContentColor = AmbitColors.TextSecondary,
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextDim, modifier = Modifier.padding(bottom = 4.dp))
}
