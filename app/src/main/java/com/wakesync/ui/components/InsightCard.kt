package com.wakesync.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.insights.InsightFailureReason
import com.wakesync.core.insights.InsightState
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Design system component 4.4 (`wear-design-system` SKILL.md): the insight card shared by
 * Resumen Post-Sesión and Detalle de Historial. Renders `Idle`/`Loading`, `Ready` and
 * `Unavailable(reason)`; the `NoInsight` variant ([NoInsightCard]) is a separate composable
 * because it owns the `[Generar Insight]` action instead of `[Reintentar]`.
 */
@Composable
fun InsightCard(
    insightState: InsightState,
    onRetryInsight: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only the offline case gets the SlateMist (section 2.5 "offline") border on the card; a
    // request that reached the backend and failed is not an offline condition.
    val isOffline = insightState is InsightState.Unavailable &&
        insightState.reason == InsightFailureReason.OFFLINE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(WakeSyncShapes.small)
            .then(
                if (isOffline) {
                    Modifier.border(WakeSyncSpacing.borderHairline, WakeSyncColors.SlateMist, WakeSyncShapes.small)
                } else {
                    Modifier
                }
            )
            .background(WakeSyncColors.BlueDeep)
            .padding(WakeSyncSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (insightState) {
            InsightState.Idle, InsightState.Loading -> {
                CircularProgressArc(
                    progress = 0.35f,
                    color = WakeSyncColors.CreamSoft,
                    trackColor = WakeSyncColors.NavyDeep,
                    modifier = Modifier.size(WakeSyncSpacing.progressSmall),
                    strokeWidth = WakeSyncSpacing.progressSmallStroke
                )
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                Text(
                    text = stringResource(R.string.insight_loading),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center
                )
            }

            is InsightState.Ready -> {
                Text(
                    text = insightState.text,
                    style = WakeSyncTextStyles.Body,
                    color = WakeSyncColors.CreamSoft,
                    textAlign = TextAlign.Center
                )
            }

            is InsightState.Unavailable -> {
                // SlateMist measures only ~3.67:1 on this card's BlueDeep fill — under the 4.5:1
                // floor for Label-sized text — so it's confined to borders: the message and the
                // retry label both stay on CreamSoft/onSurface (8.58:1), same as the Ready branch.
                val messageRes = when (insightState.reason) {
                    InsightFailureReason.OFFLINE -> R.string.insight_offline
                    InsightFailureReason.FAILED -> R.string.insight_failed
                }
                Text(
                    text = stringResource(messageRes),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.CreamSoft,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                // Same fill as the card, so the SlateMist border (3.67:1 against BlueDeep, above
                // the 3:1 non-text floor) is what outlines the button — a SlateMistMuted fill was
                // only 1.21:1 against the card and the button didn't read as a button.
                Button(
                    onClick = onRetryInsight,
                    modifier = Modifier.sizeIn(
                        minWidth = WakeSyncSpacing.minTouchTarget,
                        minHeight = WakeSyncSpacing.minTouchTarget
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.BlueDeep,
                        contentColor = WakeSyncColors.CreamSoft
                    ),
                    border = BorderStroke(WakeSyncSpacing.borderHairline, WakeSyncColors.SlateMist)
                ) {
                    Text(text = stringResource(R.string.btn_retry), style = WakeSyncTextStyles.Label)
                }
            }
        }
    }
}

/**
 * `NoInsight` variant of component 4.4 (RF-INS-03): a persisted session with no insight yet.
 * The `[Generar Insight]` action is the caller's primary bottom button (4.2), not part of this
 * card, so the card itself stays a plain label.
 */
@Composable
fun NoInsightCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(WakeSyncShapes.small)
            .background(WakeSyncColors.BlueDeep)
            .padding(WakeSyncSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.insight_none),
            style = WakeSyncTextStyles.Label,
            color = WakeSyncColors.TanMuted,
            textAlign = TextAlign.Center
        )
    }
}
