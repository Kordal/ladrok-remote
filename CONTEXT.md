# Ladrok Remote

A phone app that is a remote control for Ladrok TV. It follows the Ladrok mantra: only what you need, nothing that shouts, little to set up.

## Language

**Remote**:
The phone app, and the phone while it acts as the TV's remote control.
_Avoid_: Controller, companion app

**TV**:
A Ladrok TV (or another Android TV) the Remote can pair with and connect to.
_Avoid_: Host, receiver, box

**Key**:
One button on the Remote: an arrow, OK, Back, Home, a playback or volume button, the sleep timer, or a digit 1–9. Each sends one HID usage (see [docs/protocol.md](docs/protocol.md)).
_Avoid_: Button (in code), command

**Keypad**:
The Remote's digits 1–9, three by three. They mirror the Keypad of the Launcher (Ladrok TV's home screen, see [ladrok-start](https://github.com/Kordal/ladrok-start/blob/main/CONTEXT.md)): each digit opens the App with that digit.
_Avoid_: Number pad, numpad

**Type**:
Sending text to whatever has focus on the TV, such as a search box, as if typed on a keyboard.
_Avoid_: Keyboard mode, input

**Search**:
The Remote looking for TVs nearby to pair with, while the TV is in Add accessory.
_Avoid_: Scan, discover

**Pair**:
The one-time Bluetooth bond between the Remote and a TV, confirmed on both with the same code.
_Avoid_: Register, bond (in UI text)

**Link**:
The Remote's state towards a TV: unsupported, no permission, Bluetooth off, starting, waiting (or searching), connecting, connected.
_Avoid_: Connection state, status
