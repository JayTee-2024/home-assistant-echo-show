package net.badacid.homeassist.echoshow.screens.signin

import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.screens.visibleTexts
import net.badacid.homeassist.echoshow.screens.withText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h600dp-land-tvdpi")
class SignInViewTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val form = HttpAnswer(200, """{"type":"form","flow_id":"flow-1","step_id":"init","errors":{}}""")

    private fun shown(find: (suspend () -> String?)?, act: (SignInFlow) -> Unit = {}): SignInView {
        val scope = TestScope(StandardTestDispatcher())
        val flow = SignInFlow({ form }, scope, { 0L }, if (find == null) "http://192.168.1.100:8123" else null, null, null, find) {}
        flow.start()
        scope.testScheduler.advanceUntilIdle()
        act(flow)
        scope.testScheduler.advanceUntilIdle()
        return SignInView(context, flow).apply {
            show(flow.state.value)
            measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1280, 800)
        }
    }

    private fun edits(view: View): List<EditText> = when (view) {
        is EditText -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { edits(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `an address found on the network asks the person to check it is theirs`() {
        val found = shown(find = { "http://192.168.1.50:8123" })
        assertTrue(visibleTexts(found).toString(), visibleTexts(found).contains("Found on your network. Check this is your Home Assistant:"))
        val saved = shown(find = null)
        assertTrue(visibleTexts(saved).contains("Home Assistant found at"))
    }

    @Test
    fun `the access token is hidden as it is typed, until Show is pressed`() {
        val view = shown(find = null) { it.goToToken() }
        val token = edits(view).single()
        assertTrue(token.transformationMethod is PasswordTransformationMethod)
        view.withText("Show").performClick()
        assertFalse(token.transformationMethod is PasswordTransformationMethod)
        view.withText("Hide").performClick()
        assertTrue(token.transformationMethod is PasswordTransformationMethod)
    }
}
