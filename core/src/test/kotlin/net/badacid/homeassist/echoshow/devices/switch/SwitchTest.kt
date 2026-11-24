package net.badacid.homeassist.echoshow.devices.switch

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Port of the LG app's src/devices/switch/switch.test.ts: plugs, switches and on/off helpers. */
class SwitchTest {
    private val tvPlug = sampleEntity("switch.tv_plug") // on
    private val kettlePlug = sampleEntity("switch.kettle_plug") // off
    private val guestMode = sampleEntity("input_boolean.guest_mode") // off

    @Test
    fun `reads on and off`() {
        assertEquals(SwitchView(isOn = true, isAvailable = true), readSwitch(tvPlug))
        assertEquals(SwitchView(isOn = false, isAvailable = true), readSwitch(kettlePlug))
    }

    @Test
    fun `reads an unavailable switch as off and not available`() {
        assertEquals(SwitchView(isOn = false, isAvailable = false), readSwitch(sampleEntity("switch.spare_plug")))
    }

    @Test
    fun `asks Home Assistant to toggle, and predicts the opposite state`() {
        val action = toggle(tvPlug)
        assertEquals("switch", action.call.domain)
        assertEquals("toggle", action.call.service)
        assertEquals("switch.tv_plug", action.call.entityId)
        assertEquals(JsonObject(emptyMap()), action.call.data)
        assertEquals("off", action.predicted.state)
        assertEquals("on", toggle(kettlePlug).predicted.state)
    }

    @Test
    fun `uses the entity's own type for the service, so helpers work too`() {
        assertEquals("input_boolean", toggle(guestMode).call.domain)
    }

    @Test
    fun `leaves the entity it was given unchanged`() {
        toggle(tvPlug)
        assertEquals("on", tvPlug.state)
    }

    @Test
    fun `covers switches and on-off helpers`() {
        assertEquals(listOf("switch", "input_boolean"), SwitchLogic.domains)
        assertEquals(CardSize.SMALL, SwitchLogic.cardSize)
        assertEquals(80, SwitchLogic.mainOrder)
    }

    @Test
    fun `toggles on OK, and does nothing when the switch is unavailable`() {
        assertEquals(OkAction.Run(toggle(tvPlug)), SwitchLogic.okAction(tvPlug))
        assertEquals(OkAction.Nothing, SwitchLogic.okAction(changed(tvPlug, emptyMap(), "unavailable")))
    }

    @Test
    fun `is shown as on only when it is on`() {
        assertTrue(SwitchLogic.isActive(tvPlug))
        assertFalse(SwitchLogic.isActive(kettlePlug))
    }

    @Test
    fun `sums itself up in a word`() {
        assertEquals("On", SwitchLogic.summary(tvPlug))
        assertEquals("Off", SwitchLogic.summary(kettlePlug))
        assertEquals("Unavailable", SwitchLogic.summary(sampleEntity("switch.spare_plug")))
    }
}
