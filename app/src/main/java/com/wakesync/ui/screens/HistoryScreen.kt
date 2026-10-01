package com.wakesync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen: Historial (placeholder). Only the empty state (design system component 4.10)
 * ships in this stage — the real session list + Detalle flow (RF-INS-03) is Etapa 7. No
 * on-screen exit button (section 6.7 / 5.2 pattern): return via physical back
 * ([BackHandler]); see [com.wakesync.ui.navigation.WakeSyncNavHost] for why an edge swipe
 * is not wired yet.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current

    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep)
            .padding(horizontal = WakeSyncSpacing.safeInsetHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = WakeSyncColors.BronzeMuted,
                modifier = Modifier
                    .padding(bottom = WakeSyncSpacing.sm)
                    .size(32.dp)
            )
            Text(
                text = stringResource(R.string.history_empty_state),
                style = WakeSyncTextStyles.Body,
                color = WakeSyncColors.TanMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
