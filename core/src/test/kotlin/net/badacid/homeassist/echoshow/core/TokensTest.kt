package net.badacid.homeassist.echoshow.core

import kotlinx.coroutines.test.runTest
import net.badacid.homeassist.echoshow.testsupport.ScriptedSend
import net.badacid.homeassist.echoshow.testsupport.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class TokensTest {
    private val now = 1_700_000_000_000L
    private val account = SavedSignIn("http://ha:8123", SignInKind.ACCOUNT, "old-access", "refresh-1", now - 1)
    private val longLived = signInWithToken("http://ha:8123", "long-lived")

    private fun answer(status: Int, body: Map<String, Any?>) = HttpAnswer(status, toJson(body).toString())

    @Test
    fun `refreshes and keeps the refresh token`() = runTest {
        val script = ScriptedSend(answer(200, mapOf("access_token" to "new-access", "token_type" to "Bearer", "expires_in" to 1800)))

        val result = refreshTokens(script.send, account, now)

        assertEquals(RefreshResult.Refreshed(account.copy(accessToken = "new-access", expiresAt = now + 1_800_000)), result)
        assertEquals(
            listOf(
                HttpRequest(
                    "POST",
                    "http://ha:8123/auth/token",
                    formBody = mapOf("grant_type" to "refresh_token", "refresh_token" to "refresh-1", "client_id" to CLIENT_ID),
                ),
            ),
            script.requests,
        )
    }

    @Test
    fun `a refused refresh is Rejected`() = runTest {
        for (status in listOf(400, 403)) {
            val script = ScriptedSend(answer(status, mapOf("error" to "invalid_grant")))
            assertEquals(RefreshResult.Rejected, refreshTokens(script.send, account, now), status.toString())
        }
    }

    @Test
    fun `no answer, or an answer that is not tokens, is CannotReach`() = runTest {
        assertEquals(RefreshResult.CannotReach, refreshTokens(ScriptedSend(IOException("timeout")).send, account, now))
        assertEquals(RefreshResult.CannotReach, refreshTokens(ScriptedSend(HttpAnswer(502, "Bad gateway")).send, account, now))
        assertEquals(RefreshResult.CannotReach, refreshTokens(ScriptedSend(HttpAnswer(200, "<html>")).send, account, now))
    }

    @Test
    fun `needsRefresh ten seconds before expiry, never for a long-lived token`() {
        val fresh = account.copy(expiresAt = now + 60_000)
        assertFalse(needsRefresh(fresh, now))
        assertFalse(needsRefresh(fresh, now + 49_999))
        assertTrue(needsRefresh(fresh, now + 50_000))
        assertFalse(needsRefresh(longLived, now))
    }

    @Test
    fun `revoke sends the refresh token and never throws`() = runTest {
        val script = ScriptedSend(answer(200, emptyMap()))
        revokeTokens(script.send, account)
        assertEquals(listOf(HttpRequest("POST", "http://ha:8123/auth/revoke", formBody = mapOf("token" to "refresh-1"))), script.requests)

        revokeTokens(ScriptedSend(IOException("down")).send, account)
        revokeTokens(ScriptedSend(HttpAnswer(500, "")).send, account)
    }

    @Test
    fun `revoke does nothing for a long-lived token`() = runTest {
        val script = ScriptedSend()
        revokeTokens(script.send, longLived)
        assertEquals(emptyList<HttpRequest>(), script.requests)
    }
}
