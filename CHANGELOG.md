# Changelog

## 1.0.0 — 2026-11-24

The first version. Install it with [docs/install.md](docs/install.md).

It is the Echo Show version of [Home Assist for LG TVs](https://github.com/JayTee-2024/home-assistant-lg)
1.2.0, rebuilt as a native Android app for touch.

- Sign in with a Home Assistant username and password (with two-step codes), or with a long-lived access
  token. The Echo never stores the password. The app looks for Home Assistant on your network and shows
  the address it found for you to check.
- Choose which Home Assistant devices to show; each becomes a card on a grid with pages. Rooms and names
  come from Home Assistant.
- Touch: tap a card for its main action, hold it or tap **⋯** for all its controls, swipe between pages.
- Device types: lights (brightness, shades of white and colour), switches and plugs, buttons, scenes and
  scripts, sensors and binary sensors, heating, media players (such as Sonos), blinds and other safe
  covers, fans, robot vacuums, and cameras.
- Cameras open full screen, and **Watch live** plays live video where Home Assistant offers it (WebRTC).
  Tested with a Ring doorbell. A Ring camera's still picture is labelled **Last recording**.
- **Reorganise layout:** drag cards to move them, and hold one at the screen's edge to carry it to
  another page.
- **PIN lock** (optional): a 4-digit PIN when the app opens, when you come back to it and after the
  screen has gone black. Wrong PINs make you wait, longer each time.
- **Screen blanking:** the screen goes black after a set time without a touch (30 seconds unless
  changed), and a touch wakes it.
- Locks, alarms, and doors, garage doors and gates are deliberately left out, together with every other
  part of the same device. So are switches, buttons, scripts and scenes named like a garage, gate or
  door opener. The app cannot know what other switches do: see "What is left out, on purpose" in the
  install guide.
- The sign-in pass is kept encrypted on the Echo. The app talks only to your Home Assistant, never
  follows a redirect to another address, and asks Android for no permission beyond network access.
- Built for an Echo Show 8 (1st generation) running LineageOS 18.1 (Android 11).
