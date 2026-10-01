package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Resumen Post-Sesión con Insight (CR-01, RF-INS-02/03/04).
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.title_session_summary),
                color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            val sessionLabel = if (record.sessionType == SessionType.NAP) {
                stringResource(R.string.title_nap)
            } else {
                stringResource(R.string.title_transit)
            }
            val outcomeLabel = stringResource(sessionOutcomeStringRes(record.outcome))
            Text(
                text = "$sessionLabel • $outcomeLabel",
                color = WakeSyncColors.TextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = UiFormatters.formatSessionDuration(record.durationSeconds),
                color = WakeSyncColors.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isAmbient) {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WakeSyncColors.CarbonSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (insightState) {
                            InsightState.Idle, InsightState.Loading -> {
                                CircularProgressArc(
                                    progress = 0.35f,
                                    color = WakeSyncColors.White,
                                    trackColor = WakeSyncColors.Carbon,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.insight_loading),
                                    color = WakeSyncColors.TextMuted,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            is InsightState.Ready -> {
                                Text(
                                    text = insightState.text,
                                    color = WakeSyncColors.White,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            InsightState.Unavailable -> {
                                Text(
                                    text = stringResource(R.string.insight_unavailable),
                                    color = WakeSyncColors.OrangeWarning,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onRetryInsight,
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WakeSyncColors.OrangeMuted,
                                        contentColor = WakeSyncColors.OrangeWarning
                                    )
                                ) {
                                    Text(text = stringResource(R.string.btn_retry), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onBackToHome,
                modifier = Modifier.sizeIn(minWidth = 54.dp, minHeight = 48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeSyncColors.CarbonSurface,
                    contentColor = WakeSyncColors.White
                )
            ) {
                Text(text = stringResource(R.string.btn_back_to_home), fontSize = 10.sp)
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
