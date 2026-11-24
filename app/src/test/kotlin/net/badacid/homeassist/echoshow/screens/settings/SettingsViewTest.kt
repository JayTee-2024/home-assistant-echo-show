package net.badacid.homeassist.echoshow.screens.settings

import android.view.View
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.buildDevices
import net.badacid.homeassist.echoshow.core.groupByRoom
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import net.badacid.homeassist.echoshow.screens.described
import net.badacid.homeassist.echoshow.screens.rowWith
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class SettingsViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val home = sampleHome()
    private val devices = buildDevices(home, DEVICE_LOGIC)
    private val calls = mutableListOf<String>()
    private val callbacks = object : SettingsCallbacks {
        override fun onClose() { calls += "close" }
        override fun onToggleDevice(device: Device) { calls += "toggle ${device.id}" }
        override fun onSignOut() { calls += "sign out" }
        override fun onOpenLink(address: String) { calls += "open $address" }
    }

    private fun state(isToken: Boolean = false) = SettingsScreenState(
        rooms = groupByRoom(devices),
        layout = Layout(listOf(CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL))),
        address = "http://192.168.1.100:8123",
        userName = "Simon",
        isToken = isToken,
        version = "0.3.0",
    )

    private fun settings(state: SettingsScreenState = state()) = SettingsView(context, callbacks).apply {
        show(state)
        laidOut(this)
    }

    private fun laidOut(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1280, 800)
    }

    @Test
    fun `settings lists rooms with their devices and ticks the ones shown`() {
        val words = visibleTexts(settings())
        assertTrue(words.toString(), words.containsAll(listOf("Living room", "Kitchen", "Lounge lamp", "Kitchen ceiling", "On screen ✓", "Add")))
        assertTrue(words.indexOf("Living room") < words.indexOf("Lounge lamp"))
    }

    @Test
    fun `a tap on a device calls toggleDevice`() {
        val view = settings()
        view.rowWith("Kitchen ceiling").performClick()
        assertEquals(listOf("toggle device:dev_lifx_ceiling"), calls)
    }

    @Test
    fun `an unsupported device is greyed with Not supported yet`() {
        val view = settings()
        assertTrue(visibleTexts(view).contains("Not supported yet"))
        assertTrue(view.rowWith("Tado bridge").alpha < 1f)
    }

    @Test
    fun `Sign out asks first, then calls signOut, and a token sign-in gets the token note`() {
        val view = settings(state(isToken = true))
        view.withText("Connection").performClick()
        laidOut(view)
        assertTrue(visibleTexts(view).containsAll(listOf("http://192.168.1.100:8123", "Simon")))
        view.withText("Sign out").performClick()
        laidOut(view)
        assertTrue(visibleTexts(view).any { it.startsWith("Your access token keeps working") })
        view.withText("Sign out").performClick()
        assertEquals(listOf("sign out"), calls)
    }

    @Test
    fun `the version is shown, and the close button closes`() {
        val view = settings()
        assertTrue(visibleTexts(view).contains("Version 0.3.0"))
        view.described("Close").performClick()
        assertEquals(listOf("close"), calls)
    }

    @Test
    fun `About opens Games Unleashed in the browser`() {
        val view = settings()
        view.withText("About").performClick()
        laidOut(view)
        view.rowWith("Games Unleashed").performClick()
        assertEquals(listOf("open https://gamesunleashed.com"), calls)
    }

    @Test
    fun screenshots() {
        val view = settings()
        snapshot(view, "settings-devices")
        view.withText("Connection").performClick()
        snapshot(view, "settings-connection")
        view.withText("About").performClick()
        snapshot(view, "settings-about")
    }
}
