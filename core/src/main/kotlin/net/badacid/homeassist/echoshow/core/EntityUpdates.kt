/*
 * Applies one event of Home Assistant's subscribe_entities to the app's map of states.
 *
 * The events use a short form (https://developers.home-assistant.io/docs/api/websocket):
 *   a  whole entities:    { "light.x": { "s": "on", "a": { ...attributes }, "c": ..., "lc": ..., "lu": ... } }
 *   c  changes:           { "light.x": { "+": { "s": "off", "a": { changed attributes } }, "-": { "a": [removed names] } } }
 *   r  removed entities:  [ "light.x" ]
 *
 * Every entity the event does not name keeps the very same object it had. Pending states (PendingStates.kt)
 * rely on that to tell "Home Assistant reported this entity" from "something else changed".
 */
package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

fun applyEntityUpdate(states: Map<String, EntityState>, event: JsonObject): Map<String, EntityState> {
    var updated: MutableMap<String, EntityState>? = null
    fun target(): MutableMap<String, EntityState> = updated ?: LinkedHashMap(states).also { updated = it }

    (event["a"] as? JsonObject)?.forEach { (entityId, value) ->
        val whole = value as? JsonObject ?: return@forEach
        val state = textOrNull(whole["s"]) ?: return@forEach
        val attributes = whole["a"] as? JsonObject ?: JsonObject(emptyMap())
        target()[entityId] = EntityState(entityId, state, attributes)
    }

    (event["c"] as? JsonObject)?.forEach { (entityId, value) ->
        val change = value as? JsonObject ?: return@forEach
        val current = (updated ?: states)[entityId] ?: return@forEach
        val added = change["+"] as? JsonObject
        val removed = (change["-"] as? JsonObject)?.get("a") as? JsonArray

        val state = textOrNull(added?.get("s")) ?: current.state
        val attributes = LinkedHashMap(current.attributes)
        (added?.get("a") as? JsonObject)?.let { attributes.putAll(it) }
        removed?.forEach { name -> textOrNull(name)?.let { attributes.remove(it) } }
        target()[entityId] = EntityState(entityId, state, JsonObject(attributes))
    }

    (event["r"] as? JsonArray)?.forEach { value ->
        val entityId = textOrNull(value) ?: return@forEach
        if (entityId in (updated ?: states)) target().remove(entityId)
    }

    return updated ?: states
}
