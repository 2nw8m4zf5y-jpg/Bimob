package com.bimob.app.ui.launcher

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
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
  Canvas(Modifier.fillMaxSize().drawWithCache {
   val minor=16.dp.toPx(); val major=80.dp.toPx()
   val grid=Path().apply {
    var x=0f;while(x<=size.width){moveTo(x,0f);lineTo(x,size.height);x+=minor}
    var y=0f;while(y<=size.height){moveTo(0f,y);lineTo(size.width,y);y+=minor}
   }
   val majorGrid=Path().apply {
    var x=0f;while(x<=size.width){moveTo(x,0f);lineTo(x,size.height);x+=major}
    var y=0f;while(y<=size.height){moveTo(0f,y);lineTo(size.width,y);y+=major}
   }
   val crane=Path().apply {
    val tx=size.width*.13f;val top=size.height*.06f;val ground=size.height*.92f
    moveTo(tx-10,top);lineTo(tx+10,ground);moveTo(tx+10,top);lineTo(tx-10,ground)
    var yy=top;while(yy<ground){moveTo(tx-10,yy);lineTo(tx+10,yy);moveTo(tx-10,yy);lineTo(tx+10,yy+34.dp.toPx());moveTo(tx+10,yy);lineTo(tx-10,yy+34.dp.toPx());yy+=34.dp.toPx()}
    moveTo(tx,top+8);lineTo(size.width*.92f,top+8);moveTo(tx,top+8);lineTo(tx+36,top-12);lineTo(size.width*.92f,top+8)
    moveTo(tx-30,top+8);lineTo(tx,top+8)
   }
   val frame=Path().apply{addRect(androidx.compose.ui.geometry.Rect(8.dp.toPx(),8.dp.toPx(),size.width-8.dp.toPx(),size.height-8.dp.toPx()))}
   val hatch=Path().apply{val ground=size.height*.92f;var xx=0f;while(xx<size.width){moveTo(xx,ground);lineTo(xx-10,ground+12.dp.toPx());xx+=14.dp.toPx()}}
   onDrawBehind {
    drawPath(grid,BP.GridMinor,.5.dp.toPx())
    drawPath(majorGrid,BP.GridMajor,1.dp.toPx())
    drawPath(frame,BP.LinePrimary,style=Stroke(1.dp.toPx()))
    drawPath(crane,BP.LinePrimary,2.dp.toPx())
    drawPath(hatch,BP.GridMajor,1.dp.toPx())
    drawLine(BP.AccentAmber,Offset(0f,size.height*.92f),Offset(size.width,size.height*.92f),1.5.dp.toPx())
   }
  }) { }
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
  BlueprintStamp(Modifier.align(Alignment.BottomEnd).padding(14.dp),"ЛАУНЧЕР МОДУЛЕЙ")
 }
}

@Composable
private fun BlueprintStamp(modifier:Modifier,screen:String){
 Column(modifier.widthIn(min=190.dp).border(1.dp,BP.LinePrimary).background(BP.BgPrimary.copy(alpha=.92f))){
  Text("ПРОЕКТ: NAGAPLAS  |  АВТОР: Nagaplezhev A.",Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.LinePrimary,fontFamily=FontFamily.Monospace)
  Text("ЭКРАН: $screen",Modifier.fillMaxWidth().border(width=0.dp,color=BP.LinePrimary).padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.TextPrimary,fontFamily=FontFamily.Monospace)
  Text("СТАДИЯ: Stage H  |  16:9 / ADAPTIVE",Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=3.dp),fontSize=7.sp,color=BP.AccentAmber,fontFamily=FontFamily.Monospace)
 }
}
