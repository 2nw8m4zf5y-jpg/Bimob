package com.bimob.app.ui.facade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RectangleShape
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

data class FacadeProject(val id:String,val name:String,val modified:String,val nodeCount:Int)

@Composable
fun ProjectFacadeScreen(projects:List<FacadeProject>,onBack:()->Unit,onNew:()->Unit,onOpen:(String)->Unit,onImport:()->Unit,onTool:(String)->Unit,onOpenLast:()->Unit) {
 BoxWithConstraints(Modifier.fillMaxSize().background(BP.BgPrimary)) {
  val width=maxWidth;val height=maxHeight
  Canvas(Modifier.fillMaxSize()){
   val minor=16.dp.toPx();val major=80.dp.toPx()
   for(x in 0..(size.width/minor).toInt())drawLine(BP.GridMinor,Offset(x*minor,0f),Offset(x*minor,size.height),.5.dp.toPx())
   for(y in 0..(size.height/minor).toInt())drawLine(BP.GridMinor,Offset(0f,y*minor),Offset(size.width,y*minor),.5.dp.toPx())
   for(x in 0..(size.width/major).toInt())drawLine(BP.GridMajor,Offset(x*major,0f),Offset(x*major,size.height),1.dp.toPx())
   for(y in 0..(size.height/major).toInt())drawLine(BP.GridMajor,Offset(0f,y*major),Offset(size.width,y*major),1.dp.toPx())
   drawRect(BP.LinePrimary,Offset(8.dp.toPx(),8.dp.toPx()),Size(size.width-16.dp.toPx(),size.height-16.dp.toPx()),Stroke(1.dp.toPx()))
   val left=size.width*.22f;val right=size.width*.88f;val roof=size.height*.18f;val base=size.height*.83f
   drawRect(BP.LinePrimary,Offset(left,roof),Size(right-left,base-roof),Stroke(2.dp.toPx()))
   for(i in 1..5){val x=left+(right-left)*i/6;drawLine(BP.LinePrimary,Offset(x,roof),Offset(x,base),1.dp.toPx())}
   for(i in 1..4){val y=roof+(base-roof)*i/5;drawLine(BP.LinePrimary,Offset(left,y),Offset(right,y),1.dp.toPx())}
   // Central elevator shaft
   val cx=(left+right)/2;drawRect(BP.AccentAmber,Offset(cx-18.dp.toPx(),roof),Size(36.dp.toPx(),base-roof),Stroke(1.5.dp.toPx()))
   drawLine(BP.AccentAmber,Offset(left,base),Offset(right,base),2.dp.toPx())
   for(i in 0..5){val y=roof+(base-roof)*i/5;drawLine(BP.TextMuted,Offset(20.dp.toPx(),y),Offset(left-8.dp.toPx(),y),1.dp.toPx())}
   // Foundation hatch
   var hx=left\n   while(hx<right){drawLine(BP.GridMajor,Offset(hx,base),Offset(hx-8,base+12.dp.toPx()),1.dp.toPx());hx+=12.dp.toPx()}
  }
  Column(Modifier.fillMaxWidth().padding(16.dp)) {
   Text("←  К ВЫБОРУ ПРОГРАММ",Modifier.clickable(onClick=onBack),fontSize=11.sp,color=BP.AccentCyan,fontFamily=FontFamily.Monospace)
   Spacer(Modifier.height(4.dp));Text("NAGAPLAS CAD  ·  WORKSPACE FAÇADE",fontSize=15.sp,color=BP.TextPrimary,fontFamily=FontFamily.Monospace)
  }
  // Project floor: windows/cards arranged in an adaptable row
  Row(Modifier.fillMaxWidth().padding(start=width*.24f,end=width*.12f,top=height*.23f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   FacadeCard("+  НОВЫЙ ПРОЕКТ", "ПУСТАЯ СЦЕНА",true,Modifier.weight(1f),onNew)
   projects.take(2).forEach{p->FacadeCard(p.name,p.modified+" · ${p.nodeCount} УЗЛОВ",false,Modifier.weight(1f)){onOpen(p.id)}}
   FacadeCard("↗  ИМПОРТ", "ФАЙЛ ПРОЕКТА",false,Modifier.weight(1f),onImport)
  }
  Column(Modifier.align(Alignment.CenterStart).padding(start=12.dp,top=height*.32f),verticalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("+12.000","+9.000","+6.000","+3.000","0.000").forEach{Text(it,fontSize=9.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)}
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
  Text("ЛИСТ 1  |  ФАСАД-НАВИГАТОР  |  STAGE H",Modifier.align(Alignment.BottomEnd).padding(12.dp),fontSize=8.sp,color=BP.TextMuted,fontFamily=FontFamily.Monospace)
 }
}

@Composable
private fun FacadeCard(title:String,subtitle:String,primary:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
 Column(modifier.heightIn(min=56.dp).background(BP.BgSurface).border(if(primary)2.dp else 1.dp,if(primary)BP.AccentCyan else BP.LinePrimary.copy(alpha=.6f),RectangleShape).clickable(onClick=onClick).padding(6.dp),verticalArrangement=Arrangement.Center){
  Text(title,fontSize=10.sp,color=if(primary)BP.AccentCyan else BP.TextPrimary,fontFamily=FontFamily.Monospace,maxLines=2,textAlign=TextAlign.Center)
  Text(subtitle,fontSize=8.sp,color=BP.TextMuted,fontFamily=FontFamily.Monospace,maxLines=1,textAlign=TextAlign.Center)
 }
}
