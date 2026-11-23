package com.bimob.app

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.bimob.app.core.model.*
import com.bimob.app.core.persistence.BimobArchiveReader
import com.bimob.app.core.persistence.BimobArchiveWriter
import com.bimob.app.core.persistence.LegacySketchMigrator
import com.bimob.app.core.persistence.ProjectRepository
import com.bimob.app.persistence.PrivateFileAtomicStorage
import com.bimob.app.persistence.SafDocumentAdapter
import java.io.File
import kotlin.math.round
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
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
                    projectNames = prefs.all.keys.sorted(),
                    onNewProject = { name ->
                        activeSession = ModelSession(Project(name = name.ifBlank { "Новый проект" }))
                    },
                    onOpenProject = ::openProject,
                    onSave = ::saveCurrent,
                    onHome = { activeSession = null },
                    onImport = { uri ->
                        try {
                            val project = BimobArchiveReader().read(saf.read(uri))
                            activeSession = ModelSession(project, initiallySaved = true)
                            privateRepository.save(revisionPath(project), activeSession!!.snapshot().revision)
                            prefs.edit().putString(project.name, "revision:${safeId(project.id.value)}").apply()
                            ToastMessage("Файл импортирован")
                        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка импорта") }
                    },
                    onExport = { uri ->
                        try {
                            activeSession?.snapshot()?.revision?.let { revision ->
                                val bytes = BimobArchiveWriter().write(revision.project)
                                val digest = saf.writeNewDocument(uri, bytes)
                                ToastMessage("Экспорт проверен • SHA-256 ${digest.take(12)}")
                            } ?: ToastMessage("Нет активного проекта")
                        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка экспорта") }
                    }
                )
            }
        }
    }

    private fun revisionPath(project: Project) = "projects/${safeId(project.id.value)}.revision"
    private fun safeId(raw: String): String {
        val safe = raw.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return safe.takeIf { it.isNotBlank() } ?: raw.hashCode().toString()
    }

    private fun openProject(name: String) {
        try {
            val stored = prefs.getString(name, null) ?: return
            val session = if (stored.startsWith("revision:")) {
                val id = stored.removePrefix("revision:")
                ModelSession(privateRepository.loadRevision("projects/$id.revision"), initiallySaved = true)
            } else {
                val migrated = LegacySketchMigrator.migrateJson(stored)
                val path = revisionPath(migrated)
                ModelSession(migrated, initiallySaved = true).also { first ->
                    privateRepository.save(path, first.snapshot().revision)
                    first.markSaved(first.snapshot().revision)
                    prefs.edit().putString(name, "revision:${safeId(migrated.id.value)}").apply()
                }
            }
            activeSession = session
        } catch (e: Exception) { ToastMessage(e.message ?: "Не удалось открыть проект") }
    }

    private fun saveCurrent() {
        val session = activeSession ?: return
        try {
            val snapshot = session.snapshot()
            privateRepository.save(revisionPath(snapshot.revision.project), snapshot.revision)
            session.markSaved(snapshot.revision)
            val name = snapshot.revision.project.name
            prefs.edit().putString(name, "revision:${safeId(snapshot.revision.project.id.value)}").apply()
            ToastMessage("Сохранено в приватное хранилище")
        } catch (e: Exception) { ToastMessage(e.message ?: "Ошибка сохранения") }
    }

    private fun ToastMessage(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}

/** Thin Compose shell. Model mutations remain owned exclusively by ModelSession. */
@Composable
private fun BimobRoot(
    session: ModelSession?,
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SafDocumentAdapter.BIMOB_MIME)) { uri -> uri?.let(onExport) }

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
        Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(snapshot.revision.project.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot.hasUnsavedChanges) "● Не сохранено" else "✓ Сохранено", color = if (snapshot.hasUnsavedChanges) Color(0xFFB26A00) else Color(0xFF287A45), fontSize = 12.sp)
                }
                TextButton(onClick = onSave) { Text("Сохранить") }
                TextButton(onClick = onHome) { Text("Проекты") }
            }
            var tool by remember { mutableStateOf(CanvasTool.WALL) }
            Row {
                FilterChip(selected = tool == CanvasTool.WALL, onClick = { tool = CanvasTool.WALL }, label = { Text("Стена") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = tool == CanvasTool.SELECT, onClick = { tool = CanvasTool.SELECT }, label = { Text("Выбрать") })
                Spacer(Modifier.width(4.dp))
                Button(onClick = { if (snapshot.canUndo) session.undo() }, enabled = snapshot.canUndo) { Text("↶") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { if (snapshot.canRedo) session.redo() }, enabled = snapshot.canRedo) { Text("↷") }
            }
            PlanCanvas(session, snapshot, tool, Modifier.weight(1f).fillMaxWidth())
            Text("Узлы: метр • шаг привязки: 0,1 м • Стен: ${snapshot.displayedProject.elements.count { it.category == ElementCategory.WALL }}", fontSize = 12.sp, color = Color.Gray)
            snapshot.diagnostic?.let { error ->
                Text("${error.code}: ${error.message}", color = Color(0xFFB3261E), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
            }
            if (snapshot.gestureActive) Text("Редактирование жеста…", fontSize = 12.sp, color = Color(0xFF1565C0))
            Button(onClick = { exportLauncher.launch("${snapshot.revision.project.name}.bimob") }, modifier = Modifier.fillMaxWidth()) { Text("Экспорт BIMOB через SAF") }
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

private enum class CanvasTool { WALL, SELECT }

@Composable
private fun PlanCanvas(session: ModelSession, snapshot: SessionSnapshot, tool: CanvasTool, modifier: Modifier = Modifier) {
    val scale = 80f
    Canvas(
        modifier = modifier.background(Color.White).pointerInput(session, tool) {
            var base: Project? = null
            var startWorld: Point2? = null
            var previewId: ModelId? = null
            var selectedWall: ModelId? = null
            var lastWorld: Point2? = null
            detectDragGestures(
                onDragStart = { screen ->
                    val world = snapWorld(screen, size, scale)
                    base = session.snapshot().revision.project
                    startWorld = world
                    lastWorld = world
                    if (tool == CanvasTool.WALL) {
                        previewId = ModelId.newId()
                        session.beginGesture("Draw wall")
                    } else {
                        selectedWall = nearestWall(base!!, world, 0.25)
                        if (selectedWall != null) session.beginGesture("Move wall")
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val original = base ?: return@detectDragGestures
                    val first = startWorld ?: return@detectDragGestures
                    val current = snapWorld(change.position, size, scale)
                    lastWorld = current
                    if (tool == CanvasTool.WALL) {
                        val id = previewId ?: return@detectDragGestures
                        val newWall = wallFrom(id, first, current)
                        session.updateGesture { project -> project.copy(elements = project.elements.filterNot { it.id == id } + newWall) }
                    } else {
                        val id = selectedWall ?: return@detectDragGestures
                        val dx = current.xMeters - first.xMeters
                        val dy = current.yMeters - first.yMeters
                        session.updateGesture { project ->
                            project.copy(elements = original.elements.map { e ->
                                if (e.id == id && e is WallElement) e.copy(axis = WallAxis(Point2(e.axis.start.xMeters+dx,e.axis.start.yMeters+dy),Point2(e.axis.end.xMeters+dx,e.axis.end.yMeters+dy))) else e
                            })
                        }
                    }
                },
                onDragEnd = {
                    val a = startWorld; val b = lastWorld
                    val valid = a != null && b != null && (a.xMeters != b.xMeters || a.yMeters != b.yMeters)
                    if (session.snapshot().gestureActive && valid) {
                        val id = previewId
                        val wallId = dragWallId
                        if (tool == CanvasTool.WALL && id != null && a != null && b != null) session.endGesture(AddWallCommand(wallFrom(id, a, b)))
                        else if (tool == CanvasTool.SELECT && wallId != null && a != null && b != null) session.endGesture(MoveWallCommand(wallId, b.xMeters-a.xMeters, b.yMeters-a.yMeters))
                        else session.cancelGesture()
                    } else session.cancelGesture()
                    base = null; startWorld = null; previewId = null; dragWallId = null; lastWorld = null
                },
                onDragCancel = {
                    session.cancelGesture()
                    base = null; startWorld = null; previewId = null; selectedWall = null; lastWorld = null
                }
            )
        }
    ) {
        val origin = Offset(size.width/2,size.height/2)
        val step = scale * .5f
        var x = origin.x % step
        while (x < size.width) { drawLine(Color(0xFFE5E9EE),Offset(x,0f),Offset(x,size.height),1f); x += step }
        var y = origin.y % step
        while (y < size.height) { drawLine(Color(0xFFE5E9EE),Offset(0f,y),Offset(size.width,y),1f); y += step }
        drawLine(Color(0xFFCCD4DD),Offset(origin.x,0f),Offset(origin.x,size.height),2f)
        drawLine(Color(0xFFCCD4DD),Offset(0f,origin.y),Offset(size.width,origin.y),2f)
        snapshot.displayedProject.elements.filterIsInstance<WallElement>().forEach { wall ->
            val start = worldScreen(wall.axis.start, size, scale)
            val end = worldScreen(wall.axis.end, size, scale)
            drawLine(Color(0xFF26394E),start,end,5f,cap=androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color(0xFF26394E),3f,start); drawCircle(Color(0xFF26394E),3f,end)
        }
    }
}

private fun snapWorld(screen: Offset, size: androidx.compose.ui.unit.IntSize, scale: Float): Point2 {
    val x = (screen.x - size.width.toFloat()/2f)/scale
    val y = (size.height.toFloat()/2f - screen.y)/scale
    return Point2((round(x*10f)/10f).toDouble(), (round(y*10f)/10f).toDouble())
}
private fun worldScreen(point: Point2, size: androidx.compose.ui.geometry.Size, scale: Float) =
    Offset((size.width/2f + point.xMeters*scale).toFloat(),(size.height/2f-point.yMeters*scale).toFloat())
private fun wallFrom(id: ModelId, a: Point2, b: Point2) = WallElement(id=id,axis=WallAxis(a,b),heightMeters=2.7,thicknessMeters=.2)
private fun nearestWall(project: Project, point: Point2, tolerance: Double): ModelId? {
    return project.elements.filterIsInstance<WallElement>().minByOrNull { w ->
        val ax=w.axis.start.xMeters;val ay=w.axis.start.yMeters;val bx=w.axis.end.xMeters;val by=w.axis.end.yMeters
        val dx=bx-ax;val dy=by-ay;val len2=dx*dx+dy*dy
        val t=if(len2==0.0)0.0 else ((point.xMeters-ax)*dx+(point.yMeters-ay)*dy)/len2
        val u=t.coerceIn(0.0,1.0);sqrt((point.xMeters-(ax+u*dx)).let{it*it}+(point.yMeters-(ay+u*dy)).let{it*it})
    }?.takeIf { w ->
        val ax=w.axis.start.xMeters;val ay=w.axis.start.yMeters;val bx=w.axis.end.xMeters;val by=w.axis.end.yMeters
        val dx=bx-ax;val dy=by-ay;val len2=dx*dx+dy*dy
        val t=if(len2==0.0)0.0 else ((point.xMeters-ax)*dx+(point.yMeters-ay)*dy)/len2
        val u=t.coerceIn(0.0,1.0);sqrt((point.xMeters-(ax+u*dx)).let{it*it}+(point.yMeters-(ay+u*dy)).let{it*it}) <= tolerance
    }?.id
}
