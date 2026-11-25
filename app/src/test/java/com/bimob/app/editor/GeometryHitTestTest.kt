package com.bimob.app.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.hypot
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
 @Test fun offsetCrosshairConvertsWithZoomAndPan(){
  val finger=Offset(200f,150f)
  val world=offsetCrosshairWorld(finger,48f,IntSize(400,400),100f,Offset(20f,-10f))
  assertEquals(-.2,world.x,1e-6);assertEquals(.88,world.y,1e-6)
  val target=offsetCrosshairScreen(finger,48f)
  assertEquals(200f,target.x,0f);assertEquals(102f,target.y,0f)
 }
 @Test fun endpointSnapHasHighestPriorityAndScreenThreshold(){
  val g=geom()
  val raw=WorldPoint(2.03,.02)
  val result=snapPoint(raw,null,g,IntSize(400,400),100f,Offset.Zero,22f,false)
  assertEquals(SnapKind.ENDPOINT,result.kind);assertEquals(2.0,result.point.x,1e-6);assertEquals(0.0,result.point.y,1e-6)
  val empty=TypedGeometry(1,emptyList(),emptyList(),emptyList(),emptyList())
  val tooFar=snapPoint(WorldPoint(2.25,0.0),null,empty,IntSize(400,400),100f,Offset.Zero,3f,false)
  assertEquals(SnapKind.NONE,tooFar.kind)
 }
 @Test fun edgeSnapPrecedesOrthoAndGrid(){
  val raw=WorldPoint(1.0,.12)
  val edge=snapPoint(raw,WorldPoint(0.0,0.0),geom(),IntSize(400,400),100f,Offset.Zero,22f,true)
  assertEquals(SnapKind.EDGE,edge.kind);assertEquals(0.0,edge.point.y,1e-6)
 }
 @Test fun orthoLockSnapsAndSoftModeUsesFiveDegreeWindow(){
  val anchor=WorldPoint(0.0,0.0)
  val raw=WorldPoint(.96,.43)
  val locked=snapPoint(raw,anchor,geom(),IntSize(400,400),100f,Offset.Zero,22f,true)
  assertEquals(SnapKind.ORTHO,locked.kind);assertEquals(0.0,locked.point.y,1e-6)
  val empty=TypedGeometry(1,emptyList(),emptyList(),emptyList(),emptyList())
  val softOutside=snapPoint(raw,anchor,empty,IntSize(400,400),100f,Offset.Zero,3f,false)
  assertEquals(SnapKind.NONE,softOutside.kind)
  val softInside=snapPoint(WorldPoint(1.0,.05),anchor,empty,IntSize(400,400),100f,Offset.Zero,3f,false)
  assertEquals(SnapKind.ORTHO,softInside.kind);assertEquals(0.0,softInside.point.y,1e-6)
 }
 @Test fun gridSnapIsLastFallbackAndThresholdIsScreenSpace(){
  val raw=WorldPoint(.43,.47)
  val snapped=snapPoint(raw,null,TypedGeometry(1,emptyList(),emptyList(),emptyList(),emptyList()),IntSize(400,400),100f,Offset.Zero,22f,false)
  assertEquals(SnapKind.GRID,snapped.kind);assertEquals(.4,snapped.point.x,1e-6);assertEquals(.5,snapped.point.y,1e-6)
  val noSnap=snapPoint(raw,null,TypedGeometry(1,emptyList(),emptyList(),emptyList(),emptyList()),IntSize(400,400),100f,Offset.Zero,3f,false)
  assertEquals(SnapKind.NONE,noSnap.kind)
 }
 @Test fun cornerAndTSnapPreviewMiterProducesFiniteJoinFootprint(){
  val corner=previewJoinCorners(wall,WorldPoint(2.0,0.0),WorldPoint(2.0,2.0),WorldPoint(2.0,0.0),.2)
  assertNotNull(corner);assertEquals(4,corner!!.size);assertTrue(corner.all{it.x.isFinite()&&it.y.isFinite()})
  val tJoin=previewJoinCorners(wall,WorldPoint(1.0,0.0),WorldPoint(1.0,1.0),WorldPoint(1.0,0.0),.2)
  assertNotNull(tJoin);assertEquals(4,tJoin!!.size)
  assertTrue(tJoin.any{abs(it.x-1.0)<.11&&abs(it.y)<.11})
 }
 @Test fun selectedWallEndpointHandlesAreScreenHitTargets(){
  val start=hitWallEndpoint(Offset(200f,200f),wall,IntSize(400,400),100f,Offset.Zero,24f)
  val end=hitWallEndpoint(Offset(400f,200f),wall,IntSize(400,400),100f,Offset.Zero,24f)
  assertTrue(start!!.moveStart);assertFalse(end!!.moveStart)
  assertNull(hitWallEndpoint(Offset(260f,200f),wall,IntSize(400,400),100f,Offset.Zero,24f))
 }
 @Test fun endpointPreviewKeepsWallThicknessAndMovesOnlyOneEnd(){
  val preview=wallEndpointPreviewCorners(wall,false,WorldPoint(3.0,1.0))
  val a=Vec2((preview[0].x+preview[3].x)/2,(preview[0].y+preview[3].y)/2)
  val b=Vec2((preview[1].x+preview[2].x)/2,(preview[1].y+preview[2].y)/2)
  assertEquals(0.0,a.x,1e-6);assertEquals(0.0,a.y,1e-6)
  assertEquals(3.0,b.x,1e-6);assertEquals(1.0,b.y,1e-6)
  assertEquals(.2,hypot(preview[0].x-preview[3].x,preview[0].y-preview[3].y),1e-6)
 }
 @Test fun rotatedCatalogBoundsAreHitTestable(){
  val item=CatalogItem("c",Vec2(1.0,1.0),Math.PI/4,1.0,1.0,1.0,0.0,false,"")
  val g=geom(listOf(item));val center=screen(item.center,100f)
  assertEquals(EditorSelection(EditorElementKind.CATALOG,"c"),hitTestTypedGeometry(center,g,IntSize(400,400),100f,Offset.Zero,24f))
  val outside=Offset(center.x+80f,center.y+180f)
  assertNull(hitTestTypedGeometry(outside,g,IntSize(400,400),100f,Offset.Zero,24f))
 }
 @Test fun catalogGripResizeKeepsOppositeFaceFixedForAllFourSidesAndRotation(){
  val item=CatalogItem("desk",Vec2(3.0,-2.0),Math.PI/3,2.0,1.2,0.8,0.0,false,"")
  for(grip in CatalogGrip.values()){
   val fixed=catalogGripFixedPoint(item,grip);val axis=catalogGripAxis(item,grip);val sign=catalogGripSign(grip);val old=catalogGripDimension(item,grip);val drag=catalogGripPoint(item,grip)
   val target=Vec2(drag.x+axis.x*sign*.75,drag.y+axis.y*sign*.75);val result=resizeCatalogGrip(item,grip,target)!!
   val dim=if(grip==CatalogGrip.WIDTH_POSITIVE||grip==CatalogGrip.WIDTH_NEGATIVE)result.width else result.depth
   val fixedAfter=Vec2(result.center.x-axis.x*sign*dim/2,result.center.y-axis.y*sign*dim/2)
   assertEquals(fixed.x,fixedAfter.x,1e-9);assertEquals(fixed.y,fixedAfter.y,1e-9);assertEquals(old+.75,dim,1e-9)
  }
 }
 @Test fun catalogGripResizeClampsBoundsAndRejectsInvalidPoint(){
  val item=CatalogItem("cabinet",Vec2(0.0,0.0),0.0,1.0,2.0,1.0,0.0,false,"")
  val min=resizeCatalogGrip(item,CatalogGrip.WIDTH_POSITIVE,Vec2(-10.0,0.0))!!;assertEquals(.05,min.width,1e-9);assertEquals(.475,min.center.x,1e-9)
  val max=resizeCatalogGrip(item,CatalogGrip.DEPTH_POSITIVE,Vec2(0.0,100.0))!!;assertEquals(50.0,max.depth,1e-9)
  assertNull(resizeCatalogGrip(item,CatalogGrip.DEPTH_NEGATIVE,Vec2(Double.NaN,0.0)))
 }

}
