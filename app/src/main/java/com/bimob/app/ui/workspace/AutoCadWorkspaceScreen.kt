package com.bimob.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.foundation.gestures.detectTapGestures
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
 "ГЛАВНАЯ" to listOf("ВЫБОР" to "SELECT","ОТРЕЗОК" to "LINE","ПОЛИЛИНИЯ" to "PLINE","СТЕНА" to "WALL","КОНТУР" to "RECT","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ОБРЕЗАТЬ" to "TRIM","СМЕЩЕНИЕ" to "OFFSET"),
 "ПОСТРОЕНИЕ" to listOf("ОТРЕЗОК" to "LINE","ПОЛИЛИНИЯ" to "PLINE","СТЕНА" to "WALL","КОНТУР" to "RECT","ОКРУЖНОСТЬ" to "CIRCLE","ДУГА" to "ARC","УЗЕЛ" to "NODE"),
 "РЕДАКТИРОВАНИЕ" to listOf("ВЫБОР" to "SELECT","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ПОВОРОТ" to "ROTATE","ЗЕРКАЛО" to "MIRROR","ОБРЕЗАТЬ" to "TRIM","УДЛИНИТЬ" to "EXTEND","СМЕЩЕНИЕ" to "OFFSET","УДАЛИТЬ" to "DELETE"),
 "АРХИТЕКТУРА" to listOf("СТЕНА" to "WALL","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW","УЗЕЛ" to "NODE","КОНТУР" to "RECT"),
 "BIM" to listOf("СТЕНА" to "WALL","УЗЕЛ" to "NODE","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW"),
 "АННОТАЦИИ" to listOf("РАЗМЕР" to "DIM","УЗЕЛ" to "NODE"),
 "СЛОИ" to listOf("ВЫБОР" to "SELECT","СТЕНА" to "WALL","ДВЕРЬ" to "DOOR","ОКНО" to "WINDOW"),
 "ВИД" to listOf("ПАНОРАМА" to "PAN","МАСШТАБ" to "ZOOM"),
 "ИНСТРУМЕНТЫ" to listOf("ВЫБОР" to "SELECT","УЗЕЛ" to "NODE","ПЕРЕНЕСТИ" to "MOVE","КОПИЯ" to "COPY","ПОВОРОТ" to "ROTATE","ЗЕРКАЛО" to "MIRROR","УДАЛИТЬ" to "DELETE")
)
private val workspaceTabs = listOf("ГЛАВНАЯ","ПОСТРОЕНИЕ","РЕДАКТИРОВАНИЕ","АРХИТЕКТУРА","BIM","АННОТАЦИИ","СЛОИ","ВИД","ИНСТРУМЕНТЫ")
private val layers=listOf("A-WALL-MAIN","A-WALL-PART","A-DOOR-WIND","A-GRID-AXIS")

@Composable fun AutoCadWorkspaceScreen(projectName:String,editor:NativeEditorViewModel,onBack:()->Unit,onSave:()->Unit,onExport:()->Unit){
 val p=BlueprintTheme.active.value;val c=wc(p);val state by editor.state.collectAsState();val camera by editor.camera.collectAsState();val g=state.typedGeometry
 var tab by remember{mutableStateOf("ГЛАВНАЯ")};var tool by remember{mutableStateOf("WALL")};var layer by remember{mutableStateOf(layers[0])};var anchor by remember{mutableStateOf("ОСЬ")}
 var props by remember{mutableStateOf(true)};var grid by remember{mutableStateOf(true)};var ortho by remember{mutableStateOf(true)};var snap by remember{mutableStateOf(true)};var track by remember{mutableStateOf(false)};var weights by remember{mutableStateOf(false)};var debug by remember{mutableStateOf(false)}
 var cmd by remember{mutableStateOf("")};var length by remember{mutableStateOf("4000")};var angle by remember{mutableStateOf("0")};var cursor by remember{mutableStateOf(Vec2(7.6,6.0))}
 val selectedWall=g?.walls?.firstOrNull{it.id==state.selection?.id&&state.selection?.kind==EditorElementKind.WALL};val degree=endpointDegree(g)
 Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).background(c.canvas)){
  Row(Modifier.fillMaxWidth().height(32.dp).background(c.panel).border(1.dp,c.grid),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
   ButtonMini("← ФАСАД",c,onBack,56.dp)
   Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically){workspaceTabs.forEach{ButtonMini(it,c,{tab=it},if(tab==it)74.dp else 64.dp,tab==it)}}
   Txt(projectName.let{if(it.endsWith(".nagaplas"))it else "$it.nagaplas"},c.ink,8.sp,Modifier.weight(1f).padding(horizontal=3.dp),center=true)
   Txt(AppVersion.VERSION_LABEL,c.accent,8.sp,Modifier.border(.5.dp,c.grid).padding(horizontal=3.dp))
   ButtonMini("СОХРАНИТЬ",c,onSave,56.dp);ButtonMini("ЭКСПОРТ",c,onExport,42.dp);ButtonMini("ТЕМА: ${p.label}",c,{BlueprintTheme.next()},62.dp)
  }
  Row(Modifier.fillMaxWidth().height(58.dp).background(c.panel).border(1.dp,c.grid),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.Center){
    Row(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically){(ribbonTools[tab]?:ribbonTools["ГЛАВНАЯ"]!!).forEachIndexed{index,pair->val (label,id)=pair;Tool(id,label,tool==id,c){tool=id};if(index==2||index==6)Box(Modifier.width(1.dp).height(30.dp).background(c.grid))}}
    Row(Modifier.fillMaxWidth().height(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Txt(when(tab){"ГЛАВНАЯ"->"ПОСТРОЕНИЕ  ·  РЕДАКТИРОВАНИЕ";"ПОСТРОЕНИЕ"->"ГЕОМЕТРИЯ  ·  ПРИМИТИВЫ";"РЕДАКТИРОВАНИЕ"->"ТРАНСФОРМАЦИИ  ·  МОДИФИКАЦИЯ";"АРХИТЕКТУРА"->"СТЕНЫ  ·  ПРОЁМЫ  ·  УЗЛЫ";"BIM"->"МОДЕЛЬ  ·  КОНСТРУКЦИЯ";"АННОТАЦИИ"->"РАЗМЕРЫ  ·  МАРКИ";"СЛОИ"->"ВИДИМОСТЬ  ·  СТРУКТУРА";"ВИД"->"НАВИГАЦИЯ  ·  ОТОБРАЖЕНИЕ";else->"СИСТЕМНЫЕ ИНСТРУМЕНТЫ"},c.muted,6.sp)}
   }
   Column(Modifier.width(120.dp),verticalArrangement=Arrangement.Center){Txt("АКТИВНЫЙ СЛОЙ",c.muted,6.sp);ButtonMini("■ $layer ▼",c,{layer=layers[(layers.indexOf(layer)+1)%layers.size]},116.dp)}
   Column(Modifier.width(64.dp),verticalArrangement=Arrangement.Center){Txt("ПРИВЯЗКА",c.muted,6.sp);ButtonMini("$anchor ▼",c,{anchor=when(anchor){"ОСЬ"->"НАРУЖ";"НАРУЖ"->"ВНУТР";else->"ОСЬ"}},60.dp)}
   ButtonMini("↶",c,{editor.undo()},22.dp);ButtonMini("↷",c,{editor.redo()},22.dp)
  }
  Row(
   modifier = Modifier.weight(1f).fillMaxWidth(),
   verticalAlignment = Alignment.CenterVertically
  ) {
   ToolDock(
    modifier = Modifier.width(46.dp).fillMaxHeight(),
    c = c,
    active = tool,
    choose = { selectedTool -> tool = selectedTool }
   )
   Box(
    modifier = Modifier.weight(1f).fillMaxHeight().background(c.canvas),
    contentAlignment = Alignment.Center
   ) {
    VectorCanvas(
     modifier = Modifier.fillMaxSize(), c = c, g = g, selected = state.selection?.id,
     zoom = camera.zoom, px = camera.panX, py = camera.panY, grid = grid, weight = weights,
     cursor = cursor, snapEnabled = snap, onCursor = { cursor = it }, onSelect = { editor.selectElement(it) }
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
     Modifier.width(164.dp).fillMaxHeight(), c, g, state.selection, degree, editor,
     layer, layers, { layer = it }, { debug = !debug }
    )
   }
  }
Row(Modifier.fillMaxWidth().height(30.dp).background(c.panel).border(1.dp,c.grid).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
   Txt("[AI МОСТ-H]",c.snap,7.sp);Txt(" $tool >",c.dim,7.sp);BasicTextField(cmd,{cmd=it},Modifier.weight(1f).padding(horizontal=4.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),singleLine=true,decorationBox={inner->if(cmd.isEmpty())Txt("Следующая точка или [Длина/Угол/Замкнуть/Отменить]",c.muted,8.sp);inner()})
   ButtonMini("↵",c,{cmd="";},20.dp);Txt("ПАРСЕР: ОФЛАЙН · ГРАФ —",c.muted,6.sp)
  }
  Row(Modifier.fillMaxWidth().height(26.dp).background(c.panel).border(1.dp,c.grid).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
   Txt("X: ${cursor.x*1000}  Y: ${cursor.y*1000}  Z: 0.0",c.dim,6.sp,Modifier.weight(1f))
   Toggle("МОДЕЛЬ",true,c);Toggle("СЕТКА [F7]",grid,c){grid=!grid};Toggle("ОРТО [F8]",ortho,c){ortho=!ortho};Toggle("OSNAP [F3]",snap,c){snap=!snap};Toggle("ОТСЛЕЖ",track,c){track=!track};Toggle("ВЕС ЛИНИЙ",weights,c){weights=!weights};Toggle("СВОЙСТВА",props,c){props=!props}
  }
 }
}

@Composable private fun VectorCanvas(modifier:Modifier,c:WC,g:TypedGeometry?,selected:String?,zoom:Float,px:Float,py:Float,grid:Boolean,weight:Boolean,cursor:Vec2,snapEnabled:Boolean,onCursor:(Vec2)->Unit,onSelect:(EditorSelection?)->Unit){
 Canvas(modifier
  .pointerInput(g,zoom,px,py){
   awaitPointerEventScope {
    while(true){
     val event=awaitPointerEvent()
     val position=event.changes.firstOrNull()?.position ?: continue
     val raw=Vec2(((position.x-size.width/2-px)/zoom).toDouble(),((size.height/2-position.y-py)/zoom).toDouble())
     onCursor(if(snapEnabled)snapWorkspaceEndpoint(raw,g,zoom,22f)else raw)
    }
   }
  }
  .pointerInput(g,selected,zoom,px,py){detectTapGestures{t->val raw=Vec2(((t.x-size.width/2-px)/zoom).toDouble(),((size.height/2-t.y-py)/zoom).toDouble());onCursor(if(snapEnabled)snapWorkspaceEndpoint(raw,g,zoom,22f)else raw);val w=g?.walls?.minByOrNull{dist(raw,it.axisStart,it.axisEnd)};onSelect(if(w!=null&&dist(raw,w.axisStart,w.axisEnd)<.18)EditorSelection(EditorElementKind.WALL,w.id)else null)}}){
  val center=Offset(size.width/2+px,size.height/2+py);fun s(v:Vec2)=Offset(center.x+(v.x*zoom).toFloat(),center.y-(v.y*zoom).toFloat())
  if(grid){val step=zoom*.1f;val major=zoom; if(step>3){var x=((center.x%step)+step)%step;while(x<size.width){drawLine(c.grid,Offset(x,0f),Offset(x,size.height),.45f);x+=step};var y=((center.y%step)+step)%step;while(y<size.height){drawLine(c.grid,Offset(0f,y),Offset(size.width,y),.45f);y+=step}};if(major>8){var x=((center.x%major)+major)%major;while(x<size.width){drawLine(c.major,Offset(x,0f),Offset(x,size.height),.65f);x+=major};var y=((center.y%major)+major)%major;while(y<size.height){drawLine(c.major,Offset(0f,y),Offset(size.width,y),.65f);y+=major}}}
  g?.joins?.forEach{j->val path=Path().apply{j.corners.forEachIndexed{i,v->val q=s(v);if(i==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()};drawPath(path,c.fill);drawPath(path,c.dim,style=Stroke(.7f))}
  g?.walls?.forEach{w->val path=Path().apply{w.corners.forEachIndexed{i,v->val q=s(v);if(i==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()};drawPath(path,if(w.id==selected)c.select.copy(alpha=.18f)else c.fill);drawPath(path,if(w.id==selected)c.select else c.ink,style=Stroke(if(weight)1.8f else .9f));val a=s(w.axisStart);val b=s(w.axisEnd);drawLine(if(w.id==selected)c.select else c.dim,a,b,.65f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(7f,4f,2f,4f)));if(w.id==selected)listOf(a,b).forEach{drawRect(c.select,Offset(it.x-3,it.y-3),Size(6f,6f))}}
  g?.openings?.forEach{o->val wall=g.walls.firstOrNull{it.id==o.hostWallId}?:return@forEach;val dx=wall.axisEnd.x-wall.axisStart.x;val dy=wall.axisEnd.y-wall.axisStart.y;val len=hypot(dx,dy);if(len>1e-6){val ux=dx/len;val uy=dy/len;val a=s(Vec2(o.center.x-ux*o.width/2,o.center.y-uy*o.width/2));val b=s(Vec2(o.center.x+ux*o.width/2,o.center.y+uy*o.width/2));drawLine(c.canvas,a,b,5f);drawLine(c.dim,a,b,.8f);if(o.kind=="WINDOW"){val n=Offset(-(b.y-a.y)*.07f,(b.x-a.x)*.07f);drawLine(c.dim,a+n,b+n,.8f);drawLine(c.dim,a-n,b-n,.8f)}else drawArc(c.dim,0f,90f,false,Offset(a.x-(o.width*zoom).toFloat(),a.y-(o.width*zoom).toFloat()),Size((o.width*zoom*2).toFloat(),(o.width*zoom*2).toFloat()),style=Stroke(.7f))}}
  g?.catalog?.forEach{o->val q=s(o.center);drawRect(c.dim,Offset(q.x-(o.width*zoom/2).toFloat(),q.y-(o.depth*zoom/2).toFloat()),Size((o.width*zoom).toFloat(),(o.depth*zoom).toFloat()),style=Stroke(1f))}
  val q=s(cursor);drawRect(c.snap,Offset(q.x-3,q.y-3),Size(6f,6f),style=Stroke(1.2f));val u=Offset(22f,size.height-22f);drawLine(c.accent,u,u+Offset(22f,0f),1.4f);drawLine(c.snap,u,u+Offset(0f,-22f),1.4f)
 }
}
@Composable private fun Properties(modifier:Modifier,c:WC,g:TypedGeometry?,sel:EditorSelection?,degree:Map<String,Int>,vm:NativeEditorViewModel,layer:String,layers:List<String>,choose:(String)->Unit,debug:()->Unit){
 val wall=g?.walls?.firstOrNull{it.id==sel?.id&&sel.kind==EditorElementKind.WALL}
 Column(modifier.background(c.panel).border(1.dp,c.grid).padding(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
  Header("СВОЙСТВА (WALL)",c)
  if(wall!=null){RowProp("ID / Слой",wall.id.take(7)+" · "+layer,c);RowProp("Длина L", "${(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)*1000).roundToInt()} мм",c);RowProp("Толщ. B","—",c);RowProp("Высота H","${(wall.height*1000).roundToInt()} мм",c);RowProp("Привязка","ОСЬ",c);RowProp("Угол ∠","${(Math.toDegrees(atan2(wall.axisEnd.y-wall.axisStart.y,wall.axisEnd.x-wall.axisStart.x))*10).roundToInt()/10.0}°",c);RowProp("N1","(${(wall.axisStart.x*1000).roundToInt()}, ${(wall.axisStart.y*1000).roundToInt()})",c);RowProp("N2","(${(wall.axisEnd.x*1000).roundToInt()}, ${(wall.axisEnd.y*1000).roundToInt()})",c);RowProp("Degree","d=${degree[key(wall.axisStart)]?:1}/${degree[key(wall.axisEnd)]?:1}",c);RowProp("Объём","— м³",c);RowProp("Материал","—",c);EditProp("L · м",hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y).toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("length",v)}};EditProp("H · м",wall.height.toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("height",v)}}
  }else RowProp("Выбор","Нет объекта",c)
  Header("СЛОИ (LAYERS)",c);layers.forEachIndexed{i,name->Row(Modifier.fillMaxWidth().clickable{choose(name)},verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).background(listOf(c.dim,c.select,c.snap,c.grid)[i]));Txt((if(name==layer)"● "else"○ ")+name,if(name==layer)c.accent else c.muted,6.sp,max=1)}}
  Header("ГРАФ ЯДРА C++20",c);RowProp("Узлы","—",c);RowProp("Сегменты",g?.walls?.size?.toString()?: "—",c);RowProp("Комнаты","—",c);RowProp("Валид.","—",c);ButtonMini("DEBUG GRAPH",c,debug,108.dp)
 }
}
@Composable private fun Header(t:String,c:WC){Txt(t,c.accent,7.sp,Modifier.fillMaxWidth().background(c.canvas).padding(vertical=2.dp),bold=true)}
@Composable private fun RowProp(a:String,b:String,c:WC){Column{Txt(a,c.muted,6.sp);Txt(b,c.ink,7.sp,max=1)}}
@Composable private fun EditProp(label:String,value:String,c:WC,commit:(String)->Unit){var v by remember(value){mutableStateOf(value)};Row(verticalAlignment=Alignment.CenterVertically){Txt(label,c.muted,6.sp,Modifier.width(27.dp));BasicTextField(v,{v=it},Modifier.weight(1f).border(.5.dp,c.grid).padding(2.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),singleLine=true);ButtonMini("↵",c,{commit(v)},16.dp)}}
@Composable private fun Tool(id:String,label:String,active:Boolean,c:WC,click:()->Unit){
 val glyph=cadGlyph(id)
 val hit=Modifier.width(48.dp).fillMaxHeight().clickable{click()}.border(if(active)1.dp else 0.dp,c.accent).background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent)
 Column(hit,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
  CadVectorIcon(glyph,c.accent,physicalSizeMm=6f)
  Txt(label,c.ink,8.sp,max=1)
 }
}
@Composable private fun ToolDock(modifier:Modifier,c:WC,active:String,choose:(String)->Unit){
 Column(modifier.background(c.panel).border(1.dp,c.grid).verticalScroll(rememberScrollState()).padding(vertical=2.dp,horizontal=2.dp),verticalArrangement=Arrangement.spacedBy(2.dp),horizontalAlignment=Alignment.CenterHorizontally){
  listOf("SELECT","LINE","PLINE","WALL","RECT","CIRCLE","ARC","MOVE","COPY","TRIM","OFFSET","DOOR","WINDOW","DIM","NODE","ROTATE").forEach { id ->
   val isActive=active==id
   Box(
    Modifier.fillMaxWidth().height(48.dp)
     .clickable{choose(id)}
     .semantics {
      contentDescription = when(id) {
       "SELECT" -> "Выбор объектов"
       "LINE" -> "Отрезок"
       "PLINE" -> "Полилиния"
       "WALL" -> "Стена"
       "RECT" -> "Прямоугольник"
       "CIRCLE" -> "Окружность"
       "ARC" -> "Дуга"
       "MOVE" -> "Перенести"
       "COPY" -> "Копировать"
       "TRIM" -> "Обрезать"
       "OFFSET" -> "Смещение"
       "DOOR" -> "Дверь"
       "WINDOW" -> "Окно"
       "DIM" -> "Размер"
       "NODE" -> "Узел"
       "ROTATE" -> "Поворот"
       else -> id
      }
      role = androidx.compose.ui.semantics.Role.Button
      selected = isActive
     }
     .background(if(isActive)c.accent.copy(alpha=.12f)else Color.Transparent)
     .border(if(isActive)1.dp else .5.dp,if(isActive)c.accent else c.grid),
    contentAlignment=Alignment.Center
   ){
    CadVectorIcon(cadGlyph(id),if(isActive)c.accent else c.ink,physicalSizeMm=6f)
   }
  }
 }
}
private fun cadGlyph(id:String)=when(id){
 "SELECT"->CadGlyph.SELECT
 "LINE"->CadGlyph.LINE
 "PLINE"->CadGlyph.POLYLINE
 "WALL"->CadGlyph.WALL
 "RECT"->CadGlyph.RECT
 "CIRCLE"->CadGlyph.CIRCLE
 "ARC"->CadGlyph.ARC
 "MOVE"->CadGlyph.MOVE
 "COPY"->CadGlyph.COPY
 "TRIM"->CadGlyph.TRIM
 "OFFSET"->CadGlyph.OFFSET
 "DOOR"->CadGlyph.DOOR
 "WINDOW"->CadGlyph.WINDOW
 "DIM"->CadGlyph.DIMENSION
 "NODE"->CadGlyph.NODE
 "ROTATE"->CadGlyph.ROTATE
 "MIRROR"->CadGlyph.MIRROR
 "EXTEND"->CadGlyph.EXTEND
 "DELETE"->CadGlyph.DELETE
 "PAN"->CadGlyph.PAN
 "ZOOM"->CadGlyph.ZOOM
 else->CadGlyph.NODE
}
@Composable private fun Glyph(id:String,color:Color,m:Modifier){Canvas(m){val st=Stroke(1.1f);when(id){ "LINE","PLINE"->drawLine(color,Offset(1f,size.height-1),Offset(size.width-1,1f),1.2f);"WALL"->{drawRect(color,Offset(1f,2f),Size(size.width-2,size.height-4),style=st);drawLine(color,Offset(2f,size.height/2),Offset(size.width-2,size.height/2),.8f)};"RECT"->drawRect(color,Offset(2f,2f),Size(size.width-4,size.height-4),style=st);"MOVE"->{drawLine(color,Offset(size.width/2,0f),Offset(size.width/2,size.height),1f);drawLine(color,Offset(0f,size.height/2),Offset(size.width,size.height/2),1f)};"COPY"->{drawRect(color,Offset(2f,2f),Size(size.width-6,size.height-6),style=st);drawRect(color,Offset(5f,5f),Size(size.width-6,size.height-6),style=st)};"TRIM"->{drawLine(color,Offset(1f,size.height-1),Offset(size.width-1,1f),1f);drawLine(color,Offset(1f,1f),Offset(size.width-1,size.height-1),1f)};"OFFSET"->drawCircle(color,size.minDimension/3,style=st);"DOOR"->drawArc(color,0f,90f,false,Offset(1f,1f),Size(size.width-2,size.height-2),style=st);"WINDOW"->{drawRect(color,Offset(2f,2f),Size(size.width-4,size.height-4),style=st);drawLine(color,Offset(2f,size.height/2),Offset(size.width-2,size.height/2),1f)};"DIM"->drawLine(color,Offset(1f,size.height/2),Offset(size.width-1,size.height/2),1f);else->drawCircle(color,size.minDimension/3,style=st)}}}
@Composable private fun ButtonMini(text:String,c:WC,click:()->Unit,w:androidx.compose.ui.unit.Dp,active:Boolean=false){Box(Modifier.width(w).fillMaxHeight().clickable{click()}.background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent).border(.5.dp,c.grid).padding(horizontal=2.dp),contentAlignment=Alignment.Center){Txt(text,if(active)c.accent else c.ink,6.sp,max=1)}}
@Composable private fun Toggle(t:String,on:Boolean,c:WC,click:()->Unit={}){Box(Modifier.clickable{click()}.background(if(on)c.accent.copy(alpha=.12f)else Color.Transparent).padding(horizontal=3.dp)){Txt(t,if(on)c.accent else c.muted,6.sp,max=1)}}
@Composable private fun Txt(t:String,color:Color,size:androidx.compose.ui.unit.TextUnit,m:Modifier=Modifier,bold:Boolean=false,max:Int=Int.MAX_VALUE,center:Boolean=false){androidx.compose.material3.Text(t,m,color=color,fontFamily=FontFamily.Monospace,fontSize=size,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,maxLines=max,overflow=TextOverflow.Ellipsis,textAlign=if(center)androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)}
private fun key(p:Vec2)="${(p.x*1000).roundToInt()}:${(p.y*1000).roundToInt()}"
private fun endpointDegree(g:TypedGeometry?):Map<String,Int>{val d=mutableMapOf<String,Int>();g?.walls?.forEach{w->listOf(w.axisStart,w.axisEnd).forEach{d[key(it)]=(d[key(it)]?:0)+1}};return d}
private fun dist(p:Vec2,a:Vec2,b:Vec2):Double{val x=b.x-a.x;val y=b.y-a.y;val l=x*x+y*y;if(l<1e-12)return hypot(p.x-a.x,p.y-a.y);val t=(((p.x-a.x)*x+(p.y-a.y)*y)/l).coerceIn(0.0,1.0);return hypot(p.x-a.x-t*x,p.y-a.y-t*y)}
