package com.bimob.app.ui.launcher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette
import com.bimob.app.ui.blueprint.ThemeButton
private data class Module(val title:String,val subtitle:String,val status:String,val active:Boolean)
private val modules=listOf(Module("NAGAPLAS CAD","2D/3D ЧЕРЧЕНИЕ · УЗЛЫ · ОСИ","ОТКРЫТЬ ФАСАД",true),Module("NAGAPLAS BIM","ИНФОРМАЦИОННАЯ МОДЕЛЬ · IFC","В РАЗРАБОТКЕ",false),Module("УЗЛЫ И КМ","СОПРЯЖЕНИЯ · ФАСОНКИ · БОЛТЫ","ЗАПЛАНИРОВАНО",false),Module("2D ЛИСТЫ ГОСТ","ПЛАНЫ · РАЗРЕЗЫ · КОМПОНОВКА","ЗАПЛАНИРОВАНО",false),Module("РАСЧЁТ МКЭ","ЭПЮРЫ МОМЕНТОВ · ПРОГИБЫ","ЗАПЛАНИРОВАНО",false),Module("СПЕЦИФИКАЦИИ","ВЕДОМОСТИ СТАЛИ И БЕТОНА","ЗАПЛАНИРОВАНО",false),Module("ОБМЕН DXF / IFC","ИМПОРТ И ЭКСПОРТ ФОРМАТОВ","ЗАПЛАНИРОВАНО",false),Module("AI-МОСТ АГЕНТОВ","JSON-RPC · КОМАНДЫ В ЯДРО","АРХИТЕКТУРА STAGE H",false))
@Composable fun ProgramLauncherScreen(onCad:()->Unit){
 val context=LocalContext.current;val p=LocalBlueprintPalette.current
 Row(Modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing)){
  Box(Modifier.weight(.14f).fillMaxHeight()){
   Canvas(Modifier.fillMaxSize()){val x=size.width*.54f;val step=36.dp.toPx();val path=Path().apply{moveTo(x-8.dp.toPx(),0f);lineTo(x+8.dp.toPx(),size.height);moveTo(x+8.dp.toPx(),0f);lineTo(x-8.dp.toPx(),size.height);var y=0f;while(y<size.height){moveTo(x-8.dp.toPx(),y);lineTo(x+8.dp.toPx(),y+step);moveTo(x+8.dp.toPx(),y);lineTo(x-8.dp.toPx(),y+step);moveTo(x-8.dp.toPx(),y);lineTo(x+8.dp.toPx(),y);y+=step};moveTo(x-24.dp.toPx(),0f);lineTo(x+24.dp.toPx(),0f);moveTo(x,0f);lineTo(x+12.dp.toPx(),-14.dp.toPx())};drawPath(path,p.linePrimary,style=Stroke(1.dp.toPx()));drawLine(p.accentAmber,Offset(x,0f),Offset(x,size.height),1.dp.toPx())}
  }
  Column(Modifier.weight(.86f).fillMaxHeight()){
   Row(Modifier.fillMaxWidth().height(44.dp).border(1.dp,p.linePrimary),verticalAlignment=Alignment.CenterVertically){
    Canvas(Modifier.weight(1f).fillMaxHeight()){val path=Path().apply{moveTo(0f,size.height*.2f);lineTo(size.width,size.height*.2f);var x=0f;val step=38.dp.toPx();while(x<size.width){moveTo(x,size.height*.2f);lineTo(x+step/2,size.height*.8f);lineTo(x+step,size.height*.2f);x+=step}};drawPath(path,p.linePrimary,style=Stroke(1.dp.toPx()))}
    Column(Modifier.padding(horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("NAGAPLAS · ЛАУНЧЕР",fontSize=11.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace);Text("C++20 CORE · IDLE",fontSize=7.sp,color=p.textMuted,fontFamily=FontFamily.Monospace)}
    ThemeButton{BlueprintTheme.next()}
   }
   Column(Modifier.weight(1f)){
    repeat(2){row->Row(Modifier.weight(1f).fillMaxWidth()){
     modules.subList(row*4,row*4+4).forEachIndexed{index,m->val axis=index+1+row*4
      Column(Modifier.weight(1f).fillMaxHeight().padding(3.dp).drawBehind{val cx=size.width/2;drawLine(p.accentAmber,Offset(cx,0f),Offset(cx,size.height),1.dp.toPx());drawCircle(p.accentAmber,4.dp.toPx(),Offset(cx,5.dp.toPx()),style=Stroke(1.dp.toPx()));val hook=Path().apply{moveTo(cx-4.dp.toPx(),size.height*.08f);lineTo(cx,size.height*.1f);lineTo(cx+4.dp.toPx(),size.height*.08f)};drawPath(hook,p.accentAmber,style=Stroke(1.dp.toPx()))}.background(p.bgSurface).border(if(m.active)2.dp else 1.dp,if(m.active)p.accentCyan else p.linePrimary.copy(alpha=.65f),RectangleShape).clickable{if(m.active)onCad()else Toast.makeText(context,"Модуль запланирован",Toast.LENGTH_SHORT).show()}.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
       Text("ОСЬ ${axis}",fontSize=8.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace)
       Canvas(Modifier.size(34.dp)){val path=Path().apply{moveTo(size.width*.1f,size.height*.8f);lineTo(size.width*.9f,size.height*.2f);moveTo(size.width*.1f,size.height*.2f);lineTo(size.width*.9f,size.height*.8f)};drawPath(path,p.linePrimary,style=Stroke(1.2.dp.toPx()));drawCircle(p.accentCyan,2.dp.toPx(),Offset(size.width*.1f,size.height*.8f));drawCircle(p.accentCyan,2.dp.toPx(),Offset(size.width*.9f,size.height*.2f))}
       Text(m.title,fontSize=if(m.active)10.sp else 8.sp,color=if(m.active)p.accentCyan else p.textPrimary,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=2)
       Text(m.subtitle,fontSize=6.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=2)
       Text("[ ${m.status} ]",fontSize=7.sp,color=if(m.active)p.accentCyan else p.accentAmber,fontFamily=FontFamily.Monospace,textAlign=TextAlign.Center,maxLines=1)
      }
     }
    }}
   }
   Row(Modifier.fillMaxWidth().height(18.dp).border(1.dp,p.linePrimary),verticalAlignment=Alignment.CenterVertically){Canvas(Modifier.weight(1f).fillMaxHeight()){var x=0f;while(x<size.width){drawLine(p.gridMajor,Offset(x,size.height),Offset(x-8.dp.toPx(),0f),1.dp.toPx());x+=12.dp.toPx()}};Text("УР. ЗЕМЛИ 0.000",fontSize=7.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace)}
  }
 }
}