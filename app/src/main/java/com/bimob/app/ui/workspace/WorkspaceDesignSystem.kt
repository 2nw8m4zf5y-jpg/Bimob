package com.bimob.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Workspace visual primitives. Keep this file independent from editor/model state.
 * Visible glyph target is 6 mm; hit targets are owned by the caller and are deliberately larger.
 */
internal object CadWorkspaceTokens {
    val canvas = Color(0xFF0B1525)
    val panel = Color(0xFF14243A)
    val panelRaised = Color(0xFF182A40)
    val gridMinor = Color(0xFF182538)
    val gridMajor = Color(0xFF2B4058)
    val divider = Color(0xFF344B63)
    val geometry = Color(0xFFC9D8E8)
    val active = Color(0xFF42BDF5)
    val selected = Color(0xFFFFD166)
    val snap = Color(0xFF37E6A5)
    val warning = Color(0xFFFFB547)
    val error = Color(0xFFFF5263)
    val text = Color(0xFFE7F3FF)
    val muted = Color(0xFF91A8C0)
}

internal enum class CadGlyph {
    SELECT, LINE, POLYLINE, WALL, RECT, CIRCLE, ARC, MOVE, COPY, TRIM, OFFSET,
    DOOR, WINDOW, DIMENSION, NODE, ROTATE, MIRROR, EXTEND, DELETE, PAN, ZOOM
}

/** Shared vector glyph artwork on a 24-unit grid, sized by role in dp. */
@Composable
internal fun CadVectorIcon(
    glyph: CadGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    iconSizeDp: Float = 24f,
    strokeWidth: Float = 1.35f
) {
    val iconSize = iconSizeDp.dp
    Box(modifier = modifier.size(iconSize)) {
        Canvas(Modifier.size(iconSize)) {
            val scale = min(size.width, size.height) / 24f
            fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
            fun line(a: Offset, b: Offset, sw: Float = strokeWidth) = drawLine(color, a, b, sw * scale)
            fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean = false) =
                drawRect(color, p(x, y), Size(w * scale, h * scale), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(strokeWidth * scale))
            fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
                drawCircle(color, r * scale, p(x, y), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else Stroke(strokeWidth * scale))
            fun path(points: List<Pair<Float, Float>>, close: Boolean = false) {
                val shape = Path()
                points.forEachIndexed { index, point ->
                    val q = p(point.first, point.second)
                    if (index == 0) shape.moveTo(q.x, q.y) else shape.lineTo(q.x, q.y)
                }
                if (close) shape.close()
                drawPath(shape, color, style = Stroke(strokeWidth * scale))
            }
            when (glyph) {
                CadGlyph.SELECT -> { path(listOf(5f to 3f, 5f to 19f, 10f to 14f, 13f to 21f, 16f to 20f, 12f to 13f, 19f to 12f, 5f to 3f), true) }
                CadGlyph.LINE -> line(p(3f, 21f), p(21f, 3f))
                CadGlyph.POLYLINE -> path(listOf(3f to 20f, 7f to 5f, 16f to 17f, 21f to 4f))
                CadGlyph.WALL -> { rect(3f, 4f, 18f, 16f); line(p(3f, 12f), p(21f, 12f)) }
                CadGlyph.RECT -> rect(4f, 4f, 16f, 16f)
                CadGlyph.CIRCLE -> circle(12f, 12f, 8f)
                CadGlyph.ARC -> { drawArc(color, 205f, 130f, false, p(3f, 3f), Size(18f * scale, 18f * scale), style = Stroke(strokeWidth * scale)); line(p(4f, 17f), p(4f, 21f)); line(p(4f, 21f), p(8f, 21f)) }
                CadGlyph.MOVE -> { line(p(12f, 3f), p(12f, 21f)); line(p(3f, 12f), p(21f, 12f)); path(listOf(12f to 3f, 9f to 6f, 12f to 3f, 15f to 6f)); path(listOf(21f to 12f, 18f to 9f, 21f to 12f, 18f to 15f)); path(listOf(12f to 21f, 9f to 18f, 12f to 21f, 15f to 18f)); path(listOf(3f to 12f, 6f to 9f, 3f to 12f, 6f to 15f)) }
                CadGlyph.COPY -> { rect(3f, 3f, 13f, 13f); rect(8f, 8f, 13f, 13f) }
                CadGlyph.TRIM -> { line(p(3f, 3f), p(21f, 21f)); line(p(3f, 21f), p(21f, 3f)); line(p(12f, 3f), p(12f, 9f)) }
                CadGlyph.OFFSET -> { rect(4f, 4f, 12f, 12f); rect(8f, 8f, 12f, 12f) }
                CadGlyph.DOOR -> { line(p(4f, 4f), p(4f, 20f)); line(p(4f, 4f), p(20f, 4f)); path(listOf(4f to 20f, 20f to 20f, 20f to 4f)); drawArc(color, 0f, 90f, false, p(4f, 4f), Size(16f * scale, 16f * scale), style = Stroke(strokeWidth * scale)) }
                CadGlyph.WINDOW -> { rect(3f, 5f, 18f, 14f); line(p(3f, 12f), p(21f, 12f)); line(p(12f, 5f), p(12f, 19f)) }
                CadGlyph.DIMENSION -> { line(p(4f, 12f), p(20f, 12f)); line(p(4f, 8f), p(4f, 16f)); line(p(20f, 8f), p(20f, 16f)); path(listOf(4f to 12f, 7f to 10f, 4f to 12f, 7f to 14f)); path(listOf(20f to 12f, 17f to 10f, 20f to 12f, 17f to 14f)) }
                CadGlyph.NODE -> { circle(12f, 12f, 5f); line(p(12f, 2f), p(12f, 7f)); line(p(12f, 17f), p(12f, 22f)); line(p(2f, 12f), p(7f, 12f)); line(p(17f, 12f), p(22f, 12f)); circle(12f, 12f, 1.5f, true) }
                CadGlyph.ROTATE -> { drawArc(color, 45f, 275f, false, p(4f, 4f), Size(16f * scale, 16f * scale), style = Stroke(strokeWidth * scale)); path(listOf(18f to 4f, 20f to 8f, 18f to 4f, 14f to 5f)) }
                CadGlyph.MIRROR -> { line(p(12f, 2f), p(12f, 22f)); path(listOf(4f to 6f, 10f to 12f, 4f to 18f), true); path(listOf(20f to 6f, 14f to 12f, 20f to 18f), true) }
                CadGlyph.EXTEND -> { line(p(3f, 19f), p(18f, 4f)); line(p(9f, 4f), p(18f, 4f)); line(p(18f, 4f), p(18f, 13f)); line(p(6f, 21f), p(21f, 6f)) }
                CadGlyph.DELETE -> { line(p(5f, 5f), p(19f, 19f)); line(p(5f, 19f), p(19f, 5f)); rect(4f, 4f, 16f, 16f) }
                CadGlyph.PAN -> { circle(12f, 12f, 4f); line(p(12f, 2f), p(12f, 8f)); line(p(12f, 16f), p(12f, 22f)); line(p(2f, 12f), p(8f, 12f)); line(p(16f, 12f), p(22f, 12f)) }
                CadGlyph.ZOOM -> { circle(10f, 10f, 6f); line(p(15f, 15f), p(21f, 21f)); line(p(7f, 10f), p(13f, 10f)); line(p(10f, 7f), p(10f, 13f)) }
            }
        }
    }
}
