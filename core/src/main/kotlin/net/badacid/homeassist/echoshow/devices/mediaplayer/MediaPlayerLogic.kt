package net.badacid.homeassist.echoshow.devices.mediaplayer

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.DeviceLogic
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE
import net.badacid.homeassist.echoshow.devices.readableName

object MediaPlayerLogic : DeviceLogic {
    override val id = "media_player"
    override val label = "Media"
    override val icon = "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    override val domains = listOf("media_player")
    override val mainOrder = 30
    override val cardSize = CardSize.WIDE

    override fun supports(entity: EntityState) = true

    override fun okAction(entity: EntityState): OkAction {
        if (!readMediaPlayer(entity).isAvailable) return OkAction.Nothing
        return playPause(entity)?.let { OkAction.Run(it) } ?: OkAction.OpenPanel
    }

    override fun isActive(entity: EntityState) = readMediaPlayer(entity).isPlaying

    override fun summary(entity: EntityState): String {
        val view = readMediaPlayer(entity)
        if (!view.isAvailable) return UNAVAILABLE
        val state = readableName(view.state)
        return if (view.title == null) state else "$state · ${view.title}"
    }

    override fun panel(entity: EntityState) = mediaPlayerPanel(entity)
}
