package net.badacid.homeassist.echoshow.screens.home

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What each card shows. The LG app's DeviceCard.tsx, part-lines.ts and devices/(type)/Card.tsx are the reference. */
class CardContentTest {
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)
    private fun content(id: String, size: CardSize = CardSize.SMALL, at: HomeSnapshot = home) =
        cardContent(id, findDevice(buildDevices(at, DEVICE_LOGIC), id), at, size)

    private fun changed(entityId: String, state: String) =
        home.copy(states = home.states + (entityId to EntityState(entityId, state, home.states.getValue(entityId).attributes)))

    @Test
    fun `a light shows On and its brightness, with its colour, its name and room`() {
        val card = content("device:dev_lifx_lamp")
        assertEquals("On", card.face.state)
        assertEquals(listOf("71%"), card.face.details)
        assertTrue(card.face.colour != null)
        assertEquals("Lounge lamp", card.name)
        assertEquals("Living room", card.room)
        assertTrue(card.isActive)
        assertEquals("light", card.typeId)
    }

    @Test
    fun `a thermostat shows the room temperature large, then the target and what it is doing`() {
        val card = content("device:dev_tado", CardSize.WIDE)
        assertEquals("20.5°", card.face.big)
        assertEquals(listOf("Set to 21° · Heating"), card.face.details)
    }

    @Test
    fun `a speaker shows what is playing`() {
        val card = content("device:dev_sonos", CardSize.WIDE)
        assertEquals("Clair de Lune", card.face.state)
        assertEquals(listOf("Claude Debussy", "Playing"), card.face.details)
    }

    @Test
    fun `a doorbell is a picture, with its other parts as lines, two on a large card`() {
        val card = content("device:dev_ring", CardSize.LARGE)
        assertTrue(card.face.isPicture)
        assertEquals(2, card.extraLines.size)
        assertTrue(card.extraLines[0].startsWith("Ding "))
        assertEquals(1, content("device:dev_ring", CardSize.SMALL).extraLines.size)
    }

    @Test
    fun `an unavailable device says Unavailable and is not active`() {
        val card = content("device:dev_noname")
        assertEquals("Unavailable", card.face.state)
        assertTrue(card.isUnavailable)
        assertFalse(card.isActive)
    }

    @Test
    fun `a device Home Assistant no longer has says so and offers removal`() {
        val card = content("device:gone")
        assertTrue(card.isMissing)
        assertEquals("Not available", card.name)
        assertEquals("No longer in Home Assistant", card.room)
        assertEquals(listOf("Tap to remove"), card.extraLines)
    }

    @Test
    fun `a device with nothing to show yet says so`() {
        val card = content("device:dev_bridge")
        assertEquals("Tado bridge", card.name)
        assertEquals("Nothing here can be shown yet", card.room)
        assertFalse(card.isMissing)
    }

    @Test
    fun `a switch, a sensor, a cover, a fan, a vacuum and a button each show their state`() {
        assertEquals("On", content("device:dev_plug_tv").face.state)
        assertEquals("Open", content("device:dev_blind").face.state)
        assertEquals(listOf("40%"), content("device:dev_blind").face.details)
        assertEquals("On", content("device:dev_fan").face.state)
        assertEquals("Docked", content("device:dev_vacuum").face.state)
        assertEquals("Activate", content("entity:scene.movie_night").face.state)
        assertEquals("Off", content("device:dev_plug_kettle").face.state)
        assertNull(content("device:dev_plug_kettle").face.colour)
        assertTrue(devices.isNotEmpty())
    }

    @Test
    fun `a sensor shows its reading with the unit`() {
        val sensorHome = home.copy(entities = home.entities.filter { it.entityId != "climate.living_room" })
        val card = content("device:dev_tado", at = sensorHome)
        assertEquals("48", card.face.state)
        assertEquals("%", card.face.unit)
        assertTrue(changed("light.lounge_lamp", "off").states.isNotEmpty())
    }
}
