package net.badacid.homeassist.echoshow.devices.light

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE

object LightLogic : DeviceLogic {
    override val id = "light"
    override val label = "Light"
    override val icon = "M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z"
    override val domains = listOf("light")
    override val mainOrder = 50
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readLight(entity).isAvailable) OkAction.Run(toggle(entity)) else OkAction.Nothing

    override fun isActive(entity: EntityState) = readLight(entity).isOn

    override fun summary(entity: EntityState): String {
        val view = readLight(entity)
        if (!view.isAvailable) return UNAVAILABLE
        if (!view.isOn) return "Off"
        return if (view.brightnessPercent == null) "On" else "On · ${view.brightnessPercent}%"
    }

    override fun panel(entity: EntityState) = lightPanel(entity)
}
