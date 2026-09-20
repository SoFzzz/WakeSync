package com.wakesync.sensors

import com.wakesync.core.model.GeoPoint
import com.wakesync.sensors.mock.MockSensorEngine
import com.wakesync.sensors.real.RealSensorSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Repository and dynamic switcher (RF-SENS-06) that routes sensor streams between
 * real hardware [RealSensorSource] and simulated data [MockSensorEngine] without restarting the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepository(
    val realSensorSource: SensorSource,
    val mockSensorEngine: MockSensorEngine
) : SensorSource {

    private val _isSimulated = MutableStateFlow(false)
    val isSimulated: StateFlow<Boolean> = _isSimulated.asStateFlow()

    /**
     * RF-SENS-06: Toggles between real sensors and MockSensorEngine at runtime.
     */
    fun setSimulated(simulated: Boolean) {
        _isSimulated.value = simulated
    }

    override fun getHeartRate(): Flow<Int> =
        _isSimulated.flatMapLatest { simulated ->
            if (simulated) mockSensorEngine.getHeartRate() else realSensorSource.getHeartRate()
        }

    override fun getMotionSvm(): Flow<Float> =
        _isSimulated.flatMapLatest { simulated ->
            if (simulated) mockSensorEngine.getMotionSvm() else realSensorSource.getMotionSvm()
        }

    override fun getLocation(): Flow<GeoPoint> =
        _isSimulated.flatMapLatest { simulated ->
            if (simulated) mockSensorEngine.getLocation() else realSensorSource.getLocation()
        }
}
