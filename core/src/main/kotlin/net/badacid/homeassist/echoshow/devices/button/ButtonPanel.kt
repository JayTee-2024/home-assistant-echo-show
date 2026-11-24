package net.badacid.homeassist.echoshow.devices.button

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press

fun buttonPanel(entity: EntityState): List<PanelRow> {
    val view = readButton(entity)
    return listOf(PanelRow("Action", listOf(press(view.verb, press(entity), view.canPress))))
}
