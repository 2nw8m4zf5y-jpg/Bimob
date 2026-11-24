package com.bimob.app

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimob.app.core.model.ModelSession
import com.bimob.app.core.model.SessionSnapshot
import com.bimob.app.core.model.WallCanvasSnapshot
import com.bimob.app.core.persistence.BimobArchiveReader
import com.bimob.app.core.persistence.LegacySketchMigrator
import com.bimob.app.core.persistence.ProjectRepository
import com.bimob.app.persistence.PrivateFileAtomicStorage
import com.bimob.app.persistence.SafDocumentAdapter
import com.bimob.app.editor.NativeEditorViewModel
import com.bimob.app.editor.NativeEditorViewport
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import kotlin.math.round
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private val nativeEditor by viewModels<NativeEditorViewModel>()
    private val prefs by lazy { getSharedPreferences("bimob_projects", MODE_PRIVATE) }
    private val privateRepository by lazy { ProjectRepository(PrivateFileAtomicStorage(File(filesDir, "model_repository"))) }
    private val saf by lazy { SafDocumentAdapter(contentResolver, cacheDir) }
    private var activeSession by mutableStateOf<ModelSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BimobRoot(
                    session = activeSession,
                    nativeEditor = nativeEditor,
                    projectNames = prefs.all.keys.sorted(),
                    onNewProject = { name ->
                        activeSession = ModelSession.newProject(name)
                    },
                    onOpenProject = ::openProject,
                    onSave = ::saveCurrent,
                    onHome = { activeSession = null },
                    onImport = { uri ->
                        try {
                            val project = BimobArchiveReader().read(saf.read(uri))
                            activeSession = ModelSession.importedProject(project)
                            activeSession!!.save(privateRepository, revisionPath(activeSession!!.snapshot().projectId))
                            prefs.edit().putString(project.name, "revision:${safeId(project.id.value)}").apply()
                            ToastMessage("Файл импортирован")
                        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка импорта") }
                    },
                    onExport = { uri ->
                        try {
                            activeSession?.let { current ->
                                val bytes = current.exportArchive()
                                val digest = saf.writeNewDocument(uri, bytes)
                                ToastMessage("Экспорт проверен • SHA-256 ${digest.take(12)}")
                            } ?: ToastMessage("Нет активного проекта")
                        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка экспорта") }
                    }
                )
            }
        }
    }

    private fun revisionPath(projectId: String) = "projects/${safeId(projectId)}.revision"
    private fun safeId(raw: String): String {
        val safe = raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return safe.takeIf { it.isNotBlank() } ?: raw.hashCode().toString()
    }

    private fun openProject(name: String) {
        try {
            val stored = prefs.getString(name, null) ?: return
            val session = if (stored.startsWith("revision:")) {
                val id = stored.removePrefix("revision:")
                ModelSession.load(privateRepository, "projects/$id.revision")
            } else {
                val migrated = LegacySketchMigrator.migrateJson(stored)
                val path = revisionPath(migrated.id.value)
                ModelSession.migrateLegacyProject(migrated).also { first ->
                    first.save(privateRepository, path)
                    prefs.edit().putString(name, "revision:${safeId(first.snapshot().projectId)}").apply()
                }
            }
            activeSession = session
        } catch (e: Exception) { ToastMessage(e.message ?: "Не удалось открыть проект") }
    }

    private fun saveCurrent() {
        val session = activeSession ?: return
        try {
            val snapshot = session.snapshot()
            session.save(privateRepository, revisionPath(snapshot.projectId))
            prefs.edit().putString(snapshot.projectName, "revision:${safeId(snapshot.projectId)}").apply()
            ToastMessage("Сохранено в приватное хранилище")
        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка сохранения") }
    }

    private fun ToastMessage(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}

/** Thin Compose shell. Model mutations remain owned exclusively by ModelSession. */
@Composable
private fun BimobRoot(
    session: ModelSession?,
    nativeEditor: NativeEditorViewModel,
    projectNames: List<String>,
    onNewProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onSave: () -> Unit,
    onHome: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onExport: (android.net.Uri) -> Unit
) {
    var newProjectDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("Мой проект") }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImport) }

    if (session == null) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("BIMOB", fontSize = 32.sp, color = Color(0xFF18273D))
            Text("Модель здания • единый источник истины", color = Color.Gray)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { newProjectDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Создать проект") }
            Button(onClick = { openLauncher.launch(arrayOf(SafDocumentAdapter.BIMOB_MIME, "application/zip")) }, modifier = Modifier.fillMaxWidth()) { Text("Импорт .bimob через SAF") }
            Spacer(Modifier.height(20.dp))
            Text("Проекты", style = MaterialTheme.typography.titleMedium)
            projectNames.forEach { name ->
                OutlinedButton(onClick = { onOpenProject(name) }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
            Text("Сохранённые revision snapshots хранятся в приватной директории приложения. SAF используется только для обмена файлами.", fontSize = 12.sp, color = Color.Gray)
        }
    } else {
        val snapshot by session.state.collectAsState()
        LaunchedEffect(snapshot.projectId) { nativeEditor.resetProject(snapshot.projectId, snapshot.projectName) }
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(snapshot.projectName, style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot.hasUnsavedChanges) "● Не сохранено" else "✓ Сохранено", color = if (snapshot.hasUnsavedChanges) Color(0xFFB26A00) else Color(0xFF287A45), fontSize = 12.sp)
                }
                TextButton(onClick = onHome) { Text("Проекты") }
            }
            NativeEditorViewport(nativeEditor, Modifier.weight(1f).fillMaxWidth())
            Text("Редактор нативной модели • геометрия и история управляются движком C++", fontSize = 11.sp, color = Color.Gray)
        }
    }
    if (newProjectDialog) {
        AlertDialog(
            onDismissRequest = { newProjectDialog = false },
            title = { Text("Новый проект") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("Название") }) },
            confirmButton = { TextButton(onClick = { newProjectDialog = false; onNewProject(newName) }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { newProjectDialog = false }) { Text("Отмена") } }
        )
    }
}

