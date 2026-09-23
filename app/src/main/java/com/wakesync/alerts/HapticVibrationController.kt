package com.wakesync.alerts

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.wakesync.core.alerts.AlertControllerContract
import com.wakesync.core.model.AlertLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Internal interface abstracting hardware vibration actuation for pure JVM unit testability.
 */
internal interface VibratorActuator {
    fun hasAmplitudeControl(): Boolean
    fun vibrate(effect: VibrationEffect)
    fun cancel()
}

/**
 * Controller responsible for progressive LRA haptic vibration waveforms in WakeSync (RF-ALRT-01).
 *
 * Implements [AlertControllerContract] to integrate reactively with com.wakesync.core without
 * architectural coupling. Formas de onda pre-cacheadas para garantizar latencia de despacho < 20 ms.
 *
 * @param actuator Low-level vibration actuation mechanism.
 * @param scope CoroutineScope managing auto-reset timers for one-shot patterns (repeat = -1).
 */
class HapticVibrationController internal constructor(
    private val actuator: VibratorActuator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : AlertControllerContract {

    /**
     * Primary constructor resolving the appropriate [Vibrator] based on Android API level.
     */
    constructor(
        context: Context,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(SystemVibratorActuator(getVibrator(context)), scope)

    /**
     * Secondary constructor wrapping an existing [Vibrator] instance.
     */
    constructor(
        vibrator: Vibrator,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(SystemVibratorActuator(vibrator), scope)

    private val _activeAlertLevel = MutableStateFlow(AlertLevel.NONE)
    override val activeAlertLevel: StateFlow<AlertLevel> = _activeAlertLevel.asStateFlow()

    private var autoResetJob: Job? = null

    // Pre-cached waveforms for zero-allocation dispatch
    private val effectLevel1: VibrationEffect? by lazy {
        createEffect(TIMINGS_LEVEL_1, AMPLITUDES_LEVEL_1, REPEAT_LEVEL_1)
    }
    private val effectLevel2: VibrationEffect? by lazy {
        createEffect(TIMINGS_LEVEL_2, AMPLITUDES_LEVEL_2, REPEAT_LEVEL_2)
    }
    private val effectLevel3: VibrationEffect? by lazy {
        createEffect(TIMINGS_LEVEL_3, AMPLITUDES_LEVEL_3, REPEAT_LEVEL_3)
    }

    init {
        // Eagerly initialize waveforms to guarantee sub-millisecond dispatch latency (<20ms target)
        effectLevel1
        effectLevel2
        effectLevel3
    }

    companion object {
        private const val TAG = "HapticVibrationController"
        private const val TARGET_DISPATCH_LATENCY_MS = 20L

        // Normative LRA waveforms from haptic-waveform-spec (Appendix E)
        val TIMINGS_LEVEL_1 = longArrayOf(0, 150, 600, 150, 600)
        val AMPLITUDES_LEVEL_1 = intArrayOf(0, 60, 0, 90, 0)
        const val REPEAT_LEVEL_1 = -1
        val DURATION_LEVEL_1_MS: Long = TIMINGS_LEVEL_1.sum()

        val TIMINGS_LEVEL_2 = longArrayOf(0, 300, 300, 300, 300, 400)
        val AMPLITUDES_LEVEL_2 = intArrayOf(0, 140, 0, 180, 0, 220)
        const val REPEAT_LEVEL_2 = -1
        val DURATION_LEVEL_2_MS: Long = TIMINGS_LEVEL_2.sum()

        val TIMINGS_LEVEL_3 = longArrayOf(0, 200, 100, 200, 500)
        val AMPLITUDES_LEVEL_3 = intArrayOf(0, 255, 0, 255, 0)
        const val REPEAT_LEVEL_3 = 0

        /**
         * Resolves the system vibrator supporting dual API levels (API 31+ VibratorManager / API 30 Vibrator).
         */
        private fun getVibrator(context: Context): Vibrator {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
                    ?: (@Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
    }

    private class SystemVibratorActuator(private val vibrator: Vibrator) : VibratorActuator {
        override fun hasAmplitudeControl(): Boolean = vibrator.hasAmplitudeControl()
        override fun vibrate(effect: VibrationEffect) = vibrator.vibrate(effect)
        override fun cancel() = vibrator.cancel()
    }

    /**
     * Creates a [VibrationEffect] waveform, applying fallback if amplitude control is not supported.
     */
    private fun createEffect(timings: LongArray, amplitudes: IntArray, repeat: Int): VibrationEffect? {
        return try {
            if (actuator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, repeat)
            } else {
                // PHASE2_HARDWARE_REQUIRED: Physical LRA motor amplitude control
                // Fallback: binary on/off waveform preserving exact timings and repeat behavior
                VibrationEffect.createWaveform(timings, repeat)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create VibrationEffect waveform: ${e.message}")
            null
        }
    }

    /**
     * Dispatches the progressive haptic alert corresponding to the specified [level].
     *
     * Despacho con latencia objetivo < 20 ms. Si se invoca con [AlertLevel.NONE], cancela la alerta activa.
     * Para patrones de un solo ciclo (SOFT y MODERATE, repeat = -1), programa la transición automática
     * de activeAlertLevel a NONE al concluir la duración del patrón.
     */
    override fun triggerAlert(level: AlertLevel) {
        autoResetJob?.cancel()

        if (level == AlertLevel.NONE) {
            cancelAlert()
            return
        }

        val startTimeNanos = System.nanoTime()

        val effect = when (level) {
            AlertLevel.SOFT -> effectLevel1
            AlertLevel.MODERATE -> effectLevel2
            AlertLevel.URGENT -> effectLevel3
            AlertLevel.NONE -> null
        }

        effect?.let {
            try {
                actuator.vibrate(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch vibration for level $level: ${e.message}", e)
            }
        }

        _activeAlertLevel.value = level

        // Schedule automatic reset to NONE when one-shot patterns (repeat = -1) conclude
        when (level) {
            AlertLevel.SOFT -> {
                autoResetJob = scope.launch {
                    delay(DURATION_LEVEL_1_MS)
                    _activeAlertLevel.value = AlertLevel.NONE
                    Log.d(TAG, "Auto-reset alert level SOFT to NONE after ${DURATION_LEVEL_1_MS}ms")
                }
            }
            AlertLevel.MODERATE -> {
                autoResetJob = scope.launch {
                    delay(DURATION_LEVEL_2_MS)
                    _activeAlertLevel.value = AlertLevel.NONE
                    Log.d(TAG, "Auto-reset alert level MODERATE to NONE after ${DURATION_LEVEL_2_MS}ms")
                }
            }
            AlertLevel.URGENT, AlertLevel.NONE -> {
                // URGENT uses repeat = 0 and requires explicit cancelAlert(); NONE is idle
            }
        }

        val elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000.0
        Log.i(
            TAG,
            "Dispatched alert level $level in ${String.format(Locale.US, "%.2f", elapsedMs)}ms " +
                "(target <${TARGET_DISPATCH_LATENCY_MS}ms)"
        )
    }

    /**
     * Immediately halts active vibration waveforms, cancels auto-reset jobs, and resets [activeAlertLevel] to [AlertLevel.NONE].
     */
    override fun cancelAlert() {
        autoResetJob?.cancel()
        try {
            actuator.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel vibration: ${e.message}", e)
        }
        _activeAlertLevel.value = AlertLevel.NONE
        Log.i(TAG, "Active alert cancelled and vibration stopped")
    }
}
