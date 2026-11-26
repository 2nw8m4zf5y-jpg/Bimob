package com.bimob.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.AppVersion
import com.bimob.app.editor.*
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.BlueprintPalette
import kotlin.math.*

private data class WC(val canvas:Color,val panel:Color,val accent:Color,val grid:Color,val major:Color,val dim:Color,val select:Color,val snap:Color,val ink:Color,val muted:Color,val fill:Color)
private fun wc(p:BlueprintPalette)=when(p.id){
 "blue"->WC(Color(0xFF071324),Color(0xFF0A1A30),Color(0xFF4DE8F4),Color(0xFF102847),Color(0xFF1A385C),Color(0xFF5EC4E8),Color(0xFFFFD166),Color(0xFF3EF08B),Color(0xFFE6F2FF),Color(0xFF86A4C2),Color(0x334DE8F4))
 "paper"->WC(Color(0xFFF5F7FA),Color(0xFFE8EDF3),Color(0xFF0066CC),Color(0xFFE1E7EE),Color(0xFFCCD6E2),Color(0xFF327AA5),Color(0xFFD98B00),Color(0xFF008A55),Color(0xFF172033),Color(0xFF66758A),Color(0x220066CC))
 else->WC(Color(0xFF12161C),Color(0xFF191E25),Color(0xFF00B4D8),Color(0xFF222A34),Color(0xFF303B49),Color(0xFF69BCE0),Color(0xFFFFB300),Color(0xFF00E676),Color(0xFFE7EDF4),Color(0xFF9BA7B5),Color(0x3300B4D8))
}
private val ribbonTools = mapOf(
 "ГЛАВНАЯ" to listOf("ОТРЕЗОК" to "LINE","ПОЛИЛИНИЯ" to "PLINE","СТЕНА" to "WALL","КОНТУР" to "RECT","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ОБРЕЗАТЬ" to "TRIM","СМЕЩЕНИЕ" to "OFFSET","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW","РАЗМЕР" to "DIM","УЗЕЛ" to "NODE"),
 "ПОСТРОЕНИЕ" to listOf("ОТРЕЗОК" to "LINE","ПОЛИЛИНИЯ" to "PLINE","СТЕНА" to "WALL","КОНТУР" to "RECT","ОКРУЖНОСТЬ" to "CIRCLE","ДУГА" to "ARC","УЗЕЛ" to "NODE"),
 "РЕДАКТИРОВАНИЕ" to listOf("ВЫБОР" to "SELECT","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ПОВОРОТ" to "ROTATE","ЗЕРКАЛО" to "MIRROR","ОБРЕЗАТЬ" to "TRIM","УДЛИНИТЬ" to "EXTEND","СМЕЩЕНИЕ" to "OFFSET","УДАЛИТЬ" to "DELETE"),
 "АРХИТЕКТУРА" to listOf("СТЕНА" to "WALL","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW","УЗЕЛ" to "NODE","КОНТУР" to "RECT"),
 "BIM" to listOf("СТЕНА" to "WALL","УЗЕЛ" to "NODE","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW"),
 "АННОТАЦИИ" to listOf("РАЗМЕР" to "DIM","УЗЕЛ" to "NODE"),
 "СЛОИ" to listOf("ВЫБОР" to "SELECT","СТЕНА" to "WALL","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW"),
 "ВИД" to listOf("ПАНОРАМА" to "PAN","МАСШТАБ" to "ZOOM"),
 "ИНСТРУМЕНТЫ" to listOf("ВЫБОР" to "SELECT","УЗЕЛ" to "NODE","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ПОВОРОТ" to "ROTATE","ЗЕРКАЛО" to "MIRROR","УДАЛИТЬ" to "DELETE"),
 "УПРАВЛЕНИЕ" to listOf("ВЫБОР" to "SELECT","ПАНОРАМА" to "PAN","МАСШТАБ" to "ZOOM","СЛОИ" to "NODE","НАСТРОЙКИ" to "NODE")
)
private val workspaceTabs = listOf("ГЛАВНАЯ","ПОСТРОЕНИЕ","РЕДАКТИРОВАНИЕ","АРХИТЕКТУРА","BIM","АННОТАЦИИ","СЛОИ","ВИД","ИНСТРУМЕНТЫ")
private val layers=listOf("A-WALL-MAIN","A-WALL-PART","A-DOOR-WIND","A-GRID-AXIS")

@Composable fun AutoCadWorkspaceScreen(projectName:String,editor:NativeEditorViewModel,onBack:()->Unit,onSave:()->Unit,onExport:()->Unit){
 val context=LocalContext.current;val p=BlueprintTheme.active.value;val c=wc(p);val state by editor.state.collectAsState();val camera by editor.camera.collectAsState();val g=state.typedGeometry
 var tab by remember{mutableStateOf("ГЛАВНАЯ")};var tool by remember{mutableStateOf("WALL")};var activeRail by remember{mutableStateOf("SELECT")};var layer by remember{mutableStateOf(layers[0])};var anchor by remember{mutableStateOf("ОСЬ")}
 var props by remember{mutableStateOf(true)};var grid by remember{mutableStateOf(true)};var ortho by remember{mutableStateOf(true)};var snap by remember{mutableStateOf(true)};var track by remember{mutableStateOf(false)};var weights by remember{mutableStateOf(false)};var debug by remember{mutableStateOf(false)}
 var cmd by remember{mutableStateOf("")};var length by remember{mutableStateOf("4000")};var angle by remember{mutableStateOf("0")};var cursor by remember{mutableStateOf(Vec2(7.6,6.0))};var wallThickness by remember{mutableStateOf(0.2)};var pendingWallStart by remember{mutableStateOf<Vec2?>(null)};var bridgeStatus by remember{mutableStateOf("ОФЛАЙН · ГОТОВ")};var bridgeElapsedMs by remember{mutableStateOf(0.0)};val commandBridge=remember{AiCommandBridge()}
 val submitCadCommand={val input=cmd.trim();if(input.isNotEmpty()){val result=commandBridge.execute(input,editor,cursor,wallThickness,anchor);bridgeStatus=result.message;bridgeElapsedMs=result.elapsedNanos/1_000_000.0;cmd=""}}
 val selectedWall=g?.walls?.firstOrNull{it.id==state.selection?.id&&state.selection?.kind==EditorElementKind.WALL};val degree=endpointDegree(g)
 Column(Modifier.fillMaxSize().background(c.canvas).windowInsetsPadding(WindowInsets.safeDrawing)){
  Row(Modifier.fillMaxWidth().weight(0.042f).background(c.panel).border(1.dp,c.grid),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
   BrandMark(c);Txt("NAGAPLAS",c.ink,9.sp,bold=true)
   Txt(projectName.let{if(it.endsWith(".nagaplas"))it else "$it.nagaplas"},c.ink,8.sp,Modifier.weight(1f).padding(horizontal=3.dp),center=true)
   Txt(AppVersion.VERSION_LABEL,c.accent,8.sp,Modifier.border(.5.dp,c.grid).padding(horizontal=3.dp))
  }
  Row(Modifier.fillMaxWidth().weight(0.068f).background(c.panel).border(1.dp,c.grid),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
   ButtonMini("← ФАСАД",c,onBack,54.dp)
   Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){listOf("ГЛАВНАЯ","АННОТАЦИИ","СЛОИ","ВИД","УПРАВЛЕНИЕ").forEach{ButtonMini(it,c,{tab=it},if(tab==it)68.dp else 60.dp,tab==it)}}
   ButtonMini("▣ СОХРАНИТЬ",c,onSave,64.dp);ButtonMini("⇧ ЭКСПОРТ",c,onExport,54.dp);ButtonMini("ТЕМА: ${p.label}",c,{BlueprintTheme.next()},58.dp)
  }
  Row(Modifier.fillMaxWidth().weight(0.142f).background(c.panel).border(1.dp,c.grid),verticalAlignment=Alignment.CenterVertically){
   val groups=listOf("ЧЕРЧЕНИЕ" to listOf("ОТРЕЗОК" to "LINE","ПОЛИЛИНИЯ" to "PLINE","СТЕНА" to "WALL","КОНТУР" to "RECT"),"РЕДАКТИРОВАНИЕ" to listOf("ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ОБРЕЗАТЬ" to "TRIM","СМЕЩЕНИЕ" to "OFFSET"),"BIM / АННОТАЦИИ" to listOf("ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW","РАЗМЕР" to "DIM","УЗЕЛ" to "NODE"))
   groups.forEach { group ->
     Column(Modifier.weight(0.215f).fillMaxHeight().border(.5.dp,c.grid).padding(horizontal=1.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
      Row(Modifier.fillMaxWidth().weight(0.86f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(0.dp)) {
       group.second.forEach { (label,id) ->
        Tool(id,label,tool==id,c,Modifier.weight(1f).fillMaxHeight()) { tool=id }
       }
      }
      Box(Modifier.fillMaxWidth().weight(0.14f),contentAlignment=Alignment.Center) {
       Txt(group.first,c.muted,5.sp,max=1,center=true)
      }
     }
    }
    Column(Modifier.weight(0.235f).fillMaxHeight().border(.5.dp,c.grid).padding(1.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
     Txt("СЛОЙ",c.muted,5.sp)
     ButtonMiniFit("■ $layer ▼",c,{layer=layers[(layers.indexOf(layer)+1)%layers.size]},Modifier.fillMaxWidth(0.92f).height(15.dp))
     ButtonMiniFit("B: ${(wallThickness*1000).roundToInt()} мм",c,{val values=listOf(.1,.12,.2,.3,.4);wallThickness=values[(values.indexOf(wallThickness).coerceAtLeast(0)+1)%values.size]},Modifier.fillMaxWidth(0.92f).height(15.dp))
     Txt("ПРИВЯЗКА",c.muted,5.sp)
     Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(1.dp),verticalAlignment=Alignment.CenterVertically) {
      listOf("ОСЬ","НАРУЖ","ВНУТР").forEach { ButtonMiniFit(it,c,{anchor=it},Modifier.weight(1f).fillMaxHeight(),anchor==it) }
     }
    }
    Row(Modifier.weight(0.12f).fillMaxHeight(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){ButtonMini("↶",c,{editor.undo()},22.dp);ButtonMini("↷",c,{editor.redo()},22.dp)}
  }
  Row(
   modifier = Modifier.weight(0.636f).fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically
  ) {
   ToolDock(
    modifier = Modifier.weight(0.036f).fillMaxHeight(),
    c = c,
    active = activeRail,
    choose = { selectedTool -> activeRail=selectedTool; when(selectedTool){ "CENTER"->editor.setCamera(72f,0f,0f); "ZOOM"->editor.setCamera(camera.zoom*1.2f,camera.panX,camera.panY); "GRID"->grid=!grid; "LAYERS"->props=!props; "SETTINGS"->debug=!debug; "VIEW3D"->android.widget.Toast.makeText(context,"3D-вид ещё не реализован",android.widget.Toast.LENGTH_SHORT).show(); else->tool=selectedTool } }
   )
   Box(
    modifier = Modifier.weight(if(props) 0.808f else 0.964f).fillMaxHeight().background(c.canvas),
    contentAlignment = Alignment.Center
   ) {
    VectorCanvas(
     modifier = Modifier.fillMaxSize(), c = c, g = g, selected = state.selection?.id,
     zoom = camera.zoom, px = camera.panX, py = camera.panY, grid = grid, weight = weights,
     cursor = cursor, pendingStart = pendingWallStart, snapEnabled = snap, onCursor = { cursor = it }, onWorldTap = { point -> if(tool=="WALL"){val start=pendingWallStart;if(start==null){pendingWallStart=point;bridgeStatus="УЗЕЛ 1 ЗАДАН";}else{val constrained=if(ortho){if(abs(point.x-start.x)>=abs(point.y-start.y))Vec2(point.x,start.y)else Vec2(start.x,point.y)}else point;val end=if(snap)snapWorkspaceEndpoint(constrained,g,camera.zoom,22f)else constrained;val ok=editor.createWall(start.x,start.y,end.x,end.y,wallThickness,anchor);pendingWallStart=null;bridgeStatus=if(ok)"СТЕНА СОЗДАНА" else editor.state.value.error?: "ОШИБКА ЯДРА"}}else{editor.selectAt(point.x,point.y);bridgeStatus="ВЫБОР ОБЪЕКТА"}}
    )
    Column(Modifier.align(Alignment.TopStart).padding(4.dp)) {
     Txt("А    Б    В",c.dim,8.sp); Txt("3\n2\n1",c.dim,7.sp)
    }
    Row(
     modifier = Modifier.align(Alignment.TopCenter).padding(top=3.dp).background(c.panel).border(1.dp,c.dim).padding(3.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Txt("L:",c.dim,7.sp)
     BasicTextField(length,{length=it},Modifier.width(35.dp),textStyle=TextStyle(color=c.ink,fontSize=8.sp,fontFamily=FontFamily.Monospace),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
     Txt("мм ∠",c.dim,7.sp)
     BasicTextField(angle,{angle=it},Modifier.width(24.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
     Txt("°",c.dim,7.sp)
    }
    Txt("UCS X / Y",c.accent,7.sp,Modifier.align(Alignment.BottomStart).padding(5.dp))
    val cursorEndpoint = g?.walls?.any { wall ->
     hypot(cursor.x-wall.axisStart.x,cursor.y-wall.axisStart.y)<1e-6 || hypot(cursor.x-wall.axisEnd.x,cursor.y-wall.axisEnd.y)<1e-6
    } == true
    Txt(if(snap && cursorEndpoint) "OSNAP: УЗЕЛ" else if(snap) "OSNAP: ПОИСК" else "OSNAP: ВЫКЛ",if(snap&&cursorEndpoint)c.snap else c.muted,8.sp,Modifier.align(Alignment.BottomEnd).padding(5.dp).background(c.panel).border(1.dp,if(snap&&cursorEndpoint)c.snap else c.grid).padding(3.dp))
    if(debug && selectedWall != null) {
     Column(Modifier.align(Alignment.Center).background(c.panel).border(1.dp,c.select).padding(4.dp)) {
      Txt("N1 d=${degree[key(selectedWall.axisStart)]?:1}",c.select,7.sp)
      Txt("N2 d=${degree[key(selectedWall.axisEnd)]?:1}",c.select,7.sp)
     }
    }
   }
   if(props) {
    Properties(
     Modifier.weight(0.156f).fillMaxHeight(), c, g, state.selection, degree, editor,
     layer, layers, { layer = it }, { debug = !debug }, { wallThickness=it }
    )
   }
  }
Row(Modifier.fillMaxWidth().weight(0.058f).background(c.panel).border(1.dp,c.grid).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
   Txt("[AI МОСТ-H]",c.snap,7.sp,Modifier.weight(0.105f),bold=true)
   BasicTextField(cmd,{cmd=it},Modifier.weight(0.615f).padding(horizontal=3.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submitCadCommand()}),decorationBox={inner->if(cmd.isEmpty())Txt("4000<0 · WALL 4000 0 · СТЕНА 4000 · КОМНАТА 4000x5000",c.muted,6.sp);inner()})
   ButtonMini("↵",c,{submitCadCommand()},20.dp)
   Txt("$bridgeStatus · ${"%.2f".format(bridgeElapsedMs)} ms",c.muted,6.sp,Modifier.weight(0.28f),max=1)
  }
  Row(Modifier.fillMaxWidth().weight(0.054f).background(c.panel).border(1.dp,c.grid).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
   Txt("X: ${cursor.x*1000}  Y: ${cursor.y*1000}  Z: 0.0",c.dim,6.sp,Modifier.weight(0.22f))
   Spacer(Modifier.weight(0.26f))
   listOf("МОДЕЛЬ" to true,"СЕТКА [F7]" to grid,"ОРТО [F8]" to ortho,"OSNAP [F3]" to snap,"ОТСЛЕЖ" to track,"ВЕС ЛИНИЙ" to weights,"СВОЙСТВА" to props).forEach{(name,on)->Box(Modifier.weight(0.52f/7f),contentAlignment=Alignment.Center){Toggle(name,on,c){when(name){"СЕТКА [F7]"->grid=!grid;"ОРТО [F8]"->ortho=!ortho;"OSNAP [F3]"->snap=!snap;"ОТСЛЕЖ"->track=!track;"ВЕС ЛИНИЙ"->weights=!weights;"СВОЙСТВА"->props=!props}}}}
  }
 }
}

@Composable private fun VectorCanvas(modifier:Modifier,c:WC,g:TypedGeometry?,selected:String?,zoom:Float,px:Float,py:Float,grid:Boolean,weight:Boolean,cursor:Vec2,pendingStart:Vec2?,snapEnabled:Boolean,onCursor:(Vec2)->Unit,onWorldTap:(Vec2)->Unit){
 val latestWorldTap by rememberUpdatedState(onWorldTap)
 Canvas(modifier
  .pointerInput(g,zoom,px,py,snapEnabled){
   detectDragGestures(onDragStart={position->
    val raw=Vec2(((position.x-size.width/2-px)/zoom).toDouble(),((size.height/2-position.y-py)/zoom).toDouble())
    onCursor(if(snapEnabled)snapWorkspaceEndpoint(raw,g,zoom,22f)else raw)
   },onDrag={change,_->
    val position=change.position
    val raw=Vec2(((position.x-size.width/2-px)/zoom).toDouble(),((size.height/2-position.y-py)/zoom).toDouble())
    onCursor(if(snapEnabled)snapWorkspaceEndpoint(raw,g,zoom,22f)else raw)
   })
  }
  .pointerInput(g,selected,zoom,px,py,snapEnabled){detectTapGestures{t->val raw=Vec2(((t.x-size.width/2-px)/zoom).toDouble(),((size.height/2-t.y-py)/zoom).toDouble());val snapped=if(snapEnabled)snapWorkspaceEndpoint(raw,g,zoom,22f)else raw;onCursor(snapped);latestWorldTap(snapped)}}){
  val center=Offset(size.width/2+px,size.height/2+py);fun s(v:Vec2)=Offset(center.x+(v.x*zoom).toFloat(),center.y-(v.y*zoom).toFloat())
  if(grid){val step=zoom*.1f;val major=zoom; if(step>3){var x=((center.x%step)+step)%step;while(x<size.width){drawLine(c.grid,Offset(x,0f),Offset(x,size.height),.45f);x+=step};var y=((center.y%step)+step)%step;while(y<size.height){drawLine(c.grid,Offset(0f,y),Offset(size.width,y),.45f);y+=step}};if(major>8){var x=((center.x%major)+major)%major;while(x<size.width){drawLine(c.major,Offset(x,0f),Offset(x,size.height),.65f);x+=major};var y=((center.y%major)+major)%major;while(y<size.height){drawLine(c.major,Offset(0f,y),Offset(size.width,y),.65f);y+=major}}}
  g?.joins?.forEach{j->val path=Path().apply{j.corners.forEachIndexed{i,v->val q=s(v);if(i==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()};drawPath(path,c.fill);drawPath(path,c.dim,style=Stroke(.7f))}
  g?.walls?.forEach{w->val path=Path().apply{w.corners.forEachIndexed{i,v->val q=s(v);if(i==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()};drawPath(path,if(w.id==selected)c.select.copy(alpha=.18f)else c.fill);clipPath(path){val xs=w.corners.map{ s(it).x };val ys=w.corners.map{ s(it).y };val left=xs.minOrNull()?:0f;val right=xs.maxOrNull()?:0f;val top=ys.minOrNull()?:0f;val bottom=ys.maxOrNull()?:0f;var hx=left-(bottom-top);while(hx<right){drawLine(if(w.id==selected)c.select.copy(alpha=.5f)else c.ink.copy(alpha=.42f),Offset(hx,top),Offset(hx+(bottom-top),bottom),.55f);hx+=5f}};drawPath(path,if(w.id==selected)c.select else c.ink,style=Stroke(if(weight)1.8f else .9f));val a=s(w.axisStart);val b=s(w.axisEnd);drawLine(if(w.id==selected)c.select else c.dim,a,b,.65f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(7f,4f,2f,4f)));if(w.id==selected)listOf(a,b).forEach{drawRect(c.select,Offset(it.x-3,it.y-3),Size(6f,6f))}}
  g?.openings?.forEach{o->val wall=g.walls.firstOrNull{it.id==o.hostWallId}?:return@forEach;val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val len=hypot(dx,dy);if(len>1e-6){val ux=dx/len;val uy=dy/len;val a=s(Vec2(o.center.x-ux*o.width/2,o.center.y-uy*o.width/2));val b=s(Vec2(o.center.x+ux*o.width/2,o.center.y+uy*o.width/2));drawLine(c.canvas,a,b,5f);drawLine(c.dim,a,b,.8f);if(o.kind=="WINDOW"){val n=Offset(-(b.y-a.y)*.07f,(b.x-a.x)*.07f);drawLine(c.dim,a+n,b+n,.8f);drawLine(c.dim,a-n,b-n,.8f)}else drawArc(c.dim,0f,90f,false,Offset(a.x-(o.width*zoom).toFloat(),a.y-(o.width*zoom).toFloat()),Size((o.width*zoom*2).toFloat(),(o.width*zoom*2).toFloat()),style=Stroke(.7f))}}
  g?.catalog?.forEach{o->val q=s(o.center);drawRect(c.dim,Offset(q.x-(o.width*zoom/2).toFloat(),q.y-(o.depth*zoom/2).toFloat()),Size((o.width*zoom).toFloat(),(o.depth*zoom).toFloat()),style=Stroke(1f))}
  val q=s(cursor);pendingStart?.let{start->val p=s(start);drawLine(c.snap,p,q,1f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5f,4f)));drawCircle(c.snap,3.5f,p,style=Stroke(1.1f))};drawRect(c.snap,Offset(q.x-3,q.y-3),Size(6f,6f),style=Stroke(1.2f));val u=Offset(22f,size.height-22f);drawLine(c.accent,u,u+Offset(22f,0f),1.4f);drawLine(c.snap,u,u+Offset(0f,-22f),1.4f)
 }
}
@Composable private fun Properties(modifier:Modifier,c:WC,g:TypedGeometry?,sel:EditorSelection?,degree:Map<String,Int>,vm:NativeEditorViewModel,layer:String,layers:List<String>,choose:(String)->Unit,debug:()->Unit,onThickness:(Double)->Unit){
 val wall=g?.walls?.firstOrNull{it.id==sel?.id&&sel.kind==EditorElementKind.WALL}
 Column(modifier.background(c.panel).border(1.dp,c.grid).padding(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
  Header("СВОЙСТВА (WALL)",c)
  if(wall!=null){RowProp("ID / Слой",wall.id.take(7)+" · "+layer,c);RowProp("Длина L", "${(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)*1000).roundToInt()} мм",c);RowProp("Толщ. B","${(hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()} мм",c);Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.spacedBy(1.dp)){ listOf(100,120,200,300,400).forEach { v -> ButtonMini("$v",c,{vm.updateSelectedProperty("thickness",v/1000.0);onThickness(v/1000.0)},24.dp,((hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()==v)) } }
  RowProp("Высота H","${(wall.height*1000).roundToInt()} мм",c);RowProp("Привязка","ОСЬ",c);RowProp("Угол ∠","${(Math.toDegrees(atan2(wall.axisEnd.y-wall.axisStart.y,wall.axisEnd.x-wall.axisStart.x))*10).roundToInt()/10.0}°",c);RowProp("N1","(${(wall.axisStart.x*1000).roundToInt()}, ${(wall.axisStart.y*1000).roundToInt()})",c);RowProp("N2","(${(wall.axisEnd.x*1000).roundToInt()}, ${(wall.axisEnd.y*1000).roundToInt()})",c);RowProp("Degree","d=${degree[key(wall.axisStart)]?:1}/${degree[key(wall.axisEnd)]?:1}",c);RowProp("Объём","— м³",c);RowProp("Материал","—",c);EditProp("L · м",hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y).toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("length",v)}};EditProp("H · м",wall.height.toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("height",v)}}
  }else RowProp("Выбор","Нет объекта",c)
  Header("СЛОИ (LAYERS)",c);layers.forEachIndexed{i,name->Row(Modifier.fillMaxWidth().clickable{choose(name)},verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).background(listOf(c.dim,c.select,c.snap,c.grid)[i]));Txt((if(name==layer)"● "else"○ ")+name,if(name==layer)c.accent else c.muted,6.sp,max=1)}}
  Header("ГРАФ ЯДРА C++20",c);val nodeCount=g?.walls?.flatMap{listOf(key(it.axisStart),key(it.axisEnd))}?.distinct()?.size;RowProp("Узлы",nodeCount?.toString()?: "—",c);RowProp("Сегменты",g?.walls?.size?.toString()?: "—",c);RowProp("Комнаты","—",c);RowProp("Валид.",if(g!=null)"OK" else "—",c);ButtonMini("DEBUG GRAPH",c,debug,108.dp)
 }
}
@Composable private fun Header(t:String,c:WC){Txt(t,c.accent,7.sp,Modifier.fillMaxWidth().background(c.canvas).padding(vertical=2.dp),bold=true)}
@Composable private fun RowProp(a:String,b:String,c:WC){Column{Txt(a,c.muted,6.sp);Txt(b,c.ink,7.sp,max=1)}}
@Composable private fun EditProp(label:String,value:String,c:WC,commit:(String)->Unit){var v by remember(value){mutableStateOf(value)};Row(verticalAlignment=Alignment.CenterVertically){Txt(label,c.muted,6.sp,Modifier.width(27.dp));BasicTextField(v,{v=it},Modifier.weight(1f).border(.5.dp,c.grid).padding(2.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),singleLine=true);ButtonMini("↵",c,{commit(v)},16.dp)}}
@Composable private fun Tool(id:String,label:String,active:Boolean,c:WC,modifier:Modifier=Modifier,click:()->Unit){
 BoxWithConstraints(modifier.clickable{click()}.border(if(active)1.dp else 0.dp,c.accent).background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent),contentAlignment=Alignment.Center){
  val iconDp=minOf(maxWidth.value*0.62f,maxHeight.value*0.43f,22f).coerceAtLeast(8f)
  Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){CadVectorIcon(cadGlyph(id),c.accent,iconSizeDp=iconDp);Txt(label,c.ink,6.sp,max=1,center=true)}
 }
}
@Composable private fun ButtonMiniFit(text:String,c:WC,click:()->Unit,modifier:Modifier,active:Boolean=false){Box(modifier.clickable{click()}.background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent).border(.5.dp,c.grid).padding(horizontal=1.dp),contentAlignment=Alignment.Center){Txt(text,if(active)c.accent else c.ink,5.sp,max=1,center=true)}}
@Composable private fun Toggle(t:String,on:Boolean,c:WC,click:()->Unit={}){Box(Modifier.clickable{click()}.background(if(on)c.accent.copy(alpha=.12f)else Color.Transparent).padding(horizontal=3.dp)){Txt(t,if(on)c.accent else c.muted,6.sp,max=1)}}
@Composable private fun Txt(t:String,color:Color,size:androidx.compose.ui.unit.TextUnit,m:Modifier=Modifier,bold:Boolean=false,max:Int=Int.MAX_VALUE,center:Boolean=false){BoxWithConstraints(m){val scale=(maxHeight.value/24f).coerceIn(0.62f,1f);androidx.compose.material3.Text(t,color=color,fontFamily=FontFamily.Monospace,fontSize=size*scale,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,maxLines=max,overflow=TextOverflow.Ellipsis,textAlign=if(center)androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)}}
private fun key(p:Vec2)="${(p.x*1000).roundToInt()}:${(p.y*1000).roundToInt()}"
private fun endpointDegree(g:TypedGeometry?):Map<String,Int>{val d=mutableMapOf<String,Int>();g?.walls?.forEach{w->listOf(w.axisStart,w.axisEnd).forEach{d[key(it)]=(d[key(it)]?:0)+1}};return d}
private fun dist(p:Vec2,a:Vec2,b:Vec2):Double{val x=b.x-a.x;val y=b.y-a.y;val l=x*x+y*y;if(l<1e-12)return hypot(p.x-a.x,p.y-a.y);val t=(((p.x-a.x)*x+(p.y-a.y)*y)/l).coerceIn(0.0,1.0);return hypot(p.x-a.x-t*x,p.y-a.y-t*y)}
