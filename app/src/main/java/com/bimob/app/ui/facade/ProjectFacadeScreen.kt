package com.bimob.app.ui.facade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.AppVersion
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette
import com.bimob.app.ui.launcher.HeaderBar
import com.bimob.app.ui.launcher.LauncherModuleSpec
import com.bimob.app.ui.launcher.ModuleTile

data class FacadeProject(val id:String,val name:String,val modified:String,val nodeCount:Int,val active:Boolean=false)

private val projectMenuSpecs=listOf(
 LauncherModuleSpec("01","ACTIVE","+ НОВЫЙ ЧЕРТЁЖ","C++20 · WORKSPACE","СОЗДАТЬ →",true,0),
 LauncherModuleSpec("02","STAGE I","ПРОЕКТ №01 · КАРКАС","ПРОЕКТ · СТРУКТУРА","ОТКРЫТЬ",false,1),
 LauncherModuleSpec("03","PLANNED","ПРОЕКТ №02 · ФЕРМА МК","МЕТАЛЛИЧЕСКАЯ ФЕРМА","ЗАПЛАНИРОВАНО",false,2),
 LauncherModuleSpec("04","PLANNED","ШАБЛОНЫ ГОСТ","ЧЕРТЁЖНЫЕ ШАБЛОНЫ","ЗАПЛАНИРОВАНО",false,3),
 LauncherModuleSpec("05","PLANNED","ИМПОРТ DXF / JSON","ВНЕШНИЕ ФОРМАТЫ","ЗАПЛАНИРОВАНО",false,6),
 LauncherModuleSpec("06","PLANNED","ЭКСПОРТ .NAGAPLAS","ОБМЕН ПРОЕКТАМИ","ЗАПЛАНИРОВАНО",false,5),
 LauncherModuleSpec("07","PLANNED","БИБЛИОТЕКА УЗЛОВ","КОМПОНЕНТЫ · СОЕДИНЕНИЯ","ЗАПЛАНИРОВАНО",false,2),
 LauncherModuleSpec("08","STAGE H","НАСТРОЙКИ СЦЕНЫ","ПАРАМЕТРЫ WORKSPACE","АРХИТЕКТУРА STAGE H",false,7)
)
@Composable fun ProjectFacadeScreen(projects:List<FacadeProject>,onBack:()->Unit,onNew:()->Unit,onOpen:(String)->Unit,onImport:()->Unit,onTool:(String)->Unit,onOpenLast:()->Unit){
 val p=LocalBlueprintPalette.current
 BoxWithConstraints(Modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=10.dp,vertical=6.dp)){
  val gridStep=24.dp
  Canvas(Modifier.fillMaxSize()){
   val step=gridStep.toPx();var x=0f;while(x<size.width){drawLine(p.gridMinor,Offset(x,0f),Offset(x,size.height),.55.dp.toPx(),StrokeCap.Butt);x+=step}
   var y=0f;while(y<size.height){drawLine(p.gridMinor,Offset(0f,y),Offset(size.width,y),.55.dp.toPx(),StrokeCap.Butt);y+=step}
   drawRect(Color(0xFF2C5E8A),style=Stroke(1.dp.toPx(),cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
   drawRect(Color(0xFF4DE8F4).copy(alpha=.5f),topLeft=Offset(4.dp.toPx(),4.dp.toPx()),size=Size(size.width-8.dp.toPx(),size.height-8.dp.toPx()),style=Stroke(1.dp.toPx(),cap=StrokeCap.Butt,join=StrokeJoin.Bevel))
  }
  Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
   HeaderBar("NAGAPLAS CAD · МЕНЮ ПРОЕКТОВ","CORE: C++20 · ${AppVersion.VERSION_LABEL} · WORKSPACE ROUTE",BlueprintTheme.active.value,BlueprintTheme::select,p,Modifier.height(44.dp),onBack)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
    for(row in 0..1)Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
     for(col in 0..3){val idx=row*4+col;val spec=projectMenuSpecs[idx]
      ModuleTile(spec,p,Modifier.weight(1f).fillMaxHeight(),enabled=(idx==0||idx==1),onClick={
       when(idx){
        0->onNew()
        1->projects.getOrNull(0)?.let{onOpen(it.id)}?:onOpenLast()
        4->onImport()
        7->onTool("НАСТРОЙКИ СЦЕНЫ")
        else->onTool(spec.title)
       }
      })
     }
    }
   }
   Text("NAGAPLAS.H-UI-005 ВО · CAD PROJECT MATRIX",color=p.textMuted,fontSize=8.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.align(Alignment.End))
  }
 }
}
