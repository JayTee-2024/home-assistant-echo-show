package net.badacid.homeassist.echoshow.screens.camera

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import net.badacid.homeassist.echoshow.core.LiveState
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.devices.camera.PictureUpdates
import net.badacid.homeassist.echoshow.screens.described
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.screens.withText
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class CameraViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val ring = findDevice(buildDevices(home, DEVICE_LOGIC), "device:dev_ring")!!
    private var closed = 0
    private val running = mutableListOf<Boolean>()

    private inner class FakeSession : LiveSession {
        override val state = MutableStateFlow(LiveState.CONNECTING)
        override val videoView: View = View(context)
        var soundOff = true
        var stopped = false
        override fun setMuted(muted: Boolean) { soundOff = muted }
        override fun stop() { stopped = true; state.value = LiveState.IDLE }
    }

    private val sessions = mutableListOf<FakeSession>()
    private fun access(canStream: Boolean) = object : LiveVideoAccess {
        override suspend fun canStream(entityId: String) = canStream
        override fun start(entityId: String): LiveSession = FakeSession().also { sessions += it }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun camera(canStream: Boolean = true) = CameraView(
        context,
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        access(canStream),
        { _, _, _ -> Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888) },
        "http://192.168.1.100:8123",
        PictureUpdates(),
        clock = { 1_000L },
        onClose = { closed++ },
        onLiveRunning = { running += it },
    ).apply {
        show(ring, home)
        measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1280, 800)
        idle()
    }

    @Test
    fun `Watch live starts the session and the controls follow its state`() {
        val view = camera()
        assertTrue(visibleTexts(view).containsAll(listOf("Front door · Hallway", "Last recording")))
        view.withText("Watch live").performClick()
        idle()
        assertEquals(1, sessions.size)
        assertTrue(visibleTexts(view).containsAll(listOf("Stop live", "Sound on", "Connecting…")))
        sessions[0].state.value = LiveState.PLAYING
        idle()
        assertTrue(visibleTexts(view).contains("Live"))
        view.withText("Sound on").performClick()
        assertFalse(sessions[0].soundOff)
        assertEquals(true, running.last())
        sessions[0].state.value = LiveState.ENDED
        idle()
        assertTrue(visibleTexts(view).containsAll(listOf("Watch again", "Live view ended")))
        assertEquals(false, running.last())
    }

    @Test
    fun `closing the view stops the session`() {
        val view = camera()
        view.withText("Watch live").performClick()
        idle()
        view.described("Close").performClick()
        assertTrue(sessions[0].stopped)
        assertEquals(1, closed)
    }

    @Test
    fun `leaving the app stops live video and notes it for the picture label`() {
        val view = camera()
        view.withText("Watch live").performClick()
        idle()
        sessions[0].state.value = LiveState.PLAYING
        idle()
        view.stopLive()
        idle()
        assertTrue(sessions[0].stopped)
        assertTrue(visibleTexts(view).contains("Watch live"))
        assertEquals(false, running.last())
        assertTrue(visibleTexts(view).contains("Updating picture…"))
    }

    @Test
    fun `a camera with no live support shows no Watch live button`() {
        val view = camera(canStream = false)
        assertFalse(visibleTexts(view).contains("Watch live"))
    }

    @Test
    fun screenshot() {
        snapshot(camera(), "camera-view")
    }
}
