package com.elsewhere.location.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class GeoTest {

    private val shanghai = GeoPoint(31.23040, 121.47370)

    @Test
    fun `destination north increases latitude by roughly requested distance`() {
        val north = Geo.destination(shanghai, bearingDegrees = 0.0, distanceMeters = 250.0)
        val actual = Geo.distanceMeters(shanghai, north)
        assertThat(actual).isWithin(1.0).of(250.0)
        assertThat(north.latitude).isGreaterThan(shanghai.latitude)
        assertThat(abs(north.longitude - shanghai.longitude)).isLessThan(0.0001)
    }

    @Test
    fun `bearing from center to north and east is correct`() {
        val north = Geo.destination(shanghai, 0.0, 100.0)
        val east = Geo.destination(shanghai, 90.0, 100.0)
        assertThat(Geo.bearingDegrees(shanghai, north)).isWithin(0.2).of(0.0)
        assertThat(Geo.bearingDegrees(shanghai, east)).isWithin(0.2).of(90.0)
    }

    @Test
    fun `angle approach never wraps the long way around`() {
        assertThat(Geo.angleDelta(350.0, 10.0)).isWithin(0.001).of(20.0)
        assertThat(Geo.angleDelta(10.0, 350.0)).isWithin(0.001).of(-20.0)
        val newAngle = Geo.approachAngle(359.0, 1.0, 5.0)
        assertThat(newAngle).isWithin(0.01).of(1.0)
    }

    @Test
    fun `distance formatting is concise`() {
        assertThat(Geo.formatDistance(0.0)).isEqualTo("0 m")
        assertThat(Geo.formatDistance(742.0)).isEqualTo("742 m")
        assertThat(Geo.formatDistance(1_240.0)).isEqualTo("1.24 km")
    }

    @Test
    fun `gcj conversion round trips inside china`() {
        val beijing = GeoPoint(39.9042, 116.4074)
        val gcj = ChineseGeo.wgs84ToGcj02(beijing)
        val back = ChineseGeo.gcj02ToWgs84(gcj)
        assertThat(Geo.distanceMeters(beijing, back)).isLessThan(0.6)
        assertThat(ChineseGeo.approximateOffsetMeters(beijing)).isGreaterThan(100.0)
    }

    @Test
    fun `gcj conversion leaves coordinates outside china untouched`() {
        val tokyo = GeoPoint(35.6762, 139.6503)
        assertThat(ChineseGeo.wgs84ToGcj02(tokyo)).isEqualTo(tokyo)
        assertThat(ChineseGeo.gcj02ToWgs84(tokyo)).isEqualTo(tokyo)
    }
}
