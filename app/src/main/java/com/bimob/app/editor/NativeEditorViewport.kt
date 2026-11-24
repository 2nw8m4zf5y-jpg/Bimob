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
import kotlin.math.*

private enum class EditorTool { WALL, SELECT, DOOR, WINDOW, CATALOG }
internal data class WorldPoint(val x:Double,val y:Double)
internal data class TransformPreviewState(val kind:EditorElementKind,val id:String,val deltaX:Double,val deltaY:Double)

@Composable
fun NativeEditorViewport(viewModel:NativeEditorViewModel,modifier:Modifier=Modifier) {
 val state by viewModel.state.collectAsState()
 val camera by viewModel.camera.collectAsState()
 var tool by remember{mutableStateOf(EditorTool.WALL)}
 var preset by remember{mutableStateOf(CatalogPreset.TABLE)}
 var rotation by remember{mutableFloatStateOf(0f)}
 var preview by remember{mutableStateOf<Pair<WorldPoint,WorldPoint>?>(null)}
 var transformPreview by remember{mutableStateOf<TransformPreviewState?>(null)}
 Column(modifier) {
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth().zIndex(1f)) {
   FilterChip(tool==EditorTool.WALL,{tool=EditorTool.WALL},{Text("Стена")})
   FilterChip(tool==EditorTool.SELECT,{tool=EditorTool.SELECT},{Text("Выбор")})
   FilterChip(tool==EditorTool.DOOR,{tool=EditorTool.DOOR},{Text("Дверь")})
   FilterChip(tool==EditorTool.WINDOW,{tool=EditorTool.WINDOW},{Text("Окно")})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth().zIndex(1f)) {
   FilterChip(tool==EditorTool.CATALOG,{tool=EditorTool.CATALOG},{Text("Каталог")})
   CatalogPreset.all.forEach { item -> FilterChip(preset==item,{preset=item;tool=EditorTool.CATALOG},{Text(item.label)}) }
   TextButton(onClick={rotation=(rotation+PI.toFloat()/4f)%(2f*PI.toFloat())}){Text("↻ 45°")}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth().zIndex(1f)) {
   Button(onClick=viewModel::undo,enabled=state.canUndo){Text("↶ Undo")}
   Button(onClick=viewModel::redo,enabled=state.canRedo){Text("↷ Redo")}
   Button(onClick=viewModel::deleteSelection,enabled=state.selection!=null,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Удалить")}
   state.selection?.let{Text("Выбрано: "+when(it.kind){EditorElementKind.WALL->"стена";EditorElementKind.OPENING->"проём";EditorElementKind.CATALOG->"объект"},style=MaterialTheme.typography.labelSmall)}
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
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY,state.typedGeometry,preset,rotation){
    detectTapGestures { screen ->
     if(!screen.x.isFinite()||!screen.y.isFinite())return@detectTapGestures
     val p=screenToWorld(screen,size,camera.zoom,Offset(camera.panX,camera.panY))
     if(!p.x.isFinite()||!p.y.isFinite())return@detectTapGestures
     when(tool){
      EditorTool.DOOR->viewModel.createOpening("DOOR",p.x,p.y)
      EditorTool.WINDOW->viewModel.createOpening("WINDOW",p.x,p.y)
      EditorTool.CATALOG->viewModel.createCatalog(preset,p.x,p.y,rotation.toDouble())
      else->viewModel.selectAt(p.x,p.y)
     }
    }
   }
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY){
    var start:WorldPoint?=null;var last:WorldPoint?=null;var gesture:String?=null;var movingId:String?=null;var settled=false
    try{detectDragGestures(onDragStart={screen->
     if(!screen.x.isFinite()||!screen.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val raw=screenToWorld(screen,size,camera.zoom,Offset(camera.panX,camera.panY))
     if(!raw.x.isFinite()||!raw.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val world=snap(raw,state.typedGeometry);start=world;last=world
     if(tool==EditorTool.SELECT){movingId=nearestWallId(screen,state.typedGeometry,size,camera.zoom,Offset(camera.panX,camera.panY))}
    },onDrag={change,_->
     change.consume();val a=start?:return@detectDragGestures
     if(!change.position.x.isFinite()||!change.position.y.isFinite())return@detectDragGestures
     val raw=screenToWorld(change.position,size,camera.zoom,Offset(camera.panX,camera.panY))
     if(!raw.x.isFinite()||!raw.y.isFinite())return@detectDragGestures
     val p=snap(raw,state.typedGeometry);val prev=last?:a
     if(tool==EditorTool.WALL)preview=a to p
     else if(tool==EditorTool.SELECT){movingId?.let{id->transformPreview=TransformPreviewState(EditorElementKind.WALL,id,p.x-a.x,p.y-a.y)}}
     last=p
    },onDragEnd={
     val a=start;val b=last;val tp=transformPreview
     try{if(tool==EditorTool.WALL&&a!=null&&b!=null)viewModel.createWall(a.x,a.y,b.x,b.y)
      else if(tp!=null&&tp.deltaX.isFinite()&&tp.deltaY.isFinite()&&(tp.deltaX!=0.0||tp.deltaY!=0.0)){viewModel.moveWall(tp.id,tp.deltaX,tp.deltaY)}
     }finally{preview=null;transformPreview=null;start=null;last=null;movingId=null;settled=true}
    },onDragCancel={preview=null;transformPreview=null;start=null;last=null;movingId=null;settled=true

