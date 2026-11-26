package com.bimob.app.ui.workspace

import com.bimob.app.editor.TypedGeometry
import com.bimob.app.editor.Vec2
import kotlin.math.hypot

/**
 * Workspace cursor snap preview. It only snaps the viewport cursor to real wall-axis
 * endpoints; it does not mutate geometry or claim that drawing commands are wired.
 */
internal fun snapWorkspaceEndpoint(raw: Vec2, geometry: TypedGeometry?, zoomPxPerMeter: Float, thresholdPx: Float): Vec2 {
    if (geometry == null || !zoomPxPerMeter.isFinite() || zoomPxPerMeter <= 0f || !thresholdPx.isFinite() || thresholdPx <= 0f) return raw
    val thresholdMeters = thresholdPx / zoomPxPerMeter
    var best: Vec2? = null
    var bestDistance = thresholdMeters.toDouble()
    geometry.walls.forEach { wall ->
        listOf(wall.axisStart, wall.axisEnd).forEach { endpoint ->
            val distance = hypot(raw.x - endpoint.x, raw.y - endpoint.y)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = endpoint
            }
        }
    }
    return best ?: raw
}
