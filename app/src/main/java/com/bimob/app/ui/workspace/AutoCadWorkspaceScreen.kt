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
 "blue"->WC(Color(0xFF0F233C),Color(0xFF142D4B),Color(0xFF00B4D8),Color(0xFF1D3554),Color(0xFF2B4567),Color(0xFF69BCE0),Color(0xFFFFB300),Color(0xFF00E676),Color(0xFFE6F2FF),Color(0xFF86A4C2),Color(0x3300B4D8))
 "paper"->WC(Color(0xFFF5F7FA),Color(0xFFE8EDF3),Color(0xFF0066CC),Color(0xFFE1E7EE),Color(0xFFCCD6E2),Color(0xFF327AA5),Color(0xFFD98B00),Color(0xFF008A55),Color(0xFF172033),Color(0xFF66758A),Color(0x220066CC))
 "cream"->WC(Color(0xFFFAF9F6),Color(0xFFF0EEE8),Color(0xFF2D7FA3),Color(0xFFE7E3DC),Color(0xFFD2CCC2),Color(0xFF6D777F),Color(0xFFB7791F),Color(0xFF2F8B60),Color(0xFF25282B),Color(0xFF77736C),Color(0x222D7FA3))
 "parchment"->WC(Color(0xFFF3EFE6),Color(0xFFE8E0D2),Color(0xFF2D7196),Color(0xFFE2D8C8),Color(0xFFCEC0AA),Color(0xFF78634D),Color(0xFFB66A24),Color(0xFF2F8B60),Color(0xFF2D2924),Color(0xFF776B5C),Color(0x222D7196))
 "slate"->WC(Color(0xFF262A30),Color(0xFF30363E),Color(0xFF00B4D8),Color(0xFF353C45),Color(0xFF454E59),Color(0xFF8995A3),Color(0xFFFFB300),Color(0xFF00E676),Color(0xFFE7EDF4),Color(0xFF9BA7B5),Color(0x3300B4D8))
 else->WC(Color(0xFF1E1F22),Color(0xFF25282D),Color(0xFF00B4D8),Color(0xFF30343A),Color(0xFF3A414A),Color(0xFF69BCE0),Color(0xFFFFB300),Color(0xFF00E676),Color(0xFFE7EDF4),Color(0xFF9BA7B5),Color(0x3300B4D8))
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

private data class WorkspaceTool(val id:String,val label:String,val glyph:String,val implemented:Boolean)
private val nagaplasTools=listOf(
 WorkspaceTool("SELECT","Выбор","SELECT",true),WorkspaceTool("WALL","Стена","WALL",true),
 WorkspaceTool("ROOM","Комната","RECT",true),WorkspaceTool("DOOR","Дверь","DOOR",true),
 WorkspaceTool("WINDOW","Окно","WINDOW",true),WorkspaceTool("LINE","Линия","LINE",true),
 WorkspaceTool("RECT","Прямоугольник","RECT",true),WorkspaceTool("CIRCLE","Круг","CIRCLE",false),
 WorkspaceTool("ARC","Дуга","ARC",false),WorkspaceTool("DIM","Размер","DIM",false),
 WorkspaceTool("MOVE","Перенос","MOVE",false),WorkspaceTool("COPY","Копия","COPY",false),
 WorkspaceTool("ROTATE","Поворот","ROTATE",false),WorkspaceTool("MIRROR","Зеркало","MIRROR",false),
 WorkspaceTool("TRIM","Обрезать","TRIM",false),WorkspaceTool("EXTEND","Удлинить","EXTEND",false),
 WorkspaceTool("OFFSET","Смещение","OFFSET",false),WorkspaceTool("DELETE","Удалить","DELETE",true),
 WorkspaceTool("NODE","Узел","NODE",false),WorkspaceTool("PAN","Панорама","PAN",false),
 WorkspaceTool("ZOOM","Масштаб","ZOOM",true),WorkspaceTool("GRID","Сетка","GRID",true),
 WorkspaceTool("ORTHO","Орто","GRID",true),WorkspaceTool("OSNAP","Привязки","NODE",true),
 WorkspaceTool("LAYERS","Слои","LAYERS",true),WorkspaceTool("MATERIAL","Материал","NODE",false),
 WorkspaceTool("TABLE","Стол","NODE",true),WorkspaceTool("BED","Кровать","NODE",true),
 WorkspaceTool("SOFA","Диван","NODE",true),WorkspaceTool("WARDROBE","Шкаф","NODE",true),
 WorkspaceTool("TOILET","Унитаз","NODE",true),WorkspaceTool("SINK","Раковина","NODE",true),
 WorkspaceTool("BATH","Ванна","NODE",true),WorkspaceTool("SHOWER","Душ","NODE",true)
)
private val nagaplasCatalog=mapOf("TABLE" to CatalogPreset.TABLE,"BED" to CatalogPreset.BED,"SOFA" to CatalogPreset.SOFA,"WARDROBE" to CatalogPreset.WARDROBE,"TOILET" to CatalogPreset.TOILET,"SINK" to CatalogPreset.SINK,"BATH" to CatalogPreset.BATH,"SHOWER" to CatalogPreset.SHOWER)

@Composable fun AutoCadWorkspaceScreen(projectName:String,editor:NativeEditorViewModel,onBack:()->Unit,onSave:()->Unit,onExport:()->Unit,onImport:()->Unit={},onSettings:()->Unit={},onProjects:()->Unit={},onNewProject:()->Unit={}){
 val palette=BlueprintTheme.active.value;val c=wc(palette);val state by editor.state.collectAsState();val camera by editor.camera.collectAsState();val g=state.typedGeometry
 var activeTool by remember{mutableStateOf("SELECT")};var cursor by remember{mutableStateOf(Vec2(0.0,0.0))}
 var menu by remember{mutableStateOf(false)};var infoMenu by remember{mutableStateOf(false)};var catalogMenu by remember{mutableStateOf(false)}
 var gridOn by remember{mutableStateOf(true)};var ortho by remember{mutableStateOf(true)};var snap by remember{mutableStateOf(true)}
 var pendingPoint by remember{mutableStateOf<Vec2?>(null)};var catalogChoice by remember{mutableStateOf("TABLE")};var layer by remember{mutableStateOf("A-WALL-MAIN")}
 var status by remember{mutableStateOf("ГОТОВ")};var showThemes by remember{mutableStateOf(false)}
 BoxWithConstraints(Modifier.fillMaxSize().background(c.canvas)){
  val cw=maxWidth/104;val ch=maxHeight/48
  fun at(col:Int,row:Int,w:Int,h:Int)=Modifier.offset(cw*col,ch*row).size(cw*w,ch*h)
  fun toolRegion(i:Int)=NagaplasWorkspaceLayout.tools[i].rect
  // Q0001: brand and menu button share the registered 12x3 block.
  Box(at(0,0,7,3).background(c.panel).border(.5.dp,c.grid),Alignment.Center){BrandMark(c)}
  Box(at(7,0,5,3).background(c.panel).border(.5.dp,c.grid).clickable{menu=!menu},Alignment.Center){Txt("≡  МЕНЮ",c.accent,7.sp,center=true,bold=true)}
  // Reserved cells are intentionally left empty.
  val quick=listOf("SELECT","WALL","ROOM","DOOR","WINDOW","CATALOG","SAVE")
  val quickRects=listOf(20,27,34,41,55,62,69)
  quick.forEachIndexed{i,tool->val col=quickRects[i];val label=when(tool){"SELECT"->"ВЫБОР";"WALL"->"СТЕНА";"ROOM"->"КОМНАТА";"DOOR"->"ДВЕРЬ";"WINDOW"->"ОКНО";"CATALOG"->"КАТАЛОГ";else->"СОХРАНИТЬ"}
   Box(at(col,0,7,3).background(if(activeTool==tool)c.accent.copy(alpha=.12f)else c.panel).border(.5.dp,if(activeTool==tool)c.accent else c.grid).clickable{if(tool=="SAVE")onSave()else if(tool=="CATALOG"){catalogMenu=true;activeTool="CATALOG"}else activeTool=tool},Alignment.Center){Txt(label,c.ink,6.sp,center=true,max=1)}
  }
  Box(at(48,0,7,3).background(c.panel).border(.5.dp,c.grid),Alignment.Center){Txt((if(projectName.endsWith(".nagaplas"))projectName else "$projectName.nagaplas")+(if(state.canUndo)" *" else ""),c.ink,6.sp,max=1,center=true)}
  // Top command group coordinates from Q0011–Q0014.
  val commandButtons=listOf(76 to "DELETE",83 to "REDO",90 to "UNDO")
  commandButtons.forEach{(col,cmd)->Box(at(col,0,7,3).background(c.panel).border(.5.dp,c.grid).clickable{when(cmd){"DELETE"->editor.deleteSelection();"REDO"->editor.redo();"UNDO"->editor.undo()}},Alignment.Center){CadVectorIcon(cadGlyph(cmd),c.accent,iconSizeDp=14f)}}
  Box(at(97,0,7,3).background(c.panel).border(.5.dp,c.grid).clickable{showThemes=!showThemes},Alignment.Center){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){BlueprintTheme.palettes.forEach{theme->Canvas(Modifier.size(8.dp)){drawCircle(when(theme.id){"cream"->Color(0xFFFAF9F6);"dark"->Color(0xFF1E1F22);"blue"->Color(0xFF0F233C);"parchment"->Color(0xFFF3EFE6);else->Color(0xFF262A30)});if(BlueprintTheme.active.value.id==theme.id)drawCircle(c.accent,style=Stroke(1.3.dp.toPx()))}}}
  }
  // 34 vector tool slots, each anchored to a 3x3 cell rectangle.
  nagaplasTools.forEachIndexed{i,t->val r=toolRegion(i);val isActive=activeTool==t.id
   Box(at(r.left,r.top,r.width,r.height).background(if(isActive)c.accent.copy(alpha=.12f)else c.panel).border(if(isActive)1.dp else .4.dp,if(isActive)c.accent else c.grid)
    .semantics{contentDescription=t.label;role=androidx.compose.ui.semantics.Role.Button;selected=isActive}
    .clickable{if(!t.implemented){status="ИНСТРУМЕНТ «${t.label}» ЕЩЁ НЕ РЕАЛИЗОВАН";return@clickable};when(t.id){"GRID"->gridOn=!gridOn;"ORTHO"->ortho=!ortho;"OSNAP"->snap=!snap;"ZOOM"->{editor.setCamera(camera.zoom*1.25f,camera.panX,camera.panY);activeTool=t.id};"DELETE"->editor.deleteSelection();in nagaplasCatalog.keys->{catalogChoice=t.id;catalogMenu=true;activeTool="CATALOG"};else->{activeTool=t.id;pendingPoint=null;status=t.label.uppercase()}}},Alignment.Center){
    CadVectorIcon(cadGlyph(t.glyph),if(!t.implemented)c.muted else if(isActive)c.accent else c.ink,iconSizeDp=12f)
   }
  }
  Box(at(102,3,2,3).background(c.panel).border(.4.dp,c.grid),Alignment.Center){Txt("⋯",c.muted,6.sp,center=true)}
  // Q0050/Q0052: project navigator and layer list.
  Box(at(0,6,12,1).background(c.panel).border(.5.dp,c.grid).clickable{infoMenu=!infoMenu},Alignment.CenterStart){Txt("ПРОЕКТ  ▾",c.accent,6.sp,Modifier.padding(start=2.dp),bold=true)}
  Box(at(0,7,12,38).background(c.panel).border(.5.dp,c.grid)){
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Txt("ФАЙЛ",c.accent,6.sp,bold=true);Txt(projectName,c.ink,5.sp,max=2)
    Txt("УРОВНИ",c.accent,6.sp,bold=true);Txt("▣  Level 1 · 0.000",c.ink,5.sp)
    Txt("ЭЛЕМЕНТЫ",c.accent,6.sp,bold=true);RowProp("Стены",g?.walls?.size?.toString()?: "0",c);RowProp("Проёмы",g?.openings?.size?.toString()?: "0",c);RowProp("Объекты",g?.catalog?.size?.toString()?: "0",c)
    Txt("СЛОИ",c.accent,6.sp,bold=true)
    listOf("A-WALL-MAIN","A-WALL-PART","A-DOOR-WIND","A-GRID-AXIS").forEach{ln->Box(Modifier.fillMaxWidth().clickable{layer=ln}.background(if(layer==ln)c.accent.copy(alpha=.1f)else Color.Transparent).padding(2.dp)){Txt((if(layer==ln)"● "else"○ ")+ln,c.ink,5.sp,max=1)}}
   }
  }
  // Main viewport is M7:CN45. It is not inset by old side-panel padding.
  Box(at(12,6,80,39).background(c.canvas).border(.5.dp,c.grid)){
   VectorCanvas(Modifier.fillMaxSize(),c,g,state.selection?.id,camera.zoom,camera.panX,camera.panY,gridOn,true,cursor,pendingPoint,snap,{cursor=it},{p->
    when(activeTool){
     "WALL","LINE"->{val first=pendingPoint;if(first==null){pendingPoint=p;status="УКАЖИТЕ КОНЕЧНУЮ ТОЧКУ"}else{val q=if(ortho){if(abs(p.x-first.x)>=abs(p.y-first.y))Vec2(p.x,first.y)else Vec2(first.x,p.y)}else p;editor.createWall(first.x,first.y,q.x,q.y,.2, "ОСЬ");pendingPoint=null;status="СТЕНА СОЗДАНА"}}
     "ROOM","RECT"->{val first=pendingPoint;if(first==null){pendingPoint=p;status="УКАЖИТЕ ПРОТИВОПОЛОЖНЫЙ УГОЛ"}else{val x=min(first.x,p.x);val y=min(first.y,p.y);editor.createRoom(x,y,abs(p.x-first.x),abs(p.y-first.y),.2,"ОСЬ");pendingPoint=null;status="КОМНАТА СОЗДАНА"}}
     "DOOR"->{if(editor.createOpening("DOOR",p.x,p.y))status="ДВЕРЬ РАЗМЕЩЕНА" else status="НЕ НАЙДЕНА СТЕНА ДЛЯ ДВЕРИ"}
     "WINDOW"->{if(editor.createOpening("WINDOW",p.x,p.y))status="ОКНО РАЗМЕЩЕНО" else status="НЕ НАЙДЕНА СТЕНА ДЛЯ ОКНА"}
     "CATALOG"->{val preset=nagaplasCatalog[catalogChoice]?:CatalogPreset.TABLE;editor.createCatalog(preset,p.x,p.y,0.0);status="ОБЪЕКТ ДОБАВЛЕН"}
     else->{editor.selectAt(p.x,p.y);status=if(state.selection==null)"ВЫБОР" else"ОБЪЕКТ ВЫБРАН"}
    }
   })
   Txt("GRID 104×48 · ${activeTool}",c.muted,5.sp,Modifier.align(Alignment.TopStart).padding(2.dp));Txt("X ${"%.3f".format(cursor.x)}  Y ${"%.3f".format(cursor.y)}",c.accent,5.sp,Modifier.align(Alignment.BottomStart).padding(2.dp))
   state.error?.let{Txt(it,c.dim,5.sp,Modifier.align(Alignment.BottomCenter).background(c.panel).padding(2.dp),max=2)}
  }
  // Q0051/Q0053: Inspector.
  Box(at(92,6,12,1).background(c.panel).border(.5.dp,c.grid),Alignment.Center){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){Txt("ИНСПЕКТОР",c.accent,5.5.sp,Modifier.weight(1f),bold=true);Txt("⋮",c.accent,7.sp,Modifier.clickable{infoMenu=!infoMenu}.padding(horizontal=2.dp))}}
  Box(at(92,7,12,38).background(c.panel).border(.5.dp,c.grid)){
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
    Header("ВЫБРАННЫЙ ОБЪЕКТ",c)
    val sel=state.selection
    if(sel==null)RowProp("Selection","Нет",c)else{RowProp("Тип",sel.kind.name,c);RowProp("ID",sel.id.take(10),c)}
    val wall=g?.walls?.firstOrNull{it.id==sel?.id}
    if(wall!=null){RowProp("L", "${(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)*1000).roundToInt()} мм",c);RowProp("B","${(hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()} мм",c);RowProp("H","${(wall.height*1000).roundToInt()} мм",c);listOf(100,120,200,300,400).forEach{v->ButtonMiniFit("${v} мм",c,{editor.updateSelectedProperty("thickness",v/1000.0)},Modifier.fillMaxWidth().height(18.dp),((hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()==v))}}
    Header("СЛОЙ",c);Txt(layer,c.ink,5.sp,max=2);Header("ГЕОМЕТРИЯ CORE",c);RowProp("Узлы",g?.walls?.flatMap{listOf(key(it.axisStart),key(it.axisEnd))}?.distinct()?.size?.toString()?: "0",c);RowProp("Сегменты",g?.walls?.size?.toString()?: "0",c);RowProp("Undo",if(state.canUndo)"READY"else"EMPTY",c);RowProp("Redo",if(state.canRedo)"READY"else"EMPTY",c)
   }
  }
  // Q0054 status row; each status indicator is interactive and grounded.
  Row(at(0,45,104,3).background(c.panel).border(.5.dp,c.grid),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){
   Txt("NAGAPLAS",c.accent,5.sp,bold=true);Txt(status,c.ink,5.sp,max=1);Spacer(Modifier.weight(1f))
   Txt("X ${"%.2f".format(cursor.x)}",c.muted,5.sp);Txt("Y ${"%.2f".format(cursor.y)}",c.muted,5.sp)
   listOf("GRID" to gridOn,"ORTHO" to ortho,"SNAP" to snap).forEach{(name,enabled)->Txt("${name} ${if(enabled)"ON"else"OFF"}",if(enabled)c.accent else c.muted,5.sp,Modifier.clickable{when(name){"GRID"->gridOn=!gridOn;"ORTHO"->ortho=!ortho;"SNAP"->snap=!snap}}.padding(horizontal=2.dp))}
   Txt("1:${"%.1f".format(camera.zoom)}",c.muted,5.sp);Txt(if(state.canUndo)"●"else"○",c.accent,5.sp)
  }
  if(menu)Box(at(0,3,12,9).background(c.panel).border(1.dp,c.accent).padding(2.dp)){
   Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(1.dp)){listOf("Новый","Проекты","Сохранить","Экспорт","Импорт","Настройка","Выход").forEach{item->Box(Modifier.weight(1f).fillMaxWidth().background(c.canvas).clickable{menu=false;when(item){"Новый"->onNewProject();"Проекты"->onProjects();"Сохранить"->onSave();"Экспорт"->onExport();"Импорт"->onImport();"Настройка"->onSettings();"Выход"->onBack()}},Alignment.CenterStart){Txt(item,c.ink,6.sp,Modifier.padding(start=3.dp))}}}
  }
  if(showThemes)Box(at(97,3,7,5).background(c.panel).border(1.dp,c.accent).padding(2.dp)){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(1.dp)){BlueprintTheme.palettes.forEach{theme->Box(Modifier.weight(1f).fillMaxWidth().clickable{BlueprintTheme.select(theme);showThemes=false}.background(if(BlueprintTheme.active.value==theme)c.accent.copy(alpha=.15f)else c.canvas),Alignment.CenterStart){Txt(theme.label,c.ink,5.sp,Modifier.padding(start=2.dp))}}}}
  if(catalogMenu)Box(at(35,12,22,18).background(c.panel).border(1.dp,c.accent).padding(2.dp)){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(1.dp)){nagaplasCatalog.forEach{(key,preset)->Box(Modifier.fillMaxWidth().height(22.dp).clickable{catalogChoice=key;catalogMenu=false;activeTool="CATALOG";status="ВЫБЕРИТЕ ТОЧКУ РАЗМЕЩЕНИЯ: ${preset.label}"}.background(c.canvas),Alignment.CenterStart){Txt(preset.label,c.ink,6.sp,Modifier.padding(start=2.dp))};Box(Modifier.fillMaxWidth().height(20.dp).clickable{catalogMenu=false},Alignment.Center){Txt("ЗАКРЫТЬ",c.muted,5.sp)}}}
  if(infoMenu)Box(at(92,6,12,7).background(c.panel).border(1.dp,c.accent).padding(2.dp)){Column{listOf("Инспектор","Настройки","Скрыть панель").forEach{Box(Modifier.fillMaxWidth().height(22.dp).clickable{infoMenu=false;when(it){"Настройки"->onSettings();"Скрыть панель"->status="СВОРАЧИВАНИЕ ПАНЕЛИ: ПОСЛЕДУЮЩИЙ ЭТАП"}},Alignment.CenterStart){Txt(it,c.ink,5.sp,Modifier.padding(2.dp))}}}}
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
  if(wall!=null){RowProp("ID / Слой",wall.id.take(7)+" · "+layer,c);RowProp("Длина L", "${(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)*1000).roundToInt()} мм",c);RowProp("Толщ. B","${(hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()} мм",c);Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.spacedBy(1.dp)){ listOf(100,120,200,300,400).forEach { v -> ButtonMiniFit("$v",c,{vm.updateSelectedProperty("thickness",v/1000.0);onThickness(v/1000.0)},Modifier.weight(1f).fillMaxHeight(),((hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).roundToInt()==v)) } }
  RowProp("Высота H","${(wall.height*1000).roundToInt()} мм",c);RowProp("Привязка","ОСЬ",c);RowProp("Угол ∠","${(Math.toDegrees(atan2(wall.axisEnd.y-wall.axisStart.y,wall.axisEnd.x-wall.axisStart.x))*10).roundToInt()/10.0}°",c);RowProp("N1","(${(wall.axisStart.x*1000).roundToInt()}, ${(wall.axisStart.y*1000).roundToInt()})",c);RowProp("N2","(${(wall.axisEnd.x*1000).roundToInt()}, ${(wall.axisEnd.y*1000).roundToInt()})",c);RowProp("Degree","d=${degree[key(wall.axisStart)]?:1}/${degree[key(wall.axisEnd)]?:1}",c);RowProp("Объём","— м³",c);RowProp("Материал","—",c);EditProp("L · м",hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y).toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("length",v)}};EditProp("H · м",wall.height.toString(),c){it.toDoubleOrNull()?.let{v->vm.updateSelectedProperty("height",v)}}
  }else RowProp("Выбор","Нет объекта",c)
  Header("СЛОИ (LAYERS)",c);layers.forEachIndexed{i,name->Row(Modifier.fillMaxWidth().clickable{choose(name)},verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).background(listOf(c.dim,c.select,c.snap,c.grid)[i]));Txt((if(name==layer)"● "else"○ ")+name,if(name==layer)c.accent else c.muted,6.sp,max=1)}}
  Header("ГРАФ ЯДРА C++20",c);val nodeCount=g?.walls?.flatMap{listOf(key(it.axisStart),key(it.axisEnd))}?.distinct()?.size;RowProp("Узлы",nodeCount?.toString()?: "—",c);RowProp("Сегменты",g?.walls?.size?.toString()?: "—",c);RowProp("Комнаты","—",c);RowProp("Валид.",if(g!=null)"OK" else "—",c);ButtonMini("DEBUG GRAPH",c,debug,108.dp)
 }
}
@Composable private fun Header(t:String,c:WC){Txt(t,c.accent,7.sp,Modifier.fillMaxWidth().background(c.canvas).padding(vertical=2.dp),bold=true)}
@Composable private fun RowProp(a:String,b:String,c:WC){Column{Txt(a,c.muted,6.sp);Txt(b,c.ink,7.sp,max=1)}}
@Composable private fun EditProp(label:String,value:String,c:WC,commit:(String)->Unit){var v by remember(value){mutableStateOf(value)};Row(verticalAlignment=Alignment.CenterVertically){Txt(label,c.muted,6.sp,Modifier.width(27.dp));BasicTextField(v,{v=it},Modifier.weight(1f).border(.5.dp,c.grid).padding(2.dp),textStyle=TextStyle(color=c.ink,fontSize=7.sp,fontFamily=FontFamily.Monospace),singleLine=true);ButtonMini("↵",c,{commit(v)},16.dp)}}
@Composable private fun BrandMark(c:WC){
 Row(Modifier.width(28.dp).fillMaxHeight().padding(horizontal=3.dp),verticalAlignment=Alignment.CenterVertically){
  Canvas(Modifier.size(24.dp)){val a=Offset(size.width*.5f,size.height*.08f);val b=Offset(size.width*.08f,size.height*.92f);val d=Offset(size.width*.92f,size.height*.92f);val st=Stroke(2.2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Butt,join=androidx.compose.ui.graphics.StrokeJoin.Bevel);drawLine(c.accent,a,b,st.width);drawLine(c.accent,a,d,st.width);drawLine(c.accent,b,d,st.width);drawLine(c.accent,Offset(size.width*.31f,size.height*.65f),Offset(size.width*.69f,size.height*.65f),st.width)}
 }
}

@Composable private fun ToolDock(modifier:Modifier,c:WC,active:String,choose:(String)->Unit){
 Column(modifier.background(c.panel).border(1.dp,c.grid).verticalScroll(rememberScrollState()).padding(vertical=2.dp,horizontal=1.dp),verticalArrangement=Arrangement.spacedBy(2.dp),horizontalAlignment=Alignment.CenterHorizontally){
  val items=listOf("SELECT","RECT","CENTER","ZOOM","VIEW3D","LAYERS","SETTINGS","GRID")
  items.forEach { id ->
   val isActive=active==id
   val label=when(id){"SELECT"->"Курсор";"RECT"->"Рамка";"CENTER"->"Центр";"ZOOM"->"Масштаб";"VIEW3D"->"3D";"LAYERS"->"Слои";"SETTINGS"->"Настройки";else->"Сетка"}
   Box(Modifier.fillMaxWidth().height(42.dp).clickable{choose(id)}
     .semantics{contentDescription=label;role=androidx.compose.ui.semantics.Role.Button;selected=isActive}
     .background(if(isActive)c.accent.copy(alpha=.12f)else Color.Transparent)
     .border(if(isActive)1.dp else .5.dp,if(isActive)c.accent else c.grid),contentAlignment=Alignment.Center){
    CadVectorIcon(cadGlyph(id),if(isActive)c.accent else c.ink,iconSizeDp=22f)
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
 "VIEW3D"->CadGlyph.VIEW3D
 "LAYERS"->CadGlyph.LAYERS
 "SETTINGS"->CadGlyph.SETTINGS
 "GRID"->CadGlyph.GRID
 else->CadGlyph.NODE
}

@Composable private fun Tool(id:String,label:String,active:Boolean,c:WC,modifier:Modifier=Modifier,click:()->Unit){
 BoxWithConstraints(modifier.clickable{click()}.border(if(active)1.dp else 0.dp,c.accent).background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent),contentAlignment=Alignment.Center){
  val iconDp=minOf(maxWidth.value*0.62f,maxHeight.value*0.43f,22f).coerceAtLeast(8f)
  Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){CadVectorIcon(cadGlyph(id),c.accent,iconSizeDp=iconDp);Txt(label,c.ink,6.sp,max=1,center=true)}
 }
}
@Composable private fun ButtonMini(text:String,c:WC,click:()->Unit,w:androidx.compose.ui.unit.Dp,active:Boolean=false){Box(Modifier.width(w).fillMaxHeight().clickable{click()}.background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent).border(.5.dp,c.grid).padding(horizontal=2.dp),contentAlignment=Alignment.Center){Txt(text,if(active)c.accent else c.ink,6.sp,max=1)}}
@Composable private fun ButtonMiniFit(text:String,c:WC,click:()->Unit,modifier:Modifier,active:Boolean=false){Box(modifier.clickable{click()}.background(if(active)c.accent.copy(alpha=.12f)else Color.Transparent).border(.5.dp,c.grid).padding(horizontal=1.dp),contentAlignment=Alignment.Center){Txt(text,if(active)c.accent else c.ink,5.sp,max=1,center=true)}}
@Composable private fun Toggle(t:String,on:Boolean,c:WC,click:()->Unit={}){Box(Modifier.clickable{click()}.background(if(on)c.accent.copy(alpha=.12f)else Color.Transparent).padding(horizontal=3.dp)){Txt(t,if(on)c.accent else c.muted,6.sp,max=1)}}
@Composable private fun Txt(t:String,color:Color,size:androidx.compose.ui.unit.TextUnit,m:Modifier=Modifier,bold:Boolean=false,max:Int=Int.MAX_VALUE,center:Boolean=false){BoxWithConstraints(m){val scale=(maxHeight.value/24f).coerceIn(0.62f,1f);androidx.compose.material3.Text(t,color=color,fontFamily=FontFamily.Monospace,fontSize=size*scale,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal,maxLines=max,overflow=TextOverflow.Ellipsis,textAlign=if(center)androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)}}
private fun key(p:Vec2)="${(p.x*1000).roundToInt()}:${(p.y*1000).roundToInt()}"
private fun endpointDegree(g:TypedGeometry?):Map<String,Int>{val d=mutableMapOf<String,Int>();g?.walls?.forEach{w->listOf(w.axisStart,w.axisEnd).forEach{d[key(it)]=(d[key(it)]?:0)+1}};return d}
private fun dist(p:Vec2,a:Vec2,b:Vec2):Double{val x=b.x-a.x;val y=b.y-a.y;val l=x*x+y*y;if(l<1e-12)return hypot(p.x-a.x,p.y-a.y);val t=(((p.x-a.x)*x+(p.y-a.y)*y)/l).coerceIn(0.0,1.0);return hypot(p.x-a.x-t*x,p.y-a.y-t*y)}
