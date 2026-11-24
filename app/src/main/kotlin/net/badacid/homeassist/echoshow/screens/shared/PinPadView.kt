package net.badacid.homeassist.echoshow.screens.shared

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import net.badacid.homeassist.echoshow.core.PIN_LENGTH
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/**
 * The on-screen PIN pad (the LG app's PinPad.tsx): a title, four dots for the digits typed, a message line,
 * then 1–9, and 0 under the 8 with Delete beside it, as on a phone.
 */
class PinPadView(context: Context, private val onDigit: (String) -> Unit, private val onDelete: () -> Unit) : LinearLayout(context) {
    private val dp = { v: Float -> Theme.dp(context, v) }
    private val title = Theme.text(context, "", Theme.HEADING_SP, bold = true).apply { gravity = Gravity.CENTER }
    private val dots = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val message = Theme.text(context, " ", Theme.BODY_SP, Theme.AMBER).apply { gravity = Gravity.CENTER }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(title, Widgets.matchWidth())
        repeat(PIN_LENGTH) { dots.addView(View(context), LayoutParams(dp(16f), dp(16f)).apply { leftMargin = dp(8f); rightMargin = dp(8f) }) }
        addView(dots, Widgets.matchWidth().apply { topMargin = dp(14f) })
        addView(message, Widgets.matchWidth().apply { topMargin = dp(10f) })
        for (row in listOf("123", "456", "789")) addView(keyRow(row.map { it.toString() }), Widgets.wrap().apply { topMargin = dp(10f) })
        addView(keyRow(listOf("", "0", "Delete")), Widgets.wrap().apply { topMargin = dp(10f) })
        setTyped(0)
    }

    private fun keyRow(keys: List<String>) = LinearLayout(context).apply {
        keys.forEachIndexed { index, key ->
            val wide = keys.size == 3 && index != 1 && keys[2] == "Delete"
            val params = LayoutParams(dp(if (wide) 96f else 64f), dp(64f)).apply { if (index > 0) leftMargin = dp(12f) }
            // The gap before the 0 is as wide as Delete, so the 0 stays under the 8.
            if (key.isEmpty()) {
                addView(View(context), params)
            } else {
                val button = Widgets.button(context, key, Widgets.ButtonStyle.OUTLINE) { if (key == "Delete") onDelete() else onDigit(key) }
                addView(button, params)
            }
        }
    }

    fun setTitle(words: String) {
        title.text = words
    }

    fun setMessage(words: String?) {
        message.text = words ?: " "
    }

    fun setTyped(count: Int) {
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (i < count) setColor(Theme.TEXT) else { setColor(0); setStroke(dp(2f), Theme.TEXT_DIM) }
            }
        }
    }
}
