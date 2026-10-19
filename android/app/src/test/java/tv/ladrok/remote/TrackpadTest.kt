// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTest {
    @Test
    fun thePointerFollowsTheFinger() {
        val pad = Trackpad(slowSteps = 2f, fastSteps = 2f)
        assertEquals(10 to -6, pad.move(dx = 5f, dy = -3f, seconds = 0.01f))
    }

    @Test
    fun aFastSwipeGoesFurtherThanASlowSlide() {
        val slow = Trackpad().move(dx = 1f, dy = 0f, seconds = 0.1f).first // 10 dp/s
        val fast = Trackpad().move(dx = 20f, dy = 0f, seconds = 0.01f).first / 20 // 2000 dp/s
        assertTrue("slow $slow, fast per dp $fast", fast > slow)
    }

    @Test
    fun slowSlidesStillAddUp() {
        val pad = Trackpad(slowSteps = 1f, fastSteps = 1f)
        val steps = List(10) { pad.move(dx = 0.3f, dy = 0f, seconds = 0.01f).first }
        assertEquals(3, steps.sum())
    }

    @Test
    fun oneMoveIsCappedAtWhatAReportCarries() {
        assertEquals(127 to -127, Trackpad().move(dx = 200f, dy = -200f, seconds = 0.01f))
    }

    @Test
    fun resetForgetsTheKeptFractions() {
        val pad = Trackpad(slowSteps = 1f, fastSteps = 1f)
        pad.move(dx = 0.4f, dy = 0f, seconds = 0.01f)
        pad.reset()
        assertEquals(0 to 0, pad.move(dx = 0.4f, dy = 0f, seconds = 0.01f))
    }
}
