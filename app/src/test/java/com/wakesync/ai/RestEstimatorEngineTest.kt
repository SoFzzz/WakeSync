package com.wakesync.ai

import com.wakesync.core.model.RestState
import com.wakesync.sensors.mock.MockSensorEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.system.measureNanoTime

@OptIn(ExperimentalCoroutinesApi::class)
class RestEstimatorEngineTest {

    private lateinit var mockSensorEngine: MockSensorEngine
    private lateinit var engine: RestEstimatorEngine

    @Before
    fun setUp() {
        mockSensorEngine = MockSensorEngine()
        engine = RestEstimatorEngine(mockSensorEngine)
    }

    @Test
    fun `mathematical formula calculates score and quietude exactly`() {
        // Base HR = 80, Current HR = 60 -> deltaHr = (80 - 60) / 80 = 0.25
        // Mean SVM = 1.25 -> quietude = 1 - (1.25 / 2.5) = 0.5
        // Expected score = 0.5 * 0.25 + 0.5 * 0.5 = 0.125 + 0.25 = 0.375
        val result = engine.calculateScoreAndState(
            baseHr = 80,
            currentHr = 60,
            meanSvm = 1.25f,
            previousDeepRestCount = 0
        )

        assertEquals(0.25f, result.deltaHrRelative, 0.0001f)
        assertEquals(0.5f, result.quietude, 0.0001f)
        assertEquals(0.375f, result.score, 0.0001f)
        assertEquals(RestState.LIGHT_REST, result.state)
        assertTrue(result.isDataValid)
    }

    @Test
    fun `score below 0_30 classifies as AWAKE`() {
        // Base HR = 70, Current HR = 70 -> deltaHr = 0
        // SVM = 2.0 -> quietude = 1 - (2.0 / 2.5) = 0.20
        // Score = 0.5 * 0 + 0.5 * 0.20 = 0.10 (< 0.30)
        val result = engine.calculateScoreAndState(
            baseHr = 70,
            currentHr = 70,
            meanSvm = 2.0f,
            previousDeepRestCount = 0
        )

        assertEquals(0.10f, result.score, 0.0001f)
        assertEquals(RestState.AWAKE, result.state)
        assertEquals(0, result.consecutiveDeepRestCount)
    }

    @Test
    fun `score between 0_30 and 0_60 classifies as LIGHT_REST`() {
        // Base HR = 75, Current HR = 65 -> deltaHr = 10 / 75 = 0.1333
        // SVM = 1.0 -> quietude = 1 - (1.0 / 2.5) = 0.60
        // Score = 0.5 * 0.1333 + 0.5 * 0.60 = 0.0667 + 0.30 = 0.3667
        val result = engine.calculateScoreAndState(
            baseHr = 75,
            currentHr = 65,
            meanSvm = 1.0f,
            previousDeepRestCount = 0
        )

        assertTrue(result.score in 0.30f..0.599f)
        assertEquals(RestState.LIGHT_REST, result.state)
        assertEquals(0, result.consecutiveDeepRestCount)
    }

    @Test
    fun `score at or above 0_60 requires 2 consecutive cycles for DEEP_REST`() {
        // High rest conditions:
        // Base HR = 75, Current HR = 58 -> deltaHr = 17 / 75 = 0.2267
        // SVM = 0.04 -> quietude = 1 - (0.04 / 2.5) = 0.984
        // Score = 0.5 * 0.2267 + 0.5 * 0.984 = 0.1133 + 0.492 = 0.6053 (>= 0.60)

        // 1st evaluation with score >= 0.60
        val eval1 = engine.calculateScoreAndState(
            baseHr = 75,
            currentHr = 58,
            meanSvm = 0.04f,
            previousDeepRestCount = 0
        )
        assertTrue(eval1.score >= 0.60f)
        assertEquals(1, eval1.consecutiveDeepRestCount)
        assertEquals(RestState.LIGHT_REST, eval1.state) // Not yet DEEP_REST on first cycle!

        // 2nd consecutive evaluation with score >= 0.60
        val eval2 = engine.calculateScoreAndState(
            baseHr = 75,
            currentHr = 58,
            meanSvm = 0.04f,
            previousDeepRestCount = eval1.consecutiveDeepRestCount
        )
        assertTrue(eval2.score >= 0.60f)
        assertEquals(2, eval2.consecutiveDeepRestCount)
        assertEquals(RestState.DEEP_REST, eval2.state) // DEEP_REST confirmed!

        // Evaluation below 0.60 resets consecutive counter to 0 immediately
        val eval3 = engine.calculateScoreAndState(
            baseHr = 75,
            currentHr = 72,
            meanSvm = 1.5f,
            previousDeepRestCount = eval2.consecutiveDeepRestCount
        )
        assertTrue(eval3.score < 0.60f)
        assertEquals(0, eval3.consecutiveDeepRestCount)
        assertEquals(RestState.AWAKE, eval3.state)
    }

    @Test
    fun `steady-state mock nap score clears DEEP_REST threshold for the realistic calibration range`() {
        // F18: a calibration that races MockSensorEngine's activation can land below the
        // NAP_START_HR of 75 (seen as low as 71 BPM). NAP_TARGET_HR (52) and NAP_TARGET_SVM must
        // keep score_reposo >= 0.60 for any base HR in that realistic range, or DEEP_REST becomes
        // permanently unreachable once HR/SVM settle at their steady-state floor. The exact floor is
        // 67 BPM (score 0.6039); 66 falls just short (0.5981) and is deliberately excluded here.
        listOf(67, 71, 75).forEach { baseHr ->
            val result = engine.calculateScoreAndState(
                baseHr = baseHr,
                currentHr = MockSensorEngine.NAP_TARGET_HR,
                meanSvm = MockSensorEngine.NAP_TARGET_SVM,
                previousDeepRestCount = 0
            )
            assertTrue(
                "Steady-state score for baseHr=$baseHr must be >= 0.60: actual=${result.score}",
                result.score >= 0.60f
            )
        }
    }

    @Test
    fun `physiological bounds enforce invalid data when HR is outside 35 to 220 BPM`() {
        val lowHr = engine.calculateScoreAndState(
            baseHr = 70,
            currentHr = 30, // Below 35 BPM
            meanSvm = 0.1f,
            previousDeepRestCount = 0
        )
        assertFalse(lowHr.isDataValid)

        val highHr = engine.calculateScoreAndState(
            baseHr = 70,
            currentHr = 230, // Above 220 BPM
            meanSvm = 0.1f,
            previousDeepRestCount = 0
        )
        assertFalse(highHr.isDataValid)
    }

    @Test
    fun `negative motion is clamped to zero`() {
        val result = engine.calculateScoreAndState(
            baseHr = 70,
            currentHr = 70,
            meanSvm = -1.5f,
            previousDeepRestCount = 0
        )
        assertEquals(1.0f, result.quietude, 0.0001f) // 1 - min(1, 0.0 / 2.5) = 1.0
        assertTrue(result.isDataValid)
    }

    @Test
    fun `data is valid only after HR_base is set and stop clears it for the next session`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val timedEngine = RestEstimatorEngine(mockSensorEngine, dispatcher) { testScheduler.currentTime }

        timedEngine.start(backgroundScope)
        mockSensorEngine.emitDirect(hr = 60, svm = 0.0f)
        assertFalse("Without HR_base the cycle must be invalid", timedEngine.evaluateCurrentCycle().isDataValid)

        timedEngine.setBaseHeartRate(75)
        assertTrue("With HR_base and fresh HR the cycle must be valid", timedEngine.evaluateCurrentCycle().isDataValid)

        timedEngine.stop()
        assertFalse("stop() must reset the published result", timedEngine.evaluationResult.value.isDataValid)
        assertEquals(0, timedEngine.evaluationResult.value.consecutiveDeepRestCount)

        timedEngine.start(backgroundScope)
        mockSensorEngine.emitDirect(hr = 60, svm = 0.0f)
        assertFalse("A new session must not reuse the previous HR_base", timedEngine.evaluateCurrentCycle().isDataValid)
        timedEngine.stop()
    }

    @Test
    fun `inference cycle executes in less than 5 milliseconds`() = runTest {
        val baseHr = 75
        val currentHr = 60
        val meanSvm = 0.5f

        // Warm up JIT
        repeat(100) {
            engine.calculateScoreAndState(baseHr, currentHr, meanSvm, 0)
        }

        val elapsedNanos = measureNanoTime {
            engine.calculateScoreAndState(baseHr, currentHr, meanSvm, 0)
        }
        val elapsedMs = elapsedNanos / 1_000_000.0

        assertTrue("Evaluation took $elapsedMs ms which is >= 5.0 ms", elapsedMs < 5.0)
    }
}
