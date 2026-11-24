package net.badacid.homeassist.echoshow.screens.signin

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.core.HttpRequest
import net.badacid.homeassist.echoshow.core.HttpSend
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.SignInKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** The sign-in screen's steps, without views. The LG app's SignInScreen.tsx is the behaviour to match. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInFlowTest {
    private val form = HttpAnswer(200, """{"type":"form","flow_id":"flow-1","step_id":"init","errors":{}}""")
    private val mfa = HttpAnswer(200, """{"type":"form","flow_id":"flow-1","step_id":"mfa","errors":{}}""")
    private val wrong = HttpAnswer(200, """{"type":"form","flow_id":"flow-1","step_id":"init","errors":{"base":"invalid_auth"}}""")
    private val entry = HttpAnswer(200, """{"type":"create_entry","result":"code-1"}""")
    private val tokens = HttpAnswer(200, """{"access_token":"a","refresh_token":"r","expires_in":1800}""")

    /** Answers in order; an answer may be an IOException, or a CompletableDeferred to wait on. */
    private class Answers(vararg answers: Any) {
        private val queue = ArrayDeque(answers.toList())
        val requests = mutableListOf<HttpRequest>()
        val send: HttpSend = { request ->
            requests += request
            when (val next = queue.removeFirstOrNull() ?: IOException("none scripted")) {
                is IOException -> throw next
                is CompletableDeferred<*> -> next.await() as HttpAnswer
                else -> next as HttpAnswer
            }
        }
    }

    private val signedIn = mutableListOf<SavedSignIn>()

    private fun TestScope.flow(answers: Answers, startAddress: String? = null, notice: String? = null, refusedToken: String? = null) =
        SignInFlow(answers.send, backgroundScope, { 1_000L }, startAddress, notice, refusedToken) { signedIn += it }.also {
            it.start()
            runCurrent()
        }

    @Test
    fun `tries the saved address at once and moves to username and password`() = runTest {
        val answers = Answers(form)
        val flow = flow(answers, startAddress = "http://192.168.1.100:8123")
        assertEquals(SignInForm.CREDENTIALS, flow.state.value.form)
        assertEquals("http://192.168.1.100:8123/auth/login_flow", answers.requests.single().url)
        assertEquals("192.168.1.100:8123", flow.state.value.shownAddress)
    }

    @Test
    fun `with nothing saved tries Home Assistant's own name, and asks for the address when nothing answers`() = runTest {
        val answers = Answers(IOException("no such host"))
        val flow = flow(answers)
        assertEquals("http://homeassistant.local:8123/auth/login_flow", answers.requests.single().url)
        assertEquals(SignInForm.ADDRESS, flow.state.value.form)
        assertEquals("Could not reach Home Assistant at this address.", flow.state.value.message)
        assertEquals("", flow.state.value.typedAddress)
    }

    @Test
    fun `the address keeps what was tried when nothing answers`() = runTest {
        val flow = flow(Answers(IOException("timeout")), startAddress = "http://192.168.1.100:8123")
        assertEquals("192.168.1.100:8123", flow.state.value.typedAddress)
    }

    @Test
    fun `an empty or bad address says so`() = runTest {
        val flow = flow(Answers(IOException("x")))
        flow.connect("  ")
        assertEquals("Enter an address.", flow.state.value.message)
        flow.connect("a b")
        assertEquals("That is not a valid address.", flow.state.value.message)
        assertEquals(SignInForm.ADDRESS, flow.state.value.form)
    }

    @Test
    fun `a good address moves to the credentials step`() = runTest {
        val answers = Answers(IOException("x"), form)
        val flow = flow(answers)
        flow.connect("192.168.1.100")
        runCurrent()
        assertEquals(SignInForm.CREDENTIALS, flow.state.value.form)
        assertEquals("http://192.168.1.100:8123/auth/login_flow", answers.requests.last().url)
    }

    @Test
    fun `missing username or password says so without asking Home Assistant`() = runTest {
        val answers = Answers(form)
        val flow = flow(answers)
        flow.signIn("echo", "")
        assertEquals("Enter your username and password.", flow.state.value.message)
        assertEquals(1, answers.requests.size)
    }

    @Test
    fun `a wrong password stays with Home Assistant's message and asks for the password again`() = runTest {
        val flow = flow(Answers(form, wrong))
        val attemptsBefore = flow.state.value.answers
        flow.signIn("echo", "nope")
        assertTrue(flow.state.value.busy)
        runCurrent()
        assertEquals(SignInForm.CREDENTIALS, flow.state.value.form)
        assertEquals("Invalid username or password", flow.state.value.message)
        assertFalse(flow.state.value.busy)
        assertTrue(flow.state.value.answers > attemptsBefore, "the view clears the password on each answer")
    }

    @Test
    fun `a two-step code goes to the code step, then signs in`() = runTest {
        val flow = flow(Answers(form, mfa, entry, tokens))
        flow.signIn("echo", "secret")
        runCurrent()
        assertEquals(SignInForm.CODE, flow.state.value.form)
        flow.verify("123456")
        runCurrent()
        assertEquals(1, signedIn.size)
    }

    @Test
    fun `signed in calls back with the sign-in`() = runTest {
        val flow = flow(Answers(form, entry, tokens), startAddress = "http://ha:8123")
        flow.signIn("echo", "secret")
        runCurrent()
        assertEquals(SavedSignIn("http://ha:8123", SignInKind.ACCOUNT, "a", "r", 1_000L + 1_800_000), signedIn.single())
    }

    @Test
    fun `the token route signs in with a trimmed token, and needs one`() = runTest {
        val flow = flow(Answers(form), startAddress = "http://ha:8123")
        flow.goToToken()
        assertEquals(SignInForm.TOKEN, flow.state.value.form)
        flow.useToken("  ")
        assertEquals("Enter an access token.", flow.state.value.message)
        flow.useToken(" abc.def \n")
        assertEquals(SavedSignIn("http://ha:8123", SignInKind.LONG_LIVED_TOKEN, "abc.def", "", 0), signedIn.single())
    }

    @Test
    fun `the token route from the address form needs an address first`() = runTest {
        val flow = flow(Answers(IOException("x")))
        flow.useTokenFor("")
        assertEquals("Enter the address of your Home Assistant first.", flow.state.value.message)
        flow.useTokenFor("10.0.0.5")
        assertEquals(SignInForm.TOKEN, flow.state.value.form)
        assertEquals("10.0.0.5:8123", flow.state.value.shownAddress)
    }

    @Test
    fun `a refused token is shown again with the message, without trying the address`() = runTest {
        val answers = Answers()
        val flow = flow(answers, startAddress = "http://ha:8123", notice = "Home Assistant did not accept that access token. Check it and try again.", refusedToken = "old-token")
        assertEquals(SignInForm.TOKEN, flow.state.value.form)
        assertEquals("old-token", flow.state.value.typedToken)
        assertEquals("Home Assistant did not accept that access token. Check it and try again.", flow.state.value.message)
        assertTrue(answers.requests.isEmpty())
    }

    @Test
    fun `the notice shows with the first form`() = runTest {
        val flow = flow(Answers(form), notice = "Home Assistant did not accept the sign-in. Please sign in again.")
        assertEquals("Home Assistant did not accept the sign-in. Please sign in again.", flow.state.value.message)
    }

    @Test
    fun `back goes to the step before, and from the first forms does nothing`() = runTest {
        val flow = flow(Answers(form, mfa, form), startAddress = "http://ha:8123")
        flow.signIn("echo", "secret")
        runCurrent()
        assertEquals(SignInForm.CODE, flow.state.value.form)
        assertTrue(flow.back())
        runCurrent()
        assertEquals(SignInForm.CREDENTIALS, flow.state.value.form)
        assertTrue(flow.back())
        assertEquals(SignInForm.ADDRESS, flow.state.value.form)
        assertFalse(flow.back())
    }

    @Test
    fun `an answer that arrives after the person moved on is ignored`() = runTest {
        val slow = CompletableDeferred<HttpAnswer>()
        val flow = flow(Answers(slow), startAddress = "http://ha:8123")
        flow.change()
        slow.complete(form)
        runCurrent()
        assertEquals(SignInForm.ADDRESS, flow.state.value.form)
        assertEquals("ha:8123", flow.state.value.typedAddress)
    }

    @Test
    fun `the password is never part of the state`() = runTest {
        val flow = flow(Answers(form, wrong))
        flow.signIn("echo", "secret-password")
        runCurrent()
        assertFalse(flow.state.value.toString().contains("secret-password"))
        assertNull(flow.state.value.typedToken)
    }
}
