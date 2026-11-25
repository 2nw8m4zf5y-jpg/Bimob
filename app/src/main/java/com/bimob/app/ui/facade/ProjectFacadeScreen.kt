package com.bimob.app.ui.facade

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.ui.blueprint.BlueprintTheme as BP

data class FacadeProject(val id:String,val name:String,val modified:String,val nodeCount:Int,val active:Boolean=false)

@Composable
fun ProjectFacadeScreen(projects:List<FacadeProject>,onBack:()->Unit,onNew:()->Unit,onOpen:(String)->Unit,onImport:()->Unit,onTool:(String)->Unit,onOpenLast:()->Unit) {
 BoxWithConstraints(Modifier.fillMaxSize().background(BP.BgPrimary).windowInsetsPadding(WindowInsets.safeDrawing)) {
  val width=maxWidth;val height=maxHeight
  Canvas(Modifier.fillMaxSize().drawWithCache {
   val minor=16.dp.toPx();val major=80.dp.toPx()
   val grid=Path().apply{var x=0f;while(x<=size.width){moveTo(x,0f);lineTo(x,size.height);x+=minor};var y=0f;while(y<=size.height){moveTo(0f,y);lineTo(size.width,y);y+=minor}}
   val majorGrid=Path().apply{var x=0f;while(x<=size.width){moveTo(x,0f);lineTo(x,size.height);x+=major};var y=0f;while(y<=size.height){moveTo(0f,y);lineTo(size.width,y);y+=major}}
   val left=size.width*.22f;val right=size.width*.88f;val roof=size.height*.18f;val base=size.height*.83f
   val facade=Path().apply{
    moveTo(left,roof);lineTo(right,roof);lineTo(right,base);lineTo(left,base);close()
    for(i in 1..5){val x=left+(right-left)*i/6;moveTo(x,roof);lineTo(x,base)}
    for(i in 1..4){val y=roof+(base-roof)*i/5;moveTo(left,y);lineTo(right,y)}
    val cx=(left+right)/2;moveTo(cx-18.dp.toPx(),roof);lineTo(cx+18.dp.toPx(),roof);lineTo(cx+18.dp.toPx(),base);lineTo(cx-18.dp.toPx(),base);close()
   }
   val foundation=Path().apply{var x=left;while(x<right){moveTo(x,base);lineTo(x-8,base+12.dp.toPx());x+=12.dp.toPx()}}
   val frame=Path().apply{addRect(androidx.compose.ui.geometry.Rect(8.dp.toPx(),8.dp.toPx(),size.width-8.dp.toPx(),size.height-8.dp.toPx()))}
   val axisX=(0..4).map{left+(right-left)*(it+1)/6}
   val axisPath=Path().apply{axisX.forEach{moveTo(it,roof-34.dp.toPx());lineTo(it,base+10.dp.toPx())}}
   val levelY=(0..4).map{roof+(base-roof)*it/4}
   val levelTicks=Path().apply{levelY.forEach{moveTo(16.dp.toPx(),it);lineTo(left-12.dp.toPx(),it)}}
   onDrawBehind{
    drawPath(grid,BP.GridMinor,style=Stroke(.5.dp.toPx()));drawPath(majorGrid,BP.GridMajor,style=Stroke(1.dp.toPx()))
    drawPath(frame,BP.LinePrimary,style=Stroke(1.dp.toPx()))
    drawPath(axisPath,BP.AccentAmber.copy(alpha=.35f),style=Stroke(1.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),5.dp.toPx()))))
    drawPath(facade,BP.LinePrimary,style=Stroke(2.dp.toPx()));drawPath(foundation,BP.GridMajor,style=Stroke(1.dp.toPx()))
    drawPath(levelTicks,BP.AccentAmber,style=Stroke(1.dp.toPx()))
    drawLine(BP.AccentAmber,Offset(left,base),Offset(right,base),2.dp.toPx())
   }
  }) { }
  Column(Modifier.fillMaxWidth().padding(16.dp)) {
   Text("←  К ВЫБОРУ ПРОГРАММ",Modifier.clickable(onClick=onBack),fontSize=11.sp,color=BP.AccentCyan,fontFamily=FontFamily.Monospace)
   Spacer(Modifier.height(4.dp));Text("NAGAPLAS CAD  ·  WORKSPACE FAÇADE",fontSize=15.sp,color=BP.TextPrimary,fontFamily=FontFamily.Monospace)
  }
  // Project floor: windows/cards arranged in an adaptable row
  Row(Modifier.fillMaxWidth().padding(start=width*.24f,end=width*.12f,top=height*.23f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   FacadeCard("+  НОВЫЙ ПРОЕКТ", "ПУСТАЯ СЦЕНА",true,Modifier.weight(1f),onNew)
   projects.take(2).forEach{p->FacadeCard(p.name,p.modified+" · ${p.nodeCount} УЗЛОВ",p.active,Modifier.weight(1f)){onOpen(p.id)}}
   FacadeCard("↗  ИМПОРТ", "ФАЙЛ ПРОЕКТА",false,Modifier.weight(1f),onImport)
  }
  Column(Modifier.align(Alignment.CenterStart).padding(start=10.dp,top=height*.22f),verticalArrangement=Arrangement.spacedBy(height*.035f)){
   listOf("+12.000","+9.000","+6.000","+3.000","0.000").forEach{level->
    Row(verticalAlignment=Alignment.CenterVertically){
     Canvas(Modifier.size(width=11.dp,height=10.dp)){val flag=Path().apply{moveTo(0f,0f);lineTo(size.width,size.height/2);lineTo(0f,size.height);close()};drawPath(flag,BP.AccentAmber)}
     Text(level,fontSize=8.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(start=width*.24f,end=width*.12f,top=height*.13f),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
   listOf("А","Б","В","Г","Д").forEach{axis->Box(Modifier.size(18.dp).border(1.dp,BP.AccentAmber,CircleShape),contentAlignment=Alignment.Center){Text(axis,fontSize=8.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)}}}
  }
  Row(Modifier.fillMaxWidth().padding(start=width*.24f,end=width*.12f,top=height*.48f),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("КАРКАС / ОСИ","СОПРЯЖЕНИЯ","СЕЧЕНИЯ","АРХИВ").forEach{FacadeCard(it,"CAD TOOLS",false,Modifier.weight(1f)){onTool(it)}}
  }
  Row(Modifier.fillMaxWidth().padding(start=width*.24f,end=width*.12f,top=height*.70f),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("ГОСТ / ISO","ТИПОВЫЕ УЗЛЫ","PDF / DXF","SNAP").forEach{FacadeCard(it,"БИБЛИОТЕКА",false,Modifier.weight(1f)){onTool(it)}}
  }
  Column(Modifier.align(Alignment.BottomCenter).padding(bottom=height*.06f),horizontalAlignment=Alignment.CenterHorizontally){
   Box(Modifier.background(BP.BgSurface).border(2.dp,BP.AccentAmber).clickable(onClick=onOpenLast).padding(horizontal=18.dp,vertical=10.dp)){
    Text("ВХОД В ПОСЛЕДНЮЮ СЦЕНУ  ·  C++20 CORE READY",fontSize=10.sp,color=BP.AccentCyan,fontFamily=FontFamily.Monospace)
   }
  }
  FacadeStamp(Modifier.align(Alignment.BottomEnd).padding(12.dp))
 }
}

@Composable
private fun FacadeCard(title:String,subtitle:String,primary:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
 Column(modifier.heightIn(min=64.dp).background(if(primary)BP.AccentAmber.copy(alpha=.12f) else BP.BgSurface).border(if(primary)2.dp else 1.dp,if(primary)BP.AccentAmber else BP.LinePrimary.copy(alpha=.6f),RectangleShape).clickable(onClick=onClick).padding(5.dp),verticalArrangement=Arrangement.Center){
  Canvas(Modifier.fillMaxWidth().height(22.dp)){val preview=Path().apply{moveTo(0f,size.height*.5f);lineTo(size.width,size.height*.5f);moveTo(size.width*.25f,0f);lineTo(size.width*.25f,size.height);moveTo(size.width*.5f,0f);lineTo(size.width*.5f,size.height);moveTo(size.width*.75f,0f);lineTo(size.width*.75f,size.height);moveTo(size.width*.25f,size.height*.25f);lineTo(size.width*.75f,size.height*.25f);moveTo(size.width*.25f,size.height*.75f);lineTo(size.width*.75f,size.height*.75f)};drawPath(preview,BP.LinePrimary,style=Stroke(1.dp.toPx()));if(primary)drawRect(BP.AccentAmber.copy(alpha=.4f),style=Stroke(1.dp.toPx()))}
  Text(title,fontSize=10.sp,color=if(primary)BP.AccentAmber else BP.TextPrimary,fontFamily=FontFamily.Monospace,maxLines=2,textAlign=TextAlign.Center)
  Text(subtitle,fontSize=8.sp,color=BP.TextMuted,fontFamily=FontFamily.Monospace,maxLines=1,textAlign=TextAlign.Center)
 }
}

@Composable
private fun FacadeStamp(modifier:Modifier){
 Column(modifier.widthIn(min=190.dp).border(1.dp,BP.LinePrimary).background(BP.BgPrimary.copy(alpha=.92f))){
  Text("ПРОЕКТ: NAGAPLAS  |  АВТОР: Nagaplezhev A.",Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.LinePrimary,fontFamily=FontFamily.Monospace)
  Text("ЭКРАН: ФАСАД-НАВИГАТОР CAD",Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.TextPrimary,fontFamily=FontFamily.Monospace)
  Text("СТАДИЯ: Stage H  |  16:9 / ADAPTIVE",Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)
 }
}
