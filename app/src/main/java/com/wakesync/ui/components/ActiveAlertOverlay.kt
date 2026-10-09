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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.AlertLevel
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Transversal full-screen overlay for active haptic alarms and arrival notifications
 * (Transporte arrival alert and Siesta end-of-nap alert) — `wear-design-system` SKILL.md
 * sections 2.4 and 4.x.
 *
 * Implements strict visual differentiation between self-resolving one-shot alerts
 * (SOFT / MODERATE) and continuous looping alarms requiring active tactile dismissal (URGENT).
 * All accent color comes from the `EmberRose` token (`#D47F68`, section 2.4 "Alerta activa" —
 * already ≥4.5:1 against `background`/`PureBlack`, no raw hex or emoji anywhere here); text
 * uses the shared `WakeSyncTextStyles` scale instead of loose `fontSize`s, and spacing comes
 * from `WakeSyncSpacing`. Dismissal stays a single touch on the massive center button
 * (>= 80dp), same as before.
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
                        if (isUrgent) WakeSyncColors.EmberRoseMuted.copy(alpha = 0.65f)
                        else WakeSyncColors.BlueDeep.copy(alpha = 0.5f)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WakeSyncSpacing.xxl, vertical = WakeSyncSpacing.lg),
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
                    style = WakeSyncTextStyles.Title,
                    color = WakeSyncColors.EmberRose,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))

                // Nature of alert: Self-resolving vs User action required
                Text(
                    text = when (alertLevel) {
                        AlertLevel.URGENT -> stringResource(R.string.alert_urgent_desc)
                        AlertLevel.MODERATE -> stringResource(R.string.alert_moderate_desc)
                        AlertLevel.SOFT -> stringResource(R.string.alert_soft_desc)
                        AlertLevel.NONE -> ""
                    },
                    style = WakeSyncTextStyles.Label,
                    color = if (isUrgent) WakeSyncColors.CreamSoft else WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(WakeSyncSpacing.md))

                // Massive Dismiss Button (>= 80dp touch target, far exceeding the 48dp minimum
                // from section 3.4 — deliberately oversized for blind tactile dismissal).
                // Contrast bug fixed here (pre-existing, not introduced this stage): a solid
                // EmberRose fill needs onPrimary/NavyDeep text for the skill's documented
                // 4.81:1 (section 2.4) — CreamSoft on EmberRose measures only ~2.4:1, well
                // under AA. BlueDeep's fill keeps CreamSoft, unchanged.
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUrgent) WakeSyncColors.EmberRose else WakeSyncColors.BlueDeep,
                        contentColor = if (isUrgent) WakeSyncColors.NavyDeep else WakeSyncColors.CreamSoft
                    )
                ) {
                    Text(
                        text = if (isUrgent) stringResource(R.string.btn_dismiss_alert) else stringResource(R.string.btn_stop),
                        style = WakeSyncTextStyles.Title,
                        textAlign = TextAlign.Center
                    )
                }

                if (isUrgent) {
                    Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                    Text(
                        text = stringResource(R.string.alert_touch_to_dismiss),
                        style = WakeSyncTextStyles.Label,
                        color = WakeSyncColors.TanMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
