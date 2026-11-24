package net.badacid.homeassist.echoshow.screens.reorganise

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.screens.withText
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class ReorganiseViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)
    private var saved: Layout? = null

    private val start = Layout(listOf(
        CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL),
        CardPlace("device:dev_tado", 0, 1, 0, CardSize.WIDE),
    ))

    private fun view(layout: Layout = start) = ReorganiseView(context, layout, devices, home) { saved = it }.apply {
        measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1280, 800)
    }

    private var time = SystemClock.uptimeMillis()
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        time += 20
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, action, x, y, 0))
    }

    /** Drags from the centre of one cell to the centre of another, in small steps. */
    private fun drag(view: ReorganiseView, from: Pair<Int, Int>, to: Pair<Int, Int>, lift: Boolean = true) {
        val (x0, y0) = view.centreOf(from.first, from.second)
        val (x1, y1) = view.centreOf(to.first, to.second)
        touch(view, MotionEvent.ACTION_DOWN, x0, y0)
        for (step in 1..10) touch(view, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * step / 10, y0 + (y1 - y0) * step / 10)
        if (lift) touch(view, MotionEvent.ACTION_UP, x1, y1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun lampIn(layout: Layout?) = layout!!.cards.first { it.deviceId == "device:dev_lifx_lamp" }

    @Test
    fun `dragging a card to a free cell moves it`() {
        val view = view()
        drag(view, 0 to 0, 3 to 2)
        view.withText("Done").performClick()
        assertEquals(CardPlace("device:dev_lifx_lamp", 0, 3, 2, CardSize.SMALL), lampIn(saved))
    }

    @Test
    fun `a drop that does not fit leaves the layout unchanged`() {
        val view = view()
        drag(view, 0 to 0, 2 to 0)
        assertTrue(visibleTexts(view).contains("It does not fit there"))
        view.withText("Done").performClick()
        assertEquals(start, saved)
    }

    @Test
    fun `holding at the right edge on the last page carries the card to a new page`() {
        val view = view()
        val (x0, y0) = view.centreOf(0, 0)
        touch(view, MotionEvent.ACTION_DOWN, x0, y0)
        for (step in 1..10) touch(view, MotionEvent.ACTION_MOVE, x0 + (1275 - x0) * step / 10, y0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        val (x1, y1) = view.centreOf(0, 0)
        for (step in 1..10) touch(view, MotionEvent.ACTION_MOVE, 1275 + (x1 - 1275) * step / 10, y0 + (y1 - y0) * step / 10)
        touch(view, MotionEvent.ACTION_UP, x1, y1)
        shadowOf(Looper.getMainLooper()).idle()
        view.withText("Done").performClick()
        assertEquals(CardPlace("device:dev_lifx_lamp", 1, 0, 0, CardSize.SMALL), lampIn(saved))
    }

    @Test
    fun `Done saves and empty pages are removed`() {
        val view = view(Layout(listOf(CardPlace("device:dev_tado", 0, 0, 0, CardSize.WIDE), CardPlace("device:dev_lifx_lamp", 2, 0, 0, CardSize.SMALL))))
        view.withText("Done").performClick()
        assertEquals(1, lampIn(saved).page)
    }

    @Test
    fun `a tap without a drag changes nothing`() {
        val view = view()
        val (x, y) = view.centreOf(0, 0)
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
        assertNull(saved)
        view.withText("Done").performClick()
        assertEquals(start, saved)
    }

    @Test
    fun screenshot() {
        val view = view()
        val (x0, y0) = view.centreOf(0, 0)
        val (x1, y1) = view.centreOf(3, 2)
        touch(view, MotionEvent.ACTION_DOWN, x0, y0)
        for (step in 1..10) touch(view, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * step / 10, y0 + (y1 - y0) * step / 10)
        snapshot(view, "reorganise")
    }
}
