package net.badacid.homeassist.echoshow.screens.lock

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import net.badacid.homeassist.echoshow.core.settleWait
import net.badacid.homeassist.echoshow.core.PinEntry
import net.badacid.homeassist.echoshow.core.PinResult
import net.badacid.homeassist.echoshow.core.deleteDigit
import net.badacid.homeassist.echoshow.core.secondsToWait
import net.badacid.homeassist.echoshow.core.typeDigit
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import net.badacid.homeassist.echoshow.screens.shared.PinPadView

/** The text after too many wrong tries, counting down. */
fun waitWords(seconds: Int) = "Too many wrong tries. Wait $seconds seconds."

const val WRONG_PIN = "Wrong PIN. Try again."

/**
 * The PIN lock screen (the LG app's LockScreen.tsx), over everything. Five wrong PINs wait 30 seconds;
 * "Forgot PIN?" offers to sign out, which removes the PIN. The PIN itself is never shown or logged.
 */
@SuppressLint("ViewConstructor")
class LockView(
    context: Context,
    private val pin: String,
    private val clock: () -> Long,
    /** The wrong tries already made, and when the wait after them ends: they outlast the app being restarted. */
    wrongTries: Int,
    waitUntil: Long,
    private val onTries: (wrongTries: Int, waitUntil: Long) -> Unit,
    private val onUnlock: () -> Unit,
    private val onSignOut: () -> Unit,
) : FrameLayout(context) {
    private val dp = { v: Float -> Theme.dp(context, v) }
    private var entry: PinEntry = settleWait(PinEntry("", wrongTries, waitUntil), clock())
    private var wasWrong = false
    private val pad = PinPadView(context, ::digit) {
        entry = deleteDigit(entry)
        draw()
    }.apply { setTitle("Enter your PIN") }
    private val lockBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(pad, Widgets.wrap())
        addView(Widgets.button(context, "Forgot PIN?", Widgets.ButtonStyle.LINK) { askSignOut(true) }, Widgets.wrap().apply { topMargin = dp(16f) })
    }
    private val forgotBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = GONE
        addView(Theme.text(context, "Forgot your PIN?", Theme.HEADING_SP, bold = true), Widgets.wrap())
        addView(Theme.text(context, "Signing out removes the PIN. You can then sign in again with your Home Assistant username and password, and your layout is kept.", Theme.BODY_SP, Theme.TEXT_DIM).apply {
            gravity = Gravity.CENTER
            maxWidth = dp(560f)
        }, Widgets.wrap().apply { topMargin = dp(12f) })
        addView(LinearLayout(context).apply {
            addView(Widgets.button(context, "Sign out") { onSignOut() })
            addView(Widgets.button(context, "Cancel", Widgets.ButtonStyle.OUTLINE) { askSignOut(false) }, Widgets.wrap().apply { leftMargin = dp(12f) })
        }, Widgets.wrap().apply { topMargin = dp(24f) })
    }
    private val tick = Handler(Looper.getMainLooper())
    private val countDown = object : Runnable {
        override fun run() {
            draw()
            if (secondsToWait(entry, clock()) > 0) tick.postDelayed(this, 1_000)
        }
    }

    init {
        setBackgroundColor(Theme.BACKGROUND)
        isClickable = true
        contentDescription = "Enter PIN"
        addView(lockBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(forgotBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        if (entry.waitUntil != waitUntil) onTries(entry.wrongTries, entry.waitUntil)
        draw()
        if (secondsToWait(entry, clock()) > 0) tick.postDelayed(countDown, 1_000)
    }

    private fun digit(digit: String) {
        val step = typeDigit(entry, digit, pin, clock())
        val before = entry
        entry = step.entry
        if (step.result != PinResult.UNLOCKED && (entry.wrongTries != before.wrongTries || entry.waitUntil != before.waitUntil)) {
            onTries(entry.wrongTries, entry.waitUntil)
        }
        when (step.result) {
            PinResult.UNLOCKED -> onUnlock()
            PinResult.WRONG -> wasWrong = true
            PinResult.MORE -> wasWrong = false
            PinResult.WAITING -> Unit
        }
        draw()
        tick.removeCallbacks(countDown)
        if (secondsToWait(entry, clock()) > 0) tick.postDelayed(countDown, 1_000)
    }

    private fun draw() {
        val waiting = secondsToWait(entry, clock())
        pad.setTyped(entry.digits.length)
        pad.setMessage(if (waiting > 0) waitWords(waiting) else if (wasWrong) WRONG_PIN else null)
    }

    private fun askSignOut(asking: Boolean) {
        lockBox.visibility = if (asking) GONE else VISIBLE
        forgotBox.visibility = if (asking) VISIBLE else GONE
    }

    override fun onDetachedFromWindow() {
        tick.removeCallbacks(countDown)
        super.onDetachedFromWindow()
    }
}
