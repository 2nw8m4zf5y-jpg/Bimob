package com.bimob.app

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.editor.NativeEditorViewport
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity(){
 private val editor by viewModels<NativeEditorViewModel>()
 private val prefs by lazy{getSharedPreferences("bimob_native_projects",MODE_PRIVATE)}
 private val dir by lazy{File(filesDir,"native_projects").apply{mkdirs()}}
 private var pendingJson=""
 override fun onCreate(savedInstanceState:Bundle?){
  CrashReporter.install(applicationContext)
  super.onCreate(savedInstanceState)
  setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF1E90FF),secondary=Color(0xFF00B4D8),tertiary=Color(0xFFFFB300),background=Color(0xFF12151A),surface=Color(0xFF191E24),surfaceVariant=Color(0xFF191E24),outline=Color(0xFF303947),error=Color(0xFFE53935),onPrimary=Color.White,onSecondary=Color.White,onBackground=Color(0xFFE6EDF5),onSurface=Color(0xFFE6EDF5)),shapes=Shapes(small=RoundedCornerShape(0.dp),medium=RoundedCornerShape(0.dp),large=RoundedCornerShape(0.dp)){
   val state by editor.state.collectAsState()
   var crashLog by remember { mutableStateOf(CrashReporter.readLastCrash(this@MainActivity)) }
   var showNew by remember{mutableStateOf(false)}
   var projectName by remember{mutableStateOf("Мой проект")}
   val opener=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let(::openJson)}
   val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u->u?.let{writeJson(it,pendingJson)}}
   Column(Modifier.fillMaxSize()) {
    if(crashLog != null) {
     Card(
      modifier=Modifier.fillMaxWidth().padding(8.dp),
      colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)
     ) {
      Column(Modifier.padding(12.dp)) {
       Text("Обнаружен предыдущий сбой приложения", style=MaterialTheme.typography.titleSmall)
       Text("Лог сохранён во внутренней памяти устройства.", style=MaterialTheme.typography.bodySmall)
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={
         val clipboard=getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
         clipboard.setPrimaryClip(ClipData.newPlainText("NAGAPLAS last_crash.txt",crashLog))
         toast("Лог краша скопирован")
        }) { Text("Скопировать лог краша") }
        TextButton(onClick={CrashReporter.clear(this@MainActivity);crashLog=null}) { Text("Закрыть") }
       }
      }
     }
    }
    if(state.projectId.isBlank()){
     Column(Modifier.fillMaxSize().background(Color(0xFF12151A)).padding(24.dp).verticalScroll(rememberScrollState())){
      Text("NAGAPLAS",fontSize=32.sp);Text("Нативная модель • единый источник истины",color=Color(0xFF9AA7B5))
      Spacer(Modifier.height(16.dp));Button(onClick={showNew=true},modifier=Modifier.fillMaxWidth()){Text("Создать проект")}
      OutlinedButton(onClick={opener.launch(arrayOf("application/json","text/plain"))},enabled=!state.isImporting,modifier=Modifier.fillMaxWidth()){Text(if(state.isImporting)"Импорт…" else "Открыть JSON проекта")}
      Text("Проекты",style=MaterialTheme.typography.titleMedium)
      prefs.all.keys.sorted().forEach{n->OutlinedButton(onClick={loadSaved(n)},modifier=Modifier.fillMaxWidth()){Text(n)}}
     }
    }else{
     Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))){
      NativeEditorViewport(
       editor,
       Modifier.weight(1f).fillMaxWidth(),
       projectTitle=prefs.getString("active_name","Проект")?:"Проект",
       onProjects=editor::closeAndReset,
       onSave=::saveCurrent,
       onExport={editor.exportProjectSnapshotAsync{result->result.onSuccess{pendingJson=it;exporter.launch("nagaplas-project.nagaplas")}.onFailure{toast(it.message?:"Ошибка экспорта")}}},
       onImport={opener.launch(arrayOf("application/json","text/plain"))}
      )
     }
    }
   }
   if(showNew)AlertDialog(onDismissRequest={showNew=false},title={Text("Новый проект")},text={OutlinedTextField(projectName,{projectName=it},label={Text("Название")})},
    confirmButton={TextButton(onClick={showNew=false;editor.resetProject("project-"+System.currentTimeMillis(),projectName);prefs.edit().putString("active_name",projectName).apply()}){Text("Создать")}},
    dismissButton={TextButton(onClick={showNew=false}){Text("Отмена")}})
  }}
 }
 private fun fileFor(name:String)=File(dir,"${name.hashCode().toUInt().toString(16)}.nagaplas")
 private fun legacyFileFor(name:String)=File(dir,"${name.hashCode().toUInt().toString(16)}.json")
 private fun loadSaved(name:String){lifecycleScope.launch{val json=runCatching{withContext(Dispatchers.IO){(if(fileFor(name).exists())fileFor(name)else legacyFileFor(name)).readText()}};json.onSuccess{editor.openProjectSnapshotAsync(it){ok->if(ok)prefs.edit().putString("active_name",name).apply()else toast("Не удалось открыть проект")}}.onFailure{toast(it.message?:"Не удалось открыть")}}}
 private fun saveCurrent(){val n=prefs.getString("active_name","Мой проект")?:"Мой проект";editor.exportProjectSnapshotAsync{result->result.onSuccess{json->lifecycleScope.launch{val saved=runCatching{withContext(Dispatchers.IO){val target=fileFor(n);val temp=File(dir,target.name+".tmp");temp.writeText(json);if(!temp.renameTo(target))error("Atomic save failed")}};saved.onSuccess{prefs.edit().putString(n,"saved").apply();toast("Сохранено")}.onFailure{toast(it.message?:"Ошибка сохранения")}}}.onFailure{toast(it.message?:"Ошибка сохранения")}}}
 private fun openJson(uri:Uri){lifecycleScope.launch{val read=runCatching{withContext(Dispatchers.IO){contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Пустой файл")}};read.onSuccess{json->editor.openProjectSnapshotAsync(json){ok->if(ok){val n=runCatching{JSONObject(json).optString("name").takeIf{it.isNotBlank()}}.getOrNull()?:"Импортированный проект";prefs.edit().putString("active_name",n).apply()}else toast("Проект не прошёл проверку")}}.onFailure{toast(it.message?:"Ошибка открытия")}}}
 private fun writeJson(uri:Uri,json:String){try{contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(json)}?:error("Не удалось записать файл");toast("Экспорт завершён")}catch(e:Exception){toast(e.message?:"Ошибка экспорта")}}
 private fun toast(s:String)=android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show()
}
