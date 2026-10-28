package com.elsewhere.location.location

import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.MotionMode
import com.elsewhere.location.core.RoutePlan
import com.elsewhere.location.core.SensorTelemetry
import com.elsewhere.location.core.SessionStartConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class MotionFrame(
    val basePoint: GeoPoint,
    val effectivePoint: GeoPoint,
    val mode: MotionMode,
    val speedMps: Float,
    val targetSpeedMps: Float,
    val baseSpeedMps: Float,
    val bearingDeg: Float,
    val elapsedSeconds: Long,
    val distanceMeters: Double,
    val steps: Long,
    val cadenceSpm: Float,
    val routeProgress: Float,
    val routeComplete: Boolean,
    val telemetry: SensorTelemetry,
    val driftActive: Boolean,
)

/**
 * Deterministic movement model. It has no Android dependencies so the whole
 * walking / running / cycling behaviour can be unit tested on the JVM.
 *
 * Manual movement is joystick-gated: hold to move, release to coast to a stop.
 * A route is automatic: the engine follows waypoints and emits synthetic gait
 * telemetry at the same time.
 */
class MotionEngine(
    private val config: SessionStartConfig,
    seed: Long = Random.nextLong(),
) {
    private val random = Random(seed)
    private val route: RoutePlan? = config.route?.takeIf { it.points.size >= 2 }
    private val allRoutePoints = config.route?.points.orEmpty()

    private var basePoint: GeoPoint = when {
        allRoutePoints.isNotEmpty() -> allRoutePoints.first()
        else -> config.point
    }

    private var bearingDeg: Double = if (route != null) {
        Geo.bearingDegrees(allRoutePoints[0], allRoutePoints[1])
    } else {
        0.0
    }

    private var mode: MotionMode = config.mode
    private var baseSpeed: Double = config.baseSpeedMps.toDouble().coerceIn(
        mode.speedRange.start.toDouble()..mode.speedRange.endInclusive.toDouble(),
    )
    private var currentSpeed: Double = 0.0
    private var targetSpeed: Double = 0.0
    private var joystickX: Double = 0.0
    private var joystickY: Double = 0.0
    private var joystickEngaged: Boolean = false

    private var nextRouteIndex: Int = if (route != null) 1 else -1
    private var routeComplete: Boolean = false
    private var routeDistanceTravelled: Double = 0.0
    private val routeTotalDistance: Double = config.route?.distanceMeters ?: 0.0

    private var elapsedSeconds: Double = 0.0
    private var distanceMeters: Double = 0.0
    private var stepCount: Long = 0
    private var stepFraction: Double = 0.0
    private var cadenceSpm: Float = 0f
    private var stepPhase: Double = random.nextDouble(0.0, 2.0 * PI)
    private var stepDetectedThisTick: Boolean = false
    private var currentTurnRate: Double = 0.0
    private var driftTime = 0.0

    private val driftGenerator = DriftGenerator(config.drift, seed * 31 + 7)
    private val track = mutableListOf<GeoPoint>(basePoint)
    private var trackClock = 0.0
    private var lastDriftEast = 0.0
    private var lastDriftNorth = 0.0
    private var routeCompletedThisTick = false

    private val gaitFrequency: Double
        get() = when (mode) {
            MotionMode.WALK -> 1.72
            MotionMode.RUN -> 2.64
            MotionMode.CYCLE -> 1.15
        }

    private val gaitAmplitude: Double
        get() = when (mode) {
            MotionMode.WALK -> 0.095
            MotionMode.RUN -> 0.145
            MotionMode.CYCLE -> 0.038
        }

    private val maxAcceleration: Double
        get() = when (mode) {
            MotionMode.WALK -> 0.95
            MotionMode.RUN -> 2.10
            MotionMode.CYCLE -> 1.45
        }

    private val maxTurnRate: Double
        get() = when (mode) {
            MotionMode.WALK -> 170.0
            MotionMode.RUN -> 125.0
            MotionMode.CYCLE -> 80.0
        }

    val isRouteMode: Boolean get() = route != null
    val activeRoute: RoutePlan? get() = config.route

    fun setMode(newMode: MotionMode, requestedSpeed: Float) {
        mode = newMode
        baseSpeed = requestedSpeed.toDouble().coerceIn(
            newMode.speedRange.start.toDouble()..newMode.speedRange.endInclusive.toDouble(),
        )
        targetSpeed = targetSpeed.coerceIn(0.0, baseSpeed * 1.3)
    }

    fun setBaseSpeed(requestedSpeed: Float) {
        baseSpeed = requestedSpeed.toDouble().coerceIn(
            mode.speedRange.start.toDouble()..mode.speedRange.endInclusive.toDouble(),
        )
    }

    fun setJoystick(x: Float, y: Float) {
        val magnitude = hypot(x.toDouble(), y.toDouble())
        if (magnitude < 0.055) {
            joystickX = 0.0
            joystickY = 0.0
            joystickEngaged = false
            return
        }
        val clampedX = x.coerceIn(-1f, 1f).toDouble()
        val clampedY = y.coerceIn(-1f, 1f).toDouble()
        val normalized = hypot(clampedX, clampedY).coerceAtLeast(0.0001)
        joystickX = clampedX / normalized
        joystickY = clampedY / normalized
        joystickEngaged = true
    }

    fun releaseJoystick() {
        joystickEngaged = false
        joystickX = 0.0
        joystickY = 0.0
    }

    fun currentPoint(): GeoPoint = basePoint

    fun trackSnapshot(): List<GeoPoint> = track.toList()

    fun advance(dtSeconds: Double): MotionFrame {
        val dt = dtSeconds.coerceIn(0.02, 1.0)
        elapsedSeconds += dt
        routeCompletedThisTick = false
        stepDetectedThisTick = false

        val movingManually = joystickEngaged && route == null
        val routeActive = route != null && !routeComplete && nextRouteIndex >= 0

        if (routeActive) {
            advanceRoute(dt)
        } else if (movingManually) {
            val desiredBearing = Geo.normalizeBearing(Math.toDegrees(kotlin.math.atan2(joystickX, joystickY)))
            val turn = Geo.angleDelta(bearingDeg, desiredBearing)
                .coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
            bearingDeg = Geo.normalizeBearing(bearingDeg + turn)
            currentTurnRate = turn / dt
        } else {
            currentTurnRate *= 0.88
        }

        val gait = 1.0 + gaitAmplitude * sin(stepPhase) +
            0.024 * sin(stepPhase * 2.0 + 0.6)
        val desiredSpeed = when {
            routeActive && !routeComplete -> {
                val remaining = remainingRouteLegMeters()
                val stopping = sqrt(2.0 * maxAcceleration * (remaining + 1.8).coerceAtLeast(0.1))
                min(baseSpeed * gait, stopping)
            }
            movingManually -> baseSpeed * gait
            else -> 0.0
        }
        targetSpeed = desiredSpeed.coerceAtLeast(0.0)

        val acceleration = if (targetSpeed >= currentSpeed) maxAcceleration else maxAcceleration * 1.8
        currentSpeed = moveTowards(currentSpeed, targetSpeed, acceleration * dt)
        if (currentSpeed < 0.025 && targetSpeed == 0.0) currentSpeed = 0.0

        if (currentSpeed > 0.01) {
            val stepDistance = currentSpeed * dt
            basePoint = Geo.destination(basePoint, bearingDeg, stepDistance)
            distanceMeters += stepDistance
            stepPhase += 2.0 * PI * gaitFrequency * dt
            if (stepPhase > 2.0 * PI) stepPhase %= 2.0 * PI
            if (route != null) routeDistanceTravelled += stepDistance
            updateStepCounter(dt)
            trackClock += dt
            if (trackClock >= 1.2) {
                trackClock = 0.0
                appendTrackPoint()
            }
        } else {
            cadenceSpm = 0f
        }

        if (routeActive && !routeComplete && currentSpeed > 0.0) {
            checkRouteArrival()
        }

        driftTime += dt
        val drift = driftGenerator.advance(dt)
        lastDriftEast = drift.eastMeters
        lastDriftNorth = drift.northMeters
        val effectivePoint = Geo.offsetMeters(basePoint, drift.eastMeters, drift.northMeters)

        val telemetry = buildTelemetry()
        val progress = when {
            routeTotalDistance <= 0.0 -> 0f
            config.route?.loop == true -> (routeDistanceTravelled % routeTotalDistance / routeTotalDistance).toFloat()
            else -> (routeDistanceTravelled / routeTotalDistance).coerceIn(0.0, 1.0).toFloat()
        }

        return MotionFrame(
            basePoint = basePoint,
            effectivePoint = effectivePoint,
            mode = mode,
            speedMps = currentSpeed.toFloat(),
            targetSpeedMps = targetSpeed.toFloat(),
            baseSpeedMps = baseSpeed.toFloat(),
            bearingDeg = bearingDeg.toFloat(),
            elapsedSeconds = elapsedSeconds.toLong(),
            distanceMeters = distanceMeters,
            steps = stepCount,
            cadenceSpm = cadenceSpm,
            routeProgress = progress,
            routeComplete = routeComplete,
            telemetry = telemetry.copy(stepDetected = stepDetectedThisTick),
            driftActive = config.drift.enabled,
        )
    }

    private fun advanceRoute(dt: Double) {
        if (nextRouteIndex !in allRoutePoints.indices) {
            routeComplete = true
            return
        }
        val target = allRoutePoints[nextRouteIndex]
        val desired = Geo.bearingDegrees(basePoint, target)
        val turn = Geo.angleDelta(bearingDeg, desired)
            .coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
        bearingDeg = Geo.normalizeBearing(bearingDeg + turn)
        currentTurnRate = turn / dt
    }

    private fun remainingRouteLegMeters(): Double {
        if (nextRouteIndex !in allRoutePoints.indices) return 0.0
        return Geo.distanceMeters(basePoint, allRoutePoints[nextRouteIndex])
    }

    private fun checkRouteArrival() {
        if (nextRouteIndex !in allRoutePoints.indices) return
        val target = allRoutePoints[nextRouteIndex]
        if (Geo.distanceMeters(basePoint, target) > 0.75) return

        basePoint = target
        appendTrackPoint(force = true)
        if (nextRouteIndex + 1 < allRoutePoints.size) {
            nextRouteIndex += 1
            return
        }
        if (config.route?.loop == true && allRoutePoints.size >= 2) {
            nextRouteIndex = 0
        } else {
            nextRouteIndex = -1
            routeComplete = true
            routeCompletedThisTick = true
            targetSpeed = 0.0
            cadenceSpm = 0f
        }
    }

    private fun updateStepCounter(dt: Double) {
        if (mode == MotionMode.CYCLE) {
            cadenceSpm = 0f
            return
        }
        val speedRatio = (currentSpeed / baseSpeed.coerceAtLeast(0.1)).coerceIn(0.55, 1.45)
        val stepsPerSecond = when (mode) {
            MotionMode.WALK -> 1.82 * speedRatio
            MotionMode.RUN -> 2.82 * speedRatio
            MotionMode.CYCLE -> 0.0
        }
        cadenceSpm = (stepsPerSecond * 60.0).toFloat()
        stepFraction += stepsPerSecond * dt
        val whole = floor(stepFraction).toLong()
        if (whole > 0) {
            stepCount += whole
            stepFraction -= whole
            stepDetectedThisTick = true
        }
    }

    private fun buildTelemetry(): SensorTelemetry {
        val moving = currentSpeed > 0.08
        val cycle = mode == MotionMode.CYCLE
        val gait = if (moving) sin(stepPhase) else 0.0
        val gaitDouble = if (moving) sin(stepPhase * 2.0) else 0.0
        val amplitude = when (mode) {
            MotionMode.WALK -> 1.25
            MotionMode.RUN -> 3.4
            MotionMode.CYCLE -> 0.35
        } * (if (moving) 1.0 else 0.0)

        val forward = amplitude * 0.68 * gait
        val lateral = amplitude * 0.24 * sin(stepPhase * 0.5)
        val vertical = 9.80665 + amplitude * 0.82 * gaitDouble

        val gravity = 9.80665
        val gyroZ = currentTurnRate * PI / 180.0 + (if (moving) 0.16 * gait else 0.0)
        val gyroX = if (moving) amplitude * 0.09 * cos(stepPhase) else 0.0
        val gyroY = if (moving) amplitude * 0.04 * sin(stepPhase * 0.7) else 0.0

        return SensorTelemetry(
            accelX = forward.toFloat(),
            accelY = lateral.toFloat(),
            accelZ = vertical.toFloat(),
            gyroX = gyroX.toFloat(),
            gyroY = gyroY.toFloat(),
            gyroZ = gyroZ.toFloat(),
            gravityX = 0f,
            gravityY = 0f,
            gravityZ = gravity.toFloat(),
            linearX = forward.toFloat(),
            linearY = lateral.toFloat(),
            linearZ = (vertical - gravity).toFloat(),
            stepDetected = false,
            stepCount = stepCount,
        )
    }

    private fun appendTrackPoint(force: Boolean = false) {
        if (!force && track.size >= MAX_TRACK_POINTS) {
            if (track.isNotEmpty()) {
                if (random.nextInt(20) == 0) {
                    track.removeAt(0)
                }
            }
        }
        if (track.lastOrNull() != basePoint) {
            track.add(basePoint)
            if (track.size > MAX_TRACK_POINTS) track.removeAt(0)
        }
    }

    private fun moveTowards(current: Double, target: Double, maxDelta: Double): Double =
        if (abs(target - current) <= maxDelta) target else current + (target - current).coerceIn(-maxDelta, maxDelta)

    val routeJustCompleted: Boolean get() = routeCompletedThisTick

    companion object {
        private const val MAX_TRACK_POINTS = 1_200
    }
}
