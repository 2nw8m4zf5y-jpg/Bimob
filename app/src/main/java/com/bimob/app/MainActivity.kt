package com.bimob.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.editor.NativeEditorViewport
import java.io.File
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 private val editor by viewModels<NativeEditorViewModel>()
 private val prefs by lazy{getSharedPreferences("bimob_native_projects",MODE_PRIVATE)}
 private val dir by lazy{File(filesDir,"native_projects").apply{mkdirs()}}
 private var pendingJson=""
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{
  val state by editor.state.collectAsState()
  var showNew by remember{mutableStateOf(false)}
  var projectName by remember{mutableStateOf("Мой проект")}
  val opener=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let(::openJson)}
  val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u->u?.let{writeJson(it,pendingJson)}}
  if(state.projectId.isBlank()){
   Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(24.dp).verticalScroll(rememberScrollState())){
    Text("BIMOB",fontSize=32.sp);Text("Нативная модель • единый источник истины",color=Color.Gray)
    Spacer(Modifier.height(16.dp));Button(onClick={showNew=true},modifier=Modifier.fillMaxWidth()){Text("Создать проект")}
    OutlinedButton(onClick={opener.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()){Text("Открыть JSON проекта")}
    Text("Проекты",style=MaterialTheme.typography.titleMedium)
    prefs.all.keys.sorted().forEach{n->OutlinedButton(onClick={loadSaved(n)},modifier=Modifier.fillMaxWidth()){Text(n)}}
   }
  }else{
   Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(8.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
     Column(Modifier.weight(1f)){Text(prefs.getString("active_name","Проект")?:"Проект",style=MaterialTheme.typography.titleMedium);Text("NativeSession • ревизия ${state.geometry?.revision?:0}",fontSize=12.sp,color=Color.Gray)}
     TextButton(onClick={pendingJson=editor.exportProjectSnapshot();exporter.launch("bimob-project.json")}){Text("Экспорт")}
     TextButton(onClick={saveCurrent()}){Text("Сохранить")}
     TextButton(onClick=editor::closeAndReset){Text("Проекты")}
    }
    NativeEditorViewport(editor,Modifier.weight(1f).fillMaxWidth())
   }
  }
  if(showNew)AlertDialog(onDismissRequest={showNew=false},title={Text("Новый проект")},text={OutlinedTextField(projectName,{projectName=it},label={Text("Название")})},
   confirmButton={TextButton(onClick={showNew=false;editor.resetProject("project-"+System.currentTimeMillis(),projectName);prefs.edit().putString("active_name",projectName).apply()}){Text("Создать")}},
   dismissButton={TextButton(onClick={showNew=false}){Text("Отмена")}})
 }}}
 private fun fileFor(name:String)=File(dir,"${name.hashCode().toUInt().toString(16)}.json")
 private fun loadSaved(name:String){try{if(editor.openProjectSnapshot(fileFor(name).readText()))prefs.edit().putString("active_name",name).apply()}catch(e:Exception){toast(e.message?:"Не удалось открыть")}}
 private fun saveCurrent(){try{val n=prefs.getString("active_name","Мой проект")?:"Мой проект";val target=fileFor(n);val temp=File(dir,target.name+".tmp");temp.writeText(editor.exportProjectSnapshot());if(!temp.renameTo(target))error("Atomic save failed");prefs.edit().putString(n,"saved").apply();toast("Сохранено в NativeSession")}catch(e:Exception){toast(e.message?:"Ошибка сохранения")}}
 private fun openJson(uri:Uri){try{val json=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Пустой файл");if(editor.openProjectSnapshot(json)){val n=runCatching{JSONObject(json).optString("name").takeIf{it.isNotBlank()}}.getOrNull()?:"Импортированный проект";prefs.edit().putString("active_name",n).apply()}else toast("Проект не прошёл проверку")}catch(e:Exception){toast(e.message?:"Ошибка открытия")}}
 private fun writeJson(uri:Uri,json:String){try{contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(json)}?:error("Не удалось записать файл");toast("Экспорт завершён")}catch(e:Exception){toast(e.message?:"Ошибка экспорта")}}
 private fun toast(s:String)=android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show()
}