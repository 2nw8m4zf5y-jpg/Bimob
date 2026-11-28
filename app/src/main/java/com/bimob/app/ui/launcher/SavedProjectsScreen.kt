package com.bimob.app.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.ui.blueprint.BlueprintTheme

@Composable
fun SavedProjectsScreen(projectNames:List<String>,onOpen:(String)->Unit,onBack:()->Unit){
 val p=BlueprintTheme.active.value
 Box(Modifier.fillMaxSize().background(p.bgPrimary).padding(10.dp)){
  Column(Modifier.fillMaxSize().border(1.dp,p.linePrimary).background(p.bgSurface).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
    Text("NAGAPLAS · ПРОЕКТЫ",color=p.textPrimary,fontSize=14.sp)
    Box(Modifier.border(1.dp,p.linePrimary).clickable(onClick=onBack).padding(horizontal=10.dp,vertical=6.dp)){Text("← НАЗАД",color=p.accentCyan,fontSize=9.sp)}
   }
   if(projectNames.isEmpty()){
    Box(Modifier.weight(1f).fillMaxWidth(),Alignment.Center){Text("Сохранённых проектов пока нет",color=p.textMuted,fontSize=12.sp)}
   }else{
    Column(Modifier.weight(1f).fillMaxWidth().background(p.bgPrimary).padding(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
     projectNames.sorted().forEach{name->
      Row(Modifier.fillMaxWidth().height(46.dp).border(1.dp,p.linePrimary).background(p.bgSurface).clickable{onOpen(name)}.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
       Column(Modifier.weight(1f)){Text(name,color=p.textPrimary,fontSize=11.sp,maxLines=1);Text("Файл .nagaplas · локальное сохранение",color=p.textMuted,fontSize=8.sp)}
       Text("ОТКРЫТЬ →",color=p.accentCyan,fontSize=9.sp)
      }
     }
    }
   }
   Text("Проекты хранятся локально на устройстве.",color=p.textMuted,fontSize=8.sp,modifier=Modifier.align(Alignment.End))
  }
 }
}
