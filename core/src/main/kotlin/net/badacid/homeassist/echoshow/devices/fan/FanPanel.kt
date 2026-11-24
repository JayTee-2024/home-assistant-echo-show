package net.badacid.homeassist.echoshow.devices.fan

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press
import net.badacid.homeassist.echoshow.devices.readableName

/** On/off, speed − and +, oscillation and presets. */
fun fanPanel(entity: EntityState): List<PanelRow> {
    val view = readFan(entity)
    val ok = view.isAvailable
    val rows = mutableListOf(PanelRow(if (view.isOn) "On" else "Off", listOf(press(if (view.isOn) "Turn off" else "Turn on", toggle(entity), ok))))
    if (view.canSetSpeed) {
        val from = if (view.isOn) view.speedPercent else 0
        rows += PanelRow("Speed", listOf(
            press("−", setSpeed(entity, nextSpeed(from, view.step, -1)), ok),
            PanelControl.Value(if (!view.isOn || view.speedPercent == null) "Off" else "${view.speedPercent}%"),
            press("+", setSpeed(entity, nextSpeed(from, view.step, 1)), ok),
        ))
    }
    if (view.canOscillate) {
        rows += PanelRow("Oscillate", listOf(press(if (view.isOscillating) "On" else "Off", oscillate(entity, !view.isOscillating), ok, isCurrent = view.isOscillating)))
    }
    if (view.presets.isNotEmpty()) {
        rows += PanelRow("Preset", view.presets.map { press(readableName(it), setPreset(entity, it), ok, isCurrent = view.preset == it) })
    }
    return rows
}
