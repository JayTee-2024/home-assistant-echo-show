package net.badacid.homeassist.echoshow.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.Room
import net.badacid.homeassist.echoshow.core.hasCard
import net.badacid.homeassist.echoshow.devices.deviceLogicFor
import net.badacid.homeassist.echoshow.screens.IconView
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/** What Settings draws. The app makes it from its AppState (screens do not depend on the app). */
data class SettingsScreenState(
    val rooms: List<Room>,
    val layout: Layout,
    val address: String,
    val userName: String,
    /** Signed in with a long-lived access token, which signing out cannot cancel. */
    val isToken: Boolean,
    val version: String,
)

interface SettingsCallbacks {
    fun onClose()

    /** Leaves Settings for the "Reorganise layout" mode. */
    fun onReorganise()

    /** Adds the device's card, or removes it. For a device with nothing supported, the app says so. */
    fun onToggleDevice(device: Device)
    fun onSignOut()
    fun onOpenLink(address: String)
}

private enum class Section(val label: String) { DEVICES("Devices"), CONNECTION("Connection"), ABOUT("About") }

const val GAMES_ADDRESS = "https://gamesunleashed.com"
private const val SOURCE_ADDRESS = "github.com/JayTee-2024/home-assistant-echo-show"

/**
 * Settings (the LG app's SettingsScreen.tsx): full screen over the home screen, its sections listed on the
 * left with the version beneath, the open section on the right. Reorganise layout and the PIN lock come in plan 4.
 */
@SuppressLint("ViewConstructor")
class SettingsView(context: Context, private val callbacks: SettingsCallbacks) : FrameLayout(context) {
    private val dp = { value: Float -> Theme.dp(context, value) }
    private val sectionButtons = mutableMapOf<Section, TextView>()
    private val version = Theme.text(context, "", Theme.SMALL_SP, Theme.TEXT_DIM)
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { addView(content); isFillViewport = false }
    private var section = Section.DEVICES
    private var state: SettingsScreenState? = null
    private var isConfirmingSignOut = false
    private var isSigningOut = false

    init {
        setBackgroundColor(Theme.BACKGROUND)
        isClickable = true
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Widgets.titleBar(context, "Settings", Widgets.iconButton(context, Icons.CLOSE, "Close") { callbacks.onClose() }), Widgets.matchWidth())
        val body = LinearLayout(context)
        val nav = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(8f), dp(16f))
            fun sectionButton(label: String, onPress: () -> Unit) = Theme.text(context, label, Theme.BODY_SP, Theme.TEXT_DIM).apply {
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(Theme.TOUCH_DP)
                setPadding(dp(16f), 0, dp(16f), 0)
                isClickable = true
                setOnClickListener { onPress() }
            }
            for (each in Section.entries) {
                val button = sectionButton(each.label) { open(each) }
                sectionButtons[each] = button
                addView(button, Widgets.matchWidth().apply { bottomMargin = dp(4f) })
                // As in the LG app, "Reorganise layout" sits under Devices and leaves Settings.
                if (each == Section.DEVICES) {
                    addView(sectionButton("Reorganise layout") { callbacks.onReorganise() }, Widgets.matchWidth().apply { bottomMargin = dp(4f) })
                }
            }
            addView(version, Widgets.matchWidth().apply { topMargin = dp(16f); leftMargin = dp(16f) })
        }
        body.addView(nav, LinearLayout.LayoutParams(dp(240f), LayoutParams.MATCH_PARENT))
        body.addView(scroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        column.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.setPadding(dp(24f), dp(8f), dp(32f), dp(32f))
    }

    fun show(state: SettingsScreenState) {
        val before = this.state
        this.state = state
        version.text = "Version ${state.version}"
        // Redraw only when what is shown changed: Home Assistant reports often, and a row replaced between a
        // finger going down and coming up would lose the tap. The other sections do not change while open.
        val listChanged = before == null || before.rooms != state.rooms || before.layout != state.layout
        if (before == null || (section == Section.DEVICES && listChanged)) draw()
    }

    private fun open(each: Section) {
        if (each != section) {
            section = each
            isConfirmingSignOut = false
            scroll.scrollTo(0, 0)
        }
        draw()
    }

    private fun draw() {
        val state = state ?: return
        for ((each, button) in sectionButtons) {
            val current = each == section
            button.background = if (current) Theme.roundedPanel(context, Theme.SURFACE_RAISED, 12f) else null
            button.setTextColor(if (current) Theme.TEXT else Theme.TEXT_DIM)
        }
        content.removeAllViews()
        content.addView(Theme.text(context, section.label, Theme.HEADING_SP, bold = true), Widgets.matchWidth())
        when (section) {
            Section.DEVICES -> drawDevices(state)
            Section.CONNECTION -> drawConnection(state)
            Section.ABOUT -> drawAbout(state)
        }
    }

    private fun note(words: String) =
        content.addView(Theme.text(context, words, Theme.BODY_SP, Theme.TEXT_DIM), Widgets.matchWidth().apply { topMargin = dp(12f) })

    private fun fact(label: String, value: String) {
        content.addView(Theme.text(context, label, Theme.SMALL_SP, Theme.TEXT_DIM), Widgets.matchWidth().apply { topMargin = dp(16f) })
        content.addView(Theme.text(context, value, Theme.BODY_SP), Widgets.matchWidth())
    }

    // ---- Devices (DevicePicker.tsx) ----

    private fun drawDevices(state: SettingsScreenState) {
        note("Choose what to show on the home screen. Devices are added to Home Assistant in Home Assistant itself.")
        if (state.rooms.isEmpty()) {
            note("Home Assistant has no devices yet. Add them in Home Assistant first.")
            return
        }
        for (room in state.rooms) {
            content.addView(Theme.text(context, room.name, Theme.SMALL_SP, Theme.TEXT_DIM, bold = true), Widgets.matchWidth().apply { topMargin = dp(20f); bottomMargin = dp(4f) })
            for (device in room.devices) content.addView(deviceRow(device, hasCard(state.layout, device.id)), Widgets.matchWidth().apply { bottomMargin = dp(6f) })
        }
    }

    private fun deviceRow(device: Device, isOnScreen: Boolean): View {
        val logic = device.mainPart?.let { deviceLogicFor(it.typeId) }
        val (typeWords, stateWords) = when {
            logic == null && isOnScreen -> "Not supported" to "Remove"
            logic == null -> null to "Not supported yet"
            else -> logic.label to if (isOnScreen) "On screen ✓" else "Add"
        }
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), 0, dp(16f), 0)
            val on = isOnScreen
            background = pressable(if (on) Theme.AMBER_DIM else Theme.SURFACE, on)
            if (logic == null && !isOnScreen) alpha = 0.55f
            val icon = FrameLayout(context)
            logic?.icon?.let { icon.addView(IconView(context, it, if (on) Theme.AMBER else Theme.TEXT)) }
            addView(icon, LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { rightMargin = dp(16f) })
            addView(Theme.text(context, device.name, Theme.BODY_SP).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            typeWords?.let { addView(Theme.text(context, it, Theme.SMALL_SP, Theme.TEXT_DIM), Widgets.wrap().apply { leftMargin = dp(16f) }) }
            addView(Theme.text(context, stateWords, Theme.BODY_SP, if (on) Theme.AMBER else Theme.TEXT_DIM).apply { gravity = Gravity.END; minWidth = dp(130f) }, Widgets.wrap().apply { leftMargin = dp(16f) })
            isClickable = true
            setOnClickListener { callbacks.onToggleDevice(device) }
        }
    }

    private fun pressable(colour: Int, on: Boolean) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), Theme.roundedPanel(context, Theme.SURFACE_RAISED, 12f))
        addState(intArrayOf(), Theme.roundedPanel(context, colour, 12f, if (on) Theme.AMBER else null))
    }

    // ---- Connection (ConnectionSection.tsx) ----

    private fun drawConnection(state: SettingsScreenState) {
        fact("Home Assistant address", state.address)
        fact("Signed in as", state.userName)
        val buttons = LinearLayout(context)
        if (isConfirmingSignOut) {
            note("Sign out of Home Assistant on this Echo? Your layout is kept for the next time you sign in.")
            if (state.isToken) {
                note("Your access token keeps working until you delete it in Home Assistant: open your profile there, then Security, then Long-lived access tokens.")
            }
            buttons.addView(Widgets.button(context, if (isSigningOut) "Signing out…" else "Sign out") {
                if (!isSigningOut) {
                    isSigningOut = true
                    draw()
                    callbacks.onSignOut()
                }
            })
            buttons.addView(Widgets.button(context, "Cancel", Widgets.ButtonStyle.OUTLINE) {
                isConfirmingSignOut = false
                draw()
            }, Widgets.wrap().apply { leftMargin = dp(12f) })
        } else {
            buttons.addView(Widgets.button(context, "Sign out", Widgets.ButtonStyle.OUTLINE) {
                isConfirmingSignOut = true
                draw()
            })
        }
        content.addView(buttons, Widgets.matchWidth().apply { topMargin = dp(24f) })
    }

    // ---- About (AboutSection.tsx) ----

    private fun drawAbout(state: SettingsScreenState) {
        val app = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Theme.text(context, "Home Assist Echo Show", Theme.HEADING_SP, bold = true))
            addView(Theme.text(context, "Version ${state.version}", Theme.BODY_SP, Theme.TEXT_DIM))
        }
        content.addView(app, Widgets.matchWidth().apply { topMargin = dp(12f) })
        note(
            "Control your Home Assistant home from the Echo's screen. Choose devices in Settings and they appear as cards: " +
                "lights, plugs, heating, speakers, blinds, fans, robot vacuums, cameras and more.",
        )
        note(
            "This Echo talks straight to your own Home Assistant. Nothing is sent anywhere else, and your password is never " +
                "stored. Locks, alarms, doors, garage doors and gates are deliberately not shown. Switches, buttons, scripts " +
                "and scenes are: if any of yours controls something sensitive, turn on the PIN lock in Settings.",
        )
        fact("Help, updates and source code", SOURCE_ADDRESS)
        fact("Licence", "Apache License 2.0")

        val promo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(18f))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), Theme.roundedPanel(context, Theme.SURFACE_RAISED, 16f, Theme.AMBER))
                addState(intArrayOf(), Theme.roundedPanel(context, Theme.SURFACE, 16f, Theme.AMBER_DIM))
            }
            addView(Theme.text(context, "Also from the same developer", Theme.SMALL_SP, Theme.TEXT_DIM))
            addView(Theme.text(context, "Games Unleashed", Theme.HEADING_SP, Theme.AMBER, bold = true))
            addView(
                Theme.text(context, "Free games for a rainy day. Thirty classic games in 3D: cards, board games, pub games, puzzles and arcade. Play against the computer or with friends online.", Theme.BODY_SP, Theme.TEXT_DIM),
                Widgets.matchWidth().apply { topMargin = dp(6f) },
            )
            addView(Theme.text(context, GAMES_ADDRESS, Theme.BODY_SP, Theme.AMBER), Widgets.matchWidth().apply { topMargin = dp(8f) })
            isClickable = true
            setOnClickListener { callbacks.onOpenLink(GAMES_ADDRESS) }
        }
        content.addView(promo, Widgets.matchWidth().apply { topMargin = dp(24f) })
    }

}
