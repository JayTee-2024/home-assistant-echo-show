package net.badacid.homeassist.echoshow.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Port of the LG app's src/core/reorganise.test.ts, worked out again for the Echo's 4 by 3 grid. On the Echo
 * the card is dragged with a finger; stepPlace stays for moving a card a cell at a time.
 */
class ReorganiseTest {
    @Nested
    inner class StepPlace {
        @Test
        fun `moves one cell in each direction`() {
            val at = GridPlace(0, 2, 1)
            assertEquals(GridPlace(0, 1, 1), stepPlace(at, CardSize.SMALL, Direction.LEFT, 1))
            assertEquals(GridPlace(0, 3, 1), stepPlace(at, CardSize.SMALL, Direction.RIGHT, 1))
            assertEquals(GridPlace(0, 2, 0), stepPlace(at, CardSize.SMALL, Direction.UP, 1))
            assertEquals(GridPlace(0, 2, 2), stepPlace(at, CardSize.SMALL, Direction.DOWN, 1))
        }

        @Test
        fun `stops at the top and bottom of the grid, allowing for the card's height`() {
            assertEquals(GridPlace(0, 0, 0), stepPlace(GridPlace(0, 0, 0), CardSize.SMALL, Direction.UP, 1))
            assertEquals(GridPlace(0, 0, 1), stepPlace(GridPlace(0, 0, 1), CardSize.LARGE, Direction.DOWN, 1))
        }

        @Test
        fun `carries the card past the right edge to the left of the next page`() {
            // 4 by 3: a wide card's last column is 2.
            assertEquals(GridPlace(1, 0, 1), stepPlace(GridPlace(0, 2, 1), CardSize.WIDE, Direction.RIGHT, 2))
        }

        @Test
        fun `carries the card past the left edge to the right of the page before, allowing for its width`() {
            assertEquals(GridPlace(0, 2, 0), stepPlace(GridPlace(1, 0, 0), CardSize.WIDE, Direction.LEFT, 2))
        }

        @Test
        fun `stops at the left edge of the first page`() {
            assertEquals(GridPlace(0, 0, 0), stepPlace(GridPlace(0, 0, 0), CardSize.SMALL, Direction.LEFT, 1))
        }

        @Test
        fun `goes past the last page onto a new, empty one, but no further`() {
            assertEquals(GridPlace(2, 0, 0), stepPlace(GridPlace(1, 3, 0), CardSize.SMALL, Direction.RIGHT, 2))
            assertEquals(GridPlace(2, 3, 0), stepPlace(GridPlace(2, 3, 0), CardSize.SMALL, Direction.RIGHT, 2))
        }
    }

    @Nested
    inner class CellAt {
        // A grid 600 wide and 300 tall at (100, 50), with a 30-unit gap. 4 by 3: cells 127.5 wide and 80 tall.
        private val grid = Box(left = 100f, top = 50f, width = 600f, height = 300f)

        @Test
        fun `gives the cell under the pointer`() {
            assertEquals(Cell(0, 0), cellAt(100f, 50f, grid, 30f))
            assertEquals(Cell(3, 2), cellAt(699f, 349f, grid, 30f))
            assertEquals(Cell(1, 1), cellAt(320f, 200f, grid, 30f))
        }

        @Test
        fun `keeps a point off the grid on its nearest edge cell`() {
            assertEquals(Cell(0, 0), cellAt(0f, 0f, grid, 30f))
            assertEquals(Cell(3, 2), cellAt(2000f, 2000f, grid, 30f))
        }
    }
}
