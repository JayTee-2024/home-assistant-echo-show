package net.badacid.homeassist.echoshow.devices.sensor

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow

fun sensorPanel(entity: EntityState): List<PanelRow> = listOf(PanelRow("Reading", listOf(PanelControl.Value(sensorText(readSensor(entity))))))
