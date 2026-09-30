package com.wakesync.ui.ambient

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing whether the display is currently in low-power ambient (Always-On Display) mode.
 * Consumed across all 6 screens/overlays to render muted variants and disable expensive animations.
 */
val LocalAmbientMode = compositionLocalOf { false }
