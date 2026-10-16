// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * How the Remote reaches a TV. The screen only talks to this, so another way in (the TV-side
 * Bluetooth LE service an iPhone will need, see docs/adr/0001-the-remote-is-a-bluetooth-keyboard.md) can replace [HidRemote] without
 * touching the keys.
 */
interface RemoteTransport {
    val link: StateFlow<Link>

    /** Paired TVs to choose from, the last one first. */
    val tvs: StateFlow<List<Tv>>

    /** TVs found nearby while searching, in the order found. */
    val found: StateFlow<List<Tv>>

    /** Begin (or retry, after Bluetooth was switched on or allowed); reconnects to the last TV. */
    fun start()

    fun stop()

    fun connect(tv: Tv)

    /** Looks for TVs nearby to pair with, for two minutes; they appear in [found]. */
    fun search()

    fun stopSearching()

    fun disconnect()

    fun press(key: RemoteKey)

    fun release(key: RemoteKey)

    /** Types [text] into whatever has focus on the TV, such as a search box. */
    fun type(text: String)
}
