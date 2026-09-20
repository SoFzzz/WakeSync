package com.wakesync.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.WakeSyncState
import com.wakesync.core.session.SessionManager
import com.wakesync.ui.components.ActiveAlertOverlay
import com.wakesync.ui.components.ConflictDialog
import com.wakesync.ui.screens.HomeScreen
import com.wakesync.ui.screens.NapScreen
import com.wakesync.ui.screens.SettingsScreen
import com.wakesync.ui.screens.TransitScreen
import kotlinx.coroutines.launch

/**
 * Root navigation host and state orchestrator for WakeSync on Wear OS.
 *
 * Implements:
 * - Reactive routing driven by [WakeSyncState.sessionType].
 * - Transversal priority layers for [ActiveAlertOverlay] and [ConflictDialog].
 * - Decoupled action dispatching to [SessionManager] and [SessionHistoryRepository].
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

            else -> {
                HomeScreen(
                    state = state,
                    onStartNap = { dest ->
                        sessionManager.requestStartSession(SessionType.NAP, dest)
                    },
                    onStartTransit = { dest ->
                        sessionManager.requestStartSession(SessionType.TRANSIT, dest)
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
