package com.elsewhere.location.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class SettingsSerializationTest {

    @Test
    fun `settings round trip keeps routes and coordinates`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val route = RoutePlan(
            id = "r1",
            name = "外滩环线",
            points = listOf(GeoPoint(31.2304, 121.4737), GeoPoint(31.2350, 121.4800)),
            loop = true,
            mode = MotionMode.RUN,
        )
        val original = AppSettings(
            onboarded = true,
            motionMode = MotionMode.RUN,
            lastPoint = GeoPoint(31.2, 121.5),
            routes = listOf(route),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<AppSettings>(encoded)
        assertThat(decoded).isEqualTo(original)
        assertThat(decoded.routes.single().name).isEqualTo("外滩环线")
        assertThat(decoded.routes.single().loop).isTrue()
    }

    @Test
    fun `missing fields get the intended defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<AppSettings>("{}")
        assertThat(decoded.motionMode).isEqualTo(MotionMode.WALK)
        assertThat(decoded.mapLayer).isEqualTo(MapLayer.GAODE)
        assertThat(decoded.drift.enabled).isFalse()
        assertThat(decoded.speeds.walkMps).isEqualTo(1.35f)
    }
}
