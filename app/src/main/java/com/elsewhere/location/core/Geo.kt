package com.elsewhere.location.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Small, dependency-free geodesy helpers shared by the engine, map and tests. */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    fun isValid(point: GeoPoint): Boolean =
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    fun normalizeBearing(degrees: Double): Double {
        var value = degrees % 360.0
        if (value < 0.0) value += 360.0
        return value
    }

    fun angleDelta(fromDegrees: Double, toDegrees: Double): Double {
        var delta = (toDegrees - fromDegrees) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    fun approachAngle(currentDegrees: Double, targetDegrees: Double, maxDeltaDegrees: Double): Double {
        val delta = angleDelta(currentDegrees, targetDegrees)
        val applied = delta.coerceIn(-maxDeltaDegrees, maxDeltaDegrees)
        return normalizeBearing(currentDegrees + applied)
    }

    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a).coerceIn(0.0, 1.0))
    }

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    fun destination(from: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        if (distanceMeters == 0.0) return from
        val angular = distanceMeters / EARTH_RADIUS_M
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = asin(
            sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        val latitude = Math.toDegrees(lat2).coerceIn(-90.0, 90.0)
        var longitude = Math.toDegrees(lon2)
        while (longitude > 180.0) longitude -= 360.0
        while (longitude < -180.0) longitude += 360.0
        return GeoPoint(latitude, longitude)
    }

    /** Moves a point by a local tangent-plane offset. North is positive Y. */
    fun offsetMeters(from: GeoPoint, eastMeters: Double, northMeters: Double): GeoPoint {
        if (eastMeters == 0.0 && northMeters == 0.0) return from
        val distance = sqrt(eastMeters * eastMeters + northMeters * northMeters)
        val bearing = normalizeBearing(Math.toDegrees(atan2(eastMeters, northMeters)))
        return destination(from, bearing, distance)
    }

    fun formatLatitude(latitude: Double): String = formatDms(latitude, isLatitude = true)

    fun formatLongitude(longitude: Double): String = formatDms(longitude, isLatitude = false)

    fun formatDecimal(latitude: Double, longitude: Double, decimals: Int = 6): String =
        "${"%.${decimals}f".format(latitude)}, ${"%.${decimals}f".format(longitude)}"

    fun formatDms(value: Double, isLatitude: Boolean): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutesFloat = (absolute - degrees) * 60.0
        val minutes = floor(minutesFloat).toInt()
        val seconds = (minutesFloat - minutes) * 60.0
        val cardinal = when {
            isLatitude && value >= 0 -> "N"
            isLatitude -> "S"
            value >= 0 -> "E"
            else -> "W"
        }
        return "%d°%02d′%04.1f″%s".format(degrees, minutes, seconds, cardinal)
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1_000.0 -> "${meters.toInt()} m"
        meters < 10_000.0 -> "%.2f km".format(meters / 1_000.0)
        else -> "%.1f km".format(meters / 1_000.0)
    }

    fun formatSpeed(metersPerSecond: Float): String = "%.1f".format(metersPerSecond)

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
        else "%02d:%02d".format(minutes, secs)
    }

    fun haversinePolyline(points: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 0 until points.lastIndex) total += distanceMeters(points[i], points[i + 1])
        return total
    }

    /** Converts a route's total length to a human-friendly average pace. */
    fun formatPace(meters: Double, seconds: Long): String {
        if (meters < 1.0 || seconds <= 0) return "—"
        val secondsPerKm = seconds / (meters / 1_000.0)
        val minutes = (secondsPerKm / 60).toInt()
        val secs = (secondsPerKm % 60).toInt()
        return "%d′%02d″ /km".format(minutes, secs)
    }
}
