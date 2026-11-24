package net.badacid.homeassist.echoshow.devices

import net.badacid.homeassist.echoshow.core.jsonValue
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PredictTest {
    @Test
    fun `predict replaces the state and the given attributes, and keeps the rest`() {
        val lamp = sampleEntity("light.lounge_lamp")
        val predicted = predict(lamp, "off", mapOf("brightness" to jsonValue(10)))

        assertEquals("off", predicted.state)
        assertEquals(jsonValue(10), predicted.attributes["brightness"])
        assertEquals(lamp.attributes["friendly_name"], predicted.attributes["friendly_name"])
        assertEquals("on", lamp.state)
    }

    @Test
    fun `readableName turns lower_case_names into words`() {
        assertEquals("Effect pulse", readableName("effect_pulse"))
        assertEquals("Eco", readableName("eco"))
        assertEquals("", readableName(""))
    }
}
