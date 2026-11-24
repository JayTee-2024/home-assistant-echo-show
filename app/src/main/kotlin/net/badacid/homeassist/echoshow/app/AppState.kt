package net.badacid.homeassist.echoshow.app

import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.EMPTY_HOME
import net.badacid.homeassist.echoshow.core.EMPTY_SAVED_STATE
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.core.SavedState

/** Which screen the app is on. */
enum class Phase {
    /** Reading what was saved. */
    STARTING,

    /** The sign-in screen. */
    SIGNED_OUT,

    /** Has a saved sign-in and is opening the connection, retrying if it fails. */
    CONNECTING,

    /** Connected: the home screen, perhaps with Settings or a panel over it. */
    HOME,
}

/** What is open over the home screen. */
sealed interface Overlay {
    data object None : Overlay
    data object Settings : Overlay

    /** The "Reorganise layout" mode, opened from Settings. */
    data object Reorganise : Overlay
    data class Panel(val deviceId: String) : Overlay
}

/** Everything the screens draw. Made afresh by AppModel on every change. */
data class AppState(
    val phase: Phase = Phase.STARTING,
    val saved: SavedState = EMPTY_SAVED_STATE,
    /** A message for the sign-in screen, such as why the person was signed out. */
    val notice: String? = null,
    /** A long-lived token Home Assistant refused, offered back on the sign-in screen to correct. */
    val refusedToken: String? = null,
    /** Why the last connection attempt failed, while the connecting screen retries. */
    val connectFailure: OpenFailure? = null,
    /** The home with predicted states laid over it. */
    val shownHome: HomeSnapshot = EMPTY_HOME,
    val devices: List<Device> = emptyList(),
    val status: ConnectionStatus = ConnectionStatus.CONNECTED,
    val overlay: Overlay = Overlay.None,
    val page: Int = 0,
    /** A short message at the bottom of the screen. */
    val toast: String? = null,
    val userName: String = "",
)
