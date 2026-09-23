package com.wakesync.alerts

import android.os.VibrationEffect
import com.wakesync.core.model.AlertLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HapticVibrationController] validating waveform specifications,
 * state transitions, emulator fallback behavior, and auto-reset lifecycle.
 */
class HapticVibrationControllerTest {

    private class MockVibratorActuator(
        private val amplitudeControlSupported: Boolean = true
    ) : VibratorActuator {
        var cancelCount = 0
        var lastVibratedEffect: VibrationEffect? = null

        override fun hasAmplitudeControl(): Boolean = amplitudeControlSupported

        override fun vibrate(effect: VibrationEffect) {
            lastVibratedEffect = effect
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private lateinit var mockActuator: MockVibratorActuator
    private lateinit var controller: HapticVibrationController

    @Before
    fun setUp() {
        mockActuator = MockVibratorActuator(amplitudeControlSupported = true)
        controller = HapticVibrationController(mockActuator)
    }

    @Test
    fun waveformConstants_matchHapticWaveformSpecExactly() {
        // Nivel 1 (Suave): Pre-aviso / confirmación de reposo, patrón tipo latido
        assertArrayEquals(longArrayOf(0, 150, 600, 150, 600), HapticVibrationController.TIMINGS_LEVEL_1)
        assertArrayEquals(intArrayOf(0, 60, 0, 90, 0), HapticVibrationController.AMPLITUDES_LEVEL_1)
        assertEquals(-1, HapticVibrationController.REPEAT_LEVEL_1)
        assertEquals(1500L, HapticVibrationController.DURATION_LEVEL_1_MS)

        // Nivel 2 (Moderado): Llegada al umbral de geocerca, patrón rítmico ascendente
        assertArrayEquals(longArrayOf(0, 300, 300, 300, 300, 400), HapticVibrationController.TIMINGS_LEVEL_2)
        assertArrayEquals(intArrayOf(0, 140, 0, 180, 0, 220), HapticVibrationController.AMPLITUDES_LEVEL_2)
        assertEquals(-1, HapticVibrationController.REPEAT_LEVEL_2)
        assertEquals(1600L, HapticVibrationController.DURATION_LEVEL_2_MS)

        // Nivel 3 (Urgente): Fin de siesta / alerta crítica, doble pulso repetitivo
        assertArrayEquals(longArrayOf(0, 200, 100, 200, 500), HapticVibrationController.TIMINGS_LEVEL_3)
        assertArrayEquals(intArrayOf(0, 255, 0, 255, 0), HapticVibrationController.AMPLITUDES_LEVEL_3)
        assertEquals(0, HapticVibrationController.REPEAT_LEVEL_3)
    }

    @Test
    fun triggerAlert_updatesActiveAlertLevelFlow() {
        assertEquals(AlertLevel.NONE, controller.activeAlertLevel.value)

        controller.triggerAlert(AlertLevel.SOFT)
        assertEquals(AlertLevel.SOFT, controller.activeAlertLevel.value)

        controller.triggerAlert(AlertLevel.MODERATE)
        assertEquals(AlertLevel.MODERATE, controller.activeAlertLevel.value)

        controller.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, controller.activeAlertLevel.value)
    }

    @Test
    fun cancelAlert_stopsVibrationAndResetsStateToNone() {
        controller.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, controller.activeAlertLevel.value)

        controller.cancelAlert()
        assertEquals(AlertLevel.NONE, controller.activeAlertLevel.value)
        assertTrue("cancel() must be called on the actuator", mockActuator.cancelCount > 0)
    }

    @Test
    fun triggerAlert_withNone_invokesCancelAlert() {
        controller.triggerAlert(AlertLevel.MODERATE)
        assertEquals(AlertLevel.MODERATE, controller.activeAlertLevel.value)

        controller.triggerAlert(AlertLevel.NONE)
        assertEquals(AlertLevel.NONE, controller.activeAlertLevel.value)
        assertTrue("cancel() must be called on the actuator", mockActuator.cancelCount > 0)
    }

    @Test
    fun amplitudeFallback_whenNoAmplitudeControl_initializesAndTriggersCleanly() {
        val noAmpActuator = MockVibratorActuator(amplitudeControlSupported = false)
        val fallbackController = HapticVibrationController(noAmpActuator)

        fallbackController.triggerAlert(AlertLevel.SOFT)
        assertEquals(AlertLevel.SOFT, fallbackController.activeAlertLevel.value)

        fallbackController.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, fallbackController.activeAlertLevel.value)

        fallbackController.cancelAlert()
        assertEquals(AlertLevel.NONE, fallbackController.activeAlertLevel.value)
        assertTrue(noAmpActuator.cancelCount > 0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun triggerAlert_soft_autoResetsToNoneAfterPatternDuration() = runTest {
        val testController = HapticVibrationController(mockActuator, backgroundScope)
        testController.triggerAlert(AlertLevel.SOFT)
        assertEquals(AlertLevel.SOFT, testController.activeAlertLevel.value)

        // Advance time right before pattern completion (1499ms)
        advanceTimeBy(HapticVibrationController.DURATION_LEVEL_1_MS - 1)
        runCurrent()
        assertEquals(AlertLevel.SOFT, testController.activeAlertLevel.value)

        // Advance to pattern completion (1500ms) and drain ready queue
        advanceTimeBy(1)
        runCurrent()
        assertEquals(AlertLevel.NONE, testController.activeAlertLevel.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun triggerAlert_moderate_autoResetsToNoneAfterPatternDuration() = runTest {
        val testController = HapticVibrationController(mockActuator, backgroundScope)
        testController.triggerAlert(AlertLevel.MODERATE)
        assertEquals(AlertLevel.MODERATE, testController.activeAlertLevel.value)

        // Advance time right before pattern completion (1599ms)
        advanceTimeBy(HapticVibrationController.DURATION_LEVEL_2_MS - 1)
        runCurrent()
        assertEquals(AlertLevel.MODERATE, testController.activeAlertLevel.value)

        // Advance to pattern completion (1600ms) and drain ready queue
        advanceTimeBy(1)
        runCurrent()
        assertEquals(AlertLevel.NONE, testController.activeAlertLevel.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun triggerAlert_urgent_doesNotAutoReset() = runTest {
        val testController = HapticVibrationController(mockActuator, backgroundScope)
        testController.triggerAlert(AlertLevel.URGENT)
        assertEquals(AlertLevel.URGENT, testController.activeAlertLevel.value)

        // Advance time far into the future (10 minutes)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals("URGENT must remain active indefinitely until explicit user dismissal", AlertLevel.URGENT, testController.activeAlertLevel.value)

        testController.cancelAlert()
        assertEquals(AlertLevel.NONE, testController.activeAlertLevel.value)
    }
}
