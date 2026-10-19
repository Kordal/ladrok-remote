// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a finger sliding on the pad (in dp) into mouse steps for the TV's pointer, like a laptop's trackpad:
 * slow slides move it a little for aiming, fast ones cross the screen. The gain grows from [slowSteps] to
 * [fastSteps] steps per dp as the finger speeds up to [fastSpeed] dp per second. Fractions of a step are
 * kept for the next move, so slow slides still move.
 */
class Trackpad(
    private val slowSteps: Float = SLOW_STEPS,
    private val fastSteps: Float = FAST_STEPS,
    private val fastSpeed: Float = FAST_SPEED,
) {
    private var restX = 0f
    private var restY = 0f

    /** The steps for the finger moving [dx], [dy] dp in [seconds]. */
    fun move(dx: Float, dy: Float, seconds: Float): Pair<Int, Int> {
        val speed = if (seconds > 0f) sqrt(dx * dx + dy * dy) / seconds else 0f
        val gain = slowSteps + (fastSteps - slowSteps) * (speed / fastSpeed).coerceIn(0f, 1f)
        val x = dx * gain + restX
        val y = dy * gain + restY
        val stepX = x.roundToInt().coerceIn(-127, 127)
        val stepY = y.roundToInt().coerceIn(-127, 127)
        restX = x - stepX
        restY = y - stepY
        return stepX to stepY
    }

    /** Forgets the kept fractions, when the finger lifts. */
    fun reset() {
        restX = 0f
        restY = 0f
    }

    companion object {
        /** About 500 steps across a phone's pad when aiming, about 1500 in a quick swipe. */
        const val SLOW_STEPS = 1.5f
        const val FAST_STEPS = 4.5f
        const val FAST_SPEED = 1500f
    }
}
