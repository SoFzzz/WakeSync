package com.wakesync.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.wakesync.R
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.insights.InsightProvider
import com.wakesync.core.insights.InsightState
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.model.WakeSyncState
import com.wakesync.core.places.DestinationPickerState
import com.wakesync.core.places.DestinationSearchProvider
import com.wakesync.core.session.SessionManager
import com.wakesync.ui.ambient.LocalAmbientMode
import com.wakesync.ui.components.ActiveAlertOverlay
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.CompactNotice
import com.wakesync.ui.components.ConflictDialog
import com.wakesync.ui.components.DismissibleScreen
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.screens.ConfirmDestinationScreen
import com.wakesync.ui.screens.DestinationMapScreen
import com.wakesync.ui.screens.DestinationSearchScreen
import com.wakesync.ui.screens.HistoryScreen
import com.wakesync.ui.screens.HomeScreen
import com.wakesync.ui.screens.NapScreen
import com.wakesync.ui.screens.SessionDetailScreen
import com.wakesync.ui.screens.SessionSummaryScreen
import com.wakesync.ui.screens.SettingsScreen
import com.wakesync.ui.screens.TransitScreen
import com.wakesync.ui.theme.WakeSyncColors
import com.wakesync.ui.theme.WakeSyncSpacing
import com.wakesync.ui.theme.WakeSyncTextStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Which step of the Buscar/Mapa Destino flow the user was on before an Offline/Error fallback. */
private enum class PickerStep { SEARCH, MAP }

/**
 * Offline/Error fallback for the Buscar/Mapa Destino flow (RF-PLC-05). Replaces the old
 * `PickerFallbackBanner`'s ad-hoc colored `Text` with the shared [CompactNotice] component
 * (`wear-design-system` SKILL.md components 4.7/4.11), and separates the two conditions that
 * banner used to share one orange tone for: Offline uses `SlateMist` + `ic_wifi_off` (4.11),
 * Error uses `WarningOchre` + `ic_warning` (4.7). `[Reintentar]`/`[Cancelar]` reuse the same
 * bordered-text button pattern already established by `InsightCard`'s `[Reintentar]` action,
 * instead of the loose `fontSize`/`CircleShape` combination the old banner used.
 */
@Composable
private fun DestinationPickerFallback(
    isOffline: Boolean,
    message: String,
    stepTitle: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmbient = LocalAmbientMode.current
    val accentColor = if (isOffline) WakeSyncColors.SlateMist else WakeSyncColors.WarningOchre

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmbient) WakeSyncColors.PureBlack else WakeSyncColors.BlueBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = WakeSyncSpacing.xxl)
        ) {
            CompactNotice(
                icon = painterResource(if (isOffline) R.drawable.ic_wifi_off else R.drawable.ic_warning),
                text = stepTitle,
                accentColor = accentColor
            )
            Spacer(modifier = Modifier.height(WakeSyncSpacing.sm))
            Text(
                text = message,
                style = WakeSyncTextStyles.Body,
                color = WakeSyncColors.CreamSoft,
                textAlign = TextAlign.Center,
                maxLines = 3
            )

            if (!isAmbient) {
                Spacer(modifier = Modifier.height(WakeSyncSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(WakeSyncSpacing.sm)) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.sizeIn(
                            minWidth = WakeSyncSpacing.minTouchTarget,
                            minHeight = WakeSyncSpacing.minTouchTarget
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.BlueCard,
                            contentColor = WakeSyncColors.CreamSoft
                        ),
                        border = BorderStroke(WakeSyncSpacing.borderHairline, accentColor)
                    ) {
                        Text(text = stringResource(R.string.btn_retry), style = WakeSyncTextStyles.Label)
                    }
                    Button(
                        onClick = onExit,
                        modifier = Modifier.sizeIn(
                            minWidth = WakeSyncSpacing.minTouchTarget,
                            minHeight = WakeSyncSpacing.minTouchTarget
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WakeSyncColors.BlueCard,
                            contentColor = WakeSyncColors.CreamSoft
                        )
                    ) {
                        Text(text = stringResource(R.string.btn_cancel), style = WakeSyncTextStyles.Label)
                    }
                }
            }
        }
    }
}

/**
 * Root navigation host and state orchestrator for WakeSync on Wear OS.
 *
 * Implements:
 * - Reactive routing driven by [WakeSyncState.sessionType].
 * - The Buscar/Mapa/Confirmar Destino flow (CR-01) as a plain `when` over
 *   [DestinationPickerState] rather than a nested SwipeDismissableNavHost: the contract is
 *   already linear (Idle -> Loading -> Results/Map -> Confirm) and Offline/Error have no
 *   screen identity of their own, so a parallel back stack would desync from the StateFlow.
 * - The Resumen Post-Sesión screen, shown once the just-ended session's [SessionRecord] is
 *   confirmed present in [SessionHistoryRepository.sessionHistory] (avoids racing the async
 *   DataStore write in SessionManager.endSession()).
 * - Historial -> Detalle (RF-INS-03): Detalle reads the record live from
 *   [SessionHistoryRepository.sessionHistory] by id, so an insight generated there shows up
 *   through the persisted [SessionRecord.insightText]; see [UiFormatters.resolveDetailInsightState]
 *   for why the shared contract's Ready is not trusted on that screen.
 * - F17: the destination flow can be opened on top of an active Siesta (NapScreen's directions
 *   chip); confirming there calls requestStartSession(TRANSIT, destination), which raises
 *   [ConflictDialog] instead of replacing the nap.
 * - Transversal priority layers for [ActiveAlertOverlay] and [ConflictDialog].
 */
@Composable
fun WakeSyncNavHost(
    state: WakeSyncState,
    sessionManager: SessionManager,
    historyRepository: SessionHistoryRepository,
    onRequestPermissions: () -> Unit,
    onSimulateNap: () -> Unit,
    onSimulateRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isHistoryOpen by remember { mutableStateOf(false) }
    var selectedHistoryRecordId by remember { mutableStateOf<String?>(null) }
    var detailRequestedForId by remember { mutableStateOf<String?>(null) }

    // --- Buscar/Mapa/Confirmar Destino flow (CR-01) ---
    var destinationPickerFor by remember { mutableStateOf<SessionType?>(null) }
    var pickerStep by remember { mutableStateOf(PickerStep.SEARCH) }

    // --- Resumen Post-Sesión con Insight (CR-01) ---
    val history by historyRepository.sessionHistory.collectAsState(initial = emptyList())
    var preEndTopRecordId by remember { mutableStateOf(UiFormatters.NO_PRIOR_RECORD_ID) }
    var summaryRecord by remember { mutableStateOf<SessionRecord?>(null) }
    var dismissedRecordId by remember { mutableStateOf<String?>(null) }
    var requestedInsightForId by remember { mutableStateOf<String?>(null) }

    val justEndedSession = state.sessionType == SessionType.NONE &&
        (state.napState.phase != NapPhase.IDLE || state.transitState.phase != TransitPhase.IDLE)

    // While the session is still active, keep tracking whatever the newest record is — this is
    // the baseline resolveJustEndedSessionRecord() will compare against once the session ends.
    // Capturing this baseline only when justEndedSession flips to true (at the closing edge)
    // would race SessionManager.endSession()'s async DataStore write: if that write lands first,
    // the baseline would equal the new record's own id and the summary would never appear.
    LaunchedEffect(history, justEndedSession) {
        if (!justEndedSession) {
            preEndTopRecordId = UiFormatters.mostRecentSessionRecord(history)?.id ?: UiFormatters.NO_PRIOR_RECORD_ID
            summaryRecord = null
        }
    }

    // Resolve the actual new record once SessionManager's async persist makes it visible here.
    LaunchedEffect(history, justEndedSession) {
        if (justEndedSession && summaryRecord == null) {
            summaryRecord = UiFormatters.resolveJustEndedSessionRecord(history, preEndTopRecordId)
        }
    }

    val recordForSummary = summaryRecord?.takeIf { it.id != dismissedRecordId }

    val insightContract = InsightProvider.get()
    val fallbackInsightStateFlow = remember { MutableStateFlow<InsightState>(InsightState.Idle) }
    val insightState by (insightContract?.state ?: fallbackInsightStateFlow).collectAsState()

    LaunchedEffect(recordForSummary?.id) {
        val record = recordForSummary
        if (record != null && requestedInsightForId != record.id) {
            requestedInsightForId = record.id
            insightContract?.requestInsight(record.startTimestamp)
        }
    }

    // Gate the raw InsightState: InsightProvider's contract is a long-lived singleton that
    // keeps its last Ready(text) around, so between recordForSummary appearing and its
    // requestInsight() call actually landing, insightState could still be the PREVIOUS
    // session's cached Ready(...) rather than Idle/Loading — show Loading until the request
    // for this exact record has actually been made.
    val gatedInsightState = if (recordForSummary != null && requestedInsightForId == recordForSummary.id) {
        insightState
    } else {
        InsightState.Loading
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Base Navigation Layer
        when {
            // Checked before the session branches on purpose (F17): the Transporte flow can be
            // opened from an active Siesta, and must show over NapScreen until it is confirmed or
            // dismissed. From Inicio no session is running, so the order changes nothing there.
            destinationPickerFor != null -> {
                val pickerContract = DestinationSearchProvider.get()
                val fallbackPickerStateFlow = remember { MutableStateFlow<DestinationPickerState>(DestinationPickerState.Idle) }
                val pickerState by (pickerContract?.state ?: fallbackPickerStateFlow).collectAsState()

                fun exitPicker() {
                    pickerContract?.reset()
                    destinationPickerFor = null
                    pickerStep = PickerStep.SEARCH
                }

                BackHandler { exitPicker() }

                val stepTitle = when (pickerStep) {
                    PickerStep.SEARCH -> stringResource(R.string.title_dest_search)
                    PickerStep.MAP -> stringResource(R.string.title_dest_map)
                }

                DismissibleScreen(onBack = { exitPicker() }) {
                    when (val pickerStateValue = pickerState) {
                        DestinationPickerState.Idle, is DestinationPickerState.Results -> {
                            DestinationSearchScreen(
                                state = pickerStateValue,
                                onSearch = { query -> pickerContract?.search(query) },
                                onSelectPrediction = { placeId -> pickerContract?.selectPrediction(placeId) },
                                onOpenMap = {
                                    pickerStep = PickerStep.MAP
                                    pickerContract?.openMap(null)
                                }
                            )
                        }

                        DestinationPickerState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().background(WakeSyncColors.BlueBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressArc(
                                    progress = 0.35f,
                                    color = WakeSyncColors.RoseGoldPastel,
                                    trackColor = WakeSyncColors.RoseGoldPastelTrack,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }

                        is DestinationPickerState.Map -> {
                            DestinationMapScreen(
                                image = pickerStateValue.image,
                                onPan = { dx, dy -> pickerContract?.panMap(dx, dy) },
                                onZoom = { delta -> pickerContract?.zoomMap(delta) },
                                onPinCenter = { pickerContract?.pinMapCenter() }
                            )
                        }

                        is DestinationPickerState.Confirm -> {
                            ConfirmDestinationScreen(
                                destination = pickerStateValue.destination,
                                address = pickerStateValue.address,
                                straightLineMeters = pickerStateValue.straightLineMeters,
                                onConfirm = {
                                    val forType = destinationPickerFor
                                    val chosenDestination = pickerStateValue.destination
                                    // Reset after confirming too, not just on cancel — otherwise this
                                    // Confirm state reappears next time the picker is opened.
                                    pickerContract?.reset()
                                    destinationPickerFor = null
                                    pickerStep = PickerStep.SEARCH
                                    if (forType != null) {
                                        sessionManager.requestStartSession(forType, chosenDestination)
                                    }
                                },
                                onCancel = { exitPicker() }
                            )
                        }

                        DestinationPickerState.Offline -> {
                            DestinationPickerFallback(
                                isOffline = true,
                                message = stringResource(R.string.dest_offline_msg),
                                stepTitle = stepTitle,
                                onRetry = { pickerContract?.retry() },
                                onExit = { exitPicker() }
                            )
                        }

                        is DestinationPickerState.Error -> {
                            DestinationPickerFallback(
                                isOffline = false,
                                message = pickerStateValue.message,
                                stepTitle = stepTitle,
                                onRetry = { pickerContract?.retry() },
                                onExit = { exitPicker() }
                            )
                        }
                    }
                }
            }

            state.sessionType == SessionType.NAP -> {
                // Active session: swipe is absorbed here (never reaches the system-level
                // dismiss) but deliberately does nothing (enabled = false) — a stray edge
                // swipe must not end an in-progress Siesta.
                DismissibleScreen(onBack = {}, enabled = false) {
                    NapScreen(
                        state = state,
                        onStopSession = { sessionManager.endSession(SessionOutcome.CANCELLED) },
                        onSimulateNap = onSimulateNap,
                        onRequestTransit = {
                            DestinationSearchProvider.get()?.reset()
                            pickerStep = PickerStep.SEARCH
                            destinationPickerFor = SessionType.TRANSIT
                        }
                    )
                }
            }

            state.sessionType == SessionType.TRANSIT -> {
                DismissibleScreen(onBack = {}, enabled = false) {
                    TransitScreen(
                        state = state,
                        onStopSession = { sessionManager.endSession(SessionOutcome.CANCELLED) },
                        onSimulateRoute = onSimulateRoute
                    )
                }
            }

            isSettingsOpen -> {
                DismissibleScreen(onBack = { isSettingsOpen = false }) {
                    SettingsScreen(
                        state = state,
                        onClearHistory = {
                            coroutineScope.launch {
                                historyRepository.clearHistory()
                            }
                        },
                        onRequestPermissions = onRequestPermissions,
                        onToggleSimulation = { enabled ->
                            sessionManager.setSimulationMode(enabled)
                        },
                        onBack = { isSettingsOpen = false }
                    )
                }
            }

            isHistoryOpen -> {
                val detailRecord = selectedHistoryRecordId?.let { id -> history.find { it.id == id } }
                if (detailRecord != null) {
                    val closeDetail = { selectedHistoryRecordId = null }
                    DismissibleScreen(onBack = closeDetail) {
                        SessionDetailScreen(
                            record = detailRecord,
                            insightState = UiFormatters.resolveDetailInsightState(
                                record = detailRecord,
                                requestedForId = detailRequestedForId,
                                contractState = insightState
                            ),
                            onGenerateInsight = {
                                detailRequestedForId = detailRecord.id
                                insightContract?.requestInsight(detailRecord.startTimestamp)
                            },
                            onRetryInsight = {
                                detailRequestedForId = detailRecord.id
                                insightContract?.requestInsight(detailRecord.startTimestamp)
                            },
                            onBack = closeDetail
                        )
                    }
                } else {
                    val closeHistory = {
                        isHistoryOpen = false
                        selectedHistoryRecordId = null
                    }
                    DismissibleScreen(onBack = closeHistory) {
                        HistoryScreen(
                            history = history,
                            onOpenRecord = { id -> selectedHistoryRecordId = id },
                            onBack = closeHistory
                        )
                    }
                }
            }

            recordForSummary != null -> {
                val onBackToHome = {
                    dismissedRecordId = recordForSummary.id
                    // Persist the dismissal in core state: `remember` alone is lost when the
                    // Activity is recreated, which re-opened this summary with no way out (F10)
                    sessionManager.acknowledgeSessionEnd()
                }
                DismissibleScreen(onBack = onBackToHome) {
                    SessionSummaryScreen(
                        record = recordForSummary,
                        insightState = gatedInsightState,
                        onRetryInsight = { insightContract?.retry() },
                        onBackToHome = onBackToHome
                    )
                }
            }

            else -> {
                HomeScreen(
                    state = state,
                    onStartNap = {
                        sessionManager.requestStartSession(SessionType.NAP, null)
                    },
                    onOpenDestinationSearch = { forNap ->
                        DestinationSearchProvider.get()?.reset()
                        pickerStep = PickerStep.SEARCH
                        destinationPickerFor = if (forNap) SessionType.NAP else SessionType.TRANSIT
                    },
                    onOpenHistory = { isHistoryOpen = true },
                    onOpenSettings = { isSettingsOpen = true }
                )
            }
        }

        // Transversal Layer 1: Conflict Resolution Dialog (RF-CORE-02)
        val conflict = state.pendingConflict
        if (conflict != null) {
            ConflictDialog(
                conflict = conflict,
                onResolve = { proceedWithNew ->
                    sessionManager.resolveConflict(proceedWithNew)
                }
            )
        }

        // Transversal Layer 2: Active Alert Overlay (Highest Z-Order priority)
        if (state.activeAlertLevel != AlertLevel.NONE) {
            ActiveAlertOverlay(
                alertLevel = state.activeAlertLevel,
                onDismiss = {
                    sessionManager.cancelAlert()
                }
            )
        }
    }
}
