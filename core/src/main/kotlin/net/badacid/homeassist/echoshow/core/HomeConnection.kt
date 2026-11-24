package net.badacid.homeassist.echoshow.core

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
