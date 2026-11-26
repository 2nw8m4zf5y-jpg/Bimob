package com.bimob.app.ui.launcher

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.bimob.app.AppVersion
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette

private data class LauncherModule(val title: String, val detail: String, val active: Boolean)
private val launcherModules = listOf(
 LauncherModule("NAGAPLAS CAD","2D / 3D · ОСИ · УЗЛЫ",true),
 LauncherModule("NAGAPLAS BIM","ИНФОРМАЦИОННАЯ МОДЕЛЬ",false),
 LauncherModule("СМЕТЫ","ВЕДОМОСТИ · РАСЧЁТ",false),
 LauncherModule("АРМАТУРА","КАРКАСЫ · СПЕЦИФИКАЦИИ",false),
 LauncherModule("КОНСТРУКТОР","ЭЛЕМЕНТЫ · СОЕДИНЕНИЯ",false),
 LauncherModule("ГЕНПЛАН","УЧАСТОК · ПОСАДКА",false),
 LauncherModule("ИНЖЕНЕРИЯ","MEP · СЕТИ",false),
 LauncherModule("АРХИВ","ПРОЕКТЫ · БИБЛИОТЕКА",false)
)

@Composable
fun ProgramLauncherScreen(onCad:()->Unit) {
 val context=LocalContext.current
 val density=LocalDensity.current.density
 fun dpPx(value: androidx.compose.ui.unit.Dp)=value.value*density
 fun pxDp(value:Float)= (value/density).dp
 val p=LocalBlueprintPalette.current
 BoxWithConstraints(Modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
  val w=constraints.maxWidth.toFloat()
  val h=constraints.maxHeight.toFloat()
  val towerX=w*.14f
  val jibY=h*.22f
  val cardL=w*.205f
  val cardR=w*.95f
  val gap=w*.014f
  val colW=(cardR-cardL-gap*3)/4
  val centers=List(4){i->cardL+colW*(i+.5f)+gap*i}
  val cardTop=h*.355f
  val cardH=h*.225f
  val rowGap=h*.035f
  val stampW=(w*.52f).coerceAtMost(dpPx(240.dp))
  val stampH=dpPx(42.dp)
  val stampX=w-stampW-dpPx(5.dp)
  val stampY=h-stampH-dpPx(5.dp)
  Canvas(Modifier.fillMaxSize()) {
   val thin=dpPx(1.dp)
   val main=dpPx(2.dp)
   val bevel=Stroke(thin,cap=StrokeCap.Butt,join=StrokeJoin.Bevel)
   // Grid, 20-unit minor / 100-unit major.
   var x=0f
   while(x<=size.width){drawLine(p.gridMinor,Offset(x,0f),Offset(x,size.height),thin*.55f,StrokeCap.Butt);x+=dpPx(20.dp)}
   var y=0f
   while(y<=size.height){drawLine(p.gridMinor,Offset(0f,y),Offset(size.width,y),thin*.55f,StrokeCap.Butt);y+=dpPx(20.dp)}
   x=0f
   while(x<=size.width){drawLine(p.gridMajor,Offset(x,0f),Offset(x,size.height),thin,StrokeCap.Butt);x+=dpPx(100.dp)}
   y=0f
   while(y<=size.height){drawLine(p.gridMajor,Offset(0f,y),Offset(size.width,y),thin,StrokeCap.Butt);y+=dpPx(100.dp)}
   // Double border, all joins explicitly bevelled.
   drawRect(p.linePrimary,style=Stroke(main,cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
   val inset=dpPx(4.dp)
   drawRect(p.linePrimary,Offset(inset,inset),Size(size.width-inset*2,size.height-inset*2),bevel)
   // Tower: regular bays; diagonal ends coincide with chord-axis points.
   val half=dpPx(10.dp)
   val top=jibY+dpPx(3.dp)
   val bottom=h-dpPx(8.dp)
   drawLine(p.linePrimary,Offset(towerX-half,top),Offset(towerX-half,bottom),thin,StrokeCap.Butt)
   drawLine(p.linePrimary,Offset(towerX+half,top),Offset(towerX+half,bottom),thin,StrokeCap.Butt)
   val step=dpPx(34.dp)
   var ty=top
   while(ty+step<=bottom){
    val next=ty+step
    val bay=Path().apply{moveTo(towerX-half,ty);lineTo(towerX+half,next);moveTo(towerX+half,ty);lineTo(towerX-half,next);moveTo(towerX-half,next);lineTo(towerX+half,next)}
    drawPath(bay,p.linePrimary,bevel);ty=next
   }
   if(ty<bottom){
    val endBay=Path().apply{moveTo(towerX-half,ty);lineTo(towerX+half,bottom);moveTo(towerX+half,ty);lineTo(towerX-half,bottom);moveTo(towerX-half,bottom);lineTo(towerX+half,bottom)}
    drawPath(endBay,p.linePrimary,bevel)
   }
   // Jib lattice.
   val jl=dpPx(5.dp);val jr=w*.92f;val jt=jibY-dpPx(12.dp);val jb=jibY
   drawLine(p.linePrimary,Offset(jl,jt),Offset(jr,jt),thin,StrokeCap.Butt)
   drawLine(p.linePrimary,Offset(jl,jb),Offset(jr,jb),main,StrokeCap.Butt)
   val js=dpPx(42.dp);var jx=jl
   while(jx+js<=jr){
    val nx=jx+js
    val bay=Path().apply{moveTo(jx,jt);lineTo(nx,jb);moveTo(jx,jb);lineTo(nx,jt);moveTo(nx,jt);lineTo(nx,jb)}
    drawPath(bay,p.linePrimary,bevel);jx=nx
   }
   if(jx<jr){val end=Path().apply{moveTo(jx,jt);lineTo(jr,jb);moveTo(jx,jb);lineTo(jr,jt);moveTo(jr,jt);lineTo(jr,jb)};drawPath(end,p.linePrimary,bevel)}
   // Symmetric triangular king-post/ties.
   val peak=Offset(towerX,jt-dpPx(35.dp))
   drawLine(p.accentCyan,peak,Offset(jl+dpPx(8.dp),jt),thin,StrokeCap.Butt)
   drawLine(p.accentCyan,peak,Offset(jl+(jr-jl)*.75f,jt),thin,StrokeCap.Butt)
   drawLine(p.accentCyan,Offset(towerX,peak.y),Offset(towerX,jt),thin,StrokeCap.Butt)
   drawLine(p.accentAmber,Offset(towerX,jb),Offset(towerX,top),thin,StrokeCap.Butt)
   // Trolley/spreader, exactly four vertical sling axes.
   val spreadL=cardL-gap*.5f;val spreadR=cardR+gap*.5f;val spreadY=cardTop-dpPx(8.dp)
   drawRect(p.bgSurface,topLeft=Offset(spreadL,spreadY-dpPx(5.dp)),size=Size(spreadR-spreadL,dpPx(10.dp)))
   drawLine(p.accentAmber,Offset(spreadL,spreadY),Offset(spreadR,spreadY),main,StrokeCap.Butt)
   drawLine(p.linePrimary,Offset(spreadL,spreadY-dpPx(5.dp)),Offset(spreadR,spreadY-dpPx(5.dp)),thin,StrokeCap.Butt)
   drawLine(p.linePrimary,Offset(spreadL,spreadY+dpPx(5.dp)),Offset(spreadR,spreadY+dpPx(5.dp)),thin,StrokeCap.Butt)
   centers.forEach{cx->
    drawLine(p.accentAmber,Offset(cx,jb),Offset(cx,spreadY-dpPx(5.dp)),thin,StrokeCap.Butt)
    drawLine(p.accentAmber,Offset(cx,spreadY+dpPx(5.dp)),Offset(cx,cardTop),thin,StrokeCap.Butt)
    drawLine(p.accentAmber,Offset(cx,cardTop+cardH),Offset(cx,cardTop+cardH+rowGap),thin,StrokeCap.Butt)
    
   }
   // Module outlines: exact 4-column equal-width matrix.
  repeat(2){r->repeat(4){col->
   val x0=cardL+col*(colW+gap);val y0=cardTop+r*(cardH+rowGap)
   val active=r==0&&col==0
   drawRect(if(active)p.accentCyan else p.linePrimary,Offset(x0,y0),Size(colW,cardH),Stroke(if(active)dpPx(2.dp) else thin,cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
  }}
  // Compact 2x2 GOST/CAD stamp.
   drawRect(p.linePrimary,Offset(stampX,stampY),Size(stampW,stampH),bevel)
   drawLine(p.linePrimary,Offset(stampX+stampW*.5f,stampY),Offset(stampX+stampW*.5f,stampY+stampH),thin,StrokeCap.Butt)
   drawLine(p.linePrimary,Offset(stampX,stampY+stampH*.5f),Offset(stampX+stampW,stampY+stampH*.5f),thin,StrokeCap.Butt)
  }
  Column(Modifier.fillMaxSize()) {
   Spacer(Modifier.height(pxDp(h*.355f)))
   repeat(2){row->
    Row(Modifier.fillMaxWidth().height(pxDp(cardH)),horizontalArrangement=Arrangement.spacedBy(pxDp(gap))){
     repeat(4){col->
      val m=launcherModules[row*4+col]
      Column(Modifier.weight(1f).fillMaxHeight().background(p.bgSurface,RectangleShape)
       .border(if(m.active)2.dp else 1.dp,if(m.active)p.accentCyan else p.linePrimary,RectangleShape)
       .clickable{if(m.active)onCad()else Toast.makeText(context,"МОДУЛЬ В РАЗРАБОТКЕ",Toast.LENGTH_SHORT).show()}
       .padding(horizontal=2.dp,vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
       Text("0${row*4+col+1}",fontSize=8.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace,maxLines=1)
       Text(m.title,fontSize=(pxDp(colW).value*.12f).coerceIn(6.5f,9f).sp,lineHeight=10.sp,color=if(m.active)p.accentCyan else p.textPrimary,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
       Text(m.detail,fontSize=(colW.toDp().value*.09f).coerceIn(5.5f,7f).sp,lineHeight=7.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
       Text(if(m.active)"[ ОТКРЫТЬ ]" else "[ В РАЗРАБОТКЕ ]",fontSize=(colW.toDp().value*.075f).coerceIn(5f,6.5f).sp,lineHeight=7.sp,color=if(m.active)p.accentCyan else p.accentAmber,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=1)
      }
     }
    }
    if(row==0)Spacer(Modifier.height(pxDp(rowGap)))
   }
   Spacer(Modifier.weight(1f))
   Spacer(Modifier.height(5.dp))
   // Stamp content is kept inside its CAD border, with explicit theme controls.
   Row(Modifier.width(pxDp(stampW)).height(pxDp(stampH*.5f))){
    Box(Modifier.weight(1f).fillMaxHeight().padding(start=4.dp),contentAlignment=Alignment.CenterStart){Text("NAGAPLAS",fontSize=8.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=1)}
    Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center){Text("STAGE H · CAD/BIM",fontSize=6.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,maxLines=1)}
   }
   Row(Modifier.width(pxDp(stampW)).height((stampH*.5f).toDp())){
    Box(Modifier.weight(1f).fillMaxHeight().padding(start=4.dp),contentAlignment=Alignment.CenterStart){Text("ВЕРСИЯ: ${AppVersion.VERSION_LABEL}",fontSize=6.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace,maxLines=1)}
    Row(Modifier.weight(1f).fillMaxHeight(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
     BlueprintTheme.palettes.forEach{theme->Text(theme.label,fontSize=6.sp,color=if(BlueprintTheme.active.value==theme)p.accentAmber else p.textMuted,fontFamily=FontFamily.Monospace,modifier=Modifier.clickable{BlueprintTheme.select(theme)}.padding(horizontal=2.dp))}
    }
   }
  }
 }
}
