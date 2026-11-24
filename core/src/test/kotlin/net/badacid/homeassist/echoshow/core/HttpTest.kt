package net.badacid.homeassist.echoshow.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

/** Port of the LG app's src/core/http.test.ts: the time limit added around a sender. */
class HttpTest {
    private val request = HttpRequest(method = "GET", url = "http://example.test/")

    @Test
    fun `passes the answer through when it arrives in time`() = runTest {
        val send: HttpSend = { HttpAnswer(200, "hello") }
        assertEquals(HttpAnswer(200, "hello"), withTimeout(send, 1000)(request))
    }

    @Test
    fun `passes a failure through`() = runTest {
        val send: HttpSend = { throw IOException("no route") }
        val error = assertThrows<IOException> { withTimeout(send, 1000)(request) }
        assertEquals("no route", error.message)
    }

    @Test
    fun `rejects with "timeout" once the time is up, and not before`() = runTest {
        val neverAnswers: HttpSend = { CompletableDeferred<HttpAnswer>().await() }
        val outcome = async { runCatching { withTimeout(neverAnswers, 8000)(request) } }

        advanceTimeBy(7999)
        runCurrent()
        assertFalse(outcome.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(outcome.isCompleted)
        val error = outcome.await().exceptionOrNull()
        assertTrue(error is IOException, "a time-out is an IOException, so callers treat it as no answer")
        assertEquals("timeout", error?.message)
    }

    @Test
    fun `gives the request to the sender unchanged`() = runTest {
        val seen = mutableListOf<HttpRequest>()
        val send: HttpSend = { sent ->
            seen += sent
            HttpAnswer(204, "")
        }
        withTimeout(send, 1000)(request)
        assertEquals(listOf(request), seen)
    }
}
