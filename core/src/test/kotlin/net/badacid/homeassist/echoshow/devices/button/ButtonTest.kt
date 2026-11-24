package net.badacid.homeassist.echoshow.devices.button

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Port of the LG app's src/devices/button/button.test.ts: things that are pressed or run. */
class ButtonTest {
    private val none = JsonObject(emptyMap())
    private val scene = sampleEntity("scene.movie_night")
    private val script = sampleEntity("script.goodnight")
    private val button = EntityState("button.restart_router", "unknown", none)
    private val inputButton = EntityState("input_button.doorbell_test", "2026-11-20T08:00:00+00:00", none)

    @ParameterizedTest(name = "sends the right service for {0}")
    @CsvSource(
        "button.restart_router, button, press",
        "input_button.doorbell_test, input_button, press",
        "scene.movie_night, scene, turn_on",
        "script.goodnight, script, turn_on",
    )
    fun `sends the right service`(entityId: String, domain: String, service: String) {
        val entity = listOf(button, inputButton, scene, script).first { it.entityId == entityId }
        assertEquals(ServiceCall(domain, service, entityId, none), press(entity).call)
    }

    @Test
    fun `predicts no change, because a press has no lasting state`() {
        assertEquals(scene, press(scene).predicted)
    }

    @Test
    fun `covers buttons, button helpers, scenes and scripts`() {
        assertEquals(listOf("button", "input_button", "scene", "script"), ButtonLogic.domains)
    }

    @Test
    fun `presses on OK`() {
        assertEquals(OkAction.Run(press(scene)), ButtonLogic.okAction(scene))
    }

    @Test
    fun `can be pressed while its state is unknown, which only means it was never pressed`() {
        assertEquals(OkAction.Run(press(button)), ButtonLogic.okAction(button))
    }

    @Test
    fun `does nothing on OK when it is unavailable`() {
        assertEquals(OkAction.Nothing, ButtonLogic.okAction(changed(scene, emptyMap(), "unavailable")))
    }

    @Test
    fun `is never shown as on, and has no summary line`() {
        assertFalse(ButtonLogic.isActive(scene))
        assertNull(ButtonLogic.summary(scene))
    }

    @Test
    fun `says Run for a script, Activate for a scene and Press otherwise`() {
        assertEquals("Run", readButton(script).verb)
        assertEquals("Activate", readButton(scene).verb)
        assertEquals("Press", readButton(button).verb)
    }
}
