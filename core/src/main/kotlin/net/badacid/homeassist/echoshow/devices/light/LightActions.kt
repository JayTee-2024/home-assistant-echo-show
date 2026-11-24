package net.badacid.homeassist.echoshow.devices.light

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.predict
import kotlin.math.roundToInt

fun toggle(entity: EntityState): Action = Action(
    ServiceCall("light", "toggle", entity.entityId, JsonObject(emptyMap())),
    predict(entity, if (entity.state == "on") "off" else "on"),
)

fun setBrightness(entity: EntityState, percent: Int): Action {
    val kept = percent.coerceIn(1, 100)
    return turnOn(
        entity,
        jsonObjectOf("brightness_pct" to kept),
        jsonObjectOf("brightness" to (kept / 100.0 * 255).roundToInt()),
    )
}

fun setWhite(entity: EntityState, kelvin: Int): Action {
    val view = readLight(entity)
    val kept = kelvin.coerceIn(view.minKelvin, view.maxKelvin)
    return turnOn(
        entity,
        jsonObjectOf("color_temp_kelvin" to kept),
        jsonObjectOf("color_temp_kelvin" to kept, "color_mode" to "color_temp", "rgb_color" to null),
    )
}

fun setColour(entity: EntityState, rgb: List<Int>): Action =
    turnOn(entity, jsonObjectOf("rgb_color" to rgb), jsonObjectOf("rgb_color" to rgb, "color_temp_kelvin" to null))

fun setEffect(entity: EntityState, effect: String): Action =
    turnOn(entity, jsonObjectOf("effect" to effect), jsonObjectOf("effect" to effect))

private fun turnOn(entity: EntityState, data: JsonObject, predictedAttributes: JsonObject): Action = Action(
    ServiceCall("light", "turn_on", entity.entityId, data),
    predict(entity, "on", predictedAttributes),
)
