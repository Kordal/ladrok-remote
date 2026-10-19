// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The Ladrok palette, as in the Launcher: on pure black like the boot animation.
private val Black = Color(0xFF000000)
private val Raised = Color(0xFF121212)
private val Line = Color(0xFF262626)
private val Ink = Color(0xFFE4E4E4)
private val Muted = Color(0xFF8A8A8A)
private val Silver = Color(0xFFCFCFCF)

private enum class Tab { Remote, Keypad, Pointer, Type }

/** What the Remote screen can ask for, beyond the keys themselves. */
class RemoteActions(
    val onPress: (RemoteKey) -> Unit,
    val onRelease: (RemoteKey) -> Unit,
    val onType: (String) -> Unit,
    val onMouse: (buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int) -> Unit,
    val onConnect: (Tv) -> Unit,
    val onDisconnect: () -> Unit,
    val onSearch: () -> Unit,
    val onStopSearching: () -> Unit,
    val onAllow: () -> Unit,
    val onSwitchOn: () -> Unit,
    val onStayConnected: (Boolean) -> Unit,
)

/**
 * The Remote: who it's connected to at the top, then the Remote keys (D-pad, Back, Home, playback, volume),
 * the Keypad (digits 1–9 open the Launcher's Tiles), the Pointer (an air mouse) or Type, chosen at the bottom.
 * The gear at the top opens Settings in their place.
 */
@Composable
fun RemoteScreen(link: Link, tvs: List<Tv>, found: List<Tv>, stayConnected: Boolean, actions: RemoteActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.Remote) }
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    val connected = link is Link.Connected
    Column(
        Modifier
            .fillMaxSize()
            .background(Black)
            .safeDrawingPadding()
            // The thin light outline that frames Ladrok's pictures on ladrok.com, around the whole app.
            .padding(8.dp)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Status(link, tvs, found, actions, settings) { settings = !settings }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The keys stay where they are, quiet, until a TV is connected.
            if (settings) {
                Box(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { Settings(stayConnected, actions) }
            } else if (tab == Tab.Pointer) {
                // No scrolling here: sliding on the pad scrolls the TV.
                Box(Modifier.padding(vertical = 16.dp).alpha(if (connected) 1f else 0.3f)) { PointerPad(actions, connected) }
            } else {
                // Scrolls only when the phone is turned sideways and the keys don't fit.
                Box(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp).alpha(if (connected) 1f else 0.3f)) {
                    when (tab) {
                        Tab.Remote -> RemoteKeys(actions, connected)
                        Tab.Keypad -> Keypad(actions, connected)
                        else -> TypeBox(actions, connected)
                    }
                }
            }
        }
        Tabs(tab.takeUnless { settings }) { tab = it; settings = false }
    }
}

@Composable
private fun Status(link: Link, tvs: List<Tv>, found: List<Tv>, actions: RemoteActions, settings: Boolean, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ladrok_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Silver),
                modifier = Modifier.size(width = 21.dp, height = 24.dp),
            )
            Spacer(Modifier.size(12.dp))
            val title = when (link) {
                is Link.Connected -> link.tv.name
                is Link.Connecting -> stringResource(R.string.connecting, link.tv.name)
                Link.Starting -> stringResource(R.string.starting)
                else -> stringResource(R.string.not_connected)
            }
            Text(title, color = if (link is Link.Connected) Ink else Muted, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (link is Link.Connected) Chip(stringResource(R.string.disconnect), onClick = actions.onDisconnect)
            Gear(settings, onSettings)
        }
        when (link) {
            Link.Unsupported -> Note(stringResource(R.string.unsupported))
            Link.NoPermission -> Note(stringResource(R.string.no_permission)) { Chip(stringResource(R.string.allow), onClick = actions.onAllow) }
            Link.Off -> Note(stringResource(R.string.off)) { Chip(stringResource(R.string.switch_on), onClick = actions.onSwitchOn) }
            is Link.Waiting -> if (link.searching) {
                Note(stringResource(R.string.searching))
                Chips(found, actions) { Chip(stringResource(R.string.stop), onClick = actions.onStopSearching) }
            } else {
                Chips(tvs, actions) { Chip(stringResource(R.string.pair), onClick = actions.onSearch) }
            }
            else -> {}
        }
    }
}

/** A Chip for each TV, to connect to it, then [last]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(tvs: List<Tv>, actions: RemoteActions, last: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tvs.forEach { Chip(it.name, onClick = { actions.onConnect(it) }) }
        last()
    }
}

@Composable
private fun Note(text: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = Muted, fontSize = 14.sp)
        action?.invoke()
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Ink,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Opens and closes Settings: a drawn gear, bright while Settings are open. */
@Composable
private fun Gear(open: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.settings)
    Box(
        Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Canvas(Modifier.size(22.dp)) { drawGear(if (open) Ink else Muted) }
    }
}

/** Settings: whether the Remote stays connected to the TV while minimized. */
@Composable
private fun Settings(stayConnected: Boolean, actions: RemoteActions) {
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings), color = Ink, fontSize = 18.sp)
        Text(stringResource(R.string.minimized), color = Muted, fontSize = 14.sp)
        Choice(stringResource(R.string.stay_connected), stringResource(R.string.stay_connected_note), stayConnected) { actions.onStayConnected(true) }
        Choice(stringResource(R.string.only_open), stringResource(R.string.only_open_note), !stayConnected) { actions.onStayConnected(false) }
    }
}

/** One of a few choices: a raised row, filled in Ink when chosen, like the Pointer switch. */
@Composable
private fun Choice(title: String, note: String, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (chosen) Ink else Raised)
            .border(BorderStroke(1.dp, if (chosen) Ink else Line), shape)
            .semantics { role = Role.RadioButton }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (chosen) Black else Ink, fontSize = 16.sp)
        Text(note, color = if (chosen) Raised else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Tabs(tab: Tab?, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for ((t, label) in listOf(Tab.Remote to R.string.tab_remote, Tab.Keypad to R.string.tab_keypad, Tab.Pointer to R.string.tab_pointer, Tab.Type to R.string.tab_type)) {
            Text(
                stringResource(label),
                color = if (t == tab) Ink else Muted,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTab(t) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** A raised surface with the Launcher's thin line around it. */
private fun Modifier.raised(shape: Shape) = clip(shape).background(Raised).border(BorderStroke(1.dp, Line), shape)

/**
 * A key the viewer holds: pressed on touch down, released on lift, like a real remote, so the TV repeats
 * held arrows and sees long presses (long-press OK opens a Tile's menu in the Launcher).
 */
private fun Modifier.key(key: RemoteKey, label: String, enabled: Boolean, actions: RemoteActions, haptic: () -> Unit) =
    semantics {
        contentDescription = label
        role = Role.Button
        onClick { if (enabled) { actions.onPress(key); actions.onRelease(key) }; enabled }
    }.pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
            haptic()
            actions.onPress(key)
            try { tryAwaitRelease() } finally { actions.onRelease(key) }
        })
    }

@Composable
private fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
}

@Composable
private fun RemoteKeys(actions: RemoteActions, enabled: Boolean) {
    val haptic = rememberHaptic()
    Column(
        Modifier.widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        DPad(actions, enabled, haptic)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(RemoteKey.Back, stringResource(R.string.key_back), enabled, actions, haptic) { drawBack(it) }
            RoundKey(RemoteKey.Home, stringResource(R.string.key_home), enabled, actions, haptic) { drawHome(it) }
            RoundKey(RemoteKey.PlayPause, stringResource(R.string.key_play_pause), enabled, actions, haptic) { drawPlayPause(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(RemoteKey.Rewind, stringResource(R.string.key_rewind), enabled, actions, haptic) { drawSkip(it, forward = false) }
            RoundKey(RemoteKey.Sleep, stringResource(R.string.key_sleep), enabled, actions, haptic) { drawMoon(it) }
            RoundKey(RemoteKey.FastForward, stringResource(R.string.key_fast_forward), enabled, actions, haptic) { drawSkip(it, forward = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(RemoteKey.VolumeDown, stringResource(R.string.key_volume_down), enabled, actions, haptic) { drawMinus(it) }
            RoundKey(RemoteKey.Mute, stringResource(R.string.key_mute), enabled, actions, haptic) { drawMute(it) }
            RoundKey(RemoteKey.VolumeUp, stringResource(R.string.key_volume_up), enabled, actions, haptic) { drawPlus(it) }
        }
    }
}

/** The D-pad: a ring with an arrow on each side and OK in the middle. */
@Composable
private fun DPad(actions: RemoteActions, enabled: Boolean, haptic: () -> Unit) {
    Box(
        Modifier
            .size(240.dp)
            .raised(CircleShape),
    ) {
        Arrow(RemoteKey.Up, stringResource(R.string.key_up), 0f, Alignment.TopCenter, enabled, actions, haptic)
        Arrow(RemoteKey.Right, stringResource(R.string.key_right), 90f, Alignment.CenterEnd, enabled, actions, haptic)
        Arrow(RemoteKey.Down, stringResource(R.string.key_down), 180f, Alignment.BottomCenter, enabled, actions, haptic)
        Arrow(RemoteKey.Left, stringResource(R.string.key_left), 270f, Alignment.CenterStart, enabled, actions, haptic)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .clip(CircleShape)
                .background(Black)
                .border(BorderStroke(1.dp, Line), CircleShape)
                .key(RemoteKey.Ok, stringResource(R.string.key_ok), enabled, actions, haptic),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.key_ok), color = Ink, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BoxScope.Arrow(
    key: RemoteKey, label: String, degrees: Float, alignment: Alignment,
    enabled: Boolean, actions: RemoteActions, haptic: () -> Unit,
) {
    Box(
        Modifier
            .align(alignment)
            .size(72.dp)
            .clip(CircleShape)
            .key(key, label, enabled, actions, haptic),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp).rotate(degrees)) {
            // A chevron pointing up; rotated for the other sides.
            val path = Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.7f)
                lineTo(size.width * 0.5f, size.height * 0.3f)
                lineTo(size.width * 0.85f, size.height * 0.7f)
            }
            drawPath(path, Ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun RoundKey(
    key: RemoteKey, label: String, enabled: Boolean, actions: RemoteActions, haptic: () -> Unit,
    icon: DrawScope.(Color) -> Unit,
) {
    Box(
        Modifier
            .size(64.dp)
            .raised(CircleShape)
            .key(key, label, enabled, actions, haptic),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { icon(Ink) }
    }
}

/** The Launcher's Keypad: three by three, like an old phone's. Each digit opens the Tile with that digit. */
@Composable
private fun Keypad(actions: RemoteActions, enabled: Boolean) {
    val haptic = rememberHaptic()
    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RemoteKey.DIGITS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    val digit = (RemoteKey.DIGITS.indexOf(key) + 1).toString()
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.25f)
                            .raised(RoundedCornerShape(12.dp))
                            .key(key, digit, enabled, actions, haptic),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit, color = Ink, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

/**
 * The Pointer, an air mouse: turning the phone moves the TV's pointer. A tap on the pad clicks, sliding up or
 * down scrolls, sliding sideways pans, and holding the pad still for a moment holds the button down, so turning the phone drags. Off, the pointer moves only while a thumb is on the pad. On, it moves without one, and holds
 * still while a thumb is on the pad, so a tap lands where it points. The gyroscope runs only while it moves.
 */
@Composable
private fun PointerPad(actions: RemoteActions, enabled: Boolean) {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val gyroscope = remember { sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    if (sensors == null || gyroscope == null) return Note(stringResource(R.string.no_gyroscope))
    val haptic = rememberHaptic()
    val pointer = remember { Pointer() }
    var holding by remember { mutableStateOf(false) }
    // Holding the pad still for a moment holds the left button down, so turning the phone drags.
    var dragging by remember { mutableStateOf(false) }
    var on by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notch = with(LocalDensity.current) { SCROLL_NOTCH.toPx() }
    val label = stringResource(R.string.pointer_pad)
    fun click() {
        haptic()
        actions.onMouse(HidReports.LEFT_BUTTON, 0, 0, 0, 0)
        // A short press, not a press and release in the same instant, which some apps miss.
        scope.launch {
            delay(CLICK_MS)
            actions.onMouse(0, 0, 0, 0, 0)
        }
    }

    if (enabled && (dragging || on != holding)) DisposableEffect(Unit) {
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (last != 0L) {
                    val (dx, dy) = pointer.turn(event.values[0], event.values[2], (event.timestamp - last) / 1e9f)
                    if (dx != 0 || dy != 0) actions.onMouse(if (dragging) HidReports.LEFT_BUTTON else 0, dx, dy, 0, 0)
                }
                last = event.timestamp
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensors.unregisterListener(listener)
            pointer.reset()
        }
    }

    Column(
        Modifier.widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(if (on) R.string.pointer_on else R.string.pointer_off),
            color = if (on) Black else Ink,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (on) Ink else Raised)
                .border(BorderStroke(1.dp, if (on) Ink else Line), CircleShape)
                .semantics { role = Role.Switch }
                .clickable(enabled = enabled) { haptic(); on = !on }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .raised(RoundedCornerShape(24.dp))
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    onClick { if (enabled) click(); enabled }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        holding = true
                        var at = down.position
                        var slidX = 0f
                        var slidY = 0f
                        // Set once the thumb has slid past the touch slop: true for sideways, false for up and down.
                        var sideways: Boolean? = null
                        try {
                            while (true) {
                                val event = if (sideways != null || dragging) awaitPointerEvent() else
                                    withTimeoutOrNull((HOLD_MS - (SystemClock.uptimeMillis() - down.uptimeMillis)).coerceAtLeast(1)) { awaitPointerEvent() }
                                if (event == null) {
                                    haptic()
                                    dragging = true
                                    actions.onMouse(HidReports.LEFT_BUTTON, 0, 0, 0, 0)
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (sideways == null && !dragging && change.uptimeMillis - down.uptimeMillis < TAP_MS) click()
                                    break
                                }
                                if (dragging) {
                                    change.consume()
                                    continue
                                }
                                // The TV's page follows the thumb, like a touch screen: thumb down scrolls up, thumb
                                // right pans left. A slide keeps the direction it started in.
                                slidX += change.position.x - at.x
                                slidY += change.position.y - at.y
                                at = change.position
                                if (sideways == null && (slidX * slidX + slidY * slidY) > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                    sideways = kotlin.math.abs(slidX) > kotlin.math.abs(slidY)
                                }
                                if (sideways == true) {
                                    val notches = (slidX / notch).toInt()
                                    if (notches != 0) {
                                        actions.onMouse(0, 0, 0, 0, -notches)
                                        slidX -= notches * notch
                                    }
                                } else if (sideways == false) {
                                    val notches = (slidY / notch).toInt()
                                    if (notches != 0) {
                                        actions.onMouse(0, 0, 0, notches, 0)
                                        slidY -= notches * notch
                                    }
                                }
                                change.consume()
                            }
                        } finally {
                            if (dragging) actions.onMouse(0, 0, 0, 0, 0)
                            dragging = false
                            holding = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (on) R.string.pointer_hint_on else R.string.pointer_hint),
                color = Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(RemoteKey.Back, stringResource(R.string.key_back), enabled, actions, haptic) { drawBack(it) }
            RoundKey(RemoteKey.Home, stringResource(R.string.key_home), enabled, actions, haptic) { drawHome(it) }
        }
    }
}

/** Longer than this, a touch on the pad is aiming, not a click. */
private const val TAP_MS = 250L
private const val HOLD_MS = 450L
private const val CLICK_MS = 40L

/** How far a finger slides on the pad for one notch of the TV's scroll wheel. */
private val SCROLL_NOTCH = 24.dp

/** Type: text goes to whatever has focus on the TV, such as a search box. */
@Composable
private fun TypeBox(actions: RemoteActions, enabled: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        if (!enabled || text.isEmpty()) return
        actions.onType(text)
        text = ""
    }
    Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 18.sp),
            cursorBrush = SolidColor(Silver),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier
                .fillMaxWidth()
                .raised(RoundedCornerShape(12.dp))
                .padding(16.dp),
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) Text(stringResource(R.string.type_hint), color = Muted, fontSize = 18.sp)
                    field()
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.send)) { send() }
            Chip(stringResource(R.string.delete)) { if (enabled) actions.onType("\b") }
            Chip(stringResource(R.string.enter)) { if (enabled) actions.onType("\n") }
        }
    }
}

// Key icons: thin strokes in the Launcher's ink, drawn rather than taken from an icon set.

private fun DrawScope.stroke() = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawBack(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.35f, h * 0.2f)
        lineTo(w * 0.1f, h * 0.45f)
        lineTo(w * 0.35f, h * 0.7f)
        moveTo(w * 0.1f, h * 0.45f)
        lineTo(w * 0.65f, h * 0.45f)
        cubicTo(w * 0.95f, h * 0.45f, w * 0.95f, h * 0.9f, w * 0.65f, h * 0.9f)
        lineTo(w * 0.45f, h * 0.9f)
    }
    drawPath(path, color, style = stroke())
}

private fun DrawScope.drawHome(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.1f, h * 0.48f)
        lineTo(w * 0.5f, h * 0.12f)
        lineTo(w * 0.9f, h * 0.48f)
        moveTo(w * 0.22f, h * 0.38f)
        lineTo(w * 0.22f, h * 0.9f)
        lineTo(w * 0.78f, h * 0.9f)
        lineTo(w * 0.78f, h * 0.38f)
    }
    drawPath(path, color, style = stroke())
}

private fun DrawScope.drawPlayPause(color: Color) {
    val w = size.width
    val h = size.height
    val play = Path().apply {
        moveTo(w * 0.08f, h * 0.18f)
        lineTo(w * 0.5f, h * 0.5f)
        lineTo(w * 0.08f, h * 0.82f)
        close()
    }
    drawPath(play, color)
    drawLine(color, Offset(w * 0.68f, h * 0.2f), Offset(w * 0.68f, h * 0.8f), 2.4.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(w * 0.88f, h * 0.2f), Offset(w * 0.88f, h * 0.8f), 2.4.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawSkip(color: Color, forward: Boolean) {
    val w = size.width
    val h = size.height
    fun triangle(from: Float) = Path().apply {
        val tip = if (forward) from + w * 0.45f else from - w * 0.45f
        moveTo(from, h * 0.2f)
        lineTo(tip, h * 0.5f)
        lineTo(from, h * 0.8f)
        close()
    }
    val start = if (forward) w * 0.05f else w * 0.95f
    val step = if (forward) w * 0.45f else -w * 0.45f
    drawPath(triangle(start), color)
    drawPath(triangle(start + step), color)
}

private fun DrawScope.drawMinus(color: Color) {
    drawLine(color, Offset(size.width * 0.2f, size.height / 2), Offset(size.width * 0.8f, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawPlus(color: Color) {
    drawMinus(color)
    drawLine(color, Offset(size.width / 2, size.height * 0.2f), Offset(size.width / 2, size.height * 0.8f), 2.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawMoon(color: Color) {
    val w = size.width
    val h = size.height
    val disc = Path().apply { addOval(Rect(Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f))) }
    val bite = Path().apply { addOval(Rect(Offset(w * 0.38f, h * 0.0f), Size(w * 0.7f, h * 0.7f))) }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), color, style = stroke())
}

private fun DrawScope.drawGear(color: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2
    drawCircle(color, r * 0.32f, c, style = stroke())
    drawCircle(color, r * 0.66f, c, style = stroke())
    // Eight teeth around the rim.
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
        drawLine(color, c + dir * (r * 0.66f), c + dir * (r * 0.95f), 2.4.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.drawMute(color: Color) {
    val w = size.width
    val h = size.height
    val speaker = Path().apply {
        moveTo(w * 0.08f, h * 0.38f)
        lineTo(w * 0.28f, h * 0.38f)
        lineTo(w * 0.52f, h * 0.15f)
        lineTo(w * 0.52f, h * 0.85f)
        lineTo(w * 0.28f, h * 0.62f)
        lineTo(w * 0.08f, h * 0.62f)
        close()
    }
    drawPath(speaker, color, style = stroke())
    drawLine(color, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.94f, h * 0.64f), 1.8.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(w * 0.94f, h * 0.36f), Offset(w * 0.66f, h * 0.64f), 1.8.dp.toPx(), StrokeCap.Round)
}
