package net.badacid.homeassist.echoshow.devices.button

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.domainOf
import net.badacid.homeassist.echoshow.devices.Action

/** Presses a button, or runs a scene or script. Predicts no change: a press has no lasting state. */
fun press(entity: EntityState): Action {
    val domain = domainOf(entity.entityId)
    val service = if (domain == "scene" || domain == "script") "turn_on" else "press"
    return Action(ServiceCall(domain, service, entity.entityId, JsonObject(emptyMap())), entity)
}
