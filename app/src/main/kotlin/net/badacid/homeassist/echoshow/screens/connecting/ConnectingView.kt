package net.badacid.homeassist.echoshow.screens.connecting

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import net.badacid.homeassist.echoshow.core.OpenFailure
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import net.badacid.homeassist.echoshow.screens.Widgets.ButtonStyle

/** Shown while the app opens its connection, and while it retries. Words from the LG app's ConnectingScreen.tsx. */
class ConnectingView(context: Context, address: String, failure: OpenFailure?, onSignInAgain: () -> Unit) : FrameLayout(context) {
    init {
        setBackgroundColor(Theme.BACKGROUND)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Theme.text(context, heading(failure), 32f, bold = true).apply { gravity = Gravity.CENTER }, Widgets.wrap())
            addView(Theme.text(context, detail(failure, address), Theme.BODY_SP, Theme.TEXT_DIM).apply { gravity = Gravity.CENTER }, Widgets.wrap().apply { topMargin = Theme.dp(context, 16f) })
            if (failure != null) {
                addView(Widgets.button(context, "Sign in again or change the address", ButtonStyle.OUTLINE, onSignInAgain), Widgets.wrap().apply { topMargin = Theme.dp(context, 32f) })
            }
        }
        val side = Theme.dp(context, 120f)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply { leftMargin = side; rightMargin = side })
    }

    private fun heading(failure: OpenFailure?) = when (failure) {
        null -> "Connecting to Home Assistant"
        OpenFailure.NOT_READY -> "Home Assistant is not ready"
        else -> "Cannot reach Home Assistant"
    }

    private fun detail(failure: OpenFailure?, address: String) = when (failure) {
        null -> address
        OpenFailure.NOT_READY -> "It answered at $address but could not send its devices, as happens just after it starts. Trying again every few seconds."
        else -> "Nothing answered at $address. Trying again every few seconds."
    }
}
