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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.GeoPoint
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.SecondaryIconChip
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Confirmar Destino (CR-01, RF-PLC-04) — `wear-design-system` SKILL.md section 6.5.
 *
 * Name/address stay within the round-screen safe area (`WakeSyncSpacing.safeInsetHorizontal`
 * padding + capped line counts, no change needed beyond typography tokens — the previous
 * `24.dp` padding already kept text off the curvature). Cancelar/Confirmar are icon buttons
 * (component 4.3, `ic_close`/`ic_check`) instead of plain-text circular buttons.
 *
 * [straightLineMeters] is frequently null in the current build because
 * `currentLocationProvider` is miswired in `WakeSyncApplication` (returns the transit
 * destination instead of the live GPS position — tracked as a connectivity-engineer
 * follow-up). The distance row is simply omitted when null rather than showing a
 * misleading "0 m" or "--" value.
 */
@Composable
fun ConfirmDestinationScreen(
    destination: GeoPoint,
    address: String?,
    straightLineMeters: Float?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val distanceText = UiFormatters.formatStraightLineDistance(straightLineMeters)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = WakeSyncSpacing.xxl)
                .padding(bottom = WakeSyncSpacing.bottomButtonReserve)
        ) {
            Text(
                text = stringResource(R.string.title_dest_confirm),
                style = WakeSyncTextStyles.Label,
                color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.SageTeal,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
            Text(
                text = destination.name ?: stringResource(R.string.dest_confirm_unnamed),
                style = WakeSyncTextStyles.Title,
                color = WakeSyncColors.CreamSoft,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                Text(
                    text = address,
                    style = WakeSyncTextStyles.Body,
                    color = WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (distanceText != null) {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                Text(
                    text = stringResource(R.string.dest_confirm_distance_format, distanceText),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.SageTeal,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!isAmbient) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WakeSyncSpacing.primaryButtonBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(WakeSyncSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryIconChip(
                    icon = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.btn_cancel),
                    onClick = onCancel
                )
                SecondaryIconChip(
                    icon = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.btn_confirm),
                    onClick = onConfirm
                )
            }
        }
    }
}
