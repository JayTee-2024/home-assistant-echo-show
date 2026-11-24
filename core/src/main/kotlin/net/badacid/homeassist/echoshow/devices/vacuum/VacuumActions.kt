package net.badacid.homeassist.echoshow.devices.vacuum

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

fun start(entity: EntityState): Action = send(entity, "start", "cleaning")

fun pause(entity: EntityState): Action = send(entity, "pause", "paused")

fun stop(entity: EntityState): Action = send(entity, "stop", "idle")

fun returnHome(entity: EntityState): Action = send(entity, "return_to_base", "returning")

/** Makes the vacuum beep so it can be found. Predicts no change. */
fun locate(entity: EntityState): Action =
    Action(ServiceCall("vacuum", "locate", entity.entityId, JsonObject(emptyMap())), entity)

private fun send(entity: EntityState, service: String, state: String): Action =
    Action(ServiceCall("vacuum", service, entity.entityId, JsonObject(emptyMap())), predict(entity, state))
