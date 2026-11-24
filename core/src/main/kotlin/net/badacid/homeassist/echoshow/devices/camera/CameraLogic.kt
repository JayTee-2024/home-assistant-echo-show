package net.badacid.homeassist.echoshow.devices.camera

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.PanelLayout

/** Cameras: a still picture from Home Assistant and live video. Nothing to send, so no CameraActions. */
object CameraLogic : DeviceLogic {
    override val id = "camera"
    override val label = "Camera"
    override val icon = "M23 7l-7 5 7 5V7zM1 5h15v14H1z"
    override val domains = listOf("camera")
    override val mainOrder = 10
    override val cardSize = CardSize.LARGE
    override val panelLayout = PanelLayout.FULL_SCREEN

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction =
        if (readCamera(entity).isAvailable) OkAction.OpenPanel else OkAction.Nothing

    override fun isActive(entity: EntityState) = false

    override fun summary(entity: EntityState): String? = null
}
