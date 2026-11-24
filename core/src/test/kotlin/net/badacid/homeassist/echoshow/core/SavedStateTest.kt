package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.testsupport.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/saved-state.test.ts: what the app keeps on the Echo, and how it survives bad data. */
class SavedStateTest {
    private val signIn = SavedSignIn("http://192.168.1.100:8123", SignInKind.ACCOUNT, "access-abc", "refresh-def", 1_800_000_000_000)
    private val layout = addCard(addCard(EMPTY_LAYOUT, "device:lamp", CardSize.SMALL), "device:heating", CardSize.WIDE)
    private val state = SavedState(signIn, layout, signIn.address, null)

    /** The saved text as a JSON object, changed by `change`, and written back as text. */
    private fun edited(saved: SavedState, change: (MutableMap<String, JsonElement>) -> Unit): String {
        val map = Json.parseToJsonElement(serialiseSavedState(saved)).jsonObject.toMutableMap()
        change(map)
        return JsonObject(map).toString()
    }

    @Nested
    inner class SavingAndReadingBack {
        @Test
        fun `gives back exactly what was saved`() {
            assertEquals(state, parseSavedState(serialiseSavedState(state)))
        }

        @Test
        fun `gives back a signed-out state exactly too`() {
            val signedOut = SavedState(null, layout, signIn.address, null)
            assertEquals(signedOut, parseSavedState(serialiseSavedState(signedOut)))
        }

        @Test
        fun `writes only the documented fields, in the LG app's names`() {
            val saved = Json.parseToJsonElement(serialiseSavedState(state)).jsonObject
            assertEquals(listOf("blankAfterSeconds", "layout", "layoutAddress", "pin", "signIn", "version"), saved.keys.sorted())
            assertEquals(JsonPrimitive(1), saved["version"])
            assertEquals(
                listOf("accessToken", "address", "expiresAt", "kind", "refreshToken"),
                saved["signIn"]!!.jsonObject.keys.sorted(),
            )
            assertEquals(JsonPrimitive("account"), saved["signIn"]!!.jsonObject["kind"])
            val card = saved["layout"]!!.jsonObject["cards"]!!.jsonArray[1].jsonObject
            assertEquals(toJson(mapOf("deviceId" to "device:heating", "page" to 0, "column" to 1, "row" to 0, "size" to "wide")), card)
        }
    }

    @Nested
    inner class ReadingBadData {
        @Test
        fun `starts empty when nothing was saved`() {
            assertEquals(EMPTY_SAVED_STATE, parseSavedState(null))
            assertEquals(EMPTY_SAVED_STATE, parseSavedState(""))
        }

        @Test
        fun `starts empty when the text is not JSON`() {
            assertEquals(EMPTY_SAVED_STATE, parseSavedState("""{"version": 1, "signIn""""))
        }

        @Test
        fun `parse gives the empty state for text that is not JSON`() {
            assertEquals(EMPTY_SAVED_STATE, parseSavedState("\u0000\u0001 not json at all"))
        }

        @Test
        fun `starts empty when the text is JSON of the wrong kind`() {
            assertEquals(EMPTY_SAVED_STATE, parseSavedState("42"))
            assertEquals(EMPTY_SAVED_STATE, parseSavedState("null"))
            assertEquals(EMPTY_SAVED_STATE, parseSavedState("[]"))
        }

        @Test
        fun `starts empty when the data is from another version of the app`() {
            assertEquals(EMPTY_SAVED_STATE, parseSavedState(edited(state) { it["version"] = JsonPrimitive(2) }))
            assertEquals(EMPTY_SAVED_STATE, parseSavedState(edited(state) { it["version"] = JsonPrimitive("1") }))
        }

        @Test
        fun `treats a sign-in with a missing or wrongly typed field as signed out, and keeps the layout`() {
            val text = edited(state) {
                it["signIn"] = JsonObject(it["signIn"]!!.jsonObject + ("accessToken" to JsonPrimitive(12345)))
            }
            val parsed = parseSavedState(text)
            assertNull(parsed.signIn)
            assertEquals(layout, parsed.layout)
        }

        @Test
        fun `treats a sign-in of an unknown kind as signed out`() {
            val text = edited(state) {
                it["signIn"] = JsonObject(it["signIn"]!!.jsonObject + ("kind" to JsonPrimitive("password")))
            }
            assertNull(parseSavedState(text).signIn)
        }

        @Test
        fun `drops a card with a bad field and keeps the others`() {
            val bad = listOf(
                mapOf("deviceId" to "device:odd", "page" to 0, "column" to 9, "row" to 0, "size" to "small"),
                mapOf("deviceId" to "device:huge", "page" to 0, "column" to 0, "row" to 1, "size" to "gigantic"),
                mapOf("deviceId" to 42, "page" to 0, "column" to 0, "row" to 2, "size" to "small"),
                mapOf("deviceId" to "device:neg", "page" to -1, "column" to 0, "row" to 2, "size" to "small"),
                mapOf("deviceId" to "device:half", "page" to 0, "column" to 0.5, "row" to 2, "size" to "small"),
                mapOf("deviceId" to "device:tall", "page" to 0, "column" to 0, "row" to 2, "size" to "large"),
                mapOf("deviceId" to "device:text", "page" to "0", "column" to 0, "row" to 2, "size" to "small"),
            )
            val text = edited(state) {
                val cards = it["layout"]!!.jsonObject["cards"]!!.jsonArray + bad.map { card -> toJson(card) }
                it["layout"] = JsonObject(mapOf("cards" to JsonArray(cards)))
            }
            assertEquals(layout, parseSavedState(text).layout)
        }

        @Test
        fun `parse drops cards outside the 4 by 3 grid`() {
            // A layout saved for a wider grid: columns 4 and 5 do not exist on the Echo, and a wide card in
            // column 3 would run off the edge.
            val wider = listOf(
                mapOf("deviceId" to "device:col4", "page" to 0, "column" to 4, "row" to 1, "size" to "small"),
                mapOf("deviceId" to "device:col5", "page" to 0, "column" to 5, "row" to 1, "size" to "small"),
                mapOf("deviceId" to "device:wide3", "page" to 0, "column" to 3, "row" to 2, "size" to "wide"),
            )
            val text = edited(state) {
                val cards = it["layout"]!!.jsonObject["cards"]!!.jsonArray + wider.map { card -> toJson(card) }
                it["layout"] = JsonObject(mapOf("cards" to JsonArray(cards)))
            }
            assertEquals(layout, parseSavedState(text).layout)
        }

        @Test
        fun `drops a card that covers a cell an earlier card covers, or repeats a device`() {
            val clashes = listOf(
                mapOf("deviceId" to "device:clash", "page" to 0, "column" to 1, "row" to 0, "size" to "small"),
                mapOf("deviceId" to "device:lamp", "page" to 1, "column" to 0, "row" to 0, "size" to "small"),
            )
            val text = edited(state) {
                val cards = it["layout"]!!.jsonObject["cards"]!!.jsonArray + clashes.map { card -> toJson(card) }
                it["layout"] = JsonObject(mapOf("cards" to JsonArray(cards)))
            }
            assertEquals(layout, parseSavedState(text).layout)
        }

        @Test
        fun `closes up empty pages, so a huge saved page number cannot stall the start`() {
            val far = listOf(
                mapOf("deviceId" to "device:far", "page" to 50_000_000, "column" to 0, "row" to 0, "size" to "small"),
                mapOf("deviceId" to "device:farthest", "page" to Int.MAX_VALUE, "column" to 0, "row" to 0, "size" to "small"),
            )
            val text = edited(state) {
                val cards = it["layout"]!!.jsonObject["cards"]!!.jsonArray + far.map { card -> toJson(card) }
                it["layout"] = JsonObject(mapOf("cards" to JsonArray(cards)))
            }
            val read = parseSavedState(text).layout
            assertEquals(listOf(0, 0, 1, 2), read.cards.map { it.page })
            assertEquals(3, pageCount(read))
        }

        @Test
        fun `uses an empty layout when the layout is not a list of cards`() {
            val text = edited(state) { it["layout"] = JsonPrimitive("nonsense") }
            assertEquals(EMPTY_LAYOUT, parseSavedState(text).layout)
        }
    }

    @Nested
    inner class AfterSignIn {
        @Test
        fun `keeps the layout when signing in to the same Home Assistant again`() {
            val again = afterSignIn(afterSignOut(state), signIn.copy(accessToken = "new"))
            assertEquals(layout, again.layout)
            assertEquals("new", again.signIn?.accessToken)
        }

        @Test
        fun `clears the layout when signing in to a different Home Assistant`() {
            val next = afterSignIn(afterSignOut(state), signIn.copy(address = "http://10.0.0.5:8123"))
            assertEquals(EMPTY_LAYOUT, next.layout)
            assertEquals("http://10.0.0.5:8123", next.layoutAddress)
        }

        @Test
        fun `starts an empty layout for the first ever sign-in`() {
            assertEquals(SavedState(signIn, EMPTY_LAYOUT, signIn.address, null), afterSignIn(EMPTY_SAVED_STATE, signIn))
        }
    }

    @Test
    fun `afterSignOut removes the tokens and keeps the layout and which Home Assistant it belongs to`() {
        val signedOut = afterSignOut(state)
        assertEquals(SavedState(null, layout, signIn.address, null), signedOut)
        assertFalse(serialiseSavedState(signedOut).contains("refresh-def"))
        assertFalse(serialiseSavedState(signedOut).contains("access-abc"))
    }

    @Nested
    inner class ThePin {
        private val signedIn = SavedState(
            SavedSignIn("http://ha:8123", SignInKind.ACCOUNT, "a", "r", 5),
            EMPTY_LAYOUT,
            "http://ha:8123",
            "0412",
        )

        @Test
        fun `is saved and read back`() {
            assertEquals("0412", parseSavedState(serialiseSavedState(signedIn)).pin)
        }

        @Test
        fun `is off when the saved data has none, as data from before the PIN existed`() {
            val read = parseSavedState(edited(signedIn) { it.remove("pin") })
            assertNull(read.pin)
            assertNotNull(read.signIn)
        }

        @Test
        fun `parse drops a PIN that is not four digits`() {
            for (bad in listOf(JsonPrimitive("12"), JsonPrimitive("abcd"), JsonPrimitive(1234), JsonObject(emptyMap()), JsonPrimitive("12345"))) {
                assertNull(parseSavedState(edited(signedIn) { it["pin"] = bad }).pin, bad.toString())
            }
        }

        @Test
        fun `is removed by signing out, and kept by signing in again to the same home`() {
            assertNull(afterSignOut(signedIn).pin)
            assertEquals("0412", afterSignIn(signedIn.copy(signIn = null), signedIn.signIn!!).pin)
        }
    }

    @Test
    fun `keeps the screen blanking time, and data from before it existed gets 30 seconds`() {
        val state = EMPTY_SAVED_STATE.copy(blankAfterSeconds = 120)
        assertEquals(120, parseSavedState(serialiseSavedState(state)).blankAfterSeconds)
        assertEquals(0, parseSavedState(serialiseSavedState(state.copy(blankAfterSeconds = 0))).blankAfterSeconds)
        val old = """{"version":1,"signIn":null,"layout":{"cards":[]},"layoutAddress":null,"pin":null}"""
        assertEquals(30, parseSavedState(old).blankAfterSeconds)
    }
}
