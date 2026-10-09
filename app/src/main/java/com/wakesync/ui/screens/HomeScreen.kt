@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.google.android.horologist.compose.layout.ResponsiveTimeText
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.model.WakeSyncState
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.ActionChip
import com.wakesync.ui.components.SecondaryIconChip
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen 1: Home / Mode Selector (SRS 3.1).
 *
 * Compact list (`wear-design-system` SKILL.md section 6.1): equally-weighted [ActionChip]
 * rows (Siesta, Siesta con Destino, Transporte, Historial) plus a small [SecondaryIconChip]
 * for Ajustes at the end — replaces the previous ~110dp full-width cards and the oversized
 * 48dp-filled settings button.
 *
 * - Siesta starts immediately (1 tap, no destination).
 * - Siesta con Destino opens the same Buscar/Mapa/Confirmar Destino flow as Transporte, but
 *   for a Nap session — this is a standalone chip, not a button nested inside Siesta's chip:
 *   [ActionChip] is one action per tap, and `NapScreen` only renders once a session is
 *   already active (`requestStartSession` starts it immediately), so there is no "about to
 *   start" Nap screen to attach a nested control to. It replaces the pre-Etapa-3
 *   "+ Destino" chip that used to be nested inside Siesta's old card.
 * - Transit always routes through that same destination flow (CR-01: the 3 fixed
 *   destinations no longer exist, the user picks any place via Mapbox).
 * - Historial opens an empty-state placeholder screen until the real screen lands (Etapa 7).
 * - Non-blocking runtime permission alert if needed (design system component 4.7).
 */
@Composable
fun HomeScreen(
    state: WakeSyncState,
    onStartNap: () -> Unit,
    onOpenDestinationSearch: (forNap: Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WakeSyncSpacing.sm)
        ) {
            // Permission warning, in-flow compact notice (component 4.7) — never overlaid.
            if (state.missingPermissions.isNotEmpty() && !isAmbient) {
                item {
                    Text(
                        text = stringResource(R.string.settings_permissions_missing),
                        style = WakeSyncTextStyles.Label,
                        color = WakeSyncColors.WarningOchre
                    )
                }
            }

            item {
                ActionChip(
                    icon = painterResource(R.drawable.ic_bedtime),
                    iconContentDescription = stringResource(R.string.btn_start_nap),
                    title = stringResource(R.string.btn_start_nap),
                    subtitle = stringResource(R.string.nap_calibrating_desc),
                    onClick = onStartNap
                )
            }

            item {
                ActionChip(
                    icon = painterResource(R.drawable.ic_bedtime),
                    iconContentDescription = stringResource(R.string.siesta_with_destination_title),
                    title = stringResource(R.string.siesta_with_destination_title),
                    subtitle = stringResource(R.string.siesta_with_destination_subtitle),
                    onClick = { onOpenDestinationSearch(true) }
                )
            }

            item {
                ActionChip(
                    icon = painterResource(R.drawable.ic_directions),
                    iconContentDescription = stringResource(R.string.btn_start_transit),
                    title = stringResource(R.string.btn_start_transit),
                    subtitle = stringResource(R.string.transit_select_destination_prompt),
                    onClick = { onOpenDestinationSearch(false) }
                )
            }

            item {
                ActionChip(
                    icon = painterResource(R.drawable.ic_history),
                    iconContentDescription = stringResource(R.string.title_history),
                    title = stringResource(R.string.title_history),
                    subtitle = stringResource(R.string.history_subtitle_home),
                    onClick = onOpenHistory
                )
            }

            item {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                SecondaryIconChip(
                    icon = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.title_settings),
                    onClick = onOpenSettings
                )
            }
        }

        if (!isAmbient) {
            // Opaque band behind TimeText: without it, scrolled-past chip text bleeds
            // through the (otherwise transparent) time overlay and becomes unreadable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.TopCenter)
                    .background(WakeSyncColors.NavyDeep)
            )
            ResponsiveTimeText()
        }
    }
}
