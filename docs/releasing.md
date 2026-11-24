# Releasing a new version

For whoever maintains the app. A release is a signed `.apk` attached to a GitHub release.

## The signing key

Every build is signed with one key, kept in `signing/` (git-ignored): `home-assist.jks` and
`signing.properties`. Android only installs an update signed with the same key as the app already on
the device, so **the key must never be lost or changed**: with a new key, everyone would have to
uninstall (losing their sign-in, layout and PIN) to update. Keep a copy somewhere safe, and never commit
it. `scripts/make-signing-key.sh` made it; it refuses to replace an existing key.

Without `signing/`, builds still work but are signed with that machine's debug key, and Gradle says so.
Such a build cannot update a released one.

## Steps

1. **Version:** in `app/build.gradle.kts`, raise `versionName` (for example `1.1.0`) and add 1 to
   `versionCode`. Android refuses an update whose `versionCode` is not higher.
2. **Changelog:** add the version to the top of `CHANGELOG.md`, written for the people who use the app.
3. **Check and build:**

   ```sh
   ./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease
   ```

4. **Check the signature:** it must say `CN=Home Assist for Echo Show`, with the same SHA-256 digest
   as the last release.

   ```sh
   ~/android-sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```

5. **Try it on an Echo** that has the previous release: `adb install -r` must succeed with no uninstall,
   and the sign-in and layout must still be there.
6. **Merge to `main`, tag and publish:**

   ```sh
   git tag v1.1.0 && git push origin v1.1.0
   cp app/build/outputs/apk/release/app-release.apk home-assist-1.1.0.apk
   gh release create v1.1.0 home-assist-1.1.0.apk --title "Home Assist for Echo Show 1.1.0" --notes-file notes.md
   ```

   `notes.md` is that version's section of the changelog.
7. **Download the published file** and check its SHA-256 matches the one you built.

## The live video library

`io.github.webrtc-sdk:android` (in `gradle/libs.versions.toml`) is Chromium's WebRTC. It handles video
arriving from outside the home, so check for a newer version at each release and move to it.
