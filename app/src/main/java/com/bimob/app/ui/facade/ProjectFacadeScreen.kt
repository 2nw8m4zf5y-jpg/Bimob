package com.bimob.app.ui.facade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette
import com.bimob.app.ui.blueprint.ThemeButton
data class FacadeProject(val id:String,val name:String,val modified:String,val nodeCount:Int,val active:Boolean=false)
@Composable fun ProjectFacadeScreen(projects:List<FacadeProject>,onBack:()->Unit,onNew:()->Unit,onOpen:(String)->Unit,onImport:()->Unit,onTool:(String)->Unit,onOpenLast:()->Unit){
 val p=LocalBlueprintPalette.current
 Column(Modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing)){
  Row(Modifier.height(42.dp).fillMaxWidth().border(1.dp,p.linePrimary),verticalAlignment=Alignment.CenterVertically){
   Text("← МОДУЛИ",Modifier.clickable(onClick=onBack).padding(horizontal=8.dp),fontSize=9.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace)
   Spacer(Modifier.weight(1f));Text("NAGAPLAS CAD · ФАСАД ПРОЕКТОВ",fontSize=11.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace);Spacer(Modifier.weight(1f));ThemeButton{BlueprintTheme.next()}
  }
  Row(Modifier.height(22.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Spacer(Modifier.width(54.dp))
   Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){
    Row(Modifier.weight(2f)){listOf("А","Б").forEach{AxisBubble(it,p.accentAmber,Modifier.weight(1f))}}
    AxisBubble("В",p.accentAmber,Modifier.width(76.dp))
    Row(Modifier.weight(2f)){listOf("Г","Д").forEach{AxisBubble(it,p.accentAmber,Modifier.weight(1f))}}
   }
  }
  Row(Modifier.weight(1f).fillMaxWidth()){
   Column(Modifier.width(54.dp).fillMaxHeight(),verticalArrangement=Arrangement.SpaceBetween){listOf("+12.000","+9.000","+6.000","+3.000","0.000").forEach{mark->Row(verticalAlignment=Alignment.CenterVertically){Text(mark,fontSize=7.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace);Canvas(Modifier.width(6.dp).height(5.dp)){drawLine(p.accentAmber,Offset(0f,0f),Offset(size.width,size.height/2),1.dp.toPx())}}}}
   Row(Modifier.weight(1f).fillMaxHeight().border(2.dp,p.linePrimary)){
    Column(Modifier.weight(2f).fillMaxHeight()){
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("+ СОЗДАТЬ ПРОЕКТ","НОВАЯ CAD-СЦЕНА",true,Modifier.weight(1f),onNew);FacadeCard("★ АКТИВНЫЙ ПРОЕКТ",projects.firstOrNull{it.active}?.name?: "Продолжить",true,Modifier.weight(1f)){projects.firstOrNull{it.active}?.let{onOpen(it.id)}?:onOpenLast()}}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("ШАБЛОН 01","СЕТКА ОСЕЙ",false,Modifier.weight(1f)){onTool("СЕТКА ОСЕЙ")};FacadeCard("ШАБЛОН 02","СОПРЯЖЕНИЕ УЗЛОВ",false,Modifier.weight(1f)){onTool("СОПРЯЖЕНИЕ УЗЛОВ")}}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("СОРТАМЕНТ ГОСТ / ISO","БАЗА СЕЧЕНИЙ",false,Modifier.weight(1f)){onTool("СОРТАМЕНТ ГОСТ / ISO")};FacadeCard("ТИПОВЫЕ УЗЛЫ","БИБЛИОТЕКА ФРАГМЕНТОВ",false,Modifier.weight(1f)){onTool("ТИПОВЫЕ УЗЛЫ")}}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("НАСТРОЙКИ ГРАФИКИ","ЛИНИИ · МАСШТАБ · FULLSCREEN",false,Modifier.fillMaxWidth()){onTool("НАСТРОЙКИ ГРАФИКИ")}}
    }
    Column(Modifier.width(76.dp).fillMaxHeight().border(1.dp,p.accentAmber)){
     repeat(3){Column(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("ЯДРО",fontSize=8.sp,color=p.textMuted,fontFamily=FontFamily.Monospace);Text("C++20",fontSize=9.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace);Text("READY",fontSize=8.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace)}}
     Column(Modifier.weight(1f).fillMaxWidth().clickable(onClick=onOpenLast).border(1.dp,p.linePrimary),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("ВХОД",fontSize=8.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace);Text("В СЦЕНУ",fontSize=7.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace);Canvas(Modifier.size(18.dp)){drawCircle(p.accentAmber,2.dp.toPx());drawCircle(p.accentAmber,2.dp.toPx(),Offset(size.width,size.height));drawCircle(p.accentAmber,2.dp.toPx(),Offset(size.width/2,size.height/2))}}
    }
    Column(Modifier.weight(2f).fillMaxHeight()){
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("ПРОЕКТ #02",projects.getOrNull(1)?.name?: "Каркас · Секция Б",false,Modifier.weight(1f)){projects.getOrNull(1)?.let{onOpen(it.id)}?:onOpenLast()};FacadeCard("[↗] ИМПОРТ ФАЙЛА","JSON / CAD",false,Modifier.weight(1f),onImport)}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("ШАБЛОН 03","СЕЧЕНИЯ И ПРОФИЛИ",false,Modifier.weight(1f)){onTool("СЕЧЕНИЯ И ПРОФИЛИ")};FacadeCard("АРХИВ","ВСЕ ПРОЕКТЫ",false,Modifier.weight(1f)){onTool("ВСЕ ПРОЕКТЫ")}}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("ЭКСПОРТ ЧЕРТЕЖЕЙ","JSON · DXF · PDF",false,Modifier.weight(1f)){onTool("ЭКСПОРТ")};FacadeCard("НАСТРОЙКИ ПРИВЯЗОК","OSNAP · ШАГ · ЕДИНИЦЫ",false,Modifier.weight(1f)){onTool("SNAP")}}
     Row(Modifier.weight(1f).fillMaxWidth().border(1.dp,p.linePrimary)){FacadeCard("ДИАГНОСТИКА C++20","JNI · ПАМЯТЬ · КОМАНДЫ",false,Modifier.fillMaxWidth()){onTool("ДИАГНОСТИКА")}}
    }
   }
  }
  Row(Modifier.height(16.dp).fillMaxWidth().border(1.dp,p.linePrimary),verticalAlignment=Alignment.CenterVertically){Canvas(Modifier.weight(1f).fillMaxHeight()){var x=0f;while(x<size.width){drawLine(p.gridMajor,Offset(x,size.height),Offset(x-8.dp.toPx(),0f),1.dp.toPx());x+=12.dp.toPx()}};Text("ФУНДАМЕНТ · 0.000",fontSize=7.sp,color=p.accentAmber,fontFamily=FontFamily.Monospace)}
 }
}
@Composable private fun AxisBubble(label:String,color:androidx.compose.ui.graphics.Color,modifier:Modifier){Box(modifier.fillMaxHeight(),contentAlignment=Alignment.Center){Box(Modifier.size(15.dp).border(1.dp,color,CircleShape),contentAlignment=Alignment.Center){Text(label,fontSize=7.sp,color=color,fontFamily=FontFamily.Monospace)}}}
@Composable private fun FacadeCard(title:String,subtitle:String,primary:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
 val p=LocalBlueprintPalette.current
 Column(modifier.fillMaxSize().padding(2.dp).background(if(primary)p.accentAmber.copy(alpha=.10f)else p.bgSurface).border(if(primary)1.5.dp else 1.dp,if(primary)p.accentAmber else p.linePrimary.copy(alpha=.75f),RectangleShape).clickable(onClick).padding(4.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
  Canvas(Modifier.fillMaxWidth().height(20.dp)){val cx=size.width/2;val cy=size.height/2;drawLine(p.linePrimary,Offset(0f,cy),Offset(size.width,cy),1.dp.toPx());drawLine(p.linePrimary,Offset(cx,0f),Offset(cx,size.height),1.dp.toPx());drawRect(p.accentCyan.copy(alpha=.35f),style=Stroke(1.dp.toPx()))}
  Text(title,fontSize=if(primary)8.sp else 7.sp,color=if(primary)p.accentAmber else p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=2,textAlign=TextAlign.Center)
  Text(subtitle,fontSize=6.sp,color=p.textMuted,fontFamily=FontFamily.Monospace,maxLines=2,textAlign=TextAlign.Center)
 }
}