package net.badacid.homeassist.echoshow.devices.switch

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press

fun switchPanel(entity: EntityState): List<PanelRow> {
    val view = readSwitch(entity)
    return listOf(PanelRow(if (view.isOn) "On" else "Off", listOf(press(if (view.isOn) "Turn off" else "Turn on", toggle(entity), view.isAvailable))))
}
