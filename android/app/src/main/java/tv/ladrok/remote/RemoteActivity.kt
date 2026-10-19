// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Remote's only screen. Minimized while a TV is connected, the Remote stays connected ([RemoteService],
 * with a notification to Disconnect), unless Settings say to stay connected only while on screen; with no
 * TV connected it lets go of Bluetooth until it's back on screen.
 */
class RemoteActivity : ComponentActivity() {
    private val app get() = application as RemoteApp
    private val remote get() = app.remote

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { remote.start() }
    private val enable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { remote.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system bar icons on the black screen, whatever the phone's theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val actions = RemoteActions(
            onPress = remote::press,
            onRelease = remote::release,
            onType = remote::type,
            onMouse = remote::mouse,
            onConnect = remote::connect,
            onDisconnect = remote::disconnect,
            onSearch = remote::search,
            onStopSearching = remote::stopSearching,
            onAllow = ::allow,
            onSwitchOn = { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onStayConnected = { app.setStayConnected(it) },
            onAirMouse = { app.setAirMouse(it) },
        )
        setContent {
            val link by remote.link.collectAsStateWithLifecycle()
            val stayConnected by app.stayConnected.collectAsStateWithLifecycle()
            // Started while on screen, as Android only lets a foreground service start then.
            val keep = link is Link.Connected && stayConnected
            LaunchedEffect(keep) {
                if (keep) RemoteService.start(this@RemoteActivity) else if (!stayConnected) RemoteService.stop(this@RemoteActivity)
            }
            val tvs by remote.tvs.collectAsStateWithLifecycle()
            val found by remote.found.collectAsStateWithLifecycle()
            val airMouse by app.airMouse.collectAsStateWithLifecycle()
            RemoteScreen(link, tvs, found, stayConnected, airMouse, actions)
        }
        if (savedInstanceState == null) askPermissions()
    }

    private fun askPermissions() = permissions.launch(
        // Without the notification the Remote still stays connected, but there's nothing to Disconnect from.
        if (Build.VERSION.SDK_INT >= 33) BLUETOOTH + Manifest.permission.POST_NOTIFICATIONS else BLUETOOTH,
    )

    // Once the viewer has said no twice, Android stops asking: only the app's settings can allow it then.
    private fun allow() {
        if (BLUETOOTH.any(::shouldShowRequestPermissionRationale)) return askPermissions()
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun onStart() {
        super.onStart()
        app.visible = true
        remote.start()
    }

    override fun onStop() {
        app.visible = false
        // Connected and set to stay connected, the service holds on; otherwise nothing needs Bluetooth now.
        if (remote.link.value !is Link.Connected || !app.stayConnected.value) remote.stop()
        super.onStop()
    }

    private companion object {
        val BLUETOOTH = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }
}
