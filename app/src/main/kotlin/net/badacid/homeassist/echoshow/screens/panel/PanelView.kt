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
import net.badacid.homeassist.echoshow.devices.deviceLogicFor
import net.badacid.homeassist.echoshow.screens.IconView
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets

/**
 * A device's panel: a box over the home screen, on a dark backdrop, listing each part with what it is
 * doing. A tap on the backdrop or ✕ closes it. Each type's own controls come in plan 4.
 */
@SuppressLint("ViewConstructor")
class PanelView(context: Context, private val onClose: () -> Unit) : FrameLayout(context) {
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
            addView(Widgets.iconButton(context, Icons.CLOSE, "Close") { onClose() })
        }
        box.addView(heading, Widgets.matchWidth())
        box.addView(ScrollView(context).apply { addView(parts) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12f) })
        box.addView(Theme.text(context, "More controls for this device are coming in the next version.", Theme.SMALL_SP, Theme.TEXT_DIM), Widgets.matchWidth().apply { topMargin = dp(16f) })
        addView(box, LayoutParams(dp(640f), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun show(device: Device, home: HomeSnapshot) {
        contentDescription = device.name
        title.text = device.name
        room.text = device.roomName.orEmpty()
        room.visibility = if (device.roomName == null) GONE else VISIBLE
        parts.removeAllViews()
        for (part in device.parts) {
            val logic = deviceLogicFor(part.typeId)
            val entity = home.states[part.entityId]
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56f)
                val icon = FrameLayout(context)
                logic?.icon?.let { icon.addView(IconView(context, it, Theme.TEXT_DIM)) }
                addView(icon, LinearLayout.LayoutParams(dp(28f), dp(28f)).apply { rightMargin = dp(16f) })
                addView(Theme.text(context, part.name, Theme.BODY_SP).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                val summary = entity?.let { logic?.summary(it) } ?: if (entity == null) "Unavailable" else null
                summary?.let { addView(Theme.text(context, it, Theme.BODY_SP, Theme.TEXT_DIM), Widgets.wrap().apply { leftMargin = dp(16f) }) }
            }
            parts.addView(row, Widgets.matchWidth())
        }
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
            MotionEvent.ACTION_UP -> if (downOnBackdrop) { downOnBackdrop = false; onClose() }
            MotionEvent.ACTION_CANCEL -> downOnBackdrop = false
        }
        return true
    }
}
