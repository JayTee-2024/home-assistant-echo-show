/*
 * The app's brain: which screen is showing, what is saved, and what the person's touches do. A port of the
 * logic in the LG app's src/app/App.tsx. It draws nothing; the screens draw `state` and call back here.
 *
 * Use it from one thread (the main thread in the app): `scope` must run its work on that thread too.
 */
package net.badacid.homeassist.echoshow.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.CommandRefused
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.EMPTY_HOME
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.OpenFailed
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.core.PendingStates
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.SavedState
import net.badacid.homeassist.echoshow.core.SignInKind
import net.badacid.homeassist.echoshow.core.addCard
import net.badacid.homeassist.echoshow.core.afterSignIn
import net.badacid.homeassist.echoshow.core.afterSignOut
import net.badacid.homeassist.echoshow.core.applyPending
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.core.hasCard
import net.badacid.homeassist.echoshow.core.pageCount
import net.badacid.homeassist.echoshow.core.parseSavedState
import net.badacid.homeassist.echoshow.core.removeCard
import net.badacid.homeassist.echoshow.core.serialiseSavedState
import net.badacid.homeassist.echoshow.core.settle
import net.badacid.homeassist.echoshow.core.withCurrentSizes
import net.badacid.homeassist.echoshow.core.withPending
import net.badacid.homeassist.echoshow.core.withoutPending
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.devices.OkAction
import net.badacid.homeassist.echoshow.devices.deviceLogicFor

const val SIGN_IN_REJECTED = "Home Assistant did not accept the sign-in. Please sign in again."
const val TOKEN_REJECTED = "Home Assistant did not accept that access token. Check it and try again."
const val NOT_CONNECTED = "Not connected to Home Assistant."

/** Where the saved state's text is kept. The app's is encrypted (platform/SavedTextStore.kt). */
interface SavedTextStorage {
    fun read(): String?
    fun write(text: String)
}

/** How often to check for predicted states that have waited too long (see PendingStates.kt). */
private const val PENDING_CHECK_MS = 1_000L

class AppModel(
    private val storage: SavedTextStorage,
    private val openConnection: suspend (SavedSignIn, onTokens: (SavedSignIn) -> Unit) -> HomeConnection,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val retryDelayMs: Long = 5_000,
    private val signOutLimitMs: Long = 5_000,
    private val toastMs: Long = 4_000,
) {
    private val flow = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = flow.asStateFlow()

    private var saved: SavedState = parseSavedState(storage.read())
    private var home: HomeSnapshot = EMPTY_HOME
    private var pending: PendingStates = emptyMap()
    private var connection: HomeConnection? = null
    private var connectionJobs: List<Job> = emptyList()
    private var retry: Job? = null
    private var toastJob: Job? = null
    private var pendingCheck: Job? = null
    private var started = false

    /**
     * Counts connection attempts. Anything an attempt does later is ignored once a newer attempt has
     * started or the person has signed out, so a slow attempt cannot pull them off the sign-in screen.
     */
    private var attempt = 0

    /** Connects with the saved sign-in, or shows the sign-in screen. Only the first call does anything. */
    fun start() {
        if (started) return
        started = true
        // The app starts locked when a PIN is set, as the LG app does.
        if (saved.pin != null) publish { it.copy(isLocked = true) }
        val signIn = saved.signIn
        if (signIn == null) publish { it.copy(phase = Phase.SIGNED_OUT) } else connect(signIn)
    }

    /** The sign-in screen finished. */
    fun signedIn(signIn: SavedSignIn) {
        updateSaved { afterSignIn(it, signIn) }
        publish { it.copy(notice = null, refusedToken = null) }
        connect(signIn)
    }

    /** The connecting screen's "Sign in again". */
    fun signInAgain() = signOutLocally(null)

    /** A tap on a card: the main part's everyday action. */
    fun pressCard(deviceId: String) {
        val devices = flow.value.devices
        val device = findDevice(devices, deviceId)
        // A card for a device Home Assistant no longer has: a tap removes it, as the card says.
        if (device == null) {
            updateSaved { it.copy(layout = removeCard(it.layout, deviceId)) }
            return
        }
        val main = device.mainPart ?: return
        val logic = deviceLogicFor(main.typeId) ?: return
        val entity = flow.value.shownHome.states[main.entityId] ?: return
        when (val action = logic.okAction(entity)) {
            OkAction.Nothing -> Unit
            OkAction.OpenPanel -> openPanel(deviceId)
            is OkAction.Run -> run(action.action)
        }
    }

    /** A long press on a card: its panel. */
    fun holdCard(deviceId: String) = openPanel(deviceId)

    private fun openPanel(deviceId: String) {
        // A card for a device Home Assistant no longer has has no panel to open.
        if (findDevice(flow.value.devices, deviceId) == null) return
        openOverlay(Overlay.Panel(deviceId))
    }

    /** The open connection, for live video, or null while there is none. */
    fun currentConnection(): HomeConnection? = connection

    fun openSettings() = openOverlay(Overlay.Settings)

    /** Nothing opens while the PIN lock is up: a late tap or hold timer must not draw over the lock. */
    private fun openOverlay(overlay: Overlay) {
        if (flow.value.isLocked) return
        publish { it.copy(overlay = overlay) }
    }

    // ---- The PIN lock ----

    /** Settings' Screen section: seconds before the screen blanks, 0 never. */
    fun setBlankAfter(seconds: Int) = updateSaved { it.copy(blankAfterSeconds = seconds.coerceAtLeast(0)) }

    /** Settings' PIN section: a new PIN, or null to turn the lock off. */
    fun setPin(pin: String?) = updateSaved { it.copy(pin = pin, pinWrongTries = 0, pinWaitUntil = 0) }

    /** Locks the app (it left the screen), closing whatever was open. Does nothing without a PIN. */
    fun lock() {
        if (saved.pin == null) return
        publish { it.copy(isLocked = true, overlay = Overlay.None) }
    }

    /** The lock screen took the right PIN: the wrong tries are forgotten. */
    fun unlock() {
        notePinTries(0, 0)
        publish { it.copy(isLocked = false) }
    }

    /** The lock screen's wrong tries and wait, saved so that restarting the Echo does not reset them. */
    fun notePinTries(wrongTries: Int, waitUntil: Long) {
        if (saved.pinWrongTries == wrongTries && saved.pinWaitUntil == waitUntil) return
        updateSaved { it.copy(pinWrongTries = wrongTries, pinWaitUntil = waitUntil) }
    }

    /** Settings' "Reorganise layout". */
    fun openReorganise() = openOverlay(Overlay.Reorganise)

    /** The reorganise mode's Done: keeps the new layout and goes back to the home screen. */
    fun saveLayout(layout: Layout) {
        updateSaved { it.copy(layout = layout) }
        publish { it.copy(overlay = Overlay.None) }
    }

    fun closeOverlay() = publish { it.copy(overlay = Overlay.None) }

    /** The system Back: closes what is open. False when there was nothing to close. */
    fun back(): Boolean {
        if (flow.value.overlay == Overlay.None) return false
        closeOverlay()
        return true
    }

    fun showPage(page: Int) {
        val last = pageCount(saved.layout) - 1
        publish { it.copy(page = page.coerceIn(0, last)) }
    }

    /** In the picker: puts the device on the home screen, or takes it off. */
    fun toggleDevice(device: Device) {
        if (hasCard(saved.layout, device.id)) {
            // Always allowed, even for a device that is no longer supported, so no card can get stuck.
            updateSaved { it.copy(layout = removeCard(it.layout, device.id)) }
            return
        }
        val size = device.cardSize
        if (size == null) {
            showToast("${device.name} is not supported yet.")
            return
        }
        updateSaved { it.copy(layout = addCard(it.layout, device.id, size)) }
    }

    /** Carries out one action: show its predicted state at once, and send it to Home Assistant. */
    fun run(action: Action) {
        val current = connection
        if (current == null || flow.value.status != ConnectionStatus.CONNECTED) {
            showToast(NOT_CONNECTED)
            return
        }
        pending = withPending(pending, action.predicted, clock())
        refresh()
        watchPending()
        scope.launch {
            try {
                current.call(action.call)
            } catch (refused: CommandRefused) {
                // Put the card back to the real state and say why.
                pending = withoutPending(pending, action.call.entityId)
                refresh()
                showToast(refused.reason)
            }
        }
    }

    /** Signs out for real: Home Assistant cancels this Echo's sign-in, then the Echo forgets it. */
    /** True from Sign out until the sign-in screen shows: a second Sign out is ignored. */
    private var signingOut = false

    fun signOut() {
        if (signingOut) return
        signingOut = true
        val current = connection
        var done = false
        val finish = {
            if (!done) {
                done = true
                signOutLocally(null)
            }
        }
        // Home Assistant gets a few seconds to cancel the sign-in. After that the Echo forgets it anyway.
        val limit = scope.launch {
            delay(signOutLimitMs)
            finish()
        }
        scope.launch {
            current?.signOut()
            limit.cancel()
            finish()
        }
    }

    // ---- Connecting ----

    private fun connect(signIn: SavedSignIn) {
        retry?.cancel()
        publish { it.copy(phase = Phase.CONNECTING) }
        attempt += 1
        val thisAttempt = attempt
        val isCurrent = { attempt == thisAttempt }
        scope.launch {
            val opened = try {
                openConnection(signIn) { fresh -> if (isCurrent()) updateSaved { it.copy(signIn = fresh) } }
            } catch (failed: OpenFailed) {
                if (!isCurrent()) return@launch
                if (failed.failure == OpenFailure.SIGN_IN_REJECTED) {
                    val isToken = signIn.kind == SignInKind.LONG_LIVED_TOKEN
                    // One publish, so the sign-in screen is drawn with the token to correct, never without it.
                    signOutLocally(if (isToken) TOKEN_REJECTED else SIGN_IN_REJECTED, if (isToken) signIn.accessToken else null)
                    return@launch
                }
                // Home Assistant did not answer, or was not ready. Say so, and try again shortly, with the
                // newest saved tokens.
                publish { it.copy(connectFailure = failed.failure) }
                retry = scope.launch {
                    delay(retryDelayMs)
                    val latest = saved.signIn
                    if (latest != null && isCurrent()) connect(latest)
                }
                return@launch
            }
            if (!isCurrent()) {
                opened.close()
                return@launch
            }
            useConnection(opened)
        }
    }

    private fun useConnection(opened: HomeConnection) {
        connection = opened
        home = opened.home.value
        publish { it.copy(phase = Phase.HOME, connectFailure = null, userName = opened.userName, status = opened.status.value) }
        refresh()
        connectionJobs = listOf(
            scope.launch {
                opened.home.collect { next ->
                    if (next === home) return@collect
                    // A new report from Home Assistant settles the predictions it covers.
                    pending = settle(pending, home, next, clock())
                    home = next
                    refresh()
                }
            },
            scope.launch {
                opened.status.collect { next ->
                    if (next == ConnectionStatus.SIGN_IN_REJECTED) {
                        signOutLocally(SIGN_IN_REJECTED)
                    } else {
                        publish { it.copy(status = next) }
                    }
                }
            },
        )
    }

    /** Forgets the sign-in on this Echo and shows the sign-in screen. The layout is kept. */
    private fun signOutLocally(reason: String?, refusedToken: String? = null) {
        signingOut = false
        // Ends any attempt in progress (see `attempt`).
        attempt += 1
        retry?.cancel()
        connectionJobs.forEach { it.cancel() }
        connectionJobs = emptyList()
        connection?.close()
        connection = null
        home = EMPTY_HOME
        pending = emptyMap()
        updateSaved(::afterSignOut)
        publish {
            it.copy(
                phase = Phase.SIGNED_OUT,
                connectFailure = null,
                overlay = Overlay.None,
                page = 0,
                notice = reason,
                refusedToken = refusedToken,
                isLocked = false,
                status = ConnectionStatus.CONNECTED,
            )
        }
        refresh()
    }

    // ---- Keeping things in step ----

    /** Works out the shown home and devices again, and everything that depends on them. */
    private fun refresh() {
        val shown = applyPending(home, pending)
        val devices = if (connection == null) emptyList() else buildDevices(shown, DEVICE_LOGIC)

        if (connection != null) {
            // Keep the saved layout in step with the devices as they are now. A device that has not fully
            // reported yet may look smaller than it is, so its card is left alone until it has.
            val sizeOf = { deviceId: String ->
                findDevice(devices, deviceId)?.takeIf { it.isComplete }?.cardSize
            }
            if (withCurrentSizes(saved.layout, sizeOf) !== saved.layout) {
                updateSaved { it.copy(layout = withCurrentSizes(it.layout, sizeOf)) }
            }
        }
        publish { current ->
            // An open panel closes when its device disappears.
            val overlay = current.overlay
            val keepOverlay = overlay !is Overlay.Panel || findDevice(devices, overlay.deviceId) != null
            current.copy(shownHome = shown, devices = devices, overlay = if (keepOverlay) overlay else Overlay.None)
        }
    }

    /** While any press is waiting for confirmation, checks each second for ones that have waited too long. */
    private fun watchPending() {
        if (pendingCheck?.isActive == true) return
        pendingCheck = scope.launch {
            while (pending.isNotEmpty()) {
                delay(PENDING_CHECK_MS)
                val settled = settle(pending, home, home, clock())
                if (settled !== pending) {
                    pending = settled
                    refresh()
                }
            }
        }
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        publish { it.copy(toast = message) }
        toastJob = scope.launch {
            delay(toastMs)
            publish { it.copy(toast = null) }
        }
    }

    /** Changes what is saved, in the state and in storage together. */
    private fun updateSaved(change: (SavedState) -> SavedState) {
        saved = change(saved)
        try {
            storage.write(serialiseSavedState(saved))
        } catch (_: Exception) {
            // The app keeps working with what it has; the next change tries to save again.
        }
        // If the last page empties, step back to one that exists.
        val last = pageCount(saved.layout) - 1
        publish { it.copy(saved = saved, page = minOf(it.page, last)) }
    }

    private fun publish(change: (AppState) -> AppState) {
        flow.value = change(flow.value).copy(saved = saved)
    }
}
