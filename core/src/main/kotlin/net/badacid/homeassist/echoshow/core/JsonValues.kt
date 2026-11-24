package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Kotlin values as JSON: text, numbers, booleans, null, lists and maps (with text keys), and JSON itself.
 * A whole Double is written as a whole number (22.0 as 22), so values compare equal to Home Assistant's own.
 */
fun jsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    // A whole Double is written without ".0", as JavaScript and Home Assistant write it: 22.0 → 22.
    is Double -> if (isWhole(value)) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
    is Float -> jsonValue(value.toDouble())
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { jsonValue(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { it.key as String to jsonValue(it.value) })
    else -> throw IllegalArgumentException("Cannot turn ${value::class} into JSON")
}

/** A JSON object from name-value pairs: jsonObjectOf("brightness_pct" to 40). */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { it.first to jsonValue(it.second) })

private fun isWhole(value: Double) = value.isFinite() && value == Math.rint(value) && kotlin.math.abs(value) < 1e15

/** A number as JavaScript writes it: 21 for 21.0, 20.5 for 20.5. */
fun numberText(value: Double): String = if (isWhole(value)) value.toLong().toString() else value.toString()
