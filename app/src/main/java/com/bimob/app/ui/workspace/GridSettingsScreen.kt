package com.bimob.app.ui.workspace

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

data class GridPreferences(
 val enabled:Boolean=true,val snap:Boolean=true,val minorMm:Int=100,val majorEvery:Int=5,
 val background:String="#FAF9F6",val minor:String="#DCE3EA",val major:String="#B8C4D0",
 val minorWidth:Float=.7f,val majorWidth:Float=1.2f,val lineStyle:String="SOLID"
)
object NagaGridSettings {
 val state=MutableStateFlow(GridPreferences())
 private var loaded=false
 fun load(context:Context){if(loaded)return;val p=context.getSharedPreferences("nagaplas_grid",Context.MODE_PRIVATE)
 state.value=GridPreferences(p.getBoolean("enabled",true),p.getBoolean("snap",true),p.getInt("minorMm",100),p.getInt("majorEvery",5),p.getString("bg","#FAF9F6")!!,p.getString("minor","#DCE3EA")!!,p.getString("major","#B8C4D0")!!,p.getFloat("minorWidth",.7f),p.getFloat("majorWidth",1.2f),p.getString("style","SOLID")!!);loaded=true}
 fun update(context:Context,value:GridPreferences){state.value=value;context.getSharedPreferences("nagaplas_grid",Context.MODE_PRIVATE).edit().putBoolean("enabled",value.enabled).putBoolean("snap",value.snap).putInt("minorMm",value.minorMm).putInt("majorEvery",value.majorEvery).putString("bg",value.background).putString("minor",value.minor).putString("major",value.major).putFloat("minorWidth",value.minorWidth).putFloat("majorWidth",value.majorWidth).putString("style",value.lineStyle).apply()}
 fun reset(context:Context)=update(context,GridPreferences())
}
private fun hexColor(s:String,fallback:Color)=runCatching{Color(android.graphics.Color.parseColor(s))}.getOrDefault(fallback)

@Composable fun GridSettingsScreen(onBack:()->Unit){
 val context= LocalContext.current;NagaGridSettings.load(context);val prefs by NagaGridSettings.state.collectAsState()
 var minor by remember(prefs.minorMm){mutableStateOf(prefs.minorMm.toString())}
 var majorEvery by remember(prefs.majorEvery){mutableStateOf(prefs.majorEvery.toString())}
 Column(Modifier.fillMaxSize().background(Color(0xFFFAF9F6)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth().height(42.dp).border(1.dp,Color(0xFF65788B),RoundedCornerShape(6.dp)).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){Text("ОБЩАЯ НАСТРОЙКА · СЕТКА",Modifier.weight(1f),color=Color(0xFF172033),fontSize=12.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold);Text("← НАЗАД",Modifier.clickable{onBack()}.padding(6.dp),color=Color(0xFF0066CC),fontSize=10.sp)}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SettingToggle("СЕТКА",prefs.enabled){NagaGridSettings.update(context,prefs.copy(enabled=it))};SettingToggle("SNAP TO GRID",prefs.snap){NagaGridSettings.update(context,prefs.copy(snap=it))}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SettingField("Шаг минорной (мм)",minor,{minor=it},{it.toIntOrNull()?.let{v->NagaGridSettings.update(context,prefs.copy(minorMm=v.coerceIn(1,10000)))}});SettingField("Мажор через",majorEvery,{majorEvery=it},{it.toIntOrNull()?.let{v->NagaGridSettings.update(context,prefs.copy(majorEvery=v.coerceIn(2,50)))}})}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){ColorSetting("ФОН",prefs.background){NagaGridSettings.update(context,prefs.copy(background=it))};ColorSetting("МИНОР",prefs.minor){NagaGridSettings.update(context,prefs.copy(minor=it))};ColorSetting("МАЖОР",prefs.major){NagaGridSettings.update(context,prefs.copy(major=it))}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){SettingField("Толщина минорной",prefs.minorWidth.toString(),{},{it.toFloatOrNull()?.let{v->NagaGridSettings.update(context,prefs.copy(minorWidth=v.coerceIn(.5f,3f)))}});SettingField("Толщина мажорной",prefs.majorWidth.toString(),{},{it.toFloatOrNull()?.let{v->NagaGridSettings.update(context,prefs.copy(majorWidth=v.coerceIn(.5f,3f)))}})}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("SOLID","DASH","DOT").forEach{s->Text(s,Modifier.weight(1f).border(1.dp,if(prefs.lineStyle==s)Color(0xFF0066CC)else Color(0xFF8A9AA9),RoundedCornerShape(6.dp)).clickable{NagaGridSettings.update(context,prefs.copy(lineStyle=s))}.padding(8.dp),color=Color(0xFF172033),fontSize=10.sp)}}
  Box(Modifier.weight(1f).fillMaxWidth().background(hexColor(prefs.background,Color(0xFFFAF9F6))).border(1.dp,Color(0xFF8A9AA9),RoundedCornerShape(6.dp))){Canvas(Modifier.fillMaxSize()){val step=size.width/32f;for(i in 0..32){val major=i%prefs.majorEvery==0;drawLine(hexColor(if(major)prefs.major else prefs.minor,Color.Gray),androidx.compose.ui.geometry.Offset(i*step,0f),androidx.compose.ui.geometry.Offset(i*step,size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(width=if(major)prefs.majorWidth else prefs.minorWidth,pathEffect=if(prefs.lineStyle=="DASH") androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f,4f)) else if(prefs.lineStyle=="DOT") androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1f,3f)) else null))};for(i in 0..18){val major=i%prefs.majorEvery==0;drawLine(hexColor(if(major)prefs.major else prefs.minor,Color.Gray),androidx.compose.ui.geometry.Offset(0f,i*size.height/18),androidx.compose.ui.geometry.Offset(size.width,i*size.height/18),if(major)prefs.majorWidth else prefs.minorWidth)} }}
  Text("СБРОСИТЬ КАК В REVIT",Modifier.fillMaxWidth().border(1.dp,Color(0xFF0066CC),RoundedCornerShape(6.dp)).clickable{NagaGridSettings.reset(context)}.padding(10.dp),color=Color(0xFF0066CC),fontSize=11.sp,fontWeight=FontWeight.Bold)
 }
}
@Composable private fun SettingToggle(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.weight(1f).border(1.dp,Color(0xFF8A9AA9),RoundedCornerShape(6.dp)).clickable{onChange(!value)}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=10.sp,color=Color(0xFF172033));Checkbox(value,onChange)}}
@Composable private fun SettingField(label:String,value:String,onValue:(String)->Unit,onCommit:(String)->Unit){Column(Modifier.weight(1f).border(1.dp,Color(0xFF8A9AA9),RoundedCornerShape(6.dp)).padding(6.dp)){Text(label,fontSize=9.sp,color=Color(0xFF5C6D7D));OutlinedTextField(value,onValue,modifier=Modifier.fillMaxWidth(),singleLine=true);Button(onClick={onCommit(value)},modifier=Modifier.fillMaxWidth()){Text("ПРИМЕНИТЬ",fontSize=9.sp)}}}
@Composable private fun ColorSetting(label:String,value:String,onChange:(String)->Unit){val colors=listOf("#FAF9F6","#DCE3EA","#B8C4D0","#FFFFFF","#181A1F","#071324","#0066CC","#4DE8F4");Column(Modifier.weight(1f).border(1.dp,Color(0xFF8A9AA9),RoundedCornerShape(6.dp)).padding(5.dp)){Text("$label $value",fontSize=8.sp,color=Color(0xFF172033));Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){colors.take(4).forEach{ColorChip(it,value,onChange)}};Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){colors.drop(4).forEach{ColorChip(it,value,onChange)}}}}
@Composable private fun ColorChip(hex:String,selected:String,onChange:(String)->Unit){Box(Modifier.size(20.dp).background(hexColor(hex,Color.Gray),RoundedCornerShape(3.dp)).border(if(hex==selected)2.dp else .5.dp,Color.DarkGray,RoundedCornerShape(3.dp)).clickable{onChange(hex)})}
