@file:OptIn(com.google.android.horologist.annotations.ExperimentalHorologistApi::class)

package com.wakesync.ui.screens

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.wakesync.R
import com.wakesync.core.places.DestinationPickerState
import com.wakesync.core.places.PlacePrediction
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.ActionChip
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncShapes
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles

/**
 * Mirrors [com.wakesync.places.DestinationSearchRepository.PLACES_MIN_QUERY_CHARS]. com.wakesync.ui
 * cannot import com.wakesync.places directly (wakesync-architecture), so this constant is
 * duplicated here purely to decide when to show the "mínimo 3 caracteres" hint locally;
 * the actual enforcement still happens in the repository.
 */
private const val DEST_SEARCH_MIN_QUERY_CHARS = 3

private const val REMOTE_INPUT_KEY = "wakesync_destination_query"

/**
 * Extracts the typed/dictated query from a RemoteInput activity result. Returns null when the
 * user cancelled (`resultCode != RESULT_OK`) or the result carries no [data]: on Wear OS 6 a
 * cancelled RemoteInput returns a null Intent, and `RemoteInput.getResultsFromIntent(null)`
 * throws a NullPointerException. Generic over the intent type so it is testable without Android.
 */
internal fun <T : Any> resolveRemoteInputQuery(
    resultCode: Int,
    data: T?,
    readText: (T) -> CharSequence?
): String? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    return readText(data)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun readRemoteInputText(data: Intent): CharSequence? =
    RemoteInput.getResultsFromIntent(data)?.getCharSequence(REMOTE_INPUT_KEY)

/**
 * Buscar Destino (CR-01, RF-PLC-01/02) — `wear-design-system` SKILL.md section 6.4.
 *
 * The title uses the noun ("Destino") and the action chips use the verb ("Buscar",
 * "Elegir en Mapa"), removing the previous title/button redundancy that both literally said
 * "Buscar Destino". "Buscar" is deliberately not "Buscar por Voz": `RemoteInput` offers both
 * voice dictation and the on-screen keyboard, hence the "Voz o teclado" subtitle instead of
 * a voice-only label. The two actions are design system component 4.1 (`ActionChip`) instead
 * of full-width buttons. No on-screen back arrow, and no `onExit` parameter either: the
 * enclosing `DismissibleScreen` in `WakeSyncNavHost` already maps the swipe gesture and the
 * physical back button to exiting the whole picker flow (section 5.2) — this screen has no
 * exit action of its own to wire up.
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
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val scrollState = rememberScalingLazyListState()
    var queryTooShort by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }

    val submitQuery: (String) -> Unit = { text ->
        if (text.length < DEST_SEARCH_MIN_QUERY_CHARS) {
            queryTooShort = true
        } else {
            queryTooShort = false
            onSearch(text)
        }
    }

    val searchInputLabel = stringResource(R.string.dest_search_hint)
    val remoteInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        resolveRemoteInputQuery(result.resultCode, result.data, ::readRemoteInputText)?.let(submitQuery)
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

    if (isTyping) {
        InAppQueryField(
            onSubmit = { text ->
                isTyping = false
                submitQuery(text.trim())
            },
            onCancel = { isTyping = false },
            modifier = modifier
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
                .rotaryWithScroll(scrollState)
                .padding(horizontal = WakeSyncSpacing.safeInsetHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_dest_search),
                    style = WakeSyncTextStyles.Title,
                    color = if (isAmbient) WakeSyncColors.TanMuted else WakeSyncColors.SageTeal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = WakeSyncSpacing.sm)
                )
            }

            if (!isAmbient) {
                item {
                    ActionChip(
                        icon = painterResource(R.drawable.ic_search),
                        iconContentDescription = stringResource(R.string.btn_search),
                        title = stringResource(R.string.btn_search),
                        subtitle = stringResource(R.string.dest_search_subtitle),
                        onClick = onLaunchInput,
                        modifier = Modifier.padding(bottom = WakeSyncSpacing.xs)
                    )
                }

                item {
                    ActionChip(
                        icon = painterResource(R.drawable.ic_search),
                        iconContentDescription = stringResource(R.string.btn_type_in_app),
                        title = stringResource(R.string.btn_type_in_app),
                        subtitle = stringResource(R.string.dest_type_in_app_subtitle),
                        onClick = { isTyping = true },
                        modifier = Modifier.padding(bottom = WakeSyncSpacing.xs)
                    )
                }

                item {
                    ActionChip(
                        icon = painterResource(R.drawable.ic_map),
                        iconContentDescription = stringResource(R.string.btn_choose_on_map),
                        title = stringResource(R.string.btn_choose_on_map),
                        subtitle = stringResource(R.string.dest_choose_on_map_subtitle),
                        onClick = onOpenMap
                    )
                }

                if (queryTooShort) {
                    item {
                        Text(
                            text = stringResource(R.string.dest_search_min_chars_hint),
                            style = WakeSyncTextStyles.Label,
                            color = WakeSyncColors.WarningOchre,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = WakeSyncSpacing.xs)
                        )
                    }
                }
            }

            if (state is DestinationPickerState.Results) {
                if (state.predictions.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.dest_search_no_results),
                            style = WakeSyncTextStyles.Body,
                            color = WakeSyncColors.TanMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = WakeSyncSpacing.xs)
                        )
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(WakeSyncSpacing.xs))
                    }
                    items(state.predictions.size) { index ->
                        val prediction = state.predictions[index]
                        DestinationResultItem(prediction = prediction, onClick = { onSelectPrediction(prediction.placeId) })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
            }
        }
    }
}

/**
 * Fallback text entry owned by the app, used when the system RemoteInput screen does not render
 * (Samsung Wear OS 6 / Honeyboard shows a black screen, verified on a Galaxy Watch4). It is a
 * plain text field that raises the system keyboard itself instead of delegating to another Activity.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun InAppQueryField(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = modifier.fillMaxSize().background(WakeSyncColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = WakeSyncSpacing.safeInsetHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dest_search_hint),
                style = WakeSyncTextStyles.Label,
                color = WakeSyncColors.TanMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = WakeSyncSpacing.sm)
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = WakeSyncTextStyles.Body.copy(color = WakeSyncColors.CreamSoft),
                cursorBrush = SolidColor(WakeSyncColors.RoseGold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WakeSyncShapes.extraSmall)
                    .background(WakeSyncColors.BlueDeep)
                    .padding(WakeSyncSpacing.md)
                    .focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun DestinationResultItem(prediction: PlacePrediction, onClick: () -> Unit) {
    // Single root composable (Column only, no sibling Spacer): ScalingLazyColumn scales each
    // item{} slot as one unit, so a trailing sibling here would be measured as its own
    // (unscaled) entry instead of padding that belongs to this result row.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WakeSyncShapes.extraSmall)
            .background(WakeSyncColors.BlueDeep)
            .clickable(onClick = onClick)
            .padding(horizontal = WakeSyncSpacing.md, vertical = WakeSyncSpacing.sm)
            .padding(bottom = WakeSyncSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = prediction.primaryText,
            style = WakeSyncTextStyles.Body,
            color = WakeSyncColors.CreamSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!prediction.secondaryText.isNullOrBlank()) {
            Text(
                text = prediction.secondaryText,
                style = WakeSyncTextStyles.Label,
                color = WakeSyncColors.TanMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
