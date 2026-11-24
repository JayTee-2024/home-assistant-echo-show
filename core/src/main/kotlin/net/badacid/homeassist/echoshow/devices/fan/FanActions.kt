package net.badacid.homeassist.echoshow.devices.fan

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

fun toggle(entity: EntityState): Action = send(entity, "toggle", state = if (entity.state == "on") "off" else "on")

fun setSpeed(entity: EntityState, percent: Int): Action {
    val change = jsonObjectOf("percentage" to percent)
    return send(entity, "set_percentage", change, if (percent > 0) "on" else "off", change)
}

fun oscillate(entity: EntityState, isOscillating: Boolean): Action {
    val change = jsonObjectOf("oscillating" to isOscillating)
    return send(entity, "oscillate", change, entity.state, change)
}

fun setPreset(entity: EntityState, preset: String): Action {
    val change = jsonObjectOf("preset_mode" to preset)
    return send(entity, "set_preset_mode", change, "on", change)
}

private fun send(
    entity: EntityState,
    service: String,
    data: JsonObject = JsonObject(emptyMap()),
    state: String,
    attributes: JsonObject = JsonObject(emptyMap()),
): Action = Action(ServiceCall("fan", service, entity.entityId, data), predict(entity, state, attributes))
