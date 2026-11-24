/*
 * What every device type provides, apart from its card and panel views (those live in the app module, in
 * the same package name). Each type is one folder: <Type>Model (reading), <Type>Actions (what controls
 * send) and <Type>Logic (the type itself). Registry.kt lists them all.
 */
package net.badacid.homeassist.echoshow.devices

import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.PartType
import net.badacid.homeassist.echoshow.core.ServiceCall

/**
 * One thing the person did: what to send to Home Assistant, and what the entity should look like straight
 * away. The app shows `predicted` at once and replaces it with the real state when Home Assistant reports
 * it, so a press never feels slow.
 */
data class Action(val call: ServiceCall, val predicted: EntityState)

/** What a tap on the card does. */
sealed interface OkAction {
    /** Run this action. */
    data class Run(val action: Action) : OkAction

    /** Open the device's panel: the type has no single everyday action. */
    data object OpenPanel : OkAction

    /** Nothing: a sensor, or an entity that is unavailable. */
    data object Nothing : OkAction
}

/** How the panel opens when this type is the device's main part. */
enum class PanelLayout {
    /** A box over the home screen. */
    BOX,

    /** The whole screen, with the device's name and a close button across the top. The camera uses it. */
    FULL_SCREEN,
}

interface DeviceLogic : PartType {
    /** The type's name in the device picker: 'Light'. */
    val label: String

    /**
     * The type's icon, as SVG path data drawn on a 24×24 grid with a 2-unit stroke
     * (the style of https://feathericons.com). Shown on the card and in the picker.
     */
    val icon: String

    /** What a tap on the card does. */
    fun okAction(entity: EntityState): OkAction

    /** True when the entity counts as "on", which the card marks in amber. */
    fun isActive(entity: EntityState): Boolean

    /** The entity in a few words, for when it is not the card's main part: '21.5 °C', 'On'. null when there is nothing worth a line. */
    fun summary(entity: EntityState): String?

    val panelLayout: PanelLayout get() = PanelLayout.BOX

    /** The rows of the type's box panel (devices/Panel.kt). */
    fun panel(entity: EntityState): List<PanelRow> = emptyList()
}
