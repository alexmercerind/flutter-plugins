package com.example.pedometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel

class SensorStreamHandler(
    private val flutterPluginBinding: FlutterPlugin.FlutterPluginBinding,
    private val sensorType: Int,
    private val getSensorEventListener: (context: Context, events: EventChannel.EventSink) -> SensorEventListener
) : EventChannel.StreamHandler {
    private var sensorEventListener: SensorEventListener? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    
    init {
        sensorManager = flutterPluginBinding.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager!!.getDefaultSensor(sensorType)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sensorEventListener = getSensorEventListener(flutterPluginBinding.applicationContext, events!!)
        sensorManager!!.registerListener(
            sensorEventListener,
            sensor, SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onCancel(arguments: Any?) {
        sensorManager!!.unregisterListener(sensorEventListener)
    }
}
