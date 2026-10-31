package com.harryyang.filmmeter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.wear.ambient.AmbientLifecycleObserver
import java.util.ArrayDeque

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var dialView: MeterDialView
    private lateinit var ambientObserver: AmbientLifecycleObserver
    private val handler = Handler(Looper.getMainLooper())
    private val luxSamples = ArrayDeque<Double>()
    private var smoothedEv100: Double? = null
    private var currentState = MeterState()
    private var sensorTimeout: Runnable? = null
    private var sensorRegistered = false
    private var ambientSampleOpen = false
    private var ambientSampleStopTask: Runnable? = null

    private val prefs by lazy { getSharedPreferences("film_meter", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val savedEi = prefs.getInt("ei", 100).let { if (it in ExposureMath.eiValues) it else 100 }
        val savedBias = prefs.getFloat("calibration_bias", 0f).toDouble().coerceIn(-4.0, 4.0)
        val savedManualEv = prefs.getFloat("manual_ev100", 8f).toDouble().coerceIn(-2.0, 20.0)
        val savedSource = if (prefs.getBoolean("manual_mode", false)) MeterSource.MANUAL_EV else MeterSource.AUTO_SENSOR
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
            placement = placement
        )

        dialView = MeterDialView(this, currentState, ::onStateChanged)
        setContentView(dialView)

        ambientObserver = AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                currentState = currentState.copy(ambient = true)
                dialView.setAmbientMode(true)
                dialView.updateState(currentState)
                stopLightSensor()
                stopAmbientSample()
            }

            override fun onExitAmbient() {
                stopAmbientSample()
                currentState = currentState.copy(ambient = false)
                dialView.setAmbientMode(false)
                dialView.updateState(currentState)
                startLightSensor()
            }

            override fun onUpdateAmbient() {
                // Ambient refresh is system-scheduled (normally around once per minute).
                // Take one short best-effort ALS sample during this system wake, then release it.
                dialView.invalidate()
                startAmbientSample()
            }
        })
        lifecycle.addObserver(ambientObserver)
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
        if (::ambientObserver.isInitialized && !ambientObserver.isAmbient) startLightSensor()
    }

    override fun onPause() {
        stopLightSensor()
        stopAmbientSample()
        super.onPause()
    }

    private fun startLightSensor() {
        if (!::sensorManager.isInitialized || currentState.ambient) return
        val sensor = lightSensor ?: run {
            switchToManualFallback()
            return
        }
        if (currentState.source == MeterSource.MANUAL_EV) return
        if (sensorRegistered) return
        // Each active sampling session starts with a fresh filter window. This avoids
        // dragging a stale reading forward after the watch wakes or the user re-enables AUTO.
        luxSamples.clear()
        smoothedEv100 = null
        currentState = currentState.copy(sensorResponding = false)
        val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorRegistered = success
        if (!success) {
            // Try the less aggressive normal rate once before declaring the ALS unavailable.
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
    }


    private fun startAmbientSample() {
        if (!currentState.ambient || currentState.source != MeterSource.AUTO_SENSOR) return
        val sensor = lightSensor ?: return
        stopAmbientSample()
        ambientSampleOpen = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (ambientSampleOpen) {
            val stopTask = Runnable { stopAmbientSample() }
            ambientSampleStopTask = stopTask
            handler.postDelayed(stopTask, 1_500L)
        }
    }

    private fun stopAmbientSample() {
        ambientSampleStopTask?.let(handler::removeCallbacks)
        ambientSampleStopTask = null
        if (ambientSampleOpen && ::sensorManager.isInitialized && lightSensor != null) {
            sensorManager.unregisterListener(this, lightSensor)
        }
        ambientSampleOpen = false
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
        currentState = currentState.copy(
            source = MeterSource.MANUAL_EV,
            sensorResponding = false
        )
        dialView.updateState(currentState)
        persistState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (currentState.source != MeterSource.AUTO_SENSOR || (currentState.ambient && !ambientSampleOpen)) return
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
        // Smooth the uncalibrated EV in stops; calibration remains a live, independent offset.
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
        if (currentState.ambient) stopAmbientSample()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onStateChanged(newState: MeterState) {
        currentState = newState
        prefs.edit()
            .putInt("ei", currentState.ei)
            .putFloat("calibration_bias", currentState.calibrationBiasEv.toFloat())
            .putFloat("manual_ev100", currentState.manualEv100.toFloat())
            .putBoolean("manual_mode", currentState.source == MeterSource.MANUAL_EV)
            .apply()
        if (currentState.source == MeterSource.MANUAL_EV) stopLightSensor()
        else if (!currentState.ambient) startLightSensor()
    }

    private fun persistState() {
        prefs.edit()
            .putInt("ei", currentState.ei)
            .putFloat("calibration_bias", currentState.calibrationBiasEv.toFloat())
            .putFloat("manual_ev100", currentState.manualEv100.toFloat())
            .putBoolean("manual_mode", currentState.source == MeterSource.MANUAL_EV)
            .apply()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta != 0f) {
                dialView.handleRotary(if (delta > 0f) 1 else -1)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::dialView.isInitialized && dialView.isInSettings) {
            dialView.closeSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
