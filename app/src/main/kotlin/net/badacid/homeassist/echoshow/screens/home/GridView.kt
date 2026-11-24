package net.badacid.homeassist.echoshow.screens.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import net.badacid.homeassist.echoshow.core.CardPlace
import net.badacid.homeassist.echoshow.core.GRID_COLUMNS
import net.badacid.homeassist.echoshow.core.GRID_ROWS
import net.badacid.homeassist.echoshow.core.cellsOf
import net.badacid.homeassist.echoshow.screens.Theme

/** One page of the home screen: cards placed on the 4 × 3 grid by their CardPlace, with a gap between cells. */
class GridView(context: Context) : ViewGroup(context) {
    private val gap = Theme.dp(context, 14f)
    private val places = mutableMapOf<View, CardPlace>()

    fun setCards(cards: List<Pair<CardPlace, View>>) {
        removeAllViews()
        places.clear()
        for ((place, view) in cards) {
            places[view] = place
            addView(view)
        }
    }

    private val cellWidth get() = (width - paddingLeft - paddingRight - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
    private val cellHeight get() = (height - paddingTop - paddingBottom - gap * (GRID_ROWS - 1)) / GRID_ROWS

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val cellW = (width - paddingLeft - paddingRight - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val cellH = (height - paddingTop - paddingBottom - gap * (GRID_ROWS - 1)) / GRID_ROWS
        for ((view, place) in places) {
            val cells = cellsOf(place.size)
            view.measure(
                MeasureSpec.makeMeasureSpec(cellW * cells.columns + gap * (cells.columns - 1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellH * cells.rows + gap * (cells.rows - 1), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for ((view, place) in places) {
            val left = paddingLeft + place.column * (cellWidth + gap)
            val top = paddingTop + place.row * (cellHeight + gap)
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
    }
}
