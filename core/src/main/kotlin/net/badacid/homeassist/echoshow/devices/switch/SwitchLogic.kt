package net.badacid.homeassist.echoshow.devices.switch

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE

object SwitchLogic : DeviceLogic {
    override val id = "switch"
    override val label = "Switch"
    override val icon = "M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0V8zM12 18v4"
    override val domains = listOf("switch", "input_boolean")
    override val mainOrder = 80
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readSwitch(entity).isAvailable) OkAction.Run(toggle(entity)) else OkAction.Nothing

    override fun isActive(entity: EntityState) = readSwitch(entity).isOn

    override fun summary(entity: EntityState): String {
        val view = readSwitch(entity)
        if (!view.isAvailable) return UNAVAILABLE
        return if (view.isOn) "On" else "Off"
    }
}
