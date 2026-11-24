# Installing Home Assist on an Echo Show

Home Assist shows the devices in your Home Assistant as cards on an Echo Show's touch screen, and lets you
control them by touch. The Echo talks straight to your own Home Assistant. Nothing goes through anyone
else's server.

It is an ordinary Android app, installed from a computer. It is not in any app store.

## What you need first

1. **An Echo Show running Android in place of Amazon's software.** The app was built for, and tested
   on, an **Echo Show 8 (1st generation) running LineageOS 18.1** (Android 11). It needs Android 11 or
   newer and a 32-bit ARM processor. This guide does not cover putting LineageOS on an Echo; the Echo
   must already have it.
2. **Home Assistant running on the same home network as the Echo**, with your devices already added.
3. **A Home Assistant username and password for the Echo.** You can use your own, but a separate user
   for the Echo is safer, because the Echo then cannot change Home Assistant's settings. (It can still
   control every device: Home Assistant does not limit that per user.) To make one:
   1. In Home Assistant, open **Settings**, then **People**, then the **Users** tab. (If you do not
      see a Users tab, first turn on **Advanced mode** in your user profile.)
   2. Choose **Add user**. Give it a name such as "Kitchen Echo", a username and a password.
   3. Leave **Administrator** switched off, then choose **Create**.
4. **A computer on the same network** (Windows, Mac or Linux), with Android's **platform tools**, which
   contain the `adb` program. Download them from
   [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools)
   and unzip them. The commands below are typed in a terminal opened in that folder. (On Windows, type
   `adb` as `.\adb`; on Mac and Linux, `./adb`.)

## Install

1. **Download the app:** open the
   [releases page](https://github.com/JayTee-2024/home-assistant-echo-show/releases/latest) and download
   the file ending in `.apk`.
2. **Switch on debugging on the Echo:**
   1. Open Android's **Settings**, then **About tablet** (or **About phone**), and tap **Build number** seven times. It says
      you are now a developer.
   2. Go back, open **System**, then **Developer options**, and switch on **Wireless debugging**.
   3. Tap the words **Wireless debugging** to open its screen. Leave it open.
3. **Pair the computer with the Echo** (once): on that screen choose **Pair device with pairing code**.
   It shows an address and a six-digit code. On the computer, type the address and then the code:

   ```
   adb pair 192.168.1.50:37123 123456
   ```

4. **Connect:** the Wireless debugging screen shows a second address, under **IP address & Port**.
   Its port is different from the pairing one, and it changes whenever the Echo restarts.

   ```
   adb connect 192.168.1.50:40555
   ```

5. **Install the app:**

   ```
   adb install home-assist-1.0.0.apk
   ```

   It answers `Success`.
6. **Make it the Echo's home screen:** press the Home button (or swipe up from the bottom edge).
   Android asks which home app to use: choose **Home Assist**, then **Always**. From then on the Echo
   opens straight into it.
7. **Switch Wireless debugging off again** (Settings, System, Developer options). While it is on,
   a paired computer can do anything on the Echo. Switch it back on only to update.

## First run

1. Home Assist looks for Home Assistant on your network. When it finds one it shows the address:
   **check it is yours**. If it finds nothing, or the wrong one, choose **Change** and type the
   address you use to open Home Assistant in a browser at home, such as `http://192.168.1.100:8123`.
2. Enter the Echo's **Username** and **Password** and choose **Sign in**. If your Home Assistant asks
   for a **Two-step code**, enter the code from your authenticator app. The Echo keeps a sign-in pass
   from Home Assistant, never your password.
3. The home screen starts empty. Choose **Add devices** (or the gear button at the top right, then
   **Devices**) and choose which devices to show. Each becomes a card.
4. **Tap a card** for its main action, such as switching a light on or off. **Hold it**, or tap its
   **⋯** button, for all its controls. Swipe left and right between pages.
5. To move cards, open Settings and choose **Reorganise layout**: drag a card to a free place, or hold
   it at the screen's edge to carry it to the next page.
6. A camera opens full screen. Choose **Watch live** to see it live, where Home Assistant offers live
   video for it; sound starts off.
7. The screen goes black after 30 seconds without a touch, and a touch wakes it. Change the time, or
   choose Never, in Settings under **Screen**.

To sign in as someone else or change the address, open **Settings**, then **Connection**, and choose
**Sign out**.

## What is left out, on purpose

Locks, alarms, and doors, garage doors and gates are not shown, together with every other part of the
same device: they are listed in Settings as **Not supported yet**. Switches, buttons, scripts and
scenes whose names contain *garage*, *gate*, *unlock*, *disarm* or *opener*, or both *door* and *open*,
are hidden in the same way.

That is a guess from names, and the app cannot know what a switch, button, script or scene really does.
If any of yours controls something sensitive under another name (a relay called "Door release", say),
leave it off the home screen, or turn on the **PIN lock**.

## Keeping it safe

- **Anyone who can touch the Echo can use the app.** If anything in your Home Assistant controls
  something sensitive, turn on the **PIN lock** in the app's Settings. The app then asks for a 4-digit
  PIN when it opens, when you come back to it, and after the screen has gone black. Wrong PINs make you
  wait, longer each time. If you forget the PIN, the lock screen lets you sign out, and you sign in
  again with your Home Assistant password.
- **The PIN guards the app, not the Echo.** Someone at the Echo can still reach Android's own settings.
- **The Echo keeps a sign-in pass** from Home Assistant (never your password), encrypted with a key held
  by Android. On an Echo with unlocked software like this, someone with the device and the right tools
  could still get at it, so **sign out before you sell or give away the Echo**: open **Settings**, then
  **Connection**, then **Sign out**.
- **To cancel the Echo's sign-in from elsewhere**, open your profile in Home Assistant, then
  **Security**, and delete the Echo's entry under **Refresh tokens** (or under **Long-lived access
  tokens**, if you signed in with one).
- **Keep Wireless debugging off** except while you install or update.
- **On your home network, an `http://` address is not encrypted:** your password, as you sign in, and
  the sign-in pass travel in the clear. That is normal for Home Assistant at home. If your Home
  Assistant has an `https://` address, type that instead.

## Updating

Download the newer `.apk`, switch Wireless debugging on, connect (step 4), and run:

```
adb install -r home-assist-1.1.0.apk
```

Your sign-in, layout and PIN are kept. Then switch Wireless debugging off again.

## Removing it

```
adb uninstall net.badacid.homeassist.echoshow
```

Sign out in the app first, so Home Assistant forgets the Echo's sign-in.

## If something goes wrong

- **`adb connect` says "Connection refused":** Wireless debugging has switched itself off, or the port
  has changed. Both happen when the Echo restarts or its Wi-Fi reconnects. Open the Wireless debugging
  screen again and use the address it shows now.
- **`adb install` says the signatures do not match:** a build from somewhere else (your own build, for
  example) is installed. Uninstall it first; that removes its sign-in and layout.
- **"Cannot reach Home Assistant" or "Nothing answered at …":** check the address and the port (usually
  `:8123`), and that the Echo and Home Assistant are on the same network. Try the same address in a
  browser on a phone on the same Wi-Fi.
- **Your username or password is refused:** sign in with the same details at Home Assistant's own web
  page to check them.
- **Home Assistant refuses the usual sign-in** (some setups switch it off): choose **Advanced: use an
  access token**. In Home Assistant, open your user profile, the **Security** tab, and create a
  **Long-lived access token** at the bottom. Type it into the Echo.
- **A device is missing from Settings:** it must be in Home Assistant first, and not hidden or disabled
  there. See also "What is left out, on purpose".
- Anything else: open an issue on
  [GitHub](https://github.com/JayTee-2024/home-assistant-echo-show/issues).
