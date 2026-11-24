/*
 * What a card shows: the "face" at the top right (from the main part's type), and the name, room and a
 * line for each other part. A port of the LG app's DeviceCard.tsx, part-lines.ts and each
 * devices/(type)/Card.tsx, as data the card view draws.
 */
package net.badacid.homeassist.echoshow.screens.home

import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE
import net.badacid.homeassist.echoshow.devices.binarysensor.readBinarySensor
import net.badacid.homeassist.echoshow.devices.button.readButton
import net.badacid.homeassist.echoshow.devices.climate.formatTemperature
import net.badacid.homeassist.echoshow.devices.climate.readClimate
import net.badacid.homeassist.echoshow.devices.cover.coverWords
import net.badacid.homeassist.echoshow.devices.cover.readCover
import net.badacid.homeassist.echoshow.devices.deviceLogicFor
import net.badacid.homeassist.echoshow.devices.fan.readFan
import net.badacid.homeassist.echoshow.devices.light.readLight
import net.badacid.homeassist.echoshow.devices.mediaplayer.readMediaPlayer
import net.badacid.homeassist.echoshow.devices.readableName
import net.badacid.homeassist.echoshow.devices.sensor.readSensor
import net.badacid.homeassist.echoshow.devices.switch.readSwitch
import net.badacid.homeassist.echoshow.devices.vacuum.readVacuum

/** The top right of a card. */
data class CardFace(
    /** The state in a word or two: 'On', 'Clair de Lune'. */
    val state: String? = null,
    /** A large reading instead of `state`: a thermostat's room temperature. */
    val big: String? = null,
    /** A unit shown small after `state`: '%', '°C'. */
    val unit: String? = null,
    val details: List<String> = emptyList(),
    /** A light's colour, shown as a dot before its brightness. */
    val colour: Int? = null,
    /** A camera: the card shows its picture. */
    val isPicture: Boolean = false,
)

data class CardContent(
    val deviceId: String,
    /** The main part's type id, or null when there is none. */
    val typeId: String?,
    val icon: String?,
    val face: CardFace,
    val name: String,
    val room: String?,
    val extraLines: List<String>,
    val isActive: Boolean,
    val isUnavailable: Boolean,
    /** Home Assistant no longer has this device: a tap removes the card. */
    val isMissing: Boolean,
    /** The main part's entity, for the camera picture. */
    val mainEntity: EntityState?,
)

fun cardContent(deviceId: String, device: Device?, home: HomeSnapshot, size: CardSize): CardContent {
    val main = device?.mainPart
    val logic = main?.let { deviceLogicFor(it.typeId) }
    val entity = main?.let { home.states[it.entityId] }
    if (device == null || main == null || logic == null || entity == null) {
        return CardContent(
            deviceId = deviceId,
            typeId = null,
            icon = null,
            face = CardFace(),
            name = device?.name ?: "Not available",
            room = if (device == null) "No longer in Home Assistant" else "Nothing here can be shown yet",
            extraLines = if (device == null) listOf("Tap to remove") else emptyList(),
            isActive = false,
            isUnavailable = false,
            isMissing = device == null,
            mainEntity = null,
        )
    }
    return CardContent(
        deviceId = deviceId,
        typeId = logic.id,
        icon = logic.icon,
        face = faceFor(logic.id, entity),
        name = device.name,
        room = device.roomName,
        extraLines = otherPartLines(device, home, if (size == CardSize.SMALL) 1 else 2),
        isActive = logic.isActive(entity),
        isUnavailable = !isAvailable(entity),
        isMissing = false,
        mainEntity = entity,
    )
}

/** A short line for each of the device's other parts, such as 'Battery 74%'; parts with nothing to say are skipped. */
fun otherPartLines(device: Device, home: HomeSnapshot, limit: Int): List<String> {
    val lines = mutableListOf<String>()
    for (part in device.parts.drop(1)) {
        if (lines.size == limit) break
        val logic = deviceLogicFor(part.typeId) ?: continue
        val entity = home.states[part.entityId] ?: continue
        logic.summary(entity)?.let { lines += "${part.name} $it" }
    }
    return lines
}

private fun onOff(available: Boolean, on: Boolean) = if (!available) UNAVAILABLE else if (on) "On" else "Off"

fun faceFor(typeId: String, entity: EntityState): CardFace = when (typeId) {
    "light" -> {
        val view = readLight(entity)
        val rgb = view.rgb
        CardFace(
            state = onOff(view.isAvailable, view.isOn),
            details = listOfNotNull(view.brightnessPercent?.let { "$it%" }),
            colour = if (rgb != null && view.brightnessPercent != null) opaque(rgb[0], rgb[1], rgb[2]) else null,
        )
    }
    "switch" -> readSwitch(entity).let { CardFace(state = onOff(it.isAvailable, it.isOn)) }
    "button" -> readButton(entity).let { CardFace(state = if (it.canPress) it.verb else UNAVAILABLE) }
    "sensor" -> readSensor(entity).let { CardFace(state = it.reading, unit = it.unit) }
    "binary_sensor" -> CardFace(state = readBinarySensor(entity).words)
    "climate" -> {
        val view = readClimate(entity)
        if (!view.isAvailable) {
            CardFace(state = UNAVAILABLE)
        } else {
            val doing = readableName(view.hvacAction ?: view.hvacMode)
            val target = if (view.hvacMode == "off") "Off" else "Set to " + formatTemperature(view.targetTemperature)
            CardFace(big = formatTemperature(view.currentTemperature), details = listOf("$target · $doing"))
        }
    }
    "media_player" -> {
        val view = readMediaPlayer(entity)
        if (!view.isAvailable) {
            CardFace(state = UNAVAILABLE)
        } else {
            CardFace(
                state = view.title ?: readableName(view.state),
                details = listOfNotNull(view.artist, if (view.title != null) readableName(view.state) else null),
            )
        }
    }
    "cover" -> {
        val view = readCover(entity)
        CardFace(
            state = if (view.isAvailable) coverWords(view) else UNAVAILABLE,
            details = if (view.isAvailable && view.position != null) listOf("${view.position}%") else emptyList(),
        )
    }
    "fan" -> {
        val view = readFan(entity)
        CardFace(
            state = onOff(view.isAvailable, view.isOn),
            details = if (view.isOn && view.speedPercent != null) listOf("${view.speedPercent}%") else emptyList(),
        )
    }
    "vacuum" -> CardFace(state = readVacuum(entity).words)
    "camera" -> CardFace(isPicture = true)
    else -> CardFace(state = entity.state)
}

/** An opaque colour from red, green and blue (0 to 255), as Android stores colours. */
private fun opaque(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
