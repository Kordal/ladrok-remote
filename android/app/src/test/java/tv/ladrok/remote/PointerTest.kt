// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PointerTest {
    @Test
    fun turningLeftMovesLeftAndTiltingUpMovesUp() {
        val pointer = Pointer(stepsPerRadian = 1000f)
        // Counter-clockwise about z (top swings left) and about x (top tilts up), for 10 ms.
        assertEquals(-10 to -5, pointer.turn(aboutX = 0.5f, aboutZ = 1f, seconds = 0.01f))
        assertEquals(10 to 5, pointer.turn(aboutX = -0.5f, aboutZ = -1f, seconds = 0.01f))
    }

    @Test
    fun aStillHandDoesNotMoveThePointer() {
        assertEquals(0 to 0, Pointer().turn(aboutX = 0.01f, aboutZ = -0.01f, seconds = 0.02f))
    }

    @Test
    fun slowTurnsAddUpInsteadOfRoundingAway() {
        val pointer = Pointer(stepsPerRadian = 100f)
        // 0.3 of a step each time: four turns make one step.
        val steps = List(4) { pointer.turn(aboutX = 0f, aboutZ = -0.3f, seconds = 0.01f).first }
        assertEquals(1, steps.sum())
    }

    @Test
    fun aFastTurnIsCappedToOneReport() {
        assertEquals(127 to 0, Pointer().turn(aboutX = 0f, aboutZ = -20f, seconds = 0.02f))
    }

    @Test
    fun resetForgetsKeptFractions() {
        val pointer = Pointer(stepsPerRadian = 100f)
        pointer.turn(aboutX = 0f, aboutZ = -0.4f, seconds = 0.01f)
        pointer.reset()
        assertEquals(0 to 0, pointer.turn(aboutX = 0f, aboutZ = -0.4f, seconds = 0.01f))
    }
}
