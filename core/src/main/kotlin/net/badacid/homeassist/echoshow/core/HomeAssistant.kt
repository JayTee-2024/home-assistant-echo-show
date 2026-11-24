/*
 * The Home Assistant data the app reads, described once, and small functions that take values out of it
 * safely.
 *
 * Home Assistant sends attributes as loosely shaped JSON, and what is present differs from one integration
 * to the next. Code elsewhere never reaches into `attributes` directly: it uses the readers below, which
 * answer null (or an empty list) for anything missing or of the wrong kind.
 *
 * This file does not talk to Home Assistant. Home Assistant's own description of these shapes:
 * https://developers.home-assistant.io/docs/core/entity
 */
package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** One entity's current state, as Home Assistant reports it. */
data class EntityState(
    /** For example 'light.lounge_lamp'. The part before the dot is the entity's type. */
    val entityId: String,
    /** For example 'on', 'off', '20.5', 'unavailable'. Always text. */
    val state: String,
    val attributes: JsonObject,
)

/** An entity's entry in Home Assistant's entity registry: where it belongs and whether it is shown. */
data class RegistryEntity(
    val entityId: String,
    val deviceId: String?,
    val areaId: String?,
    /** Set when the entity is switched off in Home Assistant. Such entities have no state. */
    val disabledBy: String?,
    /** Set when the entity is hidden from Home Assistant's own screens. */
    val hiddenBy: String?,
    /** 'config' or 'diagnostic' for settings and technical readings; null for ordinary entities. */
    val entityCategory: String?,
    /** The name the person gave it, if any. */
    val name: String?,
    /** The name the integration gave it. */
    val originalName: String?,
)

/** A device's entry in Home Assistant's device registry: one physical thing. */
data class RegistryDevice(
    val id: String,
    val name: String?,
    /** The name the person gave it, if any. */
    val nameByUser: String?,
    val areaId: String?,
    val disabledBy: String?,
)

/** A room, in Home Assistant's words an "area". */
data class RegistryArea(val areaId: String, val name: String)

/** Everything the app knows about the home at one moment. */
data class HomeSnapshot(
    val states: Map<String, EntityState>,
    val entities: List<RegistryEntity>,
    val devices: List<RegistryDevice>,
    val areas: List<RegistryArea>,
)

val EMPTY_HOME = HomeSnapshot(emptyMap(), emptyList(), emptyList(), emptyList())

/** The entity's type: 'light' for 'light.lounge_lamp'. Empty text when there is no dot. */
fun domainOf(entityId: String): String {
    val dot = entityId.indexOf('.')
    return if (dot == -1) "" else entityId.substring(0, dot)
}

/** False when Home Assistant cannot reach the entity or does not know its state. */
fun isAvailable(entity: EntityState): Boolean = entity.state != "unavailable" && entity.state != "unknown"

/** The attribute as a number, or null when it is missing or not a real number (text that looks like one is not). */
fun readNumber(entity: EntityState, name: String): Double? = numberOrNull(entity.attributes[name])

/** The attribute as text, or null when it is missing or not text. */
fun readText(entity: EntityState, name: String): String? = textOrNull(entity.attributes[name])

/** The attribute as a list of text. Empty when it is missing or not a list; other kinds of item are dropped. */
fun readTextList(entity: EntityState, name: String): List<String> {
    val value = entity.attributes[name] as? JsonArray ?: return emptyList()
    return value.mapNotNull { textOrNull(it) }
}

/**
 * Whether the entity says it supports a feature. Home Assistant packs these into one number,
 * `supported_features`, one bit per feature; each device type's model names the bits it uses.
 */
fun hasFeature(entity: EntityState, bit: Int): Boolean {
    val features = readNumber(entity, "supported_features")?.toLong() ?: 0L
    return features and bit.toLong() != 0L
}

/** One entry of Home Assistant's state list, or null when it has no id or no text state. */
fun readEntityState(value: JsonElement?): EntityState? {
    val item = value as? JsonObject ?: return null
    val entityId = textOrNull(item["entity_id"]) ?: return null
    val state = textOrNull(item["state"]) ?: return null
    val attributes = item["attributes"] as? JsonObject ?: JsonObject(emptyMap())
    return EntityState(entityId, state, attributes)
}

/*
 * The three functions below turn the registry lists Home Assistant sends into the shapes above. They keep
 * only the fields the app uses. A missing or wrongly typed field becomes null, and an entry with no id is
 * dropped, so newer Home Assistant versions that add fields, or send entries with fewer of them, cannot
 * break the app.
 */

fun readRegistryEntities(value: JsonElement?): List<RegistryEntity> =
    records(value).mapNotNull { item ->
        val entityId = textOrNull(item["entity_id"]) ?: return@mapNotNull null
        RegistryEntity(
            entityId = entityId,
            deviceId = textOrNull(item["device_id"]),
            areaId = textOrNull(item["area_id"]),
            disabledBy = textOrNull(item["disabled_by"]),
            hiddenBy = textOrNull(item["hidden_by"]),
            entityCategory = textOrNull(item["entity_category"]),
            name = textOrNull(item["name"]),
            originalName = textOrNull(item["original_name"]),
        )
    }

fun readRegistryDevices(value: JsonElement?): List<RegistryDevice> =
    records(value).mapNotNull { item ->
        val id = textOrNull(item["id"]) ?: return@mapNotNull null
        RegistryDevice(
            id = id,
            name = textOrNull(item["name"]),
            nameByUser = textOrNull(item["name_by_user"]),
            areaId = textOrNull(item["area_id"]),
            disabledBy = textOrNull(item["disabled_by"]),
        )
    }

fun readRegistryAreas(value: JsonElement?): List<RegistryArea> =
    records(value).mapNotNull { item ->
        val areaId = textOrNull(item["area_id"]) ?: return@mapNotNull null
        val name = textOrNull(item["name"]) ?: return@mapNotNull null
        RegistryArea(areaId, name)
    }

/** The objects in a list, skipping anything in it that is not an object. Empty when `value` is not a list. */
private fun records(value: JsonElement?): List<JsonObject> =
    (value as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

/** A JSON text value, or null for anything else (numbers, booleans, null, lists, objects). */
fun textOrNull(value: JsonElement?): String? =
    (value as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A JSON number that is finite, or null for anything else (including text that looks like a number). */
fun numberOrNull(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.doubleOrNull?.takeIf { it.isFinite() }
}
