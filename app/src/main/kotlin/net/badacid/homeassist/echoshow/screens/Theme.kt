package net.badacid.homeassist.echoshow.screens

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

/** The app's look, as the LG app's: dark background, light text, amber for "on". */
object Theme {
    val BACKGROUND = Color.parseColor("#0E1116")
    val SURFACE = Color.parseColor("#1A1F27")
    val SURFACE_RAISED = Color.parseColor("#252B35")
    val TEXT = Color.parseColor("#E6E8EB")
    val TEXT_DIM = Color.parseColor("#9AA3AE")
    val AMBER = Color.parseColor("#FFB454")
    val AMBER_DIM = Color.parseColor("#3A2E1B")
    val AMBER_PRESSED = Color.parseColor("#D9963F")
    val ERROR = Color.parseColor("#FF7A7A")
    val BACKDROP = Color.parseColor("#B3000000")

    const val TITLE_SP = 28f
    const val HEADING_SP = 22f
    const val BODY_SP = 18f
    const val CARD_NAME_SP = 18f
    const val CARD_LINE_SP = 15f
    const val SMALL_SP = 14f

    /** Android's smallest comfortable touch target. */
    const val TOUCH_DP = 48f

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun roundedPanel(context: Context, colour: Int, radiusDp: Float = 16f, strokeColour: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(colour)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColour != null) setStroke(dp(context, 2f), strokeColour)
        }

    fun text(context: Context, words: String = "", sizeSp: Float = BODY_SP, colour: Int = TEXT, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = words
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(colour)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}
