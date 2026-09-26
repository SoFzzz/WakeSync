@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.google.android.horologist.compose.layout.ResponsiveTimeText
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.PrimaryBottomButton
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Resumen Post-Sesión con Insight (CR-01, RF-INS-02/03/04) — `wear-design-system` SKILL.md
 * section 6.6.
 *
 * One primary datum (`Display`): session duration. Session type + outcome ("Siesta ·
 * Completada") is the `Label` kicker above it, replacing the previous separate "Resumen de
 * Sesión" heading — `ResponsiveTimeText` (with the same opaque-band pattern as `HomeScreen`)
 * already gives the screen its page context, so a redundant heading wasn't adding
 * information. `[Volver a Inicio]` is the design system's primary bottom button (component
 * 4.2) instead of a plain circular text button; the insight card (component 4.4) is styled
 * with the shared shape/color/typography tokens instead of a one-off `RoundedCornerShape`.
 *
 * The caller (WakeSyncNavHost) is responsible for calling
 * [com.wakesync.core.insights.InsightContract.requestInsight] exactly once for [record],
 * and only after confirming [record] is already visible in
 * [com.wakesync.core.data.SessionHistoryRepository.sessionHistory] — requesting it as soon as
 * sessionType flips to NONE loses a race against the async DataStore write performed by
 * SessionManager.endSession(), which would otherwise show [InsightState.Unavailable] almost
 * always. [InsightState.Idle] is treated the same as [InsightState.Loading] here because the
 * underlying [com.wakesync.core.insights.InsightProvider] instance is a long-lived singleton
 * that may still be Idle right when this screen enters composition.
 *
 * The content scrolls (touch and rotary) so a long insight can never push [Volver a Inicio]
 * off the round screen — this is also what keeps `ResponsiveTimeText` (fixed overlay, top of
 * screen) from ever colliding with the card below it: the column's top `contentPadding`
 * (`WakeSyncSpacing.safeInsetVertical`, the same constant `HomeScreen` reserves for it) scrolls
 * the card's start position below the time text instead of under it.
 */
@Composable
fun SessionSummaryScreen(
    record: SessionRecord,
    insightState: InsightState,
    onRetryInsight: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()

    // The back gesture must dismiss the summary the same way [Volver a Inicio] does (F10):
    // otherwise it re-opens on the next Activity recreation with no way out.
    BackHandler(onBack = onBackToHome)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
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
                val sessionLabel = if (record.sessionType == SessionType.NAP) {
                    stringResource(R.string.title_nap)
                } else {
                    stringResource(R.string.title_transit)
                }
                val outcomeLabel = stringResource(sessionOutcomeStringRes(record.outcome))
                Text(
                    text = "$sessionLabel · $outcomeLabel",
                    style = WakeSyncTextStyles.Label,
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.SageTeal,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                Text(
                    text = UiFormatters.formatSessionDuration(record.durationSeconds),
                    style = WakeSyncTextStyles.Display,
                    color = WakeSyncColors.CreamSoft
                )
            }

            if (!isAmbient) {
                item {
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.md))
                    InsightCard(insightState = insightState, onRetryInsight = onRetryInsight)
                }

                item {
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.lg))
                    PrimaryBottomButton(
                        text = stringResource(R.string.btn_back_to_home),
                        onClick = onBackToHome
                    )
                }
            }
        }

        if (!isAmbient) {
            // Opaque band behind TimeText: same pattern as HomeScreen — without it, scrolled
            // content bleeds through the otherwise-transparent time overlay.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WakeSyncSpacing.xxxl)
                    .align(Alignment.TopCenter)
                    .background(WakeSyncColors.PureBlack)
            )
            ResponsiveTimeText()
        }
    }
}

@Composable
private fun InsightCard(insightState: InsightState, onRetryInsight: () -> Unit) {
    // Offline state gets a SlateMist border on the card — the only place that token touches
    // this component now (see the Unavailable branch below for why not the text/button too).
    val isUnavailable = insightState is InsightState.Unavailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WakeSyncShapes.small)
            .then(
                if (isUnavailable) {
                    Modifier.border(1.dp, WakeSyncColors.SlateMist, WakeSyncShapes.small)
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
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
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

            InsightState.Unavailable -> {
                // SlateMist (offline, section 2.5), not WarningOchre (error): the string
                // always says "sin conexión", so this is the offline case, not a request
                // failure. SlateMist measures only ~3.67:1 on this card's BlueDeep fill —
                // under the 4.5:1 floor for Label-sized text — so it's confined to the card
                // border (added on the Column above) instead of the text: the message and
                // the retry button both stay on CreamSoft/onSurface (8.58:1), same as the
                // Ready branch's text right above this one.
                Text(
                    text = stringResource(R.string.insight_unavailable),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.CreamSoft,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                Button(
                    onClick = onRetryInsight,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.SlateMistMuted,
                        contentColor = WakeSyncColors.CreamSoft
                    )
                ) {
                    Text(text = stringResource(R.string.btn_retry), style = WakeSyncTextStyles.Label)
                }
            }
        }
    }
}

private fun sessionOutcomeStringRes(outcome: SessionOutcome): Int = when (outcome) {
    SessionOutcome.COMPLETED -> R.string.session_outcome_completed
    SessionOutcome.INTERRUPTED_BY_ARRIVAL -> R.string.session_outcome_completed
    SessionOutcome.TIMED_OUT -> R.string.session_outcome_timed_out
    SessionOutcome.CANCELLED -> R.string.session_outcome_cancelled
}
