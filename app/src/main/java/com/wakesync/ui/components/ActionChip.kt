package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.1 (`wear-design-system` SKILL.md): icon + title + one-line
 * subtitle action chip for compact lists (Inicio, Historial, Buscar Destino). Container
 * [WakeSyncColors.BlueDeep] (`surfaceContainer`), radius 16dp, compact height — replaces
 * the ~110dp full-width cards previously used in HomeScreen.
 */
@Composable
fun ActionChip(
    icon: Painter,
    iconContentDescription: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = WakeSyncShapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep,
            contentColor = WakeSyncColors.CreamSoft
        ),
        border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WakeSyncSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = iconContentDescription,
                tint = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(WakeSyncSpacing.sm))
            Column {
                Text(text = title, style = WakeSyncTextStyles.Title, color = WakeSyncColors.CreamSoft)
                Text(
                    text = subtitle,
                    style = WakeSyncTextStyles.Body,
                    color = WakeSyncColors.TanMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Design system component 4.3: circular secondary icon button. The clickable target keeps
 * the mandatory 48dp minimum (section 3.4), but the visible filled circle is 36dp so a
 * compact action (e.g. Ajustes in Inicio) never reads as an oversized primary button — this
 * is the direct fix for the previous 48dp fully-filled settings button with an off-center
 * glyph.
 */
@Composable
fun SecondaryIconChip(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    if (isAmbient) {
                        Modifier.background(WakeSyncColors.PureBlack)
                    } else {
                        Modifier.background(WakeSyncColors.BlueDeep)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.CreamSoft,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
