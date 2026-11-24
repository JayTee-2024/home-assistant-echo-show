package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Port of the LG app's src/core/layout.test.ts. The LG grid is 6 by 3; the Echo's is 4 by 3, so tests whose
 * numbers depend on the width were worked out again for 4 columns (marked "4 by 3").
 */
class LayoutTest {
    /** Adds cards in order: "a:small" is a small card for device "a"; a bare "a" is small. */
    private fun layoutOf(vararg cards: String): Layout {
        var layout = EMPTY_LAYOUT
        for (card in cards) {
            val (deviceId, size) = (card.split(":") + "small").let { it[0] to it[1] }
            layout = addCard(layout, deviceId, CardSize.fromSavedName(size)!!)
        }
        return layout
    }

    private fun placeOf(layout: Layout?, deviceId: String): String {
        if (layout == null) return "refused"
        val card = layout.cards.firstOrNull { it.deviceId == deviceId } ?: return "not placed"
        return "page ${card.page}, column ${card.column}, row ${card.row}"
    }

    @Test
    fun `the grid is 4 columns by 3 rows`() {
        assertEquals(4, GRID_COLUMNS)
        assertEquals(3, GRID_ROWS)
    }

    @Test
    fun `cellsOf gives the cells each card size covers`() {
        assertEquals(Cells(1, 1), cellsOf(CardSize.SMALL))
        assertEquals(Cells(2, 1), cellsOf(CardSize.WIDE))
        assertEquals(Cells(2, 2), cellsOf(CardSize.LARGE))
    }

    @Nested
    inner class Pages {
        @Test
        fun `has one page when empty`() {
            assertEquals(1, pageCount(EMPTY_LAYOUT))
            assertEquals(emptyList<CardPlace>(), cardsOnPage(EMPTY_LAYOUT, 0))
        }

        @Test
        fun `counts pages up to the last one with a card`() {
            val layout = Layout(listOf(CardPlace("a", 2, 0, 0, CardSize.SMALL)))
            assertEquals(3, pageCount(layout))
            assertEquals(listOf("a"), cardsOnPage(layout, 2).map { it.deviceId })
            assertEquals(emptyList<CardPlace>(), cardsOnPage(layout, 0))
        }
    }

    @Nested
    inner class AddCard {
        @Test
        fun `puts the first card at the top left of the first page`() {
            assertEquals("page 0, column 0, row 0", placeOf(layoutOf("a:small"), "a"))
        }

        @Test
        fun `fills a row from left to right, then the next row`() {
            // 4 by 3: the row holds four.
            val layout = layoutOf("a", "b", "c", "d", "e")
            assertEquals("page 0, column 3, row 0", placeOf(layout, "d"))
            assertEquals("page 0, column 0, row 1", placeOf(layout, "e"))
        }

        @Test
        fun `a fifth small card on a full row goes to the next free row`() {
            val layout = layoutOf("heating:wide", "a", "b", "c")
            assertEquals("page 0, column 3, row 0", placeOf(layout, "b"))
            assertEquals("page 0, column 0, row 1", placeOf(layout, "c"))
        }

        @Test
        fun `puts a small card beside a wide one`() {
            assertEquals("page 0, column 2, row 0", placeOf(layoutOf("heating:wide", "lamp:small"), "lamp"))
        }

        @Test
        fun `keeps a large card clear of the cells another large card covers`() {
            // 4 by 3: the doorbell covers columns 0-1 of rows 0 and 1, so each of those rows has two free cells.
            val layout = layoutOf("doorbell:large", "a", "b", "c", "d", "e")
            assertEquals("page 0, column 3, row 0", placeOf(layout, "b"))
            assertEquals("page 0, column 2, row 1", placeOf(layout, "c"))
            assertEquals("page 0, column 0, row 2", placeOf(layout, "e"))
        }

        @Test
        fun `two large cards fill the top two rows of a page`() {
            val layout = layoutOf("door1:large", "door2:large", "door3:large")
            assertEquals("page 0, column 0, row 0", placeOf(layout, "door1"))
            assertEquals("page 0, column 2, row 0", placeOf(layout, "door2"))
            assertEquals("page 1, column 0, row 0", placeOf(layout, "door3"))
        }

        @Test
        fun `moves a card that does not fit the remaining cells to the next place it fits`() {
            // 4 by 3: three small cards leave one free cell in row 0, too narrow for a wide card.
            val layout = layoutOf("a", "b", "c", "heating:wide", "f")
            assertEquals("page 0, column 0, row 1", placeOf(layout, "heating"))
            assertEquals("page 0, column 3, row 0", placeOf(layout, "f"))
        }

        @Test
        fun `does not let a large card hang off the bottom row`() {
            // 4 by 3: eight small cards fill rows 0 and 1. Row 2 is free but a 2×2 card needs two rows.
            val layout = layoutOf(*"abcdefgh".map { it.toString() }.toTypedArray(), "doorbell:large")
            assertEquals("page 1, column 0, row 0", placeOf(layout, "doorbell"))
        }

        @Test
        fun `starts a new page when every page is full`() {
            // 4 by 3: twelve small cards fill a page.
            val layout = layoutOf(*"abcdefghijkl".map { it.toString() }.toTypedArray(), "extra")
            assertEquals("page 1, column 0, row 0", placeOf(layout, "extra"))
            assertEquals(2, pageCount(layout))
        }

        @Test
        fun `changes nothing when the device already has a card`() {
            val layout = layoutOf("a", "b")
            assertEquals(layout, addCard(layout, "a", CardSize.SMALL))
        }
    }

    @Nested
    inner class RemoveCard {
        @Test
        fun `leaves the gap, and the next card added fills it`() {
            val removed = removeCard(layoutOf("a", "b", "c"), "b")
            assertFalse(hasCard(removed, "b"))
            assertEquals("page 0, column 2, row 0", placeOf(removed, "c"))
            assertEquals("page 0, column 1, row 0", placeOf(addCard(removed, "d", CardSize.SMALL), "d"))
        }

        @Test
        fun `changes nothing for a device with no card`() {
            val layout = layoutOf("a")
            assertEquals(layout, removeCard(layout, "zzz"))
        }
    }

    @Nested
    inner class WithCurrentSizes {
        @Test
        fun `moves a card whose device changed size to a place where the new size fits`() {
            val layout = layoutOf("a", "b", "c")
            val sizes = mapOf("a" to CardSize.SMALL, "b" to CardSize.LARGE, "c" to CardSize.SMALL)

            val updated = withCurrentSizes(layout) { sizes[it] }

            assertEquals(CardSize.LARGE, updated.cards.first { it.deviceId == "b" }.size)
            assertEquals("page 0, column 0, row 0", placeOf(updated, "a"))
            assertEquals("page 0, column 2, row 0", placeOf(updated, "c"))
            // 4 by 3: no 2×2 space is left in rows 0-1 beside a and c, so b goes below them.
            assertEquals("page 0, column 0, row 1", placeOf(updated, "b"))
        }

        @Test
        fun `keeps a card as it is when its device is gone`() {
            val layout = layoutOf("a", "gone:wide")
            assertEquals(layout, withCurrentSizes(layout) { if (it == "a") CardSize.SMALL else null })
        }

        @Test
        fun `gives back the same layout when nothing changed size`() {
            val layout = layoutOf("a", "b:wide")
            val sizes = mapOf("a" to CardSize.SMALL, "b" to CardSize.WIDE)
            assertSame(layout, withCurrentSizes(layout) { sizes[it] })
        }
    }

    @Nested
    inner class MoveCard {
        @Test
        fun `moves a card to free cells`() {
            assertEquals("page 0, column 3, row 2", placeOf(moveCard(layoutOf("a", "b"), "a", GridPlace(0, 3, 2)), "a"))
        }

        @Test
        fun `counts the card's own cells as free, so a wide card can shift by one`() {
            assertEquals("page 0, column 1, row 0", placeOf(moveCard(layoutOf("heating:wide"), "heating", GridPlace(0, 1, 0)), "heating"))
        }

        @Test
        fun `moves a card to another page, including one that does not exist yet`() {
            val moved = moveCard(layoutOf("a", "b"), "b", GridPlace(1, 0, 0))
            assertEquals("page 1, column 0, row 0", placeOf(moved, "b"))
            assertEquals(2, moved?.let { pageCount(it) })
        }

        @Test
        fun `swaps two cards of the same size when one is dropped on the other`() {
            val swapped = moveCard(layoutOf("a", "b", "c"), "a", GridPlace(0, 2, 0))
            assertEquals("page 0, column 2, row 0", placeOf(swapped, "a"))
            assertEquals("page 0, column 0, row 0", placeOf(swapped, "c"))
            assertEquals("page 0, column 1, row 0", placeOf(swapped, "b"))
        }

        @Test
        fun `swaps two wide cards placed exactly on each other`() {
            val swapped = moveCard(layoutOf("h1:wide", "h2:wide"), "h1", GridPlace(0, 2, 0))
            assertEquals("page 0, column 2, row 0", placeOf(swapped, "h1"))
            assertEquals("page 0, column 0, row 0", placeOf(swapped, "h2"))
        }

        @Test
        fun `refuses a place that overlaps a card of another size`() {
            val layout = layoutOf("heating:wide", "a")
            assertNull(moveCard(layout, "a", GridPlace(0, 1, 0)))
            assertNull(moveCard(layout, "heating", GridPlace(0, 1, 0)))
        }

        @Test
        fun `refuses a place that runs off the grid`() {
            val layout = layoutOf("doorbell:large")
            // 4 by 3: a 2-wide card starting in column 3 runs off the right edge.
            assertNull(moveCard(layout, "doorbell", GridPlace(0, 3, 0)))
            assertNull(moveCard(layout, "doorbell", GridPlace(0, 0, 2)))
            assertNull(moveCard(layout, "doorbell", GridPlace(-1, 0, 0)))
            assertNull(moveCard(layout, "doorbell", GridPlace(0, -1, 0)))
            assertNull(moveCard(layout, "doorbell", GridPlace(0, 0, -1)))
        }

        @Test
        fun `refuses to move a card that is not there`() {
            assertNull(moveCard(layoutOf("a"), "zzz", GridPlace(0, 3, 0)))
        }
    }

    @Nested
    inner class RemoveEmptyPages {
        @Test
        fun `drops empty pages and numbers the later ones down, keeping every place on its page`() {
            val layout = Layout(
                listOf(
                    CardPlace("a", 0, 3, 1, CardSize.SMALL),
                    CardPlace("b", 2, 2, 2, CardSize.SMALL),
                    CardPlace("c", 4, 0, 0, CardSize.WIDE),
                ),
            )
            assertEquals(
                listOf(
                    CardPlace("a", 0, 3, 1, CardSize.SMALL),
                    CardPlace("b", 1, 2, 2, CardSize.SMALL),
                    CardPlace("c", 2, 0, 0, CardSize.WIDE),
                ),
                removeEmptyPages(layout).cards,
            )
        }

        @Test
        fun `gives back the same layout when no page is empty`() {
            val layout = layoutOf("a", "b")
            assertSame(layout, removeEmptyPages(layout))
        }
    }

    @Test
    fun `isOnGrid tells a place on the 4 by 3 grid from one off it`() {
        assertEquals(true, isOnGrid(CardPlace("a", 0, 3, 2, CardSize.SMALL)))
        assertEquals(false, isOnGrid(CardPlace("a", 0, 4, 0, CardSize.SMALL)))
        assertEquals(false, isOnGrid(CardPlace("a", 0, 3, 0, CardSize.WIDE)))
        assertEquals(false, isOnGrid(CardPlace("a", 0, 0, 2, CardSize.LARGE)))
        assertEquals(false, isOnGrid(CardPlace("a", -1, 0, 0, CardSize.SMALL)))
    }
}
