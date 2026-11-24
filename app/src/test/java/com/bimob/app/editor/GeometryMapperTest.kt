package com.bimob.app.editor

import com.bimob.app.core.nativebridge.RenderGeometryBuffer
import org.junit.Assert.*
import org.junit.Test

class GeometryMapperTest {
 @Test fun mapsFlatBuffersIntoTypedEntities() {
  val g=RenderGeometryBuffer(1,7,listOf("w"),floatArrayOf(0f,0f,2f,0f,2f,.2f,0f,.2f,.3f,2.7f),
   emptyList(),floatArrayOf(),listOf("o"),floatArrayOf(1f,0f,0.5f,0.9f,2.1f,0f,.3f,4f),
   listOf("c"),floatArrayOf(3f,4f,1f,1.2f,.7f,.75f,.3f,1f))
  val typed=GeometryMapper.map(g,mapOf("o" to OpeningRenderInfo("DOOR","w")),mapOf("c" to CatalogRenderInfo("w","asset.test","furniture")))
  assertEquals(7L,typed.revision);assertEquals("w",typed.walls.single().id)
  assertEquals(0.0,typed.walls.single().axisStart.x,1e-6); assertEquals(0.1,typed.walls.single().axisStart.y,1e-6)
  assertEquals(2.0,typed.walls.single().axisEnd.x,1e-6); assertEquals(0.1,typed.walls.single().axisEnd.y,1e-6)
  assertEquals("DOOR",typed.openings.single().kind);assertEquals("w",typed.openings.single().hostWallId); assertEquals(0.3,typed.openings.single().elevation,1e-6); assertEquals(0.5,typed.openings.single().offsetAlongWall,1e-6); assertEquals(4.0,typed.openings.single().hostAxisLength,1e-6)
  assertEquals(1.0,typed.catalog.single().rotationRadians,0.0); assertEquals(0.3,typed.catalog.single().elevation,1e-6); assertTrue(typed.catalog.single().hosted); assertEquals("w",typed.catalog.single().hostWallId); assertEquals("asset.test",typed.catalog.single().assetId)
 }
 @Test fun rejectsMismatchInsteadOfUnsafeIndexing() {
  val g=RenderGeometryBuffer(1,0,listOf("wall"),floatArrayOf(),emptyList(),floatArrayOf(),emptyList(),floatArrayOf(),emptyList(),floatArrayOf())
  assertThrows(IllegalArgumentException::class.java){GeometryMapper.map(g,emptyMap())}
 }
}
