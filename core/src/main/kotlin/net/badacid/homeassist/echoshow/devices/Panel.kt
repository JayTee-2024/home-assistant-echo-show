package net.badacid.homeassist.echoshow.devices

import net.badacid.homeassist.echoshow.core.EntityState

/**
 * A panel is a list of rows, each a label then controls, as the LG app's PanelRow, PanelButton and PanelValue
 * (src/devices/shared/controls.tsx). Each type builds its rows from an entity in devices/<type>/<Type>Panel.kt;
 * one view in the app draws them.
 */
data class PanelRow(val label: String, val controls: List<PanelControl> = emptyList())

sealed interface PanelControl {
    /** A button. `action` null: greyed. `isCurrent`: the chosen one of a set. `swatch`: a colour as 0xRRGGBB. */
    data class Press(val label: String, val action: Action?, val isCurrent: Boolean = false, val swatch: Int? = null) : PanelControl

    /** Words, such as "71%". */
    data class Value(val text: String) : PanelControl

    /**
     * − and + around a number that changes on screen at once and is sent once it has rested for `debounceMs`
     * (the LG app's heating target and media volume). `step(from, ±1)` gives the next number, `from` being
     * null when nothing is known; `format` shows a number, or none.
     */
    class Stepper(
        val value: Double?,
        val step: (Double?, Int) -> Double,
        val format: (Double?) -> String,
        val send: (Double) -> Action,
        val debounceMs: Long,
        val isEnabled: Boolean,
    ) : PanelControl
}

/** The panel rows for a part of the given type. Empty for a type with no box panel (the camera). */
fun panelRows(typeId: String, entity: EntityState): List<PanelRow> = deviceLogicFor(typeId)?.panel(entity) ?: emptyList()

/** Turns "Turn off" into a greyed button when the entity is unavailable. */
internal fun press(label: String, action: Action?, isAvailable: Boolean, isCurrent: Boolean = false, swatch: Int? = null) =
    PanelControl.Press(label, if (isAvailable) action else null, isCurrent, swatch)
