package com.bimob.app.editor

import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import org.junit.Assert.*
import org.junit.Test

class GeometryMapperTest {
 @Test fun mapsFlatBuffersIntoTypedEntities() {
  val g=RenderGeometryBuffer(1,7,listOf("w"),floatArrayOf(0f,0f,2f,0f,2f,.2f,0f,.2f,0f,2.7f),
   emptyList(),floatArrayOf(),listOf("o"),floatArrayOf(1f,0f,0f,0.9f,2.1f,0f,0f,0f),
   listOf("c"),floatArrayOf(3f,4f,1f,1.2f,.7f,.75f,0f,0f))
  val typed=GeometryMapper.map(g,mapOf("o" to OpeningRenderInfo("DOOR","w")))
  assertEquals(7L,typed.revision);assertEquals("w",typed.walls.single().id)
  assertEquals(Vec2(0.0,0.1),typed.walls.single().axisStart)
  assertEquals(Vec2(2.0,0.1),typed.walls.single().axisEnd)
  assertEquals("DOOR",typed.openings.single().kind);assertEquals("w",typed.openings.single().hostWallId)
  assertEquals(1.0,typed.catalog.single().rotationRadians,0.0)
 }
 @Test fun rejectsMismatchInsteadOfUnsafeIndexing() {
  val g=RenderGeometryBuffer(1,0,listOf("wall"),floatArrayOf(),emptyList(),floatArrayOf(),emptyList(),floatArrayOf(),emptyList(),floatArrayOf())
  assertThrows(IllegalArgumentException::class.java){GeometryMapper.map(g,emptyMap())}
 }
}
