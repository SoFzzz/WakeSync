package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Non-blocking visual indicator when biometrics or heart rate sensors report SENSOR_NO_DISPONIBLE.
 * Informs the user without disrupting active background services or causing crashes.
 */
@Composable
fun SensorUnavailableBanner(
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.WarningOchreMuted.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = if (isAmbient) WakeSyncColors.BlueDeep else WakeSyncColors.WarningOchre,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sensor_unavailable_title),
                color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.WarningOchre,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!isAmbient) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.sensor_unavailable_msg),
                    color = WakeSyncColors.CreamSoft,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}
