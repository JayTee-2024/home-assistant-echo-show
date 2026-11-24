/*
 * The real live connection to Home Assistant, over its WebSocket API
 * (https://developers.home-assistant.io/docs/api/websocket).
 *
 * It does what the LG app got from Home Assistant's JavaScript library: signs the socket in, loads the
 * home (user, registries, states), keeps the states current, sends commands, reconnects after a drop and
 * gets a fresh access token when one runs out. It turns all of that into core's small HomeConnection
 * interface, and decides nothing about what the home means.
 *
 * Threading: OkHttp calls the listener on its own threads. Everything that changes this object's state
 * holds `lock`; waiting happens in coroutines on `scope`.
 */
package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import net.badacid.homeassist.echoshow.core.COMMAND_REFUSED
import net.badacid.homeassist.echoshow.core.CommandRefused
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.EMPTY_HOME
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.HttpSend
import net.badacid.homeassist.echoshow.core.OpenFailed
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.core.RefreshResult
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.applyEntityUpdate
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.core.needsRefresh
import net.badacid.homeassist.echoshow.core.readRegistryAreas
import net.badacid.homeassist.echoshow.core.readRegistryDevices
import net.badacid.homeassist.echoshow.core.readRegistryEntities
import net.badacid.homeassist.echoshow.core.reconnectDelayMs
import net.badacid.homeassist.echoshow.core.refreshTokens
import net.badacid.homeassist.echoshow.core.revokeTokens
import net.badacid.homeassist.echoshow.core.textOrNull
import net.badacid.homeassist.echoshow.core.withTimeout as withSendTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** HTTP requests (token refresh and revoke) give up after this long, as the LG app's did. */
const val REQUEST_TIMEOUT_MS = 8_000L

/** How long the socket may take to answer and accept the sign-in. */
private const val SIGN_IN_TIMEOUT_MS = 10_000L

private val REGISTRY_EVENTS = listOf("entity_registry_updated", "device_registry_updated", "area_registry_updated")

/**
 * Opens the connection and waits until the first full picture of the home has arrived. Throws OpenFailed.
 *
 * `onTokens` is called whenever a fresh access token is fetched, with the sign-in to save in place of the old
 * one. `reconnectDelay` is how long to wait before reconnect attempt n (counted from 0).
 */
suspend fun openHomeConnection(
    signIn: SavedSignIn,
    onTokens: (SavedSignIn) -> Unit,
    client: OkHttpClient,
    scope: CoroutineScope,
    clock: () -> Long = System::currentTimeMillis,
    firstLoadTimeoutMs: Long = 20_000,
    registryRefreshDelayMs: Long = 1_000,
    reconnectDelay: (Int) -> Long = ::reconnectDelayMs,
): HomeConnection {
    val connection = HomeAssistantConnection(signIn, onTokens, client, scope, clock, firstLoadTimeoutMs, registryRefreshDelayMs, reconnectDelay)
    connection.open()
    return connection
}

private class HomeAssistantConnection(
    private var signIn: SavedSignIn,
    private val onTokens: (SavedSignIn) -> Unit,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val firstLoadTimeoutMs: Long,
    private val registryRefreshDelayMs: Long,
    private val reconnectDelay: (Int) -> Long,
) : HomeConnection {
    private val lock = Any()
    private val send: HttpSend = withSendTimeout(OkHttpSend(client), REQUEST_TIMEOUT_MS)
    private val homeFlow = MutableStateFlow(EMPTY_HOME)
    private val statusFlow = MutableStateFlow(ConnectionStatus.CONNECTED)

    /** The working session, or null while there is none. */
    private var session: Session? = null
    private var isClosed = false
    private var registryRefresh: Job? = null
    private var reconnecting: Job? = null

    override var userName: String = ""
        private set
    override val home: StateFlow<HomeSnapshot> = homeFlow.asStateFlow()
    override val status: StateFlow<ConnectionStatus> = statusFlow.asStateFlow()

    /** Connects for the first time. On failure nothing is left running. */
    suspend fun open() {
        val (fresh, loaded) = connect()
        synchronized(lock) {
            session = fresh
            userName = loaded.userName
            // States that arrived after the first full set are already in the session's own copy.
            homeFlow.value = loaded.home.copy(states = fresh.states)
        }
        fresh.markWorking()
    }

    override suspend fun call(call: ServiceCall) {
        val current = synchronized(lock) { session.takeIf { statusFlow.value == ConnectionStatus.CONNECTED } } ?: throw CommandRefused()
        try {
            current.request(
                jsonObjectOf(
                    "type" to "call_service",
                    "domain" to call.domain,
                    "service" to call.service,
                    "service_data" to call.data,
                    "target" to mapOf("entity_id" to call.entityId),
                ),
            )
        } catch (refused: SessionError) {
            throw CommandRefused(refused.message?.takeIf { it.isNotEmpty() } ?: COMMAND_REFUSED)
        }
    }

    override suspend fun signOut() {
        val toRevoke = synchronized(lock) { signIn }
        revokeTokens(send, toRevoke)
        close()
    }

    override fun close() {
        val toClose = synchronized(lock) {
            if (isClosed) return
            isClosed = true
            registryRefresh?.cancel()
            reconnecting?.cancel()
            session.also { session = null }
        }
        toClose?.close()
    }

    // ---- Connecting ----

    private class Loaded(val userName: String, val home: HomeSnapshot)

    /** One attempt: fresh tokens if needed, a signed-in socket, and the whole home. Throws OpenFailed. */
    private suspend fun connect(): Pair<Session, Loaded> {
        refreshIfNeeded()
        val url = socketUrl(synchronized(lock) { signIn.address }) ?: throw OpenFailed(OpenFailure.CANNOT_REACH)
        val fresh = Session(url)
        try {
            try {
                withTimeout(SIGN_IN_TIMEOUT_MS) { fresh.signIn(synchronized(lock) { signIn.accessToken }) }
            } catch (_: TimeoutCancellationException) {
                throw OpenFailed(OpenFailure.CANNOT_REACH)
            }
            val loaded = try {
                withTimeout(firstLoadTimeoutMs) { load(fresh) }
            } catch (_: TimeoutCancellationException) {
                throw OpenFailed(OpenFailure.NOT_READY)
            } catch (_: SessionError) {
                throw OpenFailed(OpenFailure.NOT_READY)
            }
            return fresh to loaded
        } catch (failure: Throwable) {
            // The socket may be open but the home could not be loaded. Close it, or it would stay open.
            fresh.close()
            throw failure
        }
    }

    private suspend fun refreshIfNeeded() {
        val current = synchronized(lock) { signIn }
        if (!needsRefresh(current, clock())) return
        when (val result = refreshTokens(send, current, clock())) {
            is RefreshResult.Refreshed -> {
                synchronized(lock) { signIn = result.signIn }
                onTokens(result.signIn)
            }
            RefreshResult.Rejected -> throw OpenFailed(OpenFailure.SIGN_IN_REJECTED)
            RefreshResult.CannotReach -> throw OpenFailed(OpenFailure.CANNOT_REACH)
        }
    }

    /** Fetches the user, the registries and the first states on a signed-in session. */
    private suspend fun load(on: Session): Loaded {
        // Lets Home Assistant send several messages in one, as its own app does. Not essential.
        on.sendOnly(jsonObjectOf("type" to "supported_features", "features" to mapOf("coalesce_messages" to 1)))
        for (eventType in REGISTRY_EVENTS) {
            on.sendOnly(jsonObjectOf("type" to "subscribe_events", "event_type" to eventType), onEvent = { scheduleRegistryRefresh(on) })
        }
        val firstStates = CompletableDeferred<Map<String, EntityState>>()
        // A drop before the first states arrive fails the load at once rather than at the time limit.
        on.whenEnded { firstStates.completeExceptionally(SessionError("")) }
        on.sendOnly(jsonObjectOf("type" to "subscribe_entities"), onEvent = { event ->
            val states = synchronized(lock) {
                on.states = applyEntityUpdate(on.states, event)
                if (session === on) homeFlow.value = homeFlow.value.copy(states = on.states)
                on.states
            }
            firstStates.complete(states)
        })
        return coroutineScopeLoad(on, firstStates)
    }

    private suspend fun coroutineScopeLoad(on: Session, firstStates: CompletableDeferred<Map<String, EntityState>>): Loaded =
        kotlinx.coroutines.coroutineScope {
            val user = async { on.request(jsonObjectOf("type" to "auth/current_user")) }
            val registries = async { readRegistries(on) }
            awaitAll(user, registries)
            val states = firstStates.await()
            val name = textOrNull((user.await() as? JsonObject)?.get("name")) ?: ""
            Loaded(name, registries.await().copy(states = states))
        }

    private suspend fun readRegistries(on: Session): HomeSnapshot = kotlinx.coroutines.coroutineScope {
        val entities = async { on.request(jsonObjectOf("type" to "config/entity_registry/list")) }
        val devices = async { on.request(jsonObjectOf("type" to "config/device_registry/list")) }
        val areas = async { on.request(jsonObjectOf("type" to "config/area_registry/list")) }
        HomeSnapshot(
            states = emptyMap(),
            entities = readRegistryEntities(entities.await()),
            devices = readRegistryDevices(devices.await()),
            areas = readRegistryAreas(areas.await()),
        )
    }

    /** Reads the registries again shortly after Home Assistant reports a change; a burst of changes gives one read. */
    private fun scheduleRegistryRefresh(on: Session) {
        synchronized(lock) {
            if (registryRefresh?.isActive == true || isClosed) return
            registryRefresh = scope.launch {
                delay(registryRefreshDelayMs)
                val fresh = try {
                    readRegistries(on)
                } catch (_: SessionError) {
                    return@launch // the next change tries again
                }
                synchronized(lock) {
                    if (session === on) {
                        homeFlow.value = homeFlow.value.copy(entities = fresh.entities, devices = fresh.devices, areas = fresh.areas)
                    }
                }
            }
        }
    }

    // ---- After a drop ----

    /** Called when a working session ends by itself. */
    private fun onSessionLost(lost: Session) {
        synchronized(lock) {
            if (isClosed || session !== lost) return
            session = null
            statusFlow.value = ConnectionStatus.RECONNECTING
        }
    }

    // ---- One WebSocket ----

    private class SessionError(message: String) : Exception(message)

    /**
     * One WebSocket and its message bookkeeping. Each request gets the next id; its result completes the
     * matching deferred. A subscription keeps its id and gets every event sent with it.
     */
    private inner class Session(url: String) : WebSocketListener() {
        private val socket: WebSocket = client.newWebSocket(Request.Builder().url(url).build(), this)
        private val authRequired = CompletableDeferred<Unit>()
        private val authResult = CompletableDeferred<Boolean>()
        private val pending = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()
        private val subscriptions = mutableMapOf<Int, (JsonObject) -> Unit>()
        private var nextId = 1
        private var isOpen = true
        private var isWorking = false
        private val endActions = mutableListOf<() -> Unit>()

        /** This session's copy of the states, kept current by its entities subscription. Guarded by `lock`. */
        var states: Map<String, EntityState> = emptyMap()

        /** Runs `action` when the socket ends (at once if it already has). */
        fun whenEnded(action: () -> Unit) {
            val now = synchronized(lock) {
                if (isOpen) endActions += action
                !isOpen
            }
            if (now) action()
        }

        suspend fun signIn(accessToken: String) {
            try {
                authRequired.await()
                socket.send(jsonObjectOf("type" to "auth", "access_token" to accessToken).toString())
                if (!authResult.await()) throw OpenFailed(OpenFailure.SIGN_IN_REJECTED)
            } catch (_: SessionError) {
                throw OpenFailed(OpenFailure.CANNOT_REACH)
            }
        }

        /** From now on, the socket ending is a drop to recover from rather than a failed open. */
        fun markWorking() {
            val ended = synchronized(lock) {
                isWorking = true
                !isOpen
            }
            if (ended) onSessionLost(this)
        }

        /** Sends a request and waits for its result. Throws SessionError for an error result or a closed socket. */
        suspend fun request(message: JsonObject): JsonElement? {
            val result = CompletableDeferred<JsonElement?>()
            send(message, result, null)
            return result.await()
        }

        /** Sends a request whose result does not matter. `onEvent` gets the events of a subscription. */
        fun sendOnly(message: JsonObject, onEvent: ((JsonObject) -> Unit)? = null) {
            send(message, CompletableDeferred(), onEvent)
        }

        private fun send(message: JsonObject, result: CompletableDeferred<JsonElement?>, onEvent: ((JsonObject) -> Unit)?) {
            val id = synchronized(lock) {
                if (!isOpen) {
                    result.completeExceptionally(SessionError(""))
                    return
                }
                val id = nextId++
                pending[id] = result
                if (onEvent != null) subscriptions[id] = onEvent
                id
            }
            socket.send(JsonObject(message + ("id" to JsonPrimitive(id))).toString())
        }

        fun close() {
            socket.close(1000, null)
            ended()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val parsed = try {
                Json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                return
            }
            // With coalesce_messages, Home Assistant may send several messages as one list.
            val messages = (parsed as? JsonArray)?.filterIsInstance<JsonObject>() ?: listOfNotNull(parsed as? JsonObject)
            messages.forEach(::handle)
        }

        private fun handle(message: JsonObject) {
            val id = (message["id"] as? JsonPrimitive)?.intOrNull
            when (textOrNull(message["type"])) {
                "auth_required" -> authRequired.complete(Unit)
                "auth_ok" -> authResult.complete(true)
                "auth_invalid" -> authResult.complete(false)
                "result" -> {
                    val waiting = synchronized(lock) { id?.let { pending.remove(it) } } ?: return
                    if (message["success"] == JsonPrimitive(true)) {
                        waiting.complete(message["result"])
                    } else {
                        val error = message["error"] as? JsonObject
                        waiting.completeExceptionally(SessionError(textOrNull(error?.get("message")) ?: ""))
                    }
                }
                "event" -> {
                    val listener = synchronized(lock) { id?.let { subscriptions[it] } } ?: return
                    (message["event"] as? JsonObject)?.let(listener)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            ended()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = ended()

        /** The socket is gone: everything waiting on it fails, and a working session reports the drop. */
        private fun ended() {
            val (waiting, wasWorking) = synchronized(lock) {
                if (!isOpen) return
                isOpen = false
                val all = pending.values.toList()
                pending.clear()
                subscriptions.clear()
                all to isWorking
            }
            endActions.toList().forEach { it() }
            val gone = SessionError("")
            authRequired.completeExceptionally(gone)
            authResult.completeExceptionally(gone)
            waiting.forEach { it.completeExceptionally(gone) }
            if (wasWorking) onSessionLost(this)
        }
    }

    private fun socketUrl(address: String): String? {
        val http = "$address/api/websocket".toHttpUrlOrNull() ?: return null
        return http.toString().replaceFirst("http", "ws")
    }
}
