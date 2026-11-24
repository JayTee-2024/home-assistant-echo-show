package net.badacid.homeassist.echoshow.devices.vacuum

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.hasFeature
import net.badacid.homeassist.echoshow.core.isAvailable
import net.badacid.homeassist.echoshow.devices.UNAVAILABLE
import net.badacid.homeassist.echoshow.devices.readableName

private const val PAUSE = 4
private const val STOP = 8
private const val RETURN_HOME = 16
private const val LOCATE = 512
private const val START = 8192

private val WORDS = mapOf(
    "cleaning" to "Cleaning",
    "docked" to "Docked",
    "returning" to "Returning to dock",
    "idle" to "Idle",
    "paused" to "Paused",
    "error" to "Needs attention",
)

data class VacuumView(
    val isAvailable: Boolean,
    val activity: String,
    val words: String,
    val canStart: Boolean,
    val canPause: Boolean,
    val canStop: Boolean,
    val canReturnHome: Boolean,
    val canLocate: Boolean,
)

fun readVacuum(entity: EntityState): VacuumView {
    val available = isAvailable(entity)
    return VacuumView(
        isAvailable = available,
        activity = entity.state,
        words = if (available) WORDS[entity.state] ?: readableName(entity.state) else UNAVAILABLE,
        canStart = hasFeature(entity, START),
        canPause = hasFeature(entity, PAUSE),
        canStop = hasFeature(entity, STOP),
        canReturnHome = hasFeature(entity, RETURN_HOME),
        canLocate = hasFeature(entity, LOCATE),
    )
}
