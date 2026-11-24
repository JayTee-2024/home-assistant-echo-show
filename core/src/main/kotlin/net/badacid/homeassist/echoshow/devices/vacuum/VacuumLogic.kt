package net.badacid.homeassist.echoshow.devices.vacuum

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction

object VacuumLogic : DeviceLogic {
    override val id = "vacuum"
    override val label = "Vacuum"
    override val icon = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
    override val domains = listOf("vacuum")
    override val mainOrder = 40
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readVacuum(entity).isAvailable) OkAction.OpenPanel else OkAction.Nothing

    override fun isActive(entity: EntityState) = entity.state == "cleaning" || entity.state == "returning"

    override fun summary(entity: EntityState) = readVacuum(entity).words

    override fun panel(entity: EntityState) = vacuumPanel(entity)
}
