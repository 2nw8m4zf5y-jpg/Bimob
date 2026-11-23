package com.bimob.app.core.model

/**
 * Pure-Kotlin transaction boundary. Commands calculate a candidate immutable Project;
 * CommandBus validates and publishes it atomically. No Android APIs are permitted here.
 */
interface ModelMutation {
    val label: String
    fun apply(before: Project): Project
}

data class ModelRevision(val number: Long, val project: Project)

enum class CommandFailureCode { STALE_REVISION, CANCELLED, VALIDATION_FAILED, COMMAND_FAILED, HISTORY_EMPTY }

sealed interface CommandResult {
    data class Committed(val revision: ModelRevision, val issues: List<ValidationIssue>) : CommandResult
    data class Rejected(val code: CommandFailureCode, val message: String, val issues: List<ValidationIssue> = emptyList()) : CommandResult
}

fun interface CancellationCheck {
    fun isCancelled(): Boolean
    companion object { val NEVER = CancellationCheck { false } }
}

sealed interface ModelEvent {
    data class RevisionCommitted(val revision: ModelRevision, val commandLabel: String) : ModelEvent
    data class RevisionRestored(val revision: ModelRevision, val action: HistoryAction) : ModelEvent
}
enum class HistoryAction { UNDO, REDO }

/**
 * Writers serialize state changes under a monitor. Listener deliveries are queued
 * in commit order and dispatched outside the state lock, so callbacks cannot
 * reorder revision events or block snapshot readers during model publication.
 */
class CommandBus(initialProject: Project, private val historyLimit: Int = 100) {
    private val monitor = Any()
    private val dispatchMonitor = Any()
    private val pendingEvents = ArrayDeque<Pair<ModelEvent, List<(ModelEvent) -> Unit>>>()
    private var current = ModelRevision(0L, freezeProject(initialProject))
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private val listeners = mutableSetOf<(ModelEvent) -> Unit>()

    init {
        require(historyLimit >= 0) { "historyLimit must be non-negative" }
        require(ModelValidator.validate(current.project).none { it.severity == ValidationSeverity.ERROR }) {
            "Initial project must satisfy model invariants."
        }
    }

    fun snapshot(): ModelRevision = synchronized(monitor) { current }

    fun subscribe(listener: (ModelEvent) -> Unit): AutoCloseable {
        synchronized(monitor) { listeners += listener }
        return AutoCloseable { synchronized(monitor) { listeners -= listener } }
    }

    fun execute(mutation: ModelMutation, expectedRevision: Long? = null,
               cancellation: CancellationCheck = CancellationCheck.NEVER): CommandResult {
        val committed: ModelRevision
        synchronized(monitor) {
            if (expectedRevision != null && expectedRevision != current.number)
                return CommandResult.Rejected(CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}.")
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled before staging.")

            val before = current
            val candidate = try { freezeProject(mutation.apply(before.project)) }
            catch (t: Throwable) {
                return CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, t.message ?: t::class.java.simpleName)
            }
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled before validation.")
            val issues = ModelValidator.validate(candidate)
            if (issues.any { it.severity == ValidationSeverity.ERROR })
                return CommandResult.Rejected(CommandFailureCode.VALIDATION_FAILED,
                    "Candidate model failed validation.", issues)
            if (candidate == before.project) return CommandResult.Committed(before, issues)

            if (historyLimit > 0) {
                undoStack.addLast(before.project)
                while (undoStack.size > historyLimit) undoStack.removeFirst()
            }
            redoStack.clear()
            current = ModelRevision(before.number + 1, candidate)
            committed = current
            enqueueEvent(ModelEvent.RevisionCommitted(committed, mutation.label))
        }
        drainEvents()
        return CommandResult.Committed(committed, ModelValidator.validate(committed.project))
    }

    fun undo(expectedRevision: Long? = null): CommandResult = restore(HistoryAction.UNDO, expectedRevision)
    fun redo(expectedRevision: Long? = null): CommandResult = restore(HistoryAction.REDO, expectedRevision)

    private fun restore(action: HistoryAction, expectedRevision: Long?): CommandResult {
        val restored: ModelRevision
        synchronized(monitor) {
            if (expectedRevision != null && expectedRevision != current.number)
                return CommandResult.Rejected(CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}.")
            val source = if (action == HistoryAction.UNDO) undoStack else redoStack
            val destination = if (action == HistoryAction.UNDO) redoStack else undoStack
            if (source.isEmpty()) return CommandResult.Rejected(CommandFailureCode.HISTORY_EMPTY, "${action.name} history is empty.")
            val target = source.removeLast()
            destination.addLast(current.project)
            while (destination.size > historyLimit) destination.removeFirst()
            current = ModelRevision(current.number + 1, target)
            restored = current
            enqueueEvent(ModelEvent.RevisionRestored(restored, action))
        }
        drainEvents()
        return CommandResult.Committed(restored, ModelValidator.validate(restored.project))
    }

    private fun enqueueEvent(event: ModelEvent) {
        pendingEvents.addLast(event to listeners.toList())
    }

    /** One dispatcher drains FIFO. Reentrant commits append and are delivered by this drain. */
    private fun drainEvents() {
        synchronized(dispatchMonitor) {
            while (true) {
                val delivery = synchronized(monitor) {
                    if (pendingEvents.isEmpty()) null else pendingEvents.removeFirst()
                } ?: return
                delivery.second.forEach { listener -> runCatching { listener(delivery.first) } }
            }
        }
    }
}

/** Defensive copies of collection containers prevent callers from mutating published snapshots. */
private fun freezeProject(project: Project): Project = project.copy(
    buildings = project.buildings.toList(),
    levels = project.levels.toList(),
    elements = project.elements.map { element ->
        when (element) {
            is WallElement -> element.copy(layers = element.layers.toList(), parameters = element.parameters.toList())
            else -> element
        }
    },
    materials = project.materials.toList(),
    types = project.types.map { it.copy(parameters = it.parameters.toList()) }
)
