package net.badacid.homeassist.echoshow.screens.panel

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.devices.Action
import net.badacid.homeassist.echoshow.devices.PanelControl
import net.badacid.homeassist.echoshow.devices.PanelRow
import net.badacid.homeassist.echoshow.devices.panelRows
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/**
 * A device's panel: a box over the home screen, on a dark backdrop, listing each part with what it is
 * doing. A tap on the backdrop or ✕ closes it. Each type's own controls come in plan 4.
 */
@SuppressLint("ViewConstructor")
class PanelView(context: Context, private val onClose: () -> Unit, private val onRun: (Action) -> Unit) : FrameLayout(context) {
    private val dp = { value: Float -> Theme.dp(context, value) }
    private val box = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.roundedPanel(context, Theme.SURFACE, 20f)
        setPadding(dp(28f), dp(16f), dp(16f), dp(24f))
        isClickable = true // Taps inside the box stay in it.
    }
    private val title = Theme.text(context, "", Theme.TITLE_SP, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val room = Theme.text(context, "", Theme.BODY_SP, Theme.TEXT_DIM)
    private val parts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(Theme.BACKDROP)
        isClickable = true
        val heading = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            val words = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title, Widgets.matchWidth())
                addView(room, Widgets.matchWidth())
            }
            addView(words, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(Widgets.iconButton(context, Icons.CLOSE, "Close") { close() })
        }
        box.addView(heading, Widgets.matchWidth())
        box.addView(ScrollView(context).apply { addView(parts) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })
        addView(box, LayoutParams(dp(820f), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun show(device: Device, home: HomeSnapshot) {
        contentDescription = device.name
        title.text = device.name
        room.text = device.roomName.orEmpty()
        room.visibility = if (device.roomName == null) GONE else VISIBLE
        val shown = device.parts.mapNotNull { part ->
            val entity = home.states[part.entityId] ?: return@mapNotNull null
            part to panelRows(part.typeId, entity)
        }
        rowsByPart = shown.map { it.second }
        // Redraw only when what is shown changed: a report every second would otherwise replace a button
        // between a finger going down and coming up. Steppers are kept by part and row.
        val signature = shown.map { (part, rows) -> part.entityId to rows.map(::signatureOf) }
        if (signature == shownSignature) {
            updateSteppers(shown)
            return
        }
        shownSignature = signature
        parts.removeAllViews()
        val manyParts = shown.size > 1
        shown.forEachIndexed { partIndex, (part, rows) ->
            // A heading only where it adds something: not for a part named like its device.
            if (manyParts && part.name != device.name) {
                parts.addView(Theme.text(context, part.name, Theme.HEADING_SP, bold = true), Widgets.matchWidth().apply { topMargin = dp(if (partIndex == 0) 4f else 20f) })
            }
            parts.addView(PanelRowsView(context, rows, { label, stepper -> stepperView(part.entityId, label, stepper) }) { row, control ->
                (rowsByPart.getOrNull(partIndex)?.getOrNull(row)?.controls?.getOrNull(control) as? PanelControl.Press)?.action?.let(onRun)
            }, Widgets.matchWidth())
        }
    }

    private var rowsByPart: List<List<PanelRow>> = emptyList()
    private var shownSignature: Any? = null
    private val steppers = mutableMapOf<String, StepperView>()

    private fun stepperView(entityId: String, label: String, stepper: PanelControl.Stepper): StepperView =
        steppers.getOrPut("$entityId/$label") { StepperView(context, label, stepper, onRun) }.also { it.update(stepper) }

    private fun updateSteppers(shown: List<Pair<net.badacid.homeassist.echoshow.core.Part, List<PanelRow>>>) {
        for ((part, rows) in shown) for (row in rows) for (control in row.controls) {
            if (control is PanelControl.Stepper) steppers["${part.entityId}/${row.label}"]?.update(control)
        }
    }

    private fun signatureOf(row: PanelRow): Any = row.label to row.controls.map {
        when (it) {
            is PanelControl.Press -> listOf(it.label, it.isCurrent, it.action != null, it.swatch)
            is PanelControl.Value -> it.text
            // The stepper keeps itself up to date; only its place counts.
            is PanelControl.Stepper -> "stepper"
        }
    }

    /** Sends anything still waiting, then closes. */
    private fun close() {
        steppers.values.forEach { it.flush() }
        onClose()
    }

    override fun onDetachedFromWindow() {
        steppers.values.forEach { it.flush() }
        super.onDetachedFromWindow()
    }

    // The box is limited to most of the screen's height, so a device with many parts scrolls inside it.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxBox = MeasureSpec.getSize(heightMeasureSpec) - dp(64f)
        (box.layoutParams as LayoutParams).height = LayoutParams.WRAP_CONTENT
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (box.measuredHeight > maxBox) {
            (box.layoutParams as LayoutParams).height = maxBox
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    // A tap on the backdrop (outside the box) closes the panel.
    private var downOnBackdrop = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downOnBackdrop = true
            MotionEvent.ACTION_UP -> if (downOnBackdrop) { downOnBackdrop = false; close() }
            MotionEvent.ACTION_CANCEL -> downOnBackdrop = false
        }
        return true
    }
}
