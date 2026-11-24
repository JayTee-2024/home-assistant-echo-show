/*
 * Where the cards go: a grid of 4 columns by 3 rows (the LG app's is 6 by 3), repeated over as many pages as
 * needed.
 *
 * A layout is just a list of cards, each with its page, its top-left cell and its size. Pages are not
 * stored: there are as many as the highest page number in use.
 *
 * Every function here returns a new layout and leaves the one it was given untouched.
 */
package net.badacid.homeassist.echoshow.core

/** Four columns: on an 8-inch screen, six would make cards about 3 cm wide, too small for fingers and names. */
const val GRID_COLUMNS = 4
const val GRID_ROWS = 3

/** One card's place. `column` and `row` are its top-left cell, counted from 0. */
data class CardPlace(
    val deviceId: String,
    val page: Int,
    val column: Int,
    val row: Int,
    val size: CardSize,
)

data class Layout(val cards: List<CardPlace>)

val EMPTY_LAYOUT = Layout(emptyList())

/** How many grid cells a card covers across and down. */
data class Cells(val columns: Int, val rows: Int)

fun cellsOf(size: CardSize): Cells = when (size) {
    CardSize.LARGE -> Cells(2, 2)
    CardSize.WIDE -> Cells(2, 1)
    CardSize.SMALL -> Cells(1, 1)
}

/** The number of pages: up to the last page that has a card, and never fewer than one. */
fun pageCount(layout: Layout): Int = (layout.cards.maxOfOrNull { it.page } ?: 0).coerceAtLeast(0) + 1

fun cardsOnPage(layout: Layout, page: Int): List<CardPlace> = layout.cards.filter { it.page == page }

fun hasCard(layout: Layout, deviceId: String): Boolean = layout.cards.any { it.deviceId == deviceId }

/**
 * Adds a card in the first free place that fits it: pages in order, and on each page row by row, left to
 * right. When no page has room, the card starts a new page. A device that already has a card is left where
 * it is.
 */
fun addCard(layout: Layout, deviceId: String, size: CardSize): Layout {
    if (hasCard(layout, deviceId)) return layout

    val pages = pageCount(layout)
    for (page in 0 until pages) {
        for (row in 0 until GRID_ROWS) {
            for (column in 0 until GRID_COLUMNS) {
                val place = CardPlace(deviceId, page, column, row, size)
                if (fits(layout, place)) return Layout(layout.cards + place)
            }
        }
    }
    return Layout(layout.cards + CardPlace(deviceId, pages, 0, 0, size))
}

/** Removes a device's card. The other cards stay where they are, leaving a gap. */
fun removeCard(layout: Layout, deviceId: String): Layout {
    if (!hasCard(layout, deviceId)) return layout
    return Layout(layout.cards.filter { it.deviceId != deviceId })
}

/**
 * Brings a saved layout up to date with the devices as they are now.
 *
 * A device's card size can change between runs (say its camera was removed in Home Assistant). Such a card
 * is taken out and added again at its new size. A card whose device is unknown (`sizeOf` gives null) is
 * kept exactly as it is, so the home screen can say what happened to it.
 *
 * Gives back the same layout object when nothing needed changing.
 */
fun withCurrentSizes(layout: Layout, sizeOf: (deviceId: String) -> CardSize?): Layout {
    var updated = layout
    for (card in layout.cards) {
        val size = sizeOf(card.deviceId)
        if (size == null || size == card.size) continue
        updated = addCard(removeCard(updated, card.deviceId), card.deviceId, size)
    }
    return updated
}

/** True when the card lies wholly on a page of the grid. */
fun isOnGrid(place: CardPlace): Boolean {
    val size = cellsOf(place.size)
    return place.page >= 0 && place.column >= 0 && place.row >= 0 &&
        place.column + size.columns <= GRID_COLUMNS &&
        place.row + size.rows <= GRID_ROWS
}

/** True when `place` lies wholly on the grid and covers no cell another card covers. */
private fun fits(layout: Layout, place: CardPlace): Boolean =
    isOnGrid(place) && layout.cards.none { it.page == place.page && overlaps(it, place) }

/** True when two cards on the same page share at least one cell. */
fun overlaps(a: CardPlace, b: CardPlace): Boolean {
    val sizeA = cellsOf(a.size)
    val sizeB = cellsOf(b.size)
    val apartSideways = a.column + sizeA.columns <= b.column || b.column + sizeB.columns <= a.column
    val apartUpAndDown = a.row + sizeA.rows <= b.row || b.row + sizeB.rows <= a.row
    return !apartSideways && !apartUpAndDown
}

/** A place on the grid: a page, and the top-left cell a card would cover. */
data class GridPlace(val page: Int, val column: Int, val row: Int)

/**
 * Moves a card, for the "Reorganise layout" mode. Gives the new layout, or null when the move is refused
 * and nothing changes.
 *
 *   - The card moves when its new cells are on the grid and free. Its own old cells count as free, so a
 *     wide card can shift sideways by one.
 *   - Dropped exactly on another card of the same size, the two swap places.
 *   - Anything else (part of another card in the way, or off the grid) is refused.
 *
 * The page may be one past the last, which starts a new page.
 */
fun moveCard(layout: Layout, deviceId: String, to: GridPlace): Layout? {
    val card = layout.cards.firstOrNull { it.deviceId == deviceId } ?: return null
    if (to.page < 0) return null

    val moved = CardPlace(deviceId, to.page, to.column, to.row, card.size)
    val others = removeCard(layout, deviceId)
    if (fits(others, moved)) {
        return Layout(layout.cards.map { if (it.deviceId == deviceId) moved else it })
    }

    // A swap: a card of the same size sits exactly at the place.
    val atPlace = others.cards.firstOrNull {
        it.page == to.page && it.column == to.column && it.row == to.row && it.size == card.size
    } ?: return null

    val swappedOut = atPlace.copy(page = card.page, column = card.column, row = card.row)
    return Layout(
        layout.cards.map {
            when (it.deviceId) {
                deviceId -> moved
                atPlace.deviceId -> swappedOut
                else -> it
            }
        },
    )
}

/**
 * Drops pages that have no cards and numbers the later pages down to close the gap. Every card keeps its
 * place on its own page. Gives back the same layout when no page is empty.
 */
fun removeEmptyPages(layout: Layout): Layout {
    val used = layout.cards.map { it.page }.distinct().sorted()
    if (used.withIndex().all { (index, page) -> page == index }) return layout
    return Layout(layout.cards.map { it.copy(page = used.indexOf(it.page)) })
}
