package net.badacid.homeassist.echoshow.devices.light

import kotlinx.serialization.json.JsonArray
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.numberOrNull
import net.badacid.homeassist.echoshow.core.readNumber
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.core.readTextList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** What a light reports, in the shape its card and panel use. */
data class LightView(
    val isOn: Boolean,
    val isAvailable: Boolean,
    /** 1 to 100. null when the light is off or cannot dim. */
    val brightnessPercent: Int?,
    val canDim: Boolean,
    /** True when the light can show different whites, from warm to cool. */
    val canSetWhite: Boolean,
    val canSetColour: Boolean,
    /** The white the light is showing, in kelvin. null when it is showing a colour, or is off. */
    val whiteKelvin: Int?,
    /** The warmest and coolest whites the light can show, in kelvin. */
    val minKelvin: Int,
    val maxKelvin: Int,
    /** The colour the light is showing as red, green, blue (0 to 255 each). null when unknown. */
    val rgb: List<Int>?,
    val effects: List<String>,
    val effect: String?,
)

private val COLOUR_MODES = listOf("hs", "xy", "rgb", "rgbw", "rgbww")
private const val DEFAULT_MIN_KELVIN = 2000
private const val DEFAULT_MAX_KELVIN = 6500
private const val WHITE_STEP_COUNT = 5
private const val BRIGHTNESS_STEP = 10

fun readLight(entity: EntityState): LightView {
    val modes = readTextList(entity, "supported_color_modes")
    val canSetWhite = "color_temp" in modes
    val canSetColour = modes.any { it in COLOUR_MODES }
    val canDim = canSetWhite || canSetColour || "brightness" in modes
    val isOn = entity.state == "on"
    return LightView(
        isOn = isOn,
        isAvailable = isAvailable(entity),
        brightnessPercent = if (isOn && canDim) brightnessPercent(entity) else null,
        canDim = canDim,
        canSetWhite = canSetWhite,
        canSetColour = canSetColour,
        whiteKelvin = if (isOn) readNumber(entity, "color_temp_kelvin")?.roundToInt() else null,
        minKelvin = readNumber(entity, "min_color_temp_kelvin")?.roundToInt() ?: DEFAULT_MIN_KELVIN,
        maxKelvin = readNumber(entity, "max_color_temp_kelvin")?.roundToInt() ?: DEFAULT_MAX_KELVIN,
        rgb = if (isOn) readRgb(entity) else null,
        effects = readTextList(entity, "effect_list"),
        effect = readText(entity, "effect"),
    )
}

/** Five whites, from the warmest the bulb can show to the coolest, evenly spread. */
fun whiteSteps(view: LightView): List<Int> =
    (0 until WHITE_STEP_COUNT).map { index ->
        val share = index.toDouble() / (WHITE_STEP_COUNT - 1)
        (view.minKelvin + (view.maxKelvin - view.minKelvin) * share).roundToInt()
    }

/** The brightness one step up (direction 1) or down (-1): the next multiple of ten, kept between 1 and 100. */
fun nextBrightness(current: Int?, direction: Int): Int {
    val from = (current ?: 0).toDouble()
    val next = if (direction > 0) {
        floor(from / BRIGHTNESS_STEP) * BRIGHTNESS_STEP + BRIGHTNESS_STEP
    } else {
        ceil(from / BRIGHTNESS_STEP) * BRIGHTNESS_STEP - BRIGHTNESS_STEP
    }
    return next.toInt().coerceIn(1, 100)
}

private fun brightnessPercent(entity: EntityState): Int? {
    val brightness = readNumber(entity, "brightness") ?: return null
    return maxOf(1, (brightness / 255 * 100).roundToInt())
}

private fun readRgb(entity: EntityState): List<Int>? {
    val value = entity.attributes["rgb_color"] as? JsonArray ?: return null
    if (value.size != 3) return null
    val numbers = value.map { numberOrNull(it) ?: return null }
    return numbers.map { it.roundToInt() }
}
