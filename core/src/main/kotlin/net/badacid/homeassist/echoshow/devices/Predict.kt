/* Small helpers the device types share for building predicted states and readable words. */
package net.badacid.homeassist.echoshow.devices

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState

/** A copy of `entity` with a new state and some attributes replaced. The original is not changed. */
fun predict(entity: EntityState, state: String, attributes: Map<String, JsonElement> = emptyMap()): EntityState =
    EntityState(entity.entityId, state, JsonObject(entity.attributes + attributes))

/** Turns Home Assistant's lower_case_names into words for the screen: 'effect_pulse' → 'Effect pulse'. */
fun readableName(text: String): String =
    text.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

/** The word a card shows for an entity Home Assistant cannot reach. */
const val UNAVAILABLE = "Unavailable"
