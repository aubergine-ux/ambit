package io.aubergine.ambit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.aubergine.ambit.model.DeviceCategory

/**
 * A single dark scheme, on purpose.
 *
 * The app is used on a dashboard mount and at night, so there is no light theme to switch
 * to — a white screen in a dark car is unusable and would be a worse default than any
 * amount of system consistency. Everything is monospaced because the content is addresses,
 * frequencies and signal levels, and those read wrong in a proportional face.
 */

object CivColors {
    val Background = Color(0xFF05080C)
    val Panel = Color(0xFF080E15)
    val PanelRaised = Color(0xFF0C141D)

    val Accent = Color(0xFF33D6E8)
    val AccentDim = Color(0xFF1B7A87)
    val AccentGlow = Color(0x3333D6E8)

    val TextPrimary = Color(0xFFDCEAEE)
    val TextSecondary = Color(0xFF8AA3AC)
    val TextDim = Color(0xFF54686F)

    val Ssid = Color(0xFFFF3D9A)
    val Ok = Color(0xFF5BE86B)
    val Warn = Color(0xFFFFB020)
    val Danger = Color(0xFFFF4D4D)
    val Info = Color(0xFF6FA8FF)

    val Divider = Color(0xFF16303A)
}

/** Each category owns one colour, used for its border, its radar dot, and its legend chip. */
fun DeviceCategory.color(): Color = when (this) {
    DeviceCategory.PRIORITY -> CivColors.Danger
    DeviceCategory.FLEET -> CivColors.Warn
    DeviceCategory.INFRASTRUCTURE -> CivColors.Info
    DeviceCategory.COMMERCIAL -> CivColors.Accent
    DeviceCategory.RESIDENTIAL -> Color(0xFF4E7A86)
    DeviceCategory.RANDOMIZED -> Color(0xFF5E5470)
    DeviceCategory.UNKNOWN -> CivColors.TextDim
}

private val Scheme = darkColorScheme(
    primary = CivColors.Accent,
    onPrimary = CivColors.Background,
    secondary = CivColors.AccentDim,
    background = CivColors.Background,
    onBackground = CivColors.TextPrimary,
    surface = CivColors.Panel,
    onSurface = CivColors.TextPrimary,
    surfaceVariant = CivColors.PanelRaised,
    onSurfaceVariant = CivColors.TextSecondary,
    error = CivColors.Danger,
    outline = CivColors.Divider,
)

private val Mono = FontFamily.Monospace

private val CivTypography = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp),
)

val LocalMono = staticCompositionLocalOf { Mono }

@Composable
fun AmbitTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMono provides Mono) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = CivTypography,
            content = content,
        )
    }
}
