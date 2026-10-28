package com.elsewhere.location.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AMap raster tiles use GCJ-02 inside mainland China while Android's location
 * API and mock providers use WGS-84. Treating both as the same coordinate space
 * introduces a 300–600 m visual error, so all internal session points stay in
 * WGS-84 and are transformed only at the map/data-source boundary.
 */
object ChineseGeo {

    fun wgs84ToGcj02(point: GeoPoint): GeoPoint {
        if (outOfChina(point)) return point
        val dLatRaw = transformLatitude(point.longitude - 105.0, point.latitude - 35.0)
        val dLonRaw = transformLongitude(point.longitude - 105.0, point.latitude - 35.0)
        val radLat = point.latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val dLat = (dLatRaw * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val dLon = (dLonRaw * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return GeoPoint(
            latitude = point.latitude + dLat,
            longitude = point.longitude + dLon,
        )
    }

    fun gcj02ToWgs84(point: GeoPoint): GeoPoint {
        if (outOfChina(point)) return point
        var estimate = point
        repeat(4) {
            val forward = wgs84ToGcj02(estimate)
            estimate = GeoPoint(
                latitude = estimate.latitude - (forward.latitude - point.latitude),
                longitude = estimate.longitude - (forward.longitude - point.longitude),
            )
        }
        return estimate
    }

    fun toDisplay(point: GeoPoint, layer: MapLayer): GeoPoint = when (layer) {
        MapLayer.GAODE -> wgs84ToGcj02(point)
        MapLayer.OSM -> point
    }

    fun toWgs84(point: GeoPoint, layer: MapLayer): GeoPoint = when (layer) {
        MapLayer.GAODE -> gcj02ToWgs84(point)
        MapLayer.OSM -> point
    }

    fun outOfChina(point: GeoPoint): Boolean {
        return point.longitude !in 72.004..137.8347 || point.latitude !in 0.8293..55.8271
    }

    fun approximateOffsetMeters(point: GeoPoint): Double {
        if (outOfChina(point)) return 0.0
        val gcj = wgs84ToGcj02(point)
        val latMeters = abs(gcj.latitude - point.latitude) * 111_320.0
        val lonMeters = abs(gcj.longitude - point.longitude) *
            111_320.0 * cos(Math.toRadians(point.latitude))
        return sqrt(latMeters * latMeters + lonMeters * lonMeters)
    }

    private fun transformLatitude(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private const val A = 6_378_245.0
    private const val EE = 0.00669342162296594323
}
