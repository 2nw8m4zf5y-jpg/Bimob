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
                    Text(snapshot.projectName, style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot.hasUnsavedChanges) "● Не сохранено" else "✓ Сохранено", color = if (snapshot.hasUnsavedChanges) Color(0xFFB26A00) else Color(0xFF287A45), fontSize = 12.sp)
                }
                TextButton(onClick = onSave) { Text("Сохранить") }
                TextButton(onClick = onHome) { Text("Проекты") }
            }
            var tool by remember { mutableStateOf(CanvasTool.WALL) }
            var selectedWallId by remember { mutableStateOf<String?>(null) }
            var thicknessText by remember(selectedWallId) {
                mutableStateOf(snapshot.walls.firstOrNull { it.id == selectedWallId }?.thicknessMeters?.toString() ?: "0.2")
            }
            Row {
                FilterChip(selected = tool == CanvasTool.WALL, onClick = { tool = CanvasTool.WALL }, label = { Text("Стена") })
                Spacer(Modifier.width(4.dp))
                FilterChip(selected = tool == CanvasTool.SELECT, onClick = { tool = CanvasTool.SELECT }, label = { Text("Выбор") })
                Spacer(Modifier.width(4.dp))
                Button(onClick = { session.undo() }, enabled = snapshot.canUndo) { Text("↶") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { session.redo() }, enabled = snapshot.canRedo) { Text("↷") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { selectedWallId?.let { session.deleteWall(it); selectedWallId = null } }, enabled = selectedWallId != null) { Text("Удалить") }
            }
            PlanCanvas(session, snapshot, tool, selectedWallId, { selectedWallId = it }, Modifier.weight(1f).fillMaxWidth())
            if (selectedWallId != null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(thicknessText, { thicknessText = it }, label = { Text("Толщина, м") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = {
                        val id = selectedWallId
                        val thickness = thicknessText.toDoubleOrNull()
                        if (id != null && thickness != null && thickness > 0.0) session.setWallThickness(id, thickness)
                    }) { Text("Применить") }
                }
            }
            Text("Узлы: метр • шаг привязки: 0,1 м • Стен: ${snapshot.walls.size}", fontSize = 12.sp, color = Color.Gray)
            snapshot.diagnostic?.let { error ->
                Text("${error.code}: ${error.message}", color = Color(0xFFB3261E), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
            }
            if (snapshot.gestureActive) Text("Редактирование жеста…", fontSize = 12.sp, color = Color(0xFF1565C0))
            Button(onClick = { exportLauncher.launch("${snapshot.projectName}.bimob") }, modifier = Modifier.fillMaxWidth()) { Text("Экспорт BIMOB через SAF") }
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
private fun PlanCanvas(session: ModelSession, snapshot: SessionSnapshot, tool: CanvasTool, selectedWallId: String?, onSelectWall: (String?) -> Unit, modifier: Modifier = Modifier) {
    val scale = 80f
    Canvas(modifier = modifier.background(Color.White)
        .pointerInput(session, tool, snapshot.revisionNumber) {
            detectTapGestures { point -> onSelectWall(nearestWall(snapshot.walls, snapWorld(point, size, scale), 0.25)) }
        }
        .pointerInput(session, tool) {
            var startWorld: CanvasPoint? = null
            var previewId: String? = null
            var dragWallId: String? = null
            var lastWorld: CanvasPoint? = null
            detectDragGestures(
                onDragStart = { screen ->
                    val world = snapWorld(screen, size, scale); startWorld = world; lastWorld = world
                    if (tool == CanvasTool.WALL) {
                        previewId = java.util.UUID.randomUUID().toString(); session.beginGesture("Draw wall")
                    } else {
                        dragWallId = nearestWall(session.snapshot().walls, world, 0.25)
                        onSelectWall(dragWallId)
                        if (dragWallId != null) session.beginGesture("Move wall")
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val first = startWorld ?: return@detectDragGestures
                    val current = snapWorld(change.position, size, scale); lastWorld = current
                    if (tool == CanvasTool.WALL) {
                        val id = previewId ?: return@detectDragGestures
                        session.previewDrawWall(id, first.x, first.y, current.x, current.y)
                    } else {
                        val id = dragWallId ?: return@detectDragGestures
                        session.previewMoveWall(id, current.x - first.x, current.y - first.y)
                    }
                },
                onDragEnd = {
                    val a = startWorld; val b = lastWorld
                    val valid = a != null && b != null && (a.x != b.x || a.y != b.y)
                    if (session.snapshot().gestureActive && valid &&
                        ((tool == CanvasTool.WALL && previewId != null) || (tool == CanvasTool.SELECT && dragWallId != null))) session.endGesture()
                    else session.cancelGesture()
                    startWorld = null; previewId = null; dragWallId = null; lastWorld = null
                },
                onDragCancel = {
                    session.cancelGesture()
                    startWorld = null; previewId = null; dragWallId = null; lastWorld = null
                }
            )
        }
    ) {
        val origin = Offset(size.width / 2, size.height / 2); val step = scale * .5f
        var x = origin.x % step
        while (x < size.width) { drawLine(Color(0xFFE5E9EE), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = origin.y % step
        while (y < size.height) { drawLine(Color(0xFFE5E9EE), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawLine(Color(0xFFCCD4DD), Offset(origin.x, 0f), Offset(origin.x, size.height), 2f)
        drawLine(Color(0xFFCCD4DD), Offset(0f, origin.y), Offset(size.width, origin.y), 2f)
        snapshot.walls.forEach { wall ->
            val start = worldScreen(CanvasPoint(wall.startX, wall.startY), size, scale)
            val end = worldScreen(CanvasPoint(wall.endX, wall.endY), size, scale)
            drawLine(if (wall.id == selectedWallId) Color(0xFF1976D2) else Color(0xFF26394E), start, end, 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color(0xFF26394E), 3f, start); drawCircle(Color(0xFF26394E), 3f, end)
        }
    }
}
private data class CanvasPoint(val x: Double, val y: Double)
private fun snapWorld(screen: Offset, size: androidx.compose.ui.unit.IntSize, scale: Float): CanvasPoint {
    val x = (screen.x - size.width.toFloat() / 2f) / scale
    val y = (size.height.toFloat() / 2f - screen.y) / scale
    return CanvasPoint((round(x * 10f) / 10f).toDouble(), (round(y * 10f) / 10f).toDouble())
}
private fun worldScreen(point: CanvasPoint, size: androidx.compose.ui.geometry.Size, scale: Float) =
    Offset((size.width / 2f + point.x * scale).toFloat(), (size.height / 2f - point.y * scale).toFloat())
private fun nearestWall(walls: List<WallCanvasSnapshot>, point: CanvasPoint, tolerance: Double): String? =
    walls.minByOrNull { wall ->
        val dx = wall.endX - wall.startX; val dy = wall.endY - wall.startY; val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((point.x - wall.startX) * dx + (point.y - wall.startY) * dy) / len2
        val u = t.coerceIn(0.0, 1.0); val px = wall.startX + u * dx; val py = wall.startY + u * dy
        sqrt((point.x - px) * (point.x - px) + (point.y - py) * (point.y - py))
    }?.takeIf { wall ->
        val dx = wall.endX - wall.startX; val dy = wall.endY - wall.startY; val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((point.x - wall.startX) * dx + (point.y - wall.startY) * dy) / len2
        val u = t.coerceIn(0.0, 1.0); val px = wall.startX + u * dx; val py = wall.startY + u * dy
        sqrt((point.x - px) * (point.x - px) + (point.y - py) * (point.y - py)) <= tolerance
    }?.id
