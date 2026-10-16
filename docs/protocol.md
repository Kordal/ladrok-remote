# What the Remote sends

The Remote is a Bluetooth HID device (a keyboard and a consumer control). The descriptor is in [HidReports.kt](../android/app/src/main/java/tv/ladrok/remote/HidReports.kt):

- **Report 1, keyboard**: 8 bytes, the boot keyboard layout (modifiers, reserved, six keys).
- **Report 2, consumer control**: one 16-bit usage, little-endian. 0 means nothing is held.

A Key is pressed on touch down and released on lift, so the TV repeats held arrows and sees long presses.

| Key | Page | Usage | Linux (hid-input) | Android (Generic.kl) |
|---|---|---|---|---|
| Up | Keyboard 0x07 | 0x52 | KEY_UP | DPAD_UP |
| Down | Keyboard | 0x51 | KEY_DOWN | DPAD_DOWN |
| Left | Keyboard | 0x50 | KEY_LEFT | DPAD_LEFT |
| Right | Keyboard | 0x4F | KEY_RIGHT | DPAD_RIGHT |
| 1–9, 0 | Keyboard | 0x1E–0x26, 0x27 | KEY_1–KEY_0 | 1–9, 0 |
| OK | Consumer 0x0C | 0x041 Menu Pick | KEY_SELECT (353) | DPAD_CENTER |
| Back | Consumer | 0x224 AC Back | KEY_BACK (158) | BACK |
| Home | Consumer | 0x223 AC Home | KEY_HOMEPAGE (172) | HOME |
| Play or pause | Consumer | 0x0CD | KEY_PLAYPAUSE (164) | MEDIA_PLAY_PAUSE |
| Rewind | Consumer | 0x0B4 | KEY_REWIND (168) | MEDIA_REWIND |
| Fast forward | Consumer | 0x0B3 | KEY_FASTFORWARD (208) | MEDIA_FAST_FORWARD |
| Volume up | Consumer | 0x0E9 | KEY_VOLUMEUP (115) | VOLUME_UP |
| Volume down | Consumer | 0x0EA | KEY_VOLUMEDOWN (114) | VOLUME_DOWN |
| Mute | Consumer | 0x0E2 | KEY_MUTE (113) | VOLUME_MUTE |

Checked against Linux `drivers/hid/hid-input.c` and AOSP `frameworks/base/data/keyboards/Generic.kl`. OK is Menu Pick, not Enter: Enter would arrive as `ENTER`, not `DPAD_CENTER`.

Type sends keyboard reports for a US layout (letters, digits, space, Enter, Backspace and the usual punctuation, with Left Shift where needed). Characters a US keyboard can't type are skipped.

An iPhone can't be a Bluetooth HID device, so the iOS Remote will need another way in (see [ADR 0001](adr/0001-the-remote-is-a-bluetooth-keyboard.md)). It should send the same Keys.
