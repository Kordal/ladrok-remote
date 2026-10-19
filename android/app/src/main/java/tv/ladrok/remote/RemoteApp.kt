// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the one [HidRemote], so the link to the TV outlives the screen: minimized, the Remote stays the
 * TV's keyboard and remote while [RemoteService] keeps the app running, unless Settings say otherwise.
 */
class RemoteApp : Application() {
    val remote: RemoteTransport by lazy { HidRemote(this) }

    /** Whether the Remote screen is on screen; the service lets go of Bluetooth only when it isn't. */
    var visible = false

    private val prefs by lazy { getSharedPreferences("remote", Context.MODE_PRIVATE) }
    private val _stayConnected by lazy { MutableStateFlow(prefs.getBoolean(STAY_CONNECTED, true)) }

    /** Settings: stay connected to the TV while minimized (with a notification), or only while on screen. */
    val stayConnected: StateFlow<Boolean> get() = _stayConnected.asStateFlow()

    fun setStayConnected(on: Boolean) {
        prefs.edit { putBoolean(STAY_CONNECTED, on) }
        _stayConnected.value = on
    }

    private val _airMouse by lazy { MutableStateFlow(prefs.getBoolean(AIR_MOUSE, false)) }

    /** Settings: the Pointer pad as a trackpad (the default), or as the air mouse that follows the phone. */
    val airMouse: StateFlow<Boolean> get() = _airMouse.asStateFlow()

    fun setAirMouse(on: Boolean) {
        prefs.edit { putBoolean(AIR_MOUSE, on) }
        _airMouse.value = on
    }

    private companion object {
        const val STAY_CONNECTED = "stay_connected"
        const val AIR_MOUSE = "air_mouse"
    }
}
