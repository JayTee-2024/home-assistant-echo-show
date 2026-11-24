package net.badacid.homeassist.echoshow.screens.home

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pages side by side, one screen wide each. A drag moves them with the finger; letting go past a third of
 * the width, or with a fling, asks for the next or previous page through `onPage`, and the pages snap to
 * whichever page `showPage` is then given. Taps go through to the cards; a sideways drag is taken from them.
 */
class PagerView(context: Context, private val onPage: (Int) -> Unit) : ViewGroup(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingSpeed = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var page = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var snap: ValueAnimator? = null

    fun setPages(pages: List<View>) {
        removeAllViews()
        pages.forEach { addView(it) }
        page = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        scrollTo(page * width, 0)
    }

    fun showPage(newPage: Int, animate: Boolean) {
        val target = newPage.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val changed = target != page
        page = target
        if (animate && (changed || scrollX != page * width)) snapTo(page * width) else if (!dragging && snap == null) scrollTo(page * width, 0)
    }

    private fun snapTo(x: Int) {
        snap?.cancel()
        snap = ValueAnimator.ofInt(scrollX, x).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { if (snap === animation) snap = null }
            })
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val w = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val h = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
        if (!dragging && snap == null) scrollTo(page * w, 0)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                if (snap != null) { snap?.cancel(); snap = null; dragging = true }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                if (!dragging && dx > slop && dx > abs(event.y - downY)) {
                    dragging = true
                    downX = event.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) stopTracking()
        }
        return dragging
    }

    private var lastX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; lastX = event.x; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (abs(event.x - downX) <= slop) return true
                    dragging = true
                    lastX = event.x
                }
                val max = ((childCount - 1) * width).coerceAtLeast(0)
                // Past the first or last page, the pages follow the finger a little, to show there is no more.
                val dx = (lastX - event.x).let { if (scrollX + it < 0 || scrollX + it > max) it / 3 else it }
                scrollTo((scrollX + dx).roundToInt(), 0)
                lastX = event.x
            }
            MotionEvent.ACTION_UP -> finishDrag(cancelled = false)
            MotionEvent.ACTION_CANCEL -> finishDrag(cancelled = true)
        }
        return true
    }

    private fun finishDrag(cancelled: Boolean) {
        if (!dragging) { stopTracking(); return }
        dragging = false
        val speed = velocity?.run { computeCurrentVelocity(1000); xVelocity } ?: 0f
        stopTracking()
        val moved = scrollX - page * width
        val wanted = when {
            cancelled -> page
            speed < -flingSpeed || moved > width / 3 -> page + 1
            speed > flingSpeed || moved < -width / 3 -> page - 1
            else -> page
        }.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        if (wanted != page) {
            onPage(wanted)
            // The app answers with showPage; until then, snap to where the finger left it heading.
            page = wanted
        }
        snapTo(page * width)
    }

    private fun track(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { velocity?.recycle(); velocity = VelocityTracker.obtain() }
        velocity?.addMovement(event)
    }

    private fun stopTracking() {
        velocity?.recycle()
        velocity = null
    }
}
