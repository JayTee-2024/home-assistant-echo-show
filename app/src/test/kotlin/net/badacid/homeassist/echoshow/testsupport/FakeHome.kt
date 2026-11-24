package net.badacid.homeassist.echoshow.testsupport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.core.jsonValue
import net.badacid.homeassist.echoshow.core.textOrNull
import kotlin.math.roundToInt

/*
 * A stand-in Home Assistant for tests: a port of the LG app's scripts/lib/fake-home.mjs.
 *
 * It speaks enough of Home Assistant's sign-in and WebSocket language for the app to sign in, list
 * devices, watch states and switch things on and off, all from the made-up home in
 * fixtures/sample-home.json, without a real Home Assistant and without anyone's password.
 *
 * This class has no network code: it takes requests and messages and returns or sends answers.
 * FakeHomeAssistant.kt puts it on a real port. Anything it does not know answers with an error.
 * The real message shapes: https://developers.home-assistant.io/docs/api/websocket
 */

const val FAKE_USERNAME = "echo"
const val FAKE_PASSWORD = "echo"
private const val HA_VERSION = "2026.11.2"
private const val ACCESS_TOKEN_SECONDS = 1800

/** An HTTP answer from the stand-in. `bytes` is set for a picture, `json` otherwise. */
data class FakeAnswer(val status: Int, val json: JsonElement? = null, val bytes: ByteArray? = null, val contentType: String = "application/json")

/** One WebSocket session: `receive` takes a message from the app; `close` ends it. */
interface FakeSocket {
    fun receive(message: JsonObject)
    fun close()
}

class FakeHome(sample: JsonObject, private val cameraPicture: ByteArray) {
    private val states: MutableList<MutableMap<String, JsonElement>> =
        (sample["states"] as JsonArray).map { it.jsonObject.toMutableMap() }.toMutableList()
    private val entities = sample["entities"] as JsonArray
    private val devices = sample["devices"] as JsonArray
    private val areas = sample["areas"] as JsonArray
    private val user = sample["user"] as JsonObject

    private val flows = mutableSetOf<String>()
    private val codes = mutableSetOf<String>()
    private val accessTokens = mutableSetOf<String>()
    private val refreshTokens = mutableSetOf<String>()
    private val subscribers = mutableListOf<Pair<(JsonObject) -> Unit, Int>>()
    private val sockets = mutableListOf<FakeSocket>()
    private val brokenCommands = mutableSetOf<String>()
    private val silentCommands = mutableSetOf<String>()
    private var counter = 0
    private var refusingRefresh = false

    /** Every refresh token the app revoked, in order. */
    val revokedTokens = mutableListOf<String>()

    /** Every call_service message received, in order. */
    val calls = mutableListOf<JsonObject>()

    val openSocketCount: Int @Synchronized get() = sockets.size

    private fun newId(prefix: String) = "$prefix-${++counter}"

    private fun findState(entityId: String?) = states.firstOrNull { textOrNull(it["entity_id"]) == entityId }

    // ---- For tests ----

    /** Issues tokens directly, as a finished sign-in would. */
    @Synchronized
    fun issueTokens(): Pair<String, String> {
        val access = newId("access")
        val refresh = newId("refresh")
        accessTokens += access
        refreshTokens += refresh
        return access to refresh
    }

    /** Every access token given out so far stops working, as when they run out. */
    @Synchronized
    fun expireAccessTokens() = accessTokens.clear()

    /** From now on, refresh tokens are refused, as when the sign-in was removed in Home Assistant. */
    @Synchronized
    fun rejectRefreshTokens() {
        refusingRefresh = true
    }

    @Synchronized
    fun breakCommand(type: String) {
        brokenCommands += type
    }

    @Synchronized
    fun silenceCommand(type: String) {
        silentCommands += type
    }

    /** Ends every open WebSocket session. */
    @Synchronized
    fun closeAllSockets(): List<FakeSocket> = sockets.toList().also { list -> list.forEach { it.close() } }

    fun stateOf(entityId: String): JsonObject? = synchronized(this) { findState(entityId)?.let { JsonObject(it) } }

    // ---- HTTP: signing in ----

    @Synchronized
    fun handleHttp(method: String, path: String, body: Map<String, JsonElement>): FakeAnswer {
        if (method == "GET" && path.startsWith("/api/camera_proxy/")) {
            val entityId = path.removePrefix("/api/camera_proxy/").substringBefore('?')
            if (findState(entityId) == null) return FakeAnswer(404, jsonObjectOf("message" to "Not found"))
            return FakeAnswer(200, bytes = cameraPicture, contentType = "image/png")
        }
        if (method == "POST" && path == "/auth/login_flow") {
            val flowId = newId("flow")
            flows += flowId
            return FakeAnswer(200, credentialsForm(flowId, emptyMap()))
        }
        if (method == "POST" && path.startsWith("/auth/login_flow/")) {
            val flowId = path.removePrefix("/auth/login_flow/")
            if (flowId !in flows) return FakeAnswer(404, jsonObjectOf("message" to "Invalid flow specified"))
            if (textOrNull(body["username"]) != FAKE_USERNAME || textOrNull(body["password"]) != FAKE_PASSWORD) {
                return FakeAnswer(200, credentialsForm(flowId, mapOf("base" to "invalid_auth")))
            }
            flows -= flowId
            val code = newId("code")
            codes += code
            return FakeAnswer(200, jsonObjectOf("type" to "create_entry", "flow_id" to flowId, "result" to code, "version" to 1))
        }
        if (method == "POST" && path == "/auth/token") {
            val grant = textOrNull(body["grant_type"])
            val code = textOrNull(body["code"])
            if (grant == "authorization_code" && code != null && code in codes) {
                codes -= code
                val refresh = newId("refresh")
                refreshTokens += refresh
                return FakeAnswer(200, JsonObject(newAccessToken() + ("refresh_token" to JsonPrimitive(refresh))))
            }
            val refresh = textOrNull(body["refresh_token"])
            if (grant == "refresh_token" && refresh in refreshTokens && !refusingRefresh) {
                return FakeAnswer(200, JsonObject(newAccessToken()))
            }
            if (grant == "refresh_token") {
                return FakeAnswer(400, jsonObjectOf("error" to "invalid_grant", "error_description" to "Invalid refresh token"))
            }
            return FakeAnswer(400, jsonObjectOf("error" to "invalid_request", "error_description" to "Invalid code"))
        }
        if (method == "POST" && path == "/auth/revoke") {
            textOrNull(body["token"])?.let {
                refreshTokens -= it
                revokedTokens += it
            }
            return FakeAnswer(200, JsonObject(emptyMap()))
        }
        return FakeAnswer(404, jsonObjectOf("message" to "Not found"))
    }

    private fun credentialsForm(flowId: String, errors: Map<String, String>) = jsonObjectOf(
        "type" to "form",
        "flow_id" to flowId,
        "handler" to listOf("homeassistant", null),
        "step_id" to "init",
        "data_schema" to listOf(mapOf("name" to "username", "type" to "string"), mapOf("name" to "password", "type" to "string")),
        "errors" to errors,
    )

    private fun newAccessToken(): Map<String, JsonElement> {
        val access = newId("access")
        accessTokens += access
        return mapOf(
            "access_token" to JsonPrimitive(access),
            "token_type" to JsonPrimitive("Bearer"),
            "expires_in" to JsonPrimitive(ACCESS_TOKEN_SECONDS),
        )
    }

    // ---- WebSocket: live data ----

    @Synchronized
    fun openSocket(sendToClient: (JsonObject) -> Unit, onClose: () -> Unit = {}): FakeSocket {
        val socket = object : FakeSocket {
            var signedIn = false
            var isOpen = true

            override fun receive(message: JsonObject) = synchronized(this@FakeHome) { handle(message) }

            override fun close() {
                synchronized(this@FakeHome) {
                    if (!isOpen) return
                    isOpen = false
                    sockets.remove(this)
                    subscribers.removeAll { it.first === sendToClient }
                }
                onClose()
            }

            private fun result(id: Int, value: Any?) =
                sendToClient(jsonObjectOf("id" to id, "type" to "result", "success" to true, "result" to value))

            private fun failure(id: Int, code: String, text: String) = sendToClient(
                jsonObjectOf("id" to id, "type" to "result", "success" to false, "error" to mapOf("code" to code, "message" to text)),
            )

            private fun handle(message: JsonObject) {
                if (!isOpen) return
                val type = textOrNull(message["type"])
                if (!signedIn) {
                    if (type == "auth" && textOrNull(message["access_token"]) in accessTokens) {
                        signedIn = true
                        sendToClient(jsonObjectOf("type" to "auth_ok", "ha_version" to HA_VERSION))
                    } else {
                        sendToClient(jsonObjectOf("type" to "auth_invalid", "message" to "Invalid access token or password"))
                    }
                    return
                }
                val id = (message["id"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: return
                if (type in silentCommands) return
                if (type in brokenCommands) return failure(id, "home_assistant_error", "Home Assistant is not ready yet.")

                when (type) {
                    "ping" -> sendToClient(jsonObjectOf("id" to id, "type" to "pong"))
                    "supported_features", "subscribe_events", "unsubscribe_events" -> result(id, null)
                    "subscribe_entities" -> {
                        subscribers += sendToClient to id
                        result(id, null)
                        sendToClient(jsonObjectOf("id" to id, "type" to "event", "event" to mapOf("a" to allStatesCompressed())))
                    }
                    "get_states" -> result(id, JsonArray(states.map { JsonObject(it) }))
                    "config/entity_registry/list" -> result(id, entities)
                    "config/device_registry/list" -> result(id, devices)
                    "config/area_registry/list" -> result(id, areas)
                    "auth/current_user" -> result(id, user)
                    "call_service" -> {
                        calls += message
                        val problem = callService(message)
                        if (problem == null) {
                            result(id, mapOf("context" to mapOf("id" to newId("context")), "response" to null))
                        } else {
                            failure(id, "home_assistant_error", problem)
                        }
                    }
                    else -> failure(id, "unknown_command", "Unknown command.")
                }
            }
        }
        sockets += socket
        sendToClient(jsonObjectOf("type" to "auth_required", "ha_version" to HA_VERSION))
        return socket
    }

    private fun allStatesCompressed(): JsonObject = JsonObject(
        states.associate { state ->
            textOrNull(state["entity_id"])!! to jsonObjectOf(
                "s" to state["state"],
                "a" to state["attributes"],
                "c" to "sample-context",
                "lc" to System.currentTimeMillis() / 1000.0,
            )
        },
    )

    /** Changes one entity and tells every subscribed socket, as Home Assistant would. */
    @Synchronized
    fun setState(entityId: String, newState: String, newAttributes: Map<String, Any?> = emptyMap()) {
        val state = findState(entityId) ?: return
        val attributes = jsonValue(newAttributes) as JsonObject
        state["state"] = JsonPrimitive(newState)
        state["attributes"] = JsonObject((state["attributes"] as JsonObject) + attributes)
        val change = jsonObjectOf(entityId to mapOf("+" to mapOf("s" to newState, "a" to attributes, "lc" to System.currentTimeMillis() / 1000.0)))
        for ((send, subscriptionId) in subscribers.toList()) {
            send(jsonObjectOf("id" to subscriptionId, "type" to "event", "event" to mapOf("c" to change)))
        }
    }

    /** Carries out a service call. Returns null when it worked, or the reason it did not. */
    private fun callService(message: JsonObject): String? {
        val target = message["target"] as? JsonObject ?: JsonObject(emptyMap())
        val data = message["service_data"] as? JsonObject ?: JsonObject(emptyMap())
        val entityId = textOrNull(target["entity_id"]) ?: textOrNull(data["entity_id"])
        val state = findState(entityId) ?: return "Entity $entityId was not found."
        val id = entityId!!
        val current = textOrNull(state["state"])
        val domain = textOrNull(message["domain"])
        val service = textOrNull(message["service"])
        val key = "$domain.$service"
        fun number(name: String) = (data[name] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

        if (domain == "light" || domain == "switch" || domain == "input_boolean") {
            if (service != "turn_on" && service != "turn_off" && service != "toggle") {
                return "The stand-in home does not know the service $key."
            }
            val turnOn = service == "turn_on" || (service == "toggle" && current != "on")
            setState(id, if (turnOn) "on" else "off", if (domain == "light") lightAttributes(state, data, turnOn) else emptyMap())
            return null
        }
        when (key) {
            "climate.set_temperature" -> setState(id, current!!, mapOf("temperature" to data["temperature"]))
            "climate.set_hvac_mode" -> {
                val mode = textOrNull(data["hvac_mode"]) ?: "off"
                setState(id, mode, mapOf("hvac_action" to if (mode == "off") "off" else "heating"))
            }
            "climate.set_preset_mode" -> setState(id, current!!, mapOf("preset_mode" to data["preset_mode"]))
            "media_player.media_play_pause" -> setState(id, if (current == "playing") "paused" else "playing")
            "media_player.volume_set" -> setState(id, current!!, mapOf("volume_level" to data["volume_level"]))
            "media_player.volume_mute" -> setState(id, current!!, mapOf("is_volume_muted" to data["is_volume_muted"]))
            "media_player.select_source" -> setState(id, current!!, mapOf("source" to data["source"]))
            "media_player.media_next_track", "media_player.media_previous_track" -> Unit
            "cover.open_cover" -> setState(id, "open", mapOf("current_position" to 100))
            "cover.close_cover" -> setState(id, "closed", mapOf("current_position" to 0))
            "cover.stop_cover" -> Unit
            "cover.set_cover_position" -> {
                val position = number("position") ?: 0.0
                setState(id, if (position > 0) "open" else "closed", mapOf("current_position" to data["position"]))
            }
            "fan.toggle", "fan.turn_on", "fan.turn_off" -> {
                val turnOn = service == "turn_on" || (service == "toggle" && current != "on")
                setState(id, if (turnOn) "on" else "off")
            }
            "fan.set_percentage" -> setState(id, if ((number("percentage") ?: 0.0) > 0) "on" else "off", mapOf("percentage" to data["percentage"]))
            "fan.oscillate" -> setState(id, current!!, mapOf("oscillating" to data["oscillating"]))
            "fan.set_preset_mode" -> setState(id, "on", mapOf("preset_mode" to data["preset_mode"]))
            "vacuum.start" -> setState(id, "cleaning")
            "vacuum.pause" -> setState(id, "paused")
            "vacuum.stop" -> setState(id, "idle")
            "vacuum.return_to_base" -> setState(id, "returning")
            "vacuum.locate", "script.turn_on" -> Unit
            "button.press", "input_button.press", "scene.turn_on" -> setState(id, java.time.Instant.now().toString())
            else -> return "The stand-in home does not know the service $key."
        }
        return null
    }

    /** What a light reports after a call: the brightness and colour asked for, or cleared when off. */
    private fun lightAttributes(state: Map<String, JsonElement>, data: JsonObject, turnOn: Boolean): Map<String, Any?> {
        if (!turnOn) return mapOf("brightness" to null)
        val attributes = mutableMapOf<String, Any?>()
        val percent = (data["brightness_pct"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        val current = ((state["attributes"] as JsonObject)["brightness"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (percent != null) {
            attributes["brightness"] = (percent / 100 * 255).roundToInt()
        } else if (current == null) {
            attributes["brightness"] = 255
        }
        (data["color_temp_kelvin"] as? JsonPrimitive)?.takeIf { !it.isString }?.let {
            attributes["color_temp_kelvin"] = it
            attributes["color_mode"] = "color_temp"
            attributes["rgb_color"] = null
        }
        (data["rgb_color"] as? JsonArray)?.let {
            attributes["rgb_color"] = it
            attributes["color_mode"] = "hs"
            attributes["color_temp_kelvin"] = null
        }
        textOrNull(data["effect"])?.let { attributes["effect"] = it }
        return attributes
    }

    companion object {
        /** The stand-in with the sample home and picture from fixtures/. */
        fun fromFixtures(): FakeHome {
            val text = FakeHome::class.java.getResource("/sample-home.json")!!.readText()
            val picture = FakeHome::class.java.getResource("/camera.png")!!.readBytes()
            return FakeHome(Json.parseToJsonElement(text).jsonObject, picture)
        }
    }
}
