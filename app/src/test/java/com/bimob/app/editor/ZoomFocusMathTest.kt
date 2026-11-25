package com.bimob.app.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomFocusMathTest {
 @Test fun focalWorldPointRemainsUnderNewCentroidDuringPinchAndPan(){
  val size=Size(1080f,2200f)
  val old=Offset(390f,820f);val next=Offset(515f,930f)
  val camera=FocusZoomCamera(60f,-90f,120f)
  val focus=screenToWorldFocus(old,size,camera.zoom,camera.panX,camera.panY)
  val updated=zoomAroundCentroid(old,next,1.7f,size,camera)
  val screen=worldToScreenFocus(focus,size,updated)
  assertEquals(next.x,screen.x,1e-3f);assertEquals(next.y,screen.y,1e-3f)
 }
 @Test fun zoomClampStillKeepsFocusWhenNotPanClamped(){
  val size=Size(900f,1400f);val old=Offset(450f,700f);val next=Offset(470f,720f)
  val camera=FocusZoomCamera(230f,0f,0f);val focus=screenToWorldFocus(old,size,camera.zoom,camera.panX,camera.panY)
  val updated=zoomAroundCentroid(old,next,2f,size,camera)
  assertEquals(240f,updated.zoom,0f)
  val p=worldToScreenFocus(focus,size,updated)
  assertEquals(next.x,p.x,1e-3f);assertEquals(next.y,p.y,1e-3f)
 }
}
