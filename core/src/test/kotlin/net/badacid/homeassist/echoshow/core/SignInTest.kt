package net.badacid.homeassist.echoshow.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.badacid.homeassist.echoshow.testsupport.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Port of the LG app's src/core/sign-in.test.ts: the conversation with Home Assistant that turns a username
 * and password into tokens. Every test uses a stand-in sender that answers from a script.
 */
class SignInTest {
    private val address = "http://192.168.1.100:8123"
    private val now = 1_700_000_000_000L

    /** A sender that gives the scripted answers in order and remembers what it was asked. */
    private class Scripted(vararg answers: Any) {
        private val queue = ArrayDeque(answers.toList())
        val requests = mutableListOf<HttpRequest>()
        val send: HttpSend = { request ->
            requests += request
            when (val next = queue.removeFirstOrNull()) {
                null -> throw IOException("the test scripted too few answers")
                is IOException -> throw next
                else -> next as HttpAnswer
            }
        }
    }

    private fun json(status: Int, body: Map<String, Any?>) = HttpAnswer(status, toJson(body).toString())

    private val form = json(200, mapOf("type" to "form", "flow_id" to "flow-1", "step_id" to "init", "errors" to emptyMap<String, Any>()))
    private val entry = json(200, mapOf("type" to "create_entry", "result" to "code-xyz", "version" to 1))
    private val tokens = json(
        200,
        mapOf("access_token" to "access-abc", "refresh_token" to "refresh-def", "expires_in" to 1800, "token_type" to "Bearer"),
    )

    @Nested
    inner class BeginSignIn {
        @Test
        fun `asks Home Assistant to start a sign-in and moves to the credentials step`() = runTest {
            val script = Scripted(form)

            val step = beginSignIn(script.send, address)

            assertEquals(SignInStep.Credentials("flow-1", null), step)
            val body = buildJsonObject {
                put("client_id", CLIENT_ID)
                putJsonArray("handler") {
                    add("homeassistant")
                    add(JsonNull)
                }
                put("redirect_uri", CLIENT_ID)
            }
            assertEquals(listOf(HttpRequest("POST", "$address/auth/login_flow", jsonBody = body)), script.requests)
        }

        @Test
        fun `says it cannot reach the address when nothing answers`() = runTest {
            val step = beginSignIn(Scripted(IOException("timeout")).send, address)
            assertTrue(step is SignInStep.CannotReach)
        }

        @Test
        fun `says it is not Home Assistant when a web page answers`() = runTest {
            val step = beginSignIn(Scripted(HttpAnswer(200, "<html>Router</html>")).send, address)
            assertTrue(step is SignInStep.NotHomeAssistant)
        }

        @Test
        fun `says it is not Home Assistant when JSON of another shape answers`() = runTest {
            val step = beginSignIn(Scripted(json(200, mapOf("hello" to "world"))).send, address)
            assertTrue(step is SignInStep.NotHomeAssistant)
        }

        @Test
        fun `passes on Home Assistant's reason when it refuses`() = runTest {
            val step = beginSignIn(Scripted(json(400, mapOf("message" to "Invalid client id"))).send, address)
            assertEquals(SignInStep.Failed("Invalid client id"), step)
        }

        @Test
        fun `points to the access token route when Home Assistant refuses the sign-in from this app`() = runTest {
            val refused = HttpAnswer(403, "CORS preflight request failed")

            val step = beginSignIn(Scripted(refused).send, address)

            assertEquals(SignInStep.Failed(SIGN_IN_REFUSED), step)
            assertTrue(SIGN_IN_REFUSED.contains("access token"))
            assertTrue(SIGN_IN_REFUSED.contains("this Echo"))
        }

        @Test
        fun `gives a plain reason when a refusal carries none`() = runTest {
            val step = beginSignIn(Scripted(HttpAnswer(500, "oops")).send, address)
            assertEquals(SignInStep.Failed("Home Assistant answered with error 500."), step)
        }
    }

    @Nested
    inner class SubmitCredentials {
        @Test
        fun `sends the username and password, exchanges the code, and ends signed in`() = runTest {
            val script = Scripted(entry, tokens)

            val step = submitCredentials(script.send, address, "flow-1", "tv", "secret", now)

            assertEquals(
                SignInStep.SignedIn(SavedSignIn(address, SignInKind.ACCOUNT, "access-abc", "refresh-def", now + 1800 * 1000)),
                step,
            )
            assertEquals(
                listOf(
                    HttpRequest(
                        "POST",
                        "$address/auth/login_flow/flow-1",
                        jsonBody = toJson(mapOf("client_id" to CLIENT_ID, "username" to "tv", "password" to "secret")),
                    ),
                    HttpRequest(
                        "POST",
                        "$address/auth/token",
                        formBody = mapOf("grant_type" to "authorization_code", "code" to "code-xyz", "client_id" to CLIENT_ID),
                    ),
                ),
                script.requests,
            )
        }

        @Test
        fun `stays on the credentials step with Home Assistant's wording for a wrong password`() = runTest {
            val wrong = json(200, mapOf("type" to "form", "flow_id" to "flow-1", "step_id" to "init", "errors" to mapOf("base" to "invalid_auth")))

            val step = submitCredentials(Scripted(wrong).send, address, "flow-1", "tv", "x", now)

            assertEquals(SignInStep.Credentials("flow-1", "Invalid username or password"), step)
        }

        @Test
        fun `shows an error key it does not know as it is`() = runTest {
            val odd = json(200, mapOf("type" to "form", "flow_id" to "flow-1", "step_id" to "init", "errors" to mapOf("base" to "odd_one")))

            val step = submitCredentials(Scripted(odd).send, address, "flow-1", "tv", "x", now)

            assertEquals(SignInStep.Credentials("flow-1", "odd_one"), step)
        }

        @Test
        fun `moves to the code step when Home Assistant asks for a two-step code`() = runTest {
            val mfa = json(200, mapOf("type" to "form", "flow_id" to "flow-1", "step_id" to "mfa", "errors" to emptyMap<String, Any>()))

            val step = submitCredentials(Scripted(mfa).send, address, "flow-1", "tv", "secret", now)

            assertEquals(SignInStep.Code("flow-1", null), step)
        }

        @Test
        fun `fails with the reason when Home Assistant aborts the sign-in`() = runTest {
            val abort = json(200, mapOf("type" to "abort", "flow_id" to "flow-1", "reason" to "too_many_retry"))

            val step = submitCredentials(Scripted(abort).send, address, "flow-1", "tv", "x", now)

            assertEquals(SignInStep.Failed("too_many_retry"), step)
        }

        @Test
        fun `starts again with an explanation when Home Assistant has forgotten the sign-in`() = runTest {
            val forgotten = json(404, mapOf("message" to "Invalid flow specified"))
            val freshForm = json(200, mapOf("type" to "form", "flow_id" to "flow-2", "step_id" to "init", "errors" to emptyMap<String, Any>()))
            val script = Scripted(forgotten, freshForm)

            val step = submitCredentials(script.send, address, "flow-1", "tv", "secret", now)

            assertEquals(SignInStep.Credentials("flow-2", "Sign-in took too long. Please try again."), step)
            assertEquals("$address/auth/login_flow", script.requests[1].url)
        }

        @Test
        fun `says it cannot reach the address when the connection drops part-way`() = runTest {
            val step = submitCredentials(Scripted(IOException("Failed to fetch")).send, address, "flow-1", "tv", "secret", now)
            assertTrue(step is SignInStep.CannotReach)
        }

        @Test
        fun `fails when the token exchange is refused`() = runTest {
            val refused = json(400, mapOf("error" to "invalid_request", "error_description" to "Invalid code"))

            val step = submitCredentials(Scripted(entry, refused).send, address, "flow-1", "tv", "secret", now)

            assertEquals(SignInStep.Failed("Invalid code"), step)
        }

        @Test
        fun `fails when the token answer lacks a token`() = runTest {
            val step = submitCredentials(
                Scripted(entry, json(200, mapOf("access_token" to "only-this"))).send,
                address, "flow-1", "tv", "secret", now,
            )
            assertTrue(step is SignInStep.NotHomeAssistant)
        }
    }

    @Nested
    inner class SubmitCode {
        @Test
        fun `sends the code and ends signed in`() = runTest {
            val script = Scripted(entry, tokens)

            val step = submitCode(script.send, address, "flow-1", "123456", now)

            assertTrue(step is SignInStep.SignedIn)
            assertEquals(
                HttpRequest("POST", "$address/auth/login_flow/flow-1", jsonBody = toJson(mapOf("client_id" to CLIENT_ID, "code" to "123456"))),
                script.requests[0],
            )
        }

        @Test
        fun `stays on the code step for a wrong code`() = runTest {
            val wrong = json(200, mapOf("type" to "form", "flow_id" to "flow-1", "step_id" to "mfa", "errors" to mapOf("base" to "invalid_code")))

            val step = submitCode(Scripted(wrong).send, address, "flow-1", "000000", now)

            assertEquals(SignInStep.Code("flow-1", "Invalid authentication code"), step)
        }
    }

    @Nested
    inner class SignInWithToken {
        @Test
        fun `builds a sign-in from a long-lived access token, trimming stray spaces`() {
            assertEquals(
                SavedSignIn(address, SignInKind.LONG_LIVED_TOKEN, "eyJ.token.value", "", 0),
                signInWithToken(address, "  eyJ.token.value \n"),
            )
        }
    }

    @Test
    fun `the client id is this app's own page`() {
        assertEquals("https://jaytee-2024.github.io/home-assistant-echo-show/", CLIENT_ID)
    }
}
