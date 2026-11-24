package net.badacid.homeassist.echoshow.app

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.flow.MutableStateFlow
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.EMPTY_SAVED_STATE
import net.badacid.homeassist.echoshow.core.HomeConnection
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.ServiceCall
import net.badacid.homeassist.echoshow.core.SignInKind
import net.badacid.homeassist.echoshow.core.serialiseSavedState
import net.badacid.homeassist.echoshow.screens.home.HomeView
import net.badacid.homeassist.echoshow.screens.settings.SettingsView
import net.badacid.homeassist.echoshow.screens.signin.SignInView
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class MainActivityTest {
    private val app get() = RuntimeEnvironment.getApplication() as HomeAssistApp
    private val signIn = SavedSignIn("http://192.168.1.100:8123", SignInKind.LONG_LIVED_TOKEN, "token", "", 0)
    private var opens = 0

    private class Memory(var text: String?) : SavedTextStorage {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    private class FakeConnection : HomeConnection {
        override val userName = "Simon"
        override val home = MutableStateFlow(sampleHome())
        override val status = MutableStateFlow(ConnectionStatus.CONNECTED)
        override suspend fun call(call: ServiceCall) = Unit
        override suspend fun signOut() = Unit
        override fun close() = Unit
    }

    private fun saved(signedIn: Boolean, pin: String? = null) = Memory(
        serialiseSavedState(
            EMPTY_SAVED_STATE.copy(
                signIn = if (signedIn) signIn else null,
                layout = Layout(listOf(
                    CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL),
                    CardPlace("device:dev_tado", 1, 0, 0, CardSize.WIDE),
                )),
                layoutAddress = signIn.address,
                pin = pin,
            ),
        ),
    )

    private fun useFakes(signedIn: Boolean = true, pin: String? = null) {
        app.storageForTests = saved(signedIn, pin)
        app.openerForTests = { _, _ -> opens++; FakeConnection() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch(): ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup().also { idle() }

    private fun allViews(view: View): Sequence<View> =
        sequenceOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().flatMap { allViews(group.getChildAt(it)) } } ?: emptySequence())

    private inline fun <reified T : View> ActivityController<MainActivity>.find(): T? =
        allViews(get().window.decorView).filterIsInstance<T>().firstOrNull()

    @Before
    fun fresh() = app.resetForTests()

    @Test
    fun `with nothing saved it shows the sign-in screen`() {
        useFakes(signedIn = false)
        val activity = launch()
        assertNotNull(activity.find<SignInView>())
        assertEquals(0, opens)
    }

    @Test
    fun `recreating the activity keeps the phase and page and does not reconnect`() {
        useFakes()
        val activity = launch()
        assertEquals(Phase.HOME, app.model.state.value.phase)
        assertNotNull(activity.find<HomeView>())
        app.model.showPage(1)
        idle()
        activity.recreate()
        idle()
        assertEquals(Phase.HOME, app.model.state.value.phase)
        assertEquals(1, app.model.state.value.page)
        assertNotNull(activity.find<HomeView>())
        assertEquals(1, opens)
    }

    @Test
    fun `Back closes settings, and on the home screen does nothing`() {
        useFakes()
        val activity = launch()
        app.model.openSettings()
        idle()
        assertNotNull(activity.find<SettingsView>())
        activity.get().onBackPressed()
        idle()
        assertNull(activity.find<SettingsView>())
        assertEquals(Overlay.None, app.model.state.value.overlay)
        activity.get().onBackPressed()
        idle()
        assertFalse(activity.get().isFinishing)
        assertNotNull(activity.find<HomeView>())
    }

    @Test
    fun `the activity stopping stops live video and lets the screen sleep again`() {
        useFakes()
        var stopped = false
        val sessionState = MutableStateFlow(net.badacid.homeassist.echoshow.core.LiveState.CONNECTING)
        app.liveForTests = object : net.badacid.homeassist.echoshow.screens.camera.LiveVideoAccess {
            override suspend fun canStream(entityId: String) = true
            override fun start(entityId: String) = object : net.badacid.homeassist.echoshow.screens.camera.LiveSession {
                override val state = sessionState
                override val videoView = View(app)
                override fun setMuted(muted: Boolean) = Unit
                override fun stop() { stopped = true }
            }
        }
        val activity = launch()
        app.model.holdCard("device:dev_ring")
        idle()
        val camera = activity.find<net.badacid.homeassist.echoshow.screens.camera.CameraView>()!!
        val keepOn = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val found = ArrayList<View>()
        camera.findViewsWithText(found, "Watch live", View.FIND_VIEWS_WITH_TEXT)
        found.first().performClick()
        idle()
        assertEquals(keepOn, activity.get().window.attributes.flags and keepOn)
        activity.pause().stop()
        idle()
        assertEquals(true, stopped)
    }

    @Test
    fun `with a PIN the app opens locked, and leaving the screen locks it again`() {
        useFakes(pin = "1234")
        val activity = launch()
        assertNotNull(activity.find<net.badacid.homeassist.echoshow.screens.lock.LockView>())
        app.model.unlock()
        idle()
        assertNull(activity.find<net.badacid.homeassist.echoshow.screens.lock.LockView>())
        activity.pause().stop()
        idle()
        activity.start().resume()
        idle()
        assertNotNull(activity.find<net.badacid.homeassist.echoshow.screens.lock.LockView>())
    }

    @Test
    fun `after the time the screen blanks and locks, and a tap only wakes it`() {
        useFakes(pin = "1234")
        val activity = launch()
        app.model.unlock()
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
        val blank = activity.get().window.decorView.findViewWithTag<View>("blank-screen")
        assertEquals(View.VISIBLE, blank.visibility)
        assertEquals(true, app.model.state.value.isLocked)
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF, activity.get().window.attributes.screenBrightness)
        // A tap wakes the screen; it does not reach the lock screen's keys underneath.
        val time = android.os.SystemClock.uptimeMillis()
        activity.get().dispatchTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, 640f, 400f, 0))
        activity.get().dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 50, android.view.MotionEvent.ACTION_UP, 640f, 400f, 0))
        idle()
        assertEquals(View.GONE, blank.visibility)
        assertEquals(android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, activity.get().window.attributes.screenBrightness)
        assertEquals(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, activity.get().window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test
    fun `blanking hides the status and navigation bars, even if they had been brought back`() {
        useFakes()
        val activity = launch()
        val decor = activity.get().window.decorView
        // As if the person had swiped the bars in, or something had brought them back.
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = 0
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
        @Suppress("DEPRECATION")
        val flags = decor.systemUiVisibility
        @Suppress("DEPRECATION")
        val wanted = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        assertEquals(wanted, flags and wanted)
    }

    @Test
    fun `with a PIN the window is marked secure and the app locks as soon as it is paused`() {
        val secure = android.view.WindowManager.LayoutParams.FLAG_SECURE
        useFakes(pin = "1234")
        val locked = launch()
        assertEquals(secure, locked.get().window.attributes.flags and secure)
        app.model.unlock()
        idle()
        locked.pause()
        idle()
        assertEquals(true, app.model.state.value.isLocked)
    }

    @Test
    fun `without a PIN the window is not marked secure`() {
        useFakes()
        val activity = launch()
        assertEquals(0, activity.get().window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}
