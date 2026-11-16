package io.aubergine.ambit.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aubergine.ambit.R
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.theme.AmbitColors
import kotlinx.coroutines.launch

/**
 * PIN entry. Deliberately bland: no app name or logo, so a glance at the screen does not
 * say what is behind it.
 */
@Composable
fun LockScreen(
    onPin: suspend (String) -> Boolean,
    biometricAvailable: Boolean,
    onBiometric: () -> Unit,
    onQuickExit: (() -> Unit)?,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pin.length < 4 || checking) return
        checking = true
        scope.launch {
            val ok = onPin(pin)
            wrong = !ok
            pin = ""
            checking = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AmbitColors.Background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge, color = AmbitColors.TextPrimary)
        Text(
            text = "•".repeat(pin.length).ifEmpty { " " },
            fontSize = 32.sp,
            color = AmbitColors.Accent,
            modifier = Modifier.padding(vertical = 16.dp).semantics {
                contentDescription = pin.length.toString()
            },
        )
        if (wrong) Text(stringResource(R.string.lock_wrong), color = AmbitColors.Warn, style = MaterialTheme.typography.bodyMedium)

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    val description = when (key) {
                        "⌫" -> stringResource(R.string.lock_delete)
                        "✓" -> stringResource(R.string.lock_enter)
                        else -> key
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .background(AmbitColors.PanelRaised, CircleShape)
                            .clickable(role = Role.Button) {
                                wrong = false
                                when (key) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "✓" -> submit()
                                    else -> if (pin.length < 12) pin += key
                                }
                            }
                            .semantics { contentDescription = description },
                    ) {
                        Text(key, fontSize = 24.sp, color = AmbitColors.TextPrimary)
                    }
                }
            }
        }
        if (biometricAvailable) {
            AmbitOutlineButton(
                label = stringResource(R.string.lock_biometric),
                onClick = onBiometric,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        onQuickExit?.let {
            AmbitOutlineButton(
                label = stringResource(R.string.action_quick_exit),
                onClick = it,
                color = AmbitColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
