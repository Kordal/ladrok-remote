// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerTest {
    private fun raw(stepsPerRadian: Float) = Pointer(stepsPerRadian, still = 0f, smoothing = 0f)

    @Test
    fun turningLeftMovesLeftAndTiltingUpMovesUp() {
        val pointer = raw(1000f)
        // Counter-clockwise about z (top swings left) and about x (top tilts up), for 10 ms.
        assertEquals(-10 to -5, pointer.turn(aboutX = 0.5f, aboutZ = 1f, seconds = 0.01f))
        assertEquals(10 to 5, pointer.turn(aboutX = -0.5f, aboutZ = -1f, seconds = 0.01f))
    }

    @Test
    fun aStillHandDoesNotMoveThePointer() {
        assertEquals(0 to 0, Pointer().turn(aboutX = 0.04f, aboutZ = -0.04f, seconds = 0.02f))
    }

    @Test
    fun aTurnCountsFromTheTremorUp() {
        // 0.15 rad/s less the 0.05 of tremor, for 100 ms.
        assertEquals(10 to 0, Pointer(1000f, still = 0.05f, smoothing = 0f).turn(aboutX = 0f, aboutZ = -0.15f, seconds = 0.1f))
    }

    @Test
    fun aJoltIsSmoothedButASteadyTurnGetsThere() {
        val pointer = Pointer(1000f, still = 0f, smoothing = 0.05f)
        val first = pointer.turn(aboutX = 0f, aboutZ = -1f, seconds = 0.01f).first
        assertTrue("first step $first", first in 1..3)
        val later = List(50) { pointer.turn(aboutX = 0f, aboutZ = -1f, seconds = 0.01f).first }.takeLast(10)
        // 10 steps per reading by then, give or take a kept fraction.
        assertTrue("later steps $later", later.all { it in 9..11 } && later.sum() in 99..101)
    }

    @Test
    fun slowTurnsAddUpInsteadOfRoundingAway() {
        val pointer = raw(100f)
        // 0.3 of a step each time: four turns make one step.
        val steps = List(4) { pointer.turn(aboutX = 0f, aboutZ = -0.3f, seconds = 0.01f).first }
        assertEquals(1, steps.sum())
    }

    @Test
    fun aFastTurnIsCappedToOneReport() {
        assertEquals(127 to 0, raw(Pointer.STEPS_PER_RADIAN).turn(aboutX = 0f, aboutZ = -20f, seconds = 0.02f))
    }

    @Test
    fun resetForgetsKeptFractionsAndTheSmoothedTurn() {
        val pointer = Pointer(100f, still = 0f, smoothing = 0.05f)
        repeat(20) { pointer.turn(aboutX = 0f, aboutZ = -2f, seconds = 0.01f) }
        pointer.reset()
        assertEquals(0 to 0, pointer.turn(aboutX = 0f, aboutZ = 0f, seconds = 0.01f))
    }
}
