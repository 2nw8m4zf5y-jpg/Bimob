package com.bimob.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.*

private enum class EditorTool { WALL, SELECT, ROTATE, RESIZE, DOOR, WINDOW, CATALOG }
internal data class WorldPoint(val x:Double,val y:Double)
internal data class TransformPreviewState(val kind:EditorElementKind,val id:String,val dx:Double=0.0,val dy:Double=0.0,val rotationRadians:Double=0.0,val scaleFactor:Double=1.0)

@Composable
fun NativeEditorViewport(viewModel:NativeEditorViewModel,modifier:Modifier=Modifier) {
 val state by viewModel.state.collectAsState()
 val camera by viewModel.camera.collectAsState()
 val hitRadiusPx=with(LocalDensity.current){24.dp.toPx()}
 var tool by remember{mutableStateOf(EditorTool.WALL)}
 var preset by remember{mutableStateOf(CatalogPreset.TABLE)}
 var rotation by remember{mutableFloatStateOf(0f)}
 var preview by remember{mutableStateOf<Pair<WorldPoint,WorldPoint>?>(null)}
 var transformPreview by remember{mutableStateOf<TransformPreviewState?>(null)}
 Column(modifier) {
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).zIndex(1f)) {
   FilterChip(tool==EditorTool.WALL,{tool=EditorTool.WALL},{Text("Стена")})
   FilterChip(tool==EditorTool.SELECT,{tool=EditorTool.SELECT},{Text("Выбор")})
   FilterChip(tool==EditorTool.ROTATE,{tool=EditorTool.ROTATE},{Text("Поворот")})
   FilterChip(tool==EditorTool.RESIZE,{tool=EditorTool.RESIZE},{Text("Размер")})
   FilterChip(tool==EditorTool.DOOR,{tool=EditorTool.DOOR},{Text("Дверь")})
   FilterChip(tool==EditorTool.WINDOW,{tool=EditorTool.WINDOW},{Text("Окно")})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth().zIndex(1f)) {
   FilterChip(tool==EditorTool.CATALOG,{tool=EditorTool.CATALOG},{Text("Каталог")})
   CatalogPreset.all.forEach { item -> FilterChip(preset==item,{preset=item;tool=EditorTool.CATALOG},{Text(item.label)}) }
   TextButton(onClick={rotation=(rotation+PI.toFloat()/4f)%(2f*PI.toFloat())}){Text("↻ 45°")}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).zIndex(1f)) {
   Button(onClick=viewModel::undo,enabled=state.canUndo){Text("↶ Undo")}
   Button(onClick=viewModel::redo,enabled=state.canRedo){Text("↷ Redo")}
   Button(onClick=viewModel::deleteSelection,enabled=state.selection!=null,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Удалить")}
   state.selection?.let{Text("Выбрано: "+when(it.kind){EditorElementKind.WALL->"стена";EditorElementKind.OPENING->"проём";EditorElementKind.CATALOG->"объект"},style=MaterialTheme.typography.labelSmall)}
   state.selection?.let{selected->
    if(selected.kind!=EditorElementKind.OPENING)TextButton(onClick={viewModel.rotateElement(selected,PI/4)}){Text("⟳ 45°")}
    TextButton(onClick={viewModel.resizeElement(selected,1.1)}){Text("+10%")}
    TextButton(onClick={viewModel.resizeElement(selected,.9)}){Text("−10%")}
   }
  }
  Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds().zIndex(0f).background(Color.White)
   .pointerInput(Unit){
    awaitEachGesture {
     var oldCentroid:Offset?=null;var oldDistance=0f
     do {
      val event=awaitPointerEvent();val pressed=event.changes.filter{it.pressed}
      if(pressed.size>=2){val a=pressed[0].position;val b=pressed[1].position;val c=(a+b)/2f;val d=(a-b).getDistance()
       if(oldCentroid!=null&&oldDistance>0f&&d>0f){val current=viewModel.camera.value
        viewModel.setCamera(current.zoom*d/oldDistance,current.panX+c.x-oldCentroid!!.x,current.panY+c.y-oldCentroid!!.y)}
       oldCentroid=c;oldDistance=d;event.changes.forEach{it.consume()}
      }else{oldCentroid=null;oldDistance=0f}
     }while(event.changes.any{it.pressed})
    }
   }
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY,state.typedGeometry,preset,rotation,hitRadiusPx){
    detectTapGestures { screen ->
     if(!screen.x.isFinite()||!screen.y.isFinite())return@detectTapGestures
     val p=screenToWorld(screen,size,camera.zoom,Offset(camera.panX,camera.panY))
     if(!p.x.isFinite()||!p.y.isFinite())return@detectTapGestures
     when(tool){
      EditorTool.DOOR->viewModel.createOpening("DOOR",p.x,p.y)
      EditorTool.WINDOW->viewModel.createOpening("WINDOW",p.x,p.y)
      EditorTool.CATALOG->viewModel.createCatalog(preset,p.x,p.y,rotation.toDouble())
      else->{val hit=state.typedGeometry?.let{hitTestTypedGeometry(screen,it,size,camera.zoom,Offset(camera.panX,camera.panY),hitRadiusPx)};viewModel.selectElement(hit)}
     }
    }
   }
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY,state.typedGeometry,hitRadiusPx){
    var start:WorldPoint?=null;var last:WorldPoint?=null;var startScreen:Offset?=null;var movingSelection:EditorSelection?=null
    try{detectDragGestures(onDragStart={screen->
     if(!screen.x.isFinite()||!screen.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val raw=screenToWorld(screen,size,camera.zoom,Offset(camera.panX,camera.panY));if(!raw.x.isFinite()||!raw.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val world=if(tool==EditorTool.WALL)snap(raw,state.typedGeometry)else raw;start=world;last=world;startScreen=screen
     if(tool!=EditorTool.WALL)movingSelection=state.typedGeometry?.let{hitTestTypedGeometry(screen,it,size,camera.zoom,Offset(camera.panX,camera.panY),hitRadiusPx)}?.takeIf{tool!=EditorTool.ROTATE||it.kind!=EditorElementKind.OPENING}
    },onDrag={change,_->
     change.consume();val origin=start?:return@detectDragGestures;val originScreen=startScreen?:return@detectDragGestures
     if(!change.position.x.isFinite()||!change.position.y.isFinite())return@detectDragGestures
     val raw=screenToWorld(change.position,size,camera.zoom,Offset(camera.panX,camera.panY));if(!raw.x.isFinite()||!raw.y.isFinite())return@detectDragGestures
     val point=snap(raw,state.typedGeometry)
     when(tool){
      EditorTool.WALL->preview=origin to point
      EditorTool.SELECT->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,point.x-origin.x,point.y-origin.y)}
      EditorTool.ROTATE->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,rotationRadians=(change.position.x-originScreen.x)/120f*2*Math.PI)}
      EditorTool.RESIZE->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,scaleFactor=exp((change.position.x-originScreen.x).toDouble()/160.0).coerceIn(.2,5.0))}
      else->Unit
     }
     last=point
    },onDragEnd={
     val from=start;val to=last;val tp=transformPreview;val sel=movingSelection
     try{when(tool){
      EditorTool.WALL->if(from!=null&&to!=null)viewModel.createWall(from.x,from.y,to.x,to.y)
      EditorTool.SELECT->if(tp!=null&&(tp.dx!=0.0||tp.dy!=0.0))viewModel.moveElement(tp.kind,tp.id,tp.dx,tp.dy)
      EditorTool.ROTATE->if(sel!=null&&tp!=null&&tp.rotationRadians!=0.0)viewModel.rotateElement(sel,tp.rotationRadians)
      EditorTool.RESIZE->if(sel!=null&&tp!=null&&tp.scaleFactor!=1.0)viewModel.resizeElement(sel,tp.scaleFactor)
      else->Unit
     }}finally{preview=null;transformPreview=null;start=null;last=null;startScreen=null;movingSelection=null}
    },onDragCancel={preview=null;transformPreview=null;start=null;last=null;startScreen=null;movingSelection=null})}
    finally{preview=null;transformPreview=null;start=null;last=null;startScreen=null;movingSelection=null}
   }

  ) {
   val origin=Offset(size.width/2f+camera.panX,size.height/2f+camera.panY);val grid=(camera.zoom*.5f).coerceAtLeast(6f)
   var gx=origin.x%grid;while(gx<size.width){drawLine(Color(0xFFE8EDF2),Offset(gx,0f),Offset(gx,size.height));gx+=grid}
   var gy=origin.y%grid;while(gy<size.height){drawLine(Color(0xFFE8EDF2),Offset(0f,gy),Offset(size.width,gy));gy+=grid}
   drawLine(Color(0xFFCAD3DD),Offset(origin.x,0f),Offset(origin.x,size.height),1.5f)
   drawLine(Color(0xFFCAD3DD),Offset(0f,origin.y),Offset(size.width,origin.y),1.5f)
   val g=state.typedGeometry
   if(g!=null){
    g.walls.forEach{wall->
     val tp=transformPreview?.takeIf{it.kind==EditorElementKind.WALL&&it.id==wall.id}
     val color=if(state.selection==EditorSelection(EditorElementKind.WALL,wall.id))Color(0xFF1976D2)else Color(0xFF33465B)
     drawTypedPolygon(if(tp==null)wall.corners else transformWallPreview(wall,tp),size,camera.zoom,Offset(camera.panX,camera.panY),if(tp!=null)Color(0xFF1976D2)else color)
    }
    g.joins.forEach{join->drawTypedPolygon(join.corners,size,camera.zoom,Offset(camera.panX,camera.panY),Color(0xFF8BA3B8))}
    g.openings.forEach{item->val wall=g.walls.firstOrNull{it.id==item.hostWallId}?:return@forEach
     val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val len=hypot(dx,dy);if(len<1e-6)return@forEach
     val ux=dx/len;val uy=dy/len;val tp=transformPreview?.takeIf{it.kind==EditorElementKind.OPENING&&it.id==item.id};val width=item.width*(tp?.scaleFactor?:1.0);val half=width/2
     val movedAlong=tp?.let{it.dx*ux+it.dy*uy}?:0.0;val newOffset=(item.offsetAlongWall+item.width/2-width/2).coerceIn(0.0,(len-width).coerceAtLeast(0.0));val along=movedAlong+newOffset+width/2-(item.offsetAlongWall+item.width/2)
     val cx=item.center.x+ux*along;val cy=item.center.y+uy*along
     val a=worldToScreen(WorldPoint(cx-ux*half,cy-uy*half),size,camera.zoom,Offset(camera.panX,camera.panY));val b=worldToScreen(WorldPoint(cx+ux*half,cy+uy*half),size,camera.zoom,Offset(camera.panX,camera.panY))
     drawLine(Color.White,a,b,strokeWidth=10f);val selected=state.selection==EditorSelection(EditorElementKind.OPENING,item.id);val color=if(selected)Color(0xFF1976D2)else Color(0xFF008C95)
     if(item.kind=="DOOR"){drawLine(color,a,b,2f);val radius=(item.width*camera.zoom.toDouble()).coerceAtLeast(4.0).toFloat();drawArc(color,0f,90f,false,Offset(a.x-radius,a.y-radius),Size(radius*2f,radius*2f),style=Stroke(1.5f))}
     else{drawLine(color,a,b,3f);drawLine(color,Offset(a.x-uy.toFloat()*4,a.y-ux.toFloat()*4),Offset(b.x-uy.toFloat()*4,b.y-ux.toFloat()*4),2f)}
    }
    g.catalog.forEach{item->
     val tp=transformPreview?.takeIf{it.kind==EditorElementKind.CATALOG&&it.id==item.id};val scale=tp?.scaleFactor?:1.0;val hw=item.width*scale/2;val hd=item.depth*scale/2;val angle=item.rotationRadians+(tp?.rotationRadians?:0.0);val cx=item.center.x+(tp?.dx?:0.0);val cy=item.center.y+(tp?.dy?:0.0)
     val corners=listOf(-hw to -hd,hw to -hd,hw to hd,-hw to hd).map{(x,y)->Vec2(cx+x*cos(angle)-y*sin(angle),cy+x*sin(angle)+y*cos(angle))}
     val path=Path();corners.forEachIndexed{k,v->val s=worldToScreen(WorldPoint(v.x,v.y),size,camera.zoom,Offset(camera.panX,camera.panY));if(k==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)};path.close()
     val selected=state.selection==EditorSelection(EditorElementKind.CATALOG,item.id);drawPath(path,if(selected)Color(0x331976D2)else Color(0x223A789A));drawPath(path,if(selected)Color(0xFF1976D2)else Color(0xFF3A789A),style=Stroke(2f))
    }
   }
   preview?.let{drawPreviewWall(it.first,it.second,size,camera.zoom,Offset(camera.panX,camera.panY))}
  }
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(4.dp))}
  Text("BGR1 • ревизия ${state.geometry?.revision?:0} • стен ${state.geometry?.wallIds?.size?:0} • проёмов ${state.geometry?.openingIds?.size?:0} • объектов ${state.geometry?.catalogInstanceIds?.size?:0}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
 }
}
private fun DrawScope.drawPreviewWall(a:WorldPoint,b:WorldPoint,size:Size,scale:Float,pan:Offset){
 if(!a.x.isFinite()||!a.y.isFinite()||!b.x.isFinite()||!b.y.isFinite()||!scale.isFinite()||scale<=0f)return
 val dx=b.x-a.x;val dy=b.y-a.y;val len=hypot(dx,dy);if(!len.isFinite()||len<1e-6)return
 val nx=-dy/len*.1;val ny=dx/len*.1
 val pts=listOf(WorldPoint(a.x+nx,a.y+ny),WorldPoint(b.x+nx,b.y+ny),WorldPoint(b.x-nx,b.y-ny),WorldPoint(a.x-nx,a.y-ny))
 val path=Path();pts.forEachIndexed{i,p->val s=worldToScreen(p,size,scale,pan);if(i==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)};path.close()
 drawPath(path,Color(0xFF1976D2).copy(alpha=.35f))
}
private fun transformWallPreview(wall:WallItem,tp:TransformPreviewState):List<Vec2>{
 val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val length=hypot(dx,dy);if(length<=1e-9)return wall.corners
 val ux=dx/length;val uy=dy/length;val vx=-uy;val vy=ux;val cx=(wall.axisStart.x+wall.axisEnd.x)/2;val cy=(wall.axisStart.y+wall.axisEnd.y)/2;val co=cos(tp.rotationRadians);val si=sin(tp.rotationRadians)
 return wall.corners.map{p->val along=(p.x-wall.axisStart.x)*ux+(p.y-wall.axisStart.y)*uy;val across=(p.x-wall.axisStart.x)*vx+(p.y-wall.axisStart.y)*vy;val x=wall.axisStart.x+ux*along*tp.scaleFactor+vx*across;val y=wall.axisStart.y+uy*along*tp.scaleFactor+vy*across;Vec2(cx+(x-cx)*co-(y-cy)*si+tp.dx,cy+(x-cx)*si+(y-cy)*co+tp.dy)}
}
private fun DrawScope.drawTypedPolygon(vertices:List<Vec2>,size:Size,scale:Float,pan:Offset,color:Color){
 if(vertices.size<3||!scale.isFinite()||scale<=0f)return
 val p=Path();vertices.forEachIndexed{i,v->if(!v.x.isFinite()||!v.y.isFinite())return;val s=worldToScreen(WorldPoint(v.x,v.y),size,scale,pan);if(i==0)p.moveTo(s.x,s.y)else p.lineTo(s.x,s.y)};p.close();drawPath(p,color.copy(alpha=.72f))
}
internal fun worldToScreen(p:WorldPoint,size:Size,scale:Float,pan:Offset):Offset {
 if(!p.x.isFinite()||!p.y.isFinite()||!scale.isFinite()||scale<=0f||!pan.x.isFinite()||!pan.y.isFinite())return Offset.Zero
 val x=size.width/2.0+pan.x.toDouble()+p.x*scale.toDouble()
 val y=size.height/2.0+pan.y.toDouble()-p.y*scale.toDouble()
 if(!x.isFinite()||!y.isFinite())return Offset.Zero
 return Offset(x.coerceIn(-1.0e7,1.0e7).toFloat(),y.coerceIn(-1.0e7,1.0e7).toFloat())
}
internal fun screenToWorld(p:Offset,size:IntSize,scale:Float,pan:Offset):WorldPoint {
 if(!p.x.isFinite()||!p.y.isFinite()||!scale.isFinite()||scale<=0f||!pan.x.isFinite()||!pan.y.isFinite())return WorldPoint(Double.NaN,Double.NaN)
 val x=(p.x.toDouble()-size.width/2.0-pan.x.toDouble())/scale.toDouble()
 val y=(size.height/2.0+pan.y.toDouble()-p.y.toDouble())/scale.toDouble()
 return if(x.isFinite()&&y.isFinite())WorldPoint(x,y) else WorldPoint(Double.NaN,Double.NaN)
}
private fun snap(p:WorldPoint,g:TypedGeometry?):WorldPoint{
 val grid=WorldPoint(round(p.x*10)/10,round(p.y*10)/10);if(g==null)return grid
 var best=grid;var dBest=.16
 g.walls.forEach{wall->listOf(wall.axisStart,wall.axisEnd).forEach{q->val d=hypot(q.x-p.x,q.y-p.y);if(d<dBest){best=WorldPoint(q.x,q.y);dBest=d}}}
 return best
}
private fun nearestWallId(p:Offset,g:TypedGeometry?,size:IntSize,scale:Float,pan:Offset):String?{
 if(g==null)return null;var best:String?=null;var dBest=24f
 g.walls.forEach{wall->val a=worldToScreen(WorldPoint(wall.axisStart.x,wall.axisStart.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan);val b=worldToScreen(WorldPoint(wall.axisEnd.x,wall.axisEnd.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan)
  val dx=b.x-a.x;val dy=b.y-a.y;val l=dx*dx+dy*dy;val t=if(l==0f)0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/l).coerceIn(0f,1f);val d=hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);if(d<dBest){dBest=d;best=wall.id}
 }
 return best
}
