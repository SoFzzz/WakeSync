package com.wakesync.core.session

import android.content.Context
import android.util.Log
import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.data.SessionHistoryRepository
import com.wakesync.core.model.AlertLevel
import com.wakesync.core.model.BiometricMetrics
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.NapSessionState
import com.wakesync.core.model.SessionConflict
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionRecord
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.model.TransitSessionState
import com.wakesync.core.model.WakeSyncState
import com.wakesync.core.permission.PermissionManager
import com.wakesync.core.service.WakeLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central coordinator of session lifecycle, reactive state, and mutual exclusion (RF-CORE-02).
 *
 * Exposes the immutable StateFlow<WakeSyncState> consumed by all other modules.
 */
class SessionManager(
    private val context: Context? = null,
    private val wakeLockManager: WakeLockManager? = context?.let { WakeLockManager(it) },
    private val historyRepository: SessionHistoryRepository? = context?.let { SessionHistoryRepository(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val _state = MutableStateFlow(WakeSyncState())
    val state: StateFlow<WakeSyncState> = _state.asStateFlow()

    private var sessionStartTimestamp: Long = 0L
    private var alertController: AlertControllerContract? = null
    private var alertCollectionJob: Job? = null

    init {
        refreshPermissions()
    }

    /**
     * Refreshes the missingPermissions field in WakeSyncState using core's internal PermissionManager.
     * Allows com.wakesync.ui to observe permission status without importing PermissionManager.
     */
    fun refreshPermissions() {
        val missing = context?.let { PermissionManager.getMissingPermissions(it) } ?: emptyList()
        _state.update { it.copy(missingPermissions = missing) }
    }

    companion object {
        private const val TAG = "SessionManager"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        internal fun resetInstanceForTesting() {
            instance = null
        }
    }

    /**
     * Requests starting a new session. Enforces mutual exclusion (RF-CORE-02).
     *
     * @return True if session started immediately, false if a conflict requires user resolution.
     */
    fun requestStartSession(type: SessionType, destination: GeoPoint? = null): Boolean {
        val currentType = _state.value.sessionType
        Log.d(TAG, "requestStartSession called: requested=$type, current=$currentType, destination=$destination")

        // Idempotency: If already running this exact session type, return true without reinitializing
        if (currentType == type && type != SessionType.NONE) {
            Log.i(TAG, "Session of type $type is already active. Ignoring redundant start request.")
            return true
        }

        if (currentType != SessionType.NONE) {
            Log.w(TAG, "Conflict detected: current session is $currentType, requested $type")
            _state.update { it.copy(pendingConflict = SessionConflict(currentType, type)) }
            return false
        }

        startSessionInternal(type, destination)
        return true
    }

    /**
     * Resolves an active mutual exclusion conflict.
     */
    fun resolveConflict(proceedWithNew: Boolean) {
        val conflict = _state.value.pendingConflict ?: return
        Log.d(TAG, "resolveConflict called: proceedWithNew=$proceedWithNew, conflict=$conflict")
        _state.update { it.copy(pendingConflict = null) }

        if (proceedWithNew) {
            endSession(SessionOutcome.CANCELLED)
            startSessionInternal(conflict.requestedSession, null)
        }
    }

    private fun startSessionInternal(type: SessionType, destination: GeoPoint?) {
        sessionStartTimestamp = System.currentTimeMillis()
        wakeLockManager?.acquireWakeLock()
        alertController?.cancelAlert()

        _state.update {
            it.copy(
                sessionType = type,
                pendingConflict = null,
                activeAlertLevel = AlertLevel.NONE,
                napState = if (type == SessionType.NAP) NapSessionState(
                    phase = NapPhase.CALIBRATING,
                    destination = destination
                ) else NapSessionState(),
                transitState = if (type == SessionType.TRANSIT) TransitSessionState(
                    phase = TransitPhase.TRACKING,
                    destination = destination
                ) else TransitSessionState()
            )
        }
        Log.d(TAG, "WakeSyncState phase changed: sessionType=$type, napPhase=${_state.value.napState.phase}, transitPhase=${_state.value.transitState.phase}")
        Log.i(TAG, "Started session of type: $type")
    }

    /**
     * Public signature for mode-flow-engineer to report the end of an active session.
     * Releases the wake lock, records the session in DataStore, and updates the state.
     *
     * @param outcome Termination outcome of the session.
     * @param stopActiveAlert True to immediately halt active vibration (e.g. CANCELLED or URGENT dismissal).
     *                        False to allow non-repeating one-shot waveforms (SOFT, MODERATE) to finish naturally.
     */
    fun endSession(outcome: SessionOutcome, stopActiveAlert: Boolean = true) {
        val currentState = _state.value
        if (currentState.sessionType == SessionType.NONE) return

        if (stopActiveAlert) {
            alertController?.cancelAlert()
        }

        val durationSeconds = ((System.currentTimeMillis() - sessionStartTimestamp) / 1000).toInt()
        val record = SessionRecord(
            sessionType = currentState.sessionType,
            startTimestamp = sessionStartTimestamp,
            durationSeconds = durationSeconds,
            restLatencySeconds = currentState.napState.restLatencySeconds,
            outcome = outcome
        )

        wakeLockManager?.releaseWakeLock()

        scope.launch {
            try {
                historyRepository?.recordSession(record)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist session record: ${e.message}", e)
            }
        }

        _state.update {
            it.copy(
                sessionType = SessionType.NONE,
                activeAlertLevel = AlertLevel.NONE,
                napState = it.napState.copy(
                    phase = when (outcome) {
                        SessionOutcome.COMPLETED -> NapPhase.COMPLETED
                        SessionOutcome.TIMED_OUT -> NapPhase.TIMED_OUT
                        SessionOutcome.CANCELLED -> NapPhase.CANCELLED
                        SessionOutcome.INTERRUPTED_BY_ARRIVAL -> NapPhase.COMPLETED
                    }
                ),
                transitState = it.transitState.copy(
                    phase = when (outcome) {
                        SessionOutcome.COMPLETED, SessionOutcome.INTERRUPTED_BY_ARRIVAL -> TransitPhase.COMPLETED
                        else -> TransitPhase.CANCELLED
                    }
                )
            )
        }
        Log.d(TAG, "WakeSyncState phase changed on endSession: outcome=$outcome, newNapPhase=${_state.value.napState.phase}, newTransitPhase=${_state.value.transitState.phase}")
        Log.i(TAG, "Ended session with outcome: $outcome, duration: ${durationSeconds}s")
    }

    /**
     * Updates biometric telemetry. Called by sensor-ai-engineer.
     */
    fun updateBiometrics(metrics: BiometricMetrics) {
        _state.update { it.copy(biometricMetrics = metrics) }
    }

    /**
     * Updates nap progression parameters. Called by mode-flow-engineer.
     */
    fun updateNapProgress(
        phase: NapPhase,
        elapsedSeconds: Int,
        remainingNapSeconds: Int,
        restLatencySeconds: Int?,
        distanceToDestinationMeters: Float? = null
    ) {
        _state.update {
            it.copy(
                napState = it.napState.copy(
                    phase = phase,
                    elapsedSeconds = elapsedSeconds,
                    remainingNapSeconds = remainingNapSeconds,
                    restLatencySeconds = restLatencySeconds,
                    distanceToDestinationMeters = distanceToDestinationMeters
                )
            )
        }
        Log.d(TAG, "WakeSyncState nap phase changed to $phase (elapsed=${elapsedSeconds}s, remaining=${remainingNapSeconds}s)")
    }

    /**
     * Updates transit progression parameters. Called by mode-flow-engineer.
     */
    fun updateTransitProgress(
        phase: TransitPhase,
        currentDistanceMeters: Float?,
        dynamicAlertRadiusMeters: Float,
        estimatedSpeedMps: Float?
    ) {
        _state.update {
            it.copy(
                transitState = it.transitState.copy(
                    phase = phase,
                    currentDistanceMeters = currentDistanceMeters,
                    dynamicAlertRadiusMeters = dynamicAlertRadiusMeters,
                    estimatedSpeedMps = estimatedSpeedMps
                )
            )
        }
        Log.d(TAG, "WakeSyncState transit phase changed to $phase (distance=${currentDistanceMeters}m, radius=${dynamicAlertRadiusMeters}m)")
    }

    /**
     * Updates the active haptic alert level.
     */
    fun setAlertLevel(level: AlertLevel) {
        _state.update { it.copy(activeAlertLevel = level) }
    }

    /**
     * Registers the haptic alert controller contract.
     * Cancels any prior collector Job to avoid duplicated state listeners.
     */
    fun registerAlertController(controller: AlertControllerContract) {
        if (this.alertController === controller && alertCollectionJob?.isActive == true) {
            Log.d(TAG, "AlertController already registered and active. Skipping.")
            return
        }

        alertCollectionJob?.cancel()
        this.alertController = controller

        alertCollectionJob = scope.launch {
            controller.activeAlertLevel.collect { level ->
                setAlertLevel(level)
            }
        }
        Log.i(TAG, "AlertController registered successfully")
    }

    /**
     * Cancels any active haptic vibration.
     *
     * Delegates solely to [AlertControllerContract.cancelAlert]. The controller's
     * StateFlow emits [AlertLevel.NONE], which the reactive collector propagates
     * to WakeSyncState, avoiding duplicate write paths (wakesync-architecture).
     */
    fun cancelAlert() {
        alertController?.cancelAlert()
    }

    private var simulationController: SimulationControllerContract? = null

    /**
     * Registers the simulation controller implementation (RF-SENS-06).
     */
    fun registerSimulationController(controller: SimulationControllerContract) {
        this.simulationController = controller
        Log.i(TAG, "SimulationController registered successfully")
    }

    /**
     * Sets simulation mode flag.
     */
    fun setSimulationMode(isSimulated: Boolean) {
        _state.update { it.copy(isSimulated = isSimulated) }
    }

    /**
     * Triggers simulated nap execution via registered controller.
     */
    fun startSimulateNap() {
        setSimulationMode(true)
        simulationController?.startSimulateNap()
    }

    /**
     * Triggers simulated route execution via registered controller.
     */
    fun startSimulateRoute(destination: GeoPoint? = null) {
        setSimulationMode(true)
        simulationController?.startSimulateRoute(destination)
    }
}
