package net.badacid.homeassist.echoshow.devices.switch

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.isAvailable

data class SwitchView(val isOn: Boolean, val isAvailable: Boolean)

fun readSwitch(entity: EntityState) = SwitchView(isOn = entity.state == "on", isAvailable = isAvailable(entity))
