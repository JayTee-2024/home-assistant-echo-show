package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.testsupport.changed
import net.badacid.homeassist.echoshow.testsupport.json
import net.badacid.homeassist.echoshow.testsupport.sampleEntity
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/home-assistant.test.ts. */
class HomeAssistantTest {
    private val lamp = sampleEntity("light.lounge_lamp")

    @Nested
    inner class DomainOf {
        @Test
        fun `gives the part of the entity id before the dot`() {
            assertEquals("light", domainOf("light.lounge_lamp"))
            assertEquals("binary_sensor", domainOf("binary_sensor.front_door_motion"))
        }

        @Test
        fun `gives an empty text for something that is not an entity id`() {
            assertEquals("", domainOf("nonsense"))
        }
    }

    @Nested
    inner class IsAvailable {
        @Test
        fun `is true for an ordinary state`() {
            assertTrue(isAvailable(lamp))
        }

        @Test
        fun `is false when Home Assistant reports unavailable or unknown`() {
            assertFalse(isAvailable(changed(lamp, emptyMap(), "unavailable")))
            assertFalse(isAvailable(changed(lamp, emptyMap(), "unknown")))
        }
    }

    @Nested
    inner class ReadNumber {
        @Test
        fun `reads a number`() {
            assertEquals(180.0, readNumber(lamp, "brightness"))
        }

        @Test
        fun `is null for a missing attribute, a null, text that looks like a number, and NaN`() {
            assertNull(readNumber(lamp, "no_such_attribute"))
            assertNull(readNumber(lamp, "color_temp_kelvin"))
            assertNull(readNumber(changed(lamp, mapOf("brightness" to "180")), "brightness"))
            assertNull(readNumber(changed(lamp, mapOf("brightness" to Double.NaN)), "brightness"))
        }

        @Test
        fun `readNumber gives null for a string, null or array`() {
            assertNull(readNumber(changed(lamp, mapOf("brightness" to "high")), "brightness"))
            assertNull(readNumber(changed(lamp, mapOf("brightness" to null)), "brightness"))
            assertNull(readNumber(changed(lamp, mapOf("brightness" to listOf(1, 2))), "brightness"))
            assertNull(readNumber(changed(lamp, mapOf("brightness" to true)), "brightness"))
        }
    }

    @Nested
    inner class ReadText {
        @Test
        fun `reads text`() {
            assertEquals("Lounge lamp", readText(lamp, "friendly_name"))
        }

        @Test
        fun `is null for a missing attribute and for a value that is not text`() {
            assertNull(readText(lamp, "no_such_attribute"))
            assertNull(readText(lamp, "brightness"))
        }

        @Test
        fun `readText gives null for a number`() {
            assertNull(readText(changed(lamp, mapOf("friendly_name" to 12)), "friendly_name"))
        }
    }

    @Nested
    inner class ReadTextList {
        @Test
        fun `reads a list of text`() {
            assertEquals(listOf("color_temp", "hs"), readTextList(lamp, "supported_color_modes"))
        }

        @Test
        fun `keeps only the text from a mixed list`() {
            val mixed = changed(lamp, mapOf("effect_list" to listOf("a", 3, null, "b")))
            assertEquals(listOf("a", "b"), readTextList(mixed, "effect_list"))
        }

        @Test
        fun `is empty for a missing attribute and for a value that is not a list`() {
            assertEquals(emptyList<String>(), readTextList(lamp, "no_such_attribute"))
            assertEquals(emptyList<String>(), readTextList(lamp, "friendly_name"))
        }
    }

    @Nested
    inner class HasFeature {
        @Test
        fun `reads one bit of supported_features`() {
            assertTrue(hasFeature(lamp, 4))
            assertFalse(hasFeature(lamp, 8))
        }

        @Test
        fun `is false when the entity lists no features`() {
            assertFalse(hasFeature(sampleEntity("switch.tv_plug"), 1))
        }
    }

    @Nested
    inner class ReadingEntityStates {
        @Test
        fun `an entity without attributes reads as empty`() {
            val entity = readEntityState(json("""{"entity_id":"sun.sun","state":"above_horizon"}"""))
            assertEquals(EntityState("sun.sun", "above_horizon", JsonObject(emptyMap())), entity)
        }

        @Test
        fun `attributes that are not an object read as empty`() {
            val entity = readEntityState(json("""{"entity_id":"sun.sun","state":"up","attributes":[1]}"""))
            assertEquals(JsonObject(emptyMap()), entity?.attributes)
        }

        @Test
        fun `an entry with no id or no text state is dropped`() {
            assertNull(readEntityState(json("""{"state":"on"}""")))
            assertNull(readEntityState(json("""{"entity_id":"light.a","state":1}""")))
            assertNull(readEntityState(json("""[]""")))
        }
    }

    @Nested
    inner class TheSampleHome {
        @Test
        fun `has a registry entry for every state, except the ones that are deliberately loose`() {
            val home = sampleHome()
            val registered = home.entities.map { it.entityId }.toSet()
            val loose = home.states.keys.filter { it !in registered }.sorted()
            assertEquals(listOf("scene.movie_night", "script.goodnight", "sun.sun"), loose)
        }
    }

    @Nested
    inner class ReadingTheRegistries {
        @Test
        fun `keeps the fields the app uses from entity registry entries`() {
            val entries = readRegistryEntities(
                json(
                    """[{"entity_id":"light.a","device_id":"dev1","area_id":null,"disabled_by":null,
                    "hidden_by":"user","entity_category":"diagnostic","name":"Lamp",
                    "original_name":"LIFX A19","platform":"lifx","unique_id":"abc"}]""",
                ),
            )
            assertEquals(
                listOf(
                    RegistryEntity(
                        entityId = "light.a",
                        deviceId = "dev1",
                        areaId = null,
                        disabledBy = null,
                        hiddenBy = "user",
                        entityCategory = "diagnostic",
                        name = "Lamp",
                        originalName = "LIFX A19",
                    ),
                ),
                entries,
            )
        }

        @Test
        fun `fills missing or wrongly typed fields with null, and drops entries with no id`() {
            val entries = readRegistryEntities(
                json("""[{"entity_id":"light.a","device_id":7},{"name":"no id"},"text",null]"""),
            )
            assertEquals(
                listOf(RegistryEntity("light.a", null, null, null, null, null, null, null)),
                entries,
            )
        }

        @Test
        fun `reads devices, including ones that carry fewer fields than usual`() {
            val devices = readRegistryDevices(
                json(
                    """[{"id":"dev1","name":"Lamp","name_by_user":"Lounge lamp","area_id":"living",
                    "disabled_by":null},{"id":"child1","name":"Child zone"},{"name":"no id"}]""",
                ),
            )
            assertEquals(
                listOf(
                    RegistryDevice("dev1", "Lamp", "Lounge lamp", "living", null),
                    RegistryDevice("child1", "Child zone", null, null, null),
                ),
                devices,
            )
        }

        @Test
        fun `reads areas and drops ones with no id or name`() {
            val areas = readRegistryAreas(
                json("""[{"area_id":"living","name":"Living room","icon":null},{"area_id":"x"},{}]"""),
            )
            assertEquals(listOf(RegistryArea("living", "Living room")), areas)
        }

        @Test
        fun `gives an empty list for anything that is not a list`() {
            assertEquals(emptyList<RegistryEntity>(), readRegistryEntities(null))
            assertEquals(emptyList<RegistryDevice>(), readRegistryDevices(json("\"nope\"")))
            assertEquals(emptyList<RegistryArea>(), readRegistryAreas(json("{}")))
        }
    }
}
