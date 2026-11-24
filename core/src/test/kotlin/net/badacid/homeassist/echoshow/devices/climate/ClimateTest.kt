package net.badacid.homeassist.echoshow.devices.climate

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.jsonObjectOf
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/climate/climate.test.ts. */
class ClimateTest {
    private val tado = sampleEntity("climate.living_room") // heat, heating, 20.5 → 21, range 5–25, step 0.1

    @Nested
    inner class ReadClimate {
        @Test
        fun `reads a thermostat`() {
            val view = readClimate(tado)
            assertEquals("heat", view.hvacMode)
            assertEquals("heating", view.hvacAction)
            assertEquals(20.5, view.currentTemperature)
            assertEquals(21.0, view.targetTemperature)
            assertEquals(5.0, view.minTemp)
            assertEquals(25.0, view.maxTemp)
            assertEquals(0.1, view.step)
            assertEquals(listOf("off", "auto", "heat"), view.hvacModes)
            assertEquals(listOf("home", "away"), view.presetModes)
            assertEquals("home", view.presetMode)
            assertTrue(view.canSetTarget)
        }

        @Test
        fun `fills in a range and a step when the thermostat reports none`() {
            val view = readClimate(changed(tado, mapOf("min_temp" to null, "max_temp" to null, "target_temp_step" to null)))
            assertEquals(7.0, view.minTemp)
            assertEquals(35.0, view.maxTemp)
            assertEquals(0.5, view.step)
        }

        @Test
        fun `reads a thermostat that is off, with no target`() {
            val view = readClimate(changed(tado, mapOf("temperature" to null, "hvac_action" to "idle"), "off"))
            assertNull(view.targetTemperature)
            assertEquals("off", view.hvacMode)
        }

        @Test
        fun `cannot set a target when the thermostat does not say it can`() {
            assertFalse(readClimate(changed(tado, mapOf("supported_features" to 0))).canSetTarget)
        }

        @Test
        fun `uses the default step for a step of zero or less`() {
            assertEquals(0.5, readClimate(changed(tado, mapOf("target_temp_step" to 0))).step)
            assertEquals(0.5, readClimate(changed(tado, mapOf("target_temp_step" to -1))).step)
        }
    }

    @Nested
    inner class StepTarget {
        private val view = readClimate(changed(tado, mapOf("target_temp_step" to 0.5)))

        @Test
        fun `moves one step up or down`() {
            assertEquals(21.5, stepTarget(view, 21.0, 1))
            assertEquals(20.5, stepTarget(view, 21.0, -1))
        }

        @Test
        fun `stays within the range`() {
            assertEquals(25.0, stepTarget(view, 25.0, 1))
            assertEquals(5.0, stepTarget(view, 5.0, -1))
            assertEquals(25.0, stepTarget(view, 40.0, -1))
        }

        @Test
        fun `lands on a whole step from a value between steps`() {
            assertEquals(21.5, stepTarget(view, 21.3, 1))
            assertEquals(21.0, stepTarget(view, 21.3, -1))
        }

        @Test
        fun `starts from the current temperature, or else the minimum, when there is no target`() {
            assertEquals(21.0, stepTarget(view, null, 1))
            val noRoom = readClimate(changed(tado, mapOf("current_temperature" to null, "target_temp_step" to 0.5)))
            assertEquals(5.5, stepTarget(noRoom, null, 1))
        }

        @Test
        fun `keeps every decimal a step needs, such as 0 point 25`() {
            val quarter = readClimate(changed(tado, mapOf("target_temp_step" to 0.25)))
            assertEquals(21.25, stepTarget(quarter, 21.0, 1))
            assertEquals(21.5, stepTarget(quarter, 21.25, 1))
        }

        @Test
        fun `avoids the floating-point dust of steps like 0 point 1`() {
            assertEquals(21.1, stepTarget(readClimate(tado), 21.0, 1))
            assertEquals(21.2, stepTarget(readClimate(tado), 21.1, 1))
        }
    }

    @Test
    fun `formatTemperature writes up to two decimals and a degree sign, or a dash`() {
        assertEquals("21°", formatTemperature(21.0))
        assertEquals("20.5°", formatTemperature(20.5))
        assertEquals("20.33°", formatTemperature(20.333))
        assertEquals("–", formatTemperature(null))
    }

    @Nested
    inner class ClimateActions {
        @Test
        fun `sets the target temperature`() {
            val action = setTarget(tado, 22.0)
            assertEquals(ServiceCall("climate", "set_temperature", "climate.living_room", jsonObjectOf("temperature" to 22)), action.call)
            assertEquals(22.0, readClimate(action.predicted).targetTemperature)
            assertEquals(jsonObjectOf("temperature" to 21.5), setTarget(tado, 21.5).call.data)
        }

        @Test
        fun `sets the mode and the preset`() {
            assertEquals(
                ServiceCall("climate", "set_hvac_mode", "climate.living_room", jsonObjectOf("hvac_mode" to "off")),
                setHvacMode(tado, "off").call,
            )
            assertEquals("off", setHvacMode(tado, "off").predicted.state)
            assertEquals(jsonObjectOf("preset_mode" to "away"), setPreset(tado, "away").call.data)
            assertEquals("away", readClimate(setPreset(tado, "away").predicted).presetMode)
        }
    }

    /** What the card shows straight after a press, before Home Assistant reports. */
    @Nested
    inner class PredictedDoing {
        private val idleAt14 = changed(tado, mapOf("temperature" to 14, "current_temperature" to 22.1, "hvac_action" to "idle"))
        private val off = changed(tado, mapOf("temperature" to null, "hvac_action" to "off"), "off")
        private fun doing(action: net.badacid.homeassist.echoshow.devices.Action) = readClimate(action.predicted).hvacAction

        @Test
        fun `a target above the room temperature shows as heating, and the card is on`() {
            val predicted = setTarget(idleAt14, 23.0).predicted
            assertEquals("heating", readClimate(predicted).hvacAction)
            assertTrue(ClimateLogic.isActive(predicted))
        }

        @Test
        fun `a target at or below the room temperature shows as idle`() {
            assertEquals("idle", doing(setTarget(tado, 19.0)))
            assertEquals("idle", doing(setTarget(tado, 20.5)))
        }

        @Test
        fun `switching off shows as off, and switching on no longer says Off`() {
            assertEquals("off", doing(setHvacMode(tado, "off")))
            assertEquals("idle", doing(setHvacMode(off, "heat")))
            assertEquals("idle", doing(setHvacMode(idleAt14, "heat")))
            assertEquals("heating", doing(setHvacMode(changed(tado, emptyMap(), "off"), "heat")))
        }

        @Test
        fun `a target set while off does not pretend the heating is on`() {
            assertEquals("off", doing(setTarget(off, 23.0)))
        }

        @Test
        fun `a thermostat that never says what it is doing gets no guess`() {
            val quiet = net.badacid.homeassist.echoshow.core.EntityState(tado.entityId, tado.state, kotlinx.serialization.json.JsonObject(tado.attributes - "hvac_action"))
            assertEquals(null, doing(setTarget(quiet, 23.0)))
            assertEquals(null, doing(setHvacMode(quiet, "off")))
        }
    }

    @Nested
    inner class TheHeatingType {
        @Test
        fun `is a wide card that opens its panel on OK`() {
            assertEquals(CardSize.WIDE, ClimateLogic.cardSize)
            assertEquals(OkAction.OpenPanel, ClimateLogic.okAction(tado))
            assertEquals(OkAction.Nothing, ClimateLogic.okAction(changed(tado, emptyMap(), "unavailable")))
        }

        @Test
        fun `is shown as on whenever it is switched on, heating or idle, and off when off or unavailable`() {
            assertTrue(ClimateLogic.isActive(tado))
            assertTrue(ClimateLogic.isActive(changed(tado, mapOf("hvac_action" to "idle"))))
            assertTrue(ClimateLogic.isActive(changed(tado, mapOf("hvac_action" to "idle"), "auto")))
            assertFalse(ClimateLogic.isActive(changed(tado, mapOf("hvac_action" to "off"), "off")))
            assertFalse(ClimateLogic.isActive(changed(tado, mapOf("hvac_action" to null), "off")))
            assertFalse(ClimateLogic.isActive(changed(tado, emptyMap(), "unavailable")))
        }

        @Test
        fun `sums itself up as the current and target temperatures`() {
            assertEquals("20.5° → 21°", ClimateLogic.summary(tado))
            assertEquals("20.5° · Off", ClimateLogic.summary(changed(tado, mapOf("temperature" to null), "off")))
        }
    }
}

class ClimateBadRangeTest {
    @Test
    fun `stepTarget does not fail when the minimum is above the maximum`() {
        // A thermostat in Fahrenheit that reports only min_temp, so the default maximum of 35 applies.
        val view = readClimate(changed(sampleEntity("climate.living_room"), mapOf("min_temp" to 45, "max_temp" to null, "target_temp_step" to 1)))
        assertEquals(45.0, stepTarget(view, 60.0, 1))
        assertEquals(45.0, stepTarget(view, 60.0, -1))
    }
}
