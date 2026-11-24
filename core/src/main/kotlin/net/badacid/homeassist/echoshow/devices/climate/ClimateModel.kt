package net.badacid.homeassist.echoshow.devices.climate

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.hasFeature
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.core.numberText
import net.badacid.homeassist.echoshow.core.readNumber
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.core.readTextList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

private const val TARGET_TEMPERATURE = 1
private const val DEFAULT_MIN = 7.0
private const val DEFAULT_MAX = 35.0
private const val DEFAULT_STEP = 0.5

data class ClimateView(
    val isAvailable: Boolean,
    /** What it is set to do: 'heat', 'auto', 'off' and so on. */
    val hvacMode: String,
    /** What it is doing right now: 'heating', 'idle'. null when it does not say. */
    val hvacAction: String?,
    val currentTemperature: Double?,
    val targetTemperature: Double?,
    val minTemp: Double,
    val maxTemp: Double,
    val step: Double,
    val hvacModes: List<String>,
    val presetModes: List<String>,
    val presetMode: String?,
    val canSetTarget: Boolean,
)

fun readClimate(entity: EntityState): ClimateView {
    val step = readNumber(entity, "target_temp_step")
    return ClimateView(
        isAvailable = isAvailable(entity),
        hvacMode = entity.state,
        hvacAction = readText(entity, "hvac_action"),
        currentTemperature = readNumber(entity, "current_temperature"),
        targetTemperature = readNumber(entity, "temperature"),
        minTemp = readNumber(entity, "min_temp") ?: DEFAULT_MIN,
        maxTemp = readNumber(entity, "max_temp") ?: DEFAULT_MAX,
        step = if (step != null && step > 0) step else DEFAULT_STEP,
        hvacModes = readTextList(entity, "hvac_modes"),
        presetModes = readTextList(entity, "preset_modes"),
        presetMode = readText(entity, "preset_mode"),
        canSetTarget = hasFeature(entity, TARGET_TEMPERATURE),
    )
}

/** The target one step up (direction 1) or down (-1), on the thermostat's own steps and inside its range. */
fun stepTarget(view: ClimateView, target: Double?, direction: Int): Double {
    val from = target ?: view.currentTemperature ?: view.minTemp
    val stepsFromZero = from / view.step
    // A value already on a step moves a whole step; one between steps moves to the nearest step in that
    // direction. The small allowance absorbs floating-point dust (21.1 / 0.1).
    val nextSteps = if (direction > 0) floor(stepsFromZero + 1e-6) + 1 else ceil(stepsFromZero - 1e-6) - 1
    val next = roundTo(nextSteps * view.step, view.step)
    // As the LG app: max then min, which never fails even when the minimum is above the maximum.
    return maxOf(view.minTemp, minOf(view.maxTemp, next))
}

/** '21°', '20.5°', or '–' when there is no temperature. */
fun formatTemperature(value: Double?): String {
    if (value == null) return "–"
    return numberText((value * 100).roundToLong() / 100.0) + "°"
}

/** `value` rounded to as many decimals as `step` has (0.25 → 2), to clear floating-point dust. */
private fun roundTo(value: Double, step: Double): Double {
    val written = numberText(step)
    val point = written.indexOf('.')
    val decimals = if (point == -1) 0 else written.length - point - 1
    val factor = 10.0.pow(decimals)
    return (value * factor).roundToLong() / factor
}
