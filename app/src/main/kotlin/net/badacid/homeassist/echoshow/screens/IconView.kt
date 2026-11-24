package net.badacid.homeassist.echoshow.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import net.badacid.homeassist.echoshow.core.PathCommand
import net.badacid.homeassist.echoshow.core.parseSvgPath

/** The icons the app's own buttons use, from the LG app's Icon.tsx. */
object Icons {
    const val SETTINGS =
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
    const val CLOSE = "M18 6 6 18M6 6l12 12"
    const val LEFT = "M15 18l-6-6 6-6"
    const val MORE = "M5 12h.01M12 12h.01M19 12h.01"
    const val CHECK = "M20 6 9 17l-5-5"
}

/**
 * A line icon from SVG path data drawn on a 24×24 grid with a 2-unit stroke and round ends, scaled to the
 * view's size, in `colour`.
 */
class IconView(context: Context, pathData: String, colour: Int) : View(context) {
    private val source = toPath(parseSvgPath(pathData))
    private val drawn = Path()
    private val scale = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colour
    }

    var colour: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (side <= 0f) return
        val unit = side / 24f
        scale.reset()
        scale.setScale(unit, unit)
        scale.postTranslate(paddingLeft + (width - paddingLeft - paddingRight - side) / 2, paddingTop + (height - paddingTop - paddingBottom - side) / 2)
        source.transform(scale, drawn)
        paint.strokeWidth = 2f * unit
        canvas.drawPath(drawn, paint)
    }

    private companion object {
        fun toPath(commands: List<PathCommand>) = Path().apply {
            for (command in commands) {
                when (command) {
                    is PathCommand.MoveTo -> moveTo(command.x, command.y)
                    is PathCommand.LineTo -> lineTo(command.x, command.y)
                    is PathCommand.CubicTo -> cubicTo(command.x1, command.y1, command.x2, command.y2, command.x, command.y)
                    PathCommand.Close -> close()
                }
            }
        }
    }
}
