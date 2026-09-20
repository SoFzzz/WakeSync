package com.wakesync.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.SensorUnavailableBanner
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Screen 2: Nap Mode (MicroNap) — SRS 3.1 & 8.4.
 *
 * Implements the 3 normative visual states:
 * 1. Calibrando Basal: Cyan (#00E5FF), rotating 20s progress ring, basal HR.
 * 2. Monitoreando: Amber (#FFD600), live rest score, RestState, live HR. Silent to protect sleep.
 * 3. Reposo Confirmado: Indigo (#7C4DFF), circular 15 min countdown, subtle dimmed palette.
 *
 * Includes [Simular Siesta] button (visible during CALIBRATING/MONITORING when isSimulated is true)
 * and [Detener] button (>= 48dp).
 */
@Composable
fun NapScreen(
    state: WakeSyncState,
    onStopSession: () -> Unit,
    onSimulateNap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val napState = state.napState
    val biometrics = state.biometricMetrics

    val isSensorUnavailable = biometrics.restState == RestState.SENSOR_UNAVAILABLE

    // Rotation animation for calibration ring
    val infiniteTransition = rememberInfiniteTransition(label = "NapCalibrationTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CalibrationRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        // Sensor Unavailable Overlay banner if needed
        if (isSensorUnavailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                SensorUnavailableBanner()
            }
        }

        // Circular State Presentation
        when (napState.phase) {
            NapPhase.CALIBRATING -> {
                // Estado 1: Calibrando Basal (Cian #00E5FF)
                Box(contentAlignment = Alignment.Center) {
                    val calibrationProgress = (napState.elapsedSeconds / 20.0f).coerceIn(0.0f, 1.0f)

                    if (!isAmbient) {
                        CircularProgressArc(
                            progress = calibrationProgress,
                            color = WakeSyncColors.CyanBasal,
                            trackColor = WakeSyncColors.CyanMuted,
                            modifier = Modifier
                                .size(200.dp)
                                .rotate(rotationAngle)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nap_calibrating_title),
                            color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.CyanBasal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val hr = biometrics.currentHeartRate ?: biometrics.baseHeartRate
                        Text(
                            text = if (hr != null) stringResource(R.string.nap_current_hr_format, hr) else "-- BPM",
                            color = WakeSyncColors.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${20 - napState.elapsedSeconds}s",
                            color = WakeSyncColors.TextMuted,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        NapControlsRow(
                            isSimulated = state.isSimulated,
                            onStop = onStopSession,
                            onSimulate = onSimulateNap,
                            isAmbient = isAmbient
                        )
                    }
                }
            }

            NapPhase.MONITORING -> {
                // Estado 2: Monitoreando (Ámbar #FFD600)
                Box(contentAlignment = Alignment.Center) {
                    val score = biometrics.restScore ?: 0.0f
                    if (!isAmbient) {
                        CircularProgressArc(
                            progress = score,
                            color = WakeSyncColors.AmberMonitoring,
                            trackColor = WakeSyncColors.AmberMuted,
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nap_monitoring_title),
                            color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.AmberMonitoring,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when (biometrics.restState) {
                                RestState.AWAKE -> stringResource(R.string.rest_state_awake)
                                RestState.LIGHT_REST -> stringResource(R.string.rest_state_light)
                                RestState.DEEP_REST -> stringResource(R.string.rest_state_deep)
                                RestState.CALIBRATING -> stringResource(R.string.nap_calibrating_title)
                                RestState.SENSOR_UNAVAILABLE -> stringResource(R.string.rest_state_sensor_unavailable)
                                RestState.UNKNOWN -> stringResource(R.string.rest_state_unknown)
                            },
                            color = WakeSyncColors.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = stringResource(R.string.nap_rest_score_format, score),
                            color = WakeSyncColors.AmberMonitoring,
                            fontSize = 12.sp
                        )

                        val currentHr = biometrics.currentHeartRate
                        if (currentHr != null) {
                            Text(
                                text = stringResource(R.string.nap_current_hr_format, currentHr),
                                color = WakeSyncColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        // Optional distance if resting on transit
                        val dist = napState.distanceToDestinationMeters
                        if (dist != null) {
                            Text(
                                text = stringResource(R.string.nap_dest_distance_format, dist),
                                color = WakeSyncColors.CyanBasal,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        NapControlsRow(
                            isSimulated = state.isSimulated,
                            onStop = onStopSession,
                            onSimulate = onSimulateNap,
                            isAmbient = isAmbient
                        )
                    }
                }
            }

            NapPhase.REST_CONFIRMED -> {
                // Estado 3: Reposo Confirmado (Índigo #7C4DFF)
                Box(contentAlignment = Alignment.Center) {
                    val remainingSec = napState.remainingNapSeconds
                    val totalSec = 15 * 60f
                    val progress = (remainingSec / totalSec).coerceIn(0.0f, 1.0f)

                    if (!isAmbient) {
                        CircularProgressArc(
                            progress = progress,
                            color = WakeSyncColors.IndigoDeepRest,
                            trackColor = WakeSyncColors.IndigoMuted,
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nap_deep_rest_title),
                            color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.IndigoDeepRest,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val minutes = remainingSec / 60
                        val seconds = remainingSec % 60
                        Text(
                            text = stringResource(R.string.nap_remaining_time_format, minutes, seconds),
                            color = WakeSyncColors.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val currentHr = biometrics.currentHeartRate
                        if (currentHr != null) {
                            Text(
                                text = stringResource(R.string.nap_current_hr_format, currentHr),
                                color = WakeSyncColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Only stop button needed once deep rest is confirmed
                        Button(
                            onClick = onStopSession,
                            modifier = Modifier.sizeIn(minWidth = 54.dp, minHeight = 48.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WakeSyncColors.CarbonSurface,
                                contentColor = WakeSyncColors.White
                            )
                        ) {
                            Text(text = stringResource(R.string.btn_stop), fontSize = 10.sp)
                        }
                    }
                }
            }

            else -> {
                // Completed, Cancelled or Idle fallback
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.title_nap),
                        color = WakeSyncColors.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStopSession,
                        modifier = Modifier.sizeIn(minWidth = 54.dp, minHeight = 48.dp),
                        shape = CircleShape
                    ) {
                        Text(text = stringResource(R.string.btn_stop), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NapControlsRow(
    isSimulated: Boolean,
    onStop: () -> Unit,
    onSimulate: () -> Unit,
    isAmbient: Boolean
) {
    if (isAmbient) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stop button (>= 48dp x 48dp)
        Button(
            onClick = onStop,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = WakeSyncColors.CarbonSurface,
                contentColor = WakeSyncColors.White
            )
        ) {
            Text(text = stringResource(R.string.btn_stop), fontSize = 10.sp)
        }

        // [Simular Siesta] button (only shown when isSimulated is true, >= 48dp x 48dp)
        if (isSimulated) {
            Button(
                onClick = onSimulate,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WakeSyncColors.CyanMuted,
                    contentColor = WakeSyncColors.CyanBasal
                )
            ) {
                Text(
                    text = "⚡",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
