package net.badacid.homeassist.echoshow.screens.reorganise

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import net.badacid.homeassist.echoshow.core.Box
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.Device
import net.badacid.homeassist.echoshow.core.GRID_COLUMNS
import net.badacid.homeassist.echoshow.core.GRID_ROWS
import net.badacid.homeassist.echoshow.core.GridPlace
import net.badacid.homeassist.echoshow.core.HomeSnapshot
import net.badacid.homeassist.echoshow.core.Layout
import net.badacid.homeassist.echoshow.core.cardsOnPage
import net.badacid.homeassist.echoshow.core.cellAt
import net.badacid.homeassist.echoshow.core.cellsOf
import net.badacid.homeassist.echoshow.core.findDevice
import net.badacid.homeassist.echoshow.core.moveCard
import net.badacid.homeassist.echoshow.core.pageCount
import net.badacid.homeassist.echoshow.core.removeEmptyPages
import net.badacid.homeassist.echoshow.screens.Icons
import net.badacid.homeassist.echoshow.screens.Theme
import net.badacid.homeassist.echoshow.screens.Widgets
import net.badacid.homeassist.echoshow.screens.home.CardView
import net.badacid.homeassist.echoshow.screens.home.GridView
import net.badacid.homeassist.echoshow.screens.home.cardContent
import kotlin.math.abs

/** How long a card held at the screen's edge waits before carrying on to the next page. */
private const val EDGE_HOLD_MS = 600L

private const val HINT = "Drag a card to move it"
private const val NO_FIT = "It does not fit there"

/**
 * The "Reorganise layout" mode (the LG app's ReorganiseScreen.tsx, by touch): drag a card to a free place;
 * the outline under it is amber where it fits. Dropped exactly on a card of its size, the two swap. Held at
 * the left or right edge, the card goes to the page before or after, and past the last page starts a new
 * one. Done (or Back) keeps the result, with empty pages taken out.
 */
@SuppressLint("ViewConstructor")
class ReorganiseView(
    context: Context,
    layout: Layout,
    private val devices: List<Device>,
    private val home: HomeSnapshot,
    private val onDone: (Layout) -> Unit,
) : FrameLayout(context) {
    private val dp = { v: Float -> Theme.dp(context, v) }
    private var working = layout
    private var page = 0
    private val hint = Theme.text(context, HINT, Theme.BODY_SP, Theme.TEXT_DIM)
    private val grid = GridView(context).apply { setPadding(dp(24f), dp(8f), dp(24f), dp(8f)) }
    private val gridArea = FrameLayout(context)
    private val outline = View(context).apply { visibility = GONE }
    private val dots = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private val pageWords = Theme.text(context, "", Theme.SMALL_SP, Theme.TEXT_DIM)
    private val gap = dp(14f).toFloat()
    private val edge = dp(48f)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val timer = Handler(Looper.getMainLooper())

    // The drag in progress.
    private var held: CardPlace? = null
    private var heldView: CardView? = null
    private var floating: CardView? = null
    private var downX = 0f
    private var downY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var edgeDirection = 0

    init {
        setBackgroundColor(Theme.BACKGROUND)
        isClickable = true
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val done = Widgets.button(context, "Done") { done() }
        column.addView(Widgets.titleBar(context, "Reorganise layout", done), Widgets.matchWidth())
        column.addView(hint, Widgets.matchWidth().apply { leftMargin = dp(24f) })
        gridArea.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        gridArea.addView(outline, LayoutParams(0, 0))
        column.addView(gridArea, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8f) })
        val bottom = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(Widgets.iconButton(context, Icons.LEFT, "Previous page") { showPage(page - 1) })
            addView(dots)
            addView(pageWords, Widgets.wrap().apply { leftMargin = dp(12f); rightMargin = dp(12f) })
            addView(Widgets.iconButton(context, Icons.LEFT, "Next page") { showPage(page + 1) }.apply { scaleX = -1f })
        }
        column.addView(bottom, Widgets.matchWidth())
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        draw()
    }

    /** The last page a card may go to: one past the last, which starts a new page. */
    private val lastPage get() = pageCount(working)

    private fun showPage(next: Int) {
        // Without a card in hand there is no new page to go to.
        val limit = if (held != null) lastPage else pageCount(working) - 1
        page = next.coerceIn(0, limit)
        draw()
    }

    private fun draw() {
        val cards = cardsOnPage(working, page).map { place ->
            val view = CardView(context, place.size, {}, {}, null)
            view.show(cardContent(place.deviceId, findDevice(devices, place.deviceId), home, place.size))
            if (place.deviceId == held?.deviceId) {
                view.alpha = 0.25f
                heldView = view
            }
            place to view
        }
        grid.setCards(cards)
        val pages = maxOf(pageCount(working), page + 1)
        dots.removeAllViews()
        repeat(pages) { index ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (index == page) Theme.TEXT else Theme.SURFACE_RAISED) }
            }, LinearLayout.LayoutParams(dp(10f), dp(10f)).apply { leftMargin = dp(6f); rightMargin = dp(6f) })
        }
        pageWords.text = if (page >= pageCount(working)) "New page" else "Page ${page + 1} of $pages"
    }

    fun done() {
        onDone(removeEmptyPages(working))
    }

    // ---- Where things are ----

    /** The grid's cells, in this view's coordinates. */
    private fun gridBox(): Box {
        var x = grid.paddingLeft.toFloat()
        var y = grid.paddingTop.toFloat()
        var view: View = grid
        while (view !== this) {
            x += view.left
            y += view.top
            view = view.parent as View
        }
        return Box(x, y, (grid.width - grid.paddingLeft - grid.paddingRight).toFloat(), (grid.height - grid.paddingTop - grid.paddingBottom).toFloat())
    }

    private fun cellSize(box: Box) = ((box.width - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS) to ((box.height - gap * (GRID_ROWS - 1)) / GRID_ROWS)

    /** For tests: the centre of a cell, in this view's coordinates. */
    internal fun centreOf(column: Int, row: Int): Pair<Float, Float> {
        val box = gridBox()
        val (w, h) = cellSize(box)
        return (box.left + column * (w + gap) + w / 2) to (box.top + row * (h + gap) + h / 2)
    }

    private fun cardAt(x: Float, y: Float): CardPlace? {
        val box = gridBox()
        val cell = cellAt(x, y, box, gap)
        if (x < box.left || y < box.top || x > box.left + box.width || y > box.top + box.height) return null
        return cardsOnPage(working, page).firstOrNull { place ->
            val cells = cellsOf(place.size)
            cell.column in place.column until place.column + cells.columns && cell.row in place.row until place.row + cells.rows
        }
    }

    /** Where the card in hand would go: the cell nearest its top-left corner. */
    private fun target(): GridPlace? {
        val card = floating ?: return null
        val box = gridBox()
        val (w, h) = cellSize(box)
        val cell = cellAt(card.x + w / 2, card.y + h / 2, box, gap)
        return GridPlace(page, cell.column, cell.row)
    }

    // ---- Dragging ----

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return cardAt(event.x, event.y) != null
        return held != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                held = cardAt(event.x, event.y) ?: return super.onTouchEvent(event)
                downX = event.x
                downY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val place = held ?: return true
                if (!dragging) {
                    if (abs(event.x - downX) < slop && abs(event.y - downY) < slop) return true
                    startDrag(place)
                }
                moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> drop(cancelled = false)
            MotionEvent.ACTION_CANCEL -> drop(cancelled = true)
        }
        return true
    }

    private fun startDrag(place: CardPlace) {
        dragging = true
        hint.text = HINT
        draw()
        val box = gridBox()
        val (w, h) = cellSize(box)
        val cells = cellsOf(place.size)
        val width = (w * cells.columns + gap * (cells.columns - 1)).toInt()
        val height = (h * cells.rows + gap * (cells.rows - 1)).toInt()
        val left = box.left + place.column * (w + gap)
        val top = box.top + place.row * (h + gap)
        grabDx = downX - left
        grabDy = downY - top
        val view = CardView(context, place.size, {}, {}, null).apply {
            show(cardContent(place.deviceId, findDevice(devices, place.deviceId), home, place.size))
            elevation = dp(8f).toFloat()
            alpha = 0.9f
        }
        floating = view
        addView(view, LayoutParams(width, height))
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        outline.layoutParams = FrameLayout.LayoutParams(width, height)
    }

    private fun moveTo(x: Float, y: Float) {
        lastX = x
        lastY = y
        val card = floating ?: return
        card.x = x - grabDx
        card.y = y - grabDy
        showTarget()
        // Held at the edge: carry the card to the page before or after.
        val direction = when {
            x < edge -> -1
            x > (width - edge).toFloat() -> 1
            else -> 0
        }
        if (direction != edgeDirection) {
            edgeDirection = direction
            timer.removeCallbacks(flipPage)
            if (direction != 0) timer.postDelayed(flipPage, EDGE_HOLD_MS)
        }
    }

    private val flipPage: Runnable = object : Runnable {
        override fun run() {
            if (held == null || edgeDirection == 0) return
            val next = (page + edgeDirection).coerceIn(0, lastPage)
            if (next != page) {
                page = next
                draw()
                showTarget()
            }
            timer.postDelayed(this, EDGE_HOLD_MS)
        }
    }

    private fun showTarget() {
        val place = held ?: return
        val to = target() ?: return
        val box = gridBox()
        val (w, h) = cellSize(box)
        val fits = moveCard(working, place.deviceId, to) != null
        outline.background = Theme.roundedPanel(context, android.graphics.Color.TRANSPARENT, 18f, if (fits) Theme.AMBER else Theme.ERROR)
        outline.visibility = VISIBLE
        // The outline lives in the grid area; the grid box is in this view's coordinates.
        outline.x = box.left + to.column * (w + gap) - gridArea.left
        outline.y = box.top + to.row * (h + gap) - gridArea.top
    }

    private fun drop(cancelled: Boolean) {
        timer.removeCallbacks(flipPage)
        edgeDirection = 0
        val place = held
        val to = if (dragging && !cancelled) target() else null
        held = null
        heldView = null
        floating?.let { removeView(it) }
        floating = null
        outline.visibility = GONE
        if (place != null && to != null) {
            val moved = moveCard(working, place.deviceId, to)
            if (moved == null) {
                hint.text = NO_FIT
                page = place.page
            } else {
                working = moved
                hint.text = HINT
            }
        }
        dragging = false
        draw()
    }

    override fun onDetachedFromWindow() {
        timer.removeCallbacks(flipPage)
        super.onDetachedFromWindow()
    }
}
