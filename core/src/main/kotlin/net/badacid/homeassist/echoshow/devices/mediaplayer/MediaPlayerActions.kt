package net.badacid.homeassist.echoshow.devices.mediaplayer

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

/** Plays or pauses. null when the player supports only the one that is not needed. */
fun playPause(entity: EntityState): Action? {
    val view = readMediaPlayer(entity)
    val toState = if (view.isPlaying) "paused" else "playing"
    return when {
        view.canPlay && view.canPause -> send(entity, "media_play_pause", state = toState)
        view.isPlaying && view.canPause -> send(entity, "media_pause", state = toState)
        !view.isPlaying && view.canPlay -> send(entity, "media_play", state = toState)
        else -> null
    }
}

fun next(entity: EntityState): Action = send(entity, "media_next_track")

fun previous(entity: EntityState): Action = send(entity, "media_previous_track")

/** Sets the volume as a percentage; Home Assistant takes it as a fraction. */
fun setVolume(entity: EntityState, percent: Int): Action {
    val level = percent.coerceIn(0, 100) / 100.0
    val change = jsonObjectOf("volume_level" to level)
    return send(entity, "volume_set", change, attributes = change)
}

fun mute(entity: EntityState, isMuted: Boolean): Action {
    val change = jsonObjectOf("is_volume_muted" to isMuted)
    return send(entity, "volume_mute", change, attributes = change)
}

fun selectSource(entity: EntityState, source: String): Action {
    val change = jsonObjectOf("source" to source)
    return send(entity, "select_source", change, attributes = change)
}

fun turnOn(entity: EntityState): Action = send(entity, "turn_on", state = "on")

fun turnOff(entity: EntityState): Action = send(entity, "turn_off", state = "off")

private fun send(
    entity: EntityState,
    service: String,
    data: JsonObject = JsonObject(emptyMap()),
    state: String = entity.state,
    attributes: JsonObject = JsonObject(emptyMap()),
): Action = Action(
    ServiceCall("media_player", service, entity.entityId, data),
    predict(entity, state, attributes),
)
