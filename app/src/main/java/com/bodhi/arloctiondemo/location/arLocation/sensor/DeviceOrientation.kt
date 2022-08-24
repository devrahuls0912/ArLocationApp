package com.bodhi.arloctiondemo.location.arLocation.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager


class DeviceOrientation(context: Context) : SensorEventListener {
    var pitch = 0f
    var roll = 0f
    private val windowManager: WindowManager
    private val mSensorManager: SensorManager

    var orientation = 0f
        private set

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR -> processArNavSensorOrientation(
                event.values
            )
            else -> Log.e("DeviceOrientation", "Sensor event type not supported")
        }
    }

    private fun processArNavSensorOrientation(rotation: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotation)
        val worldAxisX: Int
        val worldAxisY: Int
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> {
                worldAxisX = SensorManager.AXIS_Z
                worldAxisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> {
                worldAxisX = SensorManager.AXIS_MINUS_X
                worldAxisY = SensorManager.AXIS_MINUS_Z
            }
            Surface.ROTATION_270 -> {
                worldAxisX = SensorManager.AXIS_MINUS_Z
                worldAxisY = SensorManager.AXIS_X
            }
            Surface.ROTATION_0 -> {
                worldAxisX = SensorManager.AXIS_X
                worldAxisY = SensorManager.AXIS_Z
            }
            else -> {
                worldAxisX = SensorManager.AXIS_X
                worldAxisY = SensorManager.AXIS_Z
            }
        }
        val adjustedRotationMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix, worldAxisX,
            worldAxisY, adjustedRotationMatrix
        )

        // azimuth/pitch/roll
        val orientation = FloatArray(3)
        SensorManager.getOrientation(adjustedRotationMatrix, orientation)
        this.orientation = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w("DeviceOrientation", "Orientation compass unreliable")
        }
    }

    fun resume() {
        mSensorManager.registerListener(
            this,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    fun pause() {
        mSensorManager.unregisterListener(this)
    }

    init {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
}
