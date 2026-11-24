package net.badacid.homeassist.echoshow.screens.home

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.badacid.homeassist.echoshow.app.AppState
import net.badacid.homeassist.echoshow.app.Phase
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.ConnectionStatus
import net.badacid.homeassist.echoshow.core.EMPTY_SAVED_STATE
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.SignInKind
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
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
class HomeViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val calls = mutableListOf<String>()
    private val callbacks = object : HomeCallbacks {
        override fun onCardTap(deviceId: String) { calls += "tap $deviceId" }
        override fun onCardHold(deviceId: String) { calls += "hold $deviceId" }
        override fun onSettings() { calls += "settings" }
        override fun onPage(page: Int) { calls += "page $page" }
    }
    private val picture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 90, 110)) }

    private fun view() = HomeView(context, callbacks, { _, _, _ -> picture }, CoroutineScope(SupervisorJob() + Dispatchers.Main))

    private val firstPage = listOf(
        CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL),
        CardPlace("device:dev_lifx_ceiling", 0, 1, 0, CardSize.SMALL),
        CardPlace("device:dev_tado", 0, 2, 0, CardSize.WIDE),
        CardPlace("device:dev_ring", 0, 0, 1, CardSize.LARGE),
        CardPlace("device:dev_plug_kettle", 0, 2, 1, CardSize.SMALL),
        CardPlace("device:dev_sonos", 0, 3, 1, CardSize.SMALL),
        CardPlace("device:dev_blind", 0, 2, 2, CardSize.SMALL),
        CardPlace("device:dev_fan", 0, 3, 2, CardSize.SMALL),
    )
    private val twoPages = firstPage + CardPlace("device:dev_plug_tv", 1, 0, 0, CardSize.SMALL)

    private fun state(cards: List<CardPlace> = twoPages, page: Int = 0, status: ConnectionStatus = ConnectionStatus.CONNECTED, toast: String? = null) = AppState(
        phase = Phase.HOME,
        saved = EMPTY_SAVED_STATE.copy(
            signIn = SavedSignIn("http://192.168.1.100:8123", SignInKind.LONG_LIVED_TOKEN, "t", "", 0),
            layout = Layout(cards),
        ),
        shownHome = home,
        devices = buildDevices(home, DEVICE_LOGIC),
        status = status,
        page = page,
        toast = toast,
        userName = "Simon",
    )

    private fun laidOut(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1280, 800)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Drags across the screen from x0 to x1 at mid-height, over 400 ms. */
    private fun drag(view: View, x0: Float, x1: Float) {
        val start = SystemClock.uptimeMillis()
        val y = 450f
        view.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x0, y, 0))
        for (step in 1..8) {
            val x = x0 + (x1 - x0) * step / 8
            view.dispatchTouchEvent(MotionEvent.obtain(start, start + step * 50, MotionEvent.ACTION_MOVE, x, y, 0))
        }
        view.dispatchTouchEvent(MotionEvent.obtain(start, start + 400, MotionEvent.ACTION_UP, x1, y, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The words on screen: every visible TextView whose parents are all visible. (isShown needs a window.) */
    private fun texts(view: View): List<String> = if (view.visibility != View.VISIBLE) emptyList() else when (view) {
        is TextView -> listOf(view.text.toString())
        is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `a swipe left shows the next page and calls showPage`() {
        val home = view()
        home.show(state())
        laidOut(home)
        drag(home, 1000f, 300f)
        assertEquals(listOf("page 1"), calls)
    }

    @Test
    fun `a short drag goes back to the same page`() {
        val home = view()
        home.show(state())
        laidOut(home)
        drag(home, 1000f, 900f)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a swipe on the last page stays`() {
        val home = view()
        home.show(state(page = 1))
        laidOut(home)
        drag(home, 1000f, 300f)
        assertEquals(emptyList<String>(), calls)
        drag(home, 300f, 1000f)
        assertEquals(listOf("page 0"), calls)
    }

    @Test
    fun `the settings button opens settings`() {
        val home = view()
        home.show(state())
        laidOut(home)
        assertTrue(home.findViewWithContentDescription("Settings").performClick())
        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun `an empty home offers Add devices, which opens settings`() {
        val home = view()
        home.show(state(cards = emptyList()))
        laidOut(home)
        assertTrue(texts(home).containsAll(listOf("No devices yet", "Choose which of your Home Assistant devices to show here.", "Add devices")))
        home.findViewWithText("Add devices").performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun `the banner shows only while reconnecting`() {
        val banner = "Lost the connection to Home Assistant. Showing the last known states. Trying to reconnect…"
        val home = view()
        home.show(state())
        laidOut(home)
        assertTrue(banner !in texts(home))
        home.show(state(status = ConnectionStatus.RECONNECTING))
        laidOut(home)
        assertTrue(banner in texts(home))
    }

    @Test
    fun `the toast shows its words`() {
        val home = view()
        home.show(state(toast = "Not connected"))
        laidOut(home)
        assertTrue("Not connected" in texts(home))
        home.show(state())
        laidOut(home)
        assertTrue("Not connected" !in texts(home))
    }

    @Test
    fun `a tap on a card reaches onCardTap`() {
        val home = view()
        home.show(state())
        laidOut(home)
        home.findViewWithContentDescription("Lounge lamp").performClick()
        assertEquals(listOf("tap device:dev_lifx_lamp"), calls)
    }

    @Test
    fun screenshots() {
        fun shot(name: String, state: AppState) {
            val home = view()
            home.show(state)
            laidOut(home)
            snapshot(home, name)
        }
        shot("home", state())
        shot("home-empty", state(cards = emptyList()))
        shot("home-reconnecting", state(status = ConnectionStatus.RECONNECTING))
        shot("home-toast", state(toast = "Living room heating did not respond."))
    }

    private fun View.findViewWithContentDescription(words: String): View {
        val found = ArrayList<View>()
        findViewsWithText(found, words, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return found.first()
    }

    private fun View.findViewWithText(words: String): View {
        val found = ArrayList<View>()
        findViewsWithText(found, words, View.FIND_VIEWS_WITH_TEXT)
        return found.first { generateSequence(it) { v -> v.parent as? View }.all { v -> v.visibility == View.VISIBLE } }
    }
}
