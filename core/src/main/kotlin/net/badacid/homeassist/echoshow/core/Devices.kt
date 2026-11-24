/*
 * Sorts Home Assistant's entities into devices and parts.
 *
 * The words, as the design uses them:
 *   device  one physical thing in Home Assistant, such as a doorbell. Also a "thing with no device", such
 *           as a scene, which is treated as a device with a single part.
 *   part    one supported entity of a device: the doorbell's camera, its motion sensor.
 *
 * The picker lists every device this returns. A device with no parts is listed as "Not supported yet".
 * The home screen shows a card for each device the person chose.
 *
 * This file decides only what belongs to what. It knows nothing about cards or screens: the caller passes
 * in the list of part types (see PartType.kt).
 */
package net.badacid.homeassist.echoshow.core

import java.text.Collator
import java.util.Locale

data class Part(
    val entityId: String,
    /** The id of the PartType this entity belongs to: 'light'. */
    val typeId: String,
    val name: String,
)

data class Device(
    /** 'device:<registry id>', or 'entity:<entity id>' for a thing with no device. */
    val id: String,
    val name: String,
    /** null when Home Assistant has it in no area. */
    val roomName: String?,
    /** Supported parts only, in the order the card shows them. The first is the main part. */
    val parts: List<Part>,
    /** null when the device has nothing this app supports. */
    val mainPart: Part?,
    /** The size of the device's card, from its main part. null when there is no main part. */
    val cardSize: CardSize?,
    /**
     * False while one of the device's entities has not reported a state yet, as happens for a minute after
     * Home Assistant starts. Its parts, and so its card size, may then be wrong.
     */
    val isComplete: Boolean,
)

data class Room(val name: String, val devices: List<Device>)

/** The heading for devices that Home Assistant has in no area. */
const val NO_ROOM = "No room"

/**
 * The kinds of cover (Home Assistant's `device_class`) that are safe to control from the Echo. Any other
 * cover, including one that reports no kind, might be a door, garage or gate.
 */
val SAFE_COVER_KINDS = listOf("awning", "blind", "curtain", "damper", "shade", "shutter", "window")

/**
 * Types that are listed even when nothing supports them, so the picker can say "Not supported yet" for a
 * lock, an alarm or a door. Other unsupported things with no device (automations, zones, people) are not
 * listed at all: they would bury the picker.
 */
private val ALWAYS_LISTED_DOMAINS = listOf("lock", "alarm_control_panel", "cover")

/**
 * Names sort as people expect: letters before case, so "Living room Sonos" sits after "Living room heating".
 * A Collator is not safe to share between threads, so each sort makes its own.
 */
private fun nameOrder(): Comparator<String> {
    val collator = Collator.getInstance(Locale.UK)
    return Comparator { a, b -> collator.compare(a, b) }
}

/** A part while it is being collected, with what is needed to put the parts in order. */
private class CollectedPart(val part: Part, val type: PartType, val isBatteryLevel: Boolean)

fun buildDevices(home: HomeSnapshot, partTypes: List<PartType>): List<Device> {
    val registryByEntityId = home.entities.associateBy { it.entityId }
    val devices = mutableListOf<Device>()

    // 1. Real devices, each with the entities the registry says belong to it.
    for (registryDevice in home.devices) {
        if (registryDevice.disabledBy != null) continue

        val entries = home.entities.filter { it.deviceId == registryDevice.id && isShown(it) }
        // A device that can unlock, disarm or open something is never controlled from the Echo, not even
        // through its other entities (a garage opener's "toggle" button, say). Every entity of the device
        // counts here, hidden and disabled ones too: hiding a garage door in Home Assistant does not stop the
        // device's toggle button from opening it.
        val isProtected = home.entities.any {
            it.deviceId == registryDevice.id && isProtectedEntity(it, home.states[it.entityId])
        }
        val collected = if (isProtected) emptyList() else collectParts(entries, home, partTypes)
        val isComplete = entries.all { home.states[it.entityId] != null }
        val firstEntry = entries.firstOrNull()

        devices += makeDevice(
            "device:" + registryDevice.id,
            registryDevice.nameByUser
                ?: registryDevice.name
                ?: firstEntry?.let { entityName(it, home.states[it.entityId]) }
                ?: "Unnamed device",
            roomName(home, registryDevice.areaId),
            collected,
            isComplete,
        )
    }

    // 2. Things with no device: every state whose registry entry names no device, or that has no registry
    //    entry at all (scenes and scripts written in YAML, for example).
    for ((entityId, state) in home.states) {
        val entry = registryByEntityId[entityId]
        if (entry != null && (entry.deviceId != null || !isShown(entry))) continue

        val type = partTypeFor(state, partTypes)
        val isListedAnyway = domainOf(entityId) in ALWAYS_LISTED_DOMAINS
        if (type == null && !isListedAnyway) continue

        val name = if (entry != null) entityName(entry, state) else readText(state, "friendly_name") ?: entityId
        val collected = if (type == null) emptyList() else listOf(CollectedPart(Part(entityId, type.id, name), type, false))

        devices += makeDevice("entity:$entityId", name, roomName(home, entry?.areaId), collected, true)
    }

    return devices
}

/** Rooms in name order with "No room" last; devices in name order within each room. */
fun groupByRoom(devices: List<Device>): List<Room> {
    val byName = devices.groupBy { it.roomName ?: NO_ROOM }
    val roomOrder = nameOrder()
    val names = byName.keys.sortedWith { a, b ->
        when {
            a == NO_ROOM -> 1
            b == NO_ROOM -> -1
            else -> roomOrder.compare(a, b)
        }
    }
    return names.map { name -> Room(name, byName.getValue(name).sortedWith(compareBy(roomOrder) { it.name })) }
}

fun findDevice(devices: List<Device>, id: String): Device? = devices.firstOrNull { it.id == id }

/**
 * True for an entity that can unlock a door, disarm an alarm, or might open a door, garage or gate. The
 * design rules these out entirely: whoever is at the Echo must never be able to do any of those. For covers
 * this errs on the safe side: only the kinds in SAFE_COVER_KINDS are allowed, and a cover that has not
 * reported yet counts as protected.
 */
fun isProtectedEntity(entry: RegistryEntity, state: EntityState?): Boolean {
    val domain = domainOf(entry.entityId)
    if (domain == "lock" || domain == "alarm_control_panel") return true
    if (domain != "cover") return false
    if (state == null) return true
    val kind = readText(state, "device_class")
    return kind == null || kind !in SAFE_COVER_KINDS
}

/** False for entities the person, or the integration, has switched off or hidden in Home Assistant. */
private fun isShown(entry: RegistryEntity) = entry.disabledBy == null && entry.hiddenBy == null

/** A sensor that reports a battery level. These are kept even though they are "diagnostic". */
private fun isBatteryLevel(state: EntityState) =
    domainOf(state.entityId) == "sensor" && readText(state, "device_class") == "battery"

private fun partTypeFor(state: EntityState, partTypes: List<PartType>): PartType? {
    val domain = domainOf(state.entityId)
    return partTypes.firstOrNull { domain in it.domains && it.supports(state) }
}

private fun collectParts(entries: List<RegistryEntity>, home: HomeSnapshot, partTypes: List<PartType>): List<CollectedPart> =
    entries.mapNotNull { entry ->
        // An entity can be in the registry before it has reported anything. Skip it until it does.
        val state = home.states[entry.entityId] ?: return@mapNotNull null

        // Settings and technical readings (signal strength, firmware) stay off the card.
        val battery = isBatteryLevel(state)
        if (entry.entityCategory != null && !battery) return@mapNotNull null

        val type = partTypeFor(state, partTypes) ?: return@mapNotNull null
        CollectedPart(Part(entry.entityId, type.id, entityName(entry, state)), type, battery)
    }

/**
 * Card order: a battery level always comes last, so it never leads a card that has anything else to show;
 * then the most significant type first (lowest mainOrder); then by name.
 */
private fun partOrder(): Comparator<CollectedPart> =
    compareBy<CollectedPart> { it.isBatteryLevel }
        .thenBy { it.type.mainOrder }
        .thenBy(nameOrder()) { it.part.name }

private fun makeDevice(id: String, name: String, room: String?, collected: List<CollectedPart>, isComplete: Boolean): Device {
    val ordered = collected.sortedWith(partOrder())
    val main = ordered.firstOrNull()
    return Device(id, name, room, ordered.map { it.part }, main?.part, main?.type?.cardSize, isComplete)
}

/** The person's name for the entity, then the integration's, then the friendly name, then the id. */
private fun entityName(entry: RegistryEntity, state: EntityState?): String =
    entry.name ?: entry.originalName ?: state?.let { readText(it, "friendly_name") } ?: entry.entityId

private fun roomName(home: HomeSnapshot, areaId: String?): String? =
    if (areaId == null) null else home.areas.firstOrNull { it.areaId == areaId }?.name
