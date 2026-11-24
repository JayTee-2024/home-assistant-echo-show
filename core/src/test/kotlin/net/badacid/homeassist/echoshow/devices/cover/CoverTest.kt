package net.badacid.homeassist.echoshow.devices.cover

import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/cover/cover.test.ts: blinds, curtains and shutters, never doors. */
class CoverTest {
    private val blind = sampleEntity("cover.lounge_blind") // open, 40%, features 15

    @Test
    fun `reads a blind`() {
        assertEquals(
            CoverView(isAvailable = true, state = "open", position = 40, canOpen = true, canClose = true, canStop = true, canSetPosition = true),
            readCover(blind),
        )
    }

    @Test
    fun `reads a cover that reports no position`() {
        assertNull(readCover(changed(blind, mapOf("current_position" to null, "supported_features" to 3))).position)
    }

    @Test
    fun `counts a cover in an unknown position as available, so Open and Close still work`() {
        assertTrue(readCover(changed(blind, emptyMap(), "unknown")).isAvailable)
        assertEquals("Position unknown", coverWords(readCover(changed(blind, emptyMap(), "unknown"))))
    }

    @Test
    fun `opens, closes and stops`() {
        assertEquals("open_cover", open(blind).call.service)
        assertEquals("opening", open(blind).predicted.state)
        assertEquals("close_cover", close(blind).call.service)
        assertEquals("closing", close(blind).predicted.state)
        assertEquals("stop_cover", stop(blind).call.service)
    }

    @Test
    fun `sets a position between 0 and 100`() {
        assertEquals(
            ServiceCall("cover", "set_cover_position", "cover.lounge_blind", jsonObjectOf("position" to 75)),
            setPosition(blind, 75).call,
        )
        assertEquals(jsonObjectOf("position" to 100), setPosition(blind, 140).call.data)
        assertEquals(75, readCover(setPosition(blind, 75).predicted).position)
    }

    @Test
    fun `supports only the kinds of cover that are safe`() {
        assertTrue(CoverLogic.supports(blind))
        assertTrue(CoverLogic.supports(changed(blind, mapOf("device_class" to "curtain"))))
        assertFalse(CoverLogic.supports(changed(blind, mapOf("device_class" to "garage"))))
        assertFalse(CoverLogic.supports(changed(blind, mapOf("device_class" to "door"))))
        assertFalse(CoverLogic.supports(changed(blind, mapOf("device_class" to "gate"))))
        assertFalse(CoverLogic.supports(changed(blind, mapOf("device_class" to null))))
    }

    @Test
    fun `opens its panel on OK, and is shown as on when not closed`() {
        assertEquals(OkAction.OpenPanel, CoverLogic.okAction(blind))
        assertTrue(CoverLogic.isActive(blind))
        assertFalse(CoverLogic.isActive(changed(blind, emptyMap(), "closed")))
    }

    @Test
    fun `sums itself up in words, with the position when it has one`() {
        assertEquals("Open · 40%", CoverLogic.summary(blind))
        assertEquals("Closing", CoverLogic.summary(changed(blind, mapOf("current_position" to null), "closing")))
    }
}
