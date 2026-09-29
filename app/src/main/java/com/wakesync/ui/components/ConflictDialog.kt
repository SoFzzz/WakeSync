package com.wakesync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.wakesync.core.model.SessionConflict
import com.wakesync.core.model.SessionType
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Mutual exclusion dialog resolving concurrent session start requests (RF-CORE-02).
 * Prompts user to confirm whether to cancel active mode to begin new mode, or keep existing.
 */
@Composable
fun ConflictDialog(
    conflict: SessionConflict,
    onResolve: (proceedWithNew: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.conflict_dialog_title),
                color = WakeSyncColors.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            val currentName = if (conflict.runningSession == SessionType.NAP) {
                stringResource(R.string.title_nap)
            } else {
                stringResource(R.string.title_transit)
            }

            val requestedName = if (conflict.requestedSession == SessionType.NAP) {
                stringResource(R.string.title_nap)
            } else {
                stringResource(R.string.title_transit)
            }

            Text(
                text = stringResource(R.string.conflict_dialog_msg, currentName, requestedName),
                color = WakeSyncColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keep current button (Cancel request)
                Button(
                    onClick = { onResolve(false) },
                    modifier = Modifier.sizeIn(minWidth = 54.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Proceed with new button (End prior and start requested)
                Button(
                    onClick = { onResolve(true) },
                    modifier = Modifier.sizeIn(minWidth = 54.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.IndigoDeepRest,
                        contentColor = WakeSyncColors.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_confirm),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
