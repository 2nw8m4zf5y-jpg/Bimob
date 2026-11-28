package com.bimob.app.ui.workspace

import com.bimob.app.editor.TypedGeometry
import com.bimob.app.editor.Vec2
import com.bimob.app.editor.WallItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceViewportMathTest {
 private fun geometry()=TypedGeometry(
  revision=1,
  walls=listOf(WallItem("w1",listOf(Vec2(0.0,0.0),Vec2(4.0,0.0),Vec2(4.0,.2),Vec2(0.0,.2)),0.0,2.7,Vec2(0.0,0.1),Vec2(4.0,0.1))),
  joins=emptyList(),openings=emptyList(),catalog=emptyList()
 )
 @Test fun cursorSnapsToEndpointWithinPixelTolerance(){
  val result=snapWorkspaceEndpoint(Vec2(.08,.02),geometry(),100f,12f)
  assertEquals(Vec2(0.0,0.1),result)
 }
 @Test fun cursorRemainsFreeOutsideTolerance(){
  val raw=Vec2(.3,.3)
  assertEquals(raw,snapWorkspaceEndpoint(raw,geometry(),100f,12f))
 }
 @Test fun snapToleranceScalesWithZoom(){
  val raw=Vec2(.05,.05)
  assertEquals(Vec2(0.0,0.1),snapWorkspaceEndpoint(raw,geometry(),100f,12f))
  assertEquals(raw,snapWorkspaceEndpoint(raw,geometry(),300f,12f))
 }
 @Test fun invalidCameraDoesNotSnap(){
  val raw=Vec2(.01,.0)
  assertEquals(raw,snapWorkspaceEndpoint(raw,geometry(),0f,12f))
 }
}
