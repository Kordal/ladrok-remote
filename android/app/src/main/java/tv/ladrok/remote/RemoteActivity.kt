// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Remote's only screen. The phone is the TV's keyboard and remote only while this is on screen:
 * a phone left connected would count as a keyboard, and the TV would stop offering its own on-screen one.
 */
class RemoteActivity : ComponentActivity() {
    private lateinit var remote: RemoteTransport

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { remote.start() }
    private val enable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { remote.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system bar icons on the black screen, whatever the phone's theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        remote = HidRemote(this)
        val actions = RemoteActions(
            onPress = remote::press,
            onRelease = remote::release,
            onType = remote::type,
            onConnect = remote::connect,
            onDisconnect = remote::disconnect,
            onSearch = remote::search,
            onStopSearching = remote::stopSearching,
            onAllow = ::allow,
            onSwitchOn = { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        )
        setContent {
            val link by remote.link.collectAsStateWithLifecycle()
            val tvs by remote.tvs.collectAsStateWithLifecycle()
            val found by remote.found.collectAsStateWithLifecycle()
            RemoteScreen(link, tvs, found, actions)
        }
        if (savedInstanceState == null) askPermissions()
    }

    private fun askPermissions() = permissions.launch(BLUETOOTH)

    // Once the viewer has said no twice, Android stops asking: only the app's settings can allow it then.
    private fun allow() {
        if (BLUETOOTH.any(::shouldShowRequestPermissionRationale)) return askPermissions()
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun onStart() {
        super.onStart()
        remote.start()
    }

    override fun onStop() {
        remote.stop()
        super.onStop()
    }

    private companion object {
        val BLUETOOTH = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }
}
