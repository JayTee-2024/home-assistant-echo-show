package net.badacid.homeassist.echoshow.core

import net.badacid.homeassist.echoshow.core.PathCommand.Close
import net.badacid.homeassist.echoshow.core.PathCommand.CubicTo
import net.badacid.homeassist.echoshow.core.PathCommand.LineTo
import net.badacid.homeassist.echoshow.core.PathCommand.MoveTo
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SvgPathTest {
    @Test
    fun `reads absolute and relative lines`() {
        assertEquals(listOf(MoveTo(1f, 2f), LineTo(4f, 6f), LineTo(5f, 8f)), parseSvgPath("M1 2L4 6l1 2"))
    }

    @Test
    fun `extra pairs after M are lines, as the close icon uses`() {
        assertEquals(listOf(MoveTo(18f, 6f), LineTo(6f, 18f), MoveTo(6f, 6f), LineTo(18f, 18f)), parseSvgPath("M18 6 6 18M6 6l12 12"))
    }

    @Test
    fun `H and V keep the other coordinate`() {
        assertEquals(listOf(MoveTo(3f, 3f), LineTo(21f, 3f), LineTo(21f, 9f), LineTo(19f, 9f), LineTo(19f, 7f)), parseSvgPath("M3 3H21V9h-2v-2"))
    }

    @Test
    fun `Z closes and returns to the start of the shape`() {
        assertEquals(listOf(MoveTo(5f, 3f), LineTo(19f, 12f), Close, MoveTo(6f, 4f)), parseSvgPath("M5 3l14 9zm1 1"))
    }

    @Test
    fun `S reflects the previous control point`() {
        val commands = parseSvgPath("M0 0C1 1 2 1 3 0S5 -1 6 0")
        assertEquals(CubicTo(4f, -1f, 5f, -1f, 6f, 0f), commands[2])
    }

    @Test
    fun `Q becomes a cubic with the same ends`() {
        val cubic = parseSvgPath("M0 0Q3 3 6 0")[1] as CubicTo
        assertEquals(6f, cubic.x)
        assertEquals(0f, cubic.y)
        assertEquals(2f, cubic.x1, 0.001f)
        assertEquals(2f, cubic.y1, 0.001f)
    }

    @Test
    fun `turns an arc into curves that end at the arc's end point`() {
        val commands = parseSvgPath("M12 22a10 10 0 1 0 0-20")
        assertTrue(commands.drop(1).all { it is CubicTo })
        val last = commands.last() as CubicTo
        assertEquals(12f, last.x, 0.001f)
        assertEquals(2f, last.y, 0.001f)
        // A half circle of radius 10 reaches 10 units to one side of the line between its ends.
        val furthest = commands.drop(1).maxOf { abs((it as CubicTo).x - 12f) }
        assertEquals(10f, furthest, 0.2f)
    }

    @Test
    fun `reads numbers written without separators, like 0-4 and 1 point 5 point 5`() {
        assertEquals(listOf(MoveTo(0f, -4f), LineTo(1.5f, 0.5f)), parseSvgPath("M0-4L1.5.5"))
        assertEquals(listOf(MoveTo(1e2f, 2f)), parseSvgPath("M1e2,2"))
    }

    @Test
    fun `stops quietly at nonsense`() {
        assertEquals(listOf(MoveTo(1f, 1f)), parseSvgPath("M1 1 L x y"))
        assertEquals(emptyList<PathCommand>(), parseSvgPath("hello"))
        assertEquals(emptyList<PathCommand>(), parseSvgPath(""))
    }

    @Test
    fun `every icon of every device type parses to at least two commands`() {
        for (type in DEVICE_LOGIC) assertTrue(parseSvgPath(type.icon).size >= 2, type.id)
    }
}
