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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.RestState
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Screen 3: Transit Mode (TransitNudge) — SRS 3.1 & 8.5.
 *
 * Displays:
 * - "En Ruta" accent in Green (#00E676).
 * - Straight-line remaining distance (m) + estimated speed (m/s) without text clipping on 454x454 px.
 * - Dynamic alert radius (R_alert) visual indicator.
 * - Rest modulation indicator (AWAKE vs DEEP_REST).
 * - [Simular Ruta] button (visible during TRACKING when isSimulated is true) and [Detener] button (>= 48dp).
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
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        // Outer visual progress arc in Green (#00E676)
        if (!isAmbient) {
            CircularProgressArc(
                progress = progress,
                color = WakeSyncColors.GreenTransit,
                trackColor = WakeSyncColors.GreenMuted,
                modifier = Modifier.size(200.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Destination Label
            Text(
                text = destName,
                color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.GreenTransit,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Metric: Remaining Distance (Glanceability <= 3s)
            val distanceText = if (distance != null) {
                stringResource(R.string.transit_distance_format, distance)
            } else {
                "-- m"
            }
            Text(
                text = distanceText,
                color = WakeSyncColors.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Secondary Metric: Speed + Dynamic Radius
            val speedText = if (speed != null) {
                stringResource(R.string.transit_speed_format, speed)
            } else {
                "-- m/s"
            }
            Text(
                text = "$speedText • R: ${radius.toInt()}m",
                color = WakeSyncColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            // Rest State Modulation Indicator
            Text(
                text = if (isDeepRest) stringResource(R.string.transit_state_deep_rest) else stringResource(R.string.transit_state_awake),
                color = if (isDeepRest) WakeSyncColors.IndigoDeepRest else WakeSyncColors.GreenTransit,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Controls Row (Stop + Simulation Trigger)
            if (!isAmbient) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stop button (>= 48dp x 48dp)
                    Button(
                        onClick = onStopSession,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.CarbonSurface,
                            contentColor = WakeSyncColors.White
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_stop), fontSize = 10.sp)
                    }

                    // [Simular Ruta] button (only shown when isSimulated is true, >= 48dp x 48dp)
                    if (state.isSimulated) {
                        Button(
                            onClick = onSimulateRoute,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WakeSyncColors.GreenMuted,
                                contentColor = WakeSyncColors.GreenTransit
                            )
                        ) {
                            Text(
                                text = "🚌",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
