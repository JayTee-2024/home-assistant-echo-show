package net.badacid.homeassist.echoshow.devices.binarysensor

import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Port of the LG app's src/devices/binary-sensor/binary-sensor.test.ts. */
class BinarySensorTest {
    private val motion = sampleEntity("binary_sensor.front_door_motion") // off

    private fun words(deviceClass: String?) = listOf(
        readBinarySensor(changed(motion, mapOf("device_class" to deviceClass), "on")).words,
        readBinarySensor(changed(motion, mapOf("device_class" to deviceClass), "off")).words,
    )

    @Test
    fun `reads on and off`() {
        assertFalse(readBinarySensor(motion).isOn)
        assertTrue(readBinarySensor(changed(motion, emptyMap(), "on")).isOn)
    }

    @ParameterizedTest(name = "puts a {0} sensor into words")
    @CsvSource(
        "door, Open, Closed", "window, Open, Closed", "opening, Open, Closed", "garage_door, Open, Closed",
        "motion, Detected, Clear", "occupancy, Detected, Clear", "presence, Detected, Clear",
        "smoke, Detected, Clear", "gas, Detected, Clear", "problem, Detected, Clear", "safety, Detected, Clear",
        "battery, Low, Normal", "connectivity, Connected, Disconnected", "moisture, Wet, Dry",
        "lock, Unlocked, Locked",
    )
    fun `puts a sensor into words`(deviceClass: String, whenOn: String, whenOff: String) {
        assertEquals(listOf(whenOn, whenOff), words(deviceClass))
    }

    @Test
    fun `falls back to On and Off for a kind it does not know, or none`() {
        assertEquals(listOf("On", "Off"), words("something_new"))
        assertEquals(listOf("On", "Off"), words(null))
    }

    @Test
    fun `says Unavailable when the sensor cannot be reached`() {
        val view = readBinarySensor(changed(motion, emptyMap(), "unavailable"))
        assertEquals("Unavailable", view.words)
        assertFalse(view.isOn)
    }

    @Test
    fun `does nothing on OK, and is shown as on when it is on`() {
        assertEquals(OkAction.Nothing, BinarySensorLogic.okAction(motion))
        assertFalse(BinarySensorLogic.isActive(motion))
        assertTrue(BinarySensorLogic.isActive(changed(motion, emptyMap(), "on")))
    }

    @Test
    fun `sums itself up in its words`() {
        assertEquals("Clear", BinarySensorLogic.summary(motion))
    }

    @Test
    fun `covers binary_sensor entities`() {
        assertEquals(listOf("binary_sensor"), BinarySensorLogic.domains)
        assertEquals("binary_sensor", BinarySensorLogic.id)
        assertEquals(110, BinarySensorLogic.mainOrder)
    }
}
