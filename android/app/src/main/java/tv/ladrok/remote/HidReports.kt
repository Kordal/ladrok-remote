// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

/** One HID input report: its report ID and its bytes after the ID. */
class Report(val id: Int, val data: ByteArray) {
    override fun equals(other: Any?) = other is Report && other.id == id && other.data.contentEquals(data)
    override fun hashCode() = 31 * id + data.contentHashCode()
    override fun toString() = "Report($id, ${data.joinToString(" ") { "%02x".format(it) }})"
}

/**
 * What the phone sends the TV as a Bluetooth HID device: a keyboard (report 1) and a consumer control
 * (report 2), described by [DESCRIPTOR]. Pure Kotlin, so the same bytes can be checked on any platform.
 */
object HidReports {
    const val KEYBOARD = 1
    const val CONSUMER = 2

    private const val LEFT_SHIFT = 0x02

    val DESCRIPTOR: ByteArray = bytes(
        // Keyboard: 8 modifier bits, a reserved byte, up to 6 keys (the boot keyboard layout).
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, KEYBOARD,   //   Report ID
        0x05, 0x07,       //   Usage Page (Keyboard)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Variable, Absolute): modifiers
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x01,       //   Input (Constant): reserved
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x06,       //   Report Count (6)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x65,       //   Logical Maximum (101)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0x65,       //   Usage Maximum (101)
        0x81, 0x00,       //   Input (Data, Array): keys
        0xC0,             // End Collection
        // Consumer control: one 16-bit usage at a time, 0 when nothing is held.
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, CONSUMER,   //   Report ID
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (0x3FF)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (0x3FF)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array, Absolute)
        0xC0,             // End Collection
    )

    fun press(key: RemoteKey): Report = when (key.page) {
        RemoteKey.Page.Keyboard -> keyboard(0, key.usage)
        RemoteKey.Page.Consumer -> consumer(key.usage)
    }

    fun release(key: RemoteKey): Report = when (key.page) {
        RemoteKey.Page.Keyboard -> keyboard(0, 0)
        RemoteKey.Page.Consumer -> consumer(0)
    }

    /** Report [id] with nothing held, for a host asking for its current state; null for a report the descriptor lacks. */
    fun idle(id: Int): Report? = when (id) {
        KEYBOARD -> keyboard(0, 0)
        CONSUMER -> consumer(0)
        else -> null
    }

    /** Press and release for each character of [text] a US keyboard can type; others are skipped. */
    fun type(text: String): List<Report> = text.flatMap { char ->
        val (modifiers, usage) = KEYS[char] ?: return@flatMap emptyList()
        listOf(keyboard(modifiers, usage), keyboard(0, 0))
    }

    private fun keyboard(modifiers: Int, usage: Int) = Report(KEYBOARD, bytes(modifiers, 0, usage, 0, 0, 0, 0, 0))

    private fun consumer(usage: Int) = Report(CONSUMER, bytes(usage and 0xFF, usage shr 8))

    /** Character to (modifiers, keyboard usage) on a US layout, the one Android assumes for a generic keyboard. */
    private val KEYS: Map<Char, Pair<Int, Int>> = buildMap {
        for (c in 'a'..'z') put(c, 0 to 0x04 + (c - 'a'))
        for (c in 'A'..'Z') put(c, LEFT_SHIFT to 0x04 + (c - 'A'))
        for (c in '1'..'9') put(c, 0 to 0x1E + (c - '1'))
        put('0', 0 to 0x27)
        put('\n', 0 to 0x28)
        put('\b', 0 to 0x2A) // Backspace
        put(' ', 0 to 0x2C)
        "!@#$%^&*()".forEachIndexed { i, c -> put(c, LEFT_SHIFT to 0x1E + i) }
        listOf(
            '-' to 0x2D, '=' to 0x2E, '[' to 0x2F, ']' to 0x30, '\\' to 0x31, ';' to 0x33,
            '\'' to 0x34, '`' to 0x35, ',' to 0x36, '.' to 0x37, '/' to 0x38,
        ).forEach { (c, usage) -> put(c, 0 to usage) }
        listOf(
            '_' to 0x2D, '+' to 0x2E, '{' to 0x2F, '}' to 0x30, '|' to 0x31, ':' to 0x33,
            '"' to 0x34, '~' to 0x35, '<' to 0x36, '>' to 0x37, '?' to 0x38,
        ).forEach { (c, usage) -> put(c, LEFT_SHIFT to usage) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
