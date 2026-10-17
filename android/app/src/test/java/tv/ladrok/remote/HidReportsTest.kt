// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HidReportsTest {

    @Test
    fun anArrowIsAKeyboardKeyAndReleasesToAllZero() {
        val down = HidReports.press(RemoteKey.Up)
        assertEquals(HidReports.KEYBOARD, down.id)
        assertArrayEquals(bytes(0, 0, 0x52, 0, 0, 0, 0, 0), down.data)

        val up = HidReports.release(RemoteKey.Up)
        assertEquals(HidReports.KEYBOARD, up.id)
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0, 0, 0), up.data)
    }

    @Test
    fun theArrowsAreTheKeyboardArrows() {
        assertEquals(0x4F, keyOf(RemoteKey.Right))
        assertEquals(0x50, keyOf(RemoteKey.Left))
        assertEquals(0x51, keyOf(RemoteKey.Down))
        assertEquals(0x52, keyOf(RemoteKey.Up))
    }

    @Test
    fun digitsAreTheKeyboardDigitsSoTheLauncherKeypadOpensTiles() {
        assertEquals(0x1E, keyOf(RemoteKey.One))
        assertEquals(0x26, keyOf(RemoteKey.Nine))
    }

    @Test
    fun homeIsAConsumerControlLittleEndian() {
        val down = HidReports.press(RemoteKey.Home)
        assertEquals(HidReports.CONSUMER, down.id)
        assertArrayEquals(bytes(0x23, 0x02), down.data) // AC Home, 0x0223

        val up = HidReports.release(RemoteKey.Home)
        assertEquals(HidReports.CONSUMER, up.id)
        assertArrayEquals(bytes(0, 0), up.data)
    }

    @Test
    fun theTvKeysAreConsumerControls() {
        assertEquals(0x0041, usageOf(RemoteKey.Ok)) // Menu Pick: DPAD_CENTER, not Enter
        assertEquals(0x0224, usageOf(RemoteKey.Back))
        assertEquals(0x00CD, usageOf(RemoteKey.PlayPause))
        assertEquals(0x00B4, usageOf(RemoteKey.Rewind))
        assertEquals(0x00B3, usageOf(RemoteKey.FastForward))
        assertEquals(0x00E9, usageOf(RemoteKey.VolumeUp))
        assertEquals(0x00EA, usageOf(RemoteKey.VolumeDown))
        assertEquals(0x00E2, usageOf(RemoteKey.Mute))
    }

    @Test
    fun anIdleReportHoldsNothingAndMatchesTheReleases() {
        assertEquals(HidReports.release(RemoteKey.Up), HidReports.idle(HidReports.KEYBOARD))
        assertEquals(HidReports.release(RemoteKey.Home), HidReports.idle(HidReports.CONSUMER))
        assertEquals(null, HidReports.idle(4))
    }

    @Test
    fun sleepIsLadrokTvsTimerKeyAsAConsumerControl() {
        val report = HidReports.press(RemoteKey.Sleep)
        assertEquals(HidReports.CONSUMER, report.id)
        assertArrayEquals(bytes(0x36, 0x00), report.data)
        assertArrayEquals(bytes(0, 0), HidReports.release(RemoteKey.Sleep).data)
    }

    @Test
    fun everyKeyHasAReport() {
        for (key in RemoteKey.entries) {
            val report = HidReports.press(key)
            val size = if (report.id == HidReports.KEYBOARD) 8 else 2
            assertEquals("$key", size, report.data.size)
        }
    }

    @Test
    fun typingALetterPressesAndReleasesIt() {
        val reports = HidReports.type("a")
        assertEquals(2, reports.size)
        assertArrayEquals(bytes(0, 0, 0x04, 0, 0, 0, 0, 0), reports[0].data)
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0, 0, 0), reports[1].data)
    }

    @Test
    fun capitalsAndSymbolsHoldLeftShift() {
        val shift = 0x02
        assertArrayEquals(bytes(shift, 0, 0x04, 0, 0, 0, 0, 0), HidReports.type("A")[0].data)
        assertArrayEquals(bytes(shift, 0, 0x1F, 0, 0, 0, 0, 0), HidReports.type("@")[0].data)
        assertArrayEquals(bytes(shift, 0, 0x38, 0, 0, 0, 0, 0), HidReports.type("?")[0].data)
    }

    @Test
    fun typingSpacesDigitsAndPunctuation() {
        assertEquals(listOf(0x2C, 0x1E, 0x27, 0x2D, 0x37, 0x36), HidReports.type(" 10-.,").filterIndexed { i, _ -> i % 2 == 0 }.map { it.data[2].toInt() })
    }

    @Test
    fun charactersAKeyboardCannotTypeAreSkipped() {
        assertEquals(HidReports.type("ab"), HidReports.type("aéb"))
    }

    @Test
    fun theDescriptorDeclaresBothReportsAtTheirSizes() {
        assertEquals(mapOf(HidReports.KEYBOARD to 8, HidReports.CONSUMER to 2, HidReports.MOUSE to 4), inputBytesPerReport(HidReports.DESCRIPTOR))
    }

    @Test
    fun theMouseSendsButtonsThenSignedMoves() {
        assertEquals(Report(HidReports.MOUSE, bytes(0x01, 5, -3, 0)), HidReports.mouse(HidReports.LEFT_BUTTON, 5, -3))
        assertEquals(Report(HidReports.MOUSE, bytes(0, 127, -127, 1)), HidReports.mouse(0, 300, -300, 1))
        assertEquals(HidReports.mouse(0, 0, 0), HidReports.idle(HidReports.MOUSE))
    }

    private fun keyOf(key: RemoteKey): Int {
        val report = HidReports.press(key)
        assertEquals("$key", HidReports.KEYBOARD, report.id)
        return report.data[2].toInt() and 0xFF
    }

    private fun usageOf(key: RemoteKey): Int {
        val report = HidReports.press(key)
        assertEquals("$key", HidReports.CONSUMER, report.id)
        return (report.data[0].toInt() and 0xFF) or ((report.data[1].toInt() and 0xFF) shl 8)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** Walks the descriptor's short items and adds up each report's Input bits, as a HID host would. */
    private fun inputBytesPerReport(descriptor: ByteArray): Map<Int, Int> {
        val bits = linkedMapOf<Int, Int>()
        var reportId = 0
        var size = 0
        var count = 0
        var i = 0
        while (i < descriptor.size) {
            val prefix = descriptor[i].toInt() and 0xFF
            val length = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            var value = 0
            for (b in 0 until length) value = value or ((descriptor[i + 1 + b].toInt() and 0xFF) shl (8 * b))
            when (prefix and 0xFC) {
                0x84 -> reportId = value
                0x74 -> size = value
                0x94 -> count = value
                0x80 -> bits[reportId] = (bits[reportId] ?: 0) + size * count
            }
            i += 1 + length
        }
        return bits.mapValues { it.value / 8 }
    }
}
