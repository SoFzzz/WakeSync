@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Screen 1: Home / Mode Selector (SRS 3.1).
 *
 * Provides rapid start (<= 2 taps) for both Nap and Transit modes.
 * Features:
 * - Neutral white/carbon palette, strictly zero haptic disturbance during navigation.
 * - Rotary scrolling supported via Horologist (rotaryWithScroll).
 * - Progressive disclosure: Immediate 1-tap nap, with optional destination picker chip.
 * - Transit mode destination selection and 1-tap launch.
 * - Non-blocking runtime permission alert if needed.
 */
@Composable
fun HomeScreen(
    state: WakeSyncState,
    onStartNap: (destination: GeoPoint?) -> Unit,
    onStartTransit: (destination: GeoPoint) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()

    // Predefined catalogued destinations (RF-TRAN-01)
    val defaultDestinations = remember {
        listOf(
            GeoPoint(6.2518, -75.5684, "Campus UCC"),
            GeoPoint(6.2550, -75.5700, "Estación Metro"),
            GeoPoint(6.2400, -75.5800, "Casa")
        )
    }

    var transitDestination by remember { mutableStateOf(defaultDestinations[0]) }
    var showNapDestPicker by remember { mutableStateOf(false) }
    var showTransitDestPicker by remember { mutableStateOf(false) }

    // Dialog for Nap destination selection (Progressive Disclosure)
    if (showNapDestPicker) {
        DestinationPickerDialog(
            title = stringResource(R.string.title_nap) + " - " + stringResource(R.string.select_destination_title),
            destinations = defaultDestinations,
            allowNone = true,
            onSelect = { dest ->
                showNapDestPicker = false
                onStartNap(dest)
            },
            onDismiss = { showNapDestPicker = false }
        )
        return
    }

    // Dialog for Transit destination selection
    if (showTransitDestPicker) {
        DestinationPickerDialog(
            title = stringResource(R.string.title_transit) + " - " + stringResource(R.string.select_destination_title),
            destinations = defaultDestinations,
            allowNone = false,
            onSelect = { dest ->
                showTransitDestPicker = false
                if (dest != null) {
                    transitDestination = dest
                }
            },
            onDismiss = { showTransitDestPicker = false }
        )
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
                    onClick = { onStartNap(null) },
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

                        // Progressive disclosure chip for optional GPS destination
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 28.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WakeSyncColors.Carbon)
                                    .clickable { showNapDestPicker = true }
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
                    onClick = { onStartTransit(transitDestination) },
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
                            text = stringResource(R.string.transit_destination_label, transitDestination.name ?: ""),
                            fontSize = 10.sp,
                            color = WakeSyncColors.GreenTransit,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Destination change chip
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WakeSyncColors.Carbon)
                                .clickable { showTransitDestPicker = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.select_destination_title),
                                fontSize = 9.sp,
                                color = WakeSyncColors.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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

/**
 * Lightweight modal picker for destinations.
 */
@Composable
private fun DestinationPickerDialog(
    title: String,
    destinations: List<GeoPoint>,
    allowNone: Boolean,
    onSelect: (GeoPoint?) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryWithScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = title,
                    color = WakeSyncColors.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (allowNone) {
                item {
                    Button(
                        onClick = { onSelect(null) },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .sizeIn(minHeight = 48.dp)
                            .padding(vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.CarbonSurface)
                    ) {
                        Text(
                            text = stringResource(R.string.dest_none),
                            fontSize = 11.sp,
                            color = WakeSyncColors.White
                        )
                    }
                }
            }

            items(destinations.size) { index ->
                val dest = destinations[index]
                Button(
                    onClick = { onSelect(dest) },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .sizeIn(minHeight = 48.dp)
                        .padding(vertical = 3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.CarbonSurface)
                ) {
                    Text(
                        text = dest.name ?: dest.latitude.toString(),
                        fontSize = 11.sp,
                        color = WakeSyncColors.White
                    )
                }
            }

            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .padding(top = 6.dp, bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WakeSyncColors.Carbon)
                ) {
                    Text(text = stringResource(R.string.btn_cancel), fontSize = 10.sp, color = WakeSyncColors.TextMuted)
                }
            }
        }
    }
}
