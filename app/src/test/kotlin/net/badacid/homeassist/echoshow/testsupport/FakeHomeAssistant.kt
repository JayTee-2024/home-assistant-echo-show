package net.badacid.homeassist.echoshow.testsupport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.SignInKind
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.net.URLDecoder

/**
 * The stand-in Home Assistant (FakeHome) on a real local port, for tests that go through the network:
 * HTTP for signing in, and a WebSocket at /api/websocket.
 */
class FakeHomeAssistant : AutoCloseable {
    val home = FakeHome.fromFixtures()
    private val server = MockWebServer()
    private val serverSockets = mutableListOf<WebSocket>()
    @Volatile private var isDown = false

    /** When each WebSocket was asked for, in milliseconds, in order. */
    val socketAttempts = mutableListOf<Long>()

    /** Refuses new WebSockets (as a restarting Home Assistant does) and closes the open ones. */
    fun goDown() {
        isDown = true
        dropConnections()
    }

    /** Accepts WebSockets again. */
    fun comeBack() {
        isDown = false
    }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: "/"
                if (path == "/api/websocket") {
                    synchronized(socketAttempts) { socketAttempts += System.currentTimeMillis() }
                    if (isDown) return MockResponse().setResponseCode(503).setBody("Home Assistant is starting")
                    return MockResponse().withWebSocketUpgrade(socketListener())
                }
                val answer = home.handleHttp(request.method ?: "GET", path, readBody(request))
                val response = MockResponse().setResponseCode(answer.status).setHeader("Content-Type", answer.contentType)
                return if (answer.bytes != null) response.setBody(Buffer().write(answer.bytes)) else response.setBody(answer.json.toString())
            }
        }
        server.start()
    }

    /** For example http://127.0.0.1:41234, with no trailing slash. */
    val address: String = server.url("/").toString().removeSuffix("/")

    /** A finished password sign-in to this stand-in, with tokens it accepts. `expiresAt` is when the access token runs out. */
    fun signInTokens(expiresAt: Long = System.currentTimeMillis() + 1_800_000): SavedSignIn {
        val (access, refresh) = home.issueTokens()
        return SavedSignIn(address, SignInKind.ACCOUNT, access, refresh, expiresAt)
    }

    fun setState(entityId: String, state: String, attributes: Map<String, Any?> = emptyMap()) = home.setState(entityId, state, attributes)

    /**
     * Closes every open WebSocket from the server's side, as a Home Assistant restart would. (MockWebServer's
     * sockets cannot be cut abruptly; the app treats a close and a failure the same way.)
     */
    fun dropConnections() {
        val sockets = synchronized(serverSockets) { serverSockets.toList().also { serverSockets.clear() } }
        home.closeAllSockets()
        sockets.forEach { it.close(1001, "Home Assistant is restarting") }
    }

    val openSocketCount: Int get() = home.openSocketCount
    val revokedTokens: List<String> get() = home.revokedTokens
    val calls: List<JsonObject> get() = home.calls

    fun expireAccessTokens() = home.expireAccessTokens()
    fun rejectRefreshTokens() = home.rejectRefreshTokens()
    fun breakCommand(type: String) = home.breakCommand(type)
    fun silenceCommand(type: String) = home.silenceCommand(type)

    override fun close() {
        dropConnections()
        server.shutdown()
    }

    private fun socketListener() = object : WebSocketListener() {
        private var session: FakeSocket? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(serverSockets) { serverSockets += webSocket }
            // openSocket sends auth_required at once, and the app may answer before openSocket returns.
            // Holding the home's lock until `session` is set makes that answer wait for it.
            synchronized(home) {
                session = home.openSocket({ webSocket.send(it.toString()) }, onClose = { webSocket.close(1000, null) })
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return
            synchronized(home) { session }?.receive(message)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(serverSockets) { serverSockets -= webSocket }
            session?.close()
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            session?.close()
        }
    }

    private fun readBody(request: RecordedRequest): Map<String, JsonElement> {
        val text = request.body.readUtf8()
        if (text.isBlank()) return emptyMap()
        if (request.getHeader("Content-Type")?.startsWith("application/x-www-form-urlencoded") == true) {
            return text.split('&').filter { '=' in it }.associate { field ->
                val (name, value) = field.split('=', limit = 2)
                URLDecoder.decode(name, "UTF-8") to JsonPrimitive(URLDecoder.decode(value, "UTF-8"))
            }
        }
        return try {
            Json.parseToJsonElement(text).jsonObject
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }
}
