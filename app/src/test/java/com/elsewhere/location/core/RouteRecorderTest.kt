package com.elsewhere.location.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteRecorderTest {

    private val start = GeoPoint(31.23040, 121.47370)

    @Test
    fun `rejects points that are too close and tracks distance`() {
        val recorder = RouteRecorder(minPointDistanceMeters = 2.0)
        recorder.start(start, nowEpochMs = 1_000L)
        val near = Geo.destination(start, 90.0, 1.0)
        val far = Geo.destination(start, 90.0, 8.0)

        assertThat(recorder.add(near, nowEpochMs = 2_000L)).isFalse()
        assertThat(recorder.add(far, nowEpochMs = 3_500L)).isTrue()

        val snapshot = recorder.snapshot(nowEpochMs = 5_000L)
        assertThat(snapshot.pointCount).isEqualTo(2)
        assertThat(snapshot.elapsedSeconds).isEqualTo(4L)
        assertThat(snapshot.distanceMeters).isWithin(1.0).of(8.0)
        assertThat(recorder.canSave()).isTrue()
    }

    @Test
    fun `a short recording cannot be saved`() {
        val recorder = RouteRecorder(minPointDistanceMeters = 1.0)
        recorder.start(start, nowEpochMs = 1_000L)
        recorder.add(Geo.destination(start, 0.0, 2.0), nowEpochMs = 2_000L)
        assertThat(recorder.canSave()).isFalse()
        assertThat(recorder.stop()).hasSize(2)
        assertThat(recorder.isRecording).isFalse()
    }

    @Test
    fun `starting again resets the previous recording`() {
        val recorder = RouteRecorder()
        recorder.start(start, nowEpochMs = 1_000L)
        recorder.add(Geo.destination(start, 90.0, 10.0), nowEpochMs = 2_000L)
        val other = GeoPoint(39.9042, 116.4074)
        recorder.start(other, nowEpochMs = 10_000L)
        val snapshot = recorder.snapshot(nowEpochMs = 12_000L)
        assertThat(snapshot.pointCount).isEqualTo(1)
        assertThat(snapshot.distanceMeters).isEqualTo(0.0)
        assertThat(snapshot.startedAtEpochMs).isEqualTo(10_000L)
        assertThat(snapshot.elapsedSeconds).isEqualTo(2L)
    }
}
