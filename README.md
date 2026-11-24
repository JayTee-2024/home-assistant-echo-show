# Home Assist for Echo Show

A touch-screen control app for Home Assistant, built for an Amazon Echo Show 8 (1st generation) running
LineageOS 18.1 (Android 11). It is the Echo Show version of
[Home Assist for LG TVs](https://github.com/JayTee-2024/home-assistant-lg) and has the same features.

Status: in development. The design is in `docs/superpowers/specs/`.

## Building

You need Java 17 or newer and the Android SDK (platform 35).

```sh
scripts/setup-android-sdk.sh        # only if you have no Android SDK; installs it into ~/android-sdk
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

- `core/`: the app's logic in plain Kotlin, tested on the JVM.
- `app/`: the Android app.

## Licence

Apache License 2.0. See `LICENSE`.
