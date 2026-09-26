package com.wakesync

import android.content.Context
import android.util.Log
import com.wakesync.ai.RestEstimatorEngine
import com.wakesync.ai.RestEvaluationResult
import com.wakesync.alerts.HapticVibrationController
import com.wakesync.core.alerts.AlertControllerProvider
import com.wakesync.core.model.BiometricMetrics
import com.wakesync.core.model.GeoPoint
import com.wakesync.core.model.NapPhase
import com.wakesync.core.model.RestState
import com.wakesync.core.model.SessionOutcome
import com.wakesync.core.model.SessionType
import com.wakesync.core.model.TransitPhase
import com.wakesync.core.service.WakeSyncForegroundService
import com.wakesync.core.session.SessionManager
import com.wakesync.sensors.SensorRepository
import com.wakesync.sensors.mock.MockSensorEngine
import com.wakesync.sensors.real.RealSensorSource
import com.wakesync.sleep.NapManager
import com.wakesync.transit.TransitManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

import com.wakesync.core.session.SimulationControllerContract

/**
 * Coordination Architecture Component — Assigned Owner: platform-core-engineer.
 *
 * Location: com.wakesync (Root package / Neutral Composition Root).
 * Precedent: WakeSyncApplication.kt, GeofenceCalculator, AlertControllerContract.
 *
 * Responsibilities:
 * - Assembles module boundaries: sensors, ai, sleep, transit, alerts, core.
 * - Injects concrete implementations into NapManager and TransitManager.
 * - Observes SessionManager.state to orchestrate foreground service and manager lifecycles.
 * - Bridges simulation triggers ([Simulate Nap] / [Simulate Route]) with MockSensorEngine.
 * - Relays continuous biometric metrics into SessionManager for UI consumption.
 */
class AppSessionCoordinator(
    private val context: Context,
    val sessionManager: SessionManager = SessionManager.getInstance(context),
    val mockSensorEngine: MockSensorEngine = MockSensorEngine(),
    val realSensorSource: RealSensorSource = RealSensorSource(context),
    val sensorRepository: SensorRepository = SensorRepository(realSensorSource, mockSensorEngine),
    val restEstimatorEngine: RestEstimatorEngine = RestEstimatorEngine(sensorRepository),
    val hapticController: HapticVibrationController = HapticVibrationController(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SimulationControllerContract {

    companion object {
        private const val TAG = "AppSessionCoordinator"
        private const val SIMULATED_NAP_RAMP_SECONDS = 10

        @Volatile
        private var instance: AppSessionCoordinator? = null

        fun getInstance(context: Context): AppSessionCoordinator {
            return instance ?: synchronized(this) {
                instance ?: AppSessionCoordinator(context.applicationContext).also { instance = it }
            }
        }

        /**
         * F24: resolves the [RestState] to publish from a raw [RestEvaluationResult]. Gates
         * `result.isCalibrating` (a raw engine signal, true whenever HR is fresh but there's no
         * basal HR yet — regardless of session type or phase) on the caller's own session state,
         * so only a Nap session actually in [NapPhase.CALIBRATING] can show `CALIBRATING`. A
         * Transit session (no calibration phase, F8) or a Nap already in MONITORING after a
         * zero-reading calibration (NapManager's defensive-fallback path, F18) fall through to
         * `SENSOR_UNAVAILABLE` instead of getting stuck showing "Calibrando" forever. Pure and
         * side-effect-free so it can be unit tested without a Context.
         */
        internal fun resolveRestState(
            result: RestEvaluationResult,
            sessionType: SessionType,
            napPhase: NapPhase
        ): RestState {
            val isNapCalibrating = sessionType == SessionType.NAP && napPhase == NapPhase.CALIBRATING
            return when {
                result.isDataValid -> result.state
                result.isCalibrating && isNapCalibrating -> RestState.CALIBRATING
                else -> RestState.SENSOR_UNAVAILABLE
            }
        }

        /**
         * F26: suspends until ⚡ has been tapped ([trigger]) AND the nap has left
         * [NapPhase.CALIBRATING], in either order. Keeps the F18 guarantee (the simulated descent
         * never bleeds into the calibration window) while letting ⚡ be tapped at any time.
         * Pure and Context-free so it can be unit tested.
         */
        internal suspend fun awaitNapRampGate(trigger: Deferred<Unit>, napPhase: Flow<NapPhase>) {
            trigger.await()
            napPhase.first { it != NapPhase.CALIBRATING }
        }
    }

    val napManager: NapManager = NapManager(
        sessionManager = sessionManager,
        restEvaluationFlow = restEstimatorEngine.evaluationResult,
        heartRateFlow = sensorRepository.getHeartRate(),
        locationFlow = sensorRepository.getLocation(),
        alertController = hapticController,
        scope = scope,
        onBaseHeartRateCalibrated = restEstimatorEngine::setBaseHeartRate
    )

    val transitManager: TransitManager = TransitManager(
        sessionManager = sessionManager,
        locationFlow = sensorRepository.getLocation(),
        alertController = hapticController,
        scope = scope
    )

    private var sessionObserverJob: Job? = null
    private var biometricsRelayJob: Job? = null
    private var simulationModeRelayJob: Job? = null
    private var currentActiveType: SessionType = SessionType.NONE

    /** F26: completed by ⚡ to release the simulated HR descent; null outside a simulated nap. */
    @Volatile
    private var napRampTrigger: CompletableDeferred<Unit>? = null

    fun start() {
        Log.i(TAG, "Initializing AppSessionCoordinator and registering haptics contract")
        AlertControllerProvider.register(hapticController)
        sessionManager.registerAlertController(hapticController)
        sessionManager.registerSimulationController(this)

        startSimulationModeRelay()
        startBiometricsRelay()
        startSessionObserver()
    }

    /**
     * F9: sessionManager.state.isSimulated (toggled by the Settings switch and by
     * startSimulateNap/startSimulateRoute below) is the single source of truth for whether
     * sensorRepository should read from MockSensorEngine or RealSensorSource. Without this relay,
     * sensorRepository's own flag was only ever set to true and never back to false, so any
     * session after the first simulation — even a real, non-simulated one — kept reading mock
     * data regardless of the Settings toggle.
     */
    private fun startSimulationModeRelay() {
        simulationModeRelayJob?.cancel()
        simulationModeRelayJob = scope.launch {
            sessionManager.state.map { it.isSimulated }.distinctUntilChanged().collect { isSimulated ->
                sensorRepository.setSimulated(isSimulated)
            }
        }
    }

    private fun startBiometricsRelay() {
        biometricsRelayJob?.cancel()
        biometricsRelayJob = scope.launch {
            // Relay HR
            launch {
                sensorRepository.getHeartRate().collect { hr ->
                    val prev = sessionManager.state.value.biometricMetrics
                    sessionManager.updateBiometrics(prev.copy(currentHeartRate = hr))
                }
            }

            // Relay Motion
            launch {
                sensorRepository.getMotionSvm().collect { svm ->
                    val prev = sessionManager.state.value.biometricMetrics
                    sessionManager.updateBiometrics(prev.copy(motionSvm = svm))
                }
            }

            // Relay RestEstimatorEngine evaluation result
            launch {
                restEstimatorEngine.evaluationResult.collect { result ->
                    val currentState = sessionManager.state.value
                    sessionManager.updateBiometrics(
                        currentState.biometricMetrics.copy(
                            restScore = result.score,
                            restState = resolveRestState(
                                result = result,
                                sessionType = currentState.sessionType,
                                napPhase = currentState.napState.phase
                            )
                        )
                    )
                }
            }
        }
    }

    private fun startSessionObserver() {
        sessionObserverJob?.cancel()
        sessionObserverJob = scope.launch {
            sessionManager.state.collectLatest { state ->
                when (state.sessionType) {
                    SessionType.NAP -> {
                        if (currentActiveType != SessionType.NAP) {
                            currentActiveType = SessionType.NAP
                            Log.i(TAG, "Starting Nap session lifecycle in background")
                            WakeSyncForegroundService.startService(
                                context,
                                WakeSyncForegroundService.ACTION_START_NAP
                            )
                            restEstimatorEngine.start(scope)
                            if (state.isSimulated) startSimulatedNapBaseline()
                            napManager.startSession(state.napState.destination)
                        }
                    }

                    SessionType.TRANSIT -> {
                        if (currentActiveType != SessionType.TRANSIT) {
                            val dest = state.transitState.destination
                            if (dest == null) {
                                // RF-TRAN-01: transit never starts without a user-confirmed destination
                                Log.w(TAG, "Transit session requested without a confirmed destination; cancelling")
                                sessionManager.endSession(SessionOutcome.CANCELLED)
                                return@collectLatest
                            }
                            currentActiveType = SessionType.TRANSIT
                            Log.i(TAG, "Starting Transit session lifecycle")
                            WakeSyncForegroundService.startService(
                                context,
                                WakeSyncForegroundService.ACTION_START_TRANSIT
                            )
                            restEstimatorEngine.start(scope)
                            transitManager.startSession(dest)
                        }
                    }

                    SessionType.NONE -> {
                        if (currentActiveType != SessionType.NONE) {
                            Log.i(TAG, "Stopping active session managers and simulation")
                            currentActiveType = SessionType.NONE
                            napManager.stopSession()
                            transitManager.stopSession()
                            napRampTrigger = null
                            mockSensorEngine.stopSimulation()
                            restEstimatorEngine.stop()
                        }
                    }
                }
            }
        }
    }

    /**
     * F26: with Simulation Mode on, the mock holds HR at [MockSensorEngine.NAP_START_HR] (and
     * motion at rest) from the moment the nap starts, so the 20 s calibration always sees
     * simulated readings regardless of when ⚡ is tapped. ⚡ only releases the descent.
     */
    private fun startSimulatedNapBaseline() {
        val trigger = CompletableDeferred<Unit>()
        napRampTrigger = trigger
        Log.i(TAG, "Simulation Mode on: holding simulated baseline until ⚡ releases the descent")
        mockSensorEngine.startNapSimulation(
            scope,
            durationSeconds = SIMULATED_NAP_RAMP_SECONDS,
            baseHr = MockSensorEngine.NAP_START_HR,
            awaitRampStart = { awaitNapRampGate(trigger, sessionManager.state.map { it.napState.phase }) }
        )
    }

    /**
     * ⚡ "start descent" (F26). If the simulated baseline is already running (Simulation Mode was
     * on when the nap started), only releases the HR descent. Otherwise falls back to starting
     * the whole simulation, gated on calibration having closed (F18).
     */
    override fun startSimulateNap() {
        Log.i(TAG, "Simulate Nap triggered: activating MockSensorEngine")
        sessionManager.setSimulationMode(true)
        val trigger = napRampTrigger
        if (trigger != null) {
            trigger.complete(Unit)
            return
        }
        // 10 s ramp so DEEP_REST is reachable within the 30 s acceptance window (Section 9); the
        // ramp only starts once calibration has closed (F18/B2a).
        mockSensorEngine.startNapSimulation(
            scope,
            durationSeconds = SIMULATED_NAP_RAMP_SECONDS,
            baseHr = MockSensorEngine.NAP_START_HR,
            awaitRampStart = { sessionManager.state.first { it.napState.phase != NapPhase.CALIBRATING } }
        )
    }

    /**
     * Triggers deterministic [Simulate Route] synthetic GPS protocol.
     */
    override fun startSimulateRoute(destination: GeoPoint?) {
        val target = destination ?: sessionManager.state.value.transitState.destination
        if (target == null) {
            // RF-SIM-02: the route simulation only approaches the destination the user chose
            Log.w(TAG, "Simulate Route ignored: no confirmed destination")
            return
        }
        Log.i(TAG, "Simulate Route triggered: activating MockSensorEngine")
        sessionManager.setSimulationMode(true)
        mockSensorEngine.startRouteSimulation(scope, target, durationSeconds = 60)
    }
}
