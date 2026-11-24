package net.badacid.homeassist.echoshow.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoveryTest {
    @Test
    fun `only the address the Echo itself found is used, never one the announcement names`() {
        assertEquals("http://192.168.1.50:8123", addressFromService("192.168.1.50", 8123))
        assertEquals("http://[fe80::1]:8123", addressFromService("fe80::1", 8123))
    }

    @Test
    fun `nothing usable gives nothing`() {
        assertEquals(null, addressFromService(null, 8123))
        assertEquals(null, addressFromService("192.168.1.50", 0))
    }
}
