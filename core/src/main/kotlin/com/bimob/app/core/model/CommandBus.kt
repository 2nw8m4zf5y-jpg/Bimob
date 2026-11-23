package com.bimob.app.core.model

/**
 * Pure-Kotlin transaction boundary. Commands calculate a candidate immutable Project;
 * CommandBus validates and publishes it atomically. No Android APIs are permitted here.
 */
interface ModelMutation {
    val label: String
    fun apply(before: Project): Project

    /** Produces a private candidate. Implementations must not publish or mutate external state. */
    fun stage(before: Project, context: TransactionContext): Project {
        context.checkpoint()
        val candidate = apply(before)
        context.checkpoint()
        return candidate
    }
}

/** Cooperative cancellation checkpoints available to transactional and composite mutations. */
class TransactionContext internal constructor(private val cancellation: CancellationCheck) {
    fun checkpoint() {
        if (cancellation.isCancelled()) throw TransactionCancelledException()
    }
}

internal class TransactionCancelledException : RuntimeException("Transaction cancelled.")

/** Runs child mutations against a private candidate; only the final result can be committed. */
class BatchCommand(
    override val label: String,
    commands: List<ModelMutation>
) : ModelMutation {
    private val commands = java.util.Collections.unmodifiableList(ArrayList(commands))
    override fun apply(before: Project): Project = stage(before, TransactionContext(CancellationCheck.NEVER))

    override fun stage(before: Project, context: TransactionContext): Project {
        var candidate = before
        commands.forEach { command ->
            context.checkpoint()
            candidate = command.stage(candidate, context)
        }
        context.checkpoint()
        return candidate
    }
}

/** Semantic alias for a grouped transaction with the same atomic staging contract. */
class CompositeCommand(
    override val label: String,
    commands: List<ModelMutation>
) : ModelMutation {
    private val batch = BatchCommand(label, commands)
    override fun apply(before: Project): Project = batch.apply(before)
    override fun stage(before: Project, context: TransactionContext): Project = batch.stage(before, context)
}

data class ModelRevision(val number: Long, val project: Project)

enum class CommandFailureCode {
    STALE_REVISION, CANCELLED, VALIDATION_FAILED, COMMAND_FAILED,
    HISTORY_EMPTY, REVISION_OVERFLOW, EVENT_QUEUE_FULL
}

sealed interface CommandResult {
    data class Committed(val revision: ModelRevision, val issues: List<ValidationIssue>) : CommandResult
    data class Rejected(
        val code: CommandFailureCode,
        val message: String,
        val issues: List<ValidationIssue> = emptyList()
    ) : CommandResult
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

private data class HistoryEntry(val project: Project, val estimatedBytes: Long, val order: Long)

/**
 * Writers serialize state changes under a monitor. Events are queued in commit order
 * and dispatched outside the model lock. The queue limit excludes the callback in flight.
 *
 * History limits apply across Undo and Redo combined. Memory cost uses MemoryEstimator's
 * deterministic logical-size estimate, not device-specific heap measurements. The current
 * published snapshot is never evicted; only historical entries are eligible.
 */
class CommandBus(
    initialProject: Project,
    private val historyLimit: Int = 100,
    private val pendingEventLimit: Int = 1024,
    private val historyMemoryBudgetBytes: Long = 64L * 1024L * 1024L
) {
    private val monitor = Any()
    private val dispatchMonitor = Any()
    private var dispatching = false
    private val pendingEvents = ArrayDeque<Pair<ModelEvent, List<(ModelEvent) -> Unit>>>()
    private var current = ModelRevision(0L, freezeProject(initialProject))
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private var nextHistoryOrder = 0L
    private val listeners = mutableSetOf<(ModelEvent) -> Unit>()

    init {
        require(historyLimit >= 0) { "historyLimit must be non-negative" }
        require(pendingEventLimit > 0) { "pendingEventLimit must be positive" }
        require(historyMemoryBudgetBytes >= 0L) { "historyMemoryBudgetBytes must be non-negative" }
        require(ModelValidator.validate(current.project).none { it.severity == ValidationSeverity.ERROR }) {
            "Initial project must satisfy model invariants."
        }
    }

    fun snapshot(): ModelRevision = synchronized(monitor) { current }

    fun historyMemoryUsageBytes(): Long = synchronized(monitor) { historyBytes() }
    fun undoDepth(): Int = synchronized(monitor) { undoStack.size }
    fun redoDepth(): Int = synchronized(monitor) { redoStack.size }

    fun subscribe(listener: (ModelEvent) -> Unit): AutoCloseable {
        synchronized(monitor) { listeners += listener }
        return AutoCloseable { synchronized(monitor) { listeners -= listener } }
    }

    fun execute(
        mutation: ModelMutation,
        expectedRevision: Long? = null,
        cancellation: CancellationCheck = CancellationCheck.NEVER
    ): CommandResult {
        val committed: ModelRevision
        synchronized(monitor) {
            if (expectedRevision != null && expectedRevision != current.number) {
                return CommandResult.Rejected(
                    CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}."
                )
            }
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled before staging.")

            val before = current
            val context = TransactionContext(cancellation)
            val candidate = try {
                context.checkpoint()
                freezeProject(mutation.stage(before.project, context))
            } catch (_: TransactionCancelledException) {
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled during staging.")
            } catch (e: Exception) {
                return CommandResult.Rejected(CommandFailureCode.COMMAND_FAILED, e.message ?: e::class.java.simpleName)
            }
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled before validation.")

            val issues = ModelValidator.validate(candidate)
            if (issues.any { it.severity == ValidationSeverity.ERROR }) {
                return CommandResult.Rejected(
                    CommandFailureCode.VALIDATION_FAILED,
                    "Candidate model failed validation.",
                    issues
                )
            }
            if (cancellation.isCancelled())
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled after validation.")
            if (candidate == before.project) return CommandResult.Committed(before, issues)

            val nextRevision = nextRevisionNumber(before.number)
                ?: return CommandResult.Rejected(CommandFailureCode.REVISION_OVERFLOW, "Revision counter exhausted.")
            if (!hasEventCapacity())
                return CommandResult.Rejected(CommandFailureCode.EVENT_QUEUE_FULL, "Pending event queue is full.")

            // A new branch invalidates redo before budget accounting, freeing its retained snapshots.
            redoStack.clear()
            if (historyLimit > 0 && historyMemoryBudgetBytes > 0L) {
                undoStack.addLast(historyEntry(before.project))
                trimHistory()
            }
            current = ModelRevision(nextRevision, candidate)
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
            if (expectedRevision != null && expectedRevision != current.number) {
                return CommandResult.Rejected(
                    CommandFailureCode.STALE_REVISION,
                    "Expected revision $expectedRevision, current revision is ${current.number}."
                )
            }
            val source = if (action == HistoryAction.UNDO) undoStack else redoStack
            val destination = if (action == HistoryAction.UNDO) redoStack else undoStack
            if (source.isEmpty())
                return CommandResult.Rejected(CommandFailureCode.HISTORY_EMPTY, "${action.name} history is empty.")
            val nextRevision = nextRevisionNumber(current.number)
                ?: return CommandResult.Rejected(CommandFailureCode.REVISION_OVERFLOW, "Revision counter exhausted.")
            if (!hasEventCapacity())
                return CommandResult.Rejected(CommandFailureCode.EVENT_QUEUE_FULL, "Pending event queue is full.")

            val target = source.removeLast()
            if (historyLimit > 0 && historyMemoryBudgetBytes > 0L) {
                destination.addLast(historyEntry(current.project))
                trimHistory()
            }
            current = ModelRevision(nextRevision, target.project)
            restored = current
            enqueueEvent(ModelEvent.RevisionRestored(restored, action))
        }
        drainEvents()
        return CommandResult.Committed(restored, ModelValidator.validate(restored.project))
    }

    private fun historyEntry(project: Project): HistoryEntry {
        val order = nextHistoryOrder
        if (nextHistoryOrder < Long.MAX_VALUE) nextHistoryOrder++
        return HistoryEntry(project, MemoryEstimator.estimateProjectBytes(project), order)
    }

    /** Evicts the globally oldest stack-bottom entry until both limits are satisfied. */
    private fun trimHistory() {
        while (undoStack.size + redoStack.size > historyLimit ||
            historyBytes() > historyMemoryBudgetBytes
        ) {
            val undoOldest = undoStack.firstOrNull()
            val redoOldest = redoStack.firstOrNull()
            when {
                undoOldest == null && redoOldest == null -> return
                redoOldest == null -> undoStack.removeFirst()
                undoOldest == null -> redoStack.removeFirst()
                undoOldest.order <= redoOldest.order -> undoStack.removeFirst()
                else -> redoStack.removeFirst()
            }
        }
    }

    private fun historyBytes(): Long {
        var total = 0L
        (undoStack.asSequence() + redoStack.asSequence()).forEach { entry ->
            total = if (entry.estimatedBytes > Long.MAX_VALUE - total) Long.MAX_VALUE
            else total + entry.estimatedBytes
        }
        return total
    }

    /** Called only while monitor is held, before state/history publication. */
    private fun hasEventCapacity(): Boolean = pendingEvents.size < pendingEventLimit

    /** Called only while monitor is held. */
    private fun enqueueEvent(event: ModelEvent) {
        pendingEvents.addLast(event to listeners.toList())
    }

    /** One dispatcher drains FIFO. Reentrant commits append and are delivered by this drain. */
    private fun drainEvents() {
        synchronized(dispatchMonitor) {
            if (dispatching) return
            dispatching = true
            try {
                while (true) {
                    val delivery = synchronized(monitor) {
                        if (pendingEvents.isEmpty()) null else pendingEvents.removeFirst()
                    } ?: return
                    delivery.second.forEach { listener ->
                        try { listener(delivery.first) } catch (_: Exception) { /* isolate ordinary listener failures */ }
                    }
                }
            } finally {
                dispatching = false
            }
        }
    }
}

/** Revision IDs are monotonic and never wrap around. */
internal fun nextRevisionNumber(current: Long): Long? =
    if (current == Long.MAX_VALUE) null else current + 1

/** Defensive copies of collection containers prevent callers from mutating published snapshots. */
private fun <T> immutableList(values: Collection<T>): List<T> =
    java.util.Collections.unmodifiableList(ArrayList(values))

private fun freezeProject(project: Project): Project = project.copy(
    buildings = immutableList(project.buildings),
    levels = immutableList(project.levels),
    elements = immutableList(project.elements.map { element ->
        when (element) {
            is WallElement -> element.copy(
                layers = immutableList(element.layers),
                parameters = immutableList(element.parameters)
            )
            else -> element
        }
    }),
    materials = immutableList(project.materials),
    types = immutableList(project.types.map { it.copy(parameters = immutableList(it.parameters)) })
)
