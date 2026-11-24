package net.badacid.homeassist.echoshow.screens

import android.content.Context
import android.view.ViewGroup

/** Lays its children out left to right, wrapping onto a new line when the width runs out. */
class FlowLayout(context: Context, private val gapPx: Int) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0
                y += lineHeight + gapPx
                lineHeight = 0
            }
            x += child.measuredWidth + gapPx
            widest = maxOf(widest, x - gapPx)
            lineHeight = maxOf(lineHeight, child.measuredHeight)
        }
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) widest else MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, y + lineHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        // Children on one line are centred vertically on it.
        val lines = mutableListOf<MutableList<android.view.View>>(mutableListOf())
        val lineHeights = mutableListOf(0)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                lines += mutableListOf<android.view.View>()
                lineHeights += 0
                x = 0
            }
            lines.last() += child
            lineHeights[lineHeights.size - 1] = maxOf(lineHeights.last(), child.measuredHeight)
            x += child.measuredWidth + gapPx
        }
        y = paddingTop
        lines.forEachIndexed { index, line ->
            lineHeight = lineHeights[index]
            x = paddingLeft
            for (child in line) {
                val top = y + (lineHeight - child.measuredHeight) / 2
                child.layout(x, top, x + child.measuredWidth, top + child.measuredHeight)
                x += child.measuredWidth + gapPx
            }
            y += lineHeight + gapPx
        }
    }
}
