package com.bimob.app.ui.launcher

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bimob.app.ui.blueprint.BlueprintTheme as BP

private data class Module(val title:String,val status:String,val active:Boolean)
private val modules=listOf(
 Module("NAGAPLAS CAD","АКТИВЕН",true),Module("NAGAPLAS BIM","В РАЗРАБОТКЕ",false),
 Module("КОНСТРУКТОР УЗЛОВ","В РАЗРАБОТКЕ",false),Module("2D ЧЕРТЁЖНИК","В РАЗРАБОТКЕ",false),
 Module("РАСЧЁТ НАГРУЗОК","СКОРО",false),Module("СПЕЦИФИКАЦИИ ГОСТ","СКОРО",false),
 Module("КОНВЕРТЕР IFC/DXF","СКОРО",false),Module("AI-АССИСТЕНТ (МОСТ)","STAGE H",false)
)

@Composable
fun ProgramLauncherScreen(onCad:()->Unit) {
 val context=LocalContext.current
 BoxWithConstraints(Modifier.fillMaxSize().background(BP.BgPrimary).windowInsetsPadding(WindowInsets.safeDrawing)) {
  val w=maxWidth; val h=maxHeight
  Canvas(Modifier.fillMaxSize()) {
   val minor=16.dp.toPx(); val major=80.dp.toPx()
   for(x in 0..(size.width/minor).toInt()) drawLine(BP.GridMinor,Offset(x*minor,0f),Offset(x*minor,size.height),.5.dp.toPx())
   for(y in 0..(size.height/minor).toInt()) drawLine(BP.GridMinor,Offset(0f,y*minor),Offset(size.width,y*minor),.5.dp.toPx())
   for(x in 0..(size.width/major).toInt()) drawLine(BP.GridMajor,Offset(x*major,0f),Offset(x*major,size.height),1.dp.toPx())
   for(y in 0..(size.height/major).toInt()) drawLine(BP.GridMajor,Offset(0f,y*major),Offset(size.width,y*major),1.dp.toPx())
   drawRect(BP.LinePrimary,topLeft=Offset(8.dp.toPx(),8.dp.toPx()),size=Size(size.width-16.dp.toPx(),size.height-16.dp.toPx()),style=Stroke(1.dp.toPx()))
   val towerX=size.width*.13f; val top=size.height*.06f; val ground=size.height*.92f
   drawLine(BP.LinePrimary,Offset(towerX-10,top),Offset(towerX+10,ground),2.dp.toPx())
   drawLine(BP.LinePrimary,Offset(towerX+10,top),Offset(towerX-10,ground),2.dp.toPx())
   var y=top
   while(y<ground){
    drawLine(BP.LinePrimary,Offset(towerX-10,y),Offset(towerX+10,y),1.2.dp.toPx())
    drawLine(BP.LinePrimary,Offset(towerX-10,y),Offset(towerX+10,y+34.dp.toPx()),1.dp.toPx())
    drawLine(BP.LinePrimary,Offset(towerX+10,y),Offset(towerX-10,y+34.dp.toPx()),1.dp.toPx())
    y += 34.dp.toPx()
   }
   // Crane jib and counterweight
   drawLine(BP.LinePrimary,Offset(towerX,top+8),Offset(size.width*.92f,top+8),2.dp.toPx())
   drawLine(BP.LinePrimary,Offset(towerX,top+8),Offset(towerX+36,top-12),2.dp.toPx())
   drawLine(BP.LinePrimary,Offset(towerX+36,top-12),Offset(size.width*.92f,top+8),1.dp.toPx())
   drawLine(BP.LinePrimary,Offset(towerX-30,top+8),Offset(towerX,top+8),4.dp.toPx())
   drawLine(BP.AccentAmber,Offset(0f,ground),Offset(size.width,ground),1.5.dp.toPx())
   // Ground hatch
   for(x in 0..(size.width/14.dp.toPx()).toInt())drawLine(BP.GridMajor,Offset(x*14.dp.toPx(),ground),Offset(x*14.dp.toPx()-10,ground+12.dp.toPx()),1.dp.toPx())
  }
  Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp)) {
   Text("NAGAPLAS  /  PROGRAM LAUNCHER",color=BP.AccentCyan,fontFamily=FontFamily.Monospace,fontSize=12.sp)
   Spacer(Modifier.height(4.dp))
   Text("ВЫБОР ПРОГРАММЫ",color=BP.TextPrimary,fontFamily=FontFamily.Monospace,fontSize=20.sp)
   Spacer(Modifier.height(4.dp))
   Text("КРАНОВАЯ СИСТЕМА · РОВНЫЕ РЯДЫ",color=BP.TextMuted,fontFamily=FontFamily.Monospace,fontSize=10.sp)
  }
  val cardWidth=(w-72.dp)/4
  Column(Modifier.fillMaxWidth().padding(start=w*.18f,end=24.dp,top=h*.24f,bottom=h*.13f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   modules.chunked(4).forEachIndexed{row,items->
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
     items.forEachIndexed{col,m->
      var entered by remember { mutableStateOf(false) }
      LaunchedEffect(Unit){entered=true}
      val dy by animateDpAsState(if(entered)0.dp else 12.dp,animationSpec=spring(dampingRatio=.75f),label="drop")
      Column(Modifier.weight(1f).heightIn(min=64.dp).offset(y=dy).background(BP.BgSurface).border(if(m.active)2.dp else 1.dp,if(m.active)BP.AccentCyan else BP.LinePrimary.copy(alpha=.45f),RectangleShape).clickable {
       if(m.active)onCad() else Toast.makeText(context,"Модуль запланирован на следующий этап после финализации CAD-ядра",Toast.LENGTH_SHORT).show()
      }.padding(8.dp),verticalArrangement=Arrangement.Center){
       Text("ОСЬ ${col+1}  /  ${if(row==0)"+8.400" else "+4.200"}",fontSize=9.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)
       Text(m.title,fontSize=if(w<700.dp)9.sp else 11.sp,color=if(m.active)BP.AccentCyan else BP.TextPrimary,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Start)
       Text(m.status,fontSize=8.sp,color=if(m.active)BP.AccentCyan else BP.TextMuted,fontFamily=FontFamily.Monospace)
      }
     }
    }
   }
  }
  Text("ПРОЕКТ: NAGAPLAS   |   ЭКРАН: ВЫБОР МОДУЛЯ",Modifier.align(Alignment.BottomEnd).padding(16.dp),fontSize=9.sp,color=BP.TextMuted,fontFamily=FontFamily.Monospace)
 }
}
