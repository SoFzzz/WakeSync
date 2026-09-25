package com.wakesync.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Material 3 Theme for WakeSync on Wear OS — "reloj clásico de lujo, calmado"
 * (`wear-design-system` SKILL.md section 2.2). Configured with a dark navy background
 * suitable for circular OLED displays.
 */
private val WearColorScheme = ColorScheme(
    primary = WakeSyncColors.RoseGold,
    primaryContainer = WakeSyncColors.BlueDeep,
    onPrimary = WakeSyncColors.NavyDeep,
    onPrimaryContainer = WakeSyncColors.RoseGold,
    secondary = WakeSyncColors.SteelBlue,
    secondaryContainer = WakeSyncColors.BlueDeep,
    onSecondary = WakeSyncColors.NavyDeep,
    onSecondaryContainer = WakeSyncColors.SteelBlue,
    tertiary = WakeSyncColors.PlumLavender,
    tertiaryContainer = WakeSyncColors.BlueDeep,
    onTertiary = WakeSyncColors.NavyDeep,
    onTertiaryContainer = WakeSyncColors.PlumLavender,
    background = WakeSyncColors.NavyDeep,
    onBackground = WakeSyncColors.CreamSoft,
    surface = WakeSyncColors.NavyDeep,
    onSurface = WakeSyncColors.CreamSoft,
    onSurfaceVariant = WakeSyncColors.TanMuted,
    error = WakeSyncColors.EmberRose,
    onError = WakeSyncColors.NavyDeep
)

@Composable
fun WakeSyncTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        shapes = WakeSyncShapes,
        content = content
    )
}
