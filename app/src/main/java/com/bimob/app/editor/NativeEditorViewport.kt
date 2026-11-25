package com.bimob.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.*
import kotlinx.coroutines.delay

private enum class EditorTool { WALL, SELECT, ROTATE, RESIZE, CATALOG }
internal data class WorldPoint(val x:Double,val y:Double)
internal enum class SnapKind { ENDPOINT, EDGE, ORTHO, GRID, NONE }
internal data class SnapResult(val point:WorldPoint,val kind:SnapKind,val target:WorldPoint?=null)
internal data class EndpointPreview(val wallId:String,val moveStart:Boolean,val point:WorldPoint)
internal data class TransformPreviewState(val kind:EditorElementKind,val id:String,val dx:Double=0.0,val dy:Double=0.0,val rotationRadians:Double=0.0,val scaleFactor:Double=1.0)

@Composable
fun NativeEditorViewport(viewModel:NativeEditorViewModel,modifier:Modifier=Modifier,projectTitle:String="Проект",onProjects:()->Unit={},onSave:()->Unit={},onExport:()->Unit={},onImport:()->Unit={}) {
 val state by viewModel.state.collectAsState()
 val camera by viewModel.camera.collectAsState()
 val hitRadiusPx=with(LocalDensity.current){24.dp.toPx()}
 val catalogGripHitRadiusPx=with(LocalDensity.current){22.dp.toPx()}
 val catalogGripSizePx=with(LocalDensity.current){8.dp.toPx()}
 val catalogGripCenterSizePx=with(LocalDensity.current){3.dp.toPx()}
 var tool by remember{mutableStateOf(EditorTool.SELECT)}
 var preset by remember{mutableStateOf(CatalogPreset.TABLE)}
 var preview by remember{mutableStateOf<Pair<WorldPoint,WorldPoint>?>(null)}
 var transformPreview by remember{mutableStateOf<TransformPreviewState?>(null)}
 var catalogGripPreview by remember{mutableStateOf<Pair<String,CatalogGripPreview>?>(null)}
 var endpointPreview by remember{mutableStateOf<EndpointPreview?>(null)}
 var snapPreview by remember{mutableStateOf<SnapResult?>(null)}
 var wallStartSnap by remember{mutableStateOf<SnapResult?>(null)}
 var fingerScreen by remember{mutableStateOf<Offset?>(null)}
 var hudWorldPoint by remember{mutableStateOf(WorldPoint(0.0,0.0))}
 var orthoLocked by remember{mutableStateOf(false)}
 var snapEnabled by remember{mutableStateOf(true)}
 var activeTooltip by remember{mutableStateOf<String?>(null)}
 var tooltipNonce by remember{mutableStateOf(0)}
 val showTooltip:(String)->Unit={label->activeTooltip=label;tooltipNonce++}
 LaunchedEffect(activeTooltip,tooltipNonce){if(activeTooltip!=null){delay(1000L);activeTooltip=null}}
 val crosshairOffsetPx=with(LocalDensity.current){48.dp.toPx()}
 val snapThresholdPx=with(LocalDensity.current){22.dp.toPx()}
 var showCatalogPicker by remember{mutableStateOf(false)}
 var showProperties by remember{mutableStateOf(false)}
 var catalogCategory by remember{mutableStateOf("openings")}
 var homeMenuExpanded by remember{mutableStateOf(false)}
 var editingProperty by remember{mutableStateOf<Pair<String,String>?>(null)}
 var ribbonTab by remember{mutableStateOf("Архитектура")}
 var ribbonCollapsed by remember{mutableStateOf(false)}
 var editValue by remember{mutableStateOf("")}
 LaunchedEffect(state.selection){if(state.selection!=null)showProperties=true}
 Column(modifier) {
  Column(Modifier.fillMaxWidth().background(Color(0xFFE8ECF0)).zIndex(2f)) {
   Row(Modifier.fillMaxWidth().height(32.dp).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).padding(horizontal=2.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
    Box {
     Surface(onClick={homeMenuExpanded=true},shape=RectangleShape,color=Color(0xFF1E6BB8),modifier=Modifier.height(32.dp)) {
      Box(Modifier.padding(horizontal=12.dp),contentAlignment=Alignment.Center){Text("Файл",fontSize=12.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=Color.White)}
     }
     DropdownMenu(expanded=homeMenuExpanded,onDismissRequest={homeMenuExpanded=false},shape=RectangleShape) {
      DropdownMenuItem(text={Text("Проекты")},onClick={homeMenuExpanded=false;onProjects()})
      DropdownMenuItem(text={Text("Сохранить")},onClick={homeMenuExpanded=false;onSave()})
      DropdownMenuItem(text={Text("Экспорт JSON")},onClick={homeMenuExpanded=false;onExport()})
      DropdownMenuItem(text={Text("Импорт JSON")},onClick={homeMenuExpanded=false;onImport()})
     }
    }
    CursorQuickButton(tool==EditorTool.SELECT,showTooltip){tool=EditorTool.SELECT}
    RibbonQuickButton("↶","Отменить",state.canUndo,viewModel::undo,showTooltip)
    RibbonQuickButton("↷","Повторить",state.canRedo,viewModel::redo,showTooltip)
    CompactActionButton("×","Удалить",enabled=state.selection!=null,danger=true,tooltip=showTooltip){viewModel.deleteSelection()}
   }
   Row(Modifier.fillMaxWidth().height(30.dp).background(Color(0xFFE8ECF0)).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
    listOf("Архитектура","Конструкция","Системы","Каталог","Правка").forEach{tab->
     val active=ribbonTab==tab
     Box(Modifier.height(30.dp).background(if(active)Color(0xFFF5F6F8)else Color(0xFFE8ECF0)).drawBehind{
      val edge=1.dp.toPx()
      if(active){
       drawLine(Color(0xFF1E6BB8),Offset(0f,0f),Offset(size.width,0f),2.dp.toPx())
       drawLine(Color(0xFFCBD5E1),Offset(0f,0f),Offset(0f,size.height),edge)
       drawLine(Color(0xFFCBD5E1),Offset(size.width-edge,0f),Offset(size.width-edge,size.height),edge)
      }else drawLine(Color(0xFFCBD5E1),Offset(0f,size.height-edge),Offset(size.width,size.height-edge),edge)
     }.clickable{if(active)ribbonCollapsed=!ribbonCollapsed else{ribbonTab=tab;ribbonCollapsed=false}}.padding(horizontal=12.dp),contentAlignment=Alignment.Center){
      Text(tab,fontSize=11.sp,fontWeight=if(active)androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,color=if(active)Color(0xFF0F172A)else Color(0xFF475569),maxLines=1)
     }
    }
   }
    TextButton(onClick={ribbonCollapsed=!ribbonCollapsed},modifier=Modifier.width(24.dp),contentPadding=PaddingValues(0.dp)){Text(if(ribbonCollapsed)"▼" else "▲",fontSize=11.sp,color=Color(0xFF0F172A))}
   }
   if(!ribbonCollapsed)Row(Modifier.fillMaxWidth().height(68.dp).background(Color(0xFFF5F6F8)).horizontalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(horizontal=3.dp).borderBottomEngineering(),verticalAlignment=Alignment.CenterVertically) {
    when(ribbonTab){
     "Архитектура"->RibbonGroup(listOf(RibbonItem("╱","Стена",tool==EditorTool.WALL){tool=EditorTool.WALL}),showTooltip)
     "Конструкция"->RibbonGroup(listOf(RibbonItem("╱","Стена",tool==EditorTool.WALL){tool=EditorTool.WALL},RibbonItem("⌑","Узлы",highlightJoins){tool=EditorTool.SELECT;highlightJoins=!highlightJoins}),showTooltip)
     "Системы"->Unit
     "Каталог"->RibbonGroup(listOf(RibbonItem("▦","Вставка",tool==EditorTool.CATALOG){tool=EditorTool.CATALOG;showCatalogPicker=true}),showTooltip)
     else->RibbonGroup(listOf(RibbonItem("↻","Поворот",tool==EditorTool.ROTATE){tool=EditorTool.ROTATE},RibbonItem("⌖","Привязка",snapEnabled){snapEnabled=!snapEnabled},RibbonItem("90°","Орто",orthoLocked){orthoLocked=!orthoLocked}),showTooltip)
    }
   }
  }
  if(showCatalogPicker) AlertDialog(
   onDismissRequest={showCatalogPicker=false},
   title={Text("Каталог")},
   text={Column(Modifier.heightIn(max=420.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
     listOf("openings" to "Двери и окна","furniture" to "Мебель","plumbing" to "Сантехника").forEach{(category,label)->
      TextButton(onClick={catalogCategory=category},modifier=Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelSmall,color=if(catalogCategory==category)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
     }
    }
    HorizontalDivider()
    Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
     CatalogPreset.all.filter{it.category==catalogCategory}.forEach{item->
      TextButton(onClick={preset=item;tool=EditorTool.CATALOG;showCatalogPicker=false},modifier=Modifier.fillMaxWidth()){
       Column(Modifier.fillMaxWidth()){
        Text(item.label,style=MaterialTheme.typography.bodyMedium)
        val desc=if(item.category=="openings")"${item.width} м × ${item.height} м" else "${item.width} × ${item.depth} × ${item.height} м"
        Text(desc,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
      }
     }
    }
   }},
   confirmButton={TextButton(onClick={showCatalogPicker=false}){Text("Закрыть")}}
  )
  Box(Modifier.fillMaxWidth().weight(1f)) {
   Canvas(Modifier.fillMaxSize().clipToBounds().zIndex(0f).background(Color.White)
    .pointerInput(Unit){
    awaitEachGesture {
     var oldCentroid:Offset?=null;var oldDistance=0f
     do {
      val event=awaitPointerEvent();val pressed=event.changes.filter{it.pressed}
      if(pressed.size>=2){val a=pressed[0].position;val b=pressed[1].position;val c=(a+b)/2f;val d=(a-b).getDistance()
       if(oldCentroid!=null&&oldDistance>0f&&d>0f){val current=viewModel.camera.value
        val focused=zoomAroundCentroid(oldCentroid!!,c,d/oldDistance,Size(size.width.toFloat(),size.height.toFloat()),FocusZoomCamera(current.zoom,current.panX,current.panY))
        viewModel.setCamera(focused.zoom,focused.panX,focused.panY)}
       oldCentroid=c;oldDistance=d;event.changes.forEach{it.consume()}
      }else{oldCentroid=null;oldDistance=0f}
     }while(event.changes.any{it.pressed})
    }
   }
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY,state.typedGeometry,preset,hitRadiusPx){
    detectTapGestures { screen ->
     if(!screen.x.isFinite()||!screen.y.isFinite())return@detectTapGestures
     val p=screenToWorld(screen,size,camera.zoom,Offset(camera.panX,camera.panY))
     if(!p.x.isFinite()||!p.y.isFinite())return@detectTapGestures
     when(tool){
      EditorTool.CATALOG->{if(preset.category=="openings"){val kind=if(preset.assetId.contains("door"))"DOOR" else "WINDOW";viewModel.createOpening(kind,p.x,p.y,preset.width,preset.height,preset.depth)}else viewModel.createCatalog(preset,p.x,p.y,0.0)}
      else->{val hit=state.typedGeometry?.let{hitTestTypedGeometry(screen,it,size,camera.zoom,Offset(camera.panX,camera.panY),hitRadiusPx)};viewModel.selectElement(hit)}
     }
    }
   }
   .pointerInput(tool,camera.zoom,camera.panX,camera.panY,state.typedGeometry,hitRadiusPx){
    var start:WorldPoint?=null;var last:WorldPoint?=null;var startScreen:Offset?=null;var movingSelection:EditorSelection?=null;var endpointDrag:EndpointPreview?=null;var catalogGripDrag:Pair<CatalogItem,CatalogGrip>?=null;var movingWallSnapAnchor:WorldPoint?=null
    try{detectDragGestures(onDragStart={screen->
     if(!screen.x.isFinite()||!screen.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val canvasOffset=Offset(camera.panX,camera.panY)
     val cross=if(tool==EditorTool.WALL)Offset(screen.x,screen.y-crosshairOffsetPx)else screen
     val selectedWall=state.selection?.takeIf{it.kind==EditorElementKind.WALL}?.let{s->state.typedGeometry?.walls?.firstOrNull{it.id==s.id}}
     endpointDrag=if(tool==EditorTool.SELECT&&selectedWall!=null)hitWallEndpoint(screen,selectedWall,size,camera.zoom,canvasOffset,hitRadiusPx)else null
     val selectedCatalog=state.selection?.takeIf{it.kind==EditorElementKind.CATALOG}?.let{s->state.typedGeometry?.catalog?.firstOrNull{it.id==s.id}}
     val gripProbe=Offset(screen.x,screen.y-crosshairOffsetPx)
     catalogGripDrag=if(tool==EditorTool.SELECT&&endpointDrag==null&&selectedCatalog!=null)listOf(CatalogGrip.WIDTH_POSITIVE,CatalogGrip.WIDTH_NEGATIVE,CatalogGrip.DEPTH_POSITIVE,CatalogGrip.DEPTH_NEGATIVE).firstOrNull{grip->val p=catalogGripPoint(selectedCatalog,grip);val sc=worldToScreen(WorldPoint(p.x,p.y),Size(size.width.toFloat(),size.height.toFloat()),camera.zoom,canvasOffset);hypot(gripProbe.x-sc.x,gripProbe.y-sc.y)<=catalogGripHitRadiusPx}?.let{selectedCatalog to it}else null
     val raw=screenToWorld(if(catalogGripDrag!=null)gripProbe else cross,size,camera.zoom,canvasOffset);if(!raw.x.isFinite()||!raw.y.isFinite()){start=null;last=null;return@detectDragGestures}
     val wallAnchor=endpointDrag?.let{ep->selectedWall?.let{if(ep.moveStart)it.axisEnd else it.axisStart}}?.let{WorldPoint(it.x,it.y)} ?:catalogGripDrag?.let{val p=catalogGripFixedPoint(it.first,it.second);WorldPoint(p.x,p.y)} 
     val snap=if(tool==EditorTool.WALL||endpointDrag!=null||catalogGripDrag!=null)if(snapEnabled)snapPoint(raw,wallAnchor,state.typedGeometry,size,camera.zoom,canvasOffset,snapThresholdPx,orthoLocked,endpointDrag?.wallId)else SnapResult(raw,SnapKind.NONE)else SnapResult(raw,SnapKind.NONE)
     val world=endpointDrag?.point?:snap.point;start=world;last=world;startScreen=screen;fingerScreen=screen;hudWorldPoint=world
     snapPreview=snap
     wallStartSnap=if(tool==EditorTool.WALL)snap else null
     endpointPreview=endpointDrag?.copy(point=world)
     if(tool!=EditorTool.WALL&&endpointDrag==null&&catalogGripDrag==null)movingSelection=state.typedGeometry?.let{hitTestTypedGeometry(screen,it,size,camera.zoom,canvasOffset,hitRadiusPx)}?.takeIf{tool!=EditorTool.ROTATE||it.kind!=EditorElementKind.OPENING}
     movingWallSnapAnchor=movingSelection?.takeIf{tool==EditorTool.SELECT&&it.kind==EditorElementKind.WALL}?.let{sel->state.typedGeometry?.walls?.firstOrNull{it.id==sel.id}?.let{WorldPoint(it.axisStart.x,it.axisStart.y)}}
    },onDrag={change,_->
     change.consume();val origin=start?:return@detectDragGestures;val originScreen=startScreen?:return@detectDragGestures
     if(!change.position.x.isFinite()||!change.position.y.isFinite())return@detectDragGestures
     fingerScreen=change.position
     val canvasOffset=Offset(camera.panX,camera.panY)
     val cross=if(tool==EditorTool.WALL||endpointDrag!=null||catalogGripDrag!=null)Offset(change.position.x,change.position.y-crosshairOffsetPx)else change.position
     val raw=screenToWorld(cross,size,camera.zoom,canvasOffset);if(!raw.x.isFinite()||!raw.y.isFinite())return@detectDragGestures
     val anchor=endpointDrag?.let{ep->state.typedGeometry?.walls?.firstOrNull{it.id==ep.wallId}?.let{if(ep.moveStart)it.axisEnd else it.axisStart}}?.let{WorldPoint(it.x,it.y)}?:catalogGripDrag?.let{val p=catalogGripFixedPoint(it.first,it.second);WorldPoint(p.x,p.y)}?:origin
     val wallMoveAnchor=movingWallSnapAnchor
     val wallMoveCandidate=wallMoveAnchor?.let{WorldPoint(it.x+raw.x-origin.x,it.y+raw.y-origin.y)}
     val snapped=when{
      tool==EditorTool.SELECT&&wallMoveAnchor!=null&&wallMoveCandidate!=null->if(snapEnabled)snapPoint(wallMoveCandidate,wallMoveAnchor,state.typedGeometry,size,camera.zoom,canvasOffset,snapThresholdPx,orthoLocked,movingSelection?.id)else SnapResult(wallMoveCandidate,SnapKind.NONE)
      tool==EditorTool.WALL||endpointDrag!=null||catalogGripDrag!=null->if(snapEnabled)snapPoint(raw,anchor,state.typedGeometry,size,camera.zoom,canvasOffset,snapThresholdPx,orthoLocked,endpointDrag?.wallId)else SnapResult(raw,SnapKind.NONE)
      else->SnapResult(raw,SnapKind.NONE)
     }
     val point=if(tool==EditorTool.SELECT&&wallMoveAnchor!=null)WorldPoint(origin.x+snapped.point.x-wallMoveAnchor.x,origin.y+snapped.point.y-wallMoveAnchor.y)else snapped.point
     hudWorldPoint=if(tool==EditorTool.SELECT&&wallMoveAnchor!=null)snapped.point else point;snapPreview=snapped
     when{
      catalogGripDrag!=null->{val (item,grip)=catalogGripDrag!!;catalogGripPreview=resizeCatalogGrip(item,grip,Vec2(point.x,point.y))?.let{item.id to it}}
      endpointDrag!=null->endpointPreview=endpointDrag!!.copy(point=point)
      tool==EditorTool.WALL->preview=origin to point
      tool==EditorTool.SELECT->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,point.x-origin.x,point.y-origin.y)}
      tool==EditorTool.ROTATE->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,rotationRadians=(change.position.x-originScreen.x)/120f*2*Math.PI)}
      tool==EditorTool.RESIZE->movingSelection?.let{sel->transformPreview=TransformPreviewState(sel.kind,sel.id,scaleFactor=exp((change.position.x-originScreen.x).toDouble()/160.0).coerceIn(.2,5.0))}
     }
     last=point
    },onDragEnd={
     val from=start;val to=last;val tp=transformPreview;val sel=movingSelection;val ep=endpointPreview?:endpointDrag
     try{when{
      catalogGripDrag!=null&&catalogGripPreview!=null->{val item=catalogGripDrag!!.first;viewModel.resizeCatalogGrip(item,catalogGripPreview!!.second)}
      ep!=null&&from!=null&&to!=null&&hypot(to.x-from.x,to.y-from.y)>1e-6->viewModel.resizeWallEndpoint(ep.wallId,ep.moveStart,to.x,to.y)
      ep!=null->Unit
      tool==EditorTool.WALL->if(from!=null&&to!=null)viewModel.createWall(from.x,from.y,to.x,to.y)
      tool==EditorTool.SELECT->if(tp!=null&&(tp.dx!=0.0||tp.dy!=0.0))viewModel.moveElement(tp.kind,tp.id,tp.dx,tp.dy)
      tool==EditorTool.ROTATE->if(sel!=null&&tp!=null&&tp.rotationRadians!=0.0)viewModel.rotateElement(sel,tp.rotationRadians)
      tool==EditorTool.RESIZE->if(sel!=null&&tp!=null&&tp.scaleFactor!=1.0)viewModel.resizeElement(sel,tp.scaleFactor)
     }}finally{preview=null;transformPreview=null;catalogGripPreview=null;endpointPreview=null;snapPreview=null;wallStartSnap=null;fingerScreen=null;start=null;last=null;startScreen=null;movingSelection=null;movingWallSnapAnchor=null;endpointDrag=null;catalogGripDrag=null}
    },onDragCancel={preview=null;transformPreview=null;catalogGripPreview=null;endpointPreview=null;snapPreview=null;wallStartSnap=null;fingerScreen=null;start=null;last=null;startScreen=null;movingSelection=null;movingWallSnapAnchor=null;endpointDrag=null;catalogGripDrag=null})}
    finally{preview=null;transformPreview=null;endpointPreview=null;snapPreview=null;fingerScreen=null;start=null;last=null;startScreen=null;movingSelection=null;endpointDrag=null}
   }

   ) {
   val origin=Offset(size.width/2f+camera.panX,size.height/2f+camera.panY);val minor=(camera.zoom*.1f).coerceAtLeast(3f);val major=(camera.zoom*.5f).coerceAtLeast(6f)
   var gx=origin.x%minor;while(gx<size.width){drawLine(Color(0xFFE8EEF5),Offset(gx,0f),Offset(gx,size.height),1.dp.toPx());gx+=minor}
   var gy=origin.y%minor;while(gy<size.height){drawLine(Color(0xFFE2E8F0),Offset(0f,gy),Offset(size.width,gy),1.dp.toPx());gy+=minor}
   gx=origin.x%major;while(gx<size.width){drawLine(Color(0xFFCBD5E1),Offset(gx,0f),Offset(gx,size.height),1.dp.toPx());gx+=major}
   gy=origin.y%major;while(gy<size.height){drawLine(Color(0xFFCBD5E1),Offset(0f,gy),Offset(size.width,gy),1.dp.toPx());gy+=major}
   drawLine(Color(0xFF94A3B8),Offset(origin.x,0f),Offset(origin.x,size.height),1.5f)
   drawLine(Color(0xFF94A3B8),Offset(0f,origin.y),Offset(size.width,origin.y),1.5f)
   val g=state.typedGeometry
   if(g!=null){
    g.walls.forEach{wall->
     val tp=transformPreview?.takeIf{it.kind==EditorElementKind.WALL&&it.id==wall.id}
     val color=if(state.selection==EditorSelection(EditorElementKind.WALL,wall.id))Color(0xFF1E6BB8)else Color(0xFF0A0A0A)
     val ep=endpointPreview?.takeIf{it.wallId==wall.id}
     val corners=when{ep!=null->wallEndpointPreviewCorners(wall,ep.moveStart,ep.point);tp!=null->transformWallPreview(wall,tp);else->wall.corners}
     drawTypedPolygon(corners,size,camera.zoom,Offset(camera.panX,camera.panY),if(tp!=null||ep!=null)Color(0xFF1E6BB8)else color)
     if(state.selection==EditorSelection(EditorElementKind.WALL,wall.id)){
      listOf(wall.axisStart,wall.axisEnd).forEachIndexed{idx,p->val s=worldToScreen(WorldPoint(p.x,p.y),size,camera.zoom,Offset(camera.panX,camera.panY));drawCircle(Color(0xFF1E90FF),radius=6f,center=s);drawCircle(Color.White,radius=2.5f,center=s)}
     }
    }
    g.joins.forEach{join->drawTypedPolygon(join.corners,size,camera.zoom,Offset(camera.panX,camera.panY),Color(0xFF0A0A0A))}
    g.openings.forEach{item->val wall=g.walls.firstOrNull{it.id==item.hostWallId}?:return@forEach
     val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val len=hypot(dx,dy);if(len<1e-6)return@forEach
     val ux=dx/len;val uy=dy/len;val tp=transformPreview?.takeIf{it.kind==EditorElementKind.OPENING&&it.id==item.id};val width=item.width*(tp?.scaleFactor?:1.0);val half=width/2
     val movedAlong=tp?.let{it.dx*ux+it.dy*uy}?:0.0;val newOffset=(item.offsetAlongWall+item.width/2-width/2).coerceIn(0.0,(len-width).coerceAtLeast(0.0));val along=movedAlong+newOffset+width/2-(item.offsetAlongWall+item.width/2)
     val cx=item.center.x+ux*along;val cy=item.center.y+uy*along
     val a=worldToScreen(WorldPoint(cx-ux*half,cy-uy*half),size,camera.zoom,Offset(camera.panX,camera.panY));val b=worldToScreen(WorldPoint(cx+ux*half,cy+uy*half),size,camera.zoom,Offset(camera.panX,camera.panY))
     drawLine(Color.White,a,b,strokeWidth=10f);val selected=state.selection==EditorSelection(EditorElementKind.OPENING,item.id);val color=if(selected)Color(0xFF1E90FF)else Color(0xFF00A859)
     if(item.kind=="DOOR"){drawLine(color,a,b,2f);val radius=(item.width*camera.zoom.toDouble()).coerceAtLeast(4.0).toFloat();drawArc(color,0f,90f,false,Offset(a.x-radius,a.y-radius),Size(radius*2f,radius*2f),style=Stroke(1.5f))}
     else{drawLine(color,a,b,3f);drawLine(color,Offset(a.x-uy.toFloat()*4,a.y-ux.toFloat()*4),Offset(b.x-uy.toFloat()*4,b.y-ux.toFloat()*4),2f)}
    }
    g.catalog.forEach{item->
     val tp=transformPreview?.takeIf{it.kind==EditorElementKind.CATALOG&&it.id==item.id};val gp=catalogGripPreview?.takeIf{it.first==item.id}?.second
     val width=gp?.width?:item.width*(tp?.scaleFactor?:1.0);val depth=gp?.depth?:item.depth*(tp?.scaleFactor?:1.0);val hw=width/2;val hd=depth/2;val angle=item.rotationRadians+(tp?.rotationRadians?:0.0);val cx=gp?.center?.x?:item.center.x+(tp?.dx?:0.0);val cy=gp?.center?.y?:item.center.y+(tp?.dy?:0.0)
     val corners=listOf(-hw to -hd,hw to -hd,hw to hd,-hw to hd).map{(x,y)->Vec2(cx+x*cos(angle)-y*sin(angle),cy+x*sin(angle)+y*cos(angle))}
     val path=Path();corners.forEachIndexed{k,v->val s=worldToScreen(WorldPoint(v.x,v.y),size,camera.zoom,Offset(camera.panX,camera.panY));if(k==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)};path.close()
     val selected=state.selection==EditorSelection(EditorElementKind.CATALOG,item.id);drawPath(path,if(selected)Color(0x331976D2)else Color(0x223A789A));drawPath(path,if(selected)Color(0xFF1E6BB8)else Color(0xFF3A789A),style=Stroke(2f))
     if(selected){val handleItem=item.copy(center=Vec2(cx,cy),width=width,depth=depth);listOf(CatalogGrip.WIDTH_POSITIVE,CatalogGrip.WIDTH_NEGATIVE,CatalogGrip.DEPTH_POSITIVE,CatalogGrip.DEPTH_NEGATIVE).forEach{grip->val p=catalogGripPoint(handleItem,grip);val sc=worldToScreen(WorldPoint(p.x,p.y),size,camera.zoom,Offset(camera.panX,camera.panY));drawRect(Color(0xFF1E6BB8),topLeft=Offset(sc.x-catalogGripSizePx/2,sc.y-catalogGripSizePx/2),size=Size(catalogGripSizePx,catalogGripSizePx));drawRect(Color.White,topLeft=Offset(sc.x-catalogGripCenterSizePx/2,sc.y-catalogGripCenterSizePx/2),size=Size(catalogGripCenterSizePx,catalogGripCenterSizePx))}}
    }
   }
   preview?.let{drawPreviewWall(it.first,it.second,size,camera.zoom,Offset(camera.panX,camera.panY))}
   preview?.let{pair->
    listOfNotNull(wallStartSnap?.takeIf{it.kind in setOf(SnapKind.ENDPOINT,SnapKind.EDGE)},snapPreview?.takeIf{it.kind in setOf(SnapKind.ENDPOINT,SnapKind.EDGE)}).forEach{sn->
     val point=sn.point;val host=g?.walls?.minByOrNull{wallAxisDistance(point,it)}
     if(host!=null&&wallAxisDistance(point,host)<1e-5){
      val join=previewJoinCorners(host,pair.first,pair.second,point,.2)
      if(join!=null)drawTypedPolygon(join,size,camera.zoom,Offset(camera.panX,camera.panY),Color(0xFF00A859))
     }
    }
   }
   val finger=fingerScreen
   val crossTarget=if(finger!=null)Offset(finger.x,finger.y-crosshairOffsetPx)else null
   if(finger!=null&&crossTarget!=null&&(tool==EditorTool.WALL||endpointPreview!=null)){
    drawLine(Color(0xFF1E293B),finger,crossTarget,1f)
    drawCircle(Color(0xFF1E90FF),radius=7f,center=crossTarget,style=Stroke(1.5f))
    drawLine(Color(0xFF1E90FF),Offset(crossTarget.x-9,crossTarget.y),Offset(crossTarget.x+9,crossTarget.y),1.5f)
    drawLine(Color(0xFF1E90FF),Offset(crossTarget.x,crossTarget.y-9),Offset(crossTarget.x,crossTarget.y+9),1.5f)
   }
   wallStartSnap?.takeIf{it.kind!=SnapKind.NONE&&tool==EditorTool.WALL}?.let{sn->
    val startPoint=worldToScreen(sn.point,size,camera.zoom,Offset(camera.panX,camera.panY))
    drawCircle(Color(0xFF00A859),radius=6f,center=startPoint);drawCircle(Color.White,radius=2.5f,center=startPoint)
   }
   snapPreview?.takeIf{it.kind!=SnapKind.NONE}?.let{sn->
    val target=worldToScreen(sn.point,size,camera.zoom,Offset(camera.panX,camera.panY))
    drawCircle(Color(0xFF00A859),radius=7f,center=target)
    drawCircle(Color.White,radius=3f,center=target)
    drawLine(Color(0xFF00A859),Offset(target.x,0f),Offset(target.x,size.height),1f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(7f,5f)))
    drawLine(Color(0xFF00A859),Offset(0f,target.y),Offset(size.width,target.y),1f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(7f,5f)))
   }
   }
       activeTooltip?.let { label -> Surface(color=Color(0xFF1E293B),shape=RectangleShape,border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),modifier=Modifier.align(Alignment.TopStart).padding(start=6.dp,top=6.dp).zIndex(3f)){Text(label,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),color=Color.White,style=MaterialTheme.typography.labelSmall)} }
    if(showProperties) SelectionInspector(state,Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)).imePadding().width(164.dp).padding(top=2.dp,end=2.dp),onClose={showProperties=false},onEdit={field,label,value->editingProperty=field to label;editValue=value})
   Column(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)).imePadding().padding(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
    val hudFmt:(Double)->String={v->if(v.isFinite())String.format(java.util.Locale.US,"%.2f",v)else "0.00"}
    val telemetry=when{
     catalogGripPreview!=null->{val cp=catalogGripPreview!!.second;"Объект · Ш: ${hudFmt(cp.width)} м   Г: ${hudFmt(cp.depth)} м"}
     preview!=null->{val (a,b)=preview!!;val dx=b.x-a.x;val dy=b.y-a.y; "L: ${hudFmt(hypot(dx,dy))} м   ∠ ${Math.round(Math.toDegrees(atan2(dy,dx))).toInt()}°"}
     endpointPreview!=null->{val ep=endpointPreview!!;val wall=state.typedGeometry?.walls?.firstOrNull{it.id==ep.wallId};if(wall!=null){val anchor=if(ep.moveStart)wall.axisEnd else wall.axisStart;val dx=ep.point.x-anchor.x;val dy=ep.point.y-anchor.y;"L: ${hudFmt(hypot(dx,dy))} м   ∠ ${Math.round(Math.toDegrees(atan2(dy,dx))).toInt()}°"}else "Шаг сетки: 0.10 м"}
     state.selection?.kind==EditorElementKind.WALL->{val w=state.typedGeometry?.walls?.firstOrNull{it.id==state.selection!!.id};if(w!=null)"Стена · L: ${hudFmt(hypot(w.axisEnd.x-w.axisStart.x,w.axisEnd.y-w.axisStart.y))} м · Ш: ${hudFmt(hypot(w.corners[0].x-w.corners[3].x,w.corners[0].y-w.corners[3].y))} м" else "Шаг сетки: 0.10 м"}
     state.selection?.kind==EditorElementKind.OPENING->{val o=state.typedGeometry?.openings?.firstOrNull{it.id==state.selection!!.id};val host=o?.let{x->state.typedGeometry?.walls?.firstOrNull{it.id==x.hostWallId}};"Объект · Ш: ${hudFmt(o?.width?:0.0)} м · Г: ${hudFmt(host?.let{hypot(it.corners[0].x-it.corners[3].x,it.corners[0].y-it.corners[3].y)}?:0.0)} м"}
     state.selection?.kind==EditorElementKind.CATALOG->{val o=state.typedGeometry?.catalog?.firstOrNull{it.id==state.selection!!.id};"Объект · Ш: ${hudFmt(o?.width?:0.0)} м · Г: ${hudFmt(o?.depth?:0.0)} м"}
     else->"Шаг сетки: 0.10 м"
    }
    Surface(color=Color(0xFF1E293B),shape=RectangleShape){Column(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(1.dp)){Text("X: ${hudFmt(hudWorldPoint.x)} м   Y: ${hudFmt(hudWorldPoint.y)} м",fontSize=10.sp,color=Color.White);Text(telemetry,fontSize=10.sp,color=Color.White)}}
   }
   Row(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal+WindowInsetsSides.Bottom)).imePadding().padding(6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
    Text("NAGAPLAS",style=MaterialTheme.typography.labelSmall,color=Color(0xFF1E293B).copy(alpha=.25f))
    Surface(onClick={ribbonCollapsed=!ribbonCollapsed},shape=RectangleShape,color=Color(0xFFE8ECF0),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF64748B)),modifier=Modifier.size(28.dp)){
     Box(Modifier.padding(2.dp),contentAlignment=Alignment.Center){Text(if(ribbonCollapsed)"▼" else "▲",fontSize=22.sp,color=Color(0xFF1E293B),lineHeight=22.sp)}
    }
   }
  }
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(3.dp),style=MaterialTheme.typography.labelSmall)}
  if(editingProperty!=null)AlertDialog(onDismissRequest={editingProperty=null},title={Text(editPropertyDialogTitle(editingProperty!!.second))},text={OutlinedTextField(value=editValue,onValueChange={editValue=it},singleLine=true,label={Text("Число")})},confirmButton={TextButton(onClick={val parsed=editValue.replace(',','.').toDoubleOrNull();if(parsed!=null&&parsed.isFinite()&&(editingProperty!!.first!="thickness"||(parsed>0.01&&parsed<=5.0))){viewModel.updateSelectedProperty(editingProperty!!.first,parsed);editingProperty=null}else editValue=editValue}){Text("ОК")}},dismissButton={TextButton(onClick={editingProperty=null}){Text("Отмена")}})

 }
}
private fun DrawScope.drawPreviewWall(a:WorldPoint,b:WorldPoint,size:Size,scale:Float,pan:Offset){
 if(!a.x.isFinite()||!a.y.isFinite()||!b.x.isFinite()||!b.y.isFinite()||!scale.isFinite()||scale<=0f)return
 val dx=b.x-a.x;val dy=b.y-a.y;val len=hypot(dx,dy);if(!len.isFinite()||len<1e-6)return
 val nx=-dy/len*.1;val ny=dx/len*.1
 val pts=listOf(WorldPoint(a.x+nx,a.y+ny),WorldPoint(b.x+nx,b.y+ny),WorldPoint(b.x-nx,b.y-ny),WorldPoint(a.x-nx,a.y-ny))
 val path=Path();pts.forEachIndexed{i,p->val s=worldToScreen(p,size,scale,pan);if(i==0)path.moveTo(s.x,s.y)else path.lineTo(s.x,s.y)};path.close()
 drawPath(path,Color(0xFF1E90FF).copy(alpha=.35f))
}
internal fun transformWallPreview(wall:WallItem,tp:TransformPreviewState):List<Vec2>{
 val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val length=hypot(dx,dy);if(length<=1e-9)return wall.corners
 val ux=dx/length;val uy=dy/length;val vx=-uy;val vy=ux;val cx=(wall.axisStart.x+wall.axisEnd.x)/2;val cy=(wall.axisStart.y+wall.axisEnd.y)/2;val co=cos(tp.rotationRadians);val si=sin(tp.rotationRadians)
 return wall.corners.map{p->val along=(p.x-wall.axisStart.x)*ux+(p.y-wall.axisStart.y)*uy;val across=(p.x-wall.axisStart.x)*vx+(p.y-wall.axisStart.y)*vy;val x=wall.axisStart.x+ux*along*tp.scaleFactor+vx*across;val y=wall.axisStart.y+uy*along*tp.scaleFactor+vy*across;Vec2(cx+(x-cx)*co-(y-cy)*si+tp.dx,cy+(x-cx)*si+(y-cy)*co+tp.dy)}
}
private fun DrawScope.drawTypedPolygon(vertices:List<Vec2>,size:Size,scale:Float,pan:Offset,color:Color){
 if(vertices.size<3||!scale.isFinite()||scale<=0f)return
 val p=Path();vertices.forEachIndexed{i,v->if(!v.x.isFinite()||!v.y.isFinite())return;val s=worldToScreen(WorldPoint(v.x,v.y),size,scale,pan);if(i==0)p.moveTo(s.x,s.y)else p.lineTo(s.x,s.y)};p.close();drawPath(p,color);drawPath(p,color,style=Stroke(width=1.dp.toPx()))
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
internal fun wallAxisDistance(point:WorldPoint,wall:WallItem):Double{
 val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val l2=dx*dx+dy*dy
 if(l2<=1e-12)return Double.POSITIVE_INFINITY
 val t=((point.x-wall.axisStart.x)*dx+(point.y-wall.axisStart.y)*dy)/l2
 val tc=t.coerceIn(0.0,1.0);return hypot(point.x-wall.axisStart.x-tc*dx,point.y-wall.axisStart.y-tc*dy)
}
internal fun previewJoinCorners(host:WallItem,newStart:WorldPoint,newEnd:WorldPoint,joint:WorldPoint,newThickness:Double):List<Vec2>?{
 val adx=host.axisEnd.x-host.axisStart.x;val ady=host.axisEnd.y-host.axisStart.y;val al=hypot(adx,ady)
 val bdx=newEnd.x-newStart.x;val bdy=newEnd.y-newStart.y;val bl=hypot(bdx,bdy)
 if(al<=1e-9||bl<=1e-9)return null
 val au=Vec2(adx/al,ady/al);val bu=Vec2(bdx/bl,bdy/bl);val an=Vec2(-au.y,au.x);val bn=Vec2(-bu.y,bu.x)
 val den=au.x*bu.y-au.y*bu.x;if(abs(den)<1e-10)return null
 val hostThickness=hypot(host.corners[0].x-host.corners[3].x,host.corners[0].y-host.corners[3].y)
 val points=mutableListOf<Vec2>()
 for(sa in listOf(-1.0,1.0))for(sb in listOf(-1.0,1.0)){
  val p=Vec2(joint.x+an.x*hostThickness*.5*sa,joint.y+an.y*hostThickness*.5*sa)
  val q=Vec2(joint.x+bn.x*newThickness*.5*sb,joint.y+bn.y*newThickness*.5*sb)
  val t=((q.x-p.x)*bu.y-(q.y-p.y)*bu.x)/den
  points+=Vec2(p.x+t*au.x,p.y+t*au.y)
 }
 val miterLimit=2.0*max(hostThickness,newThickness);val clamped=points.map{p->val dx=p.x-joint.x;val dy=p.y-joint.y;val d=hypot(dx,dy);if(d>miterLimit&&d>1e-12)Vec2(joint.x+dx*miterLimit/d,joint.y+dy*miterLimit/d)else p}
 val cx=clamped.map{it.x}.average();val cy=clamped.map{it.y}.average()
 return clamped.sortedBy{atan2(it.y-cy,it.x-cx)}
}
internal fun offsetCrosshairScreen(finger:Offset,offsetPx:Float)=Offset(finger.x,finger.y-offsetPx)
internal fun offsetCrosshairWorld(finger:Offset,offsetPx:Float,size:IntSize,zoom:Float,pan:Offset)=screenToWorld(offsetCrosshairScreen(finger,offsetPx),size,zoom,pan)
internal fun hitWallEndpoint(screen:Offset,wall:WallItem,size:IntSize,zoom:Float,pan:Offset,radiusPx:Float):EndpointPreview?{
 val a=worldToScreen(WorldPoint(wall.axisStart.x,wall.axisStart.y),Size(size.width.toFloat(),size.height.toFloat()),zoom,pan)
 val b=worldToScreen(WorldPoint(wall.axisEnd.x,wall.axisEnd.y),Size(size.width.toFloat(),size.height.toFloat()),zoom,pan)
 val da=(screen-a).getDistance();val db=(screen-b).getDistance()
 return when{da<=radiusPx&&da<=db->EndpointPreview(wall.id,true,WorldPoint(wall.axisStart.x,wall.axisStart.y));db<=radiusPx->EndpointPreview(wall.id,false,WorldPoint(wall.axisEnd.x,wall.axisEnd.y));else->null}
}
internal fun wallEndpointPreviewCorners(wall:WallItem,moveStart:Boolean,point:WorldPoint):List<Vec2>{
 val start=if(moveStart)point else WorldPoint(wall.axisStart.x,wall.axisStart.y)
 val end=if(moveStart)WorldPoint(wall.axisEnd.x,wall.axisEnd.y)else point
 val dx=end.x-start.x;val dy=end.y-start.y;val len=hypot(dx,dy);if(len<1e-9)return wall.corners
 val half=hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)/2
 val nx=-dy/len*half;val ny=dx/len*half
 return listOf(Vec2(start.x+nx,start.y+ny),Vec2(end.x+nx,end.y+ny),Vec2(end.x-nx,end.y-ny),Vec2(start.x-nx,start.y-ny))
}
internal fun snapPoint(p:WorldPoint,anchor:WorldPoint?,g:TypedGeometry?,size:IntSize,zoom:Float,pan:Offset,thresholdPx:Float,ortho:Boolean,excludeWallId:String?=null,gridStep:Double=.1):SnapResult{
 if(!p.x.isFinite()||!p.y.isFinite()||!zoom.isFinite()||zoom<=0f)return SnapResult(p,SnapKind.NONE)
 val screenSize=Size(size.width.toFloat(),size.height.toFloat())
 val rawScreen=worldToScreen(p,screenSize,zoom,pan)
 val walls=g?.walls?.filter{it.id!=excludeWallId}.orEmpty()
 var nearest:WorldPoint?=null;var best=thresholdPx
 walls.forEach{w->listOf(w.axisStart,w.axisEnd).forEach{ep->
  val s=worldToScreen(WorldPoint(ep.x,ep.y),screenSize,zoom,pan);val d=(s-rawScreen).getDistance()
  if(d<best){best=d;nearest=WorldPoint(ep.x,ep.y)}
 }}
 nearest?.let{return SnapResult(it,SnapKind.ENDPOINT,it)}
 var edge:WorldPoint?=null;best=thresholdPx
 walls.forEach{w->
  val a=worldToScreen(WorldPoint(w.axisStart.x,w.axisStart.y),screenSize,zoom,pan);val b=worldToScreen(WorldPoint(w.axisEnd.x,w.axisEnd.y),screenSize,zoom,pan)
  val dx=b.x-a.x;val dy=b.y-a.y;val l2=dx*dx+dy*dy
  if(l2>1e-9){val t=((rawScreen.x-a.x)*dx+(rawScreen.y-a.y)*dy)/l2
   if(t in 0f..1f){val projected=Offset(a.x+t*dx,a.y+t*dy);val d=(projected-rawScreen).getDistance()
    if(d<best){best=d;edge=screenToWorld(projected,size,zoom,pan)}
   }
  }
 }
 edge?.let{return SnapResult(it,SnapKind.EDGE,it)}
 if(anchor!=null){
  val dx=p.x-anchor.x;val dy=p.y-anchor.y;val len=hypot(dx,dy)
  if(len>1e-9){val angle=atan2(dy,dx);val dirs=listOf(0.0,PI/2,PI,-PI/2);val nearestDir=dirs.minBy{abs(atan2(sin(angle-it),cos(angle-it)))}
   val error=abs(atan2(sin(angle-nearestDir),cos(angle-nearestDir)))
   if(ortho||error<=Math.toRadians(5.0)){val snapped=WorldPoint(anchor.x+cos(nearestDir)*len,anchor.y+sin(nearestDir)*len);return SnapResult(snapped,SnapKind.ORTHO,snapped)}
  }
 }
 if(gridStep>0){val q=WorldPoint(round(p.x/gridStep)*gridStep,round(p.y/gridStep)*gridStep);val d=(worldToScreen(q,screenSize,zoom,pan)-rawScreen).getDistance();if(d<=thresholdPx)return SnapResult(q,SnapKind.GRID,q)}
 return SnapResult(p,SnapKind.NONE)
}
private fun nearestWallId(p:Offset,g:TypedGeometry?,size:IntSize,scale:Float,pan:Offset):String?{
 if(g==null)return null;var best:String?=null;var dBest=24f
 g.walls.forEach{wall->val a=worldToScreen(WorldPoint(wall.axisStart.x,wall.axisStart.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan);val b=worldToScreen(WorldPoint(wall.axisEnd.x,wall.axisEnd.y),Size(size.width.toFloat(),size.height.toFloat()),scale,pan)
  val dx=b.x-a.x;val dy=b.y-a.y;val l=dx*dx+dy*dy;val t=if(l==0f)0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/l).coerceIn(0f,1f);val d=hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);if(d<dBest){dBest=d;best=wall.id}
 }
 return best
}


private data class RibbonItem(val glyph:String,val label:String,val active:Boolean,val enabled:Boolean=true,val danger:Boolean=false,val tooltipOverride:String?=null,val action:()->Unit)
@Composable private fun RibbonToggleButton(glyph:String,label:String,active:Boolean,onClick:()->Unit){
 Surface(onClick=onClick,shape=RectangleShape,color=if(active)Color(0xFFDCEBFA)else Color(0xFFF5F6F8),border=if(active)androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF1E6BB8))else androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),modifier=Modifier.size(28.dp).semantics{contentDescription=label;role=Role.Button}) {
  Box(contentAlignment=Alignment.Center){Text(glyph,fontSize=if(glyph=="90°")9.sp else 13.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=Color(0xFF0F172A))}
 }
}
@Composable private fun RibbonQuickButton(glyph:String,label:String,enabled:Boolean,action:()->Unit,tooltip:(String)->Unit){
 Surface(onClick={tooltip(label);action()},enabled=enabled,shape=RectangleShape,color=Color(0xFFF5F6F8),modifier=Modifier.size(26.dp)) {Box(contentAlignment=Alignment.Center){Text(glyph,fontSize=17.sp,color=if(enabled)Color(0xFF0F172A)else Color(0xFF94A3B8))}}
}
@Composable private fun RibbonGroup(items:List<RibbonItem>,tooltip:(String)->Unit){
 Row(Modifier.height(68.dp).padding(horizontal=3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){
  items.forEach{item->
   Surface(onClick={tooltip(item.tooltipOverride?:item.label);item.action()},enabled=item.enabled,shape=RectangleShape,color=when{item.danger&&item.enabled->Color(0xFFFEE2E2);item.active->Color(0xFFDCEBFA);else->Color(0xFFF5F6F8)},border=if(item.danger&&item.enabled)androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFDC2626))else if(item.active)androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF1E6BB8))else androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),modifier=Modifier.size(44.dp)){
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(item.glyph,fontSize=17.sp,color=if(item.danger)Color(0xFFB91C1C)else Color(0xFF0F172A));Text(item.label,fontSize=9.sp,maxLines=1,color=if(item.danger)Color(0xFFB91C1C)else Color(0xFF0F172A))}
   }
  }
  Box(Modifier.padding(start=3.dp).width(1.dp).height(68.dp).background(Color(0xFFCBD5E1)))
 }
}
@Composable private fun CursorQuickButton(selected:Boolean,tooltip:(String)->Unit,onClick:()->Unit){
 Surface(onClick={tooltip("Выбор");onClick()},shape=RectangleShape,color=if(selected)Color(0xFFDCEBFA)else Color(0xFFE8ECF0),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),modifier=Modifier.size(28.dp).semantics{contentDescription="Выбор";role=Role.Button}){
  Canvas(Modifier.fillMaxSize().padding(3.dp)){
   val p=Path().apply{moveTo(size.width*.08f,size.height*.05f);lineTo(size.width*.12f,size.height*.86f);lineTo(size.width*.36f,size.height*.63f);lineTo(size.width*.53f,size.height*.96f);lineTo(size.width*.68f,size.height*.88f);lineTo(size.width*.49f,size.height*.55f);lineTo(size.width*.82f,size.height*.51f);close()}
   drawPath(p,Color.White);drawPath(p,Color(0xFF0F172A),style=Stroke(width=1.5.dp.toPx()))
  }
 }
}
private fun Modifier.borderBottomEngineering()=drawBehind { drawLine(Color(0xFFCBD5E1),Offset(0f,size.height-1.dp.toPx()),Offset(size.width,size.height-1.dp.toPx()),strokeWidth=1.dp.toPx()) }
@Composable private fun ToolbarDivider(){Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFFCBD5E1)))}

@Composable
private fun DesktopToolButton(symbol:String,label:String,selected:Boolean,tooltip:(String)->Unit={},onClick:()->Unit){
 CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp){
  Surface(onClick={tooltip(label);onClick()},shape=RectangleShape,color=if(selected)Color(0xFF1E90FF)else MaterialTheme.colorScheme.surface,modifier=Modifier.size(28.dp).semantics{contentDescription=label;role=Role.Button}){
   Box(contentAlignment=Alignment.Center){Text(symbol,fontSize=16.sp,color=if(selected)Color.White else MaterialTheme.colorScheme.onSurface)}
  }
 }
}

@Composable
private fun CompactActionButton(symbol:String,description:String,enabled:Boolean=true,selected:Boolean=false,danger:Boolean=false,tooltip:(String)->Unit={},onClick:()->Unit){
 CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp){
  Surface(onClick={tooltip(description);onClick()},enabled=enabled,shape=RectangleShape,color=when{danger&&enabled->Color(0xFFE53935);selected->Color(0xFF00B4D8);else->MaterialTheme.colorScheme.surface},modifier=Modifier.size(28.dp).semantics{contentDescription=description;role=Role.Button}){
   Box(contentAlignment=Alignment.Center){Text(symbol,fontSize=16.sp,color=when{(danger&&enabled)||selected->Color.White;!enabled->MaterialTheme.colorScheme.onSurface.copy(alpha=.35f);else->MaterialTheme.colorScheme.onSurfaceVariant})}
  }
 }
}

internal fun editPropertyDialogTitle(field:String):String = when(field){
 "Длина","Длина (м)"->"Изменить длину (м)"
 "Ширина","Ширина (м)"->"Изменить ширину (м)"
 "Высота","Высота (м)"->"Изменить высоту (м)"
 "Отметка"->"Изменить отметку от пола (м)"
 "Подоконник"->"Изменить высоту подоконника (м)"
 "Положение"->"Изменить положение (м)"
 "Глубина"->"Изменить глубину (м)"
 "Поворот"->"Изменить угол поворота (°)"
 else->"Изменить $field"
}

internal fun compactInspectorLabel(kind:EditorElementKind,field:String):String = when(kind){
 EditorElementKind.WALL->when(field){"Длина" ,"Длина (м)"->"Д:";"Ширина","Ширина (м)"->"Ш:";"Высота","Высота (м)"->"В:";"Отметка"->"О:";else->field}
 EditorElementKind.OPENING->when(field){"Ширина"->"Ш:";"Высота"->"В:";"Подоконник"->"П:";"Положение"->"С:";else->field}
 EditorElementKind.CATALOG->when(field){"Ширина"->"Ш:";"Глубина"->"Г:";"Высота"->"В:";"Поворот"->"∠:";else->field}
}

@Composable
private fun SelectionInspector(state:NativeEditorState,modifier:Modifier=Modifier,onClose:()->Unit,onEdit:(String,String,String)->Unit){
 val selected=state.selection
 val g=state.typedGeometry
 if(selected==null){Surface(modifier,color=Color(0xFFF5F6F8),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),shape=RectangleShape){Column(Modifier.padding(5.dp)){Row(Modifier.fillMaxWidth().background(Color(0xFFE8ECF0)).padding(4.dp),verticalAlignment=Alignment.CenterVertically){Text("Свойства",fontSize=11.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=Color(0xFF0F172A),modifier=Modifier.weight(1f));TextButton(onClick=onClose,contentPadding=PaddingValues(0.dp)){Text("×",color=Color(0xFF0F172A))}};Spacer(Modifier.height(4.dp));Text("Не выбрано",fontSize=11.sp,color=Color(0xFF0F172A));Text("Выберите элемент на чертеже",fontSize=9.sp,color=Color(0xFF475569))}};return}
 val typeName=when(selected.kind){
  EditorElementKind.WALL->"Стена"
  EditorElementKind.OPENING->g?.openings?.firstOrNull{it.id==selected.id}?.kind?.let{if(it=="DOOR")"Дверь" else "Окно"}?:"Проём"
  EditorElementKind.CATALOG->g?.catalog?.firstOrNull{it.id==selected.id}?.assetId?.let{id->CatalogPreset.all.firstOrNull{it.assetId==id}?.label?.let{when{it.contains("кровать",true)->"Кровать";it.contains("диван",true)->"Диван";it.contains("стол",true)->"Стол";it.contains("шкаф",true)->"Шкаф";it.contains("унитаз",true)->"Унитаз";it.contains("раков",true)->"Раковина";it.contains("ванн",true)->"Ванна";it.contains("душ",true)->"Душ";else->"Мебель"}}}?: "Мебель"
 }
 val details:List<Triple<String,String,String>>?=when(selected.kind){
  EditorElementKind.WALL->g?.walls?.firstOrNull{it.id==selected.id}?.let{wall->listOf(Triple("Длина (м)","length","%.3f".format(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)),),Triple("Ширина (м)","thickness","%.3f".format(hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y))),Triple("Высота (м)","height","%.3f".format(wall.height)),Triple("Отметка","elevation","%.3f".format(wall.baseElevation)))}
  EditorElementKind.OPENING->g?.openings?.firstOrNull{it.id==selected.id}?.let{item->listOf(Triple("Ширина","width","%.3f".format(item.width)),Triple("Высота","height","%.3f".format(item.height)),Triple("Подоконник","sill","%.3f".format(item.sill)),Triple("Положение","offset","%.3f".format(item.offsetAlongWall)))}
  EditorElementKind.CATALOG->g?.catalog?.firstOrNull{it.id==selected.id}?.let{item->listOf(Triple("Ширина","width","%.3f".format(item.width)),Triple("Глубина","depth","%.3f".format(item.depth)),Triple("Высота","height","%.3f".format(item.height)),Triple("Поворот","rotation","%.1f".format(Math.toDegrees(item.rotationRadians))))}
 }
 if(details!=null)Surface(modifier,color=Color(0xFFF5F6F8),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFCBD5E1)),tonalElevation=0.dp,shape=RectangleShape){
  Column(Modifier.padding(horizontal=4.dp,vertical=3.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
    Column(Modifier.weight(1f)){Text(typeName,style=MaterialTheme.typography.labelSmall,maxLines=1,color=Color(0xFF0F172A))}
    Surface(onClick=onClose,shape=RectangleShape,color=Color(0xFFE8ECF0),modifier=Modifier.size(22.dp).semantics{contentDescription="Закрыть свойства";role=Role.Button}){
     Box(contentAlignment=Alignment.Center){Text("×",fontSize=16.sp)}
    }
   }
   HorizontalDivider(color=Color(0xFFCBD5E1),thickness=1.dp)
   details.forEach{(label,field,value)->Row(Modifier.fillMaxWidth().clickable{onEdit(field,label,value)},verticalAlignment=Alignment.CenterVertically){
    Text(compactInspectorLabel(selected.kind,label),style=MaterialTheme.typography.labelSmall,maxLines=1,color=Color(0xFF475569),modifier=Modifier.width(18.dp))
    Text(value,style=MaterialTheme.typography.labelSmall,maxLines=1,color=Color(0xFF1E90FF))
   }}
  }
 }
}
