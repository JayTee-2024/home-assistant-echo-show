package net.badacid.homeassist.echoshow.devices.binarysensor

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction

/** A yes/no reading put into words for its kind. Nothing to send, so there is no BinarySensorActions. */
object BinarySensorLogic : DeviceLogic {
    override val id = "binary_sensor"
    override val label = "Sensor"
    override val icon = "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
    override val domains = listOf("binary_sensor")
    override val mainOrder = 110
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction = OkAction.Nothing

    override fun isActive(entity: EntityState) = readBinarySensor(entity).isOn

    override fun summary(entity: EntityState) = readBinarySensor(entity).words

    override fun panel(entity: EntityState) = binarySensorPanel(entity)
}
