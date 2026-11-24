package net.badacid.homeassist.echoshow.devices.button

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction

object ButtonLogic : DeviceLogic {
    override val id = "button"
    override val label = "Button"
    override val icon = "M5 3l14 9-14 9V3z"
    override val domains = listOf("button", "input_button", "scene", "script")
    override val mainOrder = 90
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readButton(entity).canPress) OkAction.Run(press(entity)) else OkAction.Nothing

    override fun isActive(entity: EntityState) = false

    override fun summary(entity: EntityState): String? = null
}
