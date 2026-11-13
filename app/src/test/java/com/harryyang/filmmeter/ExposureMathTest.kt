package com.harryyang.filmmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureMathTest {
    @Test
    fun incidentLuxConversionUsesIso100Reference() {
        assertEquals(0.0, ExposureMath.ev100FromLux(2.5), 1e-9)
        assertEquals(11.965784284662087, ExposureMath.ev100FromLux(10_000.0), 1e-6)
    }

    @Test
    fun higherEiRequiresFasterExposureByExpectedStops() {
        val base = ExposureMath.exposureFromEv100(10.0, 100)
        val pushed = ExposureMath.exposureFromEv100(10.0, 400)
        assertEquals(2.0, pushed.evAtEi - base.evAtEi, 1e-9)
    }

    @Test
    fun recommendedCombinationIsWithinHalfStopAcrossNormalMeterRange() {
        for (ev in (0..36).map { -1.0 + it * 0.5 }) {
            val exposure = ExposureMath.exposureFromEv100(ev, 100)
            assertTrue("EV $ev error ${exposure.errorStops}", kotlin.math.abs(exposure.errorStops) <= 0.51)
        }
    }

    @Test
    fun aperturePriorityKeepsApertureFixedAndSolvesShutter() {
        val lockedIndex = ExposureMath.apertureValues.indexOf(5.6)
        for (ev in (4..28).map { it * 0.5 }) {
            val exposure = ExposureMath.exposureFromEv100(
                ev100 = ev,
                ei = 100,
                priority = ExposurePriority.APERTURE,
                lockedIndex = lockedIndex
            )
            assertEquals(lockedIndex, exposure.apertureIndex)
            assertTrue("EV $ev error ${exposure.errorStops}", kotlin.math.abs(exposure.errorStops) <= 0.51)
        }
    }

    @Test
    fun shutterPriorityKeepsShutterFixedAndSolvesAperture() {
        val lockedIndex = ExposureMath.shutterLabels.indexOf("125")
        for (ev in (16..30).map { it * 0.5 }) {
            val exposure = ExposureMath.exposureFromEv100(
                ev100 = ev,
                ei = 100,
                priority = ExposurePriority.SHUTTER,
                lockedIndex = lockedIndex
            )
            assertEquals(lockedIndex, exposure.shutterIndex)
            assertTrue("EV $ev error ${exposure.errorStops}", kotlin.math.abs(exposure.errorStops) <= 0.51)
        }
    }

    @Test
    fun changingEiMovesOnlyTheUnlockedExposureParameter() {
        val apertureIndex = ExposureMath.apertureValues.indexOf(8.0)
        val aperture100 = ExposureMath.exposureFromEv100(10.0, 100, ExposurePriority.APERTURE, apertureIndex)
        val aperture400 = ExposureMath.exposureFromEv100(10.0, 400, ExposurePriority.APERTURE, apertureIndex)
        assertEquals(apertureIndex, aperture400.apertureIndex)
        assertTrue(aperture400.shutterIndex < aperture100.shutterIndex)

        val shutterIndex = ExposureMath.shutterLabels.indexOf("125")
        val shutter100 = ExposureMath.exposureFromEv100(10.0, 100, ExposurePriority.SHUTTER, shutterIndex)
        val shutter400 = ExposureMath.exposureFromEv100(10.0, 400, ExposurePriority.SHUTTER, shutterIndex)
        assertEquals(shutterIndex, shutter400.shutterIndex)
        assertTrue(shutter400.apertureIndex > shutter100.apertureIndex)
    }

    @Test
    fun invalidLockedIndicesAreSafelyClamped() {
        val aperture = ExposureMath.exposureFromEv100(8.0, 100, ExposurePriority.APERTURE, 999)
        val shutter = ExposureMath.exposureFromEv100(8.0, 100, ExposurePriority.SHUTTER, -5)
        assertEquals(ExposureMath.apertureValues.lastIndex, aperture.apertureIndex)
        assertEquals(0, shutter.shutterIndex)
    }

    @Test
    fun fiveColorSchemesAreAvailableAndBrassRemainsDefault() {
        assertEquals(5, MeterColorScheme.values().size)
        assertEquals(MeterColorScheme.BRASS, MeterState().colorScheme)
    }

    @Test
    fun calibrationBiasDoesNotAlterManualEvInput() {
        val manual = MeterState(
            source = MeterSource.MANUAL_EV,
            manualEv100 = 8.0,
            calibrationBiasEv = 1.5
        )
        assertEquals(8.0, manual.effectiveEv100, 1e-9)
    }

    @Test
    fun everyDialSelectionIsWithinBounds() {
        for (ev in (0..88).map { -2.0 + it * 0.25 }) {
            val exposure = ExposureMath.exposureFromEv100(ev, 400)
            assertTrue(exposure.apertureIndex in ExposureMath.apertureValues.indices)
            assertTrue(exposure.shutterIndex in ExposureMath.shutterSeconds.indices)
        }
    }
}
