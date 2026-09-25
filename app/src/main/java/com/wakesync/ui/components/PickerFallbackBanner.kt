package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Shared orange-accent fallback for [com.wakesync.core.places.DestinationPickerState.Offline]
 * and [com.wakesync.core.places.DestinationPickerState.Error] (RF-PLC-05), reused across the
 * Buscar Destino, Mapa de Destino and Confirmar Destino screens instead of duplicating the
 * pattern three times.
 *
 * @param stepTitle Which picker step degraded (Buscar Destino / Mapa de Destino), so the user
 * keeps context of where they were when the failure happened.
 */
@Composable
fun PickerFallbackBanner(
    message: String,
    stepTitle: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
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
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = stepTitle,
                color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.WarningOchre,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = WakeSyncColors.CreamSoft,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 3
            )

            if (!isAmbient) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.WarningOchreMuted,
                        contentColor = WakeSyncColors.WarningOchre
                    )
                ) {
                    Text(text = stringResource(R.string.btn_retry), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.BlueDeep,
                        contentColor = WakeSyncColors.CreamSoft
                    )
                ) {
                    Text(text = stringResource(R.string.btn_cancel), fontSize = 10.sp)
                }
            }
        }
    }
}
