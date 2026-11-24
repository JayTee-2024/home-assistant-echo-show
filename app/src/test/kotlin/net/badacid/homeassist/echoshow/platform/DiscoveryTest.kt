package net.badacid.homeassist.echoshow.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoveryTest {
    @Test
    fun `internal_url wins over host and port`() {
        assertEquals("http://192.168.1.50:8123", addressFromService("192.168.1.50", 8123, mapOf("internal_url" to "http://192.168.1.50:8123/")))
        assertEquals("http://homeassistant.lan:8123", addressFromService("192.168.1.50", 8123, mapOf("internal_url" to "http://homeassistant.lan:8123")))
    }

    @Test
    fun `without internal_url the host and port are used, and base_url as a fallback`() {
        assertEquals("http://192.168.1.50:8123", addressFromService("192.168.1.50", 8123, emptyMap()))
        assertEquals("http://[fe80::1]:8123", addressFromService("fe80::1", 8123, emptyMap()))
        assertEquals("http://10.0.0.2:8123", addressFromService(null, 0, mapOf("base_url" to "http://10.0.0.2:8123")))
    }

    @Test
    fun `an internal_url that is not http is ignored`() {
        assertEquals("http://192.168.1.50:8123", addressFromService("192.168.1.50", 8123, mapOf("internal_url" to "junk")))
    }

    @Test
    fun `a dot-local internal_url is skipped, as Android 11 cannot look it up`() {
        assertEquals("http://192.168.1.50:8123", addressFromService("192.168.1.50", 8123, mapOf("internal_url" to "http://homeassistant.local:8123")))
        assertEquals(null, addressFromService(null, 0, mapOf("base_url" to "http://homeassistant.local:8123")))
    }
}
