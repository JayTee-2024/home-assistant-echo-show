package net.badacid.homeassist.echoshow.devices.vacuum

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.press

/** Start or Pause, Stop and Return to dock under what it is doing; Beep to find it. */
fun vacuumPanel(entity: EntityState): List<PanelRow> {
    val view = readVacuum(entity)
    val ok = view.isAvailable
    val isCleaning = view.activity == "cleaning"
    val rows = mutableListOf(PanelRow(view.words, buildList {
        if (view.canStart && !isCleaning) add(press("Start", start(entity), ok))
        if (view.canPause && isCleaning) add(press("Pause", pause(entity), ok))
        if (view.canStop) add(press("Stop", stop(entity), ok))
        if (view.canReturnHome) add(press("Return to dock", returnHome(entity), ok))
    }))
    if (view.canLocate) rows += PanelRow("Find it", listOf(press("Beep", locate(entity), ok)))
    return rows
}
