package net.badacid.homeassist.echoshow.devices.cover

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

fun open(entity: EntityState): Action = send(entity, "open_cover", state = "opening")

fun close(entity: EntityState): Action = send(entity, "close_cover", state = "closing")

fun stop(entity: EntityState): Action = send(entity, "stop_cover", state = entity.state)

fun setPosition(entity: EntityState, position: Int): Action {
    val kept = position.coerceIn(0, 100)
    return send(
        entity, "set_cover_position", jsonObjectOf("position" to kept), entity.state,
        jsonObjectOf("current_position" to kept),
    )
}

private fun send(
    entity: EntityState,
    service: String,
    data: JsonObject = JsonObject(emptyMap()),
    state: String,
    attributes: JsonObject = JsonObject(emptyMap()),
): Action = Action(ServiceCall("cover", service, entity.entityId, data), predict(entity, state, attributes))
