package com.wakesync.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.AlertLevel
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Transversal full-screen overlay for active haptic alarms and arrival notifications.
 *
 * Implements strict visual differentiation between self-resolving one-shot alerts
 * (SOFT / MODERATE) and continuous looping alarms requiring active tactile dismissal (URGENT).
 *
 * Features a massive center touch target (>= 80dp) allowing blind tactile dismissal on wrist.
 */
@Composable
fun ActiveAlertOverlay(
    alertLevel: AlertLevel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (alertLevel == AlertLevel.NONE) return

    // Explicitly override ambient mode: alerts must render at full interactive visibility
    CompositionLocalProvider(LocalAmbientMode provides false) {
        val isUrgent = (alertLevel == AlertLevel.URGENT)

        // Pulsing background animation for high glanceability
        val infiniteTransition = rememberInfiniteTransition(label = "AlertPulseTransition")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isUrgent) 1.08f else 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isUrgent) 500 else 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseScaleAnimation"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(WakeSyncColors.PureBlack),
            contentAlignment = Alignment.Center
        ) {
            // Background pulsing glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isUrgent) WakeSyncColors.CoralMuted.copy(alpha = 0.65f)
                        else WakeSyncColors.CarbonSurface.copy(alpha = 0.5f)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Alert Level Header
                Text(
                    text = when (alertLevel) {
                        AlertLevel.URGENT -> stringResource(R.string.alert_urgent_title)
                        AlertLevel.MODERATE -> stringResource(R.string.alert_moderate_title)
                        AlertLevel.SOFT -> stringResource(R.string.alert_soft_title)
                        AlertLevel.NONE -> ""
                    },
                    color = WakeSyncColors.CoralAlert,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Nature of alert: Self-resolving vs User action required
                Text(
                    text = when (alertLevel) {
                        AlertLevel.URGENT -> stringResource(R.string.alert_urgent_desc)
                        AlertLevel.MODERATE -> stringResource(R.string.alert_moderate_desc)
                        AlertLevel.SOFT -> stringResource(R.string.alert_soft_desc)
                        AlertLevel.NONE -> ""
                    },
                    color = if (isUrgent) WakeSyncColors.White else WakeSyncColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Massive Dismiss Button (>= 80dp touch target, far exceeding 48dp minimum)
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUrgent) WakeSyncColors.CoralAlert else WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    )
                ) {
                    Text(
                        text = if (isUrgent) stringResource(R.string.btn_dismiss_alert) else stringResource(R.string.btn_stop),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                if (isUrgent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.alert_touch_to_dismiss),
                        color = WakeSyncColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
