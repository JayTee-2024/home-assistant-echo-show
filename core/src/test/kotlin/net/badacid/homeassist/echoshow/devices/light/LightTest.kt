package net.badacid.homeassist.echoshow.devices.light

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.core.jsonValue
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/light/light.test.ts. */
class LightTest {
    private val lamp = sampleEntity("light.lounge_lamp") // colour bulb, on, brightness 180
    private val ceiling = sampleEntity("light.kitchen_ceiling") // colour bulb, off
    private val hall = sampleEntity("light.hall_light") // dimmable white bulb, on, brightness 128
    private val onOffOnly = changed(
        hall,
        mapOf("supported_color_modes" to listOf("onoff"), "color_mode" to "onoff", "brightness" to null),
    )

    @Nested
    inner class ReadLight {
        @Test
        fun `reads a colour bulb that is on`() {
            val view = readLight(lamp)
            assertTrue(view.isOn)
            assertEquals(71, view.brightnessPercent)
            assertTrue(view.canDim)
            assertTrue(view.canSetWhite)
            assertTrue(view.canSetColour)
            assertEquals(listOf(255, 180, 84), view.rgb)
            assertEquals(1500, view.minKelvin)
            assertEquals(9000, view.maxKelvin)
            assertEquals(listOf("effect_colorloop", "effect_pulse", "effect_stop"), view.effects)
            assertNull(view.effect)
        }

        @Test
        fun `reads a bulb that is off - no brightness, but still what it can do`() {
            val view = readLight(ceiling)
            assertFalse(view.isOn)
            assertNull(view.brightnessPercent)
            assertTrue(view.canDim)
            assertTrue(view.canSetColour)
            assertNull(view.rgb)
        }

        @Test
        fun `reads a white dimmable bulb - dims, but no white range and no colour`() {
            val view = readLight(hall)
            assertEquals(50, view.brightnessPercent)
            assertTrue(view.canDim)
            assertFalse(view.canSetWhite)
            assertFalse(view.canSetColour)
        }

        @Test
        fun `reads a plain on-off light - nothing but on and off`() {
            val view = readLight(onOffOnly)
            assertTrue(view.isOn)
            assertFalse(view.canDim)
            assertNull(view.brightnessPercent)
            assertEquals(emptyList<String>(), view.effects)
        }

        @Test
        fun `reads a light with no attributes at all without failing`() {
            val bare = EntityState("light.bare", "on", JsonObject(emptyMap()))
            assertEquals(
                LightView(
                    isOn = true, isAvailable = true, brightnessPercent = null, canDim = false,
                    canSetWhite = false, canSetColour = false, whiteKelvin = null, minKelvin = 2000,
                    maxKelvin = 6500, rgb = null, effects = emptyList(), effect = null,
                ),
                readLight(bare),
            )
        }

        @Test
        fun `never shows 0 percent for a light that is on`() {
            assertEquals(1, readLight(changed(lamp, mapOf("brightness" to 1))).brightnessPercent)
        }

        @Test
        fun `ignores a colour that is not three numbers`() {
            assertNull(readLight(changed(lamp, mapOf("rgb_color" to listOf(255, "x", 0)))).rgb)
            assertNull(readLight(changed(lamp, mapOf("rgb_color" to listOf(255, 0)))).rgb)
        }

        @Test
        fun `reads an unavailable light as off and not available`() {
            val view = readLight(changed(lamp, emptyMap(), "unavailable"))
            assertFalse(view.isOn)
            assertFalse(view.isAvailable)
        }
    }

    @Test
    fun `whiteSteps gives five whites from warm to cool across the bulb's range`() {
        assertEquals(listOf(1500, 3375, 5250, 7125, 9000), whiteSteps(readLight(lamp)))
    }

    @Nested
    inner class NextBrightness {
        @Test
        fun `moves to the next multiple of ten`() {
            assertEquals(80, nextBrightness(71, 1))
            assertEquals(70, nextBrightness(71, -1))
            assertEquals(80, nextBrightness(70, 1))
            assertEquals(60, nextBrightness(70, -1))
        }

        @Test
        fun `stays between 1 and 100`() {
            assertEquals(100, nextBrightness(100, 1))
            assertEquals(1, nextBrightness(10, -1))
            assertEquals(1, nextBrightness(1, -1))
            assertEquals(10, nextBrightness(1, 1))
        }

        @Test
        fun `counts a light that is off as zero`() {
            assertEquals(10, nextBrightness(null, 1))
            assertEquals(1, nextBrightness(null, -1))
        }
    }

    @Nested
    inner class LightActions {
        @Test
        fun `toggles, predicting the opposite state`() {
            val action = toggle(lamp)
            assertEquals("light", action.call.domain)
            assertEquals("toggle", action.call.service)
            assertEquals("light.lounge_lamp", action.call.entityId)
            assertEquals(JsonObject(emptyMap()), action.call.data)
            assertEquals("off", action.predicted.state)
            assertEquals("on", toggle(ceiling).predicted.state)
        }

        @Test
        fun `sets brightness as a percentage, turning the light on`() {
            val action = setBrightness(ceiling, 40)
            assertEquals("turn_on", action.call.service)
            assertEquals("light.kitchen_ceiling", action.call.entityId)
            assertEquals(jsonObjectOf("brightness_pct" to 40), action.call.data)
            assertEquals("on", action.predicted.state)
            assertEquals(40, readLight(action.predicted).brightnessPercent)
        }

        @Test
        fun `keeps brightness between 1 and 100`() {
            assertEquals(jsonObjectOf("brightness_pct" to 1), setBrightness(lamp, 0).call.data)
            assertEquals(jsonObjectOf("brightness_pct" to 100), setBrightness(lamp, 250).call.data)
        }

        @Test
        fun `sets a white, kept inside the bulb's own range`() {
            assertEquals(jsonObjectOf("color_temp_kelvin" to 3000), setWhite(lamp, 3000).call.data)
            assertEquals(jsonObjectOf("color_temp_kelvin" to 1500), setWhite(lamp, 500).call.data)
            assertEquals(jsonObjectOf("color_temp_kelvin" to 9000), setWhite(lamp, 20000).call.data)
            assertEquals(3000, readLight(setWhite(lamp, 3000).predicted).whiteKelvin)
            assertNull(readLight(setWhite(lamp, 3000).predicted).rgb)
        }

        @Test
        fun `sets a colour`() {
            val action = setColour(lamp, listOf(0, 128, 255))
            assertEquals("turn_on", action.call.service)
            assertEquals(jsonObjectOf("rgb_color" to listOf(0, 128, 255)), action.call.data)
            assertEquals(listOf(0, 128, 255), readLight(action.predicted).rgb)
        }

        @Test
        fun `sets an effect`() {
            val action = setEffect(lamp, "effect_pulse")
            assertEquals(jsonObjectOf("effect" to "effect_pulse"), action.call.data)
            assertEquals("effect_pulse", readLight(action.predicted).effect)
        }

        @Test
        fun `leaves the entity it was given unchanged`() {
            setBrightness(lamp, 10)
            setColour(lamp, listOf(1, 2, 3))
            assertEquals(jsonValue(180), lamp.attributes["brightness"])
            assertEquals(jsonValue(listOf(255, 180, 84)), lamp.attributes["rgb_color"])
        }
    }

    @Nested
    inner class TheLightType {
        @Test
        fun `toggles on OK, and does nothing when the light is unavailable`() {
            assertEquals(OkAction.Run(toggle(lamp)), LightLogic.okAction(lamp))
            assertEquals(OkAction.Nothing, LightLogic.okAction(changed(lamp, emptyMap(), "unavailable")))
        }

        @Test
        fun `is shown as on only when it is on`() {
            assertTrue(LightLogic.isActive(lamp))
            assertFalse(LightLogic.isActive(ceiling))
        }

        @Test
        fun `sums itself up as its state and brightness`() {
            assertEquals("On · 71%", LightLogic.summary(lamp))
            assertEquals("Off", LightLogic.summary(ceiling))
            assertEquals("On", LightLogic.summary(onOffOnly))
            assertEquals("Unavailable", LightLogic.summary(changed(lamp, emptyMap(), "unavailable")))
        }

        @Test
        fun `is a small card that leads at 50`() {
            assertEquals("light", LightLogic.id)
            assertEquals(listOf("light"), LightLogic.domains)
            assertEquals(50, LightLogic.mainOrder)
        }
    }
}
