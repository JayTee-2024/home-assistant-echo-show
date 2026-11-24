package net.badacid.homeassist.echoshow.screens.home

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.testsupport.sampleHome
import org.junit.Assert.assertEquals
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
class GridViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)

    private fun card(id: String, size: CardSize, onTap: () -> Unit = {}, onHold: () -> Unit = {}, loader: PictureLoader? = null): CardView {
        val view = CardView(context, size, onTap, onHold, loader?.let { { CameraPictureView(context, CoroutineScope(SupervisorJob() + Dispatchers.Main), it, "http://ha.local:8123") } })
        view.show(cardContent(id, findDevice(devices, id), home, size))
        return view
    }

    private fun laidOut(view: View, width: Int = 1280, height: Int = 800) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    @Test
    fun `places a small, a wide and a large card in their cells`() {
        val grid = GridView(context)
        val small = card("device:dev_lifx_lamp", CardSize.SMALL)
        val wide = card("device:dev_tado", CardSize.WIDE)
        val large = card("device:dev_ring", CardSize.LARGE)
        grid.setCards(listOf(
            CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL) to small,
            CardPlace("device:dev_tado", 0, 1, 0, CardSize.WIDE) to wide,
            CardPlace("device:dev_ring", 0, 0, 1, CardSize.LARGE) to large,
        ))
        // 1280 wide with 3 gaps of 14 dp (= 18.67 px at tvdpi, 18 px rounded down): cells of (1280 - 3g) / 4.
        laidOut(grid)
        val gap = Theme.dp(context, 14f)
        val cellW = (1280 - 3 * gap) / 4
        val cellH = (800 - 2 * gap) / 3
        assertEquals(listOf(0, 0, cellW, cellH), listOf(small.left, small.top, small.width, small.height))
        assertEquals(listOf(cellW + gap, 0, 2 * cellW + gap, cellH), listOf(wide.left, wide.top, wide.width, wide.height))
        assertEquals(listOf(0, cellH + gap, 2 * cellW + gap, 2 * cellH + gap), listOf(large.left, large.top, large.width, large.height))
    }

    private fun touch(view: View, action: Int, x: Float = 20f, y: Float = 20f, at: Long = SystemClock.uptimeMillis()) {
        view.dispatchTouchEvent(MotionEvent.obtain(at, at, action, x, y, 0))
    }

    @Test
    fun `a tap calls onTap and a hold of 500 ms calls onHold, not both`() {
        var taps = 0
        var holds = 0
        val view = card("device:dev_lifx_lamp", CardSize.SMALL, { taps++ }, { holds++ })
        laidOut(view, 300, 200)

        touch(view, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(1 to 0, taps to holds)

        touch(view, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS))
        assertEquals(1 to 1, taps to holds)
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(1 to 1, taps to holds)
    }

    @Test
    fun `moving the finger cancels the hold`() {
        var taps = 0
        var holds = 0
        val view = card("device:dev_lifx_lamp", CardSize.SMALL, { taps++ }, { holds++ })
        laidOut(view, 300, 200)
        touch(view, MotionEvent.ACTION_DOWN)
        touch(view, MotionEvent.ACTION_MOVE, x = 120f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HOLD_MS * 2))
        touch(view, MotionEvent.ACTION_UP, x = 120f)
        assertEquals(0 to 0, taps to holds)
    }

    @Test
    fun `the card dims while held and comes back when let go`() {
        val view = card("device:dev_lifx_lamp", CardSize.SMALL)
        laidOut(view, 300, 200)
        touch(view, MotionEvent.ACTION_DOWN)
        assertEquals(0.7f, view.alpha, 0.01f)
        touch(view, MotionEvent.ACTION_UP)
        assertEquals(1f, view.alpha, 0.01f)
    }

    @Test
    fun `home grid screenshot`() {
        val picture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 90, 110)) }
        val loader = PictureLoader { _, _, _ -> picture }
        val grid = GridView(context).apply { setBackgroundColor(Theme.BACKGROUND); val p = Theme.dp(context, 24f); setPadding(p, p, p, p) }
        fun place(id: String, column: Int, row: Int, size: CardSize) = CardPlace(id, 0, column, row, size) to card(id, size, loader = loader)
        grid.setCards(listOf(
            place("device:dev_lifx_lamp", 0, 0, CardSize.SMALL),
            place("device:dev_lifx_ceiling", 1, 0, CardSize.SMALL),
            place("device:dev_tado", 2, 0, CardSize.WIDE),
            place("device:dev_ring", 0, 1, CardSize.LARGE),
            place("device:dev_plug_kettle", 2, 1, CardSize.SMALL),
            place("device:dev_sonos", 3, 1, CardSize.SMALL),
            place("device:dev_blind", 2, 2, CardSize.SMALL),
            place("device:dev_fan", 3, 2, CardSize.SMALL),
        ))
        laidOut(grid)
        shadowOf(Looper.getMainLooper()).idle()
        snapshot(grid, "home-grid")
    }
}
