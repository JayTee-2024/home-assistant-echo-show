package net.badacid.homeassist.echoshow.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/** One HTTP request to Home Assistant. */
data class HttpRequest(
    /** 'GET' or 'POST'. */
    val method: String,
    val url: String,
    /** Sent as JSON. */
    val jsonBody: JsonElement? = null,
    /** Sent as form fields, the way Home Assistant's token address expects. */
    val formBody: Map<String, String>? = null,
)

data class HttpAnswer(val status: Int, val body: String)

/** Gives the answer, or throws IOException when nothing answers. The app's sender uses OkHttp. */
typealias HttpSend = suspend (HttpRequest) -> HttpAnswer

/** A sender that gives up after `timeoutMs`, throwing IOException("timeout"). */
fun withTimeout(send: HttpSend, timeoutMs: Long): HttpSend = { request ->
    try {
        kotlinx.coroutines.withTimeout(timeoutMs) { send(request) }
    } catch (_: TimeoutCancellationException) {
        throw IOException("timeout")
    }
}
