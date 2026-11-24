package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.runBlocking
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.signInWithToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SignOutTest : ConnectionTesting() {
    @Test
    fun `sign out revokes the refresh token and closes`() = runBlocking {
        val signIn = fake.signInTokens()
        val connection = open(signIn)
        connection.signOut()
        assertEquals(listOf(signIn.refreshToken), fake.revokedTokens)
        waitUntil("the socket to close") { fake.openSocketCount == 0 }
    }

    @Test
    fun `sign out revokes the latest tokens after a refresh`() = runBlocking {
        val signIn = fake.signInTokens(expiresAt = System.currentTimeMillis() - 1)
        val connection = open(signIn)
        connection.signOut()
        assertEquals(listOf(signIn.refreshToken), fake.revokedTokens)
    }

    @Test
    fun `sign out with a long-lived token revokes nothing`() = runBlocking {
        val connection = open(signInWithToken(fake.address, fake.signInTokens().accessToken))
        connection.signOut()
        assertEquals(emptyList<String>(), fake.revokedTokens)
        waitUntil("the socket to close") { fake.openSocketCount == 0 }
    }

    @Test
    fun `sign out when Home Assistant is down still closes and does not throw`() = runBlocking {
        val connection = open()
        fake.close()
        connection.signOut()
        val status = connection.status.value
        fake.setState("light.lounge_lamp", "off")
        Thread.sleep(300)
        assertSame(status, connection.status.value)
    }

    @Test
    fun `after close nothing changes and no socket is open`() = runBlocking {
        val connection = open()
        val home = connection.home.value
        connection.close()
        waitUntil("the socket to close") { fake.openSocketCount == 0 }

        fake.setState("light.lounge_lamp", "off")
        fake.dropConnections()
        Thread.sleep(500)
        assertSame(home, connection.home.value)
        assertEquals(ConnectionStatus.CONNECTED, connection.status.value)
        assertEquals(0, fake.openSocketCount)
    }

    @Test
    fun `close twice is harmless`() = runBlocking {
        val connection = open()
        connection.close()
        connection.close()
    }
}
