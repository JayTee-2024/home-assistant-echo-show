package net.badacid.homeassist.echoshow.devices

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Each type's panel rows, ported from the LG app's src/devices/<type>/Panel.tsx. */
class PanelsTest {
    private fun rows(entityId: String, entity: EntityState = sampleEntity(entityId)) =
        panelRows(typeOf(entityId), entity)

    private fun typeOf(entityId: String) = entityId.substringBefore('.')

    private fun List<PanelRow>.row(label: String) = first { it.label == label }
    private fun PanelRow.presses() = controls.filterIsInstance<PanelControl.Press>()
    private fun PanelRow.press(label: String) = presses().first { it.label == label }
    private fun PanelRow.values(): List<String> = controls.filterIsInstance<PanelControl.Value>().map { it.text }

    @Test
    fun `light - on, brightness steps, white shades and colours`() {
        val rows = rows("light.lounge_lamp")
        assertEquals("On", rows[0].label)
        assertEquals("light/toggle", rows[0].press("Turn off").action!!.call.let { it.domain + "/" + it.service })
        val brightness = rows.row("Brightness")
        assertEquals(listOf("−", "+"), brightness.presses().map { it.label })
        assertEquals(listOf("71%"), brightness.values())
        assertEquals(12, rows.row("Colour").presses().size)
        assertTrue(rows.row("Colour").presses().all { it.swatch != null })
    }

    @Test
    fun `an unavailable light shows its rows with every button greyed`() {
        val rows = rows("light.lounge_lamp", changed(sampleEntity("light.lounge_lamp"), emptyMap(), state = "unavailable"))
        assertTrue(rows.flatMap { it.presses() }.all { it.action == null })
    }

    @Test
    fun `climate - room, a stepper for the target that sends set_temperature, modes with the current one marked`() {
        val rows = rows("climate.living_room")
        assertEquals(listOf("20.5°"), rows.row("Room").values())
        val stepper = rows.row("Target").controls.single() as PanelControl.Stepper
        assertEquals(21.0, stepper.value)
        assertEquals(1500L, stepper.debounceMs)
        val up = stepper.step(21.0, 1)
        assertEquals(21.1, up, 0.001)
        assertEquals("21.1°", stepper.format(up))
        val call = stepper.send(up).call
        assertEquals("climate/set_temperature", call.domain + "/" + call.service)
        assertTrue(rows.row("Mode").press("Heat").isCurrent)
        assertFalse(rows.row("Mode").press("Off").isCurrent)
        assertTrue(rows.row("Preset").press("Home").isCurrent)
    }

    @Test
    fun `media player - playing line, transport, a volume stepper with Mute, sources`() {
        val rows = rows("media_player.living_room_sonos")
        assertEquals(listOf("Clair de Lune · Claude Debussy"), rows.row("Playing").values())
        assertEquals(listOf("Previous", "Pause", "Next"), rows[1].presses().map { it.label })
        val volume = rows.row("Volume")
        val stepper = volume.controls.filterIsInstance<PanelControl.Stepper>().single()
        assertEquals(500L, stepper.debounceMs)
        assertEquals("media_player/volume_set", stepper.send(30.0).call.let { it.domain + "/" + it.service })
        assertTrue(volume.presses().any { it.label == "Mute" || it.label == "Unmute" })
    }

    @Test
    fun `cover - Open, Stop and Close under its state, and the five positions`() {
        val rows = rows("cover.lounge_blind")
        assertEquals(listOf("Open", "Stop", "Close"), rows[0].presses().map { it.label })
        assertEquals(listOf("0%", "25%", "50%", "75%", "100%"), rows.row("Position").presses().map { it.label })
    }

    @Test
    fun `fan - on, speed steps, oscillate`() {
        val rows = rows("fan.bedroom_fan")
        assertEquals("On", rows[0].label)
        assertEquals(listOf("50%"), rows.row("Speed").values())
        assertEquals("fan/set_percentage", rows.row("Speed").press("+").action!!.call.let { it.domain + "/" + it.service })
    }

    @Test
    fun `vacuum - Return to dock sends return_to_base, and Find it beeps`() {
        val rows = rows("vacuum.robot")
        val dock = rows[0].press("Return to dock").action!!.call
        assertEquals("vacuum/return_to_base", dock.domain + "/" + dock.service)
        assertEquals("Beep", rows.row("Find it").presses().single().label)
    }

    @Test
    fun `switch, button-like scene, sensor and binary sensor`() {
        assertEquals("Turn on", rows("switch.kettle_plug")[0].presses().single().label)
        assertEquals("Reading", rows("sensor.living_room_humidity").single().label)
        assertEquals("State", rows("binary_sensor.front_door_ding").single().label)
        assertNull(rows("switch.kettle_plug")[0].values().firstOrNull())
    }
}
