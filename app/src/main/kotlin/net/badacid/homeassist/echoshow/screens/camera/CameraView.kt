package net.badacid.homeassist.echoshow.screens.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.LiveState
import net.badacid.homeassist.echoshow.devices.camera.LiveStopped
import net.badacid.homeassist.echoshow.devices.camera.PictureUpdates
import net.badacid.homeassist.echoshow.devices.camera.cameraControls
import net.badacid.homeassist.echoshow.devices.camera.labelFor
import net.badacid.homeassist.echoshow.devices.camera.readCamera
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import net.badacid.homeassist.echoshow.screens.home.CameraPictureView
import net.badacid.homeassist.echoshow.screens.home.PictureLoader
import net.badacid.homeassist.echoshow.screens.home.otherPartLines

/** One live video session. The app's is WebRTC (platform/LiveVideoSession.kt). */
interface LiveSession {
    val state: StateFlow<LiveState>

    /** The live picture. Placed under the still picture, which is hidden once the video plays. */
    val videoView: View
    fun setMuted(muted: Boolean)

    /** Ends the session. Safe to call more than once. */
    fun stop()
}

interface LiveVideoAccess {
    /** True when this camera can play live video here. */
    suspend fun canStream(entityId: String): Boolean

    /** Starts a session, muted. */
    fun start(entityId: String): LiveSession
}

/** How often the full-screen still picture is fetched again (the LG app's PANEL_REFRESH_MS). */
private const val PANEL_REFRESH_MS = 2_000L

/** How many of the device's other parts the bar lists. */
private const val FULL_SCREEN_PART_LINES = 4

/**
 * A camera's full-screen view (the LG app's camera Panel.tsx with its FullScreenPanel): the still picture
 * with its label, Watch live and Sound, the status line, and the name and other parts across the top.
 * Live video stops when the view closes or the app leaves the screen (stopLive).
 */
@SuppressLint("ViewConstructor")
class CameraView(
    context: Context,
    private val scope: CoroutineScope,
    private val live: LiveVideoAccess,
    pictures: PictureLoader,
    homeAddress: String,
    private val updates: PictureUpdates,
    private val clock: () -> Long,
    private val onClose: () -> Unit,
    /** True while live video is connecting or playing, so the app can keep the screen on. */
    private val onLiveRunning: (Boolean) -> Unit,
) : FrameLayout(context) {
    private val dp = { v: Float -> Theme.dp(context, v) }
    private var entity: EntityState? = null
    private val still = CameraPictureView(context, scope, pictures, homeAddress, PANEL_REFRESH_MS, { camera ->
        labelFor(readCamera(camera).recordingId, updates.liveStoppedAt(camera.entityId), clock())?.words
    }, fitWhole = true).apply { background = null }
    private val videoBox = FrameLayout(context)
    private val title = Theme.text(context, "", Theme.HEADING_SP, bold = true)
    private val parts = Theme.text(context, "", Theme.BODY_SP, Theme.TEXT_DIM)
    private val actions = LinearLayout(context).apply { gravity = Gravity.CENTER; visibility = GONE }
    private val status = Theme.text(context, "", Theme.BODY_SP).apply {
        background = Theme.roundedPanel(context, Theme.BACKDROP, 10f)
        setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
        visibility = GONE
    }
    private var state = LiveState.IDLE
    private var isMuted = true
    private var session: LiveSession? = null
    private var watching: Job? = null
    private var canGoLive = false
    private var asked = false

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        addView(videoBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(still, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val bar = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(200, 0, 0, 0), Color.TRANSPARENT))
            setPadding(dp(24f), dp(14f), dp(12f), dp(28f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(parts)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(Widgets.iconButton(context, Icons.CLOSE, "Close") { close() }, LayoutParams(dp(56f), dp(56f), Gravity.END or Gravity.TOP))
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { leftMargin = dp(24f); bottomMargin = dp(28f) })
        addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(24f) })
    }

    fun show(device: Device, home: HomeSnapshot) {
        contentDescription = device.name
        title.text = device.name + (device.roomName?.let { " · $it" } ?: "")
        val others = otherPartLines(device, home, FULL_SCREEN_PART_LINES)
        parts.text = others.joinToString(" · ")
        parts.visibility = if (others.isEmpty()) GONE else VISIBLE
        val camera = device.mainPart?.let { home.states[it.entityId] } ?: return
        entity = camera
        still.show(camera)
        if (!asked) {
            asked = true
            // Ask once whether this camera can go live. Until the answer comes, there is no button.
            scope.launch {
                canGoLive = live.canStream(camera.entityId)
                drawControls()
            }
        }
        drawControls()
    }

    private fun drawControls() {
        val controls = cameraControls(state, isMuted)
        actions.visibility = if (canGoLive) VISIBLE else GONE
        actions.removeAllViews()
        actions.addView(Widgets.button(context, controls.liveLabel) { pressLive() })
        controls.soundLabel?.let { words ->
            actions.addView(Widgets.button(context, words, Widgets.ButtonStyle.OUTLINE) { toggleSound() }, Widgets.wrap().apply { leftMargin = dp(12f) })
        }
        status.text = controls.status.orEmpty()
        status.visibility = if (controls.status == null) GONE else VISIBLE
        // The still picture covers the video until it plays.
        still.visibility = if (state == LiveState.PLAYING) INVISIBLE else VISIBLE
    }

    private fun pressLive() {
        if (state == LiveState.CONNECTING || state == LiveState.PLAYING) stopLive() else startLive()
    }

    private fun startLive() {
        val camera = entity ?: return
        stopLive() // Never two sessions at once.
        val started = live.start(camera.entityId)
        session = started
        isMuted = true
        videoBox.addView(started.videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        state = LiveState.CONNECTING
        onLiveRunning(true)
        drawControls()
        watching = scope.launch {
            started.state.collect { next ->
                if (session !== started) return@collect
                if (next == LiveState.IDLE) return@collect
                val wasRunning = state == LiveState.CONNECTING || state == LiveState.PLAYING
                state = next
                // A session that has broken is finished: let go of it.
                if (next == LiveState.ENDED || next == LiveState.FAILED) {
                    release(noteStop = next == LiveState.ENDED)
                    if (wasRunning) onLiveRunning(false)
                }
                drawControls()
            }
        }
    }

    /** Stops live video, as when the view closes or the app leaves the screen. The picture shows again. */
    fun stopLive() {
        val wasRunning = state == LiveState.CONNECTING || state == LiveState.PLAYING
        release(noteStop = state == LiveState.PLAYING)
        if (state != LiveState.IDLE) {
            state = LiveState.IDLE
            drawControls()
        }
        if (wasRunning) onLiveRunning(false)
    }

    private fun release(noteStop: Boolean) {
        val ending = session ?: return
        session = null
        watching?.cancel()
        watching = null
        ending.stop()
        videoBox.removeAllViews()
        // A camera whose picture is its last recording (Ring) records the live view too: its picture says
        // "Updating picture…" until that recording arrives.
        val camera = entity
        val recordingId = camera?.let { readCamera(it).recordingId }
        if (noteStop && camera != null && recordingId != null) {
            updates.noteLiveStopped(camera.entityId, LiveStopped(clock(), recordingId))
            still.refreshLabel()
        }
    }

    private fun toggleSound() {
        isMuted = !isMuted
        session?.setMuted(isMuted)
        drawControls()
    }

    private fun close() {
        stopLive()
        onClose()
    }

    override fun onDetachedFromWindow() {
        stopLive()
        super.onDetachedFromWindow()
    }
}
