package com.elsewhere.location.location

import com.elsewhere.location.core.DriftConfig
import com.elsewhere.location.core.EnvironmentProfile
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.RoutePlan
import com.elsewhere.location.core.SensorConfig
import com.elsewhere.location.core.SessionStartConfig
import com.elsewhere.location.core.SpeedSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class MotionEngineTest {

    private val shanghai = GeoPoint(31.23040, 121.47370)

    private fun config(
        mode: MotionMode = MotionMode.WALK,
        speed: Float = mode.defaultSpeedMps,
        route: RoutePlan? = null,
        drift: DriftConfig = DriftConfig(),
    ) = SessionStartConfig(
        point = shanghai,
        mode = mode,
        baseSpeedMps = speed,
        speedSettings = SpeedSettings(speed, speed, speed),
        overlayEnabled = false,
        drift = drift,
        environment = EnvironmentProfile(),
        sensors = SensorConfig(),
        route = route,
    )

    @Test
    fun `manual walking moves north with a smooth ramp`() {
        val engine = MotionEngine(config(), seed = 11L)
        engine.setJoystick(0f, 1f)
        val start = engine.currentPoint()
        var lastSpeed = 0f
        var frame = engine.advance(0.2)
        repeat(24) {
            frame = engine.advance(0.2)
            assertThat(frame.speedMps).isAtLeast(lastSpeed - 0.35f)
            lastSpeed = frame.speedMps
        }
        assertThat(frame.speedMps).isGreaterThan(0.5f)
        assertThat(frame.steps).isGreaterThan(0L)
        assertThat(frame.cadenceSpm).isGreaterThan(30f)
        assertThat(Geo.distanceMeters(start, frame.effectivePoint)).isGreaterThan(2.0)
        assertThat(frame.bearingDeg).isWithin(5f).of(0f)
    }

    @Test
    fun `releasing the joystick coasts to a stop`() {
        val engine = MotionEngine(config(), seed = 21L)
        engine.setJoystick(1f, 0f)
        repeat(20) { engine.advance(0.2) }
        val moved = engine.currentPoint()
        engine.releaseJoystick()
        repeat(80) { engine.advance(0.2) }
        val frame = engine.advance(0.2)
        assertThat(frame.speedMps).isWithin(0.01f).of(0f)
        assertThat(Geo.distanceMeters(moved, engine.currentPoint())).isLessThan(8.0)
    }

    @Test
    fun `route mode follows waypoints and completes`() {
        val finish = Geo.destination(shanghai, 20.0, 130.0)
        val route = RoutePlan(
            id = "route-1",
            name = "test route",
            points = listOf(shanghai, finish),
            mode = MotionMode.WALK,
        )
        val engine = MotionEngine(config(route = route), seed = 31L)
        var frame = engine.advance(0.2)
        var ticks = 0
        while (!frame.routeComplete && ticks < 1_000) {
            frame = engine.advance(0.2)
            ticks += 1
        }
        assertThat(frame.routeComplete).isTrue()
        assertThat(ticks).isLessThan(900)
        assertThat(frame.routeProgress).isWithin(0.03f).of(1f)
        assertThat(Geo.distanceMeters(frame.basePoint, finish)).isLessThan(2.0)
        assertThat(frame.distanceMeters).isGreaterThan(120.0)
    }

    @Test
    fun `drift makes the injected point differ from the anchor`() {
        val drift = DriftConfig(enabled = true, amplitudeMeters = 5f)
        val engine = MotionEngine(config(drift = drift), seed = 41L)
        repeat(120) { engine.advance(0.2) }
        val frame = engine.advance(0.2)
        val offset = Geo.distanceMeters(frame.basePoint, frame.effectivePoint)
        assertThat(offset).isGreaterThan(0.1)
        assertThat(offset).isLessThan(20.0)
        assertThat(frame.driftActive).isTrue()
    }

    @Test
    fun `loop route includes the closing leg and keeps cruising`() {
        val start = shanghai
        val east = Geo.destination(start, 90.0, 50.0)
        val route = RoutePlan(
            id = "loop-1",
            name = "loop test",
            points = listOf(start, east),
            loop = true,
            mode = MotionMode.CYCLE,
        )
        assertThat(route.distanceMeters).isWithin(1.0).of(100.0)
        val engine = MotionEngine(config(mode = MotionMode.CYCLE, speed = 6f, route = route), seed = 61L)
        var frame = engine.advance(0.2)
        var ticks = 0
        while (!frame.routeComplete && ticks < 800) {
            frame = engine.advance(0.2)
            ticks += 1
        }
        assertThat(frame.routeComplete).isFalse()
        assertThat(frame.distanceMeters).isGreaterThan(60.0)
        assertThat(frame.routeProgress).isAtLeast(0f)
        assertThat(frame.routeProgress).isAtMost(1f)
    }

    @Test
    fun `fast route cycle reaches the final waypoint without oscillating`() {
        val finish = Geo.destination(shanghai, 30.0, 120.0)
        val route = RoutePlan(
            id = "cycle-1",
            name = "fast test",
            points = listOf(shanghai, finish),
            mode = MotionMode.CYCLE,
        )
        val engine = MotionEngine(config(mode = MotionMode.CYCLE, speed = 10f, route = route), seed = 71L)
        var frame = engine.advance(0.2)
        var ticks = 0
        while (!frame.routeComplete && ticks < 300) {
            frame = engine.advance(0.2)
            ticks += 1
        }
        assertThat(frame.routeComplete).isTrue()
        assertThat(Geo.distanceMeters(frame.basePoint, finish)).isLessThan(5.0)
    }

    @Test
    fun `changing mode changes speed envelope and cadence behavior`() {
        val engine = MotionEngine(config(), seed = 51L)
        engine.setMode(MotionMode.CYCLE, 6.0f)
        repeat(20) { engine.advance(0.2) }
        val frame = engine.advance(0.2)
        assertThat(frame.mode).isEqualTo(MotionMode.CYCLE)
        assertThat(frame.steps).isEqualTo(0L)
        assertThat(abs(frame.baseSpeedMps - 6.0f)).isLessThan(0.2f)
    }
}
