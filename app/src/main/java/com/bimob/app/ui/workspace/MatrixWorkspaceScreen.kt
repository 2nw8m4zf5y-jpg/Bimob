package com.bimob.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.bimob.app.editor.*
import kotlinx.coroutines.flow.collect
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ceil

@Composable
fun MatrixCadWorkspaceScreen(projectName:String,editor:NativeEditorViewModel,onBack:()->Unit,onSave:()->Unit,onExport:()->Unit,onImport:()->Unit,onExportVector:(String,String)->Unit){
 val context=LocalContext.current;NagaGridSettings.load(context);val gp by NagaGridSettings.state.collectAsState()
 val state by editor.state.collectAsState();val camera by editor.camera.collectAsState();val geo=state.typedGeometry
 val palettes=listOf(
  MatrixPalette(Color(0xFFFAF9F6),Color(0xFF1E242B),Color(0xFF0066CC),Color(0xFFDCE3EA),Color(0xFFB8C4D0)),
  MatrixPalette(Color(0xFF071324),Color(0xFFE6F2FF),Color(0xFF4DE8F4),Color(0xFF102847),Color(0xFF1A385C)),
  MatrixPalette(Color(0xFF181A1F),Color(0xFFE8EBF0),Color(0xFFA0AAB8),Color(0xFF292E36),Color(0xFF424A55)),
  MatrixPalette(Color(0xFF160E24),Color(0xFFECE4F7),Color(0xFFB388FF),Color(0xFF2A1D3D),Color(0xFF47325F)),
  MatrixPalette(Color(0xFF221308),Color(0xFFFFE9D6),Color(0xFFFF9E40),Color(0xFF382213),Color(0xFF5A3820)),
  MatrixPalette(Color(0xFF0B1E14),Color(0xFFE0F4E7),Color(0xFF4CE08A),Color(0xFF1B3525),Color(0xFF31543D)),
  MatrixPalette(Color(0xFF220B0E),Color(0xFFF8E4E6),Color(0xFFFF6B6B),Color(0xFF3B171C),Color(0xFF5A252B)),
  MatrixPalette(Color(0xFF1D140E),Color(0xFFF2E5D9),Color(0xFFD7A87E),Color(0xFF38291D),Color(0xFF57402F)))
 val pref=context.getSharedPreferences("nagaplas_ui",0);var theme by remember{mutableIntStateOf(pref.getInt("themeIndex",0).coerceIn(0,7))};val p=palettes[theme]
 var homeMenu by remember{mutableStateOf(false)};var exportDialog by remember{mutableStateOf(false)};var askNew by remember{mutableStateOf(false)};var infoExpanded by remember{mutableStateOf(true)};var infoSettings by remember{mutableStateOf(false)};var drawWall by remember{mutableStateOf(false)};var cursor by remember{mutableStateOf(Vec2(0.0,0.0))};var firstPoint by remember{mutableStateOf<Vec2?>(null)};val infoPrefs=context.getSharedPreferences("nagaplas_info",0);var sectionOrder by remember{mutableStateOf(infoPrefs.getString("order","props,layers,coords")!!.split(","))};var enabledSections by remember{mutableStateOf(infoPrefs.getString("enabled","props,layers,coords")!!.split(",").toSet())};var propertyFields by remember{mutableStateOf(infoPrefs.getString("fields","id,length,thickness,xy,angle,nodes")!!.split(",").toSet())};var wallLayerVisible by remember{mutableStateOf(infoPrefs.getBoolean("wallVisible",true))};var wallLayerLocked by remember{mutableStateOf(infoPrefs.getBoolean("wallLocked",false))}
 BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).background(p.bg)){
  val gap=2.dp;val cell=minOf((maxWidth-gap*31f)/32f,(maxHeight-gap*17f)/18f);val matrixW=cell*32f+gap*31f;val matrixH=cell*18f+gap*17f;val originX=(maxWidth-matrixW)/2;val originY=(maxHeight-matrixH)/2
  fun cellBlock(col:Int,row:Int,w:Int,h:Int)=Modifier.offset(originX+col.toFloat()*(cell+gap),originY+row.toFloat()*(cell+gap)).size(cell*w.toFloat()+gap*(w-1).toFloat(),cell*h.toFloat()+gap*(h-1).toFloat())
  Canvas(Modifier.fillMaxSize().pointerInput(camera){detectDragGestures{change,drag->change.consume();editor.setCamera(camera.zoom,camera.panX+drag.x,camera.panY+drag.y)}}){
   drawRect(Color(android.graphics.Color.parseColor(gp.background)))
   val lineEffect=if(gp.lineStyle=="DASH")PathEffect.dashPathEffect(floatArrayOf(6f,4f)) else if(gp.lineStyle=="DOT")PathEffect.dashPathEffect(floatArrayOf(1f,3f)) else null
   if(gp.enabled){val rawStep=(gp.minorMm/1000f*camera.zoom).coerceAtLeast(.1f);val lod=maxOf(1,ceil(4f/rawStep).toInt());val step=rawStep*lod;val major=step*gp.majorEvery;val cx=size.width/2+camera.panX;val cy=size.height/2+camera.panY
    var x=((cx%step)+step)%step;while(x<size.width){drawLine(Color(android.graphics.Color.parseColor(gp.minor)),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),style=Stroke(width=gp.minorWidth,pathEffect=lineEffect));x+=step}
    var y=((cy%step)+step)%step;while(y<size.height){drawLine(Color(android.graphics.Color.parseColor(gp.minor)),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),style=Stroke(width=gp.minorWidth,pathEffect=lineEffect));y+=step}
    x=((cx%major)+major)%major;while(x<size.width){drawLine(Color(android.graphics.Color.parseColor(gp.major)),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),style=Stroke(width=gp.majorWidth,pathEffect=lineEffect));x+=major}
    y=((cy%major)+major)%major;while(y<size.height){drawLine(Color(android.graphics.Color.parseColor(gp.major)),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y),style=Stroke(width=gp.majorWidth,pathEffect=lineEffect));y+=major}
    drawLine(p.accent.copy(alpha=.45f),androidx.compose.ui.geometry.Offset(cx,0f),androidx.compose.ui.geometry.Offset(cx,size.height),1f);drawLine(p.accent.copy(alpha=.45f),androidx.compose.ui.geometry.Offset(0f,cy),androidx.compose.ui.geometry.Offset(size.width,cy),1f)
   }
  }
  Canvas(
   Modifier.fillMaxSize()
    .pointerInput(camera){detectDragGestures{change,drag->change.consume();editor.setCamera(camera.zoom,camera.panX+drag.x,camera.panY+drag.y)}}
    .pointerInput(camera){detectTapGestures{tap->
     val raw=Vec2((tap.x-size.width/2-camera.panX)/camera.zoom.toDouble(),(size.height/2-tap.y-camera.panY)/camera.zoom.toDouble())
     val gridStep=gp.minorMm/1000.0
     val world=if(gp.snap)Vec2(kotlin.math.round(raw.x/gridStep)*gridStep,kotlin.math.round(raw.y/gridStep)*gridStep)else raw
     cursor=world
     if(drawWall&&!wallLayerLocked){val start=firstPoint;if(start==null)firstPoint=world else{editor.createWall(start.x,start.y,world.x,world.y,.2,"ОСЬ");firstPoint=null;drawWall=false}}else if(wallLayerVisible&&!wallLayerLocked)editor.selectAt(world.x,world.y)
    }}
  ){
   val center=androidx.compose.ui.geometry.Offset(size.width/2+camera.panX,size.height/2+camera.panY)
   fun screenPoint(v:Vec2)=androidx.compose.ui.geometry.Offset(center.x+(v.x*camera.zoom).toFloat(),center.y-(v.y*camera.zoom).toFloat())
   if(wallLayerVisible)geo?.walls?.forEach{wall->
    val polygon=Path().apply{wall.corners.forEachIndexed{index,corner->val q=screenPoint(corner);if(index==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()}
    val selected=wall.id==state.selection?.id
    drawPath(polygon,if(selected)p.accent.copy(alpha=.18f)else p.accent.copy(alpha=.05f))
    drawPath(polygon,if(selected)p.accent else p.ink,style=Stroke(width=1.2f))
    val a=screenPoint(wall.axisStart);val b=screenPoint(wall.axisEnd)
    drawLine(p.ink.copy(alpha=.4f),a,b,1f)
    if(selected){drawCircle(p.accent,4f,a);drawCircle(p.accent,4f,b)}
   }
   if(wallLayerVisible)geo?.openings?.forEach{opening->
    val host=geo.walls.firstOrNull{it.id==opening.hostWallId}
    if(host!=null){val dx=host.axisEnd.x-host.axisStart.x;val dy=host.axisEnd.y-host.axisStart.y;val len=hypot(dx,dy);if(len>1e-6){val ux=dx/len;val uy=dy/len;val start=screenPoint(Vec2(opening.center.x-ux*opening.width/2,opening.center.y-uy*opening.width/2));val end=screenPoint(Vec2(opening.center.x+ux*opening.width/2,opening.center.y+uy*opening.width/2));val selected=opening.id==state.selection?.id;drawLine(p.bg,start,end,4f);drawLine(if(selected)p.accent else p.ink,start,end,1.2f);if(opening.kind=="WINDOW"){val normal=androidx.compose.ui.geometry.Offset(-(end.y-start.y)*.06f,(end.x-start.x)*.06f);drawLine(p.ink,start+normal,end+normal,1f)}}}
   }
   geo?.catalog?.forEach{item->
    val ca=cos(item.rotationRadians);val sa=sin(item.rotationRadians);val local=listOf(Vec2(-item.width/2,-item.depth/2),Vec2(item.width/2,-item.depth/2),Vec2(item.width/2,item.depth/2),Vec2(-item.width/2,item.depth/2));val polygon=Path().apply{local.forEachIndexed{index,v->val world=Vec2(item.center.x+v.x*ca-v.y*sa,item.center.y+v.x*sa+v.y*ca);val q=screenPoint(world);if(index==0)moveTo(q.x,q.y)else lineTo(q.x,q.y)};close()};val selected=item.id==state.selection?.id;drawPath(polygon,p.accent.copy(alpha=if(selected).2f else .08f));drawPath(polygon,if(selected)p.accent else p.ink,style=Stroke(width=1f))
   }
  }
  MatrixCellButton(cellBlock(0,0,2,2),"⌂","ДОМОЙ",p){homeMenu=!homeMenu}
  MatrixCellButton(cellBlock(31,0,1,1),"◉","ТЕМА",p){theme=(theme+1)%8;pref.edit().putInt("themeIndex",theme).apply();val palette=palettes[theme];NagaGridSettings.update(context,gp.copy(background=palette.bg.hex(),minor=palette.minor.hex(),major=palette.major.hex()))}
  MatrixCellButton(cellBlock(26,1,2,2),"⌫","УДАЛИТЬ",p,state.selection!=null&&wallLayerVisible&&!wallLayerLocked){editor.deleteSelection()}
  MatrixCellButton(cellBlock(28,1,2,2),"↶","ОТМЕНИТЬ",p,state.canUndo){editor.undo()}
  MatrixCellButton(cellBlock(30,1,2,2),"↷","ВПЕРЁД",p,state.canRedo){editor.redo()}
  Row(cellBlock(27,3,5,1)){
   MatrixCellButton(Modifier.weight(1f).fillMaxHeight(),if(infoExpanded)"▲" else "▼","",p){infoExpanded=!infoExpanded}
   MatrixCellButton(Modifier.weight(1f).fillMaxHeight(),"⚙","",p){infoSettings=true}
   Box(Modifier.weight(3f).fillMaxHeight().background(p.panel,RoundedCornerShape(6.dp)).border(1.dp,p.border,RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center){Text("ИНФОРМАЦИЯ",fontSize=8.sp,color=p.ink,maxLines=1)}
  }
  if(infoExpanded)Column(Modifier.offset(originX+27f*(cell+gap),originY+4f*(cell+gap)).width(cell*5f+gap*4f).heightIn(max=maxHeight-4f*(cell+gap)).background(p.panel,RoundedCornerShape(6.dp)).border(1.dp,p.border,RoundedCornerShape(6.dp)).padding(4.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){
   sectionOrder.forEach{section->if(section in enabledSections){when(section){
    "props"->InfoBox("СВОЙСТВА",p){val wall=geo?.walls?.firstOrNull{it.id==state.selection?.id};if("id" in propertyFields)Text(if(state.selection?.id==null)"Объект не выбран" else "ID: "+state.selection?.id.orEmpty(),color=p.ink,fontSize=8.sp);if(wall!=null){if("length" in propertyFields)Text("Длина: "+(hypot(wall.axisEnd.x-wall.axisStart.x,wall.axisEnd.y-wall.axisStart.y)*1000).toInt()+" мм",color=p.ink,fontSize=8.sp);if("thickness" in propertyFields)Text("Толщина: "+(hypot(wall.corners[0].x-wall.corners[3].x,wall.corners[0].y-wall.corners[3].y)*1000).toInt()+" мм",color=p.ink,fontSize=8.sp);Text("Высота: "+(wall.height*1000).toInt()+" мм",color=p.ink,fontSize=8.sp);if("xy" in propertyFields)Text("X/Y: "+(wall.axisStart.x*1000).toInt()+" / "+(wall.axisStart.y*1000).toInt(),color=p.ink,fontSize=8.sp);if("angle" in propertyFields)Text("Угол: "+Math.toDegrees(atan2(wall.axisEnd.y-wall.axisStart.y,wall.axisEnd.x-wall.axisStart.x)).toInt()+"°",color=p.ink,fontSize=8.sp);if("nodes" in propertyFields)Text("N1/N2: "+(wall.axisStart.x*1000).toInt()+","+(wall.axisStart.y*1000).toInt()+" · "+(wall.axisEnd.x*1000).toInt()+","+(wall.axisEnd.y*1000).toInt(),color=p.ink,fontSize=8.sp)}}
    "layers"->InfoBox("СЛОИ",p){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(wallLayerVisible,{wallLayerVisible=it;if(!it)editor.selectElement(null);infoPrefs.edit().putBoolean("wallVisible",it).apply()});Text("A-WALL-MAIN · видимость",color=p.ink,fontSize=8.sp)};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(wallLayerLocked,{wallLayerLocked=it;if(it)editor.selectElement(null);infoPrefs.edit().putBoolean("wallLocked",it).apply()});Text("Блокировка слоя",color=p.ink,fontSize=8.sp)}}
    "coords"->InfoBox("КООРДИНАТЫ / СЕТКА",p){Text("X "+(cursor.x*1000).toInt()+" мм",color=p.ink,fontSize=8.sp);Text("Y "+(cursor.y*1000).toInt()+" мм",color=p.ink,fontSize=8.sp);Text("Шаг "+gp.minorMm+" · SNAP "+(if(gp.snap)"ВКЛ" else "ВЫКЛ"),color=p.accent,fontSize=8.sp)}
   }}}
  }
  if(homeMenu)Column(Modifier.offset(originX,originY+2f*(cell+gap)+gap).width(cell*3f+gap*2f).background(p.panel,RoundedCornerShape(6.dp)).border(1.dp,p.border,RoundedCornerShape(6.dp)).padding(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
   MenuItem("＋","НОВЫЙ",p){homeMenu=false;askNew=true};MenuItem("▣","СОХРАНИТЬ",p){homeMenu=false;onSave()};MenuItem("⇧","ЭКСПОРТ",p){homeMenu=false;exportDialog=true};MenuItem("⇩","ИМПОРТ",p){homeMenu=false;onImport()};MenuItem("↩","ВЫХОД",p){homeMenu=false;editor.closeAndReset();onBack()}
  }
  Row(Modifier.align(Alignment.BottomStart).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){MenuItem("＋","ZOOM",p){editor.setCamera(camera.zoom*1.2f,camera.panX,camera.panY)};MenuItem("－","ZOOM",p){editor.setCamera(camera.zoom/1.2f,camera.panX,camera.panY)};MenuItem("✚","СТЕНА",p){drawWall=true};MenuItem("↖","ВЫБОР",p){drawWall=false}}
  if(exportDialog)AlertDialog(onDismissRequest={exportDialog=false},shape=RoundedCornerShape(6.dp),containerColor=p.panel,title={Text("ЭКСПОРТ",color=p.ink)},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){MenuItem("▣","JSON · ПРОЕКТ",p){exportDialog=false;onExport()};MenuItem("◇","SVG · ВЕКТОР",p){exportDialog=false;onExportVector("SVG",matrixSvg(geo))};MenuItem("▱","DXF · CAD",p){exportDialog=false;onExportVector("DXF",matrixDxf(geo)}}},confirmButton={TextButton(onClick={exportDialog=false}){Text("ОТМЕНА",color=p.ink)}})
  if(askNew)AlertDialog(onDismissRequest={askNew=false},shape=RoundedCornerShape(6.dp),containerColor=p.panel,title={Text("НОВАЯ СЦЕНА",color=p.ink)},text={Text("Сбросить текущий проект?",color=p.ink)},confirmButton={TextButton(onClick={askNew=false;editor.resetProject("matrix-"+System.currentTimeMillis(),projectName);firstPoint=null;drawWall=false}){Text("СБРОС",color=p.accent)}},dismissButton={TextButton(onClick={askNew=false}){Text("ОТМЕНА",color=p.ink)}})
  if(infoSettings) AlertDialog(
   onDismissRequest={infoSettings=false},shape=RoundedCornerShape(6.dp),containerColor=p.panel,
   title={Text("НАСТРОЙКА ИНФО-ПАНЕЛИ",color=p.ink,fontSize=11.sp)},
   text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
    sectionOrder.forEachIndexed{index,id->Row(verticalAlignment=Alignment.CenterVertically){
     Checkbox(id in enabledSections,{val next=if(it)enabledSections+id else enabledSections-id;enabledSections=next;infoPrefs.edit().putString("enabled",next.joinToString(",")).apply()});
     Text(when(id){"props"->"Свойства";"layers"->"Слои";else->"Координаты / сетка"},Modifier.weight(1f),color=p.ink,fontSize=9.sp);
     Text("▲",Modifier.clickable{if(index>0){val list=sectionOrder.toMutableList();val item=list.removeAt(index);list.add(index-1,item);sectionOrder=list;infoPrefs.edit().putString("order",list.joinToString(",")).apply()}}.padding(3.dp),color=p.accent);
     Text("▼",Modifier.clickable{if(index<sectionOrder.lastIndex){val list=sectionOrder.toMutableList();val item=list.removeAt(index);list.add(index+1,item);sectionOrder=list;infoPrefs.edit().putString("order",list.joinToString(",")).apply()}}.padding(3.dp),color=p.accent)
    }}
    listOf("id" to "ID","length" to "Длина","thickness" to "Толщина","xy" to "Координаты X/Y","angle" to "Угол","nodes" to "Узлы N1/N2").forEach{(key,label)->Row(verticalAlignment=Alignment.CenterVertically){
     Checkbox(key in propertyFields,{val next=if(it)propertyFields+key else propertyFields-key;propertyFields=next;infoPrefs.edit().putString("fields",next.joinToString(",")).apply()});Text(label,color=p.ink,fontSize=9.sp)
    }}
   }},
   confirmButton={TextButton(onClick={infoSettings=false}){Text("ГОТОВО",color=p.accent)}}
  )
 }
}
data class MatrixPalette(val bg:Color,val ink:Color,val accent:Color,val minor:Color,val major:Color){val panel=bg.copy(alpha=.96f);val border=major}
@Composable private fun MatrixCellButton(modifier:Modifier,icon:String,label:String,p:MatrixPalette,enabled:Boolean=true,onClick:()->Unit){Column(modifier.background(p.panel,RoundedCornerShape(6.dp)).border(1.dp,p.border.copy(alpha=if(enabled)1f else .25f),RoundedCornerShape(6.dp)).clickable(enabled=enabled,onClick=onClick).padding(2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(icon,color=if(enabled)p.accent else p.ink.copy(alpha=.35f),fontSize=16.sp,maxLines=1);if(label.isNotEmpty())Text(label,color=if(enabled)p.ink else p.ink.copy(alpha=.35f),fontSize=7.sp,fontFamily=FontFamily.Monospace,maxLines=1)}}
@Composable private fun MenuItem(icon:String,label:String,p:MatrixPalette,onClick:()->Unit){Row(Modifier.height(26.dp).fillMaxWidth().background(p.panel,RoundedCornerShape(6.dp)).border(1.dp,p.border,RoundedCornerShape(6.dp)).clickable(onClick=onClick).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,color=p.accent,fontSize=12.sp);Spacer(Modifier.width(5.dp));Text(label,color=p.ink,fontSize=8.sp,fontFamily=FontFamily.Monospace,maxLines=1)}}
@Composable private fun InfoBox(title:String,p:MatrixPalette,body:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().border(1.dp,p.border,RoundedCornerShape(6.dp)).padding(4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(title,color=p.accent,fontSize=8.sp);body()}}

private fun matrixSvg(g:TypedGeometry?):String{
 val walls=g?.walls.orEmpty();val points=walls.flatMap{it.corners};if(points.isEmpty())return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"></svg>"
 val minX=points.minOf{it.x}*1000;val maxX=points.maxOf{it.x}*1000;val minY=points.minOf{it.y}*1000;val maxY=points.maxOf{it.y}*1000;val pad=100.0
 val sb=StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\""+(minX-pad)+" "+(-maxY-pad)+" "+(maxX-minX+2*pad)+" "+(maxY-minY+2*pad)+"\">")
 walls.forEach{w->sb.append("<polygon fill=\"none\" stroke=\"#20252B\" stroke-width=\"2\" points=\"");w.corners.forEach{pt->sb.append((pt.x*1000).toString()+","+(-pt.y*1000).toString()+" ")};sb.append("\"/>")}
 return sb.append("</svg>").toString()
}
private fun matrixDxf(g:TypedGeometry?):String{
 val sb=StringBuilder()
 sb.append(listOf("0","SECTION","2","ENTITIES").joinToString(System.lineSeparator(),postfix=System.lineSeparator()))
 g?.walls.orEmpty().forEach{w->sb.append(listOf("0","LWPOLYLINE","8","WALLS","90",w.corners.size.toString(),"70","1").joinToString(System.lineSeparator(),postfix=System.lineSeparator()));w.corners.forEach{pt->sb.append(listOf("10",(pt.x*1000).toString(),"20",(pt.y*1000).toString()).joinToString(System.lineSeparator(),postfix=System.lineSeparator())}}
 sb.append(listOf("0","ENDSEC","0","EOF").joinToString(System.lineSeparator(),postfix=System.lineSeparator()))
 return sb.toString()
}
private fun Color.hex():String="#"+String.format("%06X",toArgb() and 0xFFFFFF)
