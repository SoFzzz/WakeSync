@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.places.DestinationPickerState
import com.wakesync.core.places.PlacePrediction
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.theme.WakeSyncColors

/**
 * Mirrors [com.wakesync.places.DestinationSearchRepository.PLACES_MIN_QUERY_CHARS]. com.wakesync.ui
 * cannot import com.wakesync.places directly (wakesync-architecture), so this constant is
 * duplicated here purely to decide when to show the "mínimo 3 caracteres" hint locally;
 * the actual enforcement still happens in the repository.
 */
private const val DEST_SEARCH_MIN_QUERY_CHARS = 3

private const val REMOTE_INPUT_KEY = "wakesync_destination_query"

/**
 * Buscar Destino (CR-01, RF-PLC-01/02).
 *
 * Renders [DestinationPickerState.Idle] (voice/keyboard entry point) and
 * [DestinationPickerState.Results] (up to 5 predictions). Voice dictation may be unavailable
 * on the Wear OS emulator; the on-screen keyboard offered by RemoteInput always works there.
 */
@Composable
fun DestinationSearchScreen(
    state: DestinationPickerState,
    onSearch: (String) -> Unit,
    onSelectPrediction: (String) -> Unit,
    onOpenMap: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()
    var queryTooShort by remember { mutableStateOf(false) }

    val searchInputLabel = stringResource(R.string.dest_search_hint)
    val remoteInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = RemoteInput.getResultsFromIntent(result.data)
            ?.getCharSequence(REMOTE_INPUT_KEY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) {
            if (text.length < DEST_SEARCH_MIN_QUERY_CHARS) {
                queryTooShort = true
            } else {
                queryTooShort = false
                onSearch(text)
            }
        }
    }

    val onLaunchInput: () -> Unit = {
        val remoteInputs = listOf(
            RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(searchInputLabel).build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        remoteInputLauncher.launch(intent)
    }

    LaunchedEffect(state) {
        if (state is DestinationPickerState.Results) {
            queryTooShort = false
        }
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
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_dest_search),
                    color = if (isAmbient) WakeSyncColors.TextMuted else WakeSyncColors.GreenTransit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (!isAmbient) {
                item {
                    Button(
                        onClick = onLaunchInput,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .sizeIn(minHeight = 48.dp)
                            .padding(vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.GreenMuted,
                            contentColor = WakeSyncColors.GreenTransit
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_search), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Button(
                        onClick = onOpenMap,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .sizeIn(minHeight = 48.dp)
                            .padding(vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.Carbon,
                            contentColor = WakeSyncColors.White
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_choose_on_map), fontSize = 11.sp)
                    }
                }

                if (queryTooShort) {
                    item {
                        Text(
                            text = stringResource(R.string.dest_search_min_chars_hint),
                            color = WakeSyncColors.OrangeWarning,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            if (state is DestinationPickerState.Results) {
                if (state.predictions.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.dest_search_no_results),
                            color = WakeSyncColors.TextMuted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                    items(state.predictions.size) { index ->
                        val prediction = state.predictions[index]
                        DestinationResultItem(prediction = prediction, onClick = { onSelectPrediction(prediction.placeId) })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WakeSyncColors.CarbonSurface,
                        contentColor = WakeSyncColors.White
                    )
                ) {
                    Text(text = "←", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DestinationResultItem(prediction: PlacePrediction, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .sizeIn(minHeight = 48.dp)
            .padding(vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WakeSyncColors.CarbonSurface,
            contentColor = WakeSyncColors.White
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = prediction.primaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (!prediction.secondaryText.isNullOrBlank()) {
                Text(
                    text = prediction.secondaryText,
                    fontSize = 9.sp,
                    color = WakeSyncColors.TextMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
