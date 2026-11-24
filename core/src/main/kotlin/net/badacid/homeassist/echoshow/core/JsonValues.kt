package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Kotlin values as JSON: text, numbers, booleans, null, lists and maps (with text keys), and JSON itself.
 * Whole numbers should be passed as Int or Long, so they read back as "40" and not "40.0".
 */
fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { jsonValue(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { it.key as String to jsonValue(it.value) })
    else -> throw IllegalArgumentException("Cannot turn ${value::class} into JSON")
}

/** A JSON object from name-value pairs: jsonObjectOf("brightness_pct" to 40). */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { it.first to jsonValue(it.second) })
