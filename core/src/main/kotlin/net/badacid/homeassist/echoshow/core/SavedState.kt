/*
 * What the app keeps on the Echo between runs, as text, and how that text is read back.
 *
 * Bad or old data never stops the app: anything that cannot be read is dropped, down to an empty state.
 * The field names are the LG app's. The app module encrypts the text before writing it (the tokens are in
 * it); this file only turns state into text and back. The password is never part of it.
 */
package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Change this number when the saved shape changes, so old data is ignored and not misread. */
private const val SAVED_VERSION = 1

data class SavedState(
    /** null when signed out. */
    val signIn: SavedSignIn?,
    val layout: Layout,
    /** The Home Assistant address the layout belongs to. Kept through sign-out. */
    val layoutAddress: String?,
    /** The PIN lock's four digits, or null when the lock is off (see PinLock.kt). */
    val pin: String?,
    /** Seconds without a touch before the screen blanks; 0 never (see ScreenSaver.kt). */
    val blankAfterSeconds: Int = DEFAULT_BLANK_AFTER_SECONDS,
)

val EMPTY_SAVED_STATE = SavedState(signIn = null, layout = EMPTY_LAYOUT, layoutAddress = null, pin = null)

fun serialiseSavedState(state: SavedState): String {
    val signIn = state.signIn
    return buildJsonObject {
        put("version", SAVED_VERSION)
        put(
            "signIn",
            if (signIn == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("address", signIn.address)
                    put("kind", signIn.kind.savedName)
                    put("accessToken", signIn.accessToken)
                    put("refreshToken", signIn.refreshToken)
                    put("expiresAt", signIn.expiresAt)
                }
            },
        )
        put(
            "layout",
            buildJsonObject {
                put(
                    "cards",
                    JsonArray(
                        state.layout.cards.map { card ->
                            buildJsonObject {
                                put("deviceId", card.deviceId)
                                put("page", card.page)
                                put("column", card.column)
                                put("row", card.row)
                                put("size", card.size.savedName)
                            }
                        },
                    ),
                )
            },
        )
        put("layoutAddress", state.layoutAddress)
        put("pin", state.pin)
        put("blankAfterSeconds", state.blankAfterSeconds)
    }.toString()
}

fun parseSavedState(text: String?): SavedState {
    if (text.isNullOrEmpty()) return EMPTY_SAVED_STATE
    val saved = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return EMPTY_SAVED_STATE

    val version = saved["version"] as? JsonPrimitive
    if (version == null || version.isString || version.content != SAVED_VERSION.toString()) return EMPTY_SAVED_STATE

    val pin = textOrNull(saved["pin"])
    return SavedState(
        signIn = readSignIn(saved["signIn"]),
        layout = readLayout(saved["layout"]),
        layoutAddress = textOrNull(saved["layoutAddress"]),
        // Absent in data saved before the PIN lock existed: the lock is then off.
        pin = if (pin != null && isValidPin(pin)) pin else null,
        // Absent in data saved before screen blanking existed: the original Echo app's 30 seconds.
        blankAfterSeconds = wholeNumberOrNull(saved["blankAfterSeconds"])?.toInt()?.takeIf { it >= 0 } ?: DEFAULT_BLANK_AFTER_SECONDS,
    )
}

/**
 * The state after a successful sign-in. The layout is kept when it belongs to the same Home Assistant, and
 * cleared when the address is different: another home's devices would all be missing.
 */
fun afterSignIn(state: SavedState, signIn: SavedSignIn): SavedState {
    val sameHome = state.layoutAddress == signIn.address
    return state.copy(signIn = signIn, layout = if (sameHome) state.layout else EMPTY_LAYOUT, layoutAddress = signIn.address)
}

/**
 * The state after signing out: the tokens go, the layout stays for the next sign-in. The PIN goes too: it
 * guards a sign-in, and signing out is the way past a forgotten PIN.
 */
fun afterSignOut(state: SavedState): SavedState =
    state.copy(signIn = null, pin = null)

private fun readSignIn(value: JsonElement?): SavedSignIn? {
    val item = value as? JsonObject ?: return null
    val address = textOrNull(item["address"]) ?: return null
    val accessToken = textOrNull(item["accessToken"]) ?: return null
    val refreshToken = textOrNull(item["refreshToken"]) ?: return null
    val expiresAt = wholeNumberOrNull(item["expiresAt"]) ?: return null
    val kind = textOrNull(item["kind"])?.let { SignInKind.fromSavedName(it) } ?: return null
    return SavedSignIn(address, kind, accessToken, refreshToken, expiresAt)
}

/** The saved cards that can be used: each must be readable, on the 4 by 3 grid, and clear of earlier cards. */
private fun readLayout(value: JsonElement?): Layout {
    val cards = (value as? JsonObject)?.get("cards") as? JsonArray ?: return EMPTY_LAYOUT
    val kept = mutableListOf<CardPlace>()
    for (item in cards) {
        val card = readCard(item) ?: continue
        val clashes = kept.any { it.deviceId == card.deviceId || (it.page == card.page && overlaps(it, card)) }
        if (!clashes) kept += card
    }
    // Empty pages are closed up, so a corrupt page number (50 million, say) cannot make adding a card walk
    // through millions of empty pages.
    return removeEmptyPages(Layout(kept))
}

/** One saved card, or null when any field is missing, of the wrong kind, or off the grid. */
private fun readCard(value: JsonElement?): CardPlace? {
    val item = value as? JsonObject ?: return null
    val deviceId = textOrNull(item["deviceId"]) ?: return null
    val page = wholeNumberOrNull(item["page"])?.toIntOrNull() ?: return null
    val column = wholeNumberOrNull(item["column"])?.toIntOrNull() ?: return null
    val row = wholeNumberOrNull(item["row"])?.toIntOrNull() ?: return null
    val size = textOrNull(item["size"])?.let { CardSize.fromSavedName(it) } ?: return null
    val card = CardPlace(deviceId, page, column, row, size)
    return if (isOnGrid(card)) card else null
}

/** A JSON number that is whole and not negative, or null. */
private fun wholeNumberOrNull(value: JsonElement?): Long? {
    val number = numberOrNull(value) ?: return null
    if (number < 0 || number != Math.floor(number) || number > Long.MAX_VALUE.toDouble()) return null
    // Read the text where it is a plain integer, so large values (times in milliseconds) stay exact.
    return (value as JsonPrimitive).content.toLongOrNull() ?: number.toLong()
}

private fun Long.toIntOrNull(): Int? = if (this in 0..Int.MAX_VALUE) toInt() else null
