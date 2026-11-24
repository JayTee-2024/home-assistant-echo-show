package net.badacid.homeassist.echoshow.screens

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** The app's buttons, fields and title bar, so every screen looks the same. */
object Widgets {
    enum class ButtonStyle { MAIN, OUTLINE, LINK }

    fun button(context: Context, words: String, style: ButtonStyle = ButtonStyle.MAIN, onClick: () -> Unit): TextView =
        Theme.text(context, words, Theme.BODY_SP, bold = style == ButtonStyle.MAIN).apply {
            gravity = Gravity.CENTER
            minHeight = Theme.dp(context, Theme.TOUCH_DP)
            val side = Theme.dp(context, 24f)
            setPadding(side, Theme.dp(context, 10f), side, Theme.dp(context, 10f))
            when (style) {
                ButtonStyle.MAIN -> {
                    setTextColor(Theme.BACKGROUND)
                    background = pressable(context, Theme.roundedPanel(context, Theme.AMBER, 12f), Theme.roundedPanel(context, Theme.AMBER_PRESSED, 12f))
                }
                ButtonStyle.OUTLINE -> {
                    setTextColor(Theme.TEXT)
                    background = pressable(
                        context,
                        Theme.roundedPanel(context, Theme.BACKGROUND, 12f, strokeColour = Theme.TEXT_DIM),
                        Theme.roundedPanel(context, Theme.SURFACE_RAISED, 12f, strokeColour = Theme.TEXT_DIM),
                    )
                }
                ButtonStyle.LINK -> {
                    setTextColor(Theme.AMBER)
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(0, Theme.dp(context, 10f), 0, Theme.dp(context, 10f))
                }
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun pressable(context: Context, normal: android.graphics.drawable.Drawable, pressed: android.graphics.drawable.Drawable) =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }

    /** A labelled text field. `onDone` runs for the keyboard's Go key. */
    fun field(context: Context, label: String, hint: String? = null, isPassword: Boolean = false, onDone: (() -> Unit)? = null): Pair<View, EditText> {
        val input = EditText(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_SP)
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.TEXT_DIM)
            this.hint = hint
            isSingleLine = true
            inputType = if (isPassword) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
            }
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            background = Theme.roundedPanel(context, Theme.SURFACE, 10f, strokeColour = Theme.SURFACE_RAISED)
            val side = Theme.dp(context, 14f)
            setPadding(side, side, side, side)
            setOnEditorActionListener { _, _, _ ->
                onDone?.invoke()
                onDone != null
            }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Theme.text(context, label, Theme.SMALL_SP, Theme.TEXT_DIM), wrap())
            addView(input, matchWidth(top = 6f))
        }
        return column to input
    }

    /** The bar across the top: a title on the left, and optional buttons on the right. */
    fun titleBar(context: Context, title: String, vararg buttons: View): View = FrameLayout(context).apply {
        val side = Theme.dp(context, 24f)
        setPadding(side, Theme.dp(context, 12f), Theme.dp(context, 12f), Theme.dp(context, 4f))
        addView(Theme.text(context, title, Theme.TITLE_SP, bold = true), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        for (button in buttons) row.addView(button)
        addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
    }

    /** A round icon button with at least the touch-target size. */
    fun iconButton(context: Context, path: String, description: String, onClick: () -> Unit): View = FrameLayout(context).apply {
        val size = Theme.dp(context, 56f)
        layoutParams = LinearLayout.LayoutParams(size, size)
        contentDescription = description
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), Theme.roundedPanel(context, Theme.SURFACE_RAISED, 28f))
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(0))
        }
        val icon = IconView(context, path, Theme.TEXT)
        val iconSize = Theme.dp(context, 30f)
        addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun matchWidth(top: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (top * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
        }
}
