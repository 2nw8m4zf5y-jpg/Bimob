package com.bimob.app.ui.facade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette
import com.bimob.app.ui.blueprint.ThemeButton

data class FacadeProject(val id:String,val name:String,val modified:String,val nodeCount:Int,val active:Boolean=false)

private val facadeAxes = listOf("А","Б","В","Г","Д")
private val facadeLevels = listOf("+15.200","+13.800","+9.600","+5.400","+1.200","0.000")

@Composable
fun ProjectFacadeScreen(
 projects:List<FacadeProject>,onBack:()->Unit,onNew:()->Unit,onOpen:(String)->Unit,
 onImport:()->Unit,onTool:(String)->Unit,onOpenLast:()->Unit
){
 val p=LocalBlueprintPalette.current
 Box(Modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing)){
  Canvas(Modifier.fillMaxSize()){
   val minor=16.dp.toPx();val major=80.dp.toPx()
   var x=0f;while(x<size.width){drawLine(p.gridMinor,Offset(x,0f),Offset(x,size.height),.5.dp.toPx(),cap=StrokeCap.Butt);x+=minor}
   var y=0f;while(y<size.height){drawLine(p.gridMinor,Offset(0f,y),Offset(size.width,y),.5.dp.toPx(),cap=StrokeCap.Butt);y+=minor}
   x=0f;while(x<size.width){drawLine(p.gridMajor,Offset(x,0f),Offset(x,size.height),.7.dp.toPx(),cap=StrokeCap.Butt);x+=major}
   y=0f;while(y<size.height){drawLine(p.gridMajor,Offset(0f,y),Offset(size.width,y),.7.dp.toPx(),cap=StrokeCap.Butt);y+=major}
  }
  Column(Modifier.fillMaxSize().padding(2.dp),verticalArrangement=Arrangement.spacedBy(1.dp)){
   // roof/parapet band, continuous across all four bays
   Row(Modifier.weight(.105f).fillMaxWidth().border(1.dp,p.accentAmber),verticalAlignment=Alignment.CenterVertically){\n    Column(Modifier.width(48.dp).fillMaxHeight().padding(end=2.dp),verticalArrangement=Arrangement.SpaceBetween){LevelMark("+15.200",p);LevelMark("+13.800",p)}
    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=5.dp),verticalArrangement=Arrangement.Center){
     Text("NAGAPLAS OS // C++20 CORE: READY  |  FPS: 60  |  MEM: —",fontSize=9.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis)
     Text("ФАСАД ПРОЕКТНОГО ЦЕНТРА · NAGAPLAS.G3.7-UI-001 ВО",fontSize=7.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,maxLines=1)
    }
    RoofAction("ТЕМА"){BlueprintTheme.next()}
    RoofAction("FULLSCREEN: ON"){onTool("FULLSCREEN")}
    RoofAction("JNI: OK"){onTool("ДИАГНОСТИКА JNI")}
    RoofAction("← МОДУЛИ",accent=true,onClick=onBack)
   }
   // upper floor level: four strict equal bays
   FacadeLevelRow(levelTop="+13.800",levelBottom="+9.600",p=p,modifier=Modifier.weight(.26f)){
    FacadeSlot(Modifier.weight(1f),"ПРОЕКТ №01 [BIM]","Каркас здания","ОТКРЫТЬ",DrawingKind.Frame,p){projects.getOrNull(0)?.let{onOpen(it.id)}?:onOpenLast()}
    FacadeSlot(Modifier.weight(1f),"ПРОЕКТ №02 [CAD]","Ферма МК","ОТКРЫТЬ",DrawingKind.Truss,p){projects.getOrNull(1)?.let{onOpen(it.id)}?:onOpenLast()}
    FacadeSlot(Modifier.weight(1f),"ПРОЕКТ №03 [3D]","Фасад · модель","ОТКРЫТЬ",DrawingKind.Facade,p){projects.getOrNull(2)?.let{onOpen(it.id)}?:onOpenLast()}
    FacadeSlot(Modifier.weight(1f),"АРХИВ / ОБЛАКО [SYNC]","Проекты · синхронизация","ОБЗОР",DrawingKind.Sync,p){onTool("АРХИВ / ОБЛАКО")}
   }
   FacadeLevelRow(levelTop="+9.600",levelBottom="+5.400",p=p,modifier=Modifier.weight(.26f)){
    FacadeSlot(Modifier.weight(1f),"РАСЧЁТ УЗЛОВ [APP]","Геометрия соединений","ЗАПУСК",DrawingKind.Nodes,p){onTool("РАСЧЁТ УЗЛОВ")}
    FacadeSlot(Modifier.weight(1f),"СПЕЦИФИКАЦИИ [APP]","Ведомости материалов","ЗАПУСК",DrawingKind.List,p){onTool("СПЕЦИФИКАЦИИ")}
    FacadeSlot(Modifier.weight(1f),"КОНВЕРТЕР IFC [APP]","Импорт · экспорт BIM","ЗАПУСК",DrawingKind.Convert,p){onTool("КОНВЕРТЕР IFC")}
    FacadeSlot(Modifier.weight(1f),"БИБЛИОТЕКА [LIB]","Элементы · узлы · профили","ЗАПУСК",DrawingKind.Library,p){onTool("БИБЛИОТЕКА")}
   }
   FacadeLevelRow(levelTop="+5.400",levelBottom="+1.200",p=p,modifier=Modifier.weight(.26f)){
    FacadeSlot(Modifier.weight(1f),"ШАБЛОНЫ [TPL]","Стартовые проекты","ОБЗОР",DrawingKind.Template,p){onTool("ШАБЛОНЫ")}
    FacadeSlot(Modifier.weight(2f),"ВХОДНОЙ ПОРТАЛ","Создать новый проект", "+ СОЗДАТЬ НОВЫЙ ПРОЕКТ",DrawingKind.Portal,p,primary=true,onClick=onNew,secondary="БЫСТРЫЙ ИМПОРТ: DWG / DXF / JSON",secondaryClick=onImport)
    FacadeSlot(Modifier.weight(1f),"НАСТРОЙКИ [CFG]","Параметры приложения","ОБЗОР",DrawingKind.Settings,p){onTool("НАСТРОЙКИ")}
   }
   // foundation strip
   Row(Modifier.weight(.105f).fillMaxWidth().border(1.dp,p.linePrimary),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=4.dp),verticalArrangement=Arrangement.Center){
     Text("ПАНЕЛЬ УПРАВЛЕНИЯ СЦЕНОЙ",fontSize=7.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace,maxLines=1)
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){
      StatusPill("СЕТКА: 100мм",p);StatusPill("ПРИВЯЗКА: ВКЛ",p);StatusPill("РЕЖИМ: FULLSCREEN",p)
     }
    }
    RoofAction("ВЫХОД",accent=true,onClick=onBack)
   }
   AxisFooter(p)
  }
 }
}

@Composable private fun FacadeLevelRow(levelTop:String,levelBottom:String,p:com.bimob.app.ui.blueprint.BlueprintPalette,modifier:Modifier=Modifier,content:@Composable RowScope.()->Unit){
 Row(modifier.fillMaxWidth()){
  Column(Modifier.width(48.dp).fillMaxHeight().padding(end=2.dp),verticalArrangement=Arrangement.SpaceBetween){
   LevelMark(levelTop,p);LevelMark(levelBottom,p)
  }
  Row(Modifier.weight(1f).fillMaxHeight().border(1.dp,p.linePrimary),horizontalArrangement=Arrangement.spacedBy(1.dp),content=content)
 }
}
@Composable private fun LevelMark(value:String,p:com.bimob.app.ui.blueprint.BlueprintPalette){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(value,fontSize=7.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace,maxLines=1);Canvas(Modifier.weight(1f).height(4.dp)){drawLine(p.accentAmber,Offset(0f,size.height/2),Offset(size.width,size.height/2),.8.dp.toPx(),cap=StrokeCap.Butt)}}
}
@Composable private fun RoofAction(label:String,accent:Boolean=false,onClick:()->Unit){
 val p=LocalBlueprintPalette.current
 Box(Modifier.padding(2.dp).border(1.dp,if(accent)p.accentAmber else p.linePrimary).clickable(onClick=onClick).padding(horizontal=5.dp,vertical=3.dp),contentAlignment=Alignment.Center){Text(label,fontSize=7.sp,color=if(accent)p.accentAmber else p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=1)}
}
@Composable private fun StatusPill(text:String,p:com.bimob.app.ui.blueprint.BlueprintPalette){
 Box(Modifier.border(1.dp,p.linePrimary).padding(horizontal=4.dp,vertical=2.dp)){Text(text,fontSize=6.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace,maxLines=1)}
}
private enum class DrawingKind{Frame,Truss,Facade,Sync,Nodes,List,Convert,Library,Template,Portal,Settings}
@Composable private fun FacadeSlot(
 modifier:Modifier,title:String,subtitle:String,button:String,kind:DrawingKind,
 p:com.bimob.app.ui.blueprint.BlueprintPalette,primary:Boolean=false,onClick:()->Unit,
 secondary:String?=null,secondaryClick:(()->Unit)?=null
){
 Column(modifier.fillMaxHeight().padding(1.dp).background(if(primary)p.accentAmber.copy(alpha=.08f)else p.bgSurface).border(if(primary)1.5.dp else 1.dp,if(primary)p.accentAmber else p.linePrimary).clickable(onClick=onClick).padding(horizontal=3.dp,vertical=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly){
  VectorMini(kind,p,Modifier.fillMaxWidth().weight(1f))
  Text(title,fontSize=if(primary)9.sp else 7.sp,color=if(primary)p.accentAmber else p.textPrimary,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
  Text(subtitle,fontSize=6.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
  Box(Modifier.border(1.dp,if(primary)p.accentAmber else p.accentCyan).clickable(onClick=onClick).padding(horizontal=5.dp,vertical=2.dp)){Text(button,fontSize=7.sp,color=if(primary)p.accentAmber else p.accentCyan,fontFamily=FontFamily.Monospace,maxLines=1)}
  if(secondary!=null&&secondaryClick!=null)Box(Modifier.border(1.dp,p.linePrimary).clickable(onClick=secondaryClick).padding(horizontal=4.dp,vertical=2.dp)){Text(secondary,fontSize=6.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis)}
 }
}
@Composable private fun VectorMini(kind:DrawingKind,p:com.bimob.app.ui.blueprint.BlueprintPalette,modifier:Modifier){
 Canvas(modifier){
  val w=size.width;val h=size.height;val t=1.dp.toPx();val cyan=p.accentCyan;val amber=p.accentAmber
  fun line(a:Offset,b:Offset,c:androidx.compose.ui.graphics.Color=cyan,sw:Float=t){drawLine(c,a,b,sw,cap=StrokeCap.Butt)}
  fun rect(x:Float,y:Float,ww:Float,hh:Float,c:androidx.compose.ui.graphics.Color=cyan){drawRect(c,Offset(x,y),Size(ww,hh),style=Stroke(t,join=StrokeJoin.Bevel))}
  when(kind){
   DrawingKind.Frame->{val x1=w*.25f;val x2=w*.75f;line(Offset(x1,h*.08f),Offset(x1,h*.92f));line(Offset(x2,h*.08f),Offset(x2,h*.92f));line(Offset(x1,h*.08f),Offset(x2,h*.08f),amber);line(Offset(x1,h*.45f),Offset(x2,h*.45f));line(Offset(x1,h*.92f),Offset(x2,h*.92f),amber);line(Offset(x1,h*.45f),Offset(x2,h*.08f));line(Offset(x1,h*.08f),Offset(x2,h*.45f))}
   DrawingKind.Truss->{val n=5;val dx=w*.72f/(n-1);val left=w*.14f;line(Offset(left,h*.2f),Offset(w-left,h*.2f),amber);line(Offset(left,h*.8f),Offset(w-left,h*.8f),amber);for(i in 0 until n){val x=left+i*dx;line(Offset(x,h*.2f),Offset(x,h*.8f));if(i<n-1){val nx=x+dx;line(Offset(x,h*.2f),Offset(nx,h*.8f));line(Offset(x,h*.8f),Offset(nx,h*.2f))}}}
   DrawingKind.Facade->{rect(w*.15f,h*.1f,w*.7f,h*.8f);for(i in 1..3)line(Offset(w*.15f,h*i/4f),Offset(w*.85f,h*i/4f));for(i in 1..4)line(Offset(w*(.15f+i*.14f),h*.1f),Offset(w*(.15f+i*.14f),h*.9f));line(Offset(w*.45f,h*.9f),Offset(w*.55f,h*.9f),amber)}
   DrawingKind.Sync->{drawCircle(cyan,h*.32f,Offset(w/2,h/2),style=Stroke(t));line(Offset(w*.25f,h*.35f),Offset(w*.72f,h*.35f),amber);line(Offset(w*.72f,h*.35f),Offset(w*.6f,h*.2f),amber);line(Offset(w*.72f,h*.35f),Offset(w*.6f,h*.5f),amber);line(Offset(w*.75f,h*.65f),Offset(w*.28f,h*.65f));line(Offset(w*.28f,h*.65f),Offset(w*.4f,h*.5f));line(Offset(w*.28f,h*.65f),Offset(w*.4f,h*.8f))}
   DrawingKind.Nodes->{for(i in 0..2)for(j in 0..2){val c=Offset(w*(.25f+j*.25f),h*(.2f+i*.3f));drawRect(amber,Offset(c.x-2.dp.toPx(),c.y-2.dp.toPx()),Size(4.dp.toPx(),4.dp.toPx()));if(j<2)line(c,Offset(c.x+w*.25f,c.y));if(i<2)line(c,Offset(c.x,c.y+h*.3f))}}
   DrawingKind.List->{for(i in 0..3){line(Offset(w*.2f,h*(.2f+i*.18f)),Offset(w*.8f,h*(.2f+i*.18f)));drawRect(amber,Offset(w*.12f,h*(.17f+i*.18f)),Size(3.dp.toPx(),3.dp.toPx()))}}
   DrawingKind.Convert->{rect(w*.12f,h*.18f,w*.32f,h*.64f);rect(w*.56f,h*.18f,w*.32f,h*.64f,amber);line(Offset(w*.44f,h*.5f),Offset(w*.56f,h*.5f),amber);line(Offset(w*.53f,h*.42f),Offset(w*.56f,h*.5f),amber);line(Offset(w*.53f,h*.58f),Offset(w*.56f,h*.5f),amber)}
   DrawingKind.Library->{for(i in 0..2){val x=w*(.2f+i*.23f);rect(x,h*.22f,w*.17f,h*.56f);line(Offset(x,h*.22f),Offset(x+w*.17f,h*.78f),amber)}}
   DrawingKind.Template->{rect(w*.2f,h*.18f,w*.6f,h*.64f);line(Offset(w*.2f,h*.5f),Offset(w*.8f,h*.5f));line(Offset(w*.5f,h*.18f),Offset(w*.5f,h*.82f),amber)}
   DrawingKind.Portal->{rect(w*.2f,h*.12f,w*.6f,h*.76f,amber);rect(w*.3f,h*.22f,w*.4f,h*.56f);drawCircle(amber,2.dp.toPx(),Offset(w*.64f,h*.5f))}
   DrawingKind.Settings->{drawCircle(cyan,h*.28f,Offset(w*.5f,h*.5f),style=Stroke(t));drawCircle(amber,h*.12f,Offset(w*.5f,h*.5f));for(i in 0..7){val a=i*Math.PI/4;line(Offset(w*.5f+ kotlin.math.cos(a).toFloat()*h*.28f,h*.5f+kotlin.math.sin(a).toFloat()*h*.28f),Offset(w*.5f+kotlin.math.cos(a).toFloat()*h*.4f,h*.5f+kotlin.math.sin(a).toFloat()*h*.4f),amber)}}
  }
 }
}
@Composable private fun AxisFooter(p:com.bimob.app.ui.blueprint.BlueprintPalette){
 Row(Modifier.height(20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  Spacer(Modifier.width(48.dp))
  Canvas(Modifier.weight(1f).height(8.dp)){val t=1.dp.toPx();drawLine(p.linePrimary,Offset(0f,0f),Offset(size.width,0f),t,cap=StrokeCap.Butt);var x=0f;while(x<size.width){drawLine(p.textMuted,Offset(x,0f),Offset(x-7.dp.toPx(),size.height),.7.dp.toPx(),cap=StrokeCap.Butt);x+=10.dp.toPx()}}
 }
 Row(Modifier.fillMaxWidth().height(20.dp),verticalAlignment=Alignment.CenterVertically){
  Spacer(Modifier.width(48.dp))
  Row(Modifier.weight(1f),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){facadeAxes.forEach{AxisBubble(it,p.accentAmber,Modifier.size(18.dp))}}
 }
}
@Composable private fun AxisBubble(label:String,color:androidx.compose.ui.graphics.Color,modifier:Modifier){
 Box(modifier,contentAlignment=Alignment.Center){Box(Modifier.size(15.dp).border(1.dp,color,CircleShape),contentAlignment=Alignment.Center){Text(label,fontSize=7.sp,color=color,fontFamily=FontFamily.Monospace)}}
}
