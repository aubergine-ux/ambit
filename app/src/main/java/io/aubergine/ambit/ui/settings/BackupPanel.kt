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
import io.aubergine.ambit.data.db.EncryptionStatus
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.LocalDate

/** Encryption status, password-protected backup and restore, and the full wipe. */
@Composable
fun BackupPanel(
    encryption: EncryptionStatus,
    onBackup: (CharArray, Uri) -> Unit,
    onRestore: (CharArray, Uri) -> Unit,
    onWipe: () -> Unit,
) {
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var restoreSource by remember { mutableStateOf<Uri?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { backupTarget = it }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreSource = it }

    AmbitPanel(title = stringResource(R.string.panel_backup)) {
        FieldLine(
            stringResource(R.string.backup_encryption),
            stringResource(
                when (encryption) {
                    EncryptionStatus.ENCRYPTED -> R.string.backup_encryption_on
                    EncryptionStatus.FAILED -> R.string.backup_encryption_failed
                    EncryptionStatus.UNKNOWN -> R.string.backup_encryption_unknown
                },
            ),
            valueColor = if (encryption == EncryptionStatus.ENCRYPTED) AmbitColors.Ok else AmbitColors.Warn,
        )
        Text(stringResource(R.string.backup_note), style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextDim)
        AmbitButton(
            stringResource(R.string.action_backup),
            onClick = { create.launch("ambit-backup-${LocalDate.now()}.ambitbak") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        AmbitOutlineButton(
            stringResource(R.string.action_restore),
            onClick = { open.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        AmbitOutlineButton(
            stringResource(R.string.action_wipe_everything),
            onClick = { confirmWipe = true },
            color = AmbitColors.Danger,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }

    backupTarget?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.backup_password_title),
            body = stringResource(R.string.backup_password_body),
            confirmTwice = true,
            onDismiss = { backupTarget = null },
            onConfirm = { password ->
                backupTarget = null
                onBackup(password, uri)
            },
        )
    }
    restoreSource?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.restore_password_title),
            body = stringResource(R.string.restore_password_body),
            confirmTwice = false,
            onDismiss = { restoreSource = null },
            onConfirm = { password ->
                restoreSource = null
                onRestore(password, uri)
            },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.wipe_title)) },
            text = { Text(stringResource(R.string.wipe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    onWipe()
                }) { Text(stringResource(R.string.wipe_confirm), color = AmbitColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = AmbitColors.PanelRaised,
            titleContentColor = AmbitColors.TextPrimary,
            textContentColor = AmbitColors.TextSecondary,
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmTwice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = password.length >= MIN_PASSWORD && (!confirmTwice || password == again)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it },
                        label = { Text(stringResource(R.string.backup_password_again)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = again.isNotEmpty() && again != password,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = valid) { Text(stringResource(R.string.action_continue_short)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = AmbitColors.PanelRaised,
        titleContentColor = AmbitColors.TextPrimary,
        textContentColor = AmbitColors.TextSecondary,
    )
}

private const val MIN_PASSWORD = 8
