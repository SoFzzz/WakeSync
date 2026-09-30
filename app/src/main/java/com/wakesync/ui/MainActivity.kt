package com.wakesync.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.session.SessionManager
import com.wakesync.ui.navigation.WakeSyncNavHost
import com.wakesync.ui.theme.WakeSyncTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main entrance ComponentActivity for WakeSync on Wear OS.
 *
 * Responsibilities:
 * - Solicits runtime permissions on startup via [ActivityResultContracts.RequestMultiplePermissions] (RF-CORE-04).
 * - Manages [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] during active haptic alert levels.
 * - Subscribes to central StateFlow<WakeSyncState> from [SessionManager].
 */
class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var historyRepository: SessionHistoryRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        sessionManager.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager.getInstance(applicationContext)
        historyRepository = SessionHistoryRepository(applicationContext)

        observeAlertStateForScreenWake()
        requestInitialPermissionsIfNeeded()

        setContent {
            val state by sessionManager.state.collectAsState()

            WakeSyncTheme {
                WakeSyncNavHost(
                    state = state,
                    sessionManager = sessionManager,
                    historyRepository = historyRepository,
                    onRequestPermissions = { launchPermissions() },
                    onSimulateNap = {
                        // Point of coordination with platform-core-engineer & sensor-ai-engineer
                        sessionManager.setSimulationMode(true)
                    },
                    onSimulateRoute = {
                        // Point of coordination with platform-core-engineer & sensor-ai-engineer
                        sessionManager.setSimulationMode(true)
                    }
                )
            }
        }
    }

    private fun requestInitialPermissionsIfNeeded() {
        val missing = sessionManager.state.value.missingPermissions
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun launchPermissions() {
        val missing = sessionManager.state.value.missingPermissions
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Prevents screen dimming or timeout during active alerts (SOFT, MODERATE, URGENT).
     * Releases FLAG_KEEP_SCREEN_ON once alertLevel returns to NONE.
     */
    private fun observeAlertStateForScreenWake() {
        lifecycleScope.launch {
            sessionManager.state.collectLatest { state ->
                if (state.activeAlertLevel != AlertLevel.NONE) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager.refreshPermissions()
    }
}
