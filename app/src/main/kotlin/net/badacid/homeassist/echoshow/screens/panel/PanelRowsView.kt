package net.badacid.homeassist.echoshow.screens.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import net.badacid.homeassist.echoshow.core.Scheduler
import net.badacid.homeassist.echoshow.core.createDebouncer
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.screens.FlowLayout
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/** Runs scheduled work on the main thread, for the core debouncer. */
private val mainScheduler = Scheduler { delayMs, run ->
    val handler = Handler(Looper.getMainLooper())
    val task = Runnable { run() }
    handler.postDelayed(task, delayMs)
    net.badacid.homeassist.echoshow.core.Cancellable { handler.removeCallbacks(task) }
}

/** A panel button: amber when it is the current one of a set, greyed when it cannot be pressed. */
fun panelButton(context: Context, label: String, isCurrent: Boolean, canPress: Boolean, swatch: Int?, onPress: () -> Unit): TextView {
    val dp = { v: Float -> Theme.dp(context, v) }
    return Theme.text(context, label, Theme.BODY_SP, if (isCurrent) Theme.AMBER else Theme.TEXT).apply {
        gravity = Gravity.CENTER
        minHeight = dp(Theme.TOUCH_DP)
        minWidth = dp(64f)
        setPadding(dp(18f), 0, dp(18f), 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), Theme.roundedPanel(context, Theme.AMBER_PRESSED, 12f))
            addState(intArrayOf(), Theme.roundedPanel(context, if (isCurrent) Theme.AMBER_DIM else Theme.SURFACE_RAISED, 12f, if (isCurrent) Theme.AMBER else null))
        }
        if (swatch != null) {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(swatch shr 16 and 0xFF, swatch shr 8 and 0xFF, swatch and 0xFF))
                setSize(dp(16f), dp(16f))
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            compoundDrawablePadding = dp(8f)
        }
        isEnabled = canPress
        alpha = if (canPress) 1f else 0.4f
        isClickable = canPress
        setOnClickListener { onPress() }
    }
}

/**
 * − and + around a number (the heating target, a player's volume). The number changes on screen at once and
 * is sent once it has rested; until Home Assistant's own report replaces it, a report in between does not
 * undo it (the LG app's ClimatePanel). Kept across redraws, so a waiting number is not lost.
 */
@SuppressLint("ViewConstructor")
class StepperView(context: Context, label: String, private var stepper: PanelControl.Stepper, private val onSend: (Action) -> Unit) : LinearLayout(context) {
    private var chosen: Double? = null
    private val value = Theme.text(context, "", Theme.BODY_SP, bold = true).apply { gravity = Gravity.CENTER; minWidth = Theme.dp(context, 88f) }
    private val minus = panelButton(context, "−", false, true, null) { press(-1) }.apply { contentDescription = "Decrease $label" }
    private val plus = panelButton(context, "+", false, true, null) { press(1) }.apply { contentDescription = "Increase $label" }
    private val debouncer = createDebouncer({
        chosen?.let { onSend(stepper.send(it)) }
        chosen = null
        refresh()
    }, stepper.debounceMs, mainScheduler)

    init {
        gravity = Gravity.CENTER_VERTICAL
        addView(minus)
        addView(value)
        addView(plus)
        refresh()
    }

    fun update(next: PanelControl.Stepper) {
        stepper = next
        refresh()
    }

    private fun press(direction: Int) {
        chosen = stepper.step(chosen ?: stepper.value, direction)
        refresh()
        debouncer.trigger()
    }

    /** Sends a number still waiting, as when the panel closes. */
    fun flush() = debouncer.flush()

    private fun refresh() {
        value.text = stepper.format(chosen ?: stepper.value)
        for (button in listOf(minus, plus)) {
            button.isEnabled = stepper.isEnabled
            button.isClickable = stepper.isEnabled
            button.alpha = if (stepper.isEnabled) 1f else 0.4f
        }
    }
}

/**
 * One part's rows: the label on the left, then its controls, wrapping. `pressAt(row, control)` is asked at
 * tap time, so a button always runs the action built from the newest report.
 */
@SuppressLint("ViewConstructor")
class PanelRowsView(
    context: Context,
    rows: List<PanelRow>,
    private val stepperFor: (rowLabel: String, PanelControl.Stepper) -> StepperView,
    private val pressAt: (row: Int, control: Int) -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val dp = { v: Float -> Theme.dp(context, v) }
        rows.forEachIndexed { rowIndex, row ->
            val line = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8f), 0, dp(8f))
                minimumHeight = dp(64f)
            }
            line.addView(Theme.text(context, row.label, Theme.BODY_SP, Theme.TEXT_DIM).apply { maxLines = 2 }, LayoutParams(dp(170f), LayoutParams.WRAP_CONTENT))
            val controls = FlowLayout(context, dp(10f))
            row.controls.forEachIndexed { index, control ->
                controls.addView(when (control) {
                    is PanelControl.Press -> panelButton(context, control.label, control.isCurrent, control.action != null, control.swatch) { pressAt(rowIndex, index) }
                    is PanelControl.Value -> Theme.text(context, control.text, Theme.BODY_SP, bold = true).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        minHeight = dp(Theme.TOUCH_DP)
                        setPadding(dp(6f), 0, dp(6f), 0)
                    }
                    is PanelControl.Stepper -> stepperFor(row.label, control).also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                })
            }
            line.addView(controls, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (rowIndex > 0) addView(View(context).apply { setBackgroundColor(Color.parseColor("#2A313C")) }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)))
            addView(line, Widgets.matchWidth())
        }
    }
}
