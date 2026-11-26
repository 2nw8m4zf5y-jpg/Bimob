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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.ViewModelProvider
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.ui.workspace.MatrixCadWorkspaceScreen
import com.bimob.app.ui.workspace.GridSettingsScreen
import com.bimob.app.ui.launcher.ProgramLauncherScreen
import com.bimob.app.ui.blueprint.BlueprintTheme
import com.bimob.app.ui.blueprint.BlueprintThemeProvider
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
 private var newProjectName="Мой проект"
 override fun onCreate(savedInstanceState:Bundle?){
  CrashReporter.install(applicationContext)
  super.onCreate(savedInstanceState)
  requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  applyImmersiveFullscreen()
  window.decorView.postDelayed({applyImmersiveFullscreen()},120L)
  setContent{
   var route by rememberSaveable{mutableStateOf(routeState)}
   var projectId by rememberSaveable{mutableStateOf(selectedProject)}
   var dialog by remember{mutableStateOf(false)}
   var projectName by rememberSaveable{mutableStateOf(newProjectName)}
   var importJson by remember{mutableStateOf<String?>(null)}
   var pendingVector by remember{mutableStateOf("")}
   val opener=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{readImport(it){json,name->importJson=json;projectName=name;projectId="import-"+System.currentTimeMillis();route="workspace"}}}
   val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{writeJson(it,pendingJson)}}
   val svgExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")){uri->uri?.let{writeJson(it,pendingVector)}}
   val dxfExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/dxf")){uri->uri?.let{writeJson(it,pendingVector)}}

   BlueprintThemeProvider {
    MaterialTheme(colorScheme=if(BlueprintTheme.active.value.id=="paper")lightColorScheme(primary=BlueprintTheme.AccentCyan,secondary=BlueprintTheme.LinePrimary,tertiary=BlueprintTheme.AccentAmber,background=BlueprintTheme.BgPrimary,surface=BlueprintTheme.BgSurface,onSurface=BlueprintTheme.TextPrimary,onSurfaceVariant=BlueprintTheme.TextMuted)else darkColorScheme(primary=BlueprintTheme.AccentCyan,secondary=BlueprintTheme.LinePrimary,tertiary=BlueprintTheme.AccentAmber,background=BlueprintTheme.BgPrimary,surface=BlueprintTheme.BgSurface,onSurface=BlueprintTheme.TextPrimary,onSurfaceVariant=BlueprintTheme.TextMuted),shapes=Shapes(small=RoundedCornerShape(0.dp),medium=RoundedCornerShape(0.dp),large=RoundedCornerShape(0.dp))){
    when(route){
     "launcher"->ProgramLauncherScreen(onCad={dialog=true},onSettings={route="settings"})
     "settings"->GridSettingsScreen(onBack={route="launcher"})
     "workspace"->WorkspaceRoute(projectId,projectName,importJson,onBack={route="launcher"},onSaved={name,json->saveSnapshot(name,json)},onExport={json->pendingJson=json;exporter.launch("nagaplas-project.json")},onImport={opener.launch(arrayOf("application/json","text/plain"))},onExportVector={format,text->pendingVector=text;if(format=="SVG")svgExporter.launch("drawing.svg")else dxfExporter.launch("drawing.dxf")})
    }
     if(dialog){
      val keyboard=LocalSoftwareKeyboardController.current
      AlertDialog(
       onDismissRequest={keyboard?.hide();dialog=false},
       shape=RoundedCornerShape(8.dp),
       containerColor=BlueprintTheme.BgSurface,
       title={Text("НОВЫЙ ПРОЕКТ",fontSize=16.sp,color=BlueprintTheme.TextPrimary)},
       text={
        OutlinedTextField(
         value=projectName,
         onValueChange={projectName=it},
         singleLine=true,
         label={Text("Название проекта")},
         trailingIcon={IconButton(onClick={keyboard?.hide()}){Text("⌄",fontSize=22.sp,color=BlueprintTheme.AccentCyan)}},
         colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=BlueprintTheme.AccentCyan,unfocusedBorderColor=BlueprintTheme.LinePrimary,focusedLabelColor=BlueprintTheme.AccentCyan,cursorColor=BlueprintTheme.AccentCyan)
        )
       },
       confirmButton={TextButton(onClick={keyboard?.hide();dialog=false;importJson=null;projectId="project-"+System.currentTimeMillis();route="workspace"}){Text("СОЗДАТЬ",color=BlueprintTheme.AccentCyan)}},
       dismissButton={TextButton(onClick={keyboard?.hide();dialog=false}){Text("ОТМЕНА",color=BlueprintTheme.TextMuted)}}
      )
     }
   }
  }
 }
 }
 private fun projectFileName(name:String)=java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8)).joinToString(""){ "%02x".format(it) }.take(24)
 private fun legacyHash(name:String)=name.hashCode().toUInt().toString(16)
 private fun saveSnapshot(name:String,json:String){lifecycleScope.launch{val file=File(dir,"${projectFileName(name)}.nagaplas");val tmp=File(dir,file.name+".tmp");val saved=runCatching{withContext(Dispatchers.IO){tmp.writeText(json);if(!tmp.renameTo(file))error("Atomic save failed")}};saved.onSuccess{prefs.edit().putString(name,"saved").putString("active_name",name).apply();toast("Сохранено")}.onFailure{toast(it.message?: "Ошибка сохранения")}}}
 private fun loadSavedProject(name:String,done:(String)->Unit){lifecycleScope.launch{val modern=File(dir,"${projectFileName(name)}.nagaplas");val legacyNagaplas=File(dir,"${legacyHash(name)}.nagaplas");val legacyJson=File(dir,"${legacyHash(name)}.json");val result=runCatching{withContext(Dispatchers.IO){(if(modern.exists())modern else if(legacyNagaplas.exists())legacyNagaplas else legacyJson).readText()}};result.onSuccess(done).onFailure{toast(it.message?: "Не удалось открыть проект")}}}
 private fun readImport(uri:Uri,done:(String,String)->Unit){lifecycleScope.launch{val result=runCatching{withContext(Dispatchers.IO){contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Файл пуст")}};result.onSuccess{json->val name=runCatching{JSONObject(json).optString("name").takeIf{it.isNotBlank()}}.getOrNull()?: "Импортированный проект";done(json,name)}.onFailure{toast(it.message?: "Не удалось прочитать файл")}}}
 private fun writeJson(uri:Uri,json:String){runCatching{contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(json)}?:error("Не удалось записать файл")}.onSuccess{toast("Экспорт готов")}.onFailure{toast(it.message?: "Ошибка экспорта")}}
 private fun toast(s:String)=android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show()
 private fun applyImmersiveFullscreen(){WindowCompat.setDecorFitsSystemWindows(window,false);if(android.os.Build.VERSION.SDK_INT>=28)window.attributes=window.attributes.apply{layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES};hideSystemBars()}
 private fun hideSystemBars(){WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
 override fun onResume(){super.onResume();applyImmersiveFullscreen();window.decorView.postDelayed({applyImmersiveFullscreen()},120L)}
 override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus){applyImmersiveFullscreen();window.decorView.postDelayed({applyImmersiveFullscreen()},120L)}}
}

@Composable
private fun WorkspaceRoute(projectId:String,projectName:String,importJson:String?,onBack:()->Unit,onSaved:(String,String)->Unit,onExport:(String)->Unit,onImport:()->Unit,onExportVector:(String,String)->Unit){
 val owner=LocalContext.current as ComponentActivity
 val editor:NativeEditorViewModel=remember(owner){ViewModelProvider(owner)[NativeEditorViewModel::class.java]}
 BackHandler{editor.closeAndReset();onBack()}
 val context=LocalContext.current
 var importHandled by rememberSaveable(projectId){mutableStateOf(false)}
 var projectInitialized by rememberSaveable(projectId){mutableStateOf(false)}
 LaunchedEffect(projectId,importJson){
  if(importJson!=null&&!importHandled){importHandled=true;projectInitialized=true;editor.openProjectSnapshotAsync(importJson){ok->if(!ok)android.widget.Toast.makeText(context,"Импорт не прошёл проверку",android.widget.Toast.LENGTH_SHORT).show()}}
  else if(importJson==null&&projectId.isNotBlank()&&!projectInitialized){projectInitialized=true;editor.resetProject(projectId,projectName)}
 }
 MatrixCadWorkspaceScreen(projectName=projectName,editor=editor,onBack={editor.closeAndReset();onBack()},
  onSave={editor.exportProjectSnapshotAsync{r->r.onSuccess{json->onSaved(projectName,json)}.onFailure{android.widget.Toast.makeText(context,it.message?: "Ошибка сохранения",android.widget.Toast.LENGTH_SHORT).show()}}},
  onExport={editor.exportProjectSnapshotAsync{r->r.onSuccess(onExport).onFailure{}}},onImport=onImport,onExportVector=onExportVector)
}
