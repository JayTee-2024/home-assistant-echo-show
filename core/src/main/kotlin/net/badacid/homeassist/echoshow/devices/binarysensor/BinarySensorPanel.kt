package net.badacid.homeassist.echoshow.devices.binarysensor

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow

fun binarySensorPanel(entity: EntityState): List<PanelRow> = listOf(PanelRow("State", listOf(PanelControl.Value(readBinarySensor(entity).words))))
