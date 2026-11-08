package com.harryyang.filmmeter

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/** Which control the photographer has fixed on the meter's mechanical dial. */
enum class ExposurePriority { NONE, APERTURE, SHUTTER }

/** Pure exposure math, deliberately separated from Android sensor/UI code. */
object ExposureMath {
    private const val LN2 = 0.6931471805599453
    private const val INCIDENT_METER_CONSTANT = 250.0

    val apertureValues = listOf(1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)
    val apertureLabels = listOf("1.4", "2", "2.8", "4", "5.6", "8", "11", "16", "22")

    // Mechanical-camera-friendly shutter scale, from 1/1000 s to 30 s.
    val shutterSeconds = listOf(
        1.0 / 1000.0, 1.0 / 500.0, 1.0 / 250.0, 1.0 / 125.0,
        1.0 / 60.0, 1.0 / 30.0, 1.0 / 15.0, 1.0 / 8.0,
        1.0 / 4.0, 1.0 / 2.0, 1.0, 2.0, 4.0, 8.0, 15.0, 30.0
    )
    val shutterLabels = listOf(
        "1000", "500", "250", "125", "60", "30", "15", "8",
        "4", "2", "1s", "2s", "4s", "8s", "15s", "30s"
    )

    val eiValues = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1600, 3200, 6400)

    data class Exposure(
        val ev100: Double,
        val evAtEi: Double,
        val aperture: Double,
        val apertureIndex: Int,
        val shutterSeconds: Double,
        val shutterIndex: Int,
        val comboEv: Double,
        /** Positive means the selected combination is brighter than the meter target. */
        val errorStops: Double
    )

    /**
     * Incident-light approximation: EV100 = log2(E * 100 / C), C=250.
     * At ISO 100 this becomes log2(lux / 2.5). Calibration is an EV offset.
     */
    fun ev100FromLux(lux: Double, calibrationBiasEv: Double = 0.0): Double {
        require(lux > 0.0 && lux.isFinite()) { "Lux must be a finite positive value" }
        require(calibrationBiasEv.isFinite()) { "Calibration must be finite" }
        return ln(lux * 100.0 / INCIDENT_METER_CONSTANT) / LN2 + calibrationBiasEv
    }

    fun exposureFromLux(
        lux: Double,
        ei: Int,
        calibrationBiasEv: Double = 0.0,
        priority: ExposurePriority = ExposurePriority.NONE,
        lockedIndex: Int = 0
    ): Exposure = exposureFromEv100(ev100FromLux(lux, calibrationBiasEv), ei, priority, lockedIndex)

    /**
     * Calculates a pair at the selected EI. When a priority is set, that parameter
     * remains exact and only the opposite dial is solved/quantized to its nearest stop.
     */
    fun exposureFromEv100(
        ev100: Double,
        ei: Int,
        priority: ExposurePriority = ExposurePriority.NONE,
        lockedIndex: Int = 0
    ): Exposure {
        require(ev100.isFinite()) { "EV must be finite" }
        require(ei > 0) { "EI must be positive" }
        val evAtEi = ev100 + log2(ei / 100.0)

        return when (priority) {
            ExposurePriority.APERTURE -> exposureWithFixedAperture(ev100, evAtEi, lockedIndex)
            ExposurePriority.SHUTTER -> exposureWithFixedShutter(ev100, evAtEi, lockedIndex)
            ExposurePriority.NONE -> findBalancedPair(ev100, evAtEi)
        }
    }

    private fun exposureWithFixedAperture(ev100: Double, evAtEi: Double, requestedIndex: Int): Exposure {
        val apertureIndex = requestedIndex.coerceIn(apertureValues.indices)
        val aperture = apertureValues[apertureIndex]
        val shutterIndex = shutterSeconds.indices.minWithOrNull(
            compareBy<Int> { index ->
                abs(log2(aperture * aperture / shutterSeconds[index]) - evAtEi)
            }.thenBy { index -> shutterSeconds[index] }
        ) ?: 0
        val shutter = shutterSeconds[shutterIndex]
        val comboEv = log2(aperture * aperture / shutter)
        return Exposure(ev100, evAtEi, aperture, apertureIndex, shutter, shutterIndex, comboEv, comboEv - evAtEi)
    }

    private fun exposureWithFixedShutter(ev100: Double, evAtEi: Double, requestedIndex: Int): Exposure {
        val shutterIndex = requestedIndex.coerceIn(shutterSeconds.indices)
        val shutter = shutterSeconds[shutterIndex]
        val apertureIndex = apertureValues.indices.minWithOrNull(
            compareBy<Int> { index ->
                abs(log2(apertureValues[index] * apertureValues[index] / shutter) - evAtEi)
            }.thenBy { index -> abs(log2(apertureValues[index] / 8.0)) }
        ) ?: 0
        val aperture = apertureValues[apertureIndex]
        val comboEv = log2(aperture * aperture / shutter)
        return Exposure(ev100, evAtEi, aperture, apertureIndex, shutter, shutterIndex, comboEv, comboEv - evAtEi)
    }

    private fun findBalancedPair(ev100: Double, evAtEi: Double): Exposure {
        var best: Exposure? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (a in apertureValues.indices) {
            val aperture = apertureValues[a]
            for (s in shutterSeconds.indices) {
                val seconds = shutterSeconds[s]
                val comboEv = log2(aperture * aperture / seconds)
                val error = abs(comboEv - evAtEi)
                // Among near-equivalent pairs prefer a useful middle aperture and
                // avoid very slow hand-held shutter speeds.
                val aperturePreference = abs(log2(aperture / 8.0)) * 0.012
                val slowShutterPenalty = if (seconds > 1.0 / 30.0) {
                    log2(seconds / (1.0 / 30.0)).coerceAtLeast(0.0) * 0.004
                } else 0.0
                val score = error + aperturePreference + slowShutterPenalty
                if (score < bestScore) {
                    bestScore = score
                    best = Exposure(
                        ev100 = ev100,
                        evAtEi = evAtEi,
                        aperture = aperture,
                        apertureIndex = a,
                        shutterSeconds = seconds,
                        shutterIndex = s,
                        comboEv = comboEv,
                        errorStops = comboEv - evAtEi
                    )
                }
            }
        }
        return requireNotNull(best)
    }

    fun formatShutter(index: Int): String = shutterLabels[index.coerceIn(shutterLabels.indices)]

    fun formatAperture(value: Double): String = when {
        value < 2.0 -> String.format(java.util.Locale.US, "%.1f", value)
        value == 2.0 -> "2"
        value == 2.8 -> "2.8"
        value == 4.0 -> "4"
        value == 5.6 -> "5.6"
        value == 8.0 -> "8"
        value == 11.0 -> "11"
        value == 16.0 -> "16"
        else -> "22"
    }

    fun luxFromEv100(ev100: Double): Double = 2.5 * 2.0.pow(ev100)

    /** EV implied by a camera combination; useful to verify priority-mode results. */
    fun combinationEv(apertureIndex: Int, shutterIndex: Int): Double {
        val aperture = apertureValues[apertureIndex.coerceIn(apertureValues.indices)]
        val shutter = shutterSeconds[shutterIndex.coerceIn(shutterSeconds.indices)]
        return log2(aperture * aperture / shutter)
    }

    /** The ideal aperture for a locked shutter before quantizing to a standard stop. */
    fun idealAperture(evAtEi: Double, shutterSeconds: Double): Double = sqrt(2.0.pow(evAtEi) * shutterSeconds)
}
