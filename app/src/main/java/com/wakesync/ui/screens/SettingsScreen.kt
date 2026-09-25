@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Text
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Screen 4: Settings (Configuración) — SRS 3.1 & local-storage-spec.
 *
 * Features:
 * - Option to clear local session history with explicit user confirmation dialog.
 * - Runtime permission status overview and re-request launcher button (RF-CORE-04).
 * - Hot simulation toggle switch (RF-SENS-06).
 * - Horologist rotary input navigation.
 */
@Composable
fun SettingsScreen(
    state: WakeSyncState,
    onClearHistory: () -> Unit,
    onRequestPermissions: () -> Unit,
    onToggleSimulation: (enabled: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Explicit confirmation dialog for history clearance (local-storage-spec requirement)
    if (showClearConfirmDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WakeSyncColors.PureBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_history_confirm_title),
                    color = WakeSyncColors.EmberRose,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_clear_history_confirm_msg),
                    color = WakeSyncColors.TanMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { showClearConfirmDialog = false },
                        modifier = Modifier.sizeIn(minWidth = 50.dp, minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.BlueDeep)
                    ) {
                        Text(text = stringResource(R.string.btn_cancel), fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            showClearConfirmDialog = false
                            onClearHistory()
                        },
                        modifier = Modifier.sizeIn(minWidth = 50.dp, minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.EmberRose)
                    ) {
                        Text(text = stringResource(R.string.btn_confirm), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = stringResource(R.string.title_settings),
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.CreamSoft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Section 1: Clear History (local-storage-spec)
            item {
                Card(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.settings_history_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.btn_clear_history),
                            fontSize = 10.sp,
                            color = WakeSyncColors.EmberRose
                        )
                    }
                }
            }

            // Section 2: Runtime Permissions (RF-CORE-04)
            item {
                Card(
                    onClick = { if (state.missingPermissions.isNotEmpty()) onRequestPermissions() },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.settings_permissions_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val hasMissing = state.missingPermissions.isNotEmpty()
                        Text(
                            text = if (hasMissing) stringResource(R.string.settings_permissions_missing)
                            else stringResource(R.string.settings_permissions_all_granted),
                            fontSize = 10.sp,
                            color = if (hasMissing) WakeSyncColors.WarningOchre else WakeSyncColors.SageTeal
                        )
                    }
                }
            }

            // Section 3: Simulation Mode Toggle (RF-SENS-06)
            item {
                Card(
                    onClick = { onToggleSimulation(!state.isSimulated) },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.settings_simulation_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.isSimulated) "Estado: SIMULADO (On)" else "Estado: SENSORES REALES (Off)",
                            fontSize = 10.sp,
                            color = if (state.isSimulated) WakeSyncColors.SteelBlue else WakeSyncColors.TanMuted
                        )
                    }
                }
            }

            // Section 4: App info & Back Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_app_info),
                    fontSize = 9.sp,
                    color = WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.BlueDeep)
                ) {
                    Text(text = "←", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
