package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReconnectTest {
    @Test
    fun `waits 0, 1, 2, 3, 4, 5, 5 seconds`() {
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L, 5000L), (0..6).map { reconnectDelayMs(it) })
    }

    @Test
    fun `counts a negative attempt as the first`() {
        assertEquals(0L, reconnectDelayMs(-3))
    }
}
