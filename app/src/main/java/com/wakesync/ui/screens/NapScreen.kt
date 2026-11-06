package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.CompactNotice
import com.wakesync.ui.components.PrimaryBottomButton
import com.wakesync.ui.components.SecondaryIconChip
import com.wakesync.ui.components.SimulationBadge
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen 2: Nap Mode (MicroNap) — SRS 3.1 & 8.4, `wear-design-system` SKILL.md section 6.2.
 *
 * One primary datum per phase (`Display`, 28sp): Calibrando shows the countdown, Monitoreando
 * shows the rest-state label, Reposo Confirmado shows the remaining time. Heart rate / score /
 * distance are always secondary (`Label`). The sensor-unavailable notice (component 4.7) sits
 * in the normal `Column` flow — the pre-F24-fix version overlaid it in a `Box` with
 * `Alignment.TopCenter` on top of the arc, which was the actual bug, not just the visuals.
 *
 * The progress arc is a direct child of the outer full-size `Box`, centered on the round
 * bezel independently of the text below it — `bottomButtonReserve` applies only to the text
 * `Column` (matches `TransitScreen`'s pattern). Wrapping the arc in that same reserve used to
 * shift its center ~32dp up, making it non-concentric with the screen (regression fixed here).
 * The calibration ring no longer rotates: the progress sweep alone already shows advancement.
 *
 * F26: with Simulation Mode on, a secondary ⚡ chip (4.3) right of `[Detener]` calls [onSimulateNap]
 * during Calibrando / Monitoreando to release the simulated HR descent towards DEEP_REST. With
 * Simulation Mode off (and in ambient mode) `[Detener]` is drawn alone, centered.
 * [onRequestTransit] is currently not rendered on this screen.
 */
@Composable
fun NapScreen(
    state: WakeSyncState,
    onStopSession: () -> Unit,
    onSimulateNap: () -> Unit,
    onRequestTransit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val napState = state.napState
    val biometrics = state.biometricMetrics

    val isSensorUnavailable = biometrics.restState == RestState.SENSOR_UNAVAILABLE

    val showSimulate = state.isSimulated &&
        (napState.phase == NapPhase.CALIBRATING || napState.phase == NapPhase.MONITORING)
    val showRequestTransit = napState.phase == NapPhase.CALIBRATING ||
        napState.phase == NapPhase.MONITORING ||
        napState.phase == NapPhase.REST_CONFIRMED

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueBackground),
        contentAlignment = Alignment.Center
    ) {
        when (napState.phase) {
            NapPhase.CALIBRATING -> {
                // Estado 1: Calibrando Basal — dato principal = cuenta regresiva
                val calibrationProgress = (napState.elapsedSeconds / 20.0f).coerceIn(0.0f, 1.0f)

                if (!isAmbient) {
                    CircularProgressArc(
                        progress = calibrationProgress,
                        color = WakeSyncColors.RoseGoldPastel,
                        trackColor = WakeSyncColors.RoseGoldPastelTrack,
                        modifier = Modifier.size(WakeSyncSpacing.progressArcDiameter)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = WakeSyncSpacing.bottomButtonReserve)
                        .padding(horizontal = WakeSyncSpacing.xxl)
                ) {
                    if (state.isSimulated && !isAmbient) {
                        SimulationBadge(modifier = Modifier.padding(bottom = WakeSyncSpacing.xs))
                    }

                    Text(
                        text = stringResource(R.string.nap_calibrating_title),
                        style = WakeSyncTextStyles.Title,
                        color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    Text(
                        text = stringResource(
                            R.string.nap_calibrating_countdown_format,
                            20 - napState.elapsedSeconds
                        ),
                        style = WakeSyncTextStyles.Display,
                        color = WakeSyncColors.CreamSoft
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    val hr = biometrics.currentHeartRate ?: biometrics.baseHeartRate
                    Text(
                        text = if (hr != null) stringResource(R.string.nap_current_hr_format, hr) else "-- BPM",
                        style = WakeSyncTextStyles.Label,
                        color = WakeSyncColors.TanMuted
                    )

                    if (isSensorUnavailable && !isAmbient) {
                        Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                        CompactNotice(
                            icon = painterResource(R.drawable.ic_warning),
                            text = stringResource(R.string.sensor_unavailable_title)
                        )
                    }
                }
            }

            NapPhase.MONITORING -> {
                // Estado 2: Monitoreando — dato principal = estado de reposo
                val isAwaitingEvaluation = isAwaitingMonitoringEvaluation(biometrics.restState, biometrics.restScore)
                val score = if (isAwaitingEvaluation) 0.0f else biometrics.restScore ?: 0.0f
                if (!isAmbient) {
                    CircularProgressArc(
                        progress = score,
                        color = WakeSyncColors.RoseGoldPastel,
                        trackColor = WakeSyncColors.RoseGoldPastelTrack,
                        modifier = Modifier.size(WakeSyncSpacing.progressArcDiameter)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = WakeSyncSpacing.bottomButtonReserve)
                        .padding(horizontal = WakeSyncSpacing.xxl)
                ) {
                    if (state.isSimulated && !isAmbient) {
                        SimulationBadge(modifier = Modifier.padding(bottom = WakeSyncSpacing.xs))
                    }

                    // Phase label demoted to Label: the rest-state text below is the one
                    // primary datum of this phase (rule 1.1), so only one of the two can be
                    // Title-sized — otherwise "Reposo Ligero" stops reading as the headline.
                    Text(
                        text = stringResource(R.string.nap_monitoring_title),
                        style = WakeSyncTextStyles.Label,
                        color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    // Rest-state label: kept to one line (Title, not Display) so it never
                    // pushes Score/FC behind Detener/simular on longer labels ("Reposo Ligero").
                    Text(
                        text = when {
                            isAwaitingEvaluation -> stringResource(R.string.nap_rest_state_evaluating)
                            biometrics.restState == RestState.AWAKE -> stringResource(R.string.rest_state_awake)
                            biometrics.restState == RestState.LIGHT_REST -> stringResource(R.string.rest_state_light)
                            biometrics.restState == RestState.DEEP_REST -> stringResource(R.string.rest_state_deep)
                            else -> stringResource(R.string.rest_state_sensor_unavailable)
                        },
                        style = WakeSyncTextStyles.Title,
                        color = WakeSyncColors.CreamSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    Text(
                        text = if (isAwaitingEvaluation) {
                            stringResource(R.string.nap_rest_score_placeholder)
                        } else {
                            stringResource(R.string.nap_rest_score_format, score)
                        },
                        style = WakeSyncTextStyles.Label,
                        color = WakeSyncColors.RoseGold
                    )

                    val currentHr = biometrics.currentHeartRate
                    if (currentHr != null) {
                        Text(
                            text = stringResource(R.string.nap_current_hr_format, currentHr),
                            style = WakeSyncTextStyles.Label,
                            color = WakeSyncColors.TanMuted
                        )
                    }

                    // Optional distance if resting on transit
                    val dist = napState.distanceToDestinationMeters
                    if (dist != null) {
                        Text(
                            text = stringResource(R.string.nap_dest_distance_format, dist),
                            style = WakeSyncTextStyles.Label,
                            color = WakeSyncColors.RoseGold
                        )
                    }

                    if (isSensorUnavailable && !isAmbient) {
                        Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                        CompactNotice(
                            icon = painterResource(R.drawable.ic_warning),
                            text = stringResource(R.string.sensor_unavailable_title)
                        )
                    }
                }
            }

            NapPhase.REST_CONFIRMED -> {
                // Estado 3: Reposo Confirmado — dato principal = tiempo restante
                val remainingSec = napState.remainingNapSeconds
                val totalSec = 15 * 60f
                val progress = (remainingSec / totalSec).coerceIn(0.0f, 1.0f)

                if (!isAmbient) {
                    CircularProgressArc(
                        progress = progress,
                        color = WakeSyncColors.RoseGoldPastel,
                        trackColor = WakeSyncColors.RoseGoldPastelTrack,
                        modifier = Modifier.size(WakeSyncSpacing.progressArcDiameter)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = WakeSyncSpacing.bottomButtonReserve)
                        .padding(horizontal = WakeSyncSpacing.xxl)
                ) {
                    if (state.isSimulated && !isAmbient) {
                        SimulationBadge(modifier = Modifier.padding(bottom = WakeSyncSpacing.xs))
                    }

                    Text(
                        text = stringResource(R.string.nap_deep_rest_title),
                        style = WakeSyncTextStyles.Title,
                        color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.RoseGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    val minutes = remainingSec / 60
                    val seconds = remainingSec % 60
                    Text(
                        text = stringResource(R.string.nap_remaining_time_format, minutes, seconds),
                        style = WakeSyncTextStyles.Display,
                        color = WakeSyncColors.CreamSoft
                    )
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                    val currentHr = biometrics.currentHeartRate
                    if (currentHr != null) {
                        Text(
                            text = stringResource(R.string.nap_current_hr_format, currentHr),
                            style = WakeSyncTextStyles.Label,
                            color = WakeSyncColors.TanMuted
                        )
                    }
                }
            }

            else -> {
                // Completed, Cancelled or Idle fallback
                Text(
                    text = stringResource(R.string.title_nap),
                    style = WakeSyncTextStyles.Title,
                    color = WakeSyncColors.CreamSoft
                )
            }
        }

        if (!isAmbient) {
            if (showSimulate) {
                // Detener keeps the exact spot it has with Simulation Mode off (bottom-centered);
                // the secondary ⚡ chip hangs off its right edge, vertically centered on it.
                CenteredWithTrailing(
                    gap = SIMULATE_CHIP_GAP,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WakeSyncSpacing.primaryButtonBottomPadding)
                ) {
                    PrimaryBottomButton(
                        text = stringResource(R.string.btn_stop),
                        onClick = onStopSession
                    )
                    SecondaryIconChip(
                        icon = painterResource(R.drawable.ic_fast_forward),
                        contentDescription = stringResource(R.string.btn_simulate_nap),
                        onClick = onSimulateNap
                    )
                }
            } else {
                PrimaryBottomButton(
                    text = stringResource(R.string.btn_stop),
                    onClick = onStopSession,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WakeSyncSpacing.primaryButtonBottomPadding)
                )
            }
        }
    }
}

/**
 * Layout gap between `[Detener]`'s right edge and the ⚡ chip's 48dp touch box. Zero on purpose:
 * the chip's visible 36dp circle is inset 6dp inside that box, so the visible gap is still 6dp.
 * With `[Detener]` (~88dp wide) centered and its center 50dp above the bottom of the 227dp
 * round screen, the circle's outer edge sits at ~111.0dp from the screen center (radius 113.5dp);
 * a `WakeSyncSpacing.sm` (8dp) layout gap would push it to ~117dp and clip it against the bezel.
 */
private val SIMULATE_CHIP_GAP: Dp = 0.dp

/**
 * Places [content]'s first child (the primary button) so that it stays horizontally centered in
 * this layout, and its second child (a trailing chip) [gap] to the right of the primary's right
 * edge, vertically centered on the primary. The layout reserves an invisible slot of the same
 * width on the left, so centering this layout centers the primary exactly as if it were alone.
 * Its height is the primary's height, keeping the primary's bottom edge where the caller anchors
 * it; a taller chip overflows evenly above and below (it is neither clipped nor re-positioned).
 */
@Composable
private fun CenteredWithTrailing(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val primary = measurables[0].measure(looseConstraints)
        val trailing = measurables[1].measure(looseConstraints)
        val sideSlot = gap.roundToPx() + trailing.width
        layout(width = primary.width + 2 * sideSlot, height = primary.height) {
            primary.placeRelative(x = sideSlot, y = 0)
            trailing.placeRelative(
                x = sideSlot + primary.width + gap.roundToPx(),
                y = (primary.height - trailing.height) / 2
            )
        }
    }
}

/**
 * True while a Nap in MONITORING has no rest evaluation of its own yet. The relay in
 * `AppSessionCoordinator` only resolves `RestState.CALIBRATING` while the nap is still calibrating
 * (and `UNKNOWN` is the pre-session default), so either value seen during MONITORING is a leftover
 * from before the first post-calibration evaluation (up to ~10 s). Its score is the engine's
 * placeholder 0.0, not a measurement, so the screen shows a neutral placeholder instead. A real
 * evaluation always carries AWAKE / LIGHT_REST / DEEP_REST (or SENSOR_UNAVAILABLE), so a genuinely
 * evaluated 0.00 score is still displayed as such.
 */
internal fun isAwaitingMonitoringEvaluation(restState: RestState, restScore: Float?): Boolean =
    restScore == null || restState == RestState.CALIBRATING || restState == RestState.UNKNOWN
