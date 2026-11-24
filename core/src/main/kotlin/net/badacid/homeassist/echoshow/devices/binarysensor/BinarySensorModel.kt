package net.badacid.homeassist.echoshow.devices.binarysensor

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE

data class BinarySensorView(
    val isOn: Boolean,
    /** The state in words: 'Open', 'Clear', 'Unavailable'. */
    val words: String,
)

/** The words for on and off, by the sensor's kind (Home Assistant's device_class). */
private val WORDS = mapOf(
    "door" to ("Open" to "Closed"),
    "window" to ("Open" to "Closed"),
    "opening" to ("Open" to "Closed"),
    "garage_door" to ("Open" to "Closed"),
    "motion" to ("Detected" to "Clear"),
    "occupancy" to ("Detected" to "Clear"),
    "presence" to ("Detected" to "Clear"),
    "smoke" to ("Detected" to "Clear"),
    "gas" to ("Detected" to "Clear"),
    "problem" to ("Detected" to "Clear"),
    "safety" to ("Detected" to "Clear"),
    "battery" to ("Low" to "Normal"),
    "connectivity" to ("Connected" to "Disconnected"),
    "moisture" to ("Wet" to "Dry"),
    "lock" to ("Unlocked" to "Locked"),
)
private val DEFAULT_WORDS = "On" to "Off"

fun readBinarySensor(entity: EntityState): BinarySensorView {
    if (!isAvailable(entity)) return BinarySensorView(false, UNAVAILABLE)
    val isOn = entity.state == "on"
    val pair = WORDS[readText(entity, "device_class")] ?: DEFAULT_WORDS
    return BinarySensorView(isOn, if (isOn) pair.first else pair.second)
}
