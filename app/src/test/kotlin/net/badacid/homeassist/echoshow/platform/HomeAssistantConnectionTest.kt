package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.async
import net.badacid.homeassist.echoshow.core.HomeConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.COMMAND_REFUSED
import net.badacid.homeassist.echoshow.core.CommandRefused
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.OpenFailed
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.IceServerConfig
import net.badacid.homeassist.echoshow.core.WebRtcMessage
import net.badacid.homeassist.echoshow.core.textOrNull
import org.junit.jupiter.api.Assertions.assertFalse
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.testsupport.FakeHomeAssistant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HomeAssistantConnectionTest : ConnectionTesting() {
    private val toggleLamp = ServiceCall("light", "toggle", "light.lounge_lamp", jsonObjectOf())

    private fun failureOf(block: suspend () -> Unit): OpenFailure =
        assertThrows<OpenFailed> { runBlocking { block() } }.failure

    @Test
    fun `opens and gives the sample home with the user's name`() = runBlocking {
        val connection = open()
        assertEquals("TV", connection.userName)
        assertEquals(ConnectionStatus.CONNECTED, connection.status.value)
        val home = connection.home.value
        assertEquals("on", home.states["light.lounge_lamp"]?.state)
        assertTrue(home.entities.isNotEmpty())
        assertTrue(home.devices.isNotEmpty())
        assertTrue(home.areas.isNotEmpty())
    }

    @Test
    fun `home updates when an entity changes`() = runBlocking {
        val connection = open()
        fake.setState("light.lounge_lamp", "off")
        connection.home.waitFor { it.states["light.lounge_lamp"]?.state == "off" }
        Unit
    }

    @Test
    fun `unchanged entities keep the same objects across updates`() = runBlocking {
        val connection = open()
        val before = connection.home.value
        fake.setState("light.lounge_lamp", "off")
        val after = connection.home.waitFor { it.states["light.lounge_lamp"]?.state == "off" }
        assertSame(before.states["switch.tv_plug"], after.states["switch.tv_plug"])
        assertNotSame(before.states["light.lounge_lamp"], after.states["light.lounge_lamp"])
    }

    @Test
    fun `a call reaches Home Assistant with the entity as its target`() = runBlocking {
        val connection = open()
        connection.call(ServiceCall("light", "turn_on", "light.kitchen_ceiling", jsonObjectOf("brightness_pct" to 40)))
        val sent = fake.calls.single()
        assertEquals(JsonPrimitive("light"), sent["domain"])
        assertEquals(JsonPrimitive("turn_on"), sent["service"])
        assertEquals(jsonObjectOf("entity_id" to "light.kitchen_ceiling"), sent["target"])
        assertEquals(jsonObjectOf("brightness_pct" to 40), sent["service_data"])
        connection.home.waitFor { it.states["light.kitchen_ceiling"]?.state == "on" }
        Unit
    }

    @Test
    fun `a refused call throws Home Assistant's reason`() = runBlocking {
        val connection = open()
        val refused = assertThrows<CommandRefused> { runBlocking { connection.call(ServiceCall("light", "toggle", "light.nowhere", jsonObjectOf())) } }
        assertEquals("Entity light.nowhere was not found.", refused.reason)
    }

    @Test
    fun `a call while reconnecting fails at once and is never sent later`() = runBlocking {
        val connection = open(reconnectDelay = { 1_000 })
        fake.dropConnections()
        connection.status.waitFor { it == ConnectionStatus.RECONNECTING }

        val started = System.currentTimeMillis()
        val refused = assertThrows<CommandRefused> { runBlocking { connection.call(toggleLamp) } }
        assertEquals(COMMAND_REFUSED, refused.reason)
        assertTrue(System.currentTimeMillis() - started < 500)

        Thread.sleep(1_500)
        assertEquals(emptyList<Any>(), fake.calls)
    }

    @Test
    fun `an invalid token gives SIGN_IN_REJECTED`() {
        val signIn = fake.signInTokens().copy(accessToken = "made-up")
        assertEquals(OpenFailure.SIGN_IN_REJECTED, failureOf { open(signIn) })
        waitUntil("the socket to close") { fake.openSocketCount == 0 }
    }

    @Test
    fun `nothing listening gives CANNOT_REACH`() {
        val gone = FakeHomeAssistant()
        val signIn = gone.signInTokens()
        gone.close()
        assertEquals(OpenFailure.CANNOT_REACH, failureOf { open(signIn) })
    }

    @Test
    fun `an address that is not a web address gives CANNOT_REACH`() {
        val signIn = fake.signInTokens().copy(address = "http://192.168.1..100:8123")
        assertEquals(OpenFailure.CANNOT_REACH, failureOf { open(signIn) })
    }

    @Test
    fun `a broken registry list gives NOT_READY and leaves no socket open`() {
        fake.breakCommand("config/device_registry/list")
        assertEquals(OpenFailure.NOT_READY, failureOf { open() })
        waitUntil("the socket to close") { fake.openSocketCount == 0 }
    }

    @Test
    fun `a silent subscribe_entities times out as NOT_READY and leaves no socket open`() {
        fake.silenceCommand("subscribe_entities")
        val started = System.currentTimeMillis()
        assertEquals(OpenFailure.NOT_READY, failureOf { open(firstLoadTimeoutMs = 500) })
        assertTrue(System.currentTimeMillis() - started < 3_000)
        waitUntil("the socket to close") { fake.openSocketCount == 0 }
    }

    @Test
    fun `a drop between auth_ok and the first states fails the open and leaves nothing running`() = runBlocking {
        fake.silenceCommand("subscribe_entities")
        // On the shared scope, not runBlocking's own thread, which the waits below keep busy.
        val opening = scope.async { runCatching { open(firstLoadTimeoutMs = 4_000) } }
        waitUntil("the app to sign in") { fake.openSocketCount == 1 }
        Thread.sleep(200)
        fake.dropConnections()

        val failure = opening.await().exceptionOrNull()
        assertTrue(failure is OpenFailed, "expected OpenFailed, got $failure")
        Thread.sleep(500)
        assertEquals(0, fake.openSocketCount, "no new socket was opened")
    }

    @Test
    fun `several registry events cause one refresh`() = runBlocking {
        val connection = open(registryRefreshDelayMs = 300)
        val before = fake.home.registryListCount
        repeat(3) { fake.home.fireRegistryEvent("device_registry_updated") }
        Thread.sleep(800)
        assertEquals(before + 3, fake.home.registryListCount, "one refresh reads the three lists once")
        assertEquals(ConnectionStatus.CONNECTED, connection.status.value)
    }

    @Test
    fun `an expired sign-in is refreshed before connecting and onTokens is told`() = runBlocking {
        val now = System.currentTimeMillis()
        val expired = fake.signInTokens(expiresAt = now - 1)
        fake.expireAccessTokens()

        val connection = open(expired)

        assertEquals("TV", connection.userName)
        val saved = savedTokens.single()
        assertEquals(expired.refreshToken, saved.refreshToken)
        assertTrue(saved.accessToken != expired.accessToken)
        assertTrue(saved.expiresAt > now)
    }

    @Test
    fun `a refused refresh before connecting gives SIGN_IN_REJECTED`() {
        val expired = fake.signInTokens(expiresAt = System.currentTimeMillis() - 1)
        fake.rejectRefreshTokens()
        assertEquals(OpenFailure.SIGN_IN_REJECTED, failureOf { open(expired) })
    }

    @Test
    fun `registry events are subscribed to`() = runBlocking {
        open()
        val subscribed = fake.home.subscribedEventTypes
        assertEquals(setOf("entity_registry_updated", "device_registry_updated", "area_registry_updated"), subscribed.toSet())
        Unit
    }


    @Test
    fun `message ids always reach Home Assistant in increasing order, even when sent from many threads`() = runBlocking {
        repeat(5) {
            val connection = open()
            val jobs = (1..40).map { i ->
                scope.async {
                    runCatching { connection.call(ServiceCall("switch", if (i % 2 == 0) "turn_on" else "turn_off", "switch.tv_plug", jsonObjectOf())) }
                }
            }
            jobs.forEach { it.await() }
            connection.close()
        }
        assertEquals(emptyList<Int>(), fake.home.idErrors)
    }

    @Test
    fun `a command Home Assistant never answers is refused after the time limit`() = runBlocking {
        val connection = open(commandTimeoutMs = 300)
        fake.silenceCommand("call_service")
        val started = System.currentTimeMillis()
        val refused = assertThrows<CommandRefused> { runBlocking { connection.call(toggleLamp) } }
        assertEquals(COMMAND_REFUSED, refused.reason)
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }

    @Test
    fun `a failure saving fresh tokens does not stop the connection opening or reconnecting`() = runBlocking {
        val expired = fake.signInTokens(expiresAt = System.currentTimeMillis() - 1)
        val connection = open(expired, onTokens = { throw java.io.IOException("disk full") })
        assertEquals("TV", connection.userName)
        Unit
    }

    @Test
    fun `the connection sends keep-alive pings, so a dead link is noticed`() = runBlocking {
        val connection = open()
        assertTrue(pingIntervalOf(connection) in 1..30_000, "ping interval ${pingIntervalOf(connection)}")
    }


    @Test
    fun `asks whether a camera can stream, and gives its ICE servers`() = runBlocking {
        fake.home.webRtcCameras += "camera.front_door"
        val connection = open()
        assertTrue(connection.cameraCanStream("camera.front_door"))
        assertFalse(connection.cameraCanStream("camera.other"))
        assertEquals(listOf(IceServerConfig(listOf("stun:stun.example:3478"), null, null)), connection.webRtcIceServers("camera.front_door"))
    }

    @Test
    fun `an offer gets the session and the answer, and closing it unsubscribes`() {
        fake.home.webRtcCameras += "camera.front_door"
        val connection = runBlocking { open() }
        val messages = java.util.concurrent.CopyOnWriteArrayList<WebRtcMessage>()
        val offer = connection.webRtcOffer("camera.front_door", "v=0 offer") { messages += it }!!
        waitUntil("the answer") { messages.size == 2 }
        assertEquals(listOf(WebRtcMessage.Session("session-1"), WebRtcMessage.Answer("v=0 fake answer")), messages.toList())
        assertEquals("v=0 offer", textOrNull(fake.home.offers.single()["offer"]))
        offer.close()
    }
}
