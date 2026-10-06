package com.wakesync.sensors.real

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wakesync.core.model.GeoPoint
import com.wakesync.sensors.SensorSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Real sensor integration with Wear OS hardware:
 * - Heart Rate: Health Services MeasureClient (PPG >= 0.5 Hz).
 * - Accelerometer: SensorManager triaxial accelerometer (20 Hz, SVM computation).
 * - Location: FusedLocationProviderClient with adaptive intervals (RF-SENS-03) and accuracy filter (RF-SENS-05).
 */
class RealSensorSource(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SensorSource {

    companion object {
        private const val TAG = "RealSensorSource"

        // Accelerometer sampling: 20 Hz = 50,000 microseconds
        private const val ACCELEROMETER_SAMPLING_PERIOD_US = 50_000

        // RF-SENS-03: Adaptive GPS update intervals
        const val GPS_INTERVAL_FAR_MS = 10_000L    // Distance > 2 km
        const val GPS_INTERVAL_MID_MS = 5_000L     // 1 km < Distance <= 2 km
        const val GPS_INTERVAL_NEAR_MS = 3_000L    // Distance <= 1 km

        // RF-SENS-05: Maximum acceptable GPS accuracy in meters
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 100.0f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val measureClient: MeasureClient? = try {
        // PHASE2_HARDWARE_REQUIRED: Wear OS Health Services client requires Wear OS runtime
        HealthServices.getClient(context).measureClient
    } catch (e: Exception) {
        Log.w(TAG, "HealthServices MeasureClient unavailable: ${e.message}")
        null
    }

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _isSkinContactPresent = MutableStateFlow(true)
    val isSkinContactPresent: StateFlow<Boolean> = _isSkinContactPresent.asStateFlow()

    private var currentGpsIntervalMs = GPS_INTERVAL_FAR_MS
    private var locationCallback: LocationCallback? = null
    private var lastValidLocation: GeoPoint? = null

    /**
     * RF-SENS-01: Heart rate flow via Health Services MeasureClient (>= 0.5 Hz).
     * RF-SENS-04: If skin contact is lost, stops emitting valid values to trigger stale watchdog.
     */
    override fun getHeartRate(): Flow<Int> = callbackFlow {
        if (measureClient == null) {
            // PHASE2_HARDWARE_REQUIRED: Emulator fallback if HealthServices client is absent
            Log.w(TAG, "HealthServices absent; getHeartRate will not emit hardware values")
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                if (dataType == DataType.HEART_RATE_BPM) {
                    val hasContact = availability == DataTypeAvailability.AVAILABLE
                    _isSkinContactPresent.value = hasContact
                    if (!hasContact) {
                        Log.w(TAG, "PPG skin contact lost or sensor unavailable: $availability")
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRatePoints = data.getData(DataType.HEART_RATE_BPM)
                for (point in heartRatePoints) {
                    val bpm = point.value.roundToInt()
                    if (_isSkinContactPresent.value) {
                        trySend(bpm)
                    }
                }
            }
        }

        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BODY_SENSORS permission for MeasureClient: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MeasureCallback: ${e.message}")
        }

        awaitClose {
            try {
                // PHASE2_HARDWARE_REQUIRED: Unregister callback via reflection to avoid
                // compile-time dependency on com.google.common.util.concurrent.ListenableFuture
                val method = measureClient.javaClass.methods.firstOrNull {
                    it.name == "unregisterMeasureCallbackAsync" && it.parameterTypes.size == 2
                }
                method?.invoke(measureClient, DataType.HEART_RATE_BPM, callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering MeasureCallback: ${e.message}")
            }
        }
    }

    /**
     * RF-SENS-02: Accelerometer SVM flow sampled at 20 Hz.
     * SVM = |sqrt(x² + y² + z²) - 9.80665|
     */
    override fun getMotionSvm(): Flow<Float> = callbackFlow {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accelerometer == null) {
            Log.w(TAG, "Accelerometer sensor unavailable")
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val totalMagnitude = sqrt(x * x + y * y + z * z)
                // Dynamic SVM: subtract 1g (Earth gravity) to isolate dynamic user motion
                val dynamicSvm = max(0.0f, abs(totalMagnitude - SensorManager.GRAVITY_EARTH))
                trySend(dynamicSvm)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No action needed for accelerometer accuracy adjustments
            }
        }

        val registered = sensorManager.registerListener(
            listener,
            accelerometer,
            ACCELEROMETER_SAMPLING_PERIOD_US
        )
        if (!registered) {
            Log.w(TAG, "Failed to register accelerometer listener at 20 Hz")
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * RF-SENS-03 & RF-SENS-05: GPS updates with accuracy filtering (>100m discarded)
     * and adaptive update intervals.
     */
    @SuppressLint("MissingPermission")
    override fun getLocation(): Flow<GeoPoint> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return

                // RF-SENS-05: Accuracy check
                if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
                    Log.w(TAG, "Location accuracy poor (${location.accuracy} m > 100 m); retaining last valid")
                    lastValidLocation?.let { trySend(it) }
                    return
                }

                val geoPoint = GeoPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    name = null
                )
                lastValidLocation = geoPoint
                trySend(geoPoint)
            }
        }
        locationCallback = callback

        startLocationUpdates(currentGpsIntervalMs, callback)

        awaitClose {
            locationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }

    /**
     * RF-SENS-03: Adapts the location polling interval based on straight-line distance to destination.
     */
    @SuppressLint("MissingPermission")
    fun updateLocationInterval(distanceToDestinationMeters: Float?) {
        val newIntervalMs = when {
            distanceToDestinationMeters == null || distanceToDestinationMeters > 2000.0f -> GPS_INTERVAL_FAR_MS
            distanceToDestinationMeters > 1000.0f -> GPS_INTERVAL_MID_MS
            else -> GPS_INTERVAL_NEAR_MS
        }

        if (newIntervalMs != currentGpsIntervalMs) {
            Log.i(TAG, "Adapting GPS interval: $currentGpsIntervalMs ms -> $newIntervalMs ms")
            currentGpsIntervalMs = newIntervalMs
            locationCallback?.let { callback ->
                startLocationUpdates(newIntervalMs, callback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(intervalMs: Long, callback: LocationCallback) {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .build()
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates: ${e.message}")
        }
    }
}
