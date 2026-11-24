package net.badacid.homeassist.echoshow.testsupport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.jsonValue
import net.badacid.homeassist.echoshow.core.readEntityState
import net.badacid.homeassist.echoshow.core.readRegistryAreas
import net.badacid.homeassist.echoshow.core.readRegistryDevices
import net.badacid.homeassist.echoshow.core.readRegistryEntities

/*
 * Gives tests the sample home from sample-home.json (a copy of the LG app's fixtures/sample-home.json)
 * in the shapes the app uses. Test support only.
 */

private val raw: JsonObject by lazy {
    val text = object {}.javaClass.getResource("/sample-home.json")!!.readText()
    Json.parseToJsonElement(text).jsonObject
}

/** The sample home. Values are immutable, so every caller can share it. */
fun sampleHome(): HomeSnapshot {
    val states = (raw["states"] as JsonArray).mapNotNull { readEntityState(it) }
    return HomeSnapshot(
        states = states.associateBy { it.entityId },
        entities = readRegistryEntities(raw["entities"]),
        devices = readRegistryDevices(raw["devices"]),
        areas = readRegistryAreas(raw["areas"]),
    )
}

/** One entity from the sample home. Throws when the id is not in the sample, to catch typos in tests. */
fun sampleEntity(entityId: String): EntityState =
    sampleHome().states[entityId] ?: error("No entity \"$entityId\" in sample-home.json")

/** A copy of `entity` with some attributes replaced, and optionally a different state. */
fun changed(entity: EntityState, attributes: Map<String, Any?>, state: String = entity.state): EntityState =
    EntityState(
        entityId = entity.entityId,
        state = state,
        attributes = JsonObject(entity.attributes + attributes.mapValues { toJson(it.value) }),
    )

/** Kotlin values as JSON, for building test data (see jsonValue). */
fun toJson(value: Any?): JsonElement = jsonValue(value)

/** A JSON value from text, for registry and attribute tests that read best as JSON. */
fun json(text: String): JsonElement = Json.parseToJsonElement(text)
