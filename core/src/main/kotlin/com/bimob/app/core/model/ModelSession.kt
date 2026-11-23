package com.bimob.app.core.model

import com.bimob.app.core.persistence.ProjectRepository
import com.bimob.app.core.persistence.BimobArchiveWriter
import com.bimob.app.core.persistence.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable UI-facing value object. No Project, ModelRevision, graph or command internals escape. */
data class WallCanvasSnapshot(val id: String, val startX: Double, val startY: Double, val endX: Double, val endY: Double, val thicknessMeters: Double, val heightMeters: Double)
data class SessionSnapshot(
    val revisionNumber: Long, val projectId: String, val projectName: String,
    val walls: List<WallCanvasSnapshot>, val elementCount: Int,
    val canUndo: Boolean, val canRedo: Boolean, val hasUnsavedChanges: Boolean,
    val diagnostic: SessionDiagnostic? = null, val gestureActive: Boolean = false
)
data class SessionDiagnostic(val code: CommandFailureCode, val message: String)

/**
 * Serialized application façade. Compose consumes only SessionSnapshot and scalar command methods.
 * This contract can be implemented by a JNI-backed façade without exposing Kotlin model types.
 */
class ModelSession internal constructor(
    initialProject: Project,
    private val commandBus: CommandBus = CommandBus(initialProject),
    initiallySaved: Boolean = false
) {
    internal constructor(initialRevision: ModelRevision, initiallySaved: Boolean = true) : this(
        initialRevision.project,
        CommandBus(initialRevision.project, initialRevisionNumber = initialRevision.number,
            initialDependencyGraph = initialRevision.dependencyGraph, initialDerivedValues = initialRevision.derivedValues),
        initiallySaved
    )

    private val lock = Any()
    private var savedRevision: ModelRevision? = if (initiallySaved) commandBus.snapshot() else null
    private var gestureBase: ModelRevision? = null
    private var gesturePreview: Project? = null
    private var gestureLabel = "Interactive edit"
    private var gestureMutation: ModelMutation? = null
    private var diagnostic: SessionDiagnostic? = null
    private val mutableState = MutableStateFlow(buildSnapshot())
    val state: StateFlow<SessionSnapshot> = mutableState.asStateFlow()
    fun snapshot(): SessionSnapshot = synchronized(lock) { mutableState.value }

    internal fun execute(command: ModelMutation): CommandResult = synchronized(lock) {
        if (gestureBase != null) {
            val result = CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, "A gesture transaction is active; finish or cancel it first.")
            diagnostic = SessionDiagnostic(result.code, result.message); publish(); return result
        }
        val result = commandBus.execute(command); acceptResult(result); publish(); result
    }

    fun addWall(id: String, startX: Double, startY: Double, endX: Double, endY: Double, heightMeters: Double = 2.7, thicknessMeters: Double = 0.2): CommandResult =
        execute(AddWallCommand(WallElement(ModelId(id), axis = WallAxis(Point2(startX, startY), Point2(endX, endY)), heightMeters = heightMeters, thicknessMeters = thicknessMeters)))
    fun moveWall(id: String, deltaX: Double, deltaY: Double): CommandResult = execute(MoveWallCommand(ModelId(id), deltaX, deltaY))
    fun deleteWall(id: String): CommandResult = execute(DeleteWallCommand(ModelId(id)))
    fun setWallThickness(id: String, thicknessMeters: Double): CommandResult = execute(SetWallThicknessCommand(ModelId(id), thicknessMeters))

    fun undo(): CommandResult = synchronized(lock) { cancelGestureInternal(); commandBus.undo().also { acceptResult(it); publish() } }
    fun redo(): CommandResult = synchronized(lock) { cancelGestureInternal(); commandBus.redo().also { acceptResult(it); publish() } }

    fun beginGesture(label: String = "Interactive edit"): Boolean = synchronized(lock) {
        if (gestureBase != null) return false
        gestureBase = commandBus.snapshot(); gesturePreview = gestureBase!!.project
        gestureLabel = label; gestureMutation = null; diagnostic = null; publish(); true
    }

    internal fun updateGesture(transform: (Project) -> Project): Boolean = synchronized(lock) {
        val base = gestureBase ?: return false
        val currentPreview = gesturePreview ?: base.project
        val next = try { transform(currentPreview) } catch (e: Exception) {
            diagnostic = SessionDiagnostic(CommandFailureCode.COMMAND_FAILED, e.message ?: "Gesture preview failed."); publish(); return false
        }
        gesturePreview = freezeProject(next); diagnostic = null; publish(); true
    }

    fun previewDrawWall(id: String, startX: Double, startY: Double, endX: Double, endY: Double, heightMeters: Double = 2.7, thicknessMeters: Double = 0.2): Boolean = synchronized(lock) {
        val wall = WallElement(ModelId(id), axis = WallAxis(Point2(startX, startY), Point2(endX, endY)), heightMeters = heightMeters, thicknessMeters = thicknessMeters)
        updateGesture { project -> project.copy(elements = project.elements.filterNot { it.id == wall.id } + wall) }.also {
            if (it) gestureMutation = AddWallCommand(wall)
        }
    }

    fun previewMoveWall(id: String, deltaX: Double, deltaY: Double): Boolean = synchronized(lock) {
        val base = gestureBase ?: return false
        val wallId = ModelId(id)
        updateGesture { _ -> base.project.copy(elements = base.project.elements.map { element ->
            if (element is WallElement && element.id == wallId) element.copy(axis = WallAxis(
                Point2(element.axis.start.xMeters + deltaX, element.axis.start.yMeters + deltaY),
                Point2(element.axis.end.xMeters + deltaX, element.axis.end.yMeters + deltaY)
            )) else element
        }) }.also { if (it) gestureMutation = MoveWallCommand(wallId, deltaX, deltaY) }
    }

    fun endGesture(): CommandResult = synchronized(lock) { endGestureInternal(gestureMutation) }
    internal fun endGesture(command: ModelMutation?): CommandResult = synchronized(lock) { endGestureInternal(command) }
    private fun endGestureInternal(command: ModelMutation?): CommandResult {
        val base = gestureBase ?: return CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, "No active gesture.")
        val preview = gesturePreview ?: base.project
        val mutation = command ?: gestureMutation ?: ReplaceProjectMutation(gestureLabel, preview)
        clearGesture()
        return commandBus.execute(mutation, expectedRevision = base.number).also { acceptResult(it); publish() }
    }
    fun cancelGesture(): Boolean = synchronized(lock) {
        if (gestureBase == null) return false
        cancelGestureInternal(); publish(); true
    }

    /** Save and edits share the same session lock; the saved marker is the exact revision persisted. */
    fun save(repository: ProjectRepository, path: String): SaveResult = synchronized(lock) {
        val revision = commandBus.snapshot()
        repository.save(path, revision).also { savedRevision = revision; diagnostic = null; publish() }
    }
    fun exportArchive(writer: BimobArchiveWriter = BimobArchiveWriter()): ByteArray = synchronized(lock) {
        writer.write(commandBus.snapshot().project)
    }

    private fun acceptResult(result: CommandResult) {
        diagnostic = when (result) {
            is CommandResult.Committed -> null
            is CommandResult.Rejected -> SessionDiagnostic(result.code, result.message)
        }
    }
    private fun cancelGestureInternal() = clearGesture()
    private fun clearGesture() { gestureBase = null; gesturePreview = null; gestureLabel = "Interactive edit"; gestureMutation = null }
    private fun publish() { mutableState.value = buildSnapshot() }
    private fun buildSnapshot(): SessionSnapshot {
        val revision = commandBus.snapshot()
        val project = gesturePreview ?: revision.project
        val walls = project.elements.filterIsInstance<WallElement>().map { w ->
            WallCanvasSnapshot(w.id.value, w.axis.start.xMeters, w.axis.start.yMeters, w.axis.end.xMeters, w.axis.end.yMeters, w.thicknessMeters, w.heightMeters)
        }
        return SessionSnapshot(
            revisionNumber = revision.number, projectId = revision.project.id.value, projectName = revision.project.name,
            walls = java.util.Collections.unmodifiableList(ArrayList(walls)), elementCount = project.elements.size,
            canUndo = commandBus.undoDepth() > 0, canRedo = commandBus.redoDepth() > 0,
            hasUnsavedChanges = savedRevision?.let { saved -> revision.project != saved.project || revision.dependencyGraph != saved.dependencyGraph || revision.derivedValues != saved.derivedValues } ?: true,
            diagnostic = diagnostic, gestureActive = gestureBase != null
        )
    }

    companion object {
        fun newProject(name: String): ModelSession = ModelSession(Project(name = name.ifBlank { "Новый проект" }))
        fun importedProject(project: Project): ModelSession = ModelSession(project)
        fun migrateLegacyProject(project: Project): ModelSession {
            val session = ModelSession(Project(name = project.name))
            val result = session.execute(ReplaceProjectMutation("Migrate legacy sketch", project))
            require(result is CommandResult.Committed) { "Legacy migration command rejected: $result" }
            return session
        }
        fun load(repository: ProjectRepository, path: String): ModelSession = ModelSession(repository.loadRevision(path), initiallySaved = true)
    }
}

class ReplaceProjectMutation(override val label: String, private val replacement: Project) : ModelMutation {
    override fun apply(before: Project): Project = replacement
}
