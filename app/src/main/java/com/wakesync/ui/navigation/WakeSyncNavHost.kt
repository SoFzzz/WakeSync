package com.wakesync.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.wakesync.ui.components.ActiveAlertOverlay
import com.wakesync.ui.components.CircularProgressArc
import com.wakesync.ui.components.ConflictDialog
import com.wakesync.ui.components.PickerFallbackBanner
import com.wakesync.ui.format.UiFormatters
import com.wakesync.ui.screens.ConfirmDestinationScreen
import com.wakesync.ui.screens.DestinationMapScreen
import com.wakesync.ui.screens.DestinationSearchScreen
import com.wakesync.ui.screens.HomeScreen
import com.wakesync.ui.screens.NapScreen
import com.wakesync.ui.screens.SessionSummaryScreen
import com.wakesync.ui.screens.SettingsScreen
import com.wakesync.ui.screens.TransitScreen
import com.wakesync.ui.theme.WakeSyncColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Which step of the Buscar/Mapa Destino flow the user was on before an Offline/Error fallback. */
private enum class PickerStep { SEARCH, MAP }

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
            state.sessionType == SessionType.NAP -> {
                NapScreen(
                    state = state,
                    onStopSession = { sessionManager.endSession(SessionOutcome.CANCELLED) },
                    onSimulateNap = onSimulateNap
                )
            }

            state.sessionType == SessionType.TRANSIT -> {
                TransitScreen(
                    state = state,
                    onStopSession = { sessionManager.endSession(SessionOutcome.CANCELLED) },
                    onSimulateRoute = onSimulateRoute
                )
            }

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

                when (val pickerStateValue = pickerState) {
                    DestinationPickerState.Idle, is DestinationPickerState.Results -> {
                        DestinationSearchScreen(
                            state = pickerStateValue,
                            onSearch = { query -> pickerContract?.search(query) },
                            onSelectPrediction = { placeId -> pickerContract?.selectPrediction(placeId) },
                            onOpenMap = {
                                pickerStep = PickerStep.MAP
                                pickerContract?.openMap(null)
                            },
                            onExit = { exitPicker() }
                        )
                    }

                    DestinationPickerState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(WakeSyncColors.PureBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressArc(
                                progress = 0.35f,
                                color = WakeSyncColors.SageTeal,
                                trackColor = WakeSyncColors.SageTealMuted,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    is DestinationPickerState.Map -> {
                        DestinationMapScreen(
                            image = pickerStateValue.image,
                            onPan = { dx, dy -> pickerContract?.panMap(dx, dy) },
                            onZoom = { delta -> pickerContract?.zoomMap(delta) },
                            onPinCenter = { pickerContract?.pinMapCenter() },
                            onExit = { exitPicker() }
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
                        PickerFallbackBanner(
                            message = stringResource(R.string.dest_offline_msg),
                            stepTitle = stepTitle,
                            onRetry = { pickerContract?.retry() },
                            onExit = { exitPicker() }
                        )
                    }

                    is DestinationPickerState.Error -> {
                        PickerFallbackBanner(
                            message = pickerStateValue.message,
                            stepTitle = stepTitle,
                            onRetry = { pickerContract?.retry() },
                            onExit = { exitPicker() }
                        )
                    }
                }
            }

            isSettingsOpen -> {
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

            recordForSummary != null -> {
                SessionSummaryScreen(
                    record = recordForSummary,
                    insightState = gatedInsightState,
                    onRetryInsight = { insightContract?.retry() },
                    onBackToHome = {
                        dismissedRecordId = recordForSummary.id
                        // Persist the dismissal in core state: `remember` alone is lost when the
                        // Activity is recreated, which re-opened this summary with no way out (F10)
                        sessionManager.acknowledgeSessionEnd()
                    }
                )
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
