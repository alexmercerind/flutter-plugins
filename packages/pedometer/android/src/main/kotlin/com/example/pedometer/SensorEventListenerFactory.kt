package com.example.pedometer

import kotlin.math.sqrt
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import io.flutter.plugin.common.EventChannel
import com.github.psambit9791.jdsp.filter.Butterworth
import android.content.Context
import android.util.Log

fun defaultSensorEventListener(
    context: Context,
    events: EventChannel.EventSink
): SensorEventListener {
    return object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val stepCount = event.values[0].toInt()
            events.success(stepCount)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }
}

fun fallbackStepCounterSensorEventListener(
    context: Context,
    events: EventChannel.EventSink
): SensorEventListener {
    return object : SensorEventListener {
        private val bufferSize: Int = 100
        private var arrayBuffer: DoubleArray = DoubleArray(bufferSize)
        private val c = 1.05
        private val threshold = 1.2
        private var tick = 0

        @Volatile
        private var stepCount = 0

        private fun filterBuffer() {
            val samplingFreq = 50.0
            val order = 20
            val cutoff = 0.2 * (samplingFreq * 0.5)
            val filter = Butterworth(arrayBuffer, samplingFreq)
            arrayBuffer = filter.lowPassFilter(order, cutoff)
        }

        // Adapted from Mladenov, M., & Mock, M. (2009).
        // A step counter service for Java-enabled devices using a built-in accelerometer.
        private fun checkSteps() {
            stepCount = 0
            var peakCount = 0
            var peakAccumulate = 0.0
            for (i in 1 until arrayBuffer.size - 1) {
                val curr: Double = arrayBuffer[i]
                if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0) {
                    peakCount++
                    peakAccumulate += curr
                }
            }
            val peakMean = peakAccumulate / peakCount
            for (i in 1 until arrayBuffer.size - 1) {
                val curr = arrayBuffer[i]
                if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0
                    && curr > c * peakMean && curr > threshold
                ) {
                    stepCount++
                }
            }
            // In idle state, step count seems to be 1.
            if (stepCount > 0) {
                stepCount--
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            try {
                if (event != null) {
                    val vec = event.values
                    val mag: Double = sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2].toDouble())
                    arrayBuffer[tick] = mag
                    tick = (tick + 1) % bufferSize

                    if (tick == 0) {
                        filterBuffer()
                        checkSteps()
                        val sharedPreferences = context.getSharedPreferences(
                            "fallbackSensorEventListener",
                            Context.MODE_PRIVATE
                        )
                        val currentStepCount = sharedPreferences.getInt("stepCount", 0)
                        val editor = sharedPreferences.edit()
                        editor.putInt("stepCount", currentStepCount + stepCount)
                        editor.apply()
                        events.success(sharedPreferences.getInt("stepCount", 0))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}


fun fallbackStepDetectorSensorEventListener(
    context: Context,
    events: EventChannel.EventSink
): SensorEventListener {
    return object : SensorEventListener {
        private val bufferSize: Int = 100
        private var arrayBuffer: DoubleArray = DoubleArray(bufferSize)
        private val c = 1.05
        private val threshold = 1.2
        private var tick = 0

        @Volatile
        private var stepCount = 0
        @Volatile
        private var stepCounts: ArrayList<Int> = arrayListOf()
        @Volatile
        private var last = 0

        private fun filterBuffer() {
            val samplingFreq = 50.0
            val order = 20
            val cutoff = 0.2 * (samplingFreq * 0.5)
            val filter = Butterworth(arrayBuffer, samplingFreq)
            arrayBuffer = filter.lowPassFilter(order, cutoff)
        }

        // Adapted from Mladenov, M., & Mock, M. (2009).
        // A step counter service for Java-enabled devices using a built-in accelerometer.
        private fun checkSteps() {
            stepCount = 0
            var peakCount = 0
            var peakAccumulate = 0.0
            for (i in 1 until arrayBuffer.size - 1) {
                val curr: Double = arrayBuffer[i]
                if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0) {
                    peakCount++
                    peakAccumulate += curr
                }
            }
            val peakMean = peakAccumulate / peakCount
            for (i in 1 until arrayBuffer.size - 1) {
                val curr = arrayBuffer[i]
                if (curr - arrayBuffer[i - 1] > 0 && curr - arrayBuffer[i + 1] > 0
                    && curr > c * peakMean && curr > threshold
                ) {
                    stepCount++
                }
            }
            // In idle state, step count seems to be 1.
            if (stepCount > 0) {
                stepCount--
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            try {
                if (event != null) {
                    val vec = event.values
                    val mag: Double = sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2].toDouble())
                    arrayBuffer[tick] = mag
                    tick = (tick + 1) % bufferSize
                    if (tick == 0) {
                        filterBuffer()
                        checkSteps()
                        stepCounts.add(stepCount)
                        if (stepCounts.size > 2) {
                            stepCounts.removeAt(0)
                            if (stepCounts[0] == 0 && stepCounts[1] == 0) {
                                // Consecutive zero accelerometer deltas, stopped state.
                                events.success(-1)
                            }
                            if (stepCounts[0] > 0 && stepCounts[1] > 0) {
                                // Consecutive non-zero accelerometer deltas, walking state.
                                events.success(-2)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
