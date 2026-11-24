package net.badacid.homeassist.echoshow.devices.fan

import kotlinx.serialization.json.JsonObject
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

/** Port of the LG app's src/devices/fan/fan.test.ts. */
class FanTest {
    private val fan = sampleEntity("fan.bedroom_fan") // on, 50%, step 25, features 59

    @Test
    fun `reads a fan`() {
        val view = readFan(fan)
        assertTrue(view.isOn)
        assertEquals(50, view.speedPercent)
        assertEquals(25.0, view.step)
        assertTrue(view.canSetSpeed)
        assertTrue(view.canOscillate)
        assertFalse(view.isOscillating)
        assertEquals(listOf("breeze"), view.presets)
    }

    @Test
    fun `reads a fan that is on with no speed, or has no speed control`() {
        val view = readFan(changed(fan, mapOf("percentage" to null, "supported_features" to 0)))
        assertTrue(view.isOn)
        assertNull(view.speedPercent)
        assertFalse(view.canSetSpeed)
    }

    @Test
    fun `uses steps of at least 10 percent, so a fan with 100 speeds is not tedious`() {
        assertEquals(10.0, readFan(changed(fan, mapOf("percentage_step" to 1))).step)
        assertEquals(10.0, readFan(changed(fan, mapOf("percentage_step" to null))).step)
    }

    @Test
    fun `nextSpeed moves in the fan's own step, between 0 and 100`() {
        assertEquals(75, nextSpeed(50, 25.0, 1))
        assertEquals(25, nextSpeed(50, 25.0, -1))
        assertEquals(100, nextSpeed(100, 25.0, 1))
        assertEquals(25, nextSpeed(null, 25.0, 1))
        assertEquals(0, nextSpeed(25, 25.0, -1))
    }

    @Test
    fun `moves a 3-speed fan up and down one speed from each reported value`() {
        val third = 100.0 / 3
        assertEquals(67, nextSpeed(33, third, 1))
        assertEquals(100, nextSpeed(66, third, 1))
        assertEquals(67, nextSpeed(100, third, -1))
        assertEquals(33, nextSpeed(67, third, -1))
        assertEquals(0, nextSpeed(33, third, -1))
    }

    @Test
    fun `moves a 6-speed fan one speed at a time`() {
        val sixth = 100.0 / 6
        assertEquals(50, nextSpeed(33, sixth, 1))
        assertEquals(100, nextSpeed(83, sixth, 1))
        assertEquals(67, nextSpeed(83, sixth, -1))
    }

    @Test
    fun `toggles, sets the speed, oscillates and sets a preset`() {
        assertEquals(ServiceCall("fan", "toggle", "fan.bedroom_fan", JsonObject(emptyMap())), toggle(fan).call)
        assertEquals("off", toggle(fan).predicted.state)
        assertEquals(jsonObjectOf("percentage" to 75), setSpeed(fan, 75).call.data)
        assertEquals("off", setSpeed(fan, 0).predicted.state)
        assertEquals(jsonObjectOf("oscillating" to true), oscillate(fan, true).call.data)
        assertEquals(
            ServiceCall("fan", "set_preset_mode", "fan.bedroom_fan", jsonObjectOf("preset_mode" to "breeze")),
            setPreset(fan, "breeze").call,
        )
    }

    @Test
    fun `toggles on OK, even with no speed control`() {
        assertEquals(OkAction.Run(toggle(fan)), FanLogic.okAction(fan))
        val plain = changed(fan, mapOf("supported_features" to 0, "percentage" to null))
        assertEquals(OkAction.Run(toggle(plain)), FanLogic.okAction(plain))
        assertEquals(OkAction.Nothing, FanLogic.okAction(changed(fan, emptyMap(), "unavailable")))
    }

    @Test
    fun `is shown as on when on, and sums itself up with its speed`() {
        assertTrue(FanLogic.isActive(fan))
        assertEquals("On · 50%", FanLogic.summary(fan))
        assertEquals("Off", FanLogic.summary(changed(fan, emptyMap(), "off")))
    }
}
