// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns the phone's turning (gyroscope, radians per second) into mouse steps for the TV's pointer, like an
 * air mouse: turn left and the pointer goes left, tilt the top up and it goes up. The phone is held like a
 * remote, screen up and its top towards the TV, so left-right is a turn about the screen's z axis and
 * up-down a turn about its x axis. Fractions of a step are kept for the next reading, so slow turns still move.
 */
class Pointer(private val stepsPerRadian: Float = STEPS_PER_RADIAN, private val still: Float = STILL) {
    private var restX = 0f
    private var restY = 0f

    /** The steps for turning at [aboutX] and [aboutZ] rad/s for [seconds]; (0, 0) when the hand is still. */
    fun turn(aboutX: Float, aboutZ: Float, seconds: Float): Pair<Int, Int> {
        // Below [still] it's the hand's tremor or the sensor's drift, not a turn.
        val x = if (abs(aboutZ) < still) 0f else -aboutZ * stepsPerRadian * seconds + restX
        val y = if (abs(aboutX) < still) 0f else -aboutX * stepsPerRadian * seconds + restY
        val dx = x.roundToInt().coerceIn(-127, 127)
        val dy = y.roundToInt().coerceIn(-127, 127)
        restX = x - dx
        restY = y - dy
        return dx to dy
    }

    /** Forgets kept fractions, when the viewer lets go. */
    fun reset() {
        restX = 0f
        restY = 0f
    }

    companion object {
        /** A starting point: about 1000 steps for a 40° turn. */
        const val STEPS_PER_RADIAN = 1400f
        const val STILL = 0.02f
    }
}
