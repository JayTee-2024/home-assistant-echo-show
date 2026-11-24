package net.badacid.homeassist.echoshow.devices

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of the first half of the LG app's src/devices/registry.test.ts (the rest is in DevicesTest). */
class RegistryTest {
    @Test
    fun `gives every type its own id`() {
        val ids = DEVICE_LOGIC.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `never lets two types claim the same Home Assistant entity type`() {
        val domains = DEVICE_LOGIC.flatMap { it.domains }
        assertEquals(emptyList<String>(), domains.groupBy { it }.filter { it.value.size > 1 }.keys.toList())
    }

    @Test
    fun `lists the types in the order their parts lead a card`() {
        val orders = DEVICE_LOGIC.map { it.mainOrder }
        assertEquals(orders.sorted(), orders)
    }

    @Test
    fun `finds a type by its id, and gives null for an unknown id`() {
        for (type in DEVICE_LOGIC) assertSame(type, deviceLogicFor(type.id))
        assertNull(deviceLogicFor("teleporter"))
    }

    @Test
    fun `never lets a type claim locks or alarm panels, which must never be controlled from the Echo`() {
        for (type in DEVICE_LOGIC) {
            assertFalse("lock" in type.domains)
            assertFalse("alarm_control_panel" in type.domains)
        }
    }

    @Test
    fun `gives every type a name for the picker`() {
        for (type in DEVICE_LOGIC) assertTrue(type.label.isNotEmpty())
    }

    @Test
    fun `has the eleven types of the LG app`() {
        assertEquals(
            listOf("camera", "climate", "media_player", "vacuum", "light", "cover", "fan", "switch", "button", "sensor", "binary_sensor"),
            DEVICE_LOGIC.map { it.id },
        )
    }
}
