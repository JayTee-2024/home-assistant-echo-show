package net.badacid.homeassist.echoshow.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import net.badacid.homeassist.echoshow.core.CardSize
import net.badacid.homeassist.echoshow.screens.IconView
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import kotlin.math.abs

/** How long a press must last to open the panel. */
const val HOLD_MS = 500L

/**
 * One card on the home screen: icon and state at the top, name, room and other parts below; amber while the
 * device is "on". A tap calls `onTap`; holding for HOLD_MS calls `onHold` instead, and the card dims while held.
 */
@SuppressLint("ViewConstructor")
class CardView(
    context: Context,
    private val size: CardSize,
    private val onTap: () -> Unit,
    private val onHold: () -> Unit,
    private val makePicture: (() -> CameraPictureView)? = null,
) : FrameLayout(context) {
    private val dp = { value: Float -> Theme.dp(context, value) }
    private val panel = Theme.roundedPanel(context, Theme.SURFACE, 18f)
    private val icon = FrameLayout(context)
    private val state = Theme.text(context, "", Theme.CARD_NAME_SP, bold = true).apply { gravity = Gravity.END; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val big = Theme.text(context, "", 34f, bold = true).apply { gravity = Gravity.END }
    private val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
    private val name = Theme.text(context, "", Theme.CARD_NAME_SP, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val room = Theme.text(context, "", Theme.CARD_LINE_SP, Theme.TEXT_DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val extras = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val top: LinearLayout
    private val names: LinearLayout
    private var picture: CameraPictureView? = null
    private var iconPath: String? = null
    private var iconView: IconView? = null

    init {
        background = panel
        val pad = dp(16f)
        setPadding(pad, pad, pad, pad)
        val faceColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(state, Widgets.wrap())
            addView(big, Widgets.wrap())
            addView(details, Widgets.wrap())
        }
        top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(icon, LinearLayout.LayoutParams(dp(36f), dp(36f)))
            addView(faceColumn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        }
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        names = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(name, Widgets.matchWidth())
            addView(room, Widgets.matchWidth())
            addView(extras, Widgets.matchWidth())
        }
        addView(names, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { rightMargin = dp(36f) })
        // "More" opens the panel, as holding the card does: the same as the LG card's more button.
        val more = FrameLayout(context).apply {
            contentDescription = "More"
            isClickable = true
            setOnClickListener { onHold() }
            addView(IconView(context, Icons.MORE, Theme.TEXT_DIM), LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
        }
        addView(more, LayoutParams(dp(Theme.TOUCH_DP), dp(Theme.TOUCH_DP), Gravity.BOTTOM or Gravity.END).apply { rightMargin = -dp(12f); bottomMargin = -dp(12f) })
    }

    private var dimmed = false

    /** A short card drops its last extra lines rather than let them run into the state at the top. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (i in 0 until extras.childCount) extras.getChildAt(i).visibility = VISIBLE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (picture != null) return
        var shown = extras.childCount
        while (shown > 0 && paddingTop + top.measuredHeight + Theme.dp(context, 8f) + names.measuredHeight + paddingBottom > measuredHeight) {
            shown -= 1
            extras.getChildAt(shown).visibility = GONE
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** True while the connection is down: the card shows its last known state, dimmed. */
    var stale = false
        set(value) {
            field = value
            applyAlpha()
        }

    private fun restingAlpha() = when {
        stale -> 0.5f
        dimmed -> 0.6f
        else -> 1f
    }

    private fun applyAlpha() {
        alpha = restingAlpha() * if (pressing) 0.7f else 1f
    }

    private var shownContent: CardContent? = null

    fun show(content: CardContent) {
        // Home Assistant reports often; a card whose content has not changed keeps its views as they are.
        if (content == shownContent) return
        shownContent = content
        contentDescription = content.name
        val active = content.isActive
        panel.setColor(if (active) Theme.AMBER_DIM else Theme.SURFACE)
        panel.setStroke(dp(2f), if (active) Theme.AMBER else Theme.SURFACE)
        dimmed = content.isUnavailable || content.isMissing
        applyAlpha()

        if (content.icon != iconPath) {
            iconPath = content.icon
            icon.removeAllViews()
            iconView = content.icon?.let { IconView(context, it, Theme.TEXT).also { view -> icon.addView(view) } }
        }
        iconView?.colour = if (active) Theme.AMBER else Theme.TEXT

        val face = content.face
        state.text = face.state.orEmpty() + (face.unit?.let { " $it" } ?: "")
        state.visibility = if (face.state == null) GONE else VISIBLE
        state.setTextColor(if (active) Theme.AMBER else Theme.TEXT)
        big.text = face.big.orEmpty()
        big.visibility = if (face.big == null) GONE else VISIBLE
        details.removeAllViews()
        face.details.forEachIndexed { index, line ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
            if (index == 0 && face.colour != null) {
                row.addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(face.colour) } }, LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { rightMargin = dp(6f) })
            }
            row.addView(Theme.text(context, line, Theme.CARD_LINE_SP, Theme.TEXT_DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            details.addView(row, Widgets.wrap())
        }

        name.text = content.name
        room.text = content.room.orEmpty()
        room.visibility = if (content.room == null) GONE else VISIBLE
        extras.removeAllViews()
        for (line in content.extraLines) {
            extras.addView(Theme.text(context, line, Theme.CARD_LINE_SP, Theme.TEXT_DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, Widgets.matchWidth())
        }

        // A camera card is its picture, with the name and lines over its lower part.
        if (face.isPicture && content.mainEntity != null && makePicture != null) {
            val view = picture ?: makePicture.invoke().also {
                picture = it
                it.isActive = pictureActive
                addView(it, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { bottomMargin = dp(if (size == CardSize.LARGE) 92f else 60f) })
            }
            view.show(content.mainEntity)
        } else {
            // No longer a picture (the camera has gone from Home Assistant): stop fetching the old one.
            picture?.let {
                it.stop()
                removeView(it)
            }
            picture = null
        }
    }

    // ---- Tap and hold ----

    // A handler, not View.postDelayed: that one waits until the view is attached to a window.
    private val timer = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var held = false
    private var pressing = false
    private val hold = Runnable {
        held = true
        pressing = false
        applyAlpha()
        onHold()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                held = false
                pressing = true
                applyAlpha()
                timer.postDelayed(hold, HOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> if (pressing && (abs(event.x - downX) > slop || abs(event.y - downY) > slop)) cancelPress()
            MotionEvent.ACTION_UP -> {
                val wasTap = pressing && !held
                cancelPress()
                if (wasTap) onTap()
            }
            MotionEvent.ACTION_CANCEL -> cancelPress()
        }
        return true
    }

    private fun cancelPress() {
        timer.removeCallbacks(hold)
        pressing = false
        applyAlpha()
    }

    override fun onDetachedFromWindow() {
        cancelPress()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    /** Only the cards on the page being shown fetch camera pictures. */
    fun setPictureActive(active: Boolean) {
        pictureActive = active
        picture?.isActive = active
    }

    private var pictureActive = true

    /** For tests and accessibility: the visible state line. */
    val stateText: TextView get() = state
}
