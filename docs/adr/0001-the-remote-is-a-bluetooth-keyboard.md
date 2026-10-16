# The Remote is a Bluetooth keyboard, and the TV connects to it first

The Android Remote registers as a Bluetooth HID device (`BluetoothHidDevice`, a keyboard plus a consumer control). The TV gets ordinary key events through its own HID host and Generic.kl, the same way as from any Bluetooth remote. Nothing has to inject input on the TV, and Type works like a real keyboard.

The alternative was a Ladrok Bluetooth LE (GATT) service on the TV that injects key events, which an iPhone would also need, because iOS can't be a HID device. It needs much more TV-side code (advertising, GATT, `INJECT_EVENTS`), so the Android Remote doesn't wait for it. The screen talks to a `RemoteTransport`, so a GATT transport can be added next to `HidRemote` later.

## What the TV must do

Tested on 2026-10-16 with two emulators (an Android 17 phone and an Android 16 Android TV image) sharing a virtual Bluetooth radio:

- TV Settings › Remotes & accessories › Add accessory **sees the phone but doesn't list it**: it only lists devices whose class of device is a peripheral, and a phone's is a phone.
- That screen makes the TV discoverable, so the **Remote searches instead**, finds the TV and pairs. Both show the same code. This works.
- The phone then connects as a HID device, and **the TV rejects it**: `btif_hh: Reject Incoming HID Connection`. Android's HID host only accepts incoming connections from devices it has connected to itself, and TV Settings has no Connect action for a paired device.

So the TV has to make the first HID connection after pairing. Ladrok TV will do that with a small platform-signed app in `device/brcm/rpi5` (like LadrokSetup): when a newly paired device's UUIDs include HID (0x1124), it connects the HID host to it. From then on the TV keeps the device's HID details, and the Remote reconnects by itself.

A stock Android TV doesn't do this, so the Remote is for Ladrok TV.
