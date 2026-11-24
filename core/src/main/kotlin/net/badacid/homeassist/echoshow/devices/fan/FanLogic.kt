package net.badacid.homeassist.echoshow.devices.fan

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE

object FanLogic : DeviceLogic {
    override val id = "fan"
    override val label = "Fan"
    override val icon = "M12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 12c0 4-2 8-6 8M12 12c4 0 8 2 8 6M12 12c-4 0-8-2-8-6"
    override val domains = listOf("fan")
    override val mainOrder = 70
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readFan(entity).isAvailable) OkAction.Run(toggle(entity)) else OkAction.Nothing

    override fun isActive(entity: EntityState) = readFan(entity).isOn

    override fun summary(entity: EntityState): String {
        val view = readFan(entity)
        if (!view.isAvailable) return UNAVAILABLE
        if (!view.isOn) return "Off"
        return if (view.speedPercent == null) "On" else "On · ${view.speedPercent}%"
    }

    override fun panel(entity: EntityState) = fanPanel(entity)
}
