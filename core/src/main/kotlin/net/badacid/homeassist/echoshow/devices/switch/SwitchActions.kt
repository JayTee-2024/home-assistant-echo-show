package net.badacid.homeassist.echoshow.devices.switch

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.domainOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

/** Uses the entity's own type for the service, so on/off helpers work too. */
fun toggle(entity: EntityState): Action = Action(
    ServiceCall(domainOf(entity.entityId), "toggle", entity.entityId, JsonObject(emptyMap())),
    predict(entity, if (entity.state == "on") "off" else "on"),
)
