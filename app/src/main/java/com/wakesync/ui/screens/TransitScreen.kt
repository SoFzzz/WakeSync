package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.RestState
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.PrimaryBottomButton
import com.wakesync.ui.components.SecondaryIconChip
import com.wakesync.ui.components.SimulationBadge
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen 3: Transit Mode (TransitNudge) — SRS 3.1 & 8.5, `wear-design-system` SKILL.md
 * section 6.3.
 *
 * One primary datum (`Display`): remaining straight-line distance. Destination name is
 * `Title`, capped at 2 lines with ellipsis (never a mid-word cut like the pre-Etapa-5
 * `maxLines = 1` silently truncating "Universidad Cooperativa de" without indicating it).
 * Speed/radius and the rest-state modulation indicator are `Label`.
 *
 * Same layout pattern as the fixed `NapScreen` (F28 in the skill): the progress arc is a
 * direct child of the outer full-size `Box`, centered on the round bezel independently of
 * the text below it — `bottomButtonReserve` applies only to the text `Column`. `Detener`
 * is the design system's primary bottom-anchored button (component 4.2); the bus emoji is
 * replaced by `ic_route` as a secondary icon chip (component 4.3), shown only in Modo
 * Simulación, next to the new `SIMULACIÓN` badge (component 4.6).
 */
@Composable
fun TransitScreen(
    state: WakeSyncState,
    onStopSession: () -> Unit,
    onSimulateRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val transitState = state.transitState
    val biometrics = state.biometricMetrics

    val destName = transitState.destination?.name ?: stringResource(R.string.transit_tracking_title)
    val distance = transitState.currentDistanceMeters
    val speed = transitState.estimatedSpeedMps
    val radius = transitState.dynamicAlertRadiusMeters
    val isDeepRest = (biometrics.restState == RestState.DEEP_REST)

    // Progress arc: Ratio of distance remaining towards dynamic alert radius
    // When distance <= radius, progress = 1.0 (alert triggered)
    val maxTrackDistance = 2000.0f
    val progress = if (distance != null) {
        (1.0f - ((distance - radius) / (maxTrackDistance - radius))).coerceIn(0.05f, 1.0f)
    } else {
        0.05f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        if (!isAmbient) {
            CircularProgressArc(
                progress = progress,
                color = WakeSyncColors.SageTeal,
                trackColor = WakeSyncColors.SageTealMuted,
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

            // Destination name: up to 2 lines with ellipsis, never a mid-word cut — no
            // marquee (principle 1.3: constant motion breaks the "calm luxury watch" feel).
            Text(
                text = destName,
                style = WakeSyncTextStyles.Title,
                color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.SageTeal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

            // Primary datum: remaining straight-line distance.
            val distanceText = if (distance != null) {
                stringResource(R.string.transit_distance_format, distance)
            } else {
                "-- m"
            }
            Text(
                text = distanceText,
                style = WakeSyncTextStyles.Display,
                color = WakeSyncColors.CreamSoft
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

            val speedText = if (speed != null) {
                stringResource(R.string.transit_speed_format, speed)
            } else {
                "-- m/s"
            }
            Text(
                text = "$speedText • R: ${radius.toInt()}m",
                style = WakeSyncTextStyles.Label,
                color = WakeSyncColors.TanMuted,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isDeepRest) stringResource(R.string.transit_state_deep_rest) else stringResource(R.string.transit_state_awake),
                style = WakeSyncTextStyles.Label,
                color = if (isDeepRest) WakeSyncColors.PlumLavender else WakeSyncColors.SageTeal,
                textAlign = TextAlign.Center
            )
        }

        if (!isAmbient) {
            if (state.isSimulated) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WakeSyncSpacing.primaryButtonBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(WakeSyncSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryBottomButton(
                        text = stringResource(R.string.btn_stop),
                        onClick = onStopSession
                    )
                    SecondaryIconChip(
                        icon = painterResource(R.drawable.ic_route),
                        contentDescription = stringResource(R.string.btn_simulate_route),
                        onClick = onSimulateRoute
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
