package com.bimob.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.*

private enum class EditorTool { WALL, SELECT, DOOR, WINDOW, CATALOG }
private data class WorldPoint(val x:Double,val y:Double)

@Composable
fun NativeEditorViewport(viewModel:NativeEditorViewModel,modifier:Modifier=Modifier) {
 val state by viewModel.state.collectAsState()
 var tool by remember{mutableStateOf(EditorTool.WALL)}
 var preset by remember{mutableStateOf(CatalogPreset.TABLE)}
 var rotation by remember{mutableFloatStateOf(0f)}
 var scale by remember{mutableFloatStateOf(72f)}
 var pan by remember{mutableStateOf(Offset.Zero)}
 var preview by remember{mutableStateOf<Pair<WorldPoint,WorldPoint>?>(null)}
 Column(modifier) {
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()) {
   FilterChip(tool==EditorTool.WALL,{tool=EditorTool.WALL},{Text("Стена")})
   FilterChip(tool==EditorTool.SELECT,{tool=EditorTool.SELECT},{Text("Выбор")})
   FilterChip(tool==EditorTool.DOOR,{tool=EditorTool.DOOR},{Text("Дверь")})
   FilterChip(tool==EditorTool.WINDOW,{tool=EditorTool.WINDOW},{Text("Окно")})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()) {
   FilterChip(tool==EditorTool.CATALOG,{tool=EditorTool.CATALOG},{Text("Каталог")})
   CatalogPreset.all.forEach { item -> FilterChip(preset==item,{preset=item;tool=EditorTool.CATALOG},{Text(item.label)}) }
   TextButton(onClick={rotation=(rotation+PI.toFloat()/4f)%(2f*PI.toFloat())}){Text("↻ 45°")}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
   Button(onClick=viewModel::undo,enabled=state.canUndo){Text("↶ Undo")}
   Button(onClick=viewModel::redo,enabled=state.canRedo){Text("↷ Redo")}
   Button(onClick=viewModel::deleteSelection,enabled=state.selection!=null,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Удалить")}
   state.selection?.let{Text("Выбрано: "+when(it.kind){EditorElementKind.WALL->"стена";EditorElementKind.OPENING->"проём";EditorElementKind.CATALOG->"объект"},style=MaterialTheme.typography.labelSmall)}
  }
  Canvas(Modifier.fillMaxWidth().weight(1f).background(Color.White)
   .pointerInput(Unit){
    awaitEachGesture {
     var oldCentroid:Offset?=null;var oldDistance=0f
     do {
      val event=awaitPointerEvent();val pressed=event.changes.filter{it.pressed}
      if(pressed.size>=2){val a=pressed[0].position;val b=pressed[1].position;val c=(a+b)/2f;val d=(a-b).getDistance()
       if(oldCentroid!=null&&oldDistance>0f&&d>0f){scale=(scale*d/oldDistance).coerceIn(12f,240f);pan+=c-oldCentroid!!}
       oldCentroid=c;oldDistance=d;event.changes.forEach{it.consume()}
      }else{oldCentroid=null;oldDistance=0f}
     }while(event.changes.any{it.pressed})
    }
   }
   .pointerInput(tool,scale,pan,state.geometry,preset,rotation){
    detectTapGestures { screen ->
     val p=screenToWorld(screen,size,scale,pan)
     when(tool){
      EditorTool.DOOR->viewModel.createOpening("DOOR",p.x,p.y)
      EditorTool.WINDOW->viewModel.createOpening("WINDOW",p.x,p.y)
      EditorTool.CATALOG->viewModel.createCatalog(preset,p.x,p.y,rotation.toDouble())
      else->viewModel.selectAt(p.x,p.y)
     }
    }
   }
   .pointerInput(tool,scale,pan,state.geometry){
    var start:WorldPoint?=null;var last:WorldPoint?=null;var gesture:String?=null;var movingId:String?=null
    detectDragGestures(onDragStart={screen->
     val world=snap(screenToWorld(screen,size,scale,pan),state.geometry);start=world;last=world
     if(tool==EditorTool.SELECT){movingId=nearestWallId(screen,state.geometry,size,scale,pan);if(movingId!=null)gesture=viewModel.beginGesture()}
    },onDrag={change,_->
     change.consume();val a=start?:return@detectDragGestures
     val p=snap(screenToWorld(change.position,size,scale,pan),state.geometry);val prev=last?:a
     if(tool==EditorTool.WALL)preview=a to p
     else if(tool==EditorTool.SELECT){movingId?.let{viewModel.moveWall(it,p.x-prev.x,p.y-prev.y)}}
     last=p
    },onDragEnd={
     val a=start;val b=last
     if(tool==EditorTool.WALL&&a!=null&&b!=null)viewModel.createWall(a.x,a.y,b.x,b.y)
     gesture?.let(viewModel::commitGesture)
     preview=null;start=null;last=null;gesture=null;movingId=null
    },onDragCancel={gesture?.let(viewModel::cancelGesture);preview=null;start=null;last=null;gesture=null;movingId=null})
   }
  ) {
   val origin=Offset(size.width/2f+pan.x,size.height/2f+pan.y);val grid=(scale*.5f).coerceAtLeast(6f)
   var gx=origin.x%grid;while(gx<size.width){drawLine(Color(0xFFE8EDF2),Offset(gx,0f),Offset(gx,size.height));gx+=grid}
   var gy=origin.y%grid;while(gy<size.height){drawLine(Color(0xFFE8EDF2),Offset(0f,gy),Offset(size.width,gy));gy+=grid}
   drawLine(Color(0xFFCAD3DD),Offset(origin.x,0f),Offset(origin.x,size.height),1.5f)
   drawLine(Color(0xFFCAD3DD),Offset(0f,origin.y),Offset(size.width,origin.y),1.5f)
   val g=state.geometry
   if(g!=null){
    g.walls.indices.forEach{i->drawPolygon(g.walls,i*10,size,scale,pan,if(state.selection==EditorSelection(EditorElementKind.WALL,g.wallIds[i]))Color(0xFF1976D2)else Color(0xFF33465B))}
    g.wallJoins.indices.forEach{i->drawPolygon(g.wallJoins,i*9,size,scale,pan,Color(0xFF8BA3B8))}
    g.openingIds.indices.forEach{i->
     val o=i*8;val info=state.openingInfo[g.openingIds[i]]?:return@forEach
     val wi=g.wallIds.indexOf(info.hostWallId);if(wi<0)return@forEach
     val seg=wallSegment(g,wi)?:return@forEach
     val dx=seg.second.x-seg.first.x;val dy=seg.second.y-seg.first.y;val len=hypot(dx,dy);if(len<1e-6)return@forEach
     val ux=dx/len;val uy=dy/len;val cx=g.openings[o].toDouble();val cy=g.openings[o+1].toDouble();val half=g.openings[o+3]/2.0
     val a=worldToScreen(WorldPoint(cx-ux*half,cy-uy*half),size,scale,pan);val b=worldToScreen(WorldPoint(cx+ux*half,cy+uy*half),size,scale,pan)
     drawLine(Color.White,a,b,strokeWidth=10f)
     val selected=state.selection==EditorSelection(EditorElementKind.OPENING,g.openingIds[i]);val c=if(selected)Color(0xFF1976D2)else Color(0xFF008C95)
     if(info.kind=="DOOR"){
      drawLine(c,a,Offset(a.x+(b.x-a.x)*.9f,a.y+(b.y-a.y)*.9f),2f)
      val radius=(g.openings[o+3]*scale).coerceAtLeast(4f)
      drawArc(c,0f,90f,false,Offset(a.x-radius,a.y-radius),Size(radius*2,radius*2),style=Stroke(1.5f))
     }else{
      drawLine(c,Offset(a.x+(b.x-a.x)*.0f,a.y+(b.y-a.y)*.0f),Offset(b.x,b.y),3f)
      drawLine(c,Offset((a.x-uy*4).toFloat(),(a.y-ux*4).toFloat()),Offset((b.x-uy*4).toFloat(),(b.y-ux*4).toFloat()),2f)
     }
    }
    g.catalogInstanceIds.indices.forEach{i->
     val o=i*8;val cx=g.catalogInstances[o].toDouble();val cy=g.catalogInstances[o+1].toDouble();val angle=g.catalogInstances[o+2].toDouble()
     val hw=g.catalogInstances[o+3]/2.0;val hd=g.catalogInstances[o+4]/2.0
     val corners=listOf(-hw to -hd,hw to -hd,hw to hd,-hw to hd)
     val path=Path()
     corners.forEachIndexed{k,pair->val x=cx+pair.first*cos(angle)-pair.second*sin(angle);val y=cy+pair.first*sin(angle)+pair.second*cos(angle);val s=worldToScreen(WorldPoint(x,y),size,scale,pan);if(k==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)}
     path.close();val selected=state.selection==EditorSelection(EditorElementKind.CATALOG,g.catalogInstanceIds[i])
     drawPath(path,if(selected)Color(0x331976D2)else Color(0x223A789A));drawPath(path,if(selected)Color(0xFF1976D2)else Color(0xFF3A789A),style=Stroke(2f))
    }
   }
   preview?.let{drawPreviewWall(it.first,it.second,size,scale,pan)}
  }
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(4.dp))}
  Text("BGR1 • ревизия ${state.geometry?.revision?:0} • стен ${state.geometry?.wallIds?.size?:0} • проёмов ${state.geometry?.openingIds?.size?:0} • объектов ${state.geometry?.catalogInstanceIds?.size?:0}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
 }
}
private fun DrawScope.drawPreviewWall(a:WorldPoint,b:WorldPoint,size:Size,scale:Float,pan:Offset){
 val dx=b.x-a.x;val dy=b.y-a.y;val len=hypot(dx,dy);if(len<1e-6)return
 val nx=-dy/len*.1;val ny=dx/len*.1
 val pts=listOf(WorldPoint(a.x+nx,a.y+ny),WorldPoint(b.x+nx,b.y+ny),WorldPoint(b.x-nx,b.y-ny),WorldPoint(a.x-nx,a.y-ny))
 val path=Path();pts.forEachIndexed{i,p->val s=worldToScreen(p,size,scale,pan);if(i==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)};path.close()
 drawPath(path,Color(0xFF1976D2).copy(alpha=.35f))
}
private fun DrawScope.drawPolygon(v:FloatArray,o:Int,size:Size,scale:Float,pan:Offset,color:Color){
 val p=Path();for(i in 0..3){val s=worldToScreen(WorldPoint(v[o+i*2].toDouble(),v[o+i*2+1].toDouble()),size,scale,pan);if(i==0)p.moveTo(s.x,s.y)else p.lineTo(s.x,s.y)};p.close();drawPath(p,color.copy(alpha=.72f))
}
private fun worldToScreen(p:WorldPoint,size:Size,scale:Float,pan:Offset)=Offset(size.width/2f+pan.x+p.x.toFloat()*scale,size.height/2f+pan.y-p.y.toFloat()*scale)
private fun screenToWorld(p:Offset,size:IntSize,scale:Float,pan:Offset)=WorldPoint(((p.x-size.width/2f-pan.x)/scale).toDouble(),((size.height/2f+pan.y-p.y)/scale).toDouble())
private fun snap(p:WorldPoint,g:com.bimob.app.core.nativebridge.RenderGeometryBuffer?):WorldPoint{
 val grid=WorldPoint(round(p.x*10)/10,round(p.y*10)/10);if(g==null)return grid
 var best=grid;var dBest=.16
 g.wallIds.indices.forEach{i->val s=wallSegment(g,i)?:return@forEach;listOf(s.first,s.second).forEach{q->val d=hypot(q.x-p.x,q.y-p.y);if(d<dBest){best=WorldPoint(q.x,q.y);dBest=d}}}
 return best
}
private data class AxisPoint(val x:Double,val y:Double)
private fun wallSegment(g:com.bimob.app.core.nativebridge.RenderGeometryBuffer,i:Int):Pair<AxisPoint,AxisPoint>?{
 val o=i*10;if(o+7>=g.walls.size)return null
 return AxisPoint((g.walls[o]+g.walls[o+6])/2.0,(g.walls[o+1]+g.walls[o+7])/2.0) to AxisPoint((g.walls[o+2]+g.walls[o+4])/2.0,(g.walls[o+3]+g.walls[o+5])/2.0)
}
private fun nearestWallId(p:Offset,g:com.bimob.app.core.nativebridge.RenderGeometryBuffer?,size:IntSize,scale:Float,pan:Offset):String?{
 if(g==null)return null;var best:String?=null;var dBest=18f
 g.wallIds.indices.forEach{i->val s=wallSegment(g,i)?:return@forEach
  val a=worldToScreen(WorldPoint(s.first.x,s.first.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan)
  val b=worldToScreen(WorldPoint(s.second.x,s.second.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan)
  val dx=b.x-a.x;val dy=b.y-a.y;val l=dx*dx+dy*dy;val t=if(l==0f)0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/l).coerceIn(0f,1f)
  val d=hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);if(d<dBest){dBest=d;best=g.wallIds[i]}
 }
 return best
}
