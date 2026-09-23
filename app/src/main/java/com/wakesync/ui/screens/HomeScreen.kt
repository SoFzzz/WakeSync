@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Screen 1: Home / Mode Selector (SRS 3.1).
 *
 * Features:
 * - Neutral white/carbon palette, strictly zero haptic disturbance during navigation.
 * - Rotary scrolling supported via Horologist (rotaryWithScroll).
 * - Nap starts immediately (1 tap, no destination) with an optional "+ Destino" chip that
 *   opens the Buscar/Mapa/Confirmar Destino flow for an optional Nap destination.
 * - Transit always routes through that same destination flow (CR-01: the 3 fixed
 *   destinations no longer exist, the user picks any place via Google Maps), so starting a
 *   Transit session now takes ~4 taps (card -> search or map -> result/pin -> confirm)
 *   instead of the single tap this screen used to document before CR-01.
 * - Non-blocking runtime permission alert if needed.
 */
@Composable
fun HomeScreen(
    state: WakeSyncState,
    onStartNap: () -> Unit,
    onOpenDestinationSearch: (forNap: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()

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
                .rotaryWithScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title Header
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Permission Warning Banner if permissions are missing
            if (state.missingPermissions.isNotEmpty() && !isAmbient) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 4.dp)
                            .background(WakeSyncColors.OrangeMuted, RoundedCornerShape(12.dp))
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_permissions_missing),
                            color = WakeSyncColors.OrangeWarning,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Card 1: Modo Siesta
            item {
                Card(
                    onClick = onStartNap,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    ),
                    border = if (isAmbient) {
                        CardDefaults.outlinedCardBorder()
                    } else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_start_nap),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WakeSyncColors.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.nap_calibrating_desc),
                            fontSize = 10.sp,
                            color = WakeSyncColors.TextMuted,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Progressive disclosure chip for optional destination (CR-01)
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 28.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WakeSyncColors.Carbon)
                                    .clickable { onOpenDestinationSearch(true) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_add_destination),
                                    fontSize = 9.sp,
                                    color = WakeSyncColors.CyanBasal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Modo Transporte
            item {
                Card(
                    onClick = { onOpenDestinationSearch(false) },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    ),
                    border = if (isAmbient) {
                        CardDefaults.outlinedCardBorder()
                    } else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_start_transit),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WakeSyncColors.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.transit_select_destination_prompt),
                            fontSize = 10.sp,
                            color = WakeSyncColors.GreenTransit,
                            maxLines = 1
                        )
                    }
                }
            }

            // Settings Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    )
                ) {
                    Text(
                        text = "⚙",
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
