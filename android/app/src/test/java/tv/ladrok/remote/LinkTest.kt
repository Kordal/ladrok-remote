// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.ladrok.remote.LinkEvent.BluetoothOff
import tv.ladrok.remote.LinkEvent.Searching
import tv.ladrok.remote.LinkEvent.NoPermission
import tv.ladrok.remote.LinkEvent.Registered
import tv.ladrok.remote.LinkEvent.Start
import tv.ladrok.remote.LinkEvent.TvChanged
import tv.ladrok.remote.LinkEvent.Unsupported

class LinkTest {
    private val lounge = Tv("AA:BB:CC:DD:EE:01", "Ladrok TV")
    private val bedroom = Tv("AA:BB:CC:DD:EE:02", "Bedroom")

    private fun Link.after(vararg events: LinkEvent) = events.fold(this) { link, event -> link.next(event) }

    @Test
    fun startingThenRegisteringWaitsForATv() {
        assertEquals(Link.Waiting(searching = false), Link.Starting.after(Registered(true)))
    }

    @Test
    fun aTvConnectingThenConnected() {
        val waiting = Link.Waiting(searching = false)
        assertEquals(Link.Connecting(lounge), waiting.after(TvChanged(lounge, TvState.Connecting)))
        assertEquals(Link.Connected(lounge), waiting.after(TvChanged(lounge, TvState.Connecting), TvChanged(lounge, TvState.Connected)))
    }

    @Test
    fun aTvConnectingEndsSearching() {
        assertEquals(Link.Connected(lounge), Link.Waiting(searching = true).after(TvChanged(lounge, TvState.Connected)))
    }

    @Test
    fun theTvDisconnectingGoesBackToWaiting() {
        assertEquals(Link.Waiting(searching = false), Link.Connected(lounge).after(TvChanged(lounge, TvState.Disconnecting), TvChanged(lounge, TvState.Disconnected)))
        assertEquals(Link.Waiting(searching = false), Link.Connecting(lounge).after(TvChanged(lounge, TvState.Disconnected)))
    }

    @Test
    fun anotherTvDisconnectingChangesNothing() {
        assertEquals(Link.Connected(lounge), Link.Connected(lounge).after(TvChanged(bedroom, TvState.Disconnected)))
    }

    @Test
    fun anotherTvConnectingTakesOver() {
        assertEquals(Link.Connected(bedroom), Link.Connected(lounge).after(TvChanged(bedroom, TvState.Connected)))
    }

    @Test
    fun searchingIsShownOnlyWhileWaiting() {
        assertEquals(Link.Waiting(searching = true), Link.Waiting(searching = false).after(Searching(true)))
        assertEquals(Link.Waiting(searching = false), Link.Waiting(searching = true).after(Searching(false)))
        assertEquals(Link.Connected(lounge), Link.Connected(lounge).after(Searching(true)))
    }

    @Test
    fun losingTheRegistrationStartsOver() {
        assertEquals(Link.Starting, Link.Connected(lounge).after(Registered(false)))
    }

    @Test
    fun bluetoothOffAndMissingPermissionWinFromAnywhere() {
        assertEquals(Link.Off, Link.Connected(lounge).after(BluetoothOff))
        assertEquals(Link.NoPermission, Link.Waiting(searching = true).after(NoPermission))
    }

    @Test
    fun tvEventsWhileOffAreIgnoredUntilStarted() {
        assertEquals(Link.Off, Link.Off.after(TvChanged(lounge, TvState.Connected), Registered(true)))
        assertEquals(Link.Starting, Link.Off.after(Start))
        assertEquals(Link.Starting, Link.NoPermission.after(Start))
    }

    @Test
    fun startDoesNotDropAConnection() {
        assertEquals(Link.Connected(lounge), Link.Connected(lounge).after(Start))
    }

    @Test
    fun unsupportedIsFinal() {
        assertEquals(Link.Unsupported, Link.Waiting(searching = false).after(Unsupported, Start, Registered(true), TvChanged(lounge, TvState.Connected)))
    }

    @Test
    fun theRememberedTvIsTheOneToReconnect() {
        assertEquals(bedroom, Tvs.toReconnect(listOf(lounge, bedroom), lastAddress = bedroom.address))
        assertNull(Tvs.toReconnect(listOf(lounge), lastAddress = bedroom.address))
        assertNull(Tvs.toReconnect(listOf(lounge, bedroom), lastAddress = null))
    }

    @Test
    fun theLastTvIsListedFirstThenByName() {
        val attic = Tv("AA:BB:CC:DD:EE:03", "Attic")
        assertEquals(listOf(lounge, attic, bedroom), Tvs.ordered(listOf(bedroom, lounge, attic), lastAddress = lounge.address))
    }

    @Test
    fun foundTvsAreAddedOnceAndKeepTheirNewestName() {
        val found = Tvs.found(Tvs.found(emptyList(), Tv(lounge.address, lounge.address)), lounge)
        assertEquals(listOf(lounge), Tvs.found(found, bedroom).take(1))
        assertEquals(listOf(lounge, bedroom), Tvs.found(found, bedroom))
    }

    @Test
    fun ladrokTvsClassOfDeviceLooksLikeATv() {
        // bluetooth.device.class_of_device=34,4,36 in Ladrok TV's device tree: services, major, minor.
        val ladrok = (34 shl 16) or (4 shl 8) or 36
        assertTrue(Tvs.looksLikeTv(ladrok))
    }

    @Test
    fun screensAndBoxesLookLikeTvsButHeadphonesAndPhonesDoNot() {
        assertTrue(Tvs.looksLikeTv(0x043C)) // video display and loudspeaker
        assertTrue(Tvs.looksLikeTv(0x0438)) // video monitor
        assertFalse(Tvs.looksLikeTv(0x0418)) // headphones
        assertFalse(Tvs.looksLikeTv(0x5A020C)) // a smartphone
    }
}
