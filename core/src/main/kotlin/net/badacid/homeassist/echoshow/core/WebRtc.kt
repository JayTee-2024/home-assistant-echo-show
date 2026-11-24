/*
 * Live camera video through Home Assistant: the messages its camera WebRTC commands send back, read into
 * plain values. The WebRTC work itself is in the app (platform/LiveVideoSession.kt).
 */
package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** One STUN or TURN server from camera/webrtc/get_client_config. */
data class IceServerConfig(val urls: List<String>, val username: String?, val credential: String?)

/** An event of a camera/webrtc/offer subscription. */
sealed interface WebRtcMessage {
    data class Session(val id: String) : WebRtcMessage
    data class Answer(val sdp: String) : WebRtcMessage
    data class Candidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?) : WebRtcMessage
    data class Error(val message: String) : WebRtcMessage
}

private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

fun readWebRtcEvent(event: JsonObject): WebRtcMessage? = when (text(event["type"])) {
    "session" -> text(event["session_id"])?.let { WebRtcMessage.Session(it) }
    "answer" -> text(event["answer"])?.let { WebRtcMessage.Answer(it) }
    "candidate" -> when (val raw = event["candidate"]) {
        is JsonObject -> text(raw["candidate"])?.let {
            WebRtcMessage.Candidate(it, text(raw["sdpMid"]), (raw["sdpMLineIndex"] as? JsonPrimitive)?.intOrNull)
        }
        else -> text(raw)?.let { WebRtcMessage.Candidate(it, null, null) }
    }
    "error" -> WebRtcMessage.Error(text(event["message"]) ?: "The camera could not start live video.")
    else -> null
}

/** The ICE servers in a camera/webrtc/get_client_config result. */
fun readIceServers(result: JsonObject?): List<IceServerConfig> {
    val servers = ((result?.get("configuration") as? JsonObject)?.get("iceServers") as? JsonArray) ?: return emptyList()
    return servers.filterIsInstance<JsonObject>().mapNotNull { server ->
        val urls = when (val raw = server["urls"]) {
            is JsonArray -> raw.mapNotNull(::text)
            else -> listOfNotNull(text(raw))
        }
        if (urls.isEmpty()) null else IceServerConfig(urls, text(server["username"]), text(server["credential"]))
    }
}

/** True when a camera/capabilities result offers WebRTC. */
fun canStreamWebRtc(result: JsonObject?): Boolean =
    (result?.get("frontend_stream_types") as? JsonArray)?.any { text(it) == "web_rtc" } == true
