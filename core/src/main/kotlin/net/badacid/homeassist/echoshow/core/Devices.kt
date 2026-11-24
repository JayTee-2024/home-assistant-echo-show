package net.badacid.homeassist.echoshow.core

/**
 * The kinds of cover (Home Assistant's `device_class`) that are safe to control from the Echo. Any other
 * cover, including one that reports no kind, might be a door, garage or gate.
 */
val SAFE_COVER_KINDS = listOf("awning", "blind", "curtain", "damper", "shade", "shutter", "window")
