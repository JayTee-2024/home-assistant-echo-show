package net.badacid.homeassist.echoshow.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** One Home Assistant service call, such as turning a light on. */
data class ServiceCall(
    /** The entity type that owns the service: 'light'. */
    val domain: String,
    /** The service's name: 'turn_on'. */
    val service: String,
    val entityId: String,
    /** The service's settings, in Home Assistant's own names: { brightness_pct: 50 }. */
    val data: JsonObject,
)

enum class ConnectionStatus {
    /** Talking to Home Assistant. */
    CONNECTED,

    /** The link dropped. The connection keeps trying by itself. */
    RECONNECTING,

    /** Home Assistant no longer accepts the saved sign-in. The person must sign in again. */
    SIGN_IN_REJECTED,
}

/**
 * The live connection to Home Assistant, as the rest of the app sees it. The real one is
 * platform/HomeAssistantConnection.kt; screens depend only on this, so they can be tested with a stand-in.
 */
interface HomeConnection {
    /** The signed-in Home Assistant user's name. */
    val userName: String

    /** The home as it is now; changes as Home Assistant reports changes. */
    val home: StateFlow<HomeSnapshot>

    val status: StateFlow<ConnectionStatus>

    /** Returns when Home Assistant has accepted the call. Throws CommandRefused with its reason. */
    suspend fun call(call: ServiceCall)

    /** Asks Home Assistant to cancel this Echo's sign-in, then closes. Never throws. */
    suspend fun signOut()

    fun close()

    // ---- Live camera video (see WebRtc.kt). Stand-ins without cameras can leave these as they are. ----

    /** True when the camera can send live video by WebRTC (camera/capabilities). */
    suspend fun cameraCanStream(entityId: String): Boolean = false

    /** The STUN and TURN servers to use for this camera (camera/webrtc/get_client_config). */
    suspend fun webRtcIceServers(entityId: String): List<IceServerConfig> = emptyList()

    /**
     * Sends a WebRTC offer for the camera (camera/webrtc/offer); its events arrive through `onMessage`, on
     * any thread. Closing it ends the subscription. Null when not connected.
     */
    fun webRtcOffer(entityId: String, offerSdp: String, onMessage: (WebRtcMessage) -> Unit): java.io.Closeable? = null
}

/** Why a connection could not be opened. */
enum class OpenFailure {
    /** Nothing answered at the address. */
    CANNOT_REACH,

    /** Home Assistant no longer accepts the saved sign-in. */
    SIGN_IN_REJECTED,

    /** Home Assistant answered but could not give the app its data, as happens for a while after it starts. */
    NOT_READY,
}

class OpenFailed(val failure: OpenFailure) : Exception(failure.name)

/** Home Assistant did not carry out a command. `reason` is its own words, or COMMAND_REFUSED. */
class CommandRefused(val reason: String = COMMAND_REFUSED) : Exception(reason)

const val COMMAND_REFUSED = "Home Assistant did not accept the command."
