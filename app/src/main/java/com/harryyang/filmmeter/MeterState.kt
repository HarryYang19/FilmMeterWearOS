package com.harryyang.filmmeter

/** Sensor placement is a model-level setting so future watches can use different geometry. */
enum class SensorPlacement {
    WATCH5_PRO_FRONT_SIDE,
    DISPLAY_TOP,
    TOP_BEZEL,
    DISPLAY_CENTER,
    SIDE_FRAME
}

enum class MeterSource { AUTO_SENSOR, MANUAL_EV }

data class MeterState(
    val lux: Double? = null,
    val ei: Int = 100,
    val calibrationBiasEv: Double = 0.0,
    val manualEv100: Double = 8.0,
    val source: MeterSource = MeterSource.AUTO_SENSOR,
    val sensorAvailable: Boolean = false,
    val sensorResponding: Boolean = false,
    val sensorName: String = "",
    val placement: SensorPlacement = SensorPlacement.WATCH5_PRO_FRONT_SIDE,
    val ambient: Boolean = false,
    val timestampMs: Long = 0L
) {
    val effectiveEv100: Double
        get() = if (source == MeterSource.AUTO_SENSOR && lux != null && lux > 0.0) {
            ExposureMath.ev100FromLux(lux, calibrationBiasEv)
        } else {
            // Manual EV is already the user's absolute exposure reference; sensor
            // calibration must not silently alter a manually entered value.
            manualEv100
        }

    val exposure: ExposureMath.Exposure
        get() = ExposureMath.exposureFromEv100(effectiveEv100, ei)
}
