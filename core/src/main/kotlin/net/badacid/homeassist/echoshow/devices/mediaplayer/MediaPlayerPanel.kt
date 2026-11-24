package net.badacid.homeassist.echoshow.devices.mediaplayer

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press
import net.badacid.homeassist.echoshow.devices.readableName
import kotlin.math.roundToInt

const val VOLUME_SEND_DELAY_MS = 500L

/** What is playing, the transport buttons, volume and mute, sources and power. */
fun mediaPlayerPanel(entity: EntityState): List<PanelRow> {
    val view = readMediaPlayer(entity)
    val ok = view.isAvailable
    val rows = mutableListOf<PanelRow>()
    if (view.title != null) {
        rows += PanelRow("Playing", listOf(PanelControl.Value(if (view.artist == null) view.title else view.title + " · " + view.artist)))
    }
    if (view.canPlayPause || view.canSkip) {
        val playAction = playPause(entity)
        rows += PanelRow(readableName(view.state), buildList {
            if (view.canSkip) add(press("Previous", previous(entity), ok))
            if (view.canPlayPause) add(press(if (view.isPlaying) "Pause" else "Play", playAction, ok))
            if (view.canSkip) add(press("Next", next(entity), ok))
        })
    }
    if (view.canSetVolume || view.canMute) {
        rows += PanelRow("Volume", buildList {
            if (view.canSetVolume) {
                // Home Assistant drops a player's volume while it is off: − and + would then guess.
                add(PanelControl.Stepper(
                    value = view.volumePercent?.toDouble(),
                    step = { from, direction -> nextVolume(from?.roundToInt(), direction).toDouble() },
                    format = { it?.let { v -> "${v.roundToInt()}%" } ?: "–" },
                    send = { setVolume(entity, it.roundToInt()) },
                    debounceMs = VOLUME_SEND_DELAY_MS,
                    isEnabled = ok && view.volumePercent != null,
                ))
            }
            if (view.canMute) add(press(if (view.isMuted) "Unmute" else "Mute", mute(entity, !view.isMuted), ok, isCurrent = view.isMuted))
        })
    }
    if (view.canSelectSource && view.sources.isNotEmpty()) {
        rows += PanelRow("Source", view.sources.map { press(it, selectSource(entity, it), ok, isCurrent = view.source == it) })
    }
    if (view.canTurnOn || view.canTurnOff) {
        val isOff = view.state == "off" || view.state == "standby"
        val allowed = if (isOff) view.canTurnOn else view.canTurnOff
        rows += PanelRow("Power", listOf(press(if (isOff) "Turn on" else "Turn off", if (allowed) (if (isOff) turnOn(entity) else turnOff(entity)) else null, ok)))
    }
    return rows
}
