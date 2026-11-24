package net.badacid.homeassist.echoshow.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.CommandRefused
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.EMPTY_SAVED_STATE
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.OpenFailed
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.SignInKind
import net.badacid.homeassist.echoshow.core.addCard
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.core.hasCard
import net.badacid.homeassist.echoshow.core.parseSavedState
import net.badacid.homeassist.echoshow.core.readEntityState
import net.badacid.homeassist.echoshow.core.readRegistryAreas
import net.badacid.homeassist.echoshow.core.readRegistryDevices
import net.badacid.homeassist.echoshow.core.readRegistryEntities
import net.badacid.homeassist.echoshow.core.serialiseSavedState
import net.badacid.homeassist.echoshow.core.signInWithToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The app's brain, against a stand-in connection, in virtual time. App.tsx in the LG app is the behaviour to match. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelTest {
    private val sampleHome: HomeSnapshot = net.badacid.homeassist.echoshow.testsupport.sampleHome()
    private val account = SavedSignIn("http://ha:8123", SignInKind.ACCOUNT, "access", "refresh", 9_999_999_999_999)

    private class MemoryStorage(var text: String? = null) : SavedTextStorage {
        val writes = mutableListOf<String>()
        override fun read() = text
        override fun write(text: String) {
            this.text = text
            writes += text
        }
    }

    private inner class FakeConnection : HomeConnection {
        override val userName = "TV"
        override val home = MutableStateFlow(sampleHome)
        override val status = MutableStateFlow(ConnectionStatus.CONNECTED)
        val calls = mutableListOf<ServiceCall>()
        var refuseWith: String? = null
        var signOutWait: CompletableDeferred<Unit>? = null
        var closed = false
        var signedOut = false
        override suspend fun call(call: ServiceCall) {
            calls += call
            refuseWith?.let { throw CommandRefused(it) }
        }
        override suspend fun signOut() {
            signOutWait?.await()
            signedOut = true
            closed = true
        }
        override fun close() {
            closed = true
        }
    }

    /** What each open attempt does: give a connection, throw a failure, or wait. */
    private val attempts = mutableListOf<suspend () -> HomeConnection>()
    private val opened = mutableListOf<SavedSignIn>()
    private val tokenCallbacks = mutableListOf<(SavedSignIn) -> Unit>()

    private fun TestScope.model(storage: SavedTextStorage) = AppModel(
        storage = storage,
        openConnection = { signIn, onTokens ->
            opened += signIn
            tokenCallbacks += onTokens
            val next = attempts.removeFirstOrNull() ?: error("no attempt scripted")
            next()
        },
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    )

    private fun savedWith(vararg devices: Pair<String, CardSize>, signIn: SavedSignIn? = account): MemoryStorage {
        var layout = net.badacid.homeassist.echoshow.core.EMPTY_LAYOUT
        for ((id, size) in devices) layout = addCard(layout, id, size)
        return MemoryStorage(serialiseSavedState(EMPTY_SAVED_STATE.copy(signIn = signIn, layout = layout, layoutAddress = signIn?.address)))
    }

    private fun TestScope.connectedModel(vararg devices: Pair<String, CardSize>): Pair<AppModel, FakeConnection> {
        val connection = FakeConnection()
        attempts += { connection }
        val model = model(savedWith(*devices))
        model.start()
        runCurrent()
        return model to connection
    }

    @Test
    fun `starts signed out with nothing saved, and connecting with a saved sign-in`() = runTest {
        val empty = model(MemoryStorage())
        empty.start()
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, empty.state.value.phase)

        val waiting = CompletableDeferred<HomeConnection>()
        attempts += { waiting.await() }
        val saved = model(savedWith())
        saved.start()
        runCurrent()
        assertEquals(Phase.CONNECTING, saved.state.value.phase)
        assertEquals(listOf(account), opened)
    }

    @Test
    fun `a finished connection shows the home`() = runTest {
        val (model, _) = connectedModel()
        val state = model.state.value
        assertEquals(Phase.HOME, state.phase)
        assertEquals("TV", state.userName)
        assertTrue(state.devices.isNotEmpty())
        assertEquals("on", state.shownHome.states["light.lounge_lamp"]?.state)
    }

    @Test
    fun `fresh tokens are saved`() = runTest {
        val storage = savedWith()
        attempts += { FakeConnection() }
        val model = model(storage)
        model.start()
        runCurrent()
        tokenCallbacks.single()(account.copy(accessToken = "fresh"))
        assertEquals("fresh", parseSavedState(storage.text).signIn?.accessToken)
    }

    @Test
    fun `a refused sign-in signs out with the message, and a refused token also offers it back`() = runTest {
        attempts += { throw OpenFailed(OpenFailure.SIGN_IN_REJECTED) }
        val model = model(savedWith())
        model.start()
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, model.state.value.phase)
        assertEquals(SIGN_IN_REJECTED, model.state.value.notice)
        assertNull(model.state.value.saved.signIn)

        val token = signInWithToken("http://ha:8123", "long-lived")
        attempts += { throw OpenFailed(OpenFailure.SIGN_IN_REJECTED) }
        model.signedIn(token)
        runCurrent()
        assertEquals(TOKEN_REJECTED, model.state.value.notice)
        assertEquals("long-lived", model.state.value.refusedToken)
    }

    @Test
    fun `a refused token arrives on the sign-in screen in the same state as the phase, never after it`() = runTest {
        val model = model(savedWith(signIn = null))
        model.start()
        runCurrent()
        // Every state the activity could draw, as a collector on the main thread sees them.
        val seen = mutableListOf<AppState>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { model.state.collect { seen += it } }
        attempts += { throw OpenFailed(OpenFailure.SIGN_IN_REJECTED) }
        model.signedIn(signInWithToken("http://ha:8123", "long-lived"))
        runCurrent()
        val firstSignedOut = seen.first { it.phase == Phase.SIGNED_OUT && it.notice == TOKEN_REJECTED }
        assertEquals("long-lived", firstSignedOut.refusedToken)
    }

    @Test
    fun `cannot reach retries every 5 seconds with the newest tokens, showing why`() = runTest {
        attempts += { throw OpenFailed(OpenFailure.CANNOT_REACH) }
        attempts += { throw OpenFailed(OpenFailure.NOT_READY) }
        attempts += { FakeConnection() }
        val model = model(savedWith())
        model.start()
        runCurrent()
        assertEquals(OpenFailure.CANNOT_REACH, model.state.value.connectFailure)
        tokenCallbacks.single()(account.copy(accessToken = "fresh"))

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, opened.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, opened.size)
        assertEquals("fresh", opened[1].accessToken)
        assertEquals(OpenFailure.NOT_READY, model.state.value.connectFailure)

        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(Phase.HOME, model.state.value.phase)
        assertNull(model.state.value.connectFailure)
    }

    @Test
    fun `a slow attempt the person has left is ignored and its connection closed`() = runTest {
        val slow = CompletableDeferred<HomeConnection>()
        attempts += { slow.await() }
        val model = model(savedWith())
        model.start()
        runCurrent()
        model.signInAgain()
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, model.state.value.phase)

        val late = FakeConnection()
        slow.complete(late)
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, model.state.value.phase)
        assertTrue(late.closed)
    }

    @Test
    fun `a tap runs the main action, shows the prediction, and a refusal puts it back with a toast`() = runTest {
        val (model, connection) = connectedModel("device:dev_lifx_lamp" to CardSize.SMALL)
        model.pressCard("device:dev_lifx_lamp")
        runCurrent()
        assertEquals("toggle", connection.calls.single().service)
        assertEquals("off", model.state.value.shownHome.states["light.lounge_lamp"]?.state)

        connection.refuseWith = "Light is busy."
        model.pressCard("device:dev_lifx_lamp")
        runCurrent()
        assertEquals("Light is busy.", model.state.value.toast)
        advanceTimeBy(4_001)
        runCurrent()
        assertNull(model.state.value.toast)
    }

    @Test
    fun `a tap while reconnecting shows Not connected`() = runTest {
        val (model, connection) = connectedModel("device:dev_lifx_lamp" to CardSize.SMALL)
        connection.status.value = ConnectionStatus.RECONNECTING
        runCurrent()
        model.pressCard("device:dev_lifx_lamp")
        runCurrent()
        assertEquals(NOT_CONNECTED, model.state.value.toast)
        assertTrue(connection.calls.isEmpty())
    }

    @Test
    fun `a tap on an open-panel type opens the panel, and a hold opens it for any device`() = runTest {
        val (model, _) = connectedModel("device:dev_tado" to CardSize.WIDE, "device:dev_lifx_lamp" to CardSize.SMALL)
        model.pressCard("device:dev_tado")
        assertEquals(Overlay.Panel("device:dev_tado"), model.state.value.overlay)
        model.closeOverlay()
        model.holdCard("device:dev_lifx_lamp")
        assertEquals(Overlay.Panel("device:dev_lifx_lamp"), model.state.value.overlay)
    }

    @Test
    fun `a card for a device Home Assistant no longer has is removed on tap, and a hold does nothing`() = runTest {
        val (model, _) = connectedModel("device:gone" to CardSize.SMALL, "device:dev_lifx_lamp" to CardSize.SMALL)
        model.holdCard("device:gone")
        assertEquals(Overlay.None, model.state.value.overlay)
        model.pressCard("device:gone")
        assertFalse(hasCard(model.state.value.saved.layout, "device:gone"))
        assertTrue(hasCard(model.state.value.saved.layout, "device:dev_lifx_lamp"))
    }

    @Test
    fun `the picker adds and removes cards, and says a device is not supported yet`() = runTest {
        val (model, _) = connectedModel()
        val lamp = findDevice(model.state.value.devices, "device:dev_lifx_lamp")!!
        model.toggleDevice(lamp)
        assertTrue(hasCard(model.state.value.saved.layout, lamp.id))
        model.toggleDevice(lamp)
        assertFalse(hasCard(model.state.value.saved.layout, lamp.id))

        val lock = findDevice(model.state.value.devices, "device:dev_lock")!!
        model.toggleDevice(lock)
        assertEquals("${lock.name} is not supported yet.", model.state.value.toast)
        assertFalse(hasCard(model.state.value.saved.layout, lock.id))
    }

    @Test
    fun `the page steps back when the last page empties`() = runTest {
        val cards = (1..12).map { "device:filler$it" to CardSize.SMALL } + ("device:dev_lifx_lamp" to CardSize.SMALL)
        val (model, _) = connectedModel(*cards.toTypedArray())
        model.showPage(1)
        assertEquals(1, model.state.value.page)
        model.toggleDevice(findDevice(model.state.value.devices, "device:dev_lifx_lamp")!!)
        assertEquals(0, model.state.value.page)
    }

    @Test
    fun `card sizes follow the devices once they have fully reported`() = runTest {
        // Saved small, but the Tado device's main part (heating) is wide.
        val (model, _) = connectedModel("device:dev_tado" to CardSize.SMALL)
        assertEquals(CardSize.WIDE, model.state.value.saved.layout.cards.single().size)
    }

    @Test
    fun `an open panel closes when its device disappears`() = runTest {
        val (model, connection) = connectedModel("device:dev_lifx_lamp" to CardSize.SMALL)
        model.holdCard("device:dev_lifx_lamp")
        connection.home.value = sampleHome.copy(devices = sampleHome.devices.filter { it.id != "dev_lifx_lamp" })
        runCurrent()
        assertEquals(Overlay.None, model.state.value.overlay)
    }

    @Test
    fun `predictions older than 5 seconds are dropped`() = runTest {
        val (model, _) = connectedModel("device:dev_lifx_lamp" to CardSize.SMALL)
        model.pressCard("device:dev_lifx_lamp")
        runCurrent()
        assertEquals("off", model.state.value.shownHome.states["light.lounge_lamp"]?.state)
        advanceTimeBy(6_100)
        runCurrent()
        assertEquals("on", model.state.value.shownHome.states["light.lounge_lamp"]?.state)
    }

    @Test
    fun `a report from Home Assistant settles the prediction`() = runTest {
        val (model, connection) = connectedModel("device:dev_lifx_lamp" to CardSize.SMALL)
        model.pressCard("device:dev_lifx_lamp")
        runCurrent()
        val lamp = sampleHome.states.getValue("light.lounge_lamp")
        // Home Assistant reports the lamp still on (the toggle did not take), with a new brightness.
        val reported = lamp.copy(attributes = kotlinx.serialization.json.JsonObject(lamp.attributes + ("brightness" to kotlinx.serialization.json.JsonPrimitive(90))))
        connection.home.value = sampleHome.copy(states = sampleHome.states + ("light.lounge_lamp" to reported))
        runCurrent()
        assertEquals("on", model.state.value.shownHome.states["light.lounge_lamp"]?.state)
    }

    @Test
    fun `back closes the overlay, and does nothing on the home screen`() = runTest {
        val (model, _) = connectedModel()
        model.openSettings()
        assertTrue(model.back())
        assertEquals(Overlay.None, model.state.value.overlay)
        assertFalse(model.back())
    }

    @Test
    fun `sign out cancels the sign-in and shows the sign-in screen, giving up waiting after 5 seconds`() = runTest {
        val (model, connection) = connectedModel()
        model.signOut()
        runCurrent()
        assertTrue(connection.signedOut)
        assertEquals(Phase.SIGNED_OUT, model.state.value.phase)
        assertNull(model.state.value.saved.signIn)

        val (slowModel, slow) = connectedModel()
        slow.signOutWait = CompletableDeferred()
        slowModel.signOut()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(Phase.HOME, slowModel.state.value.phase)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, slowModel.state.value.phase)
        assertNull(slowModel.state.value.notice)
    }

    @Test
    fun `SIGN_IN_REJECTED while connected signs out with the message`() = runTest {
        val (model, connection) = connectedModel()
        connection.status.value = ConnectionStatus.SIGN_IN_REJECTED
        runCurrent()
        assertEquals(Phase.SIGNED_OUT, model.state.value.phase)
        assertEquals(SIGN_IN_REJECTED, model.state.value.notice)
        assertTrue(connection.closed)
    }

    @Test
    fun `signing in keeps the layout for the same home and connects`() = runTest {
        val storage = savedWith("device:dev_lifx_lamp" to CardSize.SMALL, signIn = null)
        storage.text = serialiseSavedState(parseSavedState(storage.text).copy(layoutAddress = account.address))
        val model = model(storage)
        model.start()
        runCurrent()
        attempts += { FakeConnection() }
        model.signedIn(account)
        runCurrent()
        assertEquals(Phase.HOME, model.state.value.phase)
        assertTrue(hasCard(model.state.value.saved.layout, "device:dev_lifx_lamp"))
    }

    @Test
    fun `every change is written to storage`() = runTest {
        val storage = savedWith()
        attempts += { FakeConnection() }
        val model = model(storage)
        model.start()
        runCurrent()
        val before = storage.writes.size
        model.toggleDevice(findDevice(model.state.value.devices, "device:dev_lifx_lamp")!!)
        assertEquals(before + 1, storage.writes.size)
        assertTrue(hasCard(parseSavedState(storage.text).layout, "device:dev_lifx_lamp"))
    }

}
