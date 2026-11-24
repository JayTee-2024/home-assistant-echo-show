package net.badacid.homeassist.echoshow.devices.climate

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press
import net.badacid.homeassist.echoshow.devices.readableName

/** How long the target rests before Home Assistant is told: quick presses send once. */
const val TARGET_SEND_DELAY_MS = 1500L

/** The room temperature, the target with − and +, the modes and the presets. */
fun climatePanel(entity: EntityState): List<PanelRow> {
    val view = readClimate(entity)
    val ok = view.isAvailable
    val rows = mutableListOf(PanelRow("Room", listOf(PanelControl.Value(formatTemperature(view.currentTemperature)))))
    if (view.canSetTarget) {
        rows += PanelRow("Target", listOf(PanelControl.Stepper(
            value = view.targetTemperature,
            step = { from, direction -> stepTarget(view, from, direction) },
            format = ::formatTemperature,
            send = { setTarget(entity, it) },
            debounceMs = TARGET_SEND_DELAY_MS,
            isEnabled = ok,
        )))
    }
    if (view.hvacModes.isNotEmpty()) {
        rows += PanelRow("Mode", view.hvacModes.map { press(readableName(it), setHvacMode(entity, it), ok, isCurrent = view.hvacMode == it) })
    }
    if (view.presetModes.isNotEmpty()) {
        rows += PanelRow("Preset", view.presetModes.map { press(readableName(it), setPreset(entity, it), ok, isCurrent = view.presetMode == it) })
    }
    return rows
}
