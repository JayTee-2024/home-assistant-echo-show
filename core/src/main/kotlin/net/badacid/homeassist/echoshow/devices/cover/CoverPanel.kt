package net.badacid.homeassist.echoshow.devices.cover

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press

private val POSITIONS = listOf(0, 25, 50, 75, 100)

/** Open, Stop and Close under what the cover is doing, and five positions when it can be set. */
fun coverPanel(entity: EntityState): List<PanelRow> {
    val view = readCover(entity)
    val ok = view.isAvailable
    val rows = mutableListOf(PanelRow(coverWords(view), buildList {
        if (view.canOpen) add(press("Open", open(entity), ok))
        if (view.canStop) add(press("Stop", stop(entity), ok))
        if (view.canClose) add(press("Close", close(entity), ok))
    }))
    if (view.canSetPosition) {
        rows += PanelRow("Position", POSITIONS.map { press("$it%", setPosition(entity, it), ok, isCurrent = view.position == it) })
    }
    return rows
}
