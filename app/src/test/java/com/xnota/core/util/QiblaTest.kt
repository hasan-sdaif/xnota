package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaTest {

    @Test fun meccaItselfHasNoDistanceAndAZeroBearing() {
        assertEquals(0.0, Qibla.distanceKm(Qibla.KAABA_LAT, Qibla.KAABA_LON), 1e-9)
        assertEquals(0.0, Qibla.bearingDeg(Qibla.KAABA_LAT, Qibla.KAABA_LON), 1e-9)
    }

    @Test fun knownBearingsMatchThePublishedValues() {
        // Jakarta faces roughly WNW (~295°) and London roughly ESE (~119°).
        assertEquals(295.15, Qibla.bearingDeg(-6.2088, 106.8456), 0.1)
        assertEquals(118.99, Qibla.bearingDeg(51.5074, -0.1278), 0.1)
        assertEquals(58.48, Qibla.bearingDeg(40.7128, -74.0060), 0.1)
        assertEquals(277.50, Qibla.bearingDeg(-33.8688, 151.2093), 0.1)
        // Riyadh sits west-southwest of Mecca, facing back east-northeast.
        assertEquals(243.80, Qibla.bearingDeg(24.7136, 46.6753), 0.1)
    }

    @Test fun bearingsAlwaysLandInZeroThreeSixty() {
        for (lat in listOf(-60.0, -33.0, 0.0, 21.5, 51.0, 70.0)) {
            for (lon in listOf(-179.0, -0.1, 0.0, 39.8, 106.0, 178.0)) {
                val b = Qibla.bearingDeg(lat, lon)
                assertTrue("$lat,$lon -> $b", b >= 0.0 && b < 360.0)
            }
        }
    }

    @Test fun distancesArePositiveAndPlausible() {
        assertEquals(7920.14, Qibla.distanceKm(-6.2088, 106.8456), 1.0)
        assertEquals(4793.78, Qibla.distanceKm(51.5074, -0.1278), 1.0)
        // Nothing on Earth is more than half the circumference away.
        assertTrue(Qibla.distanceKm(-21.4, -140.0) <= Math.PI * 6371.0088 + 0.5)
    }

    @Test fun theAntipodeStillPointsSomewhereRatherThanThrowing() {
        // Nearly opposite the Kaaba: atan2 stays defined, so the formula answers.
        assertTrue(Qibla.bearingDeg(-21.4, -140.0) in 0.0..360.0)
    }

    @Test fun bearingLabelRoundsNicely() {
        assertEquals("295.1°", Qibla.bearingLabel(-6.2088, 106.8456))
        assertEquals("0°", Qibla.bearingLabel(Qibla.KAABA_LAT, Qibla.KAABA_LON))
    }
}
