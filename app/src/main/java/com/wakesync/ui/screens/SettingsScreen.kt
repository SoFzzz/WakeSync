@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen 4: Settings (Configuración) — SRS 3.1 & local-storage-spec.
 *
 * Features:
 * - Option to clear local session history with explicit user confirmation dialog.
 * - Runtime permission status overview and re-request launcher button (RF-CORE-04).
 * - Hot simulation toggle switch (RF-SENS-06).
 * - Horologist rotary input navigation.
 * - No on-screen exit button (`wear-design-system` SKILL.md section 6.7 / 5.2): returning
 *   to Inicio is the physical back button ([BackHandler]) — an edge swipe-to-dismiss is not
 *   wired yet because navigation here is a plain state flag, not a
 *   `SwipeDismissableNavHost` (see [com.wakesync.ui.navigation.WakeSyncNavHost] KDoc).
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

    BackHandler { onBack() }

    // Explicit confirmation dialog for history clearance (local-storage-spec requirement)
    if (showClearConfirmDialog) {
        BackHandler { showClearConfirmDialog = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WakeSyncColors.NavyDeep),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WakeSyncSpacing.xl, vertical = WakeSyncSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_history_confirm_title),
                    style = WakeSyncTextStyles.Title,
                    color = WakeSyncColors.EmberRose,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                Text(
                    text = stringResource(R.string.settings_clear_history_confirm_msg),
                    style = WakeSyncTextStyles.Body,
                    color = WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(WakeSyncSpacing.md))

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
                        Text(text = stringResource(R.string.btn_cancel), style = WakeSyncTextStyles.Label)
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
                        Text(text = stringResource(R.string.btn_confirm), style = WakeSyncTextStyles.Label)
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState),
            contentPadding = PaddingValues(
                top = WakeSyncSpacing.safeInsetVertical,
                bottom = WakeSyncSpacing.safeInsetVertical,
                start = WakeSyncSpacing.safeInsetHorizontal,
                end = WakeSyncSpacing.safeInsetHorizontal
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = stringResource(R.string.title_settings),
                    style = WakeSyncTextStyles.Title,
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.CreamSoft,
                    modifier = Modifier.padding(bottom = WakeSyncSpacing.sm)
                )
            }

            // Section 1: Clear History (local-storage-spec)
            item {
                Card(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = WakeSyncShapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(WakeSyncSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_history_title),
                            style = WakeSyncTextStyles.Title,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.btn_clear_history),
                            style = WakeSyncTextStyles.Label,
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
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = WakeSyncShapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(WakeSyncSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_permissions_title),
                            style = WakeSyncTextStyles.Title,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val hasMissing = state.missingPermissions.isNotEmpty()
                        Text(
                            text = if (hasMissing) stringResource(R.string.settings_permissions_missing)
                            else stringResource(R.string.settings_permissions_all_granted),
                            style = WakeSyncTextStyles.Label,
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
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = WakeSyncShapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueDeep
                    ),
                    border = if (isAmbient) CardDefaults.outlinedCardBorder() else null
                ) {
                    Column(modifier = Modifier.padding(WakeSyncSpacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_simulation_title),
                            style = WakeSyncTextStyles.Title,
                            color = WakeSyncColors.CreamSoft
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.isSimulated) stringResource(R.string.settings_simulation_on)
                            else stringResource(R.string.settings_simulation_off),
                            style = WakeSyncTextStyles.Label,
                            color = if (state.isSimulated) WakeSyncColors.SteelBlue else WakeSyncColors.TanMuted
                        )
                    }
                }
            }

            // Section 4: App info — no exit button underneath (section 6.7): the version
            // label sits inside the bottom safe inset instead of being covered by it.
            item {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
                Text(
                    text = stringResource(R.string.settings_app_info),
                    style = WakeSyncTextStyles.Label,
                    color = WakeSyncColors.TanMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
