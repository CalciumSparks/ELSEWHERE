package com.elsewhere.location.location

import com.elsewhere.location.core.DriftConfig
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Produces a slow, continuous offset around the ideal route. This is intentionally
 * smooth: a per-frame random jump looks like a broken GPS, not a real one.
 */
class DriftGenerator(
    private val config: DriftConfig,
    seed: Long = Random.nextLong(),
) {
    private var time = 0.0
    private val phaseA = (seed % 10_000L) / 10_000.0 * 2.0 * PI
    private val phaseB = ((seed / 10_000L) % 10_000L) / 10_000.0 * 2.0 * PI

    fun advance(dtSeconds: Double): DriftSample {
        time += dtSeconds.coerceIn(0.0, 2.0)
        if (!config.enabled || config.amplitudeMeters <= 0f) return DriftSample(0.0, 0.0)

        val frequency = config.tempo.frequency
        val amplitude = config.amplitudeMeters.toDouble()
        val main = 2.0 * PI * frequency * time

        val east = amplitude * (
            0.58 * sin(main + phaseA) +
                0.26 * sin(main * 2.31 + phaseB) +
                0.16 * sin(time * 0.37 + phaseA * 0.5)
            )
        val north = amplitude * (
            0.51 * cos(main * 0.83 + phaseB) +
                0.29 * sin(main * 1.91 + phaseA) +
                0.13 * cos(time * 0.23 + phaseB * 0.7)
            )
        return DriftSample(eastMeters = east, northMeters = north)
    }

    @Serializable
    data class DriftSample(
        val eastMeters: Double,
        val northMeters: Double,
    )
}
