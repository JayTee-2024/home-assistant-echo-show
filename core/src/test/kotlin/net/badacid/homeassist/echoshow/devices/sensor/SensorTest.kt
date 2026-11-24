package net.badacid.homeassist.echoshow.devices.sensor

import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Port of the LG app's src/devices/sensor/sensor.test.ts: a reading and its unit. */
class SensorTest {
    private val temperature = sampleEntity("sensor.living_room_temperature")

    @Test
    fun `reads the reading in Home Assistant's own text, with its unit`() {
        assertEquals(SensorView("20.5", "°C"), readSensor(temperature))
    }

    @Test
    fun `reads a sensor with no unit`() {
        assertEquals(
            SensorView("Cleaning", null),
            readSensor(changed(temperature, mapOf("unit_of_measurement" to null), "Cleaning")),
        )
    }

    @Test
    fun `says so in words when the sensor is unavailable or unknown, with no unit`() {
        assertEquals(SensorView("Unavailable", null), readSensor(changed(temperature, emptyMap(), "unavailable")))
        assertEquals(SensorView("Unknown", null), readSensor(changed(temperature, emptyMap(), "unknown")))
    }

    @Test
    fun `shows a date-and-time reading as a short date and time`() {
        val uptime = sampleEntity("sensor.tado_bridge_uptime")
        val london = ZoneId.of("Europe/London")
        assertEquals("20 Nov, 08:00", readSensor(uptime, london).reading)
        assertEquals("20 Nov, 09:00", readSensor(uptime, ZoneId.of("Europe/Paris")).reading)
    }

    @Test
    fun `keeps a timestamp it cannot read as it is`() {
        val odd = changed(sampleEntity("sensor.tado_bridge_uptime"), emptyMap(), "last Tuesday")
        assertEquals("last Tuesday", readSensor(odd).reading)
    }

    @Test
    fun `formatDateTime writes the day, the month in three letters, and the time on a 24-hour clock`() {
        assertEquals("20 Nov, 08:05", formatDateTime(LocalDateTime.of(2026, 11, 20, 8, 5)))
        assertEquals("1 Jan, 23:59", formatDateTime(LocalDateTime.of(2026, 1, 1, 23, 59)))
    }

    @Test
    fun `does nothing on OK and is never shown as on`() {
        assertEquals(OkAction.Nothing, SensorLogic.okAction(temperature))
        assertFalse(SensorLogic.isActive(temperature))
    }

    @Test
    fun `sums itself up as the reading and unit`() {
        assertEquals("20.5 °C", SensorLogic.summary(temperature))
        assertEquals("48%", SensorLogic.summary(sampleEntity("sensor.living_room_humidity")))
        assertEquals("Unavailable", SensorLogic.summary(changed(temperature, emptyMap(), "unavailable")))
    }
}
