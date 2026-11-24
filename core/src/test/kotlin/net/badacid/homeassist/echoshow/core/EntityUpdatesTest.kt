package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.testsupport.json
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import net.badacid.homeassist.echoshow.testsupport.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Home Assistant's subscribe_entities sends whole entities ("a"), changes ("c") and removals ("r") in a
 * short form: s = state, a = attributes. https://developers.home-assistant.io/docs/api/websocket
 */
class EntityUpdatesTest {
    private val states = sampleHome().states

    private fun event(text: String) = json(text) as JsonObject

    @Test
    fun `adds whole entities from a`() {
        val updated = applyEntityUpdate(
            emptyMap(),
            event("""{"a":{"light.a":{"s":"on","a":{"brightness":200},"c":"x","lc":1.0},"switch.b":{"s":"off","a":{}}}}"""),
        )
        assertEquals(EntityState("light.a", "on", toJson(mapOf("brightness" to 200)) as JsonObject), updated["light.a"])
        assertEquals("off", updated["switch.b"]?.state)
    }

    @Test
    fun `changes the state and merges attributes from a plus change`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.lounge_lamp":{"+":{"s":"off","a":{"brightness":10}}}}}"""))
        val lamp = updated.getValue("light.lounge_lamp")
        assertEquals("off", lamp.state)
        assertEquals(toJson(10), lamp.attributes["brightness"])
        assertEquals(states.getValue("light.lounge_lamp").attributes["friendly_name"], lamp.attributes["friendly_name"])
    }

    @Test
    fun `keeps the state when a plus change carries only attributes`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.lounge_lamp":{"+":{"a":{"brightness":10}}}}}"""))
        assertEquals("on", updated.getValue("light.lounge_lamp").state)
    }

    @Test
    fun `removes the attributes named in a minus change`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.lounge_lamp":{"-":{"a":["rgb_color","brightness"]}}}}"""))
        val lamp = updated.getValue("light.lounge_lamp")
        assertNull(lamp.attributes["rgb_color"])
        assertNull(lamp.attributes["brightness"])
        assertEquals("on", lamp.state)
    }

    @Test
    fun `drops removed entities`() {
        val updated = applyEntityUpdate(states, event("""{"r":["light.lounge_lamp"]}"""))
        assertFalse("light.lounge_lamp" in updated)
        assertEquals(states.size - 1, updated.size)
    }

    @Test
    fun `keeps the very same object for every entity the update does not name`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.lounge_lamp":{"+":{"s":"off"}}}}"""))
        for ((id, state) in states) {
            if (id != "light.lounge_lamp") assertSame(state, updated[id], id)
        }
    }

    @Test
    fun `gives a new object to a changed entity even when nothing in it differs`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.lounge_lamp":{"+":{"s":"on"}}}}"""))
        assertNotSame(states["light.lounge_lamp"], updated["light.lounge_lamp"])
        assertEquals(states["light.lounge_lamp"], updated["light.lounge_lamp"])
    }

    @Test
    fun `ignores a change to an entity it does not know`() {
        val updated = applyEntityUpdate(states, event("""{"c":{"light.nowhere":{"+":{"s":"on"}}}}"""))
        assertFalse("light.nowhere" in updated)
        assertEquals(states, updated)
    }

    @Test
    fun `skips malformed parts without failing`() {
        val bad = listOf(
            """{"a":[1,2]}""",
            """{"a":{"light.x":{"s":5}}}""",
            """{"a":{"light.x":"on"}}""",
            """{"c":{"light.lounge_lamp":{"+":{"s":7}}}}""",
            """{"c":{"light.lounge_lamp":{"-":{"a":"brightness"}}}}""",
            """{"c":"nonsense"}""",
            """{"r":"light.lounge_lamp"}""",
            """{"r":[1,null]}""",
        )
        for (text in bad) {
            val updated = applyEntityUpdate(states, event(text))
            assertEquals("on", updated["light.lounge_lamp"]?.state, text)
            assertFalse("light.x" in updated, text)
        }
    }

    @Test
    fun `gives back the same map when the update changes nothing`() {
        assertSame(states, applyEntityUpdate(states, event("{}")))
    }
}
