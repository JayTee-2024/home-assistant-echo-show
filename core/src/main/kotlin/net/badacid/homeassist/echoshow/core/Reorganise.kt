/*
 * The rules of the "Reorganise layout" mode: where a card being moved goes a cell at a time, and which cell
 * is under the finger when it is dragged. Whether the card may be dropped there is Layout.kt's moveCard.
 */
package net.badacid.homeassist.echoshow.core

import kotlin.math.floor

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Where something is drawn, in screen units. */
data class Box(val left: Float, val top: Float, val width: Float, val height: Float)

/** A grid cell, counted from 0. */
data class Cell(val column: Int, val row: Int)

/**
 * The place one step takes a card being moved.
 *   - Up and Down stop at the top and bottom of the grid.
 *   - Right past the edge goes to the left of the next page, and Left past the edge to the right of the page
 *     before.
 *   - The page after the last one may be used (it becomes a new page), but not any further.
 * `pageCount` is the number of pages the layout has now.
 */
fun stepPlace(place: GridPlace, size: CardSize, direction: Direction, pageCount: Int): GridPlace {
    val cells = cellsOf(size)
    val lastColumn = GRID_COLUMNS - cells.columns
    val lastRow = GRID_ROWS - cells.rows
    val lastPage = pageCount

    return when (direction) {
        Direction.UP -> place.copy(row = maxOf(0, place.row - 1))
        Direction.DOWN -> place.copy(row = minOf(lastRow, place.row + 1))
        Direction.LEFT -> when {
            place.column > 0 -> place.copy(column = place.column - 1)
            place.page == 0 -> place
            else -> place.copy(page = place.page - 1, column = lastColumn)
        }
        Direction.RIGHT -> when {
            place.column < lastColumn -> place.copy(column = place.column + 1)
            place.page >= lastPage -> place
            else -> place.copy(page = place.page + 1, column = 0)
        }
    }
}

/**
 * The grid cell under a point, for dragging with a finger. `grid` is where the grid is drawn and `gap` the
 * space between cells, both in the same units as the point. A point off the grid gives the nearest cell on
 * its edge.
 */
fun cellAt(x: Float, y: Float, grid: Box, gap: Float): Cell {
    val columnWidth = (grid.width + gap) / GRID_COLUMNS
    val rowHeight = (grid.height + gap) / GRID_ROWS
    val column = floor((x - grid.left) / columnWidth).toInt()
    val row = floor((y - grid.top) / rowHeight).toInt()
    return Cell(column.coerceIn(0, GRID_COLUMNS - 1), row.coerceIn(0, GRID_ROWS - 1))
}
