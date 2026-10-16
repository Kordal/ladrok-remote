<p align="center"><img src="android/app/src/main/res/drawable-nodpi/ladrok_logo.png" alt="Ladrok" width="160"></p>

# Ladrok Remote

A remote control for [Ladrok TV](https://github.com/Kordal/ladrok-tv) on your phone, over Bluetooth. The arrows and OK, Back and Home, playback and volume, the Launcher's Keypad (1–9 open your apps), and Type, for search boxes. Nothing else.

The phone acts as a Bluetooth keyboard and remote, so the TV sees an ordinary Bluetooth accessory. The phone is the TV's remote only while the app is open: a phone left connected would count as a keyboard, and the TV would stop showing its on-screen one.

Terms are defined in [CONTEXT.md](CONTEXT.md), decisions in [docs/adr](docs/adr), the keys in [docs/protocol.md](docs/protocol.md).

## Pair

1. On the TV: Settings › Remotes & accessories › Add accessory.
2. In the Remote: *Pair a TV*, then choose the TV.
3. Check the code is the same on both, and confirm on both.

After that the Remote connects to the same TV whenever it opens.

**Ladrok TV needs to connect first after pairing** (see [ADR 0001](docs/adr/0001-the-remote-is-a-bluetooth-keyboard.md)). That part of Ladrok TV isn't built yet, so for now the Remote pairs but the TV doesn't accept its keys.

## Status

- Unit-tested: the HID reports and descriptor, the Link state machine, which devices count as TVs.
- Tried on emulators: permissions, search, pairing with an Android TV emulator. The TV then rejects the connection, as described above.
- **Not tried on a real phone or a real TV yet.**

## Build and run

You need the Android SDK and JDK 21 or newer (the one bundled with Android Studio works). The Android app is in `android/`; the iOS one will go next to it.

```bash
cd android
./gradlew testDebugUnitTest   # HID reports, Link, TVs
./gradlew installDebug        # onto a connected phone (Android 12 or newer)
```

Emulators have no shared Bluetooth with real devices; use a phone.

## Releases

Push a tag such as `v1.0.0`. CI builds `LadrokRemote.apk`, signs it with the release key (repo secrets `LADROK_KEYSTORE_B64` and `LADROK_KEYSTORE_PASSWORD`) and attaches it with its SHA-256 to a GitHub Release.

## License

Apache License 2.0, see [LICENSE](LICENSE).
