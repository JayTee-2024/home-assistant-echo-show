package net.badacid.homeassist.echoshow.screens.signin

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import net.badacid.homeassist.echoshow.screens.Widgets.ButtonStyle

/**
 * The sign-in screen: what the app needs on the left, the current form on the right. Draws SignInFlow's
 * state; call [show] whenever it changes. Words from the LG app's SignInScreen.tsx.
 */
class SignInView(context: Context, private val flow: SignInFlow) : FrameLayout(context) {
    private val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var shownForm: SignInForm? = null
    private var shownAnswers = -1
    private var username: EditText? = null
    private var password: EditText? = null
    private var code: EditText? = null
    private var address: EditText? = null
    private var token: EditText? = null
    private val message = Theme.text(context, "", Theme.BODY_SP, Theme.ERROR)
    private val addressBox = LinearLayout(context)
    private val addressLabel = Theme.text(context, "", Theme.SMALL_SP, Theme.TEXT_DIM)
    private val addressValue = Theme.text(context, "", Theme.HEADING_SP, bold = true)
    private val mainButtons = mutableListOf<android.widget.TextView>()

    init {
        setBackgroundColor(Theme.BACKGROUND)
        val dp = { value: Float -> Theme.dp(context, value) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48f), dp(40f), dp(48f), dp(24f))
        }

        val intro = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Theme.text(context, "Connect to your Home Assistant", 32f, bold = true), Widgets.wrap())
            addView(Theme.text(context, "This app shows and controls the devices you already have in Home Assistant.", Theme.BODY_SP, Theme.TEXT_DIM), Widgets.matchWidth(top = 12f))
            addView(Theme.text(context, "Before you start", Theme.SMALL_SP, Theme.TEXT_DIM, bold = true), Widgets.matchWidth(top = 32f))
            addView(step(context, 1, "Home Assistant is running, on the same network as this Echo."), Widgets.matchWidth(top = 12f))
            addView(step(context, 2, "You have a Home Assistant username and password. A separate user for the Echo is a good idea."), Widgets.matchWidth(top = 12f))
            addView(step(context, 3, "Your devices are already added to Home Assistant."), Widgets.matchWidth(top = 12f))
        }
        row.addView(intro, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(56f) })

        addressBox.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Theme.roundedPanel(context, Theme.SURFACE, 14f)
            setPadding(dp(20f), dp(14f), dp(14f), dp(14f))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(addressLabel)
                addView(addressValue)
            }
            addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(Widgets.button(context, "Change", ButtonStyle.OUTLINE) { flow.change() })
        }
        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(addressBox, Widgets.matchWidth())
            addView(message, Widgets.matchWidth(top = 16f))
            addView(form, Widgets.matchWidth(top = 8f))
        }
        row.addView(ScrollView(context).apply { addView(right) }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(row)
    }

    fun show(state: SignInUiState) {
        addressBox.visibility = if (state.form == SignInForm.ADDRESS) View.GONE else View.VISIBLE
        addressLabel.text = when {
            // Searching the network: there is no address yet.
            state.address.isEmpty() -> "Looking for Home Assistant on your network…"
            state.form == SignInForm.LOOKING -> "Looking for Home Assistant at"
            else -> "Home Assistant found at"
        }
        addressValue.text = state.shownAddress
        addressValue.visibility = if (state.address.isEmpty()) View.GONE else View.VISIBLE
        message.text = state.message ?: ""
        message.visibility = if (state.message == null) View.GONE else View.VISIBLE

        if (state.form != shownForm) {
            shownForm = state.form
            buildForm(state)
        } else if (state.answers != shownAnswers) {
            // Each answer from Home Assistant clears what must be typed again.
            password?.setText("")
            code?.setText("")
        }
        shownAnswers = state.answers
        mainButtons.forEach { it.alpha = if (state.busy) 0.5f else 1f }
        mainButtons.firstOrNull()?.let { button ->
            button.text = when {
                state.form == SignInForm.CREDENTIALS && state.busy -> "Signing in…"
                state.form == SignInForm.CREDENTIALS -> "Sign in"
                state.form == SignInForm.CODE && state.busy -> "Checking…"
                state.form == SignInForm.CODE -> "Verify"
                else -> button.text
            }
        }
    }

    private fun buildForm(state: SignInUiState) {
        form.removeAllViews()
        mainButtons.clear()
        username = null; password = null; code = null; address = null; token = null
        val gap = 16f
        fun main(words: String, action: () -> Unit) = Widgets.button(context, words) { if (!flow.state.value.busy) action() }.also {
            mainButtons += it
            form.addView(it, Widgets.matchWidth(top = 20f))
        }
        when (state.form) {
            SignInForm.LOOKING -> Unit
            SignInForm.ADDRESS -> {
                val (view, input) = Widgets.field(context, "Home Assistant address", "For example 192.168.1.100:8123") { flow.connect(address!!.text.toString()) }
                input.setText(state.typedAddress)
                address = input
                form.addView(view, Widgets.matchWidth())
                main("Connect") { flow.connect(input.text.toString()) }
                form.addView(Widgets.button(context, "Advanced: use an access token", ButtonStyle.LINK) { flow.useTokenFor(input.text.toString()) }, Widgets.matchWidth(top = 8f))
            }
            SignInForm.CREDENTIALS -> {
                val (userView, userInput) = Widgets.field(context, "Username")
                val (passView, passInput) = Widgets.field(context, "Password", isPassword = true) { submitCredentials() }
                username = userInput
                password = passInput
                form.addView(userView, Widgets.matchWidth())
                form.addView(passView, Widgets.matchWidth(top = gap))
                main("Sign in") { submitCredentials() }
                form.addView(Theme.text(context, "Your password is not kept on this Echo. You can sign out at any time in Settings.", Theme.SMALL_SP, Theme.TEXT_DIM), Widgets.matchWidth(top = 12f))
                form.addView(Widgets.button(context, "Advanced: use an access token", ButtonStyle.LINK) { flow.goToToken() }, Widgets.matchWidth(top = 4f))
            }
            SignInForm.CODE -> {
                val (view, input) = Widgets.field(context, "Two-step code") { flow.verify(code!!.text.toString()) }
                code = input
                form.addView(view, Widgets.matchWidth())
                main("Verify") { flow.verify(input.text.toString()) }
            }
            SignInForm.TOKEN -> {
                val (view, input) = Widgets.field(context, "Long-lived access token", "Made on your profile page in Home Assistant, under Security") { flow.useToken(token!!.text.toString()) }
                input.setText(state.typedToken ?: "")
                token = input
                form.addView(view, Widgets.matchWidth())
                main("Connect") { flow.useToken(input.text.toString()) }
                form.addView(Widgets.button(context, "Back to username and password", ButtonStyle.LINK) { flow.backToCredentials() }, Widgets.matchWidth(top = 8f))
            }
        }
    }

    private fun submitCredentials() = flow.signIn(username?.text?.toString() ?: "", password?.text?.toString() ?: "")

    private fun step(context: Context, number: Int, words: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val badge = Theme.text(context, number.toString(), Theme.BODY_SP, Theme.BACKGROUND, bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.roundedPanel(context, Theme.AMBER, 16f)
        }
        val size = Theme.dp(context, 32f)
        addView(badge, LinearLayout.LayoutParams(size, size).apply { rightMargin = Theme.dp(context, 14f) })
        addView(Theme.text(context, words, Theme.BODY_SP), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
}
