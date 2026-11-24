package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/address.test.ts. */
class AddressTest {
    private fun url(input: String) = normaliseAddress(input)

    @Test
    fun `adds http and port 8123 when neither is typed`() {
        assertEquals(AddressResult.Ok("http://192.168.1.100:8123"), url("192.168.1.100"))
    }

    @Test
    fun `keeps a typed port`() {
        assertEquals(AddressResult.Ok("http://homeassistant.local:8123"), url("homeassistant.local:8123"))
        assertEquals(AddressResult.Ok("http://192.168.1.100:8080"), url("192.168.1.100:8080"))
    }

    @Test
    fun `does not add 8123 when port 80 was typed, even though http addresses omit it`() {
        assertEquals(AddressResult.Ok("http://192.168.1.100"), url("192.168.1.100:80"))
    }

    @Test
    fun `trims spaces and lower-cases the host`() {
        assertEquals(AddressResult.Ok("http://homeassistant.local:8123"), url("  HomeAssistant.LOCAL  "))
    }

    @Test
    fun `does not add a port when the scheme was typed`() {
        assertEquals(AddressResult.Ok("http://192.168.1.100"), url("http://192.168.1.100"))
        assertEquals(AddressResult.Ok("https://example.ui.nabu.casa"), url("https://example.ui.nabu.casa/"))
    }

    @Test
    fun `drops any path, query and trailing slash`() {
        assertEquals(AddressResult.Ok("http://192.168.1.100:8123"), url("http://192.168.1.100:8123/lovelace/0?x=1"))
    }

    @Test
    fun `reports empty input`() {
        assertEquals(AddressResult.Empty, url(""))
        assertEquals(AddressResult.Empty, url("   "))
    }

    @Test
    fun `reports input that is not an http or https address`() {
        for (bad in listOf("http://", "a b", "ftp://host", "ht!tp://x")) {
            assertEquals(AddressResult.Invalid, url(bad), bad)
        }
    }

    @Test
    fun `accepts a scheme typed in capitals and a host with an underscore`() {
        assertEquals(AddressResult.Ok("https://home_assistant:8443"), url("HTTPS://Home_Assistant:8443"))
    }

    @Test
    fun `drops port 443 typed with https`() {
        assertEquals(AddressResult.Ok("https://example.ui.nabu.casa"), url("https://example.ui.nabu.casa:443"))
    }

    @Test
    fun `the default address is Home Assistant's own name`() {
        assertEquals("homeassistant.local:8123", DEFAULT_ADDRESS)
    }
}
