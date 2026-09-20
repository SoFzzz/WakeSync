package com.wakesync.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Material 3 Theme for WakeSync on Wear OS.
 * Configured with dark/black background suitable for circular OLED displays.
 */
private val WearColorScheme = ColorScheme(
    primary = WakeSyncColors.White,
    primaryContainer = WakeSyncColors.CarbonSurface,
    onPrimary = WakeSyncColors.Carbon,
    onPrimaryContainer = WakeSyncColors.White,
    secondary = WakeSyncColors.CyanBasal,
    secondaryContainer = WakeSyncColors.CarbonSurface,
    onSecondary = WakeSyncColors.Carbon,
    onSecondaryContainer = WakeSyncColors.CyanBasal,
    tertiary = WakeSyncColors.IndigoDeepRest,
    tertiaryContainer = WakeSyncColors.CarbonSurface,
    onTertiary = WakeSyncColors.White,
    onTertiaryContainer = WakeSyncColors.IndigoDeepRest,
    background = WakeSyncColors.PureBlack,
    onBackground = WakeSyncColors.White,
    surface = WakeSyncColors.Carbon,
    onSurface = WakeSyncColors.White,
    onSurfaceVariant = WakeSyncColors.TextMuted,
    error = WakeSyncColors.CoralAlert,
    onError = WakeSyncColors.White
)

@Composable
fun WakeSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content
    )
}
