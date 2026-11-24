package net.badacid.homeassist.echoshow.devices.fan

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.hasFeature
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.readNumber
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.core.readTextList
import kotlin.math.roundToInt

private const val SET_SPEED = 1
private const val OSCILLATE = 2
private const val PRESET_MODE = 8
private const val SMALLEST_STEP = 10.0

data class FanView(
    val isAvailable: Boolean,
    val isOn: Boolean,
    /** 0 to 100. null when the fan does not report it. */
    val speedPercent: Int?,
    /** How far one press of − or + moves the speed, in percent. */
    val step: Double,
    val canSetSpeed: Boolean,
    val canOscillate: Boolean,
    val isOscillating: Boolean,
    val presets: List<String>,
    val preset: String?,
)

fun readFan(entity: EntityState): FanView {
    val oscillating = entity.attributes["oscillating"] as? JsonPrimitive
    return FanView(
        isAvailable = isAvailable(entity),
        isOn = entity.state == "on",
        speedPercent = readNumber(entity, "percentage")?.roundToInt(),
        step = maxOf(SMALLEST_STEP, readNumber(entity, "percentage_step") ?: 0.0),
        canSetSpeed = hasFeature(entity, SET_SPEED),
        canOscillate = hasFeature(entity, OSCILLATE),
        isOscillating = oscillating != null && !oscillating.isString && oscillating.booleanOrNull == true,
        presets = if (hasFeature(entity, PRESET_MODE)) readTextList(entity, "preset_modes") else emptyList(),
        preset = readText(entity, "preset_mode"),
    )
}

/**
 * The speed one of the fan's own steps up (direction 1) or down (-1), between 0 and 100. Works from the
 * nearest step, because Home Assistant reports a 3-speed fan as 33, 66 or 100 with a step of 33.33….
 */
fun nextSpeed(current: Int?, step: Double, direction: Int): Int {
    val nearestStep = ((current ?: 0) / step).roundToInt()
    val next = (nearestStep + direction) * step
    return next.roundToInt().coerceIn(0, 100)
}
