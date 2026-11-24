package net.badacid.homeassist.echoshow.devices.climate

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict

fun setTarget(entity: EntityState, temperature: Double): Action = Action(
    ServiceCall("climate", "set_temperature", entity.entityId, jsonObjectOf("temperature" to temperature)),
    predict(entity, entity.state, withDoing(entity, entity.state, jsonObjectOf("temperature" to temperature))),
)

/**
 * What the thermostat will be doing after a press, for the card until Home Assistant reports (a cloud
 * thermostat such as Tado can take a while): "off" when switched off; "heating" or "cooling" when the target
 * is beyond the room temperature in that mode; otherwise "idle". Without this the card kept the old word, so
 * a thermostat just switched on still said "Off". A thermostat that never says what it is doing gets no guess.
 */
private fun withDoing(entity: EntityState, mode: String, attributes: JsonObject): JsonObject {
    if (readClimate(entity).hvacAction == null) return attributes
    val after = readClimate(predict(entity, mode, attributes))
    val room = after.currentTemperature
    val target = after.targetTemperature
    val doing = when {
        mode == "off" -> "off"
        room == null || target == null -> "idle"
        mode == "heat" && target > room -> "heating"
        mode == "cool" && target < room -> "cooling"
        else -> "idle"
    }
    return JsonObject(attributes + ("hvac_action" to JsonPrimitive(doing)))
}

fun setHvacMode(entity: EntityState, mode: String): Action = Action(
    ServiceCall("climate", "set_hvac_mode", entity.entityId, jsonObjectOf("hvac_mode" to mode)),
    predict(entity, mode, withDoing(entity, mode, JsonObject(emptyMap()))),
)

fun setPreset(entity: EntityState, preset: String): Action = Action(
    ServiceCall("climate", "set_preset_mode", entity.entityId, jsonObjectOf("preset_mode" to preset)),
    predict(entity, entity.state, jsonObjectOf("preset_mode" to preset)),
)
