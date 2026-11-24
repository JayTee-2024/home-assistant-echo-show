package net.badacid.homeassist.echoshow.devices.button

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.domainOf

data class ButtonView(
    /** False only when unavailable: "unknown" just means it was never pressed. */
    val canPress: Boolean,
    /** What the button says: 'Run' for a script, 'Activate' for a scene, 'Press' otherwise. */
    val verb: String,
)

fun readButton(entity: EntityState): ButtonView {
    val verb = when (domainOf(entity.entityId)) {
        "script" -> "Run"
        "scene" -> "Activate"
        else -> "Press"
    }
    return ButtonView(canPress = entity.state != "unavailable", verb = verb)
}
