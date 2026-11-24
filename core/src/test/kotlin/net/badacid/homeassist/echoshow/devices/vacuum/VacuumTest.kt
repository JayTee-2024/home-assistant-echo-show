package net.badacid.homeassist.echoshow.devices.vacuum

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/vacuum/vacuum.test.ts: robot vacuums. */
class VacuumTest {
    private val robot = sampleEntity("vacuum.robot") // docked, features 12828

    @Test
    fun `reads a docked vacuum and what it supports`() {
        assertEquals(
            VacuumView(
                isAvailable = true, activity = "docked", words = "Docked", canStart = true, canPause = true,
                canStop = true, canReturnHome = true, canLocate = true,
            ),
            readVacuum(robot),
        )
    }

    @Test
    fun `puts each activity into words, and an error plainly`() {
        fun words(state: String) = readVacuum(changed(robot, emptyMap(), state)).words
        assertEquals("Cleaning", words("cleaning"))
        assertEquals("Returning to dock", words("returning"))
        assertEquals("Paused", words("paused"))
        assertEquals("Idle", words("idle"))
        assertEquals("Needs attention", words("error"))
        assertEquals("Unavailable", words("unavailable"))
        assertEquals("Mopping now", words("mopping_now"))
    }

    @Test
    fun `starts, pauses, stops, returns home and locates`() {
        assertEquals(ServiceCall("vacuum", "start", "vacuum.robot", JsonObject(emptyMap())), start(robot).call)
        assertEquals("cleaning", start(robot).predicted.state)
        assertEquals("pause", pause(robot).call.service)
        assertEquals("stop", stop(robot).call.service)
        assertEquals("return_to_base", returnHome(robot).call.service)
        assertEquals("returning", returnHome(robot).predicted.state)
        assertEquals("locate", locate(robot).call.service)
        assertEquals(robot, locate(robot).predicted)
    }

    @Test
    fun `opens its panel on OK, even when it needs attention`() {
        assertEquals(OkAction.OpenPanel, VacuumLogic.okAction(robot))
        assertEquals(OkAction.OpenPanel, VacuumLogic.okAction(changed(robot, emptyMap(), "error")))
        assertEquals(OkAction.Nothing, VacuumLogic.okAction(changed(robot, emptyMap(), "unavailable")))
    }

    @Test
    fun `is shown as on while cleaning or returning`() {
        assertFalse(VacuumLogic.isActive(robot))
        assertTrue(VacuumLogic.isActive(changed(robot, emptyMap(), "cleaning")))
        assertTrue(VacuumLogic.isActive(changed(robot, emptyMap(), "returning")))
    }

    @Test
    fun `sums itself up in words`() {
        assertEquals("Needs attention", VacuumLogic.summary(changed(robot, emptyMap(), "error")))
    }
}
