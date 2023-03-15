package com.example.pedometer

import android.hardware.Sensor
import android.content.Context
import android.hardware.SensorManager
import androidx.annotation.NonNull
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.engine.plugins.FlutterPlugin

class PedometerPlugin : FlutterPlugin {
    private lateinit var stepDetectorChannel: EventChannel
    private lateinit var stepCounterChannel: EventChannel

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        stepDetectorChannel = EventChannel(flutterPluginBinding.binaryMessenger, "step_detection")
        stepCounterChannel = EventChannel(flutterPluginBinding.binaryMessenger, "step_count")

        // Physical step sensors i.e. TYPE_STEP_DETECTOR & TYPE_STEP_COUNTER are not present on all devices.
        // We use software based fallback using accelerometer on devices where these sensors are not present.
        var stepSensorPresent: Boolean = false
        try {
            val sensorManager: SensorManager = flutterPluginBinding.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor: Sensor? = sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (sensor != null) {
                stepSensorPresent = true
            }
        } catch(e: Exception) {
            stepSensorPresent = false
        }

        if (stepSensorPresent) {
            val stepDetectorHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_DETECTOR, ::defaultSensorEventListener)
            val stepCounterHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_STEP_COUNTER, ::defaultSensorEventListener)
            stepDetectorChannel.setStreamHandler(stepDetectorHandler)
            stepCounterChannel.setStreamHandler(stepCounterHandler)
        } else {
            val stepDetectorHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_ACCELEROMETER, ::fallbackStepDetectorSensorEventListener)
            val stepCounterHandler = SensorStreamHandler(flutterPluginBinding, Sensor.TYPE_ACCELEROMETER, ::fallbackStepCounterSensorEventListener)
            stepDetectorChannel.setStreamHandler(stepDetectorHandler)
            stepCounterChannel.setStreamHandler(stepCounterHandler)
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        stepDetectorChannel.setStreamHandler(null)
        stepCounterChannel.setStreamHandler(null)
    }
}
