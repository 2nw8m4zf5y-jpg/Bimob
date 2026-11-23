package com.bimob.app.core.model

import com.bimob.app.core.dependency.DependencyEdge
import com.bimob.app.core.dependency.DependencyGraph
import com.bimob.app.core.dependency.DependencyNodeKey
import com.bimob.app.core.dependency.DependencyEvaluator
import com.bimob.app.core.dependency.DerivedValue
import com.bimob.app.core.dependency.EdgeAdmission
import com.bimob.app.core.dependency.RecomputeEngine\nimport com.bimob.app.core.dependency.RecomputeCancellation
import com.bimob.app.core.dependency.EvaluationResult

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

/** Transaction-local staging for model, graph and derived values. */
class TransactionContext internal constructor(
    private val cancellation: CancellationCheck,
    initialGraph: DependencyGraph,
    initialValues: Map<DependencyNodeKey, DerivedValue>
) {
    private var stagedGraph = initialGraph
    private val dirtySeeds = linkedSetOf<DependencyNodeKey>()
    private val evaluators = mutableMapOf<DependencyNodeKey, DependencyEvaluator>()
    private var stagedValues = initialValues.toMap()

    val dependencyGraph: DependencyGraph get() = stagedGraph
    val derivedValues: Map<DependencyNodeKey, DerivedValue> get() = stagedValues
    internal val dirtyNodes: Set<DependencyNodeKey> get() = dirtySeeds
    internal val registeredEvaluators: Map<DependencyNodeKey, DependencyEvaluator> get() = evaluators

    fun checkpoint() {
        if (cancellation.isCancelled()) throw TransactionCancelledException()
    }
    fun admitDependency(edge: DependencyEdge) {
        checkpoint()
        when (val result = stagedGraph.admitEdge(edge)) {
            is EdgeAdmission.Accepted -> { stagedGraph = result.graph; dirtySeeds += edge.dependent }
            is EdgeAdmission.Rejected -> throw DependencyAdmissionException(result.diagnostic)
        }
    }
    fun admitDependencies(edges: Collection<DependencyEdge>) {
        checkpoint()
        when (val result = stagedGraph.admitEdges(edges)) {
            is EdgeAdmission.Accepted -> { stagedGraph = result.graph; edges.forEach { dirtySeeds += it.dependent } }
            is EdgeAdmission.Rejected -> throw DependencyAdmissionException(result.diagnostic)
        }
    }
    fun removeDependency(edge: DependencyEdge) { checkpoint(); stagedGraph = stagedGraph.withoutEdge(edge); dirtySeeds += edge.dependent }
    fun removeDependencyNode(node: DependencyNodeKey) { checkpoint(); stagedGraph.outgoingEdges(node).forEach { dirtySeeds += it.dependent }; stagedGraph = stagedGraph.withoutNode(node); stagedValues = stagedValues - node }
    fun markDirty(node: DependencyNodeKey) { checkpoint(); dirtySeeds += node }
    fun registerEvaluator(node: DependencyNodeKey, evaluator: DependencyEvaluator) {
        checkpoint(); evaluators[node] = evaluator
    }
    internal fun publishEvaluated(values: Map<DependencyNodeKey, DerivedValue>) { stagedValues = freezeDerivedValues(values) }
}
internal class DependencyAdmissionException(
    val diagnostic: com.bimob.app.core.dependency.GraphDiagnostic
) : RuntimeException("Dependency graph admission rejected: ${diagnostic.failure}.")

internal class TransactionCancelledException : RuntimeException("Transaction cancelled.")

/** Runs child mutations against a private candidate; only the final result can be committed. */
class BatchCommand(
    override val label: String,
    commands: List<ModelMutation>
) : ModelMutation {
    private val commands = java.util.Collections.unmodifiableList(ArrayList(commands))
    override fun apply(before: Project): Project = stage(before, TransactionContext(CancellationCheck.NEVER, DependencyGraph.empty(), emptyMap()))

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

data class ModelRevision(
    val number: Long,
    val project: Project,
    val dependencyGraph: DependencyGraph = DependencyGraph.empty(),
    val derivedValues: Map<DependencyNodeKey, DerivedValue> = emptyMap()
)

enum class CommandFailureCode {
    STALE_REVISION, CANCELLED, VALIDATION_FAILED, COMMAND_FAILED, DEPENDENCY_REJECTED, EVALUATION_FAILED,
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

private data class HistoryEntry(val state: ModelRevision, val estimatedBytes: Long, val order: Long)

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
            val context = TransactionContext(cancellation, before.dependencyGraph, before.derivedValues)
            val candidate: Project
            val candidateValues: Map<DependencyNodeKey, DerivedValue>
            try {
                context.checkpoint()
                candidate = freezeProject(mutation.stage(before.project, context))
                context.checkpoint()
                val plan = RecomputeEngine.plan(context.dependencyGraph, context.dirtyNodes)
                when (val evaluated = RecomputeEngine.execute(plan, candidate, context.derivedValues, context.registeredEvaluators, RecomputeCancellation { cancellation.isCancelled() })) {
                    is EvaluationResult.Success -> {
                        context.publishEvaluated(evaluated.values)
                        candidateValues = evaluated.values
                    }
                    is EvaluationResult.Cancelled -> return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled during recompute.")
                    is EvaluationResult.Failure -> return CommandResult.Rejected(
                        CommandFailureCode.EVALUATION_FAILED,
                        "Evaluator failed at ${evaluated.failure.node.stableKey()}: ${evaluated.failure.message}"
                    )
                }
                context.checkpoint()
            } catch (_: TransactionCancelledException) {
                return CommandResult.Rejected(CommandFailureCode.CANCELLED, "Cancelled during staging or recompute.")
            } catch (e: DependencyAdmissionException) {
                return CommandResult.Rejected(
                    CommandFailureCode.DEPENDENCY_REJECTED,
                    "${e.diagnostic.failure}: ${e.diagnostic.path.joinToString(" -> ") { it.stableKey() }}"
                )
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
            if (candidate == before.project && context.dependencyGraph == before.dependencyGraph && candidateValues == before.derivedValues) return CommandResult.Committed(before, issues)

            val nextRevision = nextRevisionNumber(before.number)
                ?: return CommandResult.Rejected(CommandFailureCode.REVISION_OVERFLOW, "Revision counter exhausted.")
            if (!hasEventCapacity())
                return CommandResult.Rejected(CommandFailureCode.EVENT_QUEUE_FULL, "Pending event queue is full.")

            // A new branch invalidates redo before budget accounting, freeing its retained snapshots.
            redoStack.clear()
            if (historyLimit > 0 && historyMemoryBudgetBytes > 0L) {
                undoStack.addLast(historyEntry(before))
                trimHistory()
            }
            current = ModelRevision(nextRevision, candidate, context.dependencyGraph, freezeDerivedValues(candidateValues))
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
                destination.addLast(historyEntry(current))
                trimHistory()
            }
            current = ModelRevision(nextRevision, target.state.project, target.state.dependencyGraph, freezeDerivedValues(target.state.derivedValues))
            restored = current
            enqueueEvent(ModelEvent.RevisionRestored(restored, action))
        }
        drainEvents()
        return CommandResult.Committed(restored, ModelValidator.validate(restored.project))
    }

    private fun historyEntry(state: ModelRevision): HistoryEntry {
        val order = nextHistoryOrder
        if (nextHistoryOrder < Long.MAX_VALUE) nextHistoryOrder++
        return HistoryEntry(state, estimateRevisionBytes(state), order)
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
fun nextRevisionNumber(current: Long): Long? =
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

/** Freeze heterogeneous derived-state payloads so revisions cannot be mutated by callers. */
private fun freezeDerivedValues(values: Map<DependencyNodeKey, DerivedValue>): Map<DependencyNodeKey, DerivedValue> {
    val frozen = values.mapValues { (_, value) ->
        when (value) {
            is DerivedValue.NumberList -> value.copy(values = java.util.Collections.unmodifiableList(ArrayList(value.values)))
            else -> value
        }
    }
    return java.util.Collections.unmodifiableMap(frozen)
}

/** Conservative logical history cost including dependency topology and derived payloads. */
private fun estimateRevisionBytes(state: ModelRevision): Long {
    var total = MemoryEstimator.estimateProjectBytes(state.project)
    fun add(bytes: Long) { total = if (bytes > Long.MAX_VALUE - total) Long.MAX_VALUE else total + bytes }
    state.dependencyGraph.nodes.forEach { node ->
        val keyBytes = node.stableKey().length.toLong() * 2L
        add(if (keyBytes > Long.MAX_VALUE - 64L) Long.MAX_VALUE else 64L + keyBytes)
    }
    state.dependencyGraph.edges.forEach { edge ->
        val keyBytes = (edge.source.stableKey().length.toLong() + edge.dependent.stableKey().length.toLong()) * 2L
        add(if (keyBytes > Long.MAX_VALUE - 48L) Long.MAX_VALUE else 48L + keyBytes)
    }
    state.derivedValues.forEach { (key, value) ->
        val keyBytes = key.stableKey().length.toLong() * 2L
        val payloadBytes = when (value) {
            is DerivedValue.NumberValue -> 16L
            is DerivedValue.BooleanValue -> 8L
            is DerivedValue.TextValue -> 40L + value.value.length.toLong() * 2L
            is DerivedValue.IdValue -> 32L + value.value.length.toLong() * 2L
            is DerivedValue.NumberList -> 24L + value.values.size.toLong() * 8L
        }
        val itemBytes = if (payloadBytes > Long.MAX_VALUE - keyBytes - 48L) Long.MAX_VALUE else payloadBytes + keyBytes + 48L
        add(itemBytes)
    }
    return total
}
