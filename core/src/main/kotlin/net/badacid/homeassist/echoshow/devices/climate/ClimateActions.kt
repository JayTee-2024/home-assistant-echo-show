package net.badacid.homeassist.echoshow.devices.climate

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

fun setTarget(entity: EntityState, temperature: Double): Action = Action(
    ServiceCall("climate", "set_temperature", entity.entityId, jsonObjectOf("temperature" to temperature)),
    predict(entity, entity.state, jsonObjectOf("temperature" to temperature)),
)

fun setHvacMode(entity: EntityState, mode: String): Action = Action(
    ServiceCall("climate", "set_hvac_mode", entity.entityId, jsonObjectOf("hvac_mode" to mode)),
    predict(entity, mode),
)

fun setPreset(entity: EntityState, preset: String): Action = Action(
    ServiceCall("climate", "set_preset_mode", entity.entityId, jsonObjectOf("preset_mode" to preset)),
    predict(entity, entity.state, jsonObjectOf("preset_mode" to preset)),
)
