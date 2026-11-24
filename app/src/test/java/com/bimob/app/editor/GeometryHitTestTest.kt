package com.bimob.app.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class GeometryHitTestTest {
 private val wall=WallItem("w",listOf(Vec2(0.0,-.1),Vec2(2.0,-.1),Vec2(2.0,.1),Vec2(0.0,.1)),0.0,2.7,Vec2(0.0,0.0),Vec2(2.0,0.0))
 private fun geom(catalog:List<CatalogItem> = emptyList())=TypedGeometry(1,listOf(wall),emptyList(),emptyList(),catalog)
 private fun screen(world:Vec2,zoom:Float)=Offset(200f+world.x.toFloat()*zoom,200f-world.y.toFloat()*zoom)
 @Test fun wallHitTargetIsScreenSizedAtDifferentZooms(){
  for(zoom in listOf(24f,120f,240f)){
   assertEquals(EditorSelection(EditorElementKind.WALL,"w"),hitTestTypedGeometry(screen(Vec2(1.0,0.0),zoom),geom(),IntSize(400,400),zoom,Offset.Zero,24f))
   assertNull(hitTestTypedGeometry(Offset(200f,260f),geom(),IntSize(400,400),zoom,Offset.Zero,24f))
  }
 }
 @Test fun wallTransformPreviewRotationAndScaleArePure(){
  val scaled=transformWallPreview(wall,TransformPreviewState(EditorElementKind.WALL,"w",scaleFactor=2.0))
  val startCenter=Vec2((scaled[0].x+scaled[3].x)/2,(scaled[0].y+scaled[3].y)/2)
  val endCenter=Vec2((scaled[1].x+scaled[2].x)/2,(scaled[1].y+scaled[2].y)/2)
  assertEquals(0.0,startCenter.x,1e-6);assertEquals(4.0,endCenter.x,1e-6)
  val rotated=transformWallPreview(wall,TransformPreviewState(EditorElementKind.WALL,"w",rotationRadians=Math.PI/2))
  val center=Vec2((rotated.map{it.x}.average()),(rotated.map{it.y}.average()))
  assertEquals(1.0,center.x,1e-6);assertEquals(0.0,center.y,1e-6)
  assertEquals(2.0,wall.axisEnd.x,1e-6)
 }
 @Test fun rotatedCatalogBoundsAreHitTestable(){
  val item=CatalogItem("c",Vec2(1.0,1.0),Math.PI/4,1.0,1.0,1.0,0.0,false,"")
  val g=geom(listOf(item));val center=screen(item.center,100f)
  assertEquals(EditorSelection(EditorElementKind.CATALOG,"c"),hitTestTypedGeometry(center,g,IntSize(400,400),100f,Offset.Zero,24f))
  val outside=Offset(center.x+80f,center.y+180f)
  assertNull(hitTestTypedGeometry(outside,g,IntSize(400,400),100f,Offset.Zero,24f))
 }
}
