package com.bimob.app.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-facing serialized owner of model mutations.
 * The session never exposes mutable model collections; previews are immutable Project copies.
 */
data class SessionSnapshot(
    val revision: ModelRevision,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val hasUnsavedChanges: Boolean,
    val diagnostic: SessionDiagnostic? = null,
    val gestureActive: Boolean = false,
    val previewProject: Project? = null
) {
    val displayedProject: Project get() = previewProject ?: revision.project
}

data class SessionDiagnostic(val code: CommandFailureCode, val message: String)

class ModelSession(
    initialProject: Project,
    private val commandBus: CommandBus = CommandBus(initialProject),
    initiallySaved: Boolean = false
) {
    private val lock = Any()
    private var savedProject: Project? = if (initiallySaved) initialProject else null
    private var gestureBase: ModelRevision? = null
    private var gesturePreview: Project? = null
    private var gestureLabel: String = "Interactive edit"
    private var diagnostic: SessionDiagnostic? = null
    private val mutableState = MutableStateFlow(buildSnapshot())
    val state: StateFlow<SessionSnapshot> = mutableState.asStateFlow()

    fun snapshot(): SessionSnapshot = synchronized(lock) { mutableState.value }

    fun execute(command: ModelMutation): CommandResult = synchronized(lock) {
        if (gestureBase != null) {
            val result = CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, "A gesture transaction is active; finish or cancel it first.")
            diagnostic = SessionDiagnostic(result.code, result.message)
            publish()
            return result
        }
        val result = commandBus.execute(command)
        acceptResult(result)
        publish()
        result
    }

    fun undo(): CommandResult = synchronized(lock) {
        cancelGestureInternal()
        val result = commandBus.undo()
        acceptResult(result)
        publish()
        result
    }

    fun redo(): CommandResult = synchronized(lock) {
        cancelGestureInternal()
        val result = commandBus.redo()
        acceptResult(result)
        publish()
        result
    }

    /** Starts an ephemeral edit. No revision or Undo entry is created until endGesture(). */
    fun beginGesture(label: String = "Interactive edit"): Boolean = synchronized(lock) {
        if (gestureBase != null) return false
        gestureBase = commandBus.snapshot()
        gesturePreview = gestureBase!!.project
        gestureLabel = label
        diagnostic = null
        publish()
        true
    }

    /** Pure transform over the latest preview; callers pass no mutable UI state. */
    fun updateGesture(transform: (Project) -> Project): Boolean = synchronized(lock) {
        val base = gestureBase ?: return false
        val currentPreview = gesturePreview ?: base.project
        val next = try { transform(currentPreview) } catch (e: Exception) {
            diagnostic = SessionDiagnostic(CommandFailureCode.COMMAND_FAILED, e.message ?: "Gesture preview failed.")
            publish()
            return false
        }
        gesturePreview = next
        diagnostic = null
        publish()
        true
    }

    /** Commits the whole gesture as one command/history revision. */
    fun endGesture(): CommandResult = synchronized(lock) {
        val base = gestureBase ?: return CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, "No active gesture.")
        val preview = gesturePreview ?: base.project
        clearGesture()
        val mutation = ReplaceProjectMutation(gestureLabel, preview)
        val result = commandBus.execute(mutation, expectedRevision = base.number)
        acceptResult(result)
        publish()
        result
    }

    /** Cancellation discards the preview and never touches CommandBus/history. */
    fun cancelGesture(): Boolean = synchronized(lock) {
        if (gestureBase == null) return false
        cancelGestureInternal()
        publish()
        true
    }

    /** Called only after a repository has persisted this immutable revision snapshot. */
    fun markSaved(savedRevision: ModelRevision) = synchronized(lock) {
        savedProject = savedRevision.project
        diagnostic = null
        publish()
    }

    private fun acceptResult(result: CommandResult) {
        when (result) {
            is CommandResult.Committed -> diagnostic = null
            is CommandResult.Rejected -> diagnostic = SessionDiagnostic(result.code, result.message)
        }
    }
    private fun cancelGestureInternal() { clearGesture() }
    private fun clearGesture() { gestureBase = null; gesturePreview = null; gestureLabel = "Interactive edit" }
    private fun publish() { mutableState.value = buildSnapshot() }
    private fun buildSnapshot(): SessionSnapshot {
        val revision = commandBus.snapshot()
        return SessionSnapshot(
            revision = revision,
            canUndo = commandBus.undoDepth() > 0,
            canRedo = commandBus.redoDepth() > 0,
            hasUnsavedChanges = savedProject == null || revision.project != savedProject,
            diagnostic = diagnostic,
            gestureActive = gestureBase != null,
            previewProject = gesturePreview
        )
    }
}

class ReplaceProjectMutation(
    override val label: String,
    private val replacement: Project
) : ModelMutation {
    override fun apply(before: Project): Project = replacement
}
