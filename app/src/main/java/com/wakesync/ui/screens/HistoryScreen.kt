@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.google.android.horologist.compose.layout.ResponsiveTimeText
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.model.SessionRecord
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.HistoryRow
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Screen: Historial (RF-INS-03, `wear-design-system` SKILL.md sections 4.9, 4.10 and 5.3).
 *
 * `ScalingLazyColumn` of [HistoryRow]s, newest first, scrollable with touch and rotary; an empty
 * [history] shows the empty state (4.10) instead. No on-screen exit button (5.2): back is the
 * edge swipe (`DismissibleScreen` in WakeSyncNavHost) or the physical button ([BackHandler]).
 * Tapping a row opens Detalle for that record.
 */
@Composable
fun HistoryScreen(
    history: List<SessionRecord>,
    onOpenRecord: (recordId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val screenBackground = if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.NavyDeep
    val records = remember(history) { UiFormatters.historyNewestFirst(history) }

    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground),
        contentAlignment = Alignment.Center
    ) {
        if (records.isEmpty()) {
            HistoryEmptyState()
        } else {
            HistoryList(records = records, onOpenRecord = onOpenRecord)
        }

        if (!isAmbient) {
            // Opaque band behind TimeText (same pattern as HomeScreen / SessionSummaryScreen).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WakeSyncSpacing.xxxl)
                    .align(Alignment.TopCenter)
                    .background(screenBackground)
            )
            ResponsiveTimeText()
        }
    }
}

@Composable
private fun HistoryList(records: List<SessionRecord>, onOpenRecord: (String) -> Unit) {
    val scrollState = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .rotaryWithScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = WakeSyncSpacing.safeInsetVertical,
            bottom = WakeSyncSpacing.safeInsetVertical,
            start = WakeSyncSpacing.safeInsetHorizontal,
            end = WakeSyncSpacing.safeInsetHorizontal
        )
    ) {
        item {
            Text(
                text = stringResource(R.string.title_history),
                style = WakeSyncTextStyles.Title,
                color = WakeSyncColors.CreamSoft,
                modifier = Modifier.padding(bottom = WakeSyncSpacing.xs)
            )
        }
        items(records, key = { it.id }) { record ->
            HistoryRow(record = record, onClick = { onOpenRecord(record.id) })
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier.padding(horizontal = WakeSyncSpacing.safeInsetHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = WakeSyncColors.BronzeMuted,
            modifier = Modifier
                .padding(bottom = WakeSyncSpacing.sm)
                .size(WakeSyncSpacing.iconLarge)
        )
        Text(
            text = stringResource(R.string.history_empty_state),
            style = WakeSyncTextStyles.Body,
            color = WakeSyncColors.TanMuted,
            textAlign = TextAlign.Center
        )
    }
}
