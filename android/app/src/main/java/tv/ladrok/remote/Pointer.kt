// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Turns the phone's turning (gyroscope, radians per second) into mouse steps for the TV's pointer, like an
 * air mouse: turn left and the pointer goes left, tilt the top up and it goes up. The phone is held like a
 * remote, screen up and its top towards the TV, so left-right is a turn about the screen's z axis and
 * up-down a turn about its x axis. Readings are smoothed over [smoothing] seconds to take out the hand's
 * jitter, and fractions of a step are kept for the next reading, so slow turns still move.
 */
class Pointer(
    private val stepsPerRadian: Float = STEPS_PER_RADIAN,
    private val still: Float = STILL,
    private val smoothing: Float = SMOOTHING,
) {
    private var rateX = 0f
    private var rateZ = 0f
    private var restX = 0f
    private var restY = 0f

    /** The steps for turning at [aboutX] and [aboutZ] rad/s for [seconds]; (0, 0) when the hand is still. */
    fun turn(aboutX: Float, aboutZ: Float, seconds: Float): Pair<Int, Int> {
        val follow = if (smoothing > 0f) seconds / (smoothing + seconds) else 1f
        rateX += (aboutX - rateX) * follow
        rateZ += (aboutZ - rateZ) * follow
        val x = -calm(rateZ) * stepsPerRadian * seconds + restX
        val y = -calm(rateX) * stepsPerRadian * seconds + restY
        val dx = x.roundToInt().coerceIn(-127, 127)
        val dy = y.roundToInt().coerceIn(-127, 127)
        restX = if (calm(rateZ) == 0f) 0f else x - dx
        restY = if (calm(rateX) == 0f) 0f else y - dy
        return dx to dy
    }

    // Up to [still] it's the hand's tremor or the sensor's drift, not a turn. Above it, the turn counts from
    // [still] up, so the pointer starts moving gently instead of jumping.
    private fun calm(rate: Float) = if (abs(rate) <= still) 0f else rate - sign(rate) * still

    /** Forgets the smoothed turn and kept fractions, when the pointer stops. */
    fun reset() {
        rateX = 0f
        rateZ = 0f
        restX = 0f
        restY = 0f
    }

    companion object {
        /** About 500 steps for a 40° turn. */
        const val STEPS_PER_RADIAN = 700f
        const val STILL = 0.05f
        const val SMOOTHING = 0.06f
    }
}
