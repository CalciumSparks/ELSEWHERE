package com.elsewhere.location.core

/**
 * Collects a route from the live output while a mock session is running.
 * The recorder deliberately keeps its own list instead of reading the already
 * sampled session track, so a recording can start at an arbitrary moment and
 * still be saved as an independent route.
 */
class RouteRecorder(
    private val minPointDistanceMeters: Double = 1.5,
    private val maxPoints: Int = 512,
) {
    private val points = mutableListOf<GeoPoint>()
    private var startedAtEpochMs: Long = 0L
    private var distanceMeters: Double = 0.0
    private var lastPoint: GeoPoint? = null

    var isRecording: Boolean = false
        private set

    fun start(point: GeoPoint, nowEpochMs: Long = System.currentTimeMillis()) {
        reset()
        if (!point.isValid) return
        points += point
        lastPoint = point
        startedAtEpochMs = nowEpochMs
        isRecording = true
    }

    /** Returns true when the point was accepted. */
    fun add(point: GeoPoint, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (!isRecording || !point.isValid || points.size >= maxPoints) return false
        val previous = lastPoint
        if (previous == null) {
            points += point
            lastPoint = point
            return true
        }
        val step = Geo.distanceMeters(previous, point)
        if (step < minPointDistanceMeters) return false
        points += point
        lastPoint = point
        distanceMeters += step
        return true
    }

    fun snapshot(nowEpochMs: Long = System.currentTimeMillis()): RouteRecordingSnapshot =
        RouteRecordingSnapshot(
            points = points.toList(),
            startedAtEpochMs = startedAtEpochMs,
            elapsedSeconds = if (startedAtEpochMs == 0L) 0L else {
                ((nowEpochMs - startedAtEpochMs).coerceAtLeast(0L) / 1_000L)
            },
            distanceMeters = distanceMeters,
        )

    fun stop(): List<GeoPoint> {
        isRecording = false
        return points.toList()
    }

    fun reset() {
        points.clear()
        startedAtEpochMs = 0L
        distanceMeters = 0.0
        lastPoint = null
        isRecording = false
    }

    fun canSave(minimumPoints: Int = 2, minimumDistanceMeters: Double = 5.0): Boolean =
        points.size >= minimumPoints && distanceMeters >= minimumDistanceMeters
}

data class RouteRecordingSnapshot(
    val points: List<GeoPoint> = emptyList(),
    val startedAtEpochMs: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
) {
    val pointCount: Int get() = points.size
}
