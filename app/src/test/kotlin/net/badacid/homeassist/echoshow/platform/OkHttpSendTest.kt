package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.core.HttpRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class OkHttpSendTest {
    private val server = MockWebServer().apply { start() }
    private val send = OkHttpSend(OkHttpClient())
    private fun url(path: String) = server.url(path).toString()

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `sends JSON and form bodies with the right content types`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setBody("{}"))

        val json = send(HttpRequest("POST", url("/auth/login_flow"), jsonBody = buildJsonObject { put("client_id", JsonPrimitive("x")) }))
        send(HttpRequest("POST", url("/auth/token"), formBody = mapOf("grant_type" to "refresh_token", "token" to "a b&c")))

        assertEquals(HttpAnswer(200, """{"ok":true}"""), json)
        val first = server.takeRequest()
        assertTrue(first.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("""{"client_id":"x"}""", first.body.readUtf8())
        val second = server.takeRequest()
        assertTrue(second.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        assertEquals("grant_type=refresh_token&token=a%20b%26c", second.body.readUtf8())
    }

    @Test
    fun `sends a GET with no body`() = runTest {
        server.enqueue(MockResponse().setBody("hi"))
        assertEquals(HttpAnswer(200, "hi"), send(HttpRequest("GET", url("/api/"))))
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `gives back the status and body of an error answer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("CORS preflight request failed"))
        assertEquals(HttpAnswer(403, "CORS preflight request failed"), send(HttpRequest("POST", url("/auth/login_flow"), jsonBody = JsonPrimitive(1))))
    }

    @Test
    fun `turns a refused connection into an IOException`() = runTest {
        val address = url("/")
        server.shutdown()
        assertThrows<IOException> { send(HttpRequest("GET", address)) }
    }

    @Test
    fun `turns an address OkHttp cannot parse into an IOException`() = runTest {
        val error = assertThrows<IOException> { send(HttpRequest("GET", "http://192.168.1..100:8123/auth/login_flow")) }
        assertEquals("Not a usable address", error.message)
        assertThrows<IOException> { send(HttpRequest("GET", "not a url at all")) }
    }
}
