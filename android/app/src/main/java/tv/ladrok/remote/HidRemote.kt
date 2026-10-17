// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Remote as a Bluetooth keyboard and remote control (the HID device profile): the TV sees an ordinary
 * Bluetooth accessory, so it needs nothing installed. Everything runs on the main thread.
 */
@SuppressLint("MissingPermission") // every Bluetooth call is behind allowed() or scanAllowed()
class HidRemote(context: Context) : RemoteTransport {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val prefs = context.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private val _link = MutableStateFlow<Link>(Link.Starting)
    override val link: StateFlow<Link> = _link.asStateFlow()
    private val _tvs = MutableStateFlow<List<Tv>>(emptyList())
    override val tvs: StateFlow<List<Tv>> = _tvs.asStateFlow()
    private val _found = MutableStateFlow<List<Tv>>(emptyList())
    override val found: StateFlow<List<Tv>> = _found.asStateFlow()

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var listening = false
    private var proxyRequested = false
    /** A TV being paired, to connect to once bonded. */
    private var pending: BluetoothDevice? = null

    private var lastAddress: String?
        get() = prefs.getString("last_tv", null)
        set(value) = prefs.edit { putString("last_tv", value) }

    private fun on(event: LinkEvent) {
        _link.value = _link.value.next(event)
    }

    private fun allowed() = granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun scanAllowed() = granted(Manifest.permission.BLUETOOTH_SCAN)

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun start() {
        if (adapter == null) return on(LinkEvent.Unsupported)
        if (!allowed()) return on(LinkEvent.NoPermission)
        listen()
        if (!adapter.isEnabled) return on(LinkEvent.BluetoothOff)
        on(LinkEvent.Start)
        refreshTvs()
        if (hid != null || proxyRequested) return
        proxyRequested = true
        if (!adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE)) return on(LinkEvent.Unsupported)
        // Some phones' Bluetooth never offers the HID device profile: they just don't answer.
        main.postDelayed(noProfile, PROFILE_TIMEOUT_MS)
    }

    // A TV adding an accessory is discoverable but skips phones, so the phone looks for the TV instead.
    override fun search() {
        if (!allowed() || !scanAllowed() || _link.value !is Link.Waiting) return
        _found.value = emptyList()
        on(LinkEvent.Searching(true))
        if (adapter?.startDiscovery() != true) stopSearching()
        main.removeCallbacks(searchOver)
        main.postDelayed(searchOver, SEARCH_MS)
    }

    override fun stopSearching() {
        main.removeCallbacks(searchOver)
        if (allowed() && scanAllowed()) adapter?.cancelDiscovery()
        on(LinkEvent.Searching(false))
    }

    private val searchOver = Runnable { stopSearching() }

    override fun stop() {
        if (scanAllowed()) adapter?.cancelDiscovery()
        if (listening) context.unregisterReceiver(receiver)
        listening = false
        if (allowed()) hid?.unregisterApp()
        reset()
        on(LinkEvent.Registered(false))
    }

    private fun searching() = _link.value.let { it is Link.Waiting && it.searching }

    /** Forgets the profile and the TV, closing the proxy so it can't call back in later. */
    private fun reset() {
        main.removeCallbacks(noProfile)
        main.removeCallbacks(searchOver)
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        host = null
        pending = null
        proxyRequested = false
    }

    override fun connect(tv: Tv) {
        val hid = hid ?: return
        if (!allowed()) return
        if (searching()) stopSearching() // discovery slows connecting down
        host?.takeIf { it.address != tv.address }?.let { hid.disconnect(it) }
        val device = adapter?.getRemoteDevice(tv.address) ?: return
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            if (!hid.connect(device)) failed(device)
        } else {
            // A TV found nearby: pair first (the TV asks to confirm), then connect once bonded.
            pending = device
            on(LinkEvent.TvChanged(tv, TvState.Connecting))
            if (device.bondState == BluetoothDevice.BOND_NONE && !device.createBond()) failed(device)
        }
    }

    private fun failed(device: BluetoothDevice) {
        pending = null
        on(LinkEvent.TvChanged(device.tv(), TvState.Disconnected))
    }

    override fun disconnect() {
        if (!allowed()) return
        host?.let { hid?.disconnect(it) }
        lastAddress = null // a TV the viewer let go of isn't reconnected next time
    }

    override fun press(key: RemoteKey) = send(HidReports.press(key))

    override fun release(key: RemoteKey) = send(HidReports.release(key))

    override fun type(text: String) = HidReports.type(text).forEach(::send)

    override fun mouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int) = send(HidReports.mouse(buttons, dx, dy, wheel, pan))

    private fun send(report: Report) {
        val hid = hid ?: return
        val host = host ?: return
        if (allowed()) hid.sendReport(host, report.id, report.data)
    }

    private fun refreshTvs() {
        if (!allowed()) return
        val paired = adapter?.bondedDevices.orEmpty()
            .filter { Tvs.looksLikeTv(it.bluetoothClass?.deviceClass ?: 0) || it.address == lastAddress }
            .map { it.tv() }
        _tvs.value = Tvs.ordered(paired, lastAddress)
    }

    private fun BluetoothDevice.tv() = Tv(address, alias ?: name ?: address)

    private fun listen() {
        if (listening) return
        listening = true
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                    BluetoothAdapter.STATE_ON -> start()
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        // The profile goes with Bluetooth; ask for it again once it's back on.
                        reset()
                        on(LinkEvent.BluetoothOff)
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshTvs()
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    if (device == null || device.address != pending?.address) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> {
                            pending = null
                            if (hid?.connect(device) != true) failed(device)
                        }
                        BluetoothDevice.BOND_NONE -> failed(device) // declined on the TV, or timed out
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val deviceClass = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)?.deviceClass ?: 0
                    if (Tvs.looksLikeTv(deviceClass)) _found.value = Tvs.found(_found.value, device.tv())
                }
                // One round of discovery lasts about 12 seconds; keep looking while the viewer is.
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    if (searching() && scanAllowed()) adapter?.startDiscovery()
            }
        }
    }

    private val noProfile = Runnable { if (hid == null) on(LinkEvent.Unsupported) }

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            main.removeCallbacks(noProfile)
            val hid = proxy as BluetoothHidDevice
            this@HidRemote.hid = hid
            if (!allowed()) return on(LinkEvent.NoPermission)
            val sdp = BluetoothHidDeviceAppSdpSettings(
                context.getString(R.string.app_name), "Keyboard, remote and pointer", "Ladrok",
                BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.DESCRIPTOR,
            )
            // Fails when another app is already registered as this phone's HID device.
            if (!hid.registerApp(sdp, null, null, context.mainExecutor, callback)) on(LinkEvent.Unsupported)
        }

        override fun onServiceDisconnected(profile: Int) {
            reset()
            on(LinkEvent.Registered(false))
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            on(LinkEvent.Registered(registered))
            if (!registered) return
            // A TV still plugged in from last time, or else the last TV the Remote was connected to.
            val tv = pluggedDevice?.tv() ?: Tvs.toReconnect(_tvs.value, lastAddress)
            tv?.let(::connect)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val tvState = when (state) {
                BluetoothProfile.STATE_CONNECTING -> TvState.Connecting
                BluetoothProfile.STATE_CONNECTED -> TvState.Connected
                BluetoothProfile.STATE_DISCONNECTING -> TvState.Disconnecting
                else -> TvState.Disconnected
            }
            when (tvState) {
                TvState.Connected -> {
                    host = device
                    lastAddress = device.address
                    refreshTvs()
                }
                TvState.Disconnected -> if (host?.address == device.address) host = null
                else -> {}
            }
            on(LinkEvent.TvChanged(device.tv(), tvState))
        }

        // A HID host may ask for a report's current state, typically right after connecting: nothing is held.
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val hid = hid ?: return
            if (!allowed()) return
            val idle = HidReports.idle(id.toInt()) ?: return run { hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID) }
            hid.replyReport(device, type, id, idle.data)
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            if (allowed()) hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS) // keyboard LEDs: nothing to light
        }
    }

    private companion object {
        const val PROFILE_TIMEOUT_MS = 5_000L
        const val SEARCH_MS = 120_000L
    }
}
