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
 * Serializes writers under a short monitor; readers get immutable revision snapshots.
 * Observer callbacks are invoked after the lock and isolated from commit failures.
 */
class CommandBus(initialProject: Project, private val historyLimit: Int = 100) {
    private val monitor = Any()
    private var current = ModelRevision(0L, initialProject)
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private val listeners = mutableSetOf<(ModelEvent) -> Unit>()

    init { require(historyLimit >= 0) { "historyLimit must be non-negative" } }

    fun snapshot(): ModelRevision = synchronized(monitor) { current }

    fun subscribe(listener: (ModelEvent) -> Unit): AutoCloseable {
        synchronized(monitor) { listeners += listener }
        return AutoCloseable { synchronized(monitor) { listeners -= listener } }
    }

    fun execute(mutation: ModelMutation, expectedRevision: Long? = null,
               cancellation: CancellationCheck = CancellationCheck.NEVER): CommandResult {
        val event: ModelEvent.RevisionCommitted
        val subscribers: List<(ModelEvent) -> Unit>
        synchronized(monitor) {
            if (expectedRevision != null && expectedRevision != current.number)
                return CommandResult.Rejected(CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}.")
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled before staging.")

            val before = current
            val candidate = try { mutation.apply(before.project) }
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
            event = ModelEvent.RevisionCommitted(current, mutation.label)
            subscribers = listeners.toList()
        }
        publish(event, subscribers)
        return CommandResult.Committed(event.revision, ModelValidator.validate(event.revision.project))
    }

    fun undo(expectedRevision: Long? = null): CommandResult = restore(HistoryAction.UNDO, expectedRevision)
    fun redo(expectedRevision: Long? = null): CommandResult = restore(HistoryAction.REDO, expectedRevision)

    private fun restore(action: HistoryAction, expectedRevision: Long?): CommandResult {
        val event: ModelEvent.RevisionRestored
        val subscribers: List<(ModelEvent) -> Unit>
        synchronized(monitor) {
            if (expectedRevision != null && expectedRevision != current.number)
                return CommandResult.Rejected(CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}.")
            val source = if (action == HistoryAction.UNDO) undoStack else redoStack
            val destination = if (action == HistoryAction.UNDO) redoStack else undoStack
            if (source.isEmpty()) return CommandResult.Rejected(CommandFailureCode.HISTORY_EMPTY, "${action.name} history is empty.")
            val target = source.removeLast()
            destination.addLast(current.project)
            current = ModelRevision(current.number + 1, target)
            event = ModelEvent.RevisionRestored(current, action)
            subscribers = listeners.toList()
        }
        publish(event, subscribers)
        return CommandResult.Committed(event.revision, ModelValidator.validate(event.revision.project))
    }

    private fun publish(event: ModelEvent, subscribers: List<(ModelEvent) -> Unit) {
        subscribers.forEach { listener -> runCatching { listener(event) } }
    }
}
