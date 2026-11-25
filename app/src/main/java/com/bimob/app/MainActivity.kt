package com.bimob.app

import android.net.Uri
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.editor.NativeEditorViewport
import com.bimob.app.ui.launcher.ProgramLauncherScreen
import com.bimob.app.ui.facade.ProjectFacadeScreen
import com.bimob.app.ui.facade.FacadeProject
import com.bimob.app.ui.blueprint.BlueprintTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 private val prefs by lazy{getSharedPreferences("bimob_native_projects",MODE_PRIVATE)}
 private val dir by lazy{File(filesDir,"native_projects").apply{mkdirs()}}
 private var pendingJson=""
 private var pendingName="Импортированный проект"
 private var routeState="launcher"
 private var selectedProject=""
 private var showNewDialog=false
 private var newProjectName="Мой проект"
 override fun onCreate(savedInstanceState:Bundle?){
  CrashReporter.install(applicationContext)
  super.onCreate(savedInstanceState)
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  applyImmersiveFullscreen()
  window.decorView.postDelayed({applyImmersiveFullscreen()},120L)
  setContent{
   var route by remember{mutableStateOf(routeState)}
   var projectId by remember{mutableStateOf(selectedProject)}
   var dialog by remember{mutableStateOf(false)}
   var projectName by remember{mutableStateOf(newProjectName)}
   var importJson by remember{mutableStateOf<String?>(null)}
   val opener=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{readImport(it){json,name->importJson=json;projectName=name;projectId="import-"+System.currentTimeMillis();route="workspace"}}}
   val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{writeJson(it,pendingJson)}}
   BackHandler(route!="launcher"){
    when(route){"workspace"->route="facade";"facade"->route="launcher"}
   }
   MaterialTheme(colorScheme=darkColorScheme(primary=BlueprintTheme.AccentCyan,secondary=BlueprintTheme.LinePrimary,tertiary=BlueprintTheme.AccentAmber,background=BlueprintTheme.BgPrimary,surface=BlueprintTheme.BgSurface,onSurface=BlueprintTheme.TextPrimary),shapes=Shapes(small=RectangleShape,medium=RectangleShape,large=RectangleShape)){
    when(route){
     "launcher"->ProgramLauncherScreen(onCad={route="facade"})
     "facade"->{
      val recent=prefs.all.keys.filter{prefs.getString(it,null)=="saved"}.sorted().take(3).map{FacadeProject(it,it,"ЛОКАЛЬНЫЙ ПРОЕКТ",0)}
      ProjectFacadeScreen(recent,onBack={route="launcher"},onNew={dialog=true},onOpen={name->loadSavedProject(name){json->importJson=json;projectId=name;projectName=name;route="workspace"}},onImport={opener.launch(arrayOf("application/json","text/plain"))},onTool={tool->android.widget.Toast.makeText(this@MainActivity,"$tool · модуль в разработке",android.widget.Toast.LENGTH_SHORT).show()},onOpenLast={val last=prefs.getString("active_name","");if(last.isNotBlank()&&prefs.getString(last,null)=="saved"){loadSavedProject(last){json->importJson=json;projectId=last;projectName=last;route="workspace"}}else dialog=true})
     }
     "workspace"->WorkspaceRoute(projectId,projectName,importJson,onBack={route="facade"},onSaved={name,json->saveSnapshot(name,json)},onExport={json->pendingJson=json;exporter.launch("nagaplas-project.json")},onImport={opener.launch(arrayOf("application/json","text/plain"))})
    }
    if(dialog)AlertDialog(onDismissRequest={dialog=false},title={Text("Новый проект")},text={OutlinedTextField(projectName,{projectName=it},label={Text("Название проекта")})},confirmButton={TextButton(onClick={dialog=false;projectId="project-"+System.currentTimeMillis();route="workspace"}){Text("Создать")}},dismissButton={TextButton(onClick={dialog=false}){Text("Отмена")}})
   }
  }
 }
 private fun saveSnapshot(name:String,json:String){lifecycleScope.launch{val file=File(dir,"${name.hashCode().toUInt().toString(16)}.nagaplas");val tmp=File(dir,file.name+".tmp");val saved=runCatching{withContext(Dispatchers.IO){tmp.writeText(json);if(!tmp.renameTo(file))error("Atomic save failed")}};saved.onSuccess{prefs.edit().putString(name,"saved").putString("active_name",name).apply();toast("Сохранено")}.onFailure{toast(it.message?: "Ошибка сохранения")}}}
 private fun loadSavedProject(name:String,done:(String)->Unit){lifecycleScope.launch{val file=File(dir,"${name.hashCode().toUInt().toString(16)}.nagaplas");val legacy=File(dir,"${name.hashCode().toUInt().toString(16)}.json");val result=runCatching{withContext(Dispatchers.IO){(if(file.exists())file else legacy).readText()}};result.onSuccess(done).onFailure{toast(it.message?: "Не удалось открыть проект")}}}
 private fun readImport(uri:Uri,done:(String,String)->Unit){lifecycleScope.launch{val result=runCatching{withContext(Dispatchers.IO){contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Файл пуст")}};result.onSuccess{json->val name=runCatching{JSONObject(json).optString("name").takeIf{it.isNotBlank()}}.getOrNull()?: "Импортированный проект";done(json,name)}.onFailure{toast(it.message?: "Не удалось прочитать файл")}}}
 private fun writeJson(uri:Uri,json:String){runCatching{contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(json)}?:error("Не удалось записать файл")}.onSuccess{toast("Экспорт готов")}.onFailure{toast(it.message?: "Ошибка экспорта")}}
 private fun toast(s:String)=android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show()
 private fun applyImmersiveFullscreen(){WindowCompat.setDecorFitsSystemWindows(window,false);if(android.os.Build.VERSION.SDK_INT>=28)window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES};hideSystemBars()}
 private fun hideSystemBars(){WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
 override fun onResume(){super.onResume();applyImmersiveFullscreen();window.decorView.postDelayed({applyImmersiveFullscreen()},120L)}
 override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus){applyImmersiveFullscreen();window.decorView.postDelayed({applyImmersiveFullscreen()},120L)}}
}

@Composable
private fun WorkspaceRoute(projectId:String,projectName:String,importJson:String?,onBack:()->Unit,onSaved:(String,String)->Unit,onExport:(String)->Unit,onImport:()->Unit){
 // Native ViewModel is deliberately first obtained here, not in Activity or either blueprint screen.
 val editor:NativeEditorViewModel=viewModel()
 val state by editor.state.collectAsState()
 val context=LocalContext.current
 var importHandled by remember{mutableStateOf(false)}
 LaunchedEffect(projectId,importJson){
  if(importJson!=null&&!importHandled){importHandled=true;editor.openProjectSnapshotAsync(importJson){ok->if(!ok)android.widget.Toast.makeText(context,"Импорт не прошёл проверку",android.widget.Toast.LENGTH_SHORT).show()}}
  else if(projectId.isNotBlank()&&state.projectId.isBlank())editor.resetProject(projectId,projectName)
 }
 Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))){
  NativeEditorViewport(editor,Modifier.weight(1f).fillMaxWidth(),projectTitle=projectName,onProjects={editor.closeAndReset();onBack()},onSave={editor.exportProjectSnapshotAsync{r->r.onSuccess{json->onSaved(projectName,json)}.onFailure{android.widget.Toast.makeText(context,it.message?: "Ошибка сохранения",android.widget.Toast.LENGTH_SHORT).show()}}},onExport={editor.exportProjectSnapshotAsync{r->r.onSuccess(onExport).onFailure{}}},onImport=onImport)
 }
}
