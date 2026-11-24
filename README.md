# Home Assist for Echo Show

A touch-screen control app for Home Assistant, built for an Amazon Echo Show 8 (1st generation) running
LineageOS 18.1 (Android 11). It is the Echo Show version of
[Home Assist for LG TVs](https://github.com/JayTee-2024/home-assistant-lg) and has the same features.

**To install it, follow [docs/install.md](docs/install.md).** What changed in each version is in
[CHANGELOG.md](CHANGELOG.md).

- Your devices as cards on a grid with pages: tap for the main action, hold for all the controls.
- Lights, switches and plugs, heating, media players, blinds, fans, robot vacuums, sensors, scenes and
  scripts, and cameras with live video.
- An optional PIN lock, and a screen that blanks itself after a set time.
- Locks, alarms, and doors, garage doors and gates are deliberately left out.
- It talks only to your own Home Assistant.

## Building

You need Java 17 or newer and the Android SDK (platform 35).

```sh
scripts/setup-android-sdk.sh        # only if you have no Android SDK; installs it into ~/android-sdk
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

- `core/`: the app's logic in plain Kotlin, tested on the JVM.
- `app/`: the Android app: `platform/` (network, storage, live video), `screens/`, and `app/` which joins them.
- `docs/`: the install guide, how to release ([docs/releasing.md](docs/releasing.md)), and pictures of
  every screen (`docs/screens/`).

Builds are signed with the key in `signing/` when it is there (see docs/releasing.md), and otherwise with
your machine's debug key.

## Trying a build on the Echo

On the Echo: Settings → System → Developer options → turn on Android debugging and Wireless debugging.
Then, in a Linux shell (the PC's linked shell works):

```sh
scripts/echo.sh pair <ip:port> <code>   # once: from "Pair device with pairing code"
scripts/echo.sh connect <ip:port>       # the "IP address & Port" shown on the Wireless debugging screen
scripts/echo.sh install app/build/outputs/apk/debug/app-debug.apk
```

The pairing key stays in `.deploy/` (git-ignored). The Echo's port changes when it restarts: run `connect` again.

## Licence

Apache License 2.0. See `LICENSE`.
