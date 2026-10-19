// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.app.Application

/**
 * Holds the one [HidRemote], so the link to the TV outlives the screen: minimized, the Remote stays the
 * TV's keyboard and remote while [RemoteService] keeps the app running.
 */
class RemoteApp : Application() {
    val remote: RemoteTransport by lazy { HidRemote(this) }

    /** Whether the Remote screen is on screen; the service lets go of Bluetooth only when it isn't. */
    var visible = false
}
