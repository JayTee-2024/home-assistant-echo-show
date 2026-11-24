package net.badacid.homeassist.echoshow.devices.climate

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction

/** Heating: Simon's Tado thermostats. Target changes are debounced by the panel (see Debounce.kt). */
object ClimateLogic : DeviceLogic {
    override val id = "climate"
    override val label = "Heating"
    override val icon = "M14 14.76V3.5a2.5 2.5 0 0 0-5 0v11.26a4.5 4.5 0 1 0 5 0z"
    override val domains = listOf("climate")
    override val mainOrder = 20
    override val cardSize = CardSize.WIDE

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readClimate(entity).isAvailable) OkAction.OpenPanel else OkAction.Nothing

    override fun isActive(entity: EntityState): Boolean {
        val view = readClimate(entity)
        if (view.hvacAction != null) return view.hvacAction == "heating" || view.hvacAction == "cooling"
        return view.hvacMode != "off"
    }

    override fun summary(entity: EntityState): String {
        val view = readClimate(entity)
        val room = formatTemperature(view.currentTemperature)
        if (view.hvacMode == "off" || view.targetTemperature == null) return "$room · Off"
        return room + " → " + formatTemperature(view.targetTemperature)
    }

    override fun panel(entity: EntityState) = climatePanel(entity)
}
