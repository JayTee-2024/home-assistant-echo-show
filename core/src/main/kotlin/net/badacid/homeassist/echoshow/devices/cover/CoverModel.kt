package net.badacid.homeassist.echoshow.devices.cover

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.hasFeature
import net.badacid.homeassist.echoshow.core.readNumber
import kotlin.math.roundToInt

private const val OPEN = 1
private const val CLOSE = 2
private const val SET_POSITION = 4
private const val STOP = 8

data class CoverView(
    val isAvailable: Boolean,
    /** 'open', 'closed', 'opening', 'closing'. */
    val state: String,
    /** 0 (closed) to 100 (open). null when the cover does not report it. */
    val position: Int?,
    val canOpen: Boolean,
    val canClose: Boolean,
    val canStop: Boolean,
    val canSetPosition: Boolean,
)

fun readCover(entity: EntityState) = CoverView(
    // 'unknown' is normal for a blind that cannot report where it is (many radio blinds), and Open and
    // Close must still work for it. Only 'unavailable' means it cannot be reached.
    isAvailable = entity.state != "unavailable",
    state = entity.state,
    position = readNumber(entity, "current_position")?.roundToInt(),
    canOpen = hasFeature(entity, OPEN),
    canClose = hasFeature(entity, CLOSE),
    canStop = hasFeature(entity, STOP),
    canSetPosition = hasFeature(entity, SET_POSITION),
)

/** The cover's state in words: 'Open', 'Closing…', 'Position unknown'. */
fun coverWords(view: CoverView): String = when (view.state) {
    "opening" -> "Opening…"
    "closing" -> "Closing…"
    "closed" -> "Closed"
    "open" -> "Open"
    "unknown" -> "Position unknown"
    else -> view.state.replaceFirstChar { it.uppercaseChar() }
}
