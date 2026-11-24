package net.badacid.homeassist.echoshow.screens

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.screens.connecting.ConnectingView
import net.badacid.homeassist.echoshow.screens.signin.SignInFlow
import net.badacid.homeassist.echoshow.screens.signin.SignInView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/** Every screen drawn at the Echo's size, into docs/screens/, to look at without the Echo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class ScreenshotTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun signInShot(name: String, answer: Any, startAddress: String? = "http://192.168.1.100:8123", act: (SignInFlow) -> Unit = {}) {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val flow = SignInFlow({ if (answer is IOException) throw answer else answer as HttpAnswer }, scope, { 0L }, startAddress, null, null) {}
        flow.start()
        scope.testScheduler.advanceUntilIdle()
        act(flow)
        scope.testScheduler.advanceUntilIdle()
        val view = SignInView(context, flow)
        view.show(flow.state.value)
        snapshot(view, name)
    }

    @Test
    fun signInCredentials() = signInShot("sign-in-credentials", HttpAnswer(200, """{"type":"form","flow_id":"f","step_id":"init","errors":{}}"""))

    @Test
    fun signInAddress() = signInShot("sign-in-address", IOException("no"), startAddress = null)

    @Test
    fun signInError() = signInShot("sign-in-error", IOException("no"), startAddress = null) { it.connect("a b") }

    @Test
    fun signInToken() = signInShot("sign-in-token", HttpAnswer(200, """{"type":"form","flow_id":"f","step_id":"init","errors":{}}""")) { it.goToToken() }

    @Test
    fun connecting() {
        snapshot(ConnectingView(context, "http://192.168.1.100:8123", null) {}, "connecting")
        snapshot(ConnectingView(context, "http://192.168.1.100:8123", OpenFailure.CANNOT_REACH) {}, "connecting-failed")
    }

}
