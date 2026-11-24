package net.badacid.homeassist.echoshow.screens.lock

import android.view.View
import net.badacid.homeassist.echoshow.screens.snapshot
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.screens.withText
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
class LockViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var now = 1_000_000L
    private var unlocked = 0
    private var signedOut = 0

    private fun lock() = LockView(context, "1234", { now }, { unlocked++ }, { signedOut++ }).apply {
        measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 1280, 800)
    }

    private fun LockView.type(digits: String) = digits.forEach { withText(it.toString()).performClick() }

    @Test
    fun `the right PIN unlocks, and a wrong one says so`() {
        val view = lock()
        view.type("1111")
        assertTrue(visibleTexts(view).contains("Wrong PIN. Try again."))
        view.type("1234")
        assertEquals(1, unlocked)
    }

    @Test
    fun `five wrong PINs wait 30 seconds`() {
        val view = lock()
        repeat(5) { view.type("0000") }
        assertTrue(visibleTexts(view).toString(), visibleTexts(view).contains("Too many wrong tries. Wait 30 seconds."))
        view.type("1234")
        assertEquals(0, unlocked)
        now += 30_000
        view.type("1234")
        assertEquals(1, unlocked)
    }

    @Test
    fun `Delete takes the last digit off`() {
        val view = lock()
        view.type("12")
        view.withText("Delete").performClick()
        view.type("234")
        assertEquals(1, unlocked)
    }

    @Test
    fun `Forgot your PIN signs out, after asking`() {
        val view = lock()
        view.withText("Forgot PIN?").performClick()
        assertTrue(visibleTexts(view).contains("Forgot your PIN?"))
        view.withText("Sign out").performClick()
        assertEquals(1, signedOut)
    }

    @Test
    fun screenshot() {
        val view = lock()
        view.type("12")
        snapshot(view, "lock")
    }
}
