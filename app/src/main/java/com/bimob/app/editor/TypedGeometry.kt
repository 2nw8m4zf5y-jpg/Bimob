package com.bimob.app.editor

import com.bimob.app.core.nativebridge.RenderGeometryBuffer

/** Typed, validated projection of the native BGR1 flat buffers. Renderer/hit-test consume these, never raw offsets. */
data class Vec2(val x: Double, val y: Double)
data class WallItem(val id: String, val corners: List<Vec2>, val baseElevation: Double, val height: Double, val axisStart: Vec2, val axisEnd: Vec2)
data class OpeningItem(val id: String, val center: Vec2, val width: Double, val height: Double, val sill: Double, val hostWallId: String, val kind: String)
data class CatalogItem(val id: String, val center: Vec2, val rotationRadians: Double, val width: Double, val depth: Double, val height: Double)
data class TypedGeometry(val revision: Long, val walls: List<WallItem>, val openings: List<OpeningItem>, val catalog: List<CatalogItem>)

object GeometryMapper {
    fun map(buffer: RenderGeometryBuffer, openingInfo: Map<String, OpeningRenderInfo>): TypedGeometry {
        require(buffer.formatVersion == 1) { "Unsupported geometry format." }
        require(buffer.walls.size == buffer.wallIds.size * 10) { "Wall section/ID count mismatch." }
        require(buffer.wallJoins.size == buffer.wallJoinIds.size * 9) { "Wall join section/ID count mismatch." }
        require(buffer.openings.size == buffer.openingIds.size * 8) { "Opening section/ID count mismatch." }
        require(buffer.catalogInstances.size == buffer.catalogInstanceIds.size * 8) { "Catalog section/ID count mismatch." }
        fun finite(v: Float, label: String): Double { require(v.isFinite()) { "$label contains non-finite data." }; return v.toDouble() }
        val walls = buffer.wallIds.indices.map { i ->
            val o = i * 10; val v = buffer.walls
            val p = listOf(Vec2(finite(v[o], "Wall"), finite(v[o+1], "Wall")),
                Vec2(finite(v[o+2], "Wall"), finite(v[o+3], "Wall")),
                Vec2(finite(v[o+4], "Wall"), finite(v[o+5], "Wall")),
                Vec2(finite(v[o+6], "Wall"), finite(v[o+7], "Wall")))
            WallItem(buffer.wallIds[i], p, finite(v[o+8], "Wall"), finite(v[o+9], "Wall"),
                Vec2((p[0].x+p[3].x)/2,(p[0].y+p[3].y)/2),
                Vec2((p[1].x+p[2].x)/2,(p[1].y+p[2].y)/2))
        }
        val openings = buffer.openingIds.indices.map { i ->
            val o=i*8; val v=buffer.openings; val info=openingInfo[buffer.openingIds[i]]
            OpeningItem(buffer.openingIds[i], Vec2(finite(v[o],"Opening"),finite(v[o+1],"Opening")),
                finite(v[o+3],"Opening"), finite(v[o+4],"Opening"), finite(v[o+5],"Opening"),
                info?.hostWallId.orEmpty(), info?.kind.orEmpty())
        }
        val catalog = buffer.catalogInstanceIds.indices.map { i ->
            val o=i*8; val v=buffer.catalogInstances
            CatalogItem(buffer.catalogInstanceIds[i],Vec2(finite(v[o],"Catalog"),finite(v[o+1],"Catalog")),
                finite(v[o+2],"Catalog"),finite(v[o+3],"Catalog"),finite(v[o+4],"Catalog"),
                finite(v[o+5],"Catalog"))
        }
        return TypedGeometry(buffer.revision,walls,openings,catalog)
    }
}
