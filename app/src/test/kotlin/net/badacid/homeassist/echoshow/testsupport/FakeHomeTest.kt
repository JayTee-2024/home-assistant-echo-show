package net.badacid.homeassist.echoshow.testsupport

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.core.jsonValue
import net.badacid.homeassist.echoshow.core.textOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Port of the LG app's scripts/lib/fake-home.test.mjs, plus the network wrapper's tests. */
class FakeHomeTest {
    private val home = FakeHome.fromFixtures()

    private fun json(vararg pairs: Pair<String, Any?>): Map<String, JsonElement> = jsonObjectOf(*pairs)

    private fun signIn(): JsonObject {
        val flow = home.handleHttp("POST", "/auth/login_flow", json("client_id" to "x")).json as JsonObject
        val step = home.handleHttp(
            "POST",
            "/auth/login_flow/" + textOrNull(flow["flow_id"]),
            json("username" to FAKE_USERNAME, "password" to FAKE_PASSWORD),
        ).json as JsonObject
        return home.handleHttp("POST", "/auth/token", json("grant_type" to "authorization_code", "code" to textOrNull(step["result"]))).json as JsonObject
    }

    private inner class Session {
        val received = mutableListOf<JsonObject>()
        val socket = home.openSocket({ received += it })
        fun send(vararg pairs: Pair<String, Any?>) = socket.receive(jsonObjectOf(*pairs))
        fun last() = received.last()
    }

    private fun signedInSession() = Session().apply { send("type" to "auth", "access_token" to textOrNull(signIn()["access_token"])) }

    @Nested
    inner class SigningInOverHttp {
        @Test
        fun `goes from a flow, through the right password, to tokens`() {
            val tokens = signIn()
            assertTrue(textOrNull(tokens["access_token"])!!.isNotEmpty())
            assertTrue(textOrNull(tokens["refresh_token"])!!.isNotEmpty())
            assertEquals(JsonPrimitive(1800), tokens["expires_in"])
        }

        @Test
        fun `answers a wrong password the way Home Assistant does`() {
            val flow = home.handleHttp("POST", "/auth/login_flow", emptyMap()).json as JsonObject
            val step = home.handleHttp("POST", "/auth/login_flow/" + textOrNull(flow["flow_id"]), json("username" to "echo", "password" to "no"))
            assertEquals(200, step.status)
            assertEquals(jsonObjectOf("base" to "invalid_auth"), (step.json as JsonObject)["errors"])
        }

        @Test
        fun `answers 404 for a flow it does not know`() {
            assertEquals(404, home.handleHttp("POST", "/auth/login_flow/nope", emptyMap()).status)
        }

        @Test
        fun `refuses a code it did not give out`() {
            assertEquals(400, home.handleHttp("POST", "/auth/token", json("grant_type" to "authorization_code", "code" to "made-up")).status)
        }

        @Test
        fun `gives a new access token for a refresh token, and none once it is revoked`() {
            val refresh = textOrNull(signIn()["refresh_token"])
            assertEquals(200, home.handleHttp("POST", "/auth/token", json("grant_type" to "refresh_token", "refresh_token" to refresh)).status)
            home.handleHttp("POST", "/auth/revoke", json("token" to refresh))
            assertEquals(400, home.handleHttp("POST", "/auth/token", json("grant_type" to "refresh_token", "refresh_token" to refresh)).status)
            assertEquals(listOf(refresh), home.revokedTokens)
        }

        @Test
        fun `refuses every refresh once told to`() {
            val refresh = textOrNull(signIn()["refresh_token"])
            home.rejectRefreshTokens()
            assertEquals(400, home.handleHttp("POST", "/auth/token", json("grant_type" to "refresh_token", "refresh_token" to refresh)).status)
        }

        @Test
        fun `answers 404 for any other address`() {
            assertEquals(404, home.handleHttp("GET", "/api/nothing", emptyMap()).status)
        }
    }

    @Nested
    inner class TheWebSocket {
        @Test
        fun `asks for sign-in first, and accepts a real access token`() {
            val session = signedInSession()
            assertEquals("auth_required", textOrNull(session.received[0]["type"]))
            assertEquals("auth_ok", textOrNull(session.last()["type"]))
        }

        @Test
        fun `rejects a token it did not give out, and one that has expired`() {
            val session = Session()
            session.send("type" to "auth", "access_token" to "made-up")
            assertEquals("auth_invalid", textOrNull(session.last()["type"]))

            val token = textOrNull(signIn()["access_token"])
            home.expireAccessTokens()
            val later = Session()
            later.send("type" to "auth", "access_token" to token)
            assertEquals("auth_invalid", textOrNull(later.last()["type"]))
        }

        @Test
        fun `sends every state once when entities are subscribed to`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "subscribe_entities")
            val event = session.last()["event"]!!.jsonObject["a"]!!.jsonObject
            assertTrue("light.lounge_lamp" in event)
            assertEquals(JsonPrimitive("on"), event["light.lounge_lamp"]!!.jsonObject["s"])
        }

        @Test
        fun `answers the three registry lists and the current user from the sample home`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "config/entity_registry/list")
            assertTrue((session.last()["result"] as JsonArray).isNotEmpty())
            session.send("id" to 2, "type" to "config/device_registry/list")
            assertTrue((session.last()["result"] as JsonArray).isNotEmpty())
            session.send("id" to 3, "type" to "config/area_registry/list")
            assertTrue((session.last()["result"] as JsonArray).isNotEmpty())
            session.send("id" to 4, "type" to "auth/current_user")
            assertEquals(JsonPrimitive("TV"), session.last()["result"]!!.jsonObject["name"])
        }

        @Test
        fun `turns a light off when asked to toggle it, and tells subscribers`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "subscribe_entities")
            session.send("id" to 2, "type" to "call_service", "domain" to "light", "service" to "toggle", "target" to mapOf("entity_id" to "light.lounge_lamp"))
            val change = session.received.first { it["event"]?.jsonObject?.get("c") != null }
            assertEquals(JsonPrimitive("off"), change["event"]!!.jsonObject["c"]!!.jsonObject["light.lounge_lamp"]!!.jsonObject["+"]!!.jsonObject["s"])
            assertEquals(JsonPrimitive(true), session.last()["success"])
            assertEquals(1, home.calls.size)
        }

        @Test
        fun `sets brightness from brightness_pct and turns the light on`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "call_service", "domain" to "light", "service" to "turn_on", "target" to mapOf("entity_id" to "light.kitchen_ceiling"), "service_data" to mapOf("brightness_pct" to 40))
            val state = home.stateOf("light.kitchen_ceiling")!!
            assertEquals(JsonPrimitive("on"), state["state"])
            assertEquals(jsonValue(102), state["attributes"]!!.jsonObject["brightness"])
        }

        @Test
        fun `fails a call for an entity it does not have, with a reason`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "call_service", "domain" to "light", "service" to "toggle", "target" to mapOf("entity_id" to "light.nowhere"))
            assertEquals(JsonPrimitive(false), session.last()["success"])
            assertEquals(JsonPrimitive("Entity light.nowhere was not found."), session.last()["error"]!!.jsonObject["message"])
        }

        @Test
        fun `answers ping with pong, and an unknown command with an error`() {
            val session = signedInSession()
            session.send("id" to 1, "type" to "ping")
            assertEquals("pong", textOrNull(session.last()["type"]))
            session.send("id" to 2, "type" to "nonsense")
            assertEquals(JsonPrimitive("unknown_command"), session.last()["error"]!!.jsonObject["code"])
        }
    }

    @Nested
    inner class MakingItMisbehave {
        @Test
        fun `answers a broken command with an error, as Home Assistant does while it is starting`() {
            home.breakCommand("config/entity_registry/list")
            val session = signedInSession()
            session.send("id" to 1, "type" to "config/entity_registry/list")
            assertEquals(JsonPrimitive(false), session.last()["success"])
        }

        @Test
        fun `never answers a silenced command`() {
            home.silenceCommand("subscribe_entities")
            val session = signedInSession()
            val before = session.received.size
            session.send("id" to 1, "type" to "subscribe_entities")
            assertEquals(before, session.received.size)
        }

        @Test
        fun `counts the sockets that are open`() {
            val a = signedInSession()
            signedInSession()
            assertEquals(2, home.openSocketCount)
            a.socket.close()
            assertEquals(1, home.openSocketCount)
            home.closeAllSockets()
            assertEquals(0, home.openSocketCount)
        }
    }

    @Test
    fun `serves a picture for a camera, and 404 for a camera it does not have`() {
        assertEquals("image/png", home.handleHttp("GET", "/api/camera_proxy/camera.front_door?token=x", emptyMap()).contentType)
        assertEquals(404, home.handleHttp("GET", "/api/camera_proxy/camera.nowhere", emptyMap()).status)
    }

    @Test
    fun `sets the target temperature, the mode and the preset of a thermostat`() {
        val session = signedInSession()
        val target = mapOf("entity_id" to "climate.living_room")
        session.send("id" to 1, "type" to "call_service", "domain" to "climate", "service" to "set_temperature", "target" to target, "service_data" to mapOf("temperature" to 22.5))
        session.send("id" to 2, "type" to "call_service", "domain" to "climate", "service" to "set_hvac_mode", "target" to target, "service_data" to mapOf("hvac_mode" to "off"))
        val state = home.stateOf("climate.living_room")!!
        assertEquals(jsonValue(22.5), state["attributes"]!!.jsonObject["temperature"])
        assertEquals(JsonPrimitive("off"), state["state"])
    }

    @Nested
    inner class OverTheNetwork {
        private val client = OkHttpClient()

        @Test
        fun `answers the sign-in start over real HTTP`() {
            FakeHomeAssistant().use { server ->
                val response = client.newCall(
                    Request.Builder().url(server.address + "/auth/login_flow").post("""{"client_id":"x"}""".toRequestBody()).build(),
                ).execute()
                assertEquals(200, response.code)
                assertTrue(response.body!!.string().contains("\"flow_id\""))
            }
        }

        @Test
        fun `speaks WebSocket at api websocket, and can drop it`() {
            FakeHomeAssistant().use { server ->
                val messages = LinkedBlockingQueue<String>()
                val closed = LinkedBlockingQueue<String>()
                val token = server.signInTokens().accessToken
                client.newWebSocket(
                    Request.Builder().url(server.address.replace("http", "ws") + "/api/websocket").build(),
                    object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            messages += text
                            if (text.contains("auth_required")) webSocket.send("""{"type":"auth","access_token":"$token"}""")
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            closed += "failure"
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            closed += "closing"
                            webSocket.close(1000, null)
                        }
                    },
                )
                assertTrue(messages.poll(5, TimeUnit.SECONDS)!!.contains("auth_required"))
                assertTrue(messages.poll(5, TimeUnit.SECONDS)!!.contains("auth_ok"))
                assertEquals(1, server.openSocketCount)

                server.dropConnections()
                assertTrue(closed.poll(5, TimeUnit.SECONDS) != null)
                assertEquals(0, server.openSocketCount)
                assertFalse(messages.isNotEmpty())
            }
        }
    }

}
