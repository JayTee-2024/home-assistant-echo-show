package net.badacid.homeassist.echoshow.core

import kotlinx.serialization.json.JsonObject
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import net.badacid.homeassist.echoshow.testsupport.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Port of the LG app's src/core/devices.test.ts, and the second half of src/devices/registry.test.ts. */
class DevicesTest {
    /** Three small part types, enough to exercise every rule without the real ones. */
    private fun partType(id: String, order: Int, size: CardSize, domains: List<String> = listOf(id), supports: (EntityState) -> Boolean = { true }) =
        object : PartType {
            override val id = id
            override val domains = domains
            override val mainOrder = order
            override val cardSize = size
            override fun supports(entity: EntityState) = supports(entity)
        }

    private val light = partType("light", 50, CardSize.SMALL)
    private val cover = partType("cover", 60, CardSize.WIDE) { readText(it, "device_class") != "garage" }
    private val sensor = partType("sensor", 100, CardSize.SMALL)
    private val partTypes = listOf(light, cover, sensor)
    private val withSwitch = partType("switch", 80, CardSize.SMALL, listOf("switch", "button"))

    private fun sampleDevice(id: String): Device =
        findDevice(buildDevices(sampleHome(), partTypes), id) ?: error("No device $id")

    private fun state(entityId: String, state: String = "on", attributes: Map<String, Any?> = emptyMap()) =
        EntityState(entityId, state, toJson(attributes) as JsonObject)

    private fun entry(entityId: String, deviceId: String?, name: String? = null, originalName: String? = null, areaId: String? = null) =
        RegistryEntity(entityId, deviceId, areaId, null, null, null, name, originalName)

    /** A tiny home with loose entities only, for the rules about things with no device. */
    private fun homeWith(vararg entityIds: String) =
        EMPTY_HOME.copy(states = entityIds.associateWith { state(it) })

    /** The sample home with an entity (and its state) added to one of its devices. */
    private fun HomeSnapshot.withEntity(deviceId: String, entityId: String, attributes: Map<String, Any?> = emptyMap()) =
        copy(
            states = states + (entityId to state(entityId, "off", attributes)),
            entities = entities + entry(entityId, deviceId, originalName = entityId),
        )

    private fun HomeSnapshot.changeEntity(entityId: String, change: (RegistryEntity) -> RegistryEntity) =
        copy(entities = entities.map { if (it.entityId == entityId) change(it) else it })

    private fun HomeSnapshot.changeDevice(deviceId: String, change: (RegistryDevice) -> RegistryDevice) =
        copy(devices = devices.map { if (it.id == deviceId) change(it) else it })

    @Nested
    inner class WhichEntitiesCount {
        @Test
        fun `ignores disabled and hidden entities`() {
            val home = sampleHome().copy(
                states = sampleHome().states + ("light.hall_light_twin" to state("light.hall_light_twin")),
                entities = sampleHome().entities +
                    RegistryEntity("light.hall_light_twin", "dev_lifx_white", null, null, "user", null, null, "Twin"),
            )
            val device = findDevice(buildDevices(home, partTypes), "device:dev_lifx_white")
            assertEquals(listOf("light.hall_light"), device?.parts?.map { it.entityId })
        }

        @Test
        fun `ignores disabled devices`() {
            val home = sampleHome().changeDevice("dev_lifx_lamp") { it.copy(disabledBy = "user") }
            assertNull(findDevice(buildDevices(home, partTypes), "device:dev_lifx_lamp"))
        }

        @Test
        fun `leaves out diagnostic and configuration entities`() {
            assertEquals(listOf("light.lounge_lamp"), sampleDevice("device:dev_lifx_lamp").parts.map { it.entityId })
        }

        @Test
        fun `keeps a diagnostic sensor when it is a battery level`() {
            assertTrue("sensor.living_room_heating_battery" in sampleDevice("device:dev_tado").parts.map { it.entityId })
        }

        @Test
        fun `skips an entity that is in the registry but has no state yet`() {
            val home = sampleHome().let { it.copy(states = it.states - "light.hall_light") }
            assertEquals(emptyList<Part>(), findDevice(buildDevices(home, partTypes), "device:dev_lifx_white")?.parts)
        }

        @Test
        fun `treats an entity its type refuses as not supported`() {
            val garage = sampleDevice("device:dev_garage")
            assertEquals(emptyList<Part>(), garage.parts)
            assertNull(garage.mainPart)
            assertNull(garage.cardSize)
        }
    }

    @Nested
    inner class TheDevice {
        @Test
        fun `leads with the part whose type has the lowest order, and takes its card size`() {
            val home = sampleHome().let {
                it.copy(
                    states = it.states + ("cover.lounge_blind" to state("cover.lounge_blind", "open", mapOf("device_class" to "blind"))),
                    entities = it.entities + entry("cover.lounge_blind", "dev_lifx_lamp", originalName = "Blind"),
                )
            }
            val device = findDevice(buildDevices(home, partTypes), "device:dev_lifx_lamp")
            assertEquals("light.lounge_lamp", device?.mainPart?.entityId)
            assertEquals(CardSize.SMALL, device?.cardSize)
            assertEquals(listOf("light", "cover"), device?.parts?.map { it.typeId })
        }

        @Test
        fun `orders parts of the same type by name, with a battery level last`() {
            assertEquals(listOf("Humidity", "Temperature", "Battery"), sampleDevice("device:dev_tado").parts.map { it.name })
        }

        @Test
        fun `puts a battery level last even when its type would otherwise come first`() {
            val lateType = partType("binary_sensor", 110, CardSize.SMALL)
            val doorbell = findDevice(buildDevices(sampleHome(), listOf(sensor, lateType)), "device:dev_ring")
            assertEquals(listOf("Ding", "Motion", "Battery"), doorbell?.parts?.map { it.name })
            assertEquals("Ding", doorbell?.mainPart?.name)
        }

        @Test
        fun `still returns a device with nothing supported, with no parts`() {
            val bridge = sampleDevice("device:dev_bridge")
            assertEquals("Tado bridge", bridge.name)
            assertEquals(emptyList<Part>(), bridge.parts)
        }

        @Test
        fun `returns a device whose entities are all disabled, with no parts`() {
            val home = sampleHome().let { h ->
                h.copy(entities = h.entities.map { if (it.deviceId == "dev_plug_tv") it.copy(disabledBy = "user") else it })
            }
            assertEquals(emptyList<Part>(), findDevice(buildDevices(home, partTypes), "device:dev_plug_tv")?.parts)
        }
    }

    @Nested
    inner class NamesAndRooms {
        @Test
        fun `prefers the name the person gave the device`() {
            assertEquals("Lounge lamp", sampleDevice("device:dev_lifx_lamp").name)
        }

        @Test
        fun `uses the device name when the person gave none`() {
            assertEquals("Kettle plug", sampleDevice("device:dev_plug_kettle").name)
        }

        @Test
        fun `names a device with no name after its first entity`() {
            assertEquals("Spare plug", sampleDevice("device:dev_noname").name)
        }

        @Test
        fun `calls a device with no name and no entities Unnamed device`() {
            val home = sampleHome().let { it.copy(devices = it.devices + RegistryDevice("dev_empty", null, null, null, null)) }
            assertEquals("Unnamed device", findDevice(buildDevices(home, partTypes), "device:dev_empty")?.name)
        }

        @Test
        fun `names a part by the person's name for it, then the integration's, then the friendly name`() {
            assertEquals("Hall light", sampleDevice("device:dev_lifx_white").parts[0].name)
            assertEquals("Lounge lamp", sampleDevice("device:dev_lifx_lamp").parts[0].name)
            val loose = findDevice(buildDevices(homeWith("light.loose"), partTypes), "entity:light.loose")
            assertEquals("light.loose", loose?.name)
        }

        @Test
        fun `puts a device in its area, and gives null when it has none or the area is unknown`() {
            assertEquals("Living room", sampleDevice("device:dev_lifx_lamp").roomName)
            assertNull(sampleDevice("device:dev_garage").roomName)
            val home = sampleHome().changeDevice("dev_lifx_lamp") { it.copy(areaId = "area_that_was_deleted") }
            assertNull(findDevice(buildDevices(home, partTypes), "device:dev_lifx_lamp")?.roomName)
        }
    }

    @Nested
    inner class ThingsWithNoDevice {
        @Test
        fun `lists a loose entity of a supported type as a device of its own`() {
            val devices = buildDevices(homeWith("light.loose"), partTypes)
            assertEquals(listOf("entity:light.loose"), devices.map { it.id })
            assertEquals("light.loose", devices[0].mainPart?.entityId)
        }

        @Test
        fun `lists a loose lock, alarm or cover with no parts, so the picker can say it is not supported`() {
            val devices = buildDevices(homeWith("lock.shed", "alarm_control_panel.house"), partTypes)
            assertEquals(listOf("entity:alarm_control_panel.house", "entity:lock.shed"), devices.map { it.id }.sorted())
            assertEquals(emptyList<Part>(), devices[0].parts)
        }

        @Test
        fun `does not list other loose entities, such as automations`() {
            assertEquals(emptyList<Device>(), buildDevices(homeWith("automation.morning", "sun.sun", "zone.home"), partTypes))
        }

        @Test
        fun `uses the registry entry of a loose entity for its name and room`() {
            val home = homeWith("light.loose").copy(
                areas = listOf(RegistryArea("study", "Study")),
                entities = listOf(entry("light.loose", null, name = "Desk lamp", areaId = "study")),
            )
            val device = buildDevices(home, partTypes)[0]
            assertEquals("Desk lamp", device.name)
            assertEquals("Study", device.roomName)
        }

        @Test
        fun `gives nothing for an empty home`() {
            assertEquals(emptyList<Device>(), buildDevices(EMPTY_HOME, partTypes))
        }
    }

    @Test
    fun `groupByRoom orders rooms by name with No room last, and devices by name within a room`() {
        val rooms = groupByRoom(buildDevices(sampleHome(), partTypes))
        assertEquals(listOf("Bedroom", "Hallway", "Kitchen", "Living room", NO_ROOM), rooms.map { it.name })
        assertEquals(
            listOf("Living room heating", "Living room Sonos", "Lounge blind", "Lounge lamp", "TV plug"),
            rooms[3].devices.map { it.name },
        )
    }

    @Test
    fun `findDevice finds a device by id and gives null for an unknown id`() {
        val devices = buildDevices(sampleHome(), partTypes)
        assertEquals("Front door", findDevice(devices, "device:dev_ring")?.name)
        assertNull(findDevice(devices, "device:nope"))
    }

    @Nested
    inner class DevicesThatMustNeverBeControlledFromTheEcho {
        private fun partsOf(home: HomeSnapshot, deviceId: String, types: List<PartType> = partTypes) =
            findDevice(buildDevices(home, types), deviceId)?.parts

        @Test
        fun `gives a garage door no parts, even when it also has a button that would open it`() {
            val home = sampleHome().withEntity("dev_garage", "button.garage_toggle")
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_garage", partTypes + withSwitch))
        }

        @Test
        fun `gives a lock no parts, even its battery level`() {
            val home = sampleHome().withEntity("dev_lock", "sensor.front_door_lock_battery", mapOf("device_class" to "battery"))
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_lock"))
        }

        @Test
        fun `treats a device with an alarm panel the same way`() {
            val home = sampleHome().withEntity("dev_plug_tv", "alarm_control_panel.house")
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_tv", partTypes + withSwitch))
        }

        @Test
        fun `treats a door or gate cover the same way, and a cover whose kind is not known yet`() {
            for (deviceClass in listOf("door", "gate")) {
                val home = sampleHome().withEntity("dev_plug_kettle", "cover.side_$deviceClass", mapOf("device_class" to deviceClass))
                assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_kettle"), deviceClass)
            }
            val home = sampleHome().let { it.copy(entities = it.entities + entry("cover.not_reported_yet", "dev_plug_kettle", originalName = "Cover")) }
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_kettle"))
        }

        @Test
        fun `treats a cover with no kind as possibly a garage door, and protects its device`() {
            val home = sampleHome().withEntity("dev_plug_kettle", "cover.unlabelled")
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_kettle"))
        }

        @Test
        fun `treats a cover of a kind not known to be safe the same way`() {
            val home = sampleHome().withEntity("dev_plug_kettle", "cover.mystery", mapOf("device_class" to "something_new"))
            assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_kettle"))
        }

        @Test
        fun `lists the kinds of cover that are safe to control from the Echo`() {
            assertEquals(listOf("awning", "blind", "curtain", "damper", "shade", "shutter", "window"), SAFE_COVER_KINDS)
        }

        @Test
        fun `still allows a device with a blind, which is a cover but not a door`() {
            val home = sampleHome().withEntity("dev_plug_kettle", "cover.kitchen_blind", mapOf("device_class" to "blind"))
            assertTrue((partsOf(home, "device:dev_plug_kettle")?.size ?: 0) > 0)
        }

        @Test
        fun `protects a device even when its lock or garage door is hidden or disabled in Home Assistant`() {
            // Hidden only means "not on dashboards": Home Assistant can still operate it, and the device's
            // other entities (a toggle button, say) would open the door just the same.
            for ((entityId, field) in listOf(
                "lock.child_lock" to "disabled_by", "lock.child_lock" to "hidden_by",
                "cover.garage_x" to "hidden_by", "cover.garage_x" to "disabled_by",
            )) {
                var home = sampleHome().withEntity("dev_plug_tv", entityId, mapOf("device_class" to "garage"))
                home = home.changeEntity(entityId) {
                    if (field == "disabled_by") it.copy(disabledBy = "user") else it.copy(hiddenBy = "user")
                }
                if (field == "disabled_by") home = home.copy(states = home.states - entityId)
                assertEquals(emptyList<Part>(), partsOf(home, "device:dev_plug_tv", partTypes + withSwitch), "$entityId $field")
            }
        }
    }

    @Nested
    inner class IsComplete {
        @Test
        fun `is true when every shown entity of the device has reported a state`() {
            assertTrue(sampleDevice("device:dev_lifx_lamp").isComplete)
        }

        @Test
        fun `is false while one of its shown entities has not reported yet, as just after Home Assistant starts`() {
            val home = sampleHome().let { it.copy(states = it.states - "light.lounge_lamp") }
            assertFalse(findDevice(buildDevices(home, partTypes), "device:dev_lifx_lamp")?.isComplete ?: true)
        }

        @Test
        fun `ignores disabled entities, which never report`() {
            assertTrue(sampleDevice("device:dev_plug_kettle").isComplete)
        }
    }

    /** The second half of the LG app's registry.test.ts: the sample home sorted by the real types. */
    @Nested
    inner class TheSampleHomeSortedByTheRealTypes {
        private val devices = buildDevices(sampleHome(), DEVICE_LOGIC)
        private fun mainType(id: String) = findDevice(devices, id)?.mainPart?.typeId

        @Test
        fun `leads the LIFX bulb with its light`() = assertEquals("light", mainType("device:dev_lifx_lamp"))

        @Test
        fun `leads a plug with its switch`() = assertEquals("switch", mainType("device:dev_plug_tv"))

        @Test
        fun `lists the on-off helper as a switch of its own`() = assertEquals("switch", mainType("entity:input_boolean.guest_mode"))

        @Test
        fun `leads the doorbell with its camera, as a large card`() {
            assertEquals("camera", mainType("device:dev_ring"))
            assertEquals(CardSize.LARGE, findDevice(devices, "device:dev_ring")?.cardSize)
        }

        @Test
        fun `gives the doorbell its camera, two binary sensors and its battery as parts`() {
            assertEquals(
                listOf("camera.front_door", "binary_sensor.front_door_ding", "binary_sensor.front_door_motion", "sensor.front_door_battery"),
                findDevice(devices, "device:dev_ring")?.parts?.map { it.entityId },
            )
        }

        @Test
        fun `lists the scene and the script as buttons of their own`() {
            assertEquals("button", mainType("entity:scene.movie_night"))
            assertEquals("button", mainType("entity:script.goodnight"))
        }

        @Test
        fun `leads the Tado thermostat with its heating, as a wide card`() {
            assertEquals("climate", mainType("device:dev_tado"))
            assertEquals(CardSize.WIDE, findDevice(devices, "device:dev_tado")?.cardSize)
        }

        @Test
        fun `leads the Sonos with its media player, as a wide card`() {
            assertEquals("media_player", mainType("device:dev_sonos"))
            assertEquals(CardSize.WIDE, findDevice(devices, "device:dev_sonos")?.cardSize)
        }

        @Test
        fun `leads the blind, the fan and the robot with their own types`() {
            assertEquals("cover", mainType("device:dev_blind"))
            assertEquals("fan", mainType("device:dev_fan"))
            assertEquals("vacuum", mainType("device:dev_vacuum"))
        }

        @Test
        fun `gives the robot its battery as a second part`() {
            assertEquals(listOf("vacuum.robot", "sensor.robot_battery"), findDevice(devices, "device:dev_vacuum")?.parts?.map { it.entityId })
        }

        @Test
        fun `has nothing supported for the lock or the garage door`() {
            assertEquals(emptyList<Part>(), findDevice(devices, "device:dev_lock")?.parts)
            assertEquals(emptyList<Part>(), findDevice(devices, "device:dev_garage")?.parts)
        }
    }

    /** Switches and buttons that might open a garage or gate, by their names (Simon's rule, plan 4 task 1). */
    @Nested
    inner class OpenersByName {
        private val types = partTypes + withSwitch + partType("script", 90, CardSize.SMALL, listOf("script", "scene", "input_boolean", "input_button"))
        private fun devicesOf(home: HomeSnapshot) = buildDevices(home, types)

        private fun HomeSnapshot.withNamedEntity(deviceId: String?, entityId: String, name: String?) = copy(
            states = states + (entityId to state(entityId, "off")),
            entities = entities + entry(entityId, deviceId, name = name),
        )

        @Test
        fun `a switch named Garage door hides its whole device`() {
            val home = sampleHome().withNamedEntity("dev_plug_tv", "switch.relay_2", "Garage door")
            assertEquals(emptyList<Part>(), findDevice(devicesOf(home), "device:dev_plug_tv")?.parts)
        }

        @Test
        fun `a button called Open gate with no device is not listed`() {
            val home = sampleHome().withNamedEntity(null, "button.relay_3", "Open gate")
            assertNull(findDevice(devicesOf(home), "entity:button.relay_3"))
        }

        @Test
        fun `script unlock_front_door is not listed`() {
            val home = sampleHome().copy(states = sampleHome().states + ("script.unlock_front_door" to state("script.unlock_front_door", "off")))
            assertNull(findDevice(devicesOf(home), "entity:script.unlock_front_door"))
        }

        @Test
        fun `a device named Garage opener hides even its Toggle switch`() {
            val home = sampleHome()
                .withNamedEntity("dev_plug_tv", "switch.relay_4", "Toggle")
                .changeDevice("dev_plug_tv") { it.copy(nameByUser = "Garage opener") }
            assertEquals(emptyList<Part>(), findDevice(devicesOf(home), "device:dev_plug_tv")?.parts)
        }

        @Test
        fun `the Front door doorbell, a door sensor and a front door switch are still shown`() {
            val home = sampleHome().withNamedEntity("dev_ring", "switch.front_door_siren", "Front door siren")
            assertTrue(findDevice(devicesOf(home), "device:dev_ring")!!.parts.isNotEmpty())
            assertTrue(findDevice(devicesOf(home), "device:dev_ring")!!.parts.any { it.entityId == "switch.front_door_siren" })
        }

        @Test
        fun `the word gateway does not count`() {
            val home = sampleHome().withNamedEntity("dev_plug_tv", "switch.gateway_power", "Gateway power")
            assertTrue(findDevice(devicesOf(home), "device:dev_plug_tv")!!.parts.any { it.entityId == "switch.gateway_power" })
        }

        @Test
        fun `door and open together count, in any order`() {
            assertTrue(isOpenerByName("switch.x", listOf("Door open")))
            assertTrue(isOpenerByName("button.open_shed_door", listOf(null)))
            assertFalse(isOpenerByName("switch.door_light", listOf("Door light")))
        }
    }
}
