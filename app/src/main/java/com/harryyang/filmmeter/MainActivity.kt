package com.harryyang.filmmeter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.util.ArrayDeque

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var dialView: MeterDialView
    private val handler = Handler(Looper.getMainLooper())
    private val luxSamples = ArrayDeque<Double>()
    private var smoothedEv100: Double? = null
    private var currentState = MeterState()
    private var sensorTimeout: Runnable? = null
    private var sensorRegistered = false

    private val prefs by lazy { getSharedPreferences("film_meter", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The meter is intended to remain visible while the photographer is metering.
        // This replaces AOD/ambient-mode rendering; the OS still controls display power
        // once the activity is no longer foregrounded.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val savedEi = prefs.getInt("ei", 100).let { if (it in ExposureMath.eiValues) it else 100 }
        val savedBias = prefs.getFloat("calibration_bias", 0f).toDouble().coerceIn(-4.0, 4.0)
        val savedManualEv = prefs.getFloat("manual_ev100", 8f).toDouble().coerceIn(-2.0, 20.0)
        val savedSource = if (prefs.getBoolean("manual_mode", false)) MeterSource.MANUAL_EV else MeterSource.AUTO_SENSOR
        val savedPriority = runCatching {
            ExposurePriority.valueOf(prefs.getString("exposure_priority", ExposurePriority.NONE.name) ?: ExposurePriority.NONE.name)
        }.getOrDefault(ExposurePriority.NONE)
        val rawLockedIndex = prefs.getInt("locked_index", 0)
        val maxLockedIndex = when (savedPriority) {
            ExposurePriority.APERTURE -> ExposureMath.apertureValues.lastIndex
            ExposurePriority.SHUTTER -> ExposureMath.shutterSeconds.lastIndex
            ExposurePriority.NONE -> 0
        }
        val savedLockedIndex = rawLockedIndex.coerceIn(0, maxLockedIndex)

        val model = (android.os.Build.MODEL + " " + android.os.Build.DEVICE).uppercase()
        val placement = if (model.contains("R920") || model.contains("GALAXY WATCH5 PRO")) {
            SensorPlacement.WATCH5_PRO_FRONT_SIDE
        } else {
            // Generic profile reserved for future Wear OS device support.
            SensorPlacement.DISPLAY_TOP
        }
        currentState = MeterState(
            ei = savedEi,
            calibrationBiasEv = savedBias,
            manualEv100 = savedManualEv,
            source = savedSource,
            placement = placement,
            priority = savedPriority,
            lockedIndex = savedLockedIndex
        )

        dialView = MeterDialView(this, currentState, ::onStateChanged)
        setContentView(dialView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dialView.isInSettings) {
                    dialView.closeSettings()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        discoverLightSensor()
    }

    private fun discoverLightSensor() {
        val default = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val sensor = default ?: sensorManager.getSensorList(Sensor.TYPE_ALL)
            .firstOrNull { it.type == Sensor.TYPE_LIGHT }
        lightSensor = sensor
        currentState = currentState.copy(
            sensorAvailable = sensor != null,
            sensorName = sensor?.name.orEmpty()
        )
        if (sensor == null && currentState.source == MeterSource.AUTO_SENSOR) {
            currentState = currentState.copy(source = MeterSource.MANUAL_EV)
        }
        dialView.updateState(currentState)
    }

    override fun onResume() {
        super.onResume()
        startLightSensor()
    }

    override fun onPause() {
        stopLightSensor()
        super.onPause()
    }

    private fun startLightSensor() {
        if (!::sensorManager.isInitialized || currentState.source == MeterSource.MANUAL_EV) return
        val sensor = lightSensor ?: run {
            switchToManualFallback()
            return
        }
        if (sensorRegistered) return

        // Every new sampling session starts with a fresh filter window to avoid stale values.
        luxSamples.clear()
        smoothedEv100 = null
        currentState = currentState.copy(sensorResponding = false)
        val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorRegistered = success
        if (!success) {
            sensorRegistered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (!sensorRegistered) {
            switchToManualFallback()
            return
        }

        sensorTimeout?.let(handler::removeCallbacks)
        val timeout = Runnable {
            if (!currentState.sensorResponding && currentState.source == MeterSource.AUTO_SENSOR) {
                switchToManualFallback()
            }
        }
        sensorTimeout = timeout
        handler.postDelayed(timeout, 8_000L)
        dialView.updateState(currentState)
    }

    private fun stopLightSensor() {
        sensorTimeout?.let(handler::removeCallbacks)
        sensorTimeout = null
        if (::sensorManager.isInitialized && sensorRegistered) {
            sensorManager.unregisterListener(this)
        }
        sensorRegistered = false
    }

    private fun switchToManualFallback() {
        stopLightSensor()
        currentState = currentState.copy(source = MeterSource.MANUAL_EV, sensorResponding = false)
        dialView.updateState(currentState)
        persistState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (currentState.source != MeterSource.AUTO_SENSOR) return
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT || event.values.isEmpty()) return
        val rawLux = event.values[0].toDouble()
        if (!rawLux.isFinite() || rawLux < 0.0) return
        // Zero lux is a valid dark-scene reading; use a tiny positive floor for log2().
        val clampedLux = rawLux.coerceIn(0.1, 100_000.0)
        luxSamples.addLast(clampedLux)
        while (luxSamples.size > 7) luxSamples.removeFirst()
        val sorted = luxSamples.toList().sorted()
        val medianLux = sorted[sorted.size / 2]
        val rawEv = ExposureMath.ev100FromLux(medianLux)
        // Smooth in stops; user calibration remains a separate live offset.
        smoothedEv100 = smoothedEv100?.let { it + (rawEv - it) * 0.28 } ?: rawEv
        val stableLux = ExposureMath.luxFromEv100(smoothedEv100!!)
        currentState = currentState.copy(
            lux = stableLux,
            sensorAvailable = true,
            sensorResponding = true,
            sensorName = event.sensor.name,
            source = MeterSource.AUTO_SENSOR,
            timestampMs = System.currentTimeMillis()
        )
        sensorTimeout?.let(handler::removeCallbacks)
        sensorTimeout = null
        dialView.updateState(currentState)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onStateChanged(newState: MeterState) {
        currentState = newState
        persistState()
        if (currentState.source == MeterSource.MANUAL_EV) {
            stopLightSensor()
        } else {
            startLightSensor()
        }
    }

    private fun persistState() {
        prefs.edit()
            .putInt("ei", currentState.ei)
            .putFloat("calibration_bias", currentState.calibrationBiasEv.toFloat())
            .putFloat("manual_ev100", currentState.manualEv100.toFloat())
            .putBoolean("manual_mode", currentState.source == MeterSource.MANUAL_EV)
            .putString("exposure_priority", currentState.priority.name)
            .putInt("locked_index", currentState.lockedIndex)
            .apply()
    }
}
