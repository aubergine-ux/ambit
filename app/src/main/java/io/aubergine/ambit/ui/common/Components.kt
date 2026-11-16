package io.aubergine.ambit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.ui.theme.AmbitColors

/**
 * The panel is the app's one structural idea: a thin bordered box with a label sitting on
 * its top edge. Every section of every screen is one of these, which is what makes a long
 * scroll of dense readouts stay navigable.
 */
@Composable
fun AmbitPanel(
    title: String,
    modifier: Modifier = Modifier,
    borderColor: Color = AmbitColors.AccentDim,
    titleColor: Color = AmbitColors.Accent,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .background(AmbitColors.Panel, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** A full-width action, drawn as a filled bar. The primary control idiom of the app. */
@Composable
fun AmbitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AmbitColors.Accent,
) {
    val background = if (enabled) color else AmbitColors.Divider
    val foreground = if (enabled) AmbitColors.Background else AmbitColors.TextDim
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(background, RoundedCornerShape(2.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            textAlign = TextAlign.Center,
        )
    }
}

/** Outlined variant, for secondary or destructive actions. */
@Composable
fun AmbitOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AmbitColors.Accent,
) {
    val tint = if (enabled) color else AmbitColors.TextDim
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(1.dp, tint, RoundedCornerShape(2.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** `Name: value` in the app's house style, with the label dim and the value bright. */
@Composable
fun FieldLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AmbitColors.TextPrimary,
    labelColor: Color = AmbitColors.TextSecondary,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier, color: Color = AmbitColors.TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Small uppercase pill used for categories, severities and radio types. */
@Composable
fun AmbitChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Horizontal 0..4 bar meter for RSSI, so strength reads at a glance from a mount. */
@Composable
fun SignalBars(bars: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((5 + index * 3).dp)
                    .background(if (index < bars) color else AmbitColors.Divider),
            )
        }
    }
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, color: Color = AmbitColors.Accent) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AmbitColors.TextDim,
        modifier = modifier.padding(vertical = 6.dp),
    )
}
