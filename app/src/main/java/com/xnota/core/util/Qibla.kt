package com.xnota.core.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The qibla: the great-circle bearing from a place on Earth to the Kaaba in Mecca, for the
 * direction a Muslim faces to pray. Pure spherical trigonometry — no location services, no
 * network; the caller hands in coordinates (from the device's last known location or typed by
 * hand) and gets back a compass bearing.
 */
object Qibla {

    /** The Kaaba's position, as conventionally published. */
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /** Mean Earth radius, the IUGG value haversines conventionally use. */
    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * The initial bearing from (`lat`, `lon`) to the Kaaba, in degrees clockwise from true
     * north, always in `[0, 360)`. Standing at the Kaaba itself this is 0 by the formula's
     * `atan2(0, 0)` — a place with no direction to face, which the caller may word as it likes.
     */
    fun bearingDeg(lat: Double, lon: Double): Double {
        val from = Math.toRadians(lat)
        val to = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(to)
        val x = cos(from) * sin(to) - sin(from) * cos(to) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** The great-circle distance to the Kaaba in kilometres, 0 at the Kaaba itself. */
    fun distanceKm(lat: Double, lon: Double): Double {
        val from = Math.toRadians(lat)
        val to = Math.toRadians(KAABA_LAT)
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) + cos(from) * cos(to) * sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1.0 - a))
    }

    /** A conventional reading of the bearing for display: "295°" — see [bearingDeg]. */
    fun bearingLabel(lat: Double, lon: Double): String {
        val deg = bearingDeg(lat, lon)
        return "${if (deg == deg.toLong().toDouble()) deg.toLong() else (deg * 10).toLong() / 10.0}°"
    }
}
