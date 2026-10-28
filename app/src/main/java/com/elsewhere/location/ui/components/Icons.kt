package com.elsewhere.location.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A small, deliberately hand-tuned line set for the survey-instrument language. */
object EIcon {

    private fun icon(
        name: String,
        stroke: Float = 1.6f,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

    private fun ImageVector.Builder.line(
        vararg commands: Pair<String, FloatArray>,
        strokeWidth: Float = 1.6f,
        cap: StrokeCap = StrokeCap.Round,
        fill: SolidColor? = null,
    ) {
        path(
            fill = fill,
            stroke = if (fill == null) SolidColor(Color.Black) else null,
            strokeLineWidth = strokeWidth,
            strokeLineCap = cap,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = {
            commands.forEach { (command, values) ->
                when (command) {
                    "M" -> moveTo(values[0], values[1])
                    "L" -> lineTo(values[0], values[1])
                    "C" -> curveTo(values[0], values[1], values[2], values[3], values[4], values[5])
                    "O" -> addOval(Rect(values[0], values[1], values[2], values[3]))
                }
            }
            },
        )
    }

    private fun ImageVector.Builder.closed(
        fill: Boolean = true,
        pathBuilder: PathBuilder.() -> Unit,
    ) {
        path(
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = if (fill) null else SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }

    val Cursor = icon("cursor") {
        line("O" to floatArrayOf(7f, 7f, 17f, 17f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(12f, 2.5f), "L" to floatArrayOf(12f, 6.5f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(12f, 17.5f), "L" to floatArrayOf(12f, 21.5f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(2.5f, 12f), "L" to floatArrayOf(6.5f, 12f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(17.5f, 12f), "L" to floatArrayOf(21.5f, 12f), cap = StrokeCap.Square)
        closed { addOval(Rect(10.6f, 10.6f, 13.4f, 13.4f)) }
    }

    val Search = icon("search") {
        line("O" to floatArrayOf(4f, 4f, 15.5f, 15.5f))
        line("M" to floatArrayOf(14.2f, 14.2f), "L" to floatArrayOf(20.5f, 20.5f))
    }

    val Close = icon("close") {
        line("M" to floatArrayOf(5f, 5f), "L" to floatArrayOf(19f, 19f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(19f, 5f), "L" to floatArrayOf(5f, 19f), cap = StrokeCap.Square)
    }

    val Sliders = icon("sliders") {
        line("M" to floatArrayOf(3f, 6f), "L" to floatArrayOf(21f, 6f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(3f, 12f), "L" to floatArrayOf(21f, 12f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(3f, 18f), "L" to floatArrayOf(21f, 18f), cap = StrokeCap.Square)
        closed { addOval(Rect(7f, 4f, 11f, 8f)) }
        closed { addOval(Rect(14f, 10f, 18f, 14f)) }
        closed { addOval(Rect(6f, 16f, 10f, 20f)) }
    }

    val Layers = icon("layers") {
        line("M" to floatArrayOf(12f, 3f), "L" to floatArrayOf(21f, 8f), "L" to floatArrayOf(12f, 13f), "L" to floatArrayOf(3f, 8f), "L" to floatArrayOf(12f, 3f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(3f, 12f), "L" to floatArrayOf(12f, 17f), "L" to floatArrayOf(21f, 12f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(3f, 16f), "L" to floatArrayOf(12f, 21f), "L" to floatArrayOf(21f, 16f), cap = StrokeCap.Square)
    }

    val Locate = icon("locate") {
        line("O" to floatArrayOf(6f, 6f, 18f, 18f))
        line("M" to floatArrayOf(12f, 2f), "L" to floatArrayOf(12f, 6f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(12f, 18f), "L" to floatArrayOf(12f, 22f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(2f, 12f), "L" to floatArrayOf(6f, 12f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(18f, 12f), "L" to floatArrayOf(22f, 12f), cap = StrokeCap.Square)
        closed { addOval(Rect(10.8f, 10.8f, 13.2f, 13.2f)) }
    }

    val Route = icon("route") {
        line("M" to floatArrayOf(5.5f, 19f), "C" to floatArrayOf(5.5f, 13f, 18.5f, 12f, 18.5f, 5.5f))
        closed { addOval(Rect(3.5f, 17f, 7.5f, 21f)) }
        closed { addOval(Rect(16.5f, 3f, 20.5f, 7f)) }
        line("M" to floatArrayOf(11.7f, 10f), "L" to floatArrayOf(14.2f, 10f))
    }

    val Play = icon("play") {
        closed { moveTo(7f, 4.5f); lineTo(20f, 12f); lineTo(7f, 19.5f); close() }
    }

    val Stop = icon("stop") {
        closed { moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close() }
    }

    val ChevronUp = icon("chevron_up") {
        line("M" to floatArrayOf(4f, 15f), "L" to floatArrayOf(12f, 7f), "L" to floatArrayOf(20f, 15f), cap = StrokeCap.Square)
    }

    val ChevronDown = icon("chevron_down") {
        line("M" to floatArrayOf(4f, 9f), "L" to floatArrayOf(12f, 17f), "L" to floatArrayOf(20f, 9f), cap = StrokeCap.Square)
    }

    val ChevronLeft = icon("chevron_left") {
        line("M" to floatArrayOf(15f, 4f), "L" to floatArrayOf(7f, 12f), "L" to floatArrayOf(15f, 20f), cap = StrokeCap.Square)
    }

    val ChevronRight = icon("chevron_right") {
        line("M" to floatArrayOf(9f, 4f), "L" to floatArrayOf(17f, 12f), "L" to floatArrayOf(9f, 20f), cap = StrokeCap.Square)
    }

    val Map = icon("map") {
        line("M" to floatArrayOf(3f, 5f), "L" to floatArrayOf(8f, 3f), "L" to floatArrayOf(16f, 5f), "L" to floatArrayOf(21f, 3f), "L" to floatArrayOf(21f, 19f), "L" to floatArrayOf(16f, 21f), "L" to floatArrayOf(8f, 19f), "L" to floatArrayOf(3f, 21f), "L" to floatArrayOf(3f, 5f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(8f, 3f), "L" to floatArrayOf(8f, 19f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(16f, 5f), "L" to floatArrayOf(16f, 21f), cap = StrokeCap.Square)
    }

    val Motion = icon("motion") {
        line("M" to floatArrayOf(2f, 16f), "L" to floatArrayOf(6f, 12f), "L" to floatArrayOf(9f, 15f), "L" to floatArrayOf(14f, 7f), "L" to floatArrayOf(18f, 11f), "L" to floatArrayOf(22f, 8f))
        line("M" to floatArrayOf(18f, 4f), "L" to floatArrayOf(22f, 8f), "L" to floatArrayOf(18f, 12f), cap = StrokeCap.Square)
    }

    val Environment = icon("environment") {
        line("O" to floatArrayOf(8f, 8f, 16f, 16f))
        line("M" to floatArrayOf(5.5f, 5.5f), "C" to floatArrayOf(2f, 9f, 2f, 15f, 5.5f, 18.5f))
        line("M" to floatArrayOf(18.5f, 5.5f), "C" to floatArrayOf(22f, 9f, 22f, 15f, 18.5f, 18.5f))
        closed { addOval(Rect(11f, 11f, 13f, 13f)) }
    }

    val Apps = icon("apps") {
        line("M" to floatArrayOf(4f, 4f), "L" to floatArrayOf(10f, 4f), "L" to floatArrayOf(10f, 10f), "L" to floatArrayOf(4f, 10f), "L" to floatArrayOf(4f, 4f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(14f, 4f), "L" to floatArrayOf(20f, 4f), "L" to floatArrayOf(20f, 10f), "L" to floatArrayOf(14f, 10f), "L" to floatArrayOf(14f, 4f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(4f, 14f), "L" to floatArrayOf(10f, 14f), "L" to floatArrayOf(10f, 20f), "L" to floatArrayOf(4f, 20f), "L" to floatArrayOf(4f, 14f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(14f, 14f), "L" to floatArrayOf(20f, 14f), "L" to floatArrayOf(20f, 20f), "L" to floatArrayOf(14f, 20f), "L" to floatArrayOf(14f, 14f), cap = StrokeCap.Square)
    }

    val Joystick = icon("joystick") {
        line("O" to floatArrayOf(7f, 7f, 17f, 17f))
        line("M" to floatArrayOf(12f, 12f), "L" to floatArrayOf(16.5f, 7.5f), cap = StrokeCap.Square)
        closed { addOval(Rect(14.8f, 5.8f, 18.2f, 9.2f)) }
        line("M" to floatArrayOf(7f, 18f), "L" to floatArrayOf(17f, 18f), cap = StrokeCap.Square)
    }

    val Wifi = icon("wifi") {
        line("M" to floatArrayOf(3f, 9f), "C" to floatArrayOf(8f, 4f, 16f, 4f, 21f, 9f))
        line("M" to floatArrayOf(6.5f, 13f), "C" to floatArrayOf(9.5f, 10f, 14.5f, 10f, 17.5f, 13f))
        line("M" to floatArrayOf(10f, 17f), "C" to floatArrayOf(11.2f, 16f, 12.8f, 16f, 14f, 17f))
        closed { addOval(Rect(11.2f, 19f, 12.8f, 20.6f)) }
    }

    val Bluetooth = icon("bluetooth") {
        line("M" to floatArrayOf(7f, 7f), "L" to floatArrayOf(17f, 17f), "L" to floatArrayOf(12f, 21f), "L" to floatArrayOf(12f, 3f), "L" to floatArrayOf(17f, 7f), "L" to floatArrayOf(7f, 17f), cap = StrokeCap.Square)
    }

    val Cell = icon("cell") {
        line("M" to floatArrayOf(8f, 21f), "L" to floatArrayOf(12f, 3f), "L" to floatArrayOf(16f, 21f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(9.5f, 14f), "L" to floatArrayOf(14.5f, 14f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(10.2f, 10f), "L" to floatArrayOf(13.8f, 10f), cap = StrokeCap.Square)
    }

    val Footsteps = icon("footsteps") {
        line("O" to floatArrayOf(5f, 4f, 10f, 12f))
        line("O" to floatArrayOf(14f, 9f, 19f, 17f))
        line("O" to floatArrayOf(5.5f, 15f, 9f, 20f))
        line("O" to floatArrayOf(15f, 3f, 18f, 7f))
    }

    val Check = icon("check") {
        line("M" to floatArrayOf(4f, 12.5f), "L" to floatArrayOf(9.5f, 18f), "L" to floatArrayOf(20f, 6.5f), cap = StrokeCap.Square)
    }

    val Plus = icon("plus") {
        line("M" to floatArrayOf(12f, 4f), "L" to floatArrayOf(12f, 20f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(4f, 12f), "L" to floatArrayOf(20f, 12f), cap = StrokeCap.Square)
    }

    val Minus = icon("minus") {
        line("M" to floatArrayOf(4f, 12f), "L" to floatArrayOf(20f, 12f), cap = StrokeCap.Square)
    }

    val Back = icon("back") {
        line("M" to floatArrayOf(20f, 12f), "L" to floatArrayOf(4f, 12f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(4f, 12f), "L" to floatArrayOf(10f, 6f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(4f, 12f), "L" to floatArrayOf(10f, 18f), cap = StrokeCap.Square)
    }

    val Refresh = icon("refresh") {
        line("M" to floatArrayOf(20f, 12f), "C" to floatArrayOf(20f, 16.5f, 16.4f, 20f, 12f, 20f), "C" to floatArrayOf(7.6f, 20f, 4f, 16.4f, 4f, 12f), "C" to floatArrayOf(4f, 7.6f, 7.6f, 4f, 12f, 4f), "C" to floatArrayOf(15f, 4f, 17.3f, 5.1f, 18.8f, 7f))
        line("M" to floatArrayOf(20f, 3.5f), "L" to floatArrayOf(20f, 8.5f), "L" to floatArrayOf(15f, 8.5f), cap = StrokeCap.Square)
    }

    val External = icon("external") {
        line("M" to floatArrayOf(13f, 4f), "L" to floatArrayOf(20f, 4f), "L" to floatArrayOf(20f, 11f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(20f, 4f), "L" to floatArrayOf(11f, 13f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(18f, 14f), "L" to floatArrayOf(18f, 20f), "L" to floatArrayOf(4f, 20f), "L" to floatArrayOf(4f, 6f), "L" to floatArrayOf(10f, 6f), cap = StrokeCap.Square)
    }

    val Trash = icon("trash") {
        line("M" to floatArrayOf(4f, 7f), "L" to floatArrayOf(20f, 7f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(9f, 7f), "L" to floatArrayOf(9f, 3f), "L" to floatArrayOf(15f, 3f), "L" to floatArrayOf(15f, 7f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(6f, 7f), "L" to floatArrayOf(7f, 21f), "L" to floatArrayOf(17f, 21f), "L" to floatArrayOf(18f, 7f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(10f, 11f), "L" to floatArrayOf(10f, 17f))
        line("M" to floatArrayOf(14f, 11f), "L" to floatArrayOf(14f, 17f))
    }

    val Shield = icon("shield") {
        line("M" to floatArrayOf(12f, 2.5f), "L" to floatArrayOf(20f, 6f), "L" to floatArrayOf(20f, 12f), "C" to floatArrayOf(20f, 17f, 16.8f, 20f, 12f, 22f), "C" to floatArrayOf(7.2f, 20f, 4f, 17f, 4f, 12f), "L" to floatArrayOf(4f, 6f), "L" to floatArrayOf(12f, 2.5f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(8.5f, 12f), "L" to floatArrayOf(11.3f, 15f), "L" to floatArrayOf(16f, 9.5f), cap = StrokeCap.Square)
    }

    val Info = icon("info") {
        line("O" to floatArrayOf(3f, 3f, 21f, 21f))
        line("M" to floatArrayOf(12f, 10.5f), "L" to floatArrayOf(12f, 17f))
        closed { addOval(Rect(11.1f, 6.5f, 12.9f, 8.3f)) }
    }

    val Clock = icon("clock") {
        line("O" to floatArrayOf(3f, 3f, 21f, 21f))
        line("M" to floatArrayOf(12f, 7f), "L" to floatArrayOf(12f, 12.5f), "L" to floatArrayOf(16f, 14.5f))
    }

    val LayersOff = icon("layers_off") {
        line("M" to floatArrayOf(3f, 3f), "L" to floatArrayOf(21f, 21f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(3f, 8f), "L" to floatArrayOf(12f, 3f), "L" to floatArrayOf(21f, 8f), "L" to floatArrayOf(17f, 10f), cap = StrokeCap.Square)
        line("M" to floatArrayOf(6.5f, 12.8f), "L" to floatArrayOf(12f, 15.7f), "L" to floatArrayOf(16.3f, 13.4f), cap = StrokeCap.Square)
    }

    val Sensors = icon("sensors") {
        line("M" to floatArrayOf(4f, 18f), "L" to floatArrayOf(4f, 14f), "L" to floatArrayOf(8f, 14f), "L" to floatArrayOf(8f, 10f), "L" to floatArrayOf(12f, 10f), "L" to floatArrayOf(12f, 6f), "L" to floatArrayOf(16f, 6f), "L" to floatArrayOf(16f, 3f))
        closed { addOval(Rect(18f, 16f, 22f, 20f)) }
    }
}
private fun PathBuilder.addOval(oval: Rect) {
    val k = 0.5522848f
    val cx = (oval.left + oval.right) / 2f
    val cy = (oval.top + oval.bottom) / 2f
    val rx = (oval.right - oval.left) / 2f
    val ry = (oval.bottom - oval.top) / 2f
    moveTo(cx, cy - ry)
    curveTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
    curveTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
    curveTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
    curveTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
    close()
}
