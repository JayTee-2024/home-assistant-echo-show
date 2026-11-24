package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.runBlocking
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.signInWithToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class ReconnectTest : ConnectionTesting() {
    /** A clock the test can move forward. */
    private val offset = AtomicLong(0)
    private val clock = { System.currentTimeMillis() + offset.get() }

    private fun attempts() = synchronized(fake.socketAttempts) { fake.socketAttempts.size }

    @Test
    fun `a drop goes to RECONNECTING and back to CONNECTED`() = runBlocking {
        val connection = open()
        fake.dropConnections()
        connection.status.waitFor { it == ConnectionStatus.RECONNECTING }
        connection.status.waitFor { it == ConnectionStatus.CONNECTED }
        assertEquals(1, fake.openSocketCount)
    }

    @Test
    fun `after reconnecting, changes made while away are shown`() = runBlocking {
        val connection = open()
        fake.goDown()
        connection.status.waitFor { it == ConnectionStatus.RECONNECTING }
        fake.home.setState("light.lounge_lamp", "off")
        fake.comeBack()

        connection.status.waitFor { it == ConnectionStatus.CONNECTED }
        connection.home.waitFor { it.states["light.lounge_lamp"]?.state == "off" }
        Unit
    }

    @Test
    fun `changes after reconnecting keep arriving`() = runBlocking {
        val connection = open()
        fake.dropConnections()
        connection.status.waitFor { it == ConnectionStatus.RECONNECTING }
        connection.status.waitFor { it == ConnectionStatus.CONNECTED }
        fake.setState("switch.tv_plug", "off")
        connection.home.waitFor { it.states["switch.tv_plug"]?.state == "off" }
        Unit
    }

    @Test
    fun `retries with growing waits while Home Assistant is down`() = runBlocking {
        val connection = open(reconnectDelay = { attempt -> attempt * 150L })
        val before = attempts()
        fake.goDown()
        waitUntil("four retries") { attempts() >= before + 4 }
        val times = synchronized(fake.socketAttempts) { fake.socketAttempts.drop(before).take(4) }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue(gaps[1] > gaps[0] && gaps[2] > gaps[1], "gaps should grow: $gaps")
        assertEquals(ConnectionStatus.RECONNECTING, connection.status.value)

        fake.comeBack()
        connection.status.waitFor { it == ConnectionStatus.CONNECTED }
        Unit
    }

    @Test
    fun `an expired token is refreshed before reconnecting`() = runBlocking {
        val connection = open(clock = clock)
        fake.expireAccessTokens()
        offset.set(3_600_000)
        fake.dropConnections()

        connection.status.waitFor { it == ConnectionStatus.CONNECTED && savedTokens.isNotEmpty() }
        assertEquals(1, savedTokens.size)
        assertTrue(savedTokens[0].expiresAt > clock())
    }

    @Test
    fun `a refused refresh gives SIGN_IN_REJECTED and stops retrying`() = runBlocking {
        val connection = open(clock = clock)
        fake.expireAccessTokens()
        fake.rejectRefreshTokens()
        offset.set(3_600_000)
        fake.dropConnections()

        connection.status.waitFor { it == ConnectionStatus.SIGN_IN_REJECTED }
        val after = attempts()
        Thread.sleep(800)
        assertEquals(after, attempts(), "no more attempts after a rejection")
    }

    @Test
    fun `a token refused by the socket on reconnect gives SIGN_IN_REJECTED`() = runBlocking {
        val token = fake.signInTokens().accessToken
        val connection = open(signInWithToken(fake.address, token))
        fake.expireAccessTokens()
        fake.dropConnections()
        connection.status.waitFor { it == ConnectionStatus.SIGN_IN_REJECTED }
        Unit
    }

    @Test
    fun `close during a wait stops all retries`() = runBlocking {
        val connection = open(reconnectDelay = { 400 })
        fake.dropConnections()
        connection.status.waitFor { it == ConnectionStatus.RECONNECTING }
        val before = attempts()
        connection.close()
        Thread.sleep(1_000)
        assertEquals(before, attempts())
        assertEquals(0, fake.openSocketCount)
    }
}
