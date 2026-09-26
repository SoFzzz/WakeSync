@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.google.android.horologist.compose.layout.ResponsiveTimeText
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionRecord
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.InsightCard
import com.wakesync.ui.components.NoInsightCard
import com.wakesync.ui.components.PrimaryBottomButton
import com.wakesync.ui.components.sessionOutcomeStringRes
import com.wakesync.ui.components.sessionTypeStringRes
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen: Detalle de Historial (RF-INS-03, `wear-design-system` SKILL.md section 5.3). Same
 * layout as Resumen Post-Sesión (6.6) for an already persisted [record]: `Label` kicker
 * "Siesta · Completada", `Display` duration, `Label` start date/time, then the insight card.
 *
 * [insightState] comes from [UiFormatters.resolveDetailInsightState]: a saved
 * [SessionRecord.insightText] is shown directly (no backend call); null means no insight exists
 * and none was requested from here, so the `NoInsight` card and `[Generar Insight]` (one call)
 * are shown instead. Back is the edge swipe or the physical button — no on-screen back button.
 */
@Composable
fun SessionDetailScreen(
    record: SessionRecord,
    insightState: InsightState?,
    onGenerateInsight: () -> Unit,
    onRetryInsight: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()
    val screenBackground = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                top = WakeSyncSpacing.safeInsetVertical,
                bottom = WakeSyncSpacing.safeInsetVertical,
                start = WakeSyncSpacing.safeInsetHorizontal,
                end = WakeSyncSpacing.safeInsetHorizontal
            )
        ) {
            item {
                val typeLabel = stringResource(sessionTypeStringRes(record.sessionType))
                val outcomeLabel = stringResource(sessionOutcomeStringRes(record.outcome))
                Text(
                    text = "$typeLabel · $outcomeLabel",
                    style = WakeSyncTextStyles.Label,
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.SageTeal,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = UiFormatters.formatSessionDuration(record.durationSeconds),
                    style = WakeSyncTextStyles.Display,
                    color = WakeSyncColors.CreamSoft
                )
            }

            item {
                Text(
                    text = UiFormatters.formatHistoryDateTime(record.startTimestamp),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.TanMuted
                )
            }

            if (!isAmbient) {
                item {
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.md))
                    if (insightState == null) {
                        NoInsightCard()
                    } else {
                        InsightCard(insightState = insightState, onRetryInsight = onRetryInsight)
                    }
                }

                if (insightState == null) {
                    item {
                        Spacer(modifier = Modifier.height(WakeSyncSpacing.lg))
                        PrimaryBottomButton(
                            text = stringResource(R.string.btn_generate_insight),
                            onClick = onGenerateInsight
                        )
                    }
                }
            }
        }

        if (!isAmbient) {
            // Opaque band behind TimeText (same pattern as SessionSummaryScreen).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WakeSyncSpacing.xxxl)
                    .align(Alignment.TopCenter)
                    .background(screenBackground)
            )
            ResponsiveTimeText()
        }
    }
}
