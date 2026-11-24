package net.badacid.homeassist.echoshow.devices.light

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press
import net.badacid.homeassist.echoshow.devices.readableName

/** Twelve fixed colours rather than a colour wheel, as the LG app. */
private val COLOURS = listOf(
    "Red" to listOf(255, 0, 0), "Orange" to listOf(255, 110, 0), "Amber" to listOf(255, 180, 84),
    "Yellow" to listOf(255, 230, 0), "Lime" to listOf(150, 255, 0), "Green" to listOf(0, 220, 60),
    "Teal" to listOf(0, 200, 170), "Cyan" to listOf(0, 200, 255), "Blue" to listOf(0, 80, 255),
    "Purple" to listOf(140, 60, 255), "Magenta" to listOf(255, 0, 200), "Pink" to listOf(255, 120, 170),
)

private val WHITE_NAMES = listOf("Warm", "Soft", "Neutral", "Cool", "Daylight")

/** On/off, brightness, and (when the light supports them) shades of white, colours and effects. */
fun lightPanel(entity: EntityState): List<PanelRow> {
    val view = readLight(entity)
    val ok = view.isAvailable
    val rows = mutableListOf(PanelRow(if (view.isOn) "On" else "Off", listOf(press(if (view.isOn) "Turn off" else "Turn on", toggle(entity), ok))))
    if (view.canDim) {
        rows += PanelRow("Brightness", listOf(
            press("−", setBrightness(entity, nextBrightness(view.brightnessPercent, -1)), ok),
            PanelControl.Value(view.brightnessPercent?.let { "$it%" } ?: "Off"),
            press("+", setBrightness(entity, nextBrightness(view.brightnessPercent, 1)), ok),
        ))
    }
    if (view.canSetWhite) {
        rows += PanelRow("White", whiteSteps(view).mapIndexed { index, kelvin ->
            press(WHITE_NAMES.getOrNull(index) ?: "$kelvin K", setWhite(entity, kelvin), ok, isCurrent = view.whiteKelvin == kelvin)
        })
    }
    if (view.canSetColour) {
        rows += PanelRow("Colour", COLOURS.map { (name, rgb) ->
            press(name, setColour(entity, rgb), ok, swatch = (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2])
        })
    }
    if (view.effects.isNotEmpty()) {
        rows += PanelRow("Effect", view.effects.map { press(readableName(it), setEffect(entity, it), ok, isCurrent = view.effect == it) })
    }
    return rows
}
