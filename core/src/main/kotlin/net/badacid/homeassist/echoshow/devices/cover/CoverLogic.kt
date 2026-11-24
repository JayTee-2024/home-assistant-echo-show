package net.badacid.homeassist.echoshow.devices.cover

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.SAFE_COVER_KINDS
import net.badacid.homeassist.echoshow.core.readText
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE

/** Blinds, curtains and shutters. Never doors, garages or gates: only SAFE_COVER_KINDS are supported. */
object CoverLogic : DeviceLogic {
    override val id = "cover"
    override val label = "Blind"
    override val icon = "M3 3h18M5 3v12h14V3M5 7h14M5 11h14M12 15v5"
    override val domains = listOf("cover")
    override val mainOrder = 60
    override val cardSize = CardSize.SMALL

    override fun supports(entity: EntityState): Boolean {
        val kind = readText(entity, "device_class")
        return kind != null && kind in SAFE_COVER_KINDS
    }

    override fun okAction(entity: EntityState): OkAction =
        if (readCover(entity).isAvailable) OkAction.OpenPanel else OkAction.Nothing

    override fun isActive(entity: EntityState) = entity.state != "closed" && readCover(entity).isAvailable

    override fun summary(entity: EntityState): String {
        val view = readCover(entity)
        if (!view.isAvailable) return UNAVAILABLE
        val words = coverWords(view).replace("…", "")
        return if (view.position == null) words else "$words · ${view.position}%"
    }
}
