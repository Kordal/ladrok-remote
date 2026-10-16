// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

/**
 * A Key on the Remote, and the HID usage the TV receives for it.
 *
 * Keyboard usages (page 0x07) for the arrows and digits, so the Launcher's Keypad opens Tiles by digit;
 * consumer controls (page 0x0C) for the rest. Each maps, through Linux hid-input and Android's Generic.kl,
 * to the Android key named in the comment. docs/protocol.md lists them for other platforms.
 */
enum class RemoteKey(val page: Page, val usage: Int) {
    Up(Page.Keyboard, 0x52),          // DPAD_UP
    Down(Page.Keyboard, 0x51),        // DPAD_DOWN
    Left(Page.Keyboard, 0x50),        // DPAD_LEFT
    Right(Page.Keyboard, 0x4F),       // DPAD_RIGHT
    Ok(Page.Consumer, 0x0041),        // Menu Pick: DPAD_CENTER
    Back(Page.Consumer, 0x0224),      // AC Back: BACK
    Home(Page.Consumer, 0x0223),      // AC Home: HOME
    Menu(Page.Consumer, 0x0040),      // MENU
    PlayPause(Page.Consumer, 0x00CD), // MEDIA_PLAY_PAUSE
    Rewind(Page.Consumer, 0x00B4),    // MEDIA_REWIND
    FastForward(Page.Consumer, 0x00B3), // MEDIA_FAST_FORWARD
    VolumeUp(Page.Consumer, 0x00E9),  // VOLUME_UP
    VolumeDown(Page.Consumer, 0x00EA), // VOLUME_DOWN
    Mute(Page.Consumer, 0x00E2),      // VOLUME_MUTE
    One(Page.Keyboard, 0x1E),
    Two(Page.Keyboard, 0x1F),
    Three(Page.Keyboard, 0x20),
    Four(Page.Keyboard, 0x21),
    Five(Page.Keyboard, 0x22),
    Six(Page.Keyboard, 0x23),
    Seven(Page.Keyboard, 0x24),
    Eight(Page.Keyboard, 0x25),
    Nine(Page.Keyboard, 0x26),
    Zero(Page.Keyboard, 0x27);

    enum class Page { Keyboard, Consumer }

    companion object {
        /** The Keypad's digits 1–9, in the Launcher's order. */
        val DIGITS = listOf(One, Two, Three, Four, Five, Six, Seven, Eight, Nine)
    }
}
