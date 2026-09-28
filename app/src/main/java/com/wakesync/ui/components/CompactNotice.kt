package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.7 (`wear-design-system` SKILL.md): compact status notice — a
 * single row (icon 16dp + `Label` text), `surfaceContainer` background, radius 12dp,
 * ~32dp tall. Replaces ad-hoc overlay banners (e.g. the old `SensorUnavailableBanner`,
 * which sat in a `Box` with `Alignment.TopCenter` on top of the calibration arc — F24's
 * real bug was that overlay, not just its look). This component is meant to sit **in the
 * normal flow** of a `Column`, never layered with `Box` + top alignment over other content.
 */
@Composable
fun CompactNotice(
    icon: Painter,
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = WakeSyncColors.WarningOchre
) {
    Row(
        modifier = modifier
            .clip(WakeSyncShapes.extraSmall)
            .background(WakeSyncColors.BlueDeep)
            .padding(horizontal = WakeSyncSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(WakeSyncSpacing.xs))
        Text(text = text, style = WakeSyncTextStyles.Label, color = accentColor)
    }
}
