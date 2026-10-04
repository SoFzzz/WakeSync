package com.wakesync

import android.content.Context
import android.util.Log
import com.wakesync.ai.RestEstimatorEngine
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
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

        @Volatile
        private var instance: AppSessionCoordinator? = null

        fun getInstance(context: Context): AppSessionCoordinator {
            return instance ?: synchronized(this) {
                instance ?: AppSessionCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    val napManager: NapManager = NapManager(
        sessionManager = sessionManager,
        restEvaluationFlow = restEstimatorEngine.evaluationResult,
        heartRateFlow = sensorRepository.getHeartRate(),
        locationFlow = sensorRepository.getLocation(),
        alertController = hapticController,
        scope = scope
    )

    val transitManager: TransitManager = TransitManager(
        sessionManager = sessionManager,
        locationFlow = sensorRepository.getLocation(),
        alertController = hapticController,
        scope = scope
    )

    private var sessionObserverJob: Job? = null
    private var biometricsRelayJob: Job? = null
    private var currentActiveType: SessionType = SessionType.NONE

    fun start() {
        Log.i(TAG, "Initializing AppSessionCoordinator and registering haptics contract")
        AlertControllerProvider.register(hapticController)
        sessionManager.registerAlertController(hapticController)
        sessionManager.registerSimulationController(this)

        startBiometricsRelay()
        startSessionObserver()
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
                    val prev = sessionManager.state.value.biometricMetrics
                    sessionManager.updateBiometrics(
                        prev.copy(
                            restScore = result.score,
                            restState = if (result.isDataValid) result.state else RestState.SENSOR_UNAVAILABLE
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
                            mockSensorEngine.stopSimulation()
                            restEstimatorEngine.stop()
                        }
                    }
                }
            }
        }
    }

    /**
     * Triggers deterministic [Simulate Nap] synthetic sensor protocol.
     */
    override fun startSimulateNap() {
        Log.i(TAG, "Simulate Nap triggered: activating MockSensorEngine")
        sessionManager.setSimulationMode(true)
        sensorRepository.setSimulated(true)
        mockSensorEngine.startNapSimulation(scope, durationSeconds = 60, baseHr = 75)
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
        sensorRepository.setSimulated(true)
        mockSensorEngine.startRouteSimulation(scope, target, durationSeconds = 60)
    }
}
