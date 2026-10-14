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
import com.wakesync.ui.components.PrimaryBottomButton
import com.wakesync.ui.components.sessionOutcomeStringRes
import com.wakesync.ui.components.sessionTypeStringRes
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
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
    val screenBackground = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueBackground

    // The back gesture must dismiss the summary the same way [Volver a Inicio] does (F10):
    // otherwise it re-opens on the next Activity recreation with no way out.
    BackHandler(onBack = onBackToHome)

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
                val sessionLabel = stringResource(sessionTypeStringRes(record.sessionType))
                val outcomeLabel = stringResource(sessionOutcomeStringRes(record.outcome))
                Text(
                    text = "$sessionLabel · $outcomeLabel",
                    style = WakeSyncTextStyles.Label,
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
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
                    .background(screenBackground)
            )
            ResponsiveTimeText()
        }
    }
}
