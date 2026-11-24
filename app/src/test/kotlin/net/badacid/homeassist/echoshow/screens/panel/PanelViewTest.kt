package net.badacid.homeassist.echoshow.screens.panel

import android.view.MotionEvent
import android.view.View
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.described
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class PanelViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)
    private var closed = 0

    private fun panel(id: String) = PanelView(context) { closed++ }.apply {
        show(findDevice(devices, id)!!, home)
        measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1280, 800)
    }

    @Test
    fun `the panel lists each part with its summary`() {
        val words = visibleTexts(panel("device:dev_tado"))
        assertTrue(words.toString(), words.containsAll(listOf("Living room heating", "Living room", "Humidity", "48%", "More controls for this device are coming in the next version.")))
    }

    @Test
    fun `the panel's close button and the backdrop close it`() {
        val view = panel("device:dev_ring")
        view.described("Close").performClick()
        assertEquals(1, closed)
        // A tap at the very corner is on the backdrop, outside the box.
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 5f, 5f, 0))
        assertEquals(2, closed)
        // A tap inside the box does not close it.
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 640f, 400f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 640f, 400f, 0))
        assertEquals(2, closed)
    }

    @Test
    fun screenshot() {
        snapshot(panel("device:dev_ring"), "panel-doorbell")
        snapshot(panel("device:dev_tado"), "panel-heating")
    }
}
