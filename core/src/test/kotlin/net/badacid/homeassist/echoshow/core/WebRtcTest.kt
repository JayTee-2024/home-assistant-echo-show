package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebRtcTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `reads each kind of offer event`() {
        assertEquals(WebRtcMessage.Session("s1"), readWebRtcEvent(json("""{"type":"session","session_id":"s1"}""")))
        assertEquals(WebRtcMessage.Answer("v=0"), readWebRtcEvent(json("""{"type":"answer","answer":"v=0"}""")))
        assertEquals(WebRtcMessage.Candidate("candidate:1", "0", 0), readWebRtcEvent(json("""{"type":"candidate","candidate":{"candidate":"candidate:1","sdpMid":"0","sdpMLineIndex":0}}""")))
        assertEquals(WebRtcMessage.Candidate("candidate:2", null, null), readWebRtcEvent(json("""{"type":"candidate","candidate":"candidate:2"}""")))
        assertEquals(WebRtcMessage.Error("Camera is offline"), readWebRtcEvent(json("""{"type":"error","code":"x","message":"Camera is offline"}""")))
        assertNull(readWebRtcEvent(json("""{"type":"something_new"}""")))
    }

    @Test
    fun `reads ICE servers with one url or several`() {
        val servers = readIceServers(json("""{"configuration":{"iceServers":[{"urls":"stun:a:3478"},{"urls":["turn:b","turns:b"],"username":"u","credential":"p"}]}}"""))
        assertEquals(listOf(IceServerConfig(listOf("stun:a:3478"), null, null), IceServerConfig(listOf("turn:b", "turns:b"), "u", "p")), servers)
        assertEquals(emptyList<IceServerConfig>(), readIceServers(null))
    }

    @Test
    fun `a camera can stream when it offers web_rtc`() {
        assertTrue(canStreamWebRtc(json("""{"frontend_stream_types":["hls","web_rtc"]}""")))
        assertFalse(canStreamWebRtc(json("""{"frontend_stream_types":["hls"]}""")))
        assertFalse(canStreamWebRtc(null))
    }
}
