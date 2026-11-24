package net.badacid.homeassist.echoshow.screens.panel

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.described
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.screens.withText
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class PanelViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)
    private var closed = 0
    private val runs = mutableListOf<Action>()

    private fun View.laidOut() = apply {
        measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1280, 800)
    }

    private fun panel(id: String, at: HomeSnapshot = home) = PanelView(context, { closed++ }, { runs += it }).apply {
        show(findDevice(devices, id)!!, at)
        laidOut()
    }

    private fun withTarget(target: Double): HomeSnapshot {
        val old = home.states.getValue("climate.living_room")
        val attributes = buildJsonObject { old.attributes.forEach { (k, v) -> put(k, v) }; put("temperature", JsonPrimitive(target)) }
        return home.copy(states = home.states + ("climate.living_room" to EntityState(old.entityId, old.state, attributes.jsonObject)))
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun `the panel shows each part's rows, with a heading for parts not named like the device`() {
        val words = visibleTexts(panel("device:dev_tado"))
        assertTrue(words.toString(), words.containsAll(listOf("Living room heating", "Room", "20.5°", "Target", "21°", "Mode", "Heat", "Humidity", "Reading", "48%")))
        assertTrue("More controls for this device are coming in the next version." !in words)
    }

    @Test
    fun `a Press button runs its action`() {
        val view = panel("device:dev_plug_kettle")
        view.withText("Turn on").performClick()
        assertEquals(listOf("switch/toggle"), runs.map { it.call.domain + "/" + it.call.service })
    }

    @Test
    fun `two + taps then rest send one set_temperature for the last value`() {
        val view = panel("device:dev_tado")
        view.described("Increase Target").performClick()
        view.described("Increase Target").performClick()
        idle(1000)
        assertEquals(0, runs.size)
        assertTrue(visibleTexts(view).contains("21.2°"))
        idle(600)
        assertEquals(1, runs.size)
        assertEquals("climate/set_temperature", runs[0].call.domain + "/" + runs[0].call.service)
        assertTrue(runs[0].call.data.toString(), runs[0].call.data.toString().contains("21.2"))
    }

    @Test
    fun `a report during the wait does not change the number shown`() {
        val view = panel("device:dev_tado")
        view.described("Increase Target").performClick()
        view.show(findDevice(devices, "device:dev_tado")!!, withTarget(19.0))
        assertTrue(visibleTexts(view).contains("21.1°"))
        idle(1600)
        // Sent, and then Home Assistant's number is shown again.
        view.show(findDevice(devices, "device:dev_tado")!!, withTarget(21.1))
        assertTrue(visibleTexts(view).contains("21.1°"))
    }

    @Test
    fun `closing sends a target still waiting`() {
        val view = panel("device:dev_tado")
        view.described("Increase Target").performClick()
        view.described("Close").performClick()
        assertEquals(1, runs.size)
    }

    @Test
    fun `the panel's close button and the backdrop close it`() {
        val view = panel("device:dev_tado")
        view.described("Close").performClick()
        assertEquals(1, closed)
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 5f, 5f, 0))
        assertEquals(2, closed)
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 640f, 400f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 640f, 400f, 0))
        assertEquals(2, closed)
    }

    @Test
    fun screenshots() {
        snapshot(panel("device:dev_lifx_lamp"), "panel-light")
        snapshot(panel("device:dev_tado"), "panel-heating")
        snapshot(panel("device:dev_sonos"), "panel-media")
        snapshot(panel("device:dev_vacuum"), "panel-vacuum")
    }
}
