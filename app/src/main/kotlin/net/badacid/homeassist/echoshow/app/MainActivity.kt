package net.badacid.homeassist.echoshow.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import net.badacid.homeassist.echoshow.devices.PanelLayout
import net.badacid.homeassist.echoshow.devices.deviceLogicFor
import net.badacid.homeassist.echoshow.platform.LiveVideoSession
import net.badacid.homeassist.echoshow.screens.camera.CameraView
import net.badacid.homeassist.echoshow.screens.camera.LiveSession
import net.badacid.homeassist.echoshow.screens.camera.LiveVideoAccess
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.BuildConfig
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.SignInKind
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.core.groupByRoom
import net.badacid.homeassist.echoshow.platform.OkHttpSend
import net.badacid.homeassist.echoshow.platform.loadPicture
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.connecting.ConnectingView
import net.badacid.homeassist.echoshow.screens.home.HomeCallbacks
import net.badacid.homeassist.echoshow.screens.home.HomeScreenState
import net.badacid.homeassist.echoshow.screens.home.HomeView
import net.badacid.homeassist.echoshow.screens.home.PictureLoader
import net.badacid.homeassist.echoshow.screens.panel.PanelView
import net.badacid.homeassist.echoshow.screens.settings.SettingsCallbacks
import net.badacid.homeassist.echoshow.screens.settings.SettingsScreenState
import net.badacid.homeassist.echoshow.screens.settings.SettingsView
import net.badacid.homeassist.echoshow.screens.reorganise.ReorganiseView
import net.badacid.homeassist.echoshow.screens.lock.LockView
import net.badacid.homeassist.echoshow.screens.signin.SignInFlow
import net.badacid.homeassist.echoshow.screens.signin.SignInView

/** The app's only activity: draws the model's state with the screens and passes touches back to it. */
class MainActivity : Activity() {
    private val app get() = application as HomeAssistApp
    private val model get() = app.model
    private lateinit var scope: CoroutineScope
    private lateinit var root: FrameLayout

    // What is showing now, so a new state only rebuilds what changed.
    private var phaseView: View? = null
    private var shownPhase: Phase? = null
    private var shownFailure: Any? = null
    private var signInFlow: SignInFlow? = null
    private var signInJob: Job? = null
    private var homeView: HomeView? = null
    private var overlayView: View? = null
    private var shownOverlay: Overlay? = null
    private var settingsView: SettingsView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        root = FrameLayout(this).apply { setBackgroundColor(Theme.BACKGROUND) }
        setContentView(root)
        model.start()
        scope.launch { model.state.collect { render(it) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: AppState) {
        renderPhase(state)
        renderOverlay(state)
        renderLock(state)
    }

    private var lockView: LockView? = null

    /** The PIN lock, over everything, while the app is locked with a PIN and a sign-in. */
    private fun renderLock(state: AppState) {
        val pin = state.saved.pin
        val locked = state.isLocked && pin != null && state.saved.signIn != null
        if (locked && lockView == null) {
            lockView = LockView(this, pin!!, System::currentTimeMillis, { model.unlock() }, { model.signOut() }).also {
                root.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        } else if (!locked && lockView != null) {
            root.removeView(lockView)
            lockView = null
        }
    }

    private fun renderPhase(state: AppState) {
        // The connecting screen shows the failure; the sign-in screen starts from the notice and refused token.
        val failureKey: Any? = when (state.phase) {
            Phase.CONNECTING -> state.connectFailure
            Phase.SIGNED_OUT -> state.notice to state.refusedToken
            else -> null
        }
        // Those two are made afresh when that changes; the others once per phase.
        if (state.phase != shownPhase || failureKey != shownFailure) {
            shownPhase = state.phase
            shownFailure = failureKey
            replacePhaseView(makePhaseView(state))
        }
        if (state.phase == Phase.HOME) homeView?.show(homeScreenState(state))
    }

    private fun replacePhaseView(view: View) {
        if (shownPhase != Phase.SIGNED_OUT) {
            signInJob?.cancel()
            signInFlow = null
        }
        if (shownPhase != Phase.HOME) homeView = null
        phaseView?.let { root.removeView(it) }
        phaseView = view
        root.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun makePhaseView(state: AppState): View = when (state.phase) {
        Phase.STARTING -> View(this)
        Phase.CONNECTING -> ConnectingView(this, state.saved.signIn?.address.orEmpty(), state.connectFailure) { model.signInAgain() }
        Phase.SIGNED_OUT -> {
            val flow = SignInFlow(
                OkHttpSend(app.client),
                scope,
                System::currentTimeMillis,
                state.saved.signIn?.address ?: state.saved.layoutAddress,
                state.notice,
                state.refusedToken,
            ) { model.signedIn(it) }
            signInFlow = flow
            val view = SignInView(this, flow)
            flow.start()
            signInJob?.cancel()
            signInJob = scope.launch { flow.state.collect { view.show(it) } }
            view
        }
        Phase.HOME -> HomeView(this, homeCallbacks, pictures, scope).also { homeView = it }
    }

    private fun homeScreenState(state: AppState) = HomeScreenState(
        layout = state.saved.layout,
        home = state.shownHome,
        devices = state.devices,
        homeAddress = state.saved.signIn?.address.orEmpty(),
        isReconnecting = state.status == ConnectionStatus.RECONNECTING,
        page = state.page,
        toast = state.toast,
    )

    private fun renderOverlay(state: AppState) {
        val overlay = if (state.phase == Phase.HOME) state.overlay else Overlay.None
        if (overlay != shownOverlay) {
            shownOverlay = overlay
            overlayView?.let { root.removeView(it) }
            overlayView = null
            settingsView = null
            val view: View? = when (overlay) {
                Overlay.None -> null
                Overlay.Settings -> SettingsView(this, settingsCallbacks).also { settingsView = it }
                // Made once with the layout as it is; the person's moves stay in it until Done.
                Overlay.Reorganise -> ReorganiseView(this, state.saved.layout, state.devices, state.shownHome) { model.saveLayout(it) }
                is Overlay.Panel -> findDevice(state.devices, overlay.deviceId)?.let { device ->
                    if (isFullScreen(device)) {
                        cameraView(state).apply { show(device, state.shownHome) }
                    } else {
                        PanelView(this, { model.closeOverlay() }, { model.run(it) }).apply { show(device, state.shownHome) }
                    }
                }
            }
            view?.let {
                overlayView = it
                root.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }
        when (overlay) {
            Overlay.Settings -> settingsView?.show(settingsScreenState(state))
            is Overlay.Panel -> findDevice(state.devices, overlay.deviceId)?.let { device ->
                (overlayView as? PanelView)?.show(device, state.shownHome)
                (overlayView as? CameraView)?.show(device, state.shownHome)
            }
            Overlay.None, Overlay.Reorganise -> Unit
        }
    }

    private fun isFullScreen(device: Device) =
        device.mainPart?.let { deviceLogicFor(it.typeId) }?.panelLayout == PanelLayout.FULL_SCREEN

    private fun cameraView(state: AppState) = CameraView(
        this,
        scope,
        app.liveForTests ?: liveVideo,
        pictures,
        state.saved.signIn?.address.orEmpty(),
        app.pictureUpdates,
        System::currentTimeMillis,
        onClose = { model.closeOverlay() },
        onLiveRunning = { running ->
            // The screen stays on only while live video plays (or is starting).
            if (running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        },
    )

    /** Live video through the open connection; a camera cannot stream while there is none. */
    private val liveVideo = object : LiveVideoAccess {
        override suspend fun canStream(entityId: String) = model.currentConnection()?.cameraCanStream(entityId) == true

        override fun start(entityId: String): LiveSession {
            val connection = model.currentConnection()
            val session = connection?.let { LiveVideoSession(this@MainActivity, it, entityId, scope) }
            return object : LiveSession {
                override val state = session?.state ?: kotlinx.coroutines.flow.MutableStateFlow(net.badacid.homeassist.echoshow.core.LiveState.FAILED)
                override val videoView: View = session?.videoView ?: View(this@MainActivity)
                override fun setMuted(muted: Boolean) { session?.setMuted(muted) }
                override fun stop() { session?.stop() }
            }.also { session?.start() }
        }
    }

    /** Leaving the screen (Home, another app, the screen sleeping) stops live video, as on the TV. */
    override fun onStop() {
        (overlayView as? CameraView)?.stopLive()
        // The PIN lock locks whenever the app leaves the screen, including when the screen sleeps.
        model.lock()
        super.onStop()
    }

    private fun settingsScreenState(state: AppState) = SettingsScreenState(
        rooms = groupByRoom(state.devices),
        layout = state.saved.layout,
        address = state.saved.signIn?.address.orEmpty(),
        userName = state.userName,
        isToken = state.saved.signIn?.kind == SignInKind.LONG_LIVED_TOKEN,
        version = BuildConfig.VERSION_NAME,
        pin = state.saved.pin,
    )

    private val homeCallbacks = object : HomeCallbacks {
        override fun onCardTap(deviceId: String) = model.pressCard(deviceId)
        override fun onCardHold(deviceId: String) = model.holdCard(deviceId)
        override fun onSettings() = model.openSettings()
        override fun onPage(page: Int) = model.showPage(page)
    }

    private val settingsCallbacks = object : SettingsCallbacks {
        override fun onClose() = model.closeOverlay()
        override fun onReorganise() = model.openReorganise()
        override fun onSetPin(pin: String?) = model.setPin(pin)
        override fun onToggleDevice(device: Device) = model.toggleDevice(device)
        override fun onSignOut() = model.signOut()
        override fun onOpenLink(address: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
            } catch (_: ActivityNotFoundException) {
                // No browser on this Echo: the address is on screen to type elsewhere.
            }
        }
    }

    private val pictures = PictureLoader { url, width, height ->
        loadPicture(app.client, url, width, height, model.state.value.saved.signIn?.accessToken)
    }

    /** System Back closes what is open; on the sign-in screen it steps back a form. This app is the Home app, so it never leaves. */
    @Deprecated("Android 11 has no OnBackInvokedCallback")
    override fun onBackPressed() {
        // Back cannot get past the lock.
        if (lockView != null) return
        when (model.state.value.phase) {
            Phase.SIGNED_OUT -> signInFlow?.back()
            // Back in the reorganise mode keeps the result, as Done does.
            Phase.HOME -> (overlayView as? ReorganiseView)?.done() ?: model.back()
            else -> Unit
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Full screen: the status and navigation bars come back for a moment with a swipe from the edge. */
    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
