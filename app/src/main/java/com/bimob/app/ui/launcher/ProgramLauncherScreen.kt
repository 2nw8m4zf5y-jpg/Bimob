package com.bimob.app.ui.launcher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.AppVersion
import com.bimob.app.ui.blueprint.BlueprintPalette
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.LocalBlueprintPalette

data class LauncherModuleSpec(val code:String,val statusBadge:String,val title:String,val subtitle:String,val actionLabel:String,val isActive:Boolean,val iconType:Int)
fun launcherModuleSpecs()=listOf(
 LauncherModuleSpec("01","ACTIVE","NAGAPLAS CAD","2D / 3D · ЧЕРЧЕНИЕ","ОТКРЫТЬ →",true,0),
 LauncherModuleSpec("02","STAGE I","NAGAPLAS BIM","ИНФОРМАЦИОННАЯ МОДЕЛЬ","В РАЗРАБОТКЕ",false,1),
 LauncherModuleSpec("03","PLANNED","УЗЛЫ И КМ","СОПРЯЖЕНИЯ · ФАСОНКИ","ЗАПЛАНИРОВАНО",false,2),
 LauncherModuleSpec("04","PLANNED","2D ЛИСТЫ ГОСТ","ПЛАНЫ · РАЗРЕЗЫ","ЗАПЛАНИРОВАНО",false,3),
 LauncherModuleSpec("05","PLANNED","РАСЧЁТ МКЭ","АНАЛИЗ КОНСТРУКЦИЙ","ЗАПЛАНИРОВАНО",false,4),
 LauncherModuleSpec("06","PLANNED","СПЕЦИФИКАЦИИ","ВЕДОМОСТИ МАТЕРИАЛОВ","ЗАПЛАНИРОВАНО",false,5),
 LauncherModuleSpec("07","PLANNED","ОБМЕН DXF / IFC","ИМПОРТ · ЭКСПОРТ","ЗАПЛАНИРОВАНО",false,6),
 LauncherModuleSpec("08","STAGE H","AI-МОСТ АГЕНТОВ","JSON-RPC · CORE","АРХИТЕКТУРА STAGE H",false,7)
)
@Composable fun ProgramLauncherScreen(onCad:()->Unit){ProgramLauncherScreen(BlueprintTheme.active.value,AppVersion.VERSION_LABEL,BlueprintTheme::select,onCad)}
@Composable fun ProgramLauncherScreen(currentTheme:BlueprintPalette,versionLabel:String,onSelectTheme:(BlueprintPalette)->Unit,onOpenCadFacade:()->Unit,modifier:Modifier=Modifier){
 val p=LocalBlueprintPalette.current
 BoxWithConstraints(modifier.fillMaxSize().background(p.bgPrimary).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=10.dp,vertical=6.dp)){
  val gap=if(maxWidth>900.dp)24.dp else 12.dp
  Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
   HeaderBar("NAGAPLAS · ИНЖЕНЕРНЫЕ МОДУЛИ","CORE: C++20 · READY  |  ${versionLabel}",currentTheme,onSelectTheme,p,Modifier.height(44.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(gap)){
    for(row in 0..1) Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)){
     for(col in 0..3){val spec=launcherModuleSpecs()[row*4+col];ModuleTile(spec,p,Modifier.weight(1f).fillMaxHeight(),onClick={if(spec.isActive)onOpenCadFacade()})}
    }
   }
   Text("NAGAPLAS.H-UI-005 ВО · ENGINEERING MODULE MATRIX",color=p.textMuted,fontSize=8.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.align(Alignment.End))
  }
 }
}
@Composable internal fun HeaderBar(title:String,telemetry:String,currentTheme:BlueprintPalette,onSelectTheme:(BlueprintPalette)->Unit,p:BlueprintPalette,modifier:Modifier=Modifier,back:(()->Unit)?=null){
 Row(modifier.fillMaxWidth().border(1.dp,p.linePrimary).background(p.bgSurface).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.Center){Text(title,color=p.textPrimary,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(telemetry,color=p.textMuted,fontFamily=FontFamily.Monospace,fontSize=8.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
  if(back!=null)Box(Modifier.padding(end=8.dp).border(1.dp,p.accentCyan).clickable(onClick=back).padding(horizontal=6.dp,vertical=4.dp)){Text("← МОДУЛИ",fontSize=8.sp,color=p.accentCyan,fontFamily=FontFamily.Monospace)}
  ThemeMatrix(currentTheme,onSelectTheme,p)
 }
}
@Composable internal fun ThemeMatrix(current:BlueprintPalette,onSelect:(BlueprintPalette)->Unit,p:BlueprintPalette){
 Row(Modifier.border(1.dp,p.linePrimary).padding(horizontal=5.dp,vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){BlueprintTheme.palettes.forEach{theme->val label=when(theme.id){"blue"->"BLUE";"dark"->"DARK";else->"CAD"};Text(label,color=if(current.id==theme.id)p.accentAmber else p.textMuted,fontSize=8.sp,fontFamily=FontFamily.Monospace,fontWeight=if(current.id==theme.id)FontWeight.Bold else FontWeight.Normal,modifier=Modifier.clickable{onSelect(theme)})}}
}
@Composable internal fun ModuleTile(spec:LauncherModuleSpec,p:BlueprintPalette,modifier:Modifier=Modifier,onClick:()->Unit){
 val active=spec.statusBadge=="ACTIVE";val stageH=spec.statusBadge=="STAGE H";val stageI=spec.statusBadge=="STAGE I"
 val edge=when{active->Color(0xFF4DE8F4);stageH->Color(0xFFF2B949);stageI->Color(0xFF2C5E8A);else->Color(0xFF2C5E8A)}
 Column(modifier.border(if(active)2.dp else 1.dp,edge).background(p.bgPrimary).clickable(enabled=active,onClick=onClick)){
  Row(Modifier.fillMaxWidth().background(if(active)Color(0xFF123852)else p.bgSurface).padding(horizontal=6.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Text(if(active)"● ${spec.code} · ${spec.statusBadge}" else "${spec.code} · ${spec.statusBadge}",fontSize=8.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,color=edge,maxLines=1,overflow=TextOverflow.Ellipsis)}
  Column(Modifier.fillMaxSize().padding(horizontal=5.dp,vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly){
   ModuleGlyph(spec.iconType,if(active||stageI)Color(0xFF4DE8F4)else if(stageH)Color(0xFFF2B949)else Color(0xFF6E92B5),Modifier.size(48.dp))
   Text(spec.title,color=p.textPrimary,fontSize=10.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text(spec.subtitle,color=p.textMuted,fontSize=7.sp,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis)
   Box(Modifier.border(1.dp,if(active)Color(0xFF4DE8F4)else edge).background(if(active)Color(0xFF123852)else Color.Transparent).padding(horizontal=5.dp,vertical=2.dp)){Text(spec.actionLabel,color=if(active)Color(0xFF4DE8F4)else if(stageI)Color(0xFFF2B949)else p.textMuted,fontSize=7.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}
  }
 }
}
@Composable internal fun ModuleGlyph(kind:Int,color:Color,modifier:Modifier=Modifier){
 Canvas(modifier){val w=size.width;val h=size.height;val s=Stroke(1.7.dp.toPx(),cap=StrokeCap.Butt,join=StrokeJoin.Bevel);val amber=Color(0xFFF2B949)
 fun l(a:Offset,b:Offset,c:Color=color){drawLine(c,a,b,1.6.dp.toPx(),cap=StrokeCap.Butt)}
 fun r(x:Float,y:Float,ww:Float,hh:Float,c:Color=color){drawRect(c,Offset(x,y),Size(ww,hh),style=s)}
 when(kind){
  0->{r(w*.15f,h*.15f,w*.7f,h*.7f);l(Offset(w*.2f,h*.8f),Offset(w*.8f,h*.2f),amber);l(Offset(w*.2f,h*.8f),Offset(w*.8f,h*.8f))}
  1->{r(w*.24f,h*.24f,w*.48f,h*.48f);r(w*.34f,h*.14f,w*.48f,h*.48f,amber);l(Offset(w*.24f,h*.24f),Offset(w*.34f,h*.14f))}
  2->{for(i in 0..2){val x=w*(.2f+i*.3f);l(Offset(x,h*.2f),Offset(x,h*.8f));if(i<2)l(Offset(x,h*.2f),Offset(x+w*.3f,h*.8f),amber)}}
  3->{r(w*.15f,h*.15f,w*.7f,h*.7f);l(Offset(w*.5f,h*.15f),Offset(w*.5f,h*.85f),amber);l(Offset(w*.15f,h*.5f),Offset(w*.85f,h*.5f))}
  4->{l(Offset(w*.2f,h*.8f),Offset(w*.5f,h*.2f),amber);l(Offset(w*.5f,h*.2f),Offset(w*.8f,h*.8f));l(Offset(w*.2f,h*.8f),Offset(w*.8f,h*.8f));l(Offset(w*.2f,h*.5f),Offset(w*.8f,h*.5f))}
  5->{for(i in 0..3){val y=h*(.2f+i*.18f);l(Offset(w*.25f,y),Offset(w*.75f,y));drawCircle(amber,2.dp.toPx(),Offset(w*.18f,y))}}
  6->{r(w*.15f,h*.2f,w*.25f,h*.6f);r(w*.6f,h*.2f,w*.25f,h*.6f,amber);l(Offset(w*.42f,h*.5f),Offset(w*.58f,h*.5f),amber)}
  else->{drawCircle(color,h*.32f,Offset(w*.5f,h*.5f),style=s);drawCircle(amber,h*.12f,Offset(w*.5f,h*.5f),style=s);for(i in 0..7){val a=i*Math.PI/4;l(Offset(w*.5f+kotlin.math.cos(a).toFloat()*h*.3f,h*.5f+kotlin.math.sin(a).toFloat()*h*.3f),Offset(w*.5f+kotlin.math.cos(a).toFloat()*h*.43f,h*.5f+kotlin.math.sin(a).toFloat()*h*.43f),amber)}}
 }
}
}
