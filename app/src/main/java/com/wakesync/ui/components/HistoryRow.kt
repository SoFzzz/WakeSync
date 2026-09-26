package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.SessionRecord
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.9 (`wear-design-system` SKILL.md): one Historial row. Session-type
 * icon on the left; three lines: `Body` "Siesta · 26/09" (type + date), `Label` "21:57 · 18:32"
 * (start time + duration) and `Label` "Completada" (outcome) — the text column is only ~143dp wide
 * on a 227dp round screen, so time + duration + outcome never fit on one line. A SageTeal dot on the
 * right marks a record that already has a saved insight. Radius 12dp (`extraSmall`), min height
 * 48dp, whole row clickable.
 */
@Composable
fun HistoryRow(
    record: SessionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val typeLabel = stringResource(sessionTypeStringRes(record.sessionType))
    val outcomeLabel = stringResource(sessionOutcomeStringRes(record.outcome))
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WakeSyncSpacing.minTouchTarget),
        shape = WakeSyncShapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep,
            contentColor = WakeSyncColors.CreamSoft
        ),
        border = if (isAmbient) CardDefaults.outlinedCardBorder() else null,
        contentPadding = PaddingValues(
            horizontal = WakeSyncSpacing.md,
            vertical = WakeSyncSpacing.sm
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(sessionTypeIconRes(record.sessionType)),
                contentDescription = null,
                tint = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
                modifier = Modifier.size(WakeSyncSpacing.iconSmall)
            )
            Spacer(modifier = Modifier.width(WakeSyncSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$typeLabel · ${UiFormatters.formatHistoryDate(record.startTimestamp)}",
                    style = WakeSyncTextStyles.Body,
                    color = WakeSyncColors.CreamSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val startTime = UiFormatters.formatHistoryTime(record.startTimestamp)
                val duration = UiFormatters.formatSessionDuration(record.durationSeconds)
                Text(
                    text = "$startTime · $duration",
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.TanMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = outcomeLabel,
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.TanMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!record.insightText.isNullOrBlank()) {
                val hasInsightDescription = stringResource(R.string.cd_has_insight)
                Spacer(modifier = Modifier.width(WakeSyncSpacing.sm))
                Box(
                    modifier = Modifier
                        .size(WakeSyncSpacing.statusDot)
                        .clip(CircleShape)
                        .background(WakeSyncColors.SageTeal)
                        .semantics { contentDescription = hasInsightDescription }
                )
            }
        }
    }
}
