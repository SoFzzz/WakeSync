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
import com.wakesync.core.model.GeoPoint
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Confirmar Destino (CR-01, RF-PLC-04).
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
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.title_dest_confirm),
                color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.GreenTransit,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = destination.name ?: stringResource(R.string.dest_confirm_unnamed),
                color = WakeSyncColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (!address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = address,
                    color = WakeSyncColors.TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            if (distanceText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dest_confirm_distance_format, distanceText),
                    color = WakeSyncColors.GreenTransit,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (!isAmbient) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.CarbonSurface,
                            contentColor = WakeSyncColors.White
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_cancel), fontSize = 10.sp)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.GreenMuted,
                            contentColor = WakeSyncColors.GreenTransit
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_confirm), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
