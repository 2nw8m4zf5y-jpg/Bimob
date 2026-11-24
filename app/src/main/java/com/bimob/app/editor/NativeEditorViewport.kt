package com.bimob.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.round

private enum class EditorTool { WALL, MOVE }
private data class WorldPoint(val x: Double, val y: Double)

@Composable
fun NativeEditorViewport(viewModel: NativeEditorViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var tool by remember { mutableStateOf(EditorTool.WALL) }
    var scale by remember { mutableFloatStateOf(72f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf<Pair<WorldPoint, WorldPoint>?>(null) }
    var selectedWall by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tool == EditorTool.WALL, onClick = { tool = EditorTool.WALL }, label = { Text("Стена") })
            FilterChip(selected = tool == EditorTool.MOVE, onClick = { tool = EditorTool.MOVE }, label = { Text("Перемещение") })
            Button(onClick = viewModel::undo, enabled = state.canUndo) { Text("↶ Undo") }
            Button(onClick = viewModel::redo, enabled = state.canRedo) { Text("↷ Redo") }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).background(Color.White)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var previousCentroid: Offset? = null
                    var previousDistance = 0f
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val first = pressed[0].position
                            val second = pressed[1].position
                            val centroid = (first + second) / 2f
                            val distance = (first - second).getDistance()
                            val oldCentroid = previousCentroid
                            if (oldCentroid != null && previousDistance > 0f && distance > 0f) {
                                scale = (scale * (distance / previousDistance)).coerceIn(12f, 240f)
                                pan += centroid - oldCentroid
                            }
                            previousCentroid = centroid
                            previousDistance = distance
                            event.changes.forEach { it.consume() }
                        } else {
                            previousCentroid = null
                            previousDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(tool) {
                var start: WorldPoint? = null
                var last: WorldPoint? = null
                var gesture: String? = null
                var movingId: String? = null
                detectDragGestures(
                    onDragStart = { screen ->
                        val world = screenToWorld(screen, size, scale, pan)
                        start = snap(world, state.geometry)
                        last = start
                        if (tool == EditorTool.MOVE) {
                            movingId = nearestWallId(screen, state.geometry, size, scale, pan)
                            if (movingId != null) gesture = viewModel.beginGesture()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val first = start ?: return@detectDragGestures
                        val current = snap(screenToWorld(change.position, size, scale, pan), state.geometry)
                        val previous = last ?: first
                        if (tool == EditorTool.WALL) preview = first to current
                        else {
                            val id = movingId
                            if (id != null) viewModel.moveWall(id, current.x - previous.x, current.y - previous.y)
                        }
                        last = current
                    },
                    onDragEnd = {
                        val a = start; val b = last
                        if (tool == EditorTool.WALL && a != null && b != null) viewModel.createWall(a.x, a.y, b.x, b.y)
                        gesture?.let(viewModel::commitGesture)
                        preview = null; start = null; last = null; gesture = null; movingId = null
                    },
                    onDragCancel = {
                        gesture?.let(viewModel::cancelGesture)
                        preview = null; start = null; last = null; gesture = null; movingId = null
                    }
                )
            }
        ) {
            val origin = Offset(size.width / 2f + pan.x, size.height / 2f + pan.y)
            val grid = (scale * .5f).coerceAtLeast(6f)
            var x = origin.x % grid
            while (x < size.width) { drawLine(Color(0xFFE8EDF2), Offset(x, 0f), Offset(x, size.height)); x += grid }
            var y = origin.y % grid
            while (y < size.height) { drawLine(Color(0xFFE8EDF2), Offset(0f, y), Offset(size.width, y)); y += grid }
            drawLine(Color(0xFFCAD3DD), Offset(origin.x, 0f), Offset(origin.x, size.height), 1.5f)
            drawLine(Color(0xFFCAD3DD), Offset(0f, origin.y), Offset(size.width, origin.y), 1.5f)
            val geometry = state.geometry
            if (geometry != null) {
                geometry.walls.indices.forEach { index ->
                    val offset = index * 10
                    drawPolygon(geometry.walls, offset, size, scale, pan,
                        if (geometry.wallIds[index] == selectedWall) Color(0xFF1976D2) else Color(0xFF33465B))
                }
                geometry.wallJoins.indices.forEach { index ->
                    drawPolygon(geometry.wallJoins, index * 9, size, scale, pan, Color(0xFF8BA3B8))
                }
            }
            preview?.let { (a, b) -> drawPreviewWall(a, b, size, scale, pan) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp)) }
        Text("BGR1 • ревизия ${state.geometry?.revision ?: 0} • стен ${state.geometry?.wallIds?.size ?: 0} • стыков ${state.geometry?.wallJoinIds?.size ?: 0}",
            style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

private fun DrawScope.drawPreviewWall(a: WorldPoint, b: WorldPoint, size: androidx.compose.ui.geometry.Size, scale: Float, pan: Offset) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = kotlin.math.hypot(dx, dy)
    if (length < 1e-6) return
    val nx = -dy / length * 0.1
    val ny = dx / length * 0.1
    val corners = listOf(
        WorldPoint(a.x + nx, a.y + ny),
        WorldPoint(b.x + nx, b.y + ny),
        WorldPoint(b.x - nx, b.y - ny),
        WorldPoint(a.x - nx, a.y - ny)
    )
    val path = Path()
    corners.forEachIndexed { index, point ->
        val screen = worldToScreen(point, size, scale, pan)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    path.close()
    drawPath(path, Color(0xFF1976D2).copy(alpha = 0.35f))
}
private fun DrawScope.drawPolygon(values: FloatArray, offset: Int, size: androidx.compose.ui.geometry.Size, scale: Float, pan: Offset, color: Color) {
    val path = Path()
    for (vertex in 0 until 4) {
        val point = worldToScreen(WorldPoint(values[offset + vertex * 2].toDouble(), values[offset + vertex * 2 + 1].toDouble()), size, scale, pan)
        if (vertex == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color.copy(alpha = .72f))
}
private fun worldToScreen(p: WorldPoint, size: androidx.compose.ui.geometry.Size, scale: Float, pan: Offset) =
    Offset(size.width / 2f + pan.x + p.x.toFloat() * scale, size.height / 2f + pan.y - p.y.toFloat() * scale)
private fun screenToWorld(p: Offset, size: IntSize, scale: Float, pan: Offset) =
    WorldPoint(((p.x - size.width / 2f - pan.x) / scale).toDouble(), ((size.height / 2f + pan.y - p.y) / scale).toDouble())
private fun snap(point: WorldPoint, geometry: com.bimob.app.core.nativebridge.RenderGeometryBuffer?): WorldPoint {
    val grid = WorldPoint(round(point.x * 10.0) / 10.0, round(point.y * 10.0) / 10.0)
    if (geometry == null) return grid
    var best = grid; var bestDistance = .16
    geometry.walls.indices.forEach { i ->
        val o = i * 10
        val endpoints = listOf(
            WorldPoint((geometry.walls[o] + geometry.walls[o + 6]) / 2.0, (geometry.walls[o + 1] + geometry.walls[o + 7]) / 2.0),
            WorldPoint((geometry.walls[o + 2] + geometry.walls[o + 4]) / 2.0, (geometry.walls[o + 3] + geometry.walls[o + 5]) / 2.0)
        )
        endpoints.forEach { p ->
            val d = kotlin.math.hypot(p.x - point.x, p.y - point.y)
            if (d < bestDistance) { best = p; bestDistance = d }
        }
    }
    return best
}
private fun nearestWallId(screen: Offset, geometry: com.bimob.app.core.nativebridge.RenderGeometryBuffer?, size: IntSize, scale: Float, pan: Offset): String? {
    if (geometry == null) return null
    var best: String? = null; var distance = 18f
    geometry.walls.indices.forEach { i ->
        val o = i * 10
        val a = worldToScreen(WorldPoint((geometry.walls[o] + geometry.walls[o + 6]) / 2.0, (geometry.walls[o + 1] + geometry.walls[o + 7]) / 2.0), androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), scale, pan)
        val b = worldToScreen(WorldPoint((geometry.walls[o + 2] + geometry.walls[o + 4]) / 2.0, (geometry.walls[o + 3] + geometry.walls[o + 5]) / 2.0), androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), scale, pan)
        val dx=b.x-a.x; val dy=b.y-a.y; val len=dx*dx+dy*dy
        val t=if(len==0f) 0f else (((screen.x-a.x)*dx+(screen.y-a.y)*dy)/len).coerceIn(0f,1f)
        val d=kotlin.math.hypot(screen.x-(a.x+t*dx),screen.y-(a.y+t*dy))
        if(d<distance){distance=d;best=geometry.wallIds[i]}
    }
    return best
}
