package net.badacid.homeassist.echoshow.devices.sensor

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction

/** A reading and its unit. Sensors have nothing to send, so there is no SensorActions. */
object SensorLogic : DeviceLogic {
    override val id = "sensor"
    override val label = "Sensor"
    override val icon = "M22 12h-4l-3 9L9 3l-3 9H2"
    override val domains = listOf("sensor")
    override val mainOrder = 100
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction = OkAction.Nothing

    override fun isActive(entity: EntityState) = false

    override fun summary(entity: EntityState) = sensorText(readSensor(entity))
}
