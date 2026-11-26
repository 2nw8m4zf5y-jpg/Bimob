package com.bimob.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.AppVersion
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.ui.blueprint.BlueprintTheme

@Composable
fun AutoCadWorkspaceScreen(projectName:String, editor:NativeEditorViewModel, onBack:()->Unit, onSave:()->Unit, onExport:()->Unit) {
 val p=BlueprintTheme.active.value
 var tab by remember { mutableStateOf("ГЛАВНАЯ") }; var tool by remember { mutableStateOf("ОТРЕЗОК") }
 var sidebar by remember { mutableStateOf(true) }; var grid by remember { mutableStateOf(true) }; var ortho by remember { mutableStateOf(true) }; var snap by remember { mutableStateOf(true) }; var track by remember { mutableStateOf(false) }; var weight by remember { mutableStateOf(false) }
 var layer by remember { mutableStateOf("01_НЕСУЩИЕ_ОСИ") }
 val tools=listOf("ОТРЕЗОК","ПОЛИЛИН","КРУГ","ПРЯМОУГ","УЗЕЛ","ПЕРЕНЕСТИ","КОПИЯ","ПОВЕРНУТЬ","ОБРЕЗАТЬ","СТЕРЕТЬ","РАЗМЕР","ТЕКСТ")
 val layerNames=listOf("01_НЕСУЩИЕ_ОСИ","02_УЗЛЫ_КРЕПЛЕНИЯ","03_РАЗМЕРЫ_ГОСТ","04_ПОДЛОЖКА")
 Column(Modifier.fillMaxSize().background(p.bgPrimary).padding(WindowInsets.safeDrawing.asPaddingValues())) {
  Row(Modifier.fillMaxWidth().height(30.dp).background(p.bgSurface).border(1.dp,p.gridMajor).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){Chip("← ФАСАД",p,p.accentAmber,false,onBack);listOf("ГЛАВНАЯ","АННОТАЦИИ","СЛОИ","ВИД").forEach{Chip(it,p,p.linePrimary,tab==it){tab=it}}}
   androidx.compose.material3.Text(projectName+" · "+AppVersion.VERSION_LABEL,Modifier.weight(1f).padding(horizontal=4.dp),color=p.textPrimary,fontFamily=FontFamily.Monospace,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
   Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){Chip("←",p,p.linePrimary,false){editor.undo()};Chip("→",p,p.linePrimary,false){editor.redo()};Chip("СОХРАНИТЬ",p,p.linePrimary,false,onSave);Chip("ЭКСПОРТ",p,p.linePrimary,false,onExport);Chip("ТЕМА",p,p.accentAmber,false){BlueprintTheme.next()}}
  }
  Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).border(1.dp,p.gridMajor).padding(3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
   tools.forEachIndexed{i,t->if(i==5||i==10)Box(Modifier.width(1.dp).fillMaxHeight().background(p.gridMinor));ToolTile(t,tool==t,p){tool=t}}
   Column(Modifier.width(178.dp).fillMaxHeight().border(1.dp,p.gridMinor).padding(4.dp),verticalArrangement=Arrangement.Center){androidx.compose.material3.Text("АКТИВНЫЙ СЛОЙ",fontSize=7.sp,color=p.textMuted);Chip(layer,p,p.linePrimary,false){layer=layerNames[(layerNames.indexOf(layer)+1)%layerNames.size]}}
  }
  Row(Modifier.weight(1f).fillMaxWidth()) {
   Box(Modifier.weight(1f).fillMaxHeight().background(p.editorCanvas)){
    Canvas(Modifier.fillMaxSize()){
     if(grid){for(x in 0f..size.width step 28f)drawLine(p.editorGridMinor,Offset(x,0f),Offset(x,size.height),.6f,cap=StrokeCap.Butt);for(y in 0f..size.height step 28f)drawLine(p.editorGridMinor,Offset(0f,y),Offset(size.width,y),.6f,cap=StrokeCap.Butt)}
     val a=size.width*.16f;val b=size.width*.44f;val c=size.width*.72f;val y3=size.height*.22f;val y2=size.height*.56f;val y1=size.height*.86f
     val dash=Stroke(1f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel,pathEffect=PathEffect.dashPathEffect(floatArrayOf(12f,5f,2f,5f)))
     listOf(a,b,c).forEach{drawLine(p.accentAmber,Offset(it,y3-30),Offset(it,y1+20),style=dash)};listOf(y3,y2,y1).forEach{drawLine(p.accentAmber,Offset(a-28,it),Offset(c+25,it),style=dash)}
     drawLine(p.textPrimary,Offset(a,y3),Offset(b,y3),2.5f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel);drawLine(p.textPrimary,Offset(a,y3),Offset(a,y2),2.5f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel)
     drawLine(p.linePrimary,Offset(a,y2),Offset(size.width*.63f,y2),2f,cap=StrokeCap.Butt,pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,6f)))
     val dy=y3-18;drawLine(p.linePrimary,Offset(a,dy),Offset(b,dy),1f,cap=StrokeCap.Butt);drawLine(p.linePrimary,Offset(a,dy-4),Offset(a,dy+4),1f,cap=StrokeCap.Butt);drawLine(p.linePrimary,Offset(b,dy-4),Offset(b,dy+4),1f,cap=StrokeCap.Butt)
     listOf(a,b,c).forEach{x->listOf(y3,y2,y1).forEach{y->drawRect(p.editorCanvas,Offset(x-5,y-5),Size(10f,10f));drawRect(p.linePrimary,Offset(x-5,y-5),Size(10f,10f),style=Stroke(1.2f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel))}}
     drawRect(Color(0xFF2EE59D),Offset(a-6,y2-6),Size(12f,12f),style=Stroke(1.5f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
     val cx=size.width*.63f;drawLine(p.textPrimary,Offset(cx-65,y2),Offset(cx+65,y2),1f,cap=StrokeCap.Butt);drawLine(p.textPrimary,Offset(cx,y2-65),Offset(cx,y2+65),1f,cap=StrokeCap.Butt);drawRect(p.linePrimary,Offset(cx-4,y2-4),Size(8f,8f),style=Stroke(1.2f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
     drawLine(Color(0xFFFF5C5C),Offset(25f,size.height-22),Offset(53f,size.height-22),1.6f,cap=StrokeCap.Butt);drawLine(Color(0xFF2EE59D),Offset(25f,size.height-22),Offset(25f,size.height-50),1.6f,cap=StrokeCap.Butt)
    }
    androidx.compose.material3.Text("А     Б     В",Modifier.align(Alignment.TopStart).padding(8.dp),color=p.accentAmber,fontFamily=FontFamily.Monospace,fontSize=8.sp)
    androidx.compose.material3.Text("3\n2\n1",Modifier.align(Alignment.CenterStart).padding(start=5.dp),color=p.accentAmber,fontFamily=FontFamily.Monospace,fontSize=8.sp)
    androidx.compose.material3.Text("6000",Modifier.align(Alignment.TopCenter).padding(top=16.dp),color=p.linePrimary,fontFamily=FontFamily.Monospace,fontSize=8.sp)
    androidx.compose.material3.Text("L: 4250.00 ∠ 0°",Modifier.align(Alignment.Center).offset(x=100.dp,y=(-22).dp).border(1.dp,p.linePrimary).background(p.bgSurface).padding(4.dp),color=p.textPrimary,fontFamily=FontFamily.Monospace,fontSize=8.sp)
   }
   if(sidebar)Column(Modifier.width(196.dp).fillMaxHeight().background(p.bgSurface).border(1.dp,p.gridMajor).padding(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){androidx.compose.material3.Text("PROPERTIES / СЛОИ",fontSize=8.sp,color=p.linePrimary,fontWeight=FontWeight.Bold);androidx.compose.material3.Text("×",Modifier.clickable{sidebar=false},fontSize=12.sp,color=p.textMuted)}
    androidx.compose.material3.Text(editor.state.value.selection?.id?: "Нет выделенного объекта",fontSize=7.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=2)
    androidx.compose.material3.Text("ГЕОМЕТРИЯ · C++20",fontSize=7.sp,color=p.accentAmber,fontWeight=FontWeight.Bold)
    listOf("Начало X: —","Начало Y: —","Длина L: —","Угол ∠: —").forEach{androidx.compose.material3.Text(it,fontSize=7.sp,color=p.textMuted,fontFamily=FontFamily.Monospace)}
    androidx.compose.material3.Text("ДИСПЕТЧЕР СЛОЁВ",fontSize=7.sp,color=p.linePrimary,fontWeight=FontWeight.Bold)
    layerNames.forEach{Chip(it,p,p.linePrimary,layer==it){layer=it}}
   }
  }
  Row(Modifier.fillMaxWidth().height(26.dp).background(p.bgSurface).border(1.dp,p.gridMajor).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){androidx.compose.material3.Text("КОМАНДА:  _"+tool+" Укажите параметры команды:",fontSize=8.sp,color=p.textPrimary,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis);Chip("[AI МОСТ-H]",p,p.accentAmber,false){}}
  Row(Modifier.fillMaxWidth().height(26.dp).horizontalScroll(rememberScrollState()).border(1.dp,p.gridMinor).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){androidx.compose.material3.Text("X: 10250.00 Y: 6000.00 Z: 0.00",fontSize=8.sp,color=p.linePrimary,fontFamily=FontFamily.Monospace);Chip("МОДЕЛЬ",p,p.linePrimary,true){};Chip("СЕТКА [F7]",p,p.linePrimary,grid){grid=!grid};Chip("ОРТО [F8]",p,p.linePrimary,ortho){ortho=!ortho};Chip("OSNAP [F3]",p,p.accentCyan,snap){snap=!snap};Chip("ОТСЛЕЖ",p,p.linePrimary,track){track=!track};Chip("ВЕС ЛИНИЙ",p,p.linePrimary,weight){weight=!weight};Chip("СВОЙСТВА / СЛОИ",p,p.linePrimary,sidebar){sidebar=!sidebar}}
 }
}
@Composable private fun Chip(label:String,p:com.bimob.app.ui.blueprint.BlueprintPalette,accent:Color,active:Boolean,onClick:()->Unit){Box(Modifier.border(.8.dp,if(active)accent else p.gridMinor).background(if(active)accent.copy(alpha=.16f)else p.bgPrimary).clickable(onClick).padding(horizontal=5.dp,vertical=2.dp)){androidx.compose.material3.Text(label,fontSize=7.sp,color=if(active)accent else p.textMuted,fontFamily=FontFamily.Monospace,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)}}
@Composable private fun ToolTile(label:String,selected:Boolean,p:com.bimob.app.ui.blueprint.BlueprintPalette,onClick:()->Unit){Column(Modifier.width(44.dp).fillMaxHeight().border(1.dp,if(selected)p.linePrimary else Color.Transparent).background(if(selected)p.linePrimary.copy(alpha=.15f)else Color.Transparent).clickable(onClick).padding(vertical=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween){Canvas(Modifier.size(18.dp)){val c=p.linePrimary;val st=Stroke(1.4f,cap=StrokeCap.Butt,join=StrokeJoin.Bevel);when(label){"ОТРЕЗОК"->drawLine(c,Offset(2f,size.height-2),Offset(size.width-2,2f),1.5f,cap=StrokeCap.Butt);"КРУГ"->drawCircle(c,size.minDimension/2.4f,style=st);"ПРЯМОУГ"->drawRect(c,Offset(2f,3f),Size(size.width-4,size.height-6),style=st);"УЗЕЛ"->{drawCircle(c,size.minDimension/3,style=st);drawLine(c,Offset(size.width/2,0f),Offset(size.width/2,size.height),1f,cap=StrokeCap.Butt);drawLine(c,Offset(0f,size.height/2),Offset(size.width,size.height/2),1f,cap=StrokeCap.Butt)};else->drawRect(c,Offset(3f,3f),Size(size.width-6,size.height-6),style=st)}};androidx.compose.material3.Text(label,fontSize=6.sp,color=if(selected)p.textPrimary else p.textMuted,fontFamily=FontFamily.Monospace,maxLines=1)}}
