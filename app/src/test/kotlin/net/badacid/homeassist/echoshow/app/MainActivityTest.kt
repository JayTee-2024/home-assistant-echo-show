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

    private fun saved(signedIn: Boolean) = Memory(
        serialiseSavedState(
            EMPTY_SAVED_STATE.copy(
                signIn = if (signedIn) signIn else null,
                layout = Layout(listOf(
                    CardPlace("device:dev_lifx_lamp", 0, 0, 0, CardSize.SMALL),
                    CardPlace("device:dev_tado", 1, 0, 0, CardSize.WIDE),
                )),
                layoutAddress = signIn.address,
            ),
        ),
    )

    private fun useFakes(signedIn: Boolean = true) {
        app.storageForTests = saved(signedIn)
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
}
