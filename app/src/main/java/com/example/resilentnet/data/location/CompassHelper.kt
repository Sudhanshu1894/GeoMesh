package com.example.resilentnet.data.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class MapOrientationMode(val label: String) {
    NORTH_UP("🧭 N-Up"),
    HEADING_UP("🧭 Heading")
}

/**
 * Manages device magnetometer and accelerometer sensors to compute smooth compass azimuth.
 * 0° = North, 90° = East, 180° = South, 270° = West.
 */
class CompassHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _azimuthDegrees = MutableStateFlow(0f)
    val azimuthDegrees: StateFlow<Float> = _azimuthDegrees.asStateFlow()

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var smoothedAzimuth = 0f

    fun start() {
        if (sensorManager == null) return

        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
            if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalizedDeg = (rawDeg + 360f) % 360f
                applyLowPassFilter(normalizedDeg)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity)
                hasGravity = true
                computeOrientationIfReady()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPass(event.values, geomagnetic)
                hasGeomagnetic = true
                computeOrientationIfReady()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun computeOrientationIfReady() {
        if (hasGravity && hasGeomagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalizedDeg = (rawDeg + 360f) % 360f
                applyLowPassFilter(normalizedDeg)
            }
        }
    }

    /**
     * Circular low-pass filter to smooth angle transitions across 0°/360° boundary
     */
    private fun applyLowPassFilter(newDeg: Float) {
        var diff = newDeg - smoothedAzimuth
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f

        // Alpha = 0.2f provides smooth interpolation without perceptible lag
        val alpha = 0.2f
        smoothedAzimuth = (smoothedAzimuth + alpha * diff + 360f) % 360f
        _azimuthDegrees.value = smoothedAzimuth
    }

    private fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.25f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }

    companion object {
        fun formatHeading(degrees: Float): String {
            val deg = ((degrees % 360f) + 360f) % 360f
            val cardinal = when (deg) {
                in 337.5..360.0, in 0.0..22.5 -> "N"
                in 22.5..67.5 -> "NE"
                in 67.5..112.5 -> "E"
                in 112.5..157.5 -> "SE"
                in 157.5..202.5 -> "S"
                in 202.5..247.5 -> "SW"
                in 247.5..292.5 -> "W"
                in 292.5..337.5 -> "NW"
                else -> "N"
            }
            return "%03d° %s".format(deg.toInt(), cardinal)
        }
    }
}
