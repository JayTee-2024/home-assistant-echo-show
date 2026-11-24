package net.badacid.homeassist.echoshow.testsupport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.readEntityState
import net.badacid.homeassist.echoshow.core.readRegistryAreas
import net.badacid.homeassist.echoshow.core.readRegistryDevices
import net.badacid.homeassist.echoshow.core.readRegistryEntities

/** The sample home from fixtures/sample-home.json, as the app sees it. */
fun sampleHome(): HomeSnapshot {
    val text = FakeHome::class.java.getResource("/sample-home.json")!!.readText()
    val raw = Json.parseToJsonElement(text).jsonObject
    return HomeSnapshot(
        states = (raw["states"] as JsonArray).mapNotNull { readEntityState(it) }.associateBy { it.entityId },
        entities = readRegistryEntities(raw["entities"]),
        devices = readRegistryDevices(raw["devices"]),
        areas = readRegistryAreas(raw["areas"]),
    )
}
