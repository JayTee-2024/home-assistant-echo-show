/*
 * Reads SVG path data (the `d` of an SVG <path>) into a few plain drawing commands, so the app can draw the
 * LG app's line icons with Android's Path. Every curve becomes a cubic: quadratics are raised, and arcs are
 * split into quarter-turn cubic pieces (SVG 1.1, appendix F.6, for finding an arc's centre).
 *
 * Bad input never throws: reading stops at the first thing that cannot be read, and the commands read so
 * far are returned.
 */
package net.badacid.homeassist.echoshow.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCommand
    data object Close : PathCommand
}

fun parseSvgPath(data: String): List<PathCommand> = SvgPathReader(data).read()

private class SvgPathReader(private val text: String) {
    private var at = 0
    private val out = mutableListOf<PathCommand>()
    private var x = 0.0
    private var y = 0.0
    private var startX = 0.0
    private var startY = 0.0

    /** The last cubic's second control point, for S; the last quadratic's control point, for T. */
    private var lastCubicControl: Pair<Double, Double>? = null
    private var lastQuadControl: Pair<Double, Double>? = null

    fun read(): List<PathCommand> {
        var command: Char? = null
        while (true) {
            skipSeparators()
            if (at >= text.length) break
            val c = text[at]
            if (c.isLetter() && c != 'e' && c != 'E') {
                command = c
                at++
                if (c == 'Z' || c == 'z') {
                    close()
                    continue
                }
            } else if (command == null) {
                break
            }
            val current = command ?: break
            if (!readSegment(current)) break
            // After M, further pairs are lines (m: relative lines).
            if (current == 'M') command = 'L'
            if (current == 'm') command = 'l'
        }
        return out
    }

    private fun close() {
        out += PathCommand.Close
        x = startX
        y = startY
        lastCubicControl = null
        lastQuadControl = null
    }

    /** Reads one segment's numbers for `command` and adds its commands. False when the numbers are not there. */
    private fun readSegment(command: Char): Boolean {
        val relative = command.isLowerCase()
        val ox = if (relative) x else 0.0
        val oy = if (relative) y else 0.0
        when (command.uppercaseChar()) {
            'M' -> {
                val (px, py) = numbers(2) ?: return false
                x = ox + px; y = oy + py
                startX = x; startY = y
                out += PathCommand.MoveTo(x.toFloat(), y.toFloat())
                clearControls()
            }
            'L' -> {
                val (px, py) = numbers(2) ?: return false
                lineTo(ox + px, oy + py)
            }
            'H' -> {
                val (px) = numbers(1) ?: return false
                lineTo(ox + px, y)
            }
            'V' -> {
                val (py) = numbers(1) ?: return false
                lineTo(x, oy + py)
            }
            'C' -> {
                val n = numbers(6) ?: return false
                cubicTo(ox + n[0], oy + n[1], ox + n[2], oy + n[3], ox + n[4], oy + n[5])
            }
            'S' -> {
                val n = numbers(4) ?: return false
                val (cx, cy) = lastCubicControl?.let { (lx, ly) -> 2 * x - lx to 2 * y - ly } ?: (x to y)
                cubicTo(cx, cy, ox + n[0], oy + n[1], ox + n[2], oy + n[3])
            }
            'Q' -> {
                val n = numbers(4) ?: return false
                quadTo(ox + n[0], oy + n[1], ox + n[2], oy + n[3])
            }
            'T' -> {
                val n = numbers(2) ?: return false
                val (cx, cy) = lastQuadControl?.let { (lx, ly) -> 2 * x - lx to 2 * y - ly } ?: (x to y)
                quadTo(cx, cy, ox + n[0], oy + n[1])
            }
            'A' -> {
                val rx = number() ?: return false
                val ry = number() ?: return false
                val rotation = number() ?: return false
                val largeArc = flag() ?: return false
                val sweep = flag() ?: return false
                val ex = number() ?: return false
                val ey = number() ?: return false
                arcTo(rx, ry, rotation, largeArc, sweep, ox + ex, oy + ey)
            }
            else -> return false
        }
        return true
    }

    private fun clearControls() {
        lastCubicControl = null
        lastQuadControl = null
    }

    private fun lineTo(nx: Double, ny: Double) {
        x = nx; y = ny
        out += PathCommand.LineTo(x.toFloat(), y.toFloat())
        clearControls()
    }

    private fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, nx: Double, ny: Double) {
        out += PathCommand.CubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), nx.toFloat(), ny.toFloat())
        x = nx; y = ny
        lastCubicControl = x2 to y2
        lastQuadControl = null
    }

    private fun quadTo(qx: Double, qy: Double, nx: Double, ny: Double) {
        // A quadratic raised to a cubic: the control points are two thirds of the way to the quadratic's one.
        val x1 = x + 2.0 / 3 * (qx - x)
        val y1 = y + 2.0 / 3 * (qy - y)
        val x2 = nx + 2.0 / 3 * (qx - nx)
        val y2 = ny + 2.0 / 3 * (qy - ny)
        cubicTo(x1, y1, x2, y2, nx, ny)
        lastCubicControl = null
        lastQuadControl = qx to qy
    }

    private fun arcTo(rxIn: Double, ryIn: Double, rotationDegrees: Double, largeArc: Boolean, sweep: Boolean, ex: Double, ey: Double) {
        if (x == ex && y == ey) return
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx == 0.0 || ry == 0.0) return lineTo(ex, ey)

        val phi = rotationDegrees * PI / 180
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        // Step 1: the start point in the arc's own (unrotated) frame, centred between the ends.
        val dx = (x - ex) / 2
        val dy = (y - ey) / 2
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        // Radii too small to reach are scaled up just enough.
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        // Step 2: the centre in that frame.
        val numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var factor = sqrt(maxOf(0.0, numerator / denominator))
        if (largeArc == sweep) factor = -factor
        val cxp = factor * rx * y1p / ry
        val cyp = -factor * ry * x1p / rx
        // Step 3: the centre in the page's frame.
        val cx = cosPhi * cxp - sinPhi * cyp + (x + ex) / 2
        val cy = sinPhi * cxp + cosPhi * cyp + (y + ey) / 2
        // Step 4: the start angle and the angle swept.
        val start = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var swept = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && swept > 0) swept -= 2 * PI
        if (sweep && swept < 0) swept += 2 * PI

        // Pieces of at most a quarter turn, each a cubic.
        val pieces = ceil(abs(swept) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = swept / pieces
        val k = 4.0 / 3 * tan(step / 4)
        var theta = start
        repeat(pieces) { index ->
            val t2 = theta + step
            val (sx, sy) = pointOnEllipse(cx, cy, rx, ry, cosPhi, sinPhi, theta)
            val (fx, fy) = if (index == pieces - 1) ex to ey else pointOnEllipse(cx, cy, rx, ry, cosPhi, sinPhi, t2)
            val (d1x, d1y) = derivative(rx, ry, cosPhi, sinPhi, theta)
            val (d2x, d2y) = derivative(rx, ry, cosPhi, sinPhi, t2)
            cubicTo(sx + k * d1x, sy + k * d1y, fx - k * d2x, fy - k * d2y, fx, fy)
            theta = t2
        }
        lastCubicControl = null
    }

    private fun pointOnEllipse(cx: Double, cy: Double, rx: Double, ry: Double, cosPhi: Double, sinPhi: Double, t: Double) =
        (cx + rx * cos(t) * cosPhi - ry * sin(t) * sinPhi) to (cy + rx * cos(t) * sinPhi + ry * sin(t) * cosPhi)

    private fun derivative(rx: Double, ry: Double, cosPhi: Double, sinPhi: Double, t: Double) =
        (-rx * sin(t) * cosPhi - ry * cos(t) * sinPhi) to (-rx * sin(t) * sinPhi + ry * cos(t) * cosPhi)

    private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val length = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var a = kotlin.math.acos((dot / length).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }

    // ---- Reading numbers ----

    private fun numbers(count: Int): List<Double>? {
        val start = at
        val read = List(count) { number() ?: run { at = start; return null } }
        return read
    }

    private fun skipSeparators() {
        while (at < text.length && (text[at].isWhitespace() || text[at] == ',')) at++
    }

    /** One number: optional sign, digits with at most one point, optional exponent. "1.5.5" is 1.5 then .5. */
    private fun number(): Double? {
        skipSeparators()
        val start = at
        if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
        var digits = 0
        while (at < text.length && text[at].isDigit()) { at++; digits++ }
        if (at < text.length && text[at] == '.') {
            at++
            while (at < text.length && text[at].isDigit()) { at++; digits++ }
        }
        if (digits == 0) {
            at = start
            return null
        }
        if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
            val beforeExponent = at
            at++
            if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
            var exponentDigits = 0
            while (at < text.length && text[at].isDigit()) { at++; exponentDigits++ }
            if (exponentDigits == 0) at = beforeExponent
        }
        return text.substring(start, at).toDoubleOrNull()
    }

    /** An arc flag: a single 0 or 1, which may be written with no separator after it. */
    private fun flag(): Boolean? {
        skipSeparators()
        if (at >= text.length) return null
        return when (text[at]) {
            '0' -> { at++; false }
            '1' -> { at++; true }
            else -> null
        }
    }
}
