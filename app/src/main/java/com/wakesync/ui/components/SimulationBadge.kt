package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.6 (`wear-design-system` SKILL.md): small pill badge shown
 * whenever `state.isSimulated` is true, so it is always clear the active session is not
 * using real sensors. Pill radius (50% of height), `outline` (BronzeMuted) fill at 20%
 * alpha + 1dp border, `Label` text in `onSurfaceVariant`, uppercase.
 */
@Composable
fun SimulationBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(WakeSyncColors.BronzeMuted.copy(alpha = 0.20f))
            .border(1.dp, WakeSyncColors.BronzeMuted, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = stringResource(R.string.badge_simulation).uppercase(),
            style = WakeSyncTextStyles.Label,
            color = WakeSyncColors.TanMuted
        )
    }
}
