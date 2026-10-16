// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

/** A TV the Remote knows over Bluetooth. */
data class Tv(val address: String, val name: String)

/** A TV's Bluetooth connection, as the HID device profile reports it. */
enum class TvState { Connecting, Connected, Disconnecting, Disconnected }

/** The Link between the Remote and a TV: what the Remote screen shows above its keys. */
sealed interface Link {
    /** No Bluetooth, or this phone doesn't offer the HID device profile. Tried again each time the Remote opens. */
    data object Unsupported : Link
    /** The viewer hasn't allowed the Remote to use Bluetooth. */
    data object NoPermission : Link
    /** Bluetooth is switched off. */
    data object Off : Link
    /** Registering the Remote as a keyboard and remote with the phone's Bluetooth. */
    data object Starting : Link
    /** Ready, no TV connected; [searching] for TVs nearby to pair with. */
    data class Waiting(val searching: Boolean) : Link
    data class Connecting(val tv: Tv) : Link
    data class Connected(val tv: Tv) : Link

    fun next(event: LinkEvent): Link = when {
        // A slow phone can still register after the Remote gave up on it.
        this == Unsupported -> when (event) {
            LinkEvent.Start -> Starting
            LinkEvent.Registered(true) -> Waiting(searching = false)
            else -> this
        }
        event == LinkEvent.Unsupported -> Unsupported
        event == LinkEvent.NoPermission -> NoPermission
        event == LinkEvent.BluetoothOff -> Off
        event == LinkEvent.Start -> if (this == Off || this == NoPermission) Starting else this
        this == Off || this == NoPermission -> this
        event is LinkEvent.Registered -> when {
            !event.registered -> Starting
            this == Starting -> Waiting(searching = false)
            else -> this
        }
        event is LinkEvent.Searching -> if (this is Waiting) Waiting(event.on) else this
        event is LinkEvent.TvChanged -> when (event.state) {
            TvState.Connecting -> Connecting(event.tv)
            TvState.Connected -> Connected(event.tv)
            TvState.Disconnecting -> this
            TvState.Disconnected -> if (tv() == event.tv) Waiting(searching = false) else this
        }
        else -> this
    }

    private fun tv(): Tv? = when (this) {
        is Connecting -> tv
        is Connected -> tv
        else -> null
    }
}

/** What happens to the Link: from the system, the HID device profile, or the viewer. */
sealed interface LinkEvent {
    data object Unsupported : LinkEvent
    data object NoPermission : LinkEvent
    data object BluetoothOff : LinkEvent
    /** Bluetooth is on and allowed: register (again). */
    data object Start : LinkEvent
    data class Registered(val registered: Boolean) : LinkEvent
    data class Searching(val on: Boolean) : LinkEvent
    data class TvChanged(val tv: Tv, val state: TvState) : LinkEvent
}
