package com.wakesync.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.model.SessionConflict
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Mutual exclusion dialog resolving concurrent session start requests (RF-CORE-02, F17) —
 * design system component 4.8 (`FullScreenDialog` pattern) per section 5.4.
 *
 * Full-screen `surface` (NavyDeep, never ambient) with `Title` + `Body` and two icon actions
 * (4.3): `close` keeps the running session, `check` (primary RoseGold) ends it and starts the
 * requested one with its destination. The physical back button counts as `close`. The empty
 * `pointerInput` stops taps from reaching the session screen composed underneath: without it,
 * a tap outside the two buttons would fall through to e.g. NapScreen's `[Detener]`.
 */
@Composable
fun ConflictDialog(
    conflict: SessionConflict,
    onResolve: (proceedWithNew: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onResolve(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WakeSyncColors.BlueBackground)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = WakeSyncSpacing.xl, vertical = WakeSyncSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.conflict_dialog_title),
                style = WakeSyncTextStyles.Title,
                color = WakeSyncColors.CreamSoft,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
            Text(
                text = stringResource(
                    R.string.conflict_dialog_msg,
                    stringResource(sessionTypeStringRes(conflict.runningSession)),
                    stringResource(sessionTypeStringRes(conflict.requestedSession))
                ),
                style = WakeSyncTextStyles.Body,
                color = WakeSyncColors.TanMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(WakeSyncSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryIconChip(
                    icon = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.conflict_dialog_keep),
                    onClick = { onResolve(false) }
                )
                SecondaryIconChip(
                    icon = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.conflict_dialog_proceed),
                    onClick = { onResolve(true) },
                    containerColor = WakeSyncColors.RoseGold,
                    iconTint = WakeSyncColors.NavyDeep
                )
            }
        }
    }
}
