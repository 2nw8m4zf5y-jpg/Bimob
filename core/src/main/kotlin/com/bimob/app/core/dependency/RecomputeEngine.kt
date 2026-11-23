package com.bimob.app.core.dependency

import com.bimob.app.core.model.Project

/** Small portable value vocabulary for derived state; avoids arbitrary JVM object graphs. */
sealed interface DerivedValue {
    data class NumberValue(val value: Double) : DerivedValue {
        init { require(value.isFinite()) }
    }
    data class BooleanValue(val value: Boolean) : DerivedValue
    data class TextValue(val value: String) : DerivedValue
    data class IdValue(val value: String) : DerivedValue
    data class NumberList(val values: List<Double>) : DerivedValue {
        init { require(values.all(Double::isFinite)) }
    }
}

data class EvaluationFailure(val node: DependencyNodeKey, val message: String)

sealed interface EvaluationResult {
    data class Success(val values: Map<DependencyNodeKey, DerivedValue>, val order: List<DependencyNodeKey>) : EvaluationResult
    data class Failure(val failure: EvaluationFailure, val completedOrder: List<DependencyNodeKey>) : EvaluationResult
    data class Cancelled(val completedOrder: List<DependencyNodeKey>) : EvaluationResult
}

fun interface RecomputeCancellation {
    fun isCancelled(): Boolean
    companion object { val NEVER = RecomputeCancellation { false } }
}

fun interface DependencyEvaluator {
    fun evaluate(project: Project, currentValues: Map<DependencyNodeKey, DerivedValue>): DerivedValue
}

data class RecomputePlan(val dirtyNodes: List<DependencyNodeKey>, val order: List<DependencyNodeKey>)

enum class EngineBudget { DIRTY_NODES, EVALUATIONS, FAN_OUT, GRAPH_NODES, GRAPH_EDGES }
data class EngineLimits(
    val maxDirtyNodesPerTransaction: Int = 100_000,
    val maxEvaluationsPerTransaction: Int = 1_000_000,
    val maxFanOutPerNode: Int = 10_000,
    val maxGraphNodes: Int = 250_000,
    val maxGraphEdges: Int = 1_000_000
) {
    init { require(maxDirtyNodesPerTransaction > 0 && maxEvaluationsPerTransaction > 0 && maxFanOutPerNode >= 0 && maxGraphNodes > 0 && maxGraphEdges >= 0) }
}
class EngineBudgetExceeded(val budget: EngineBudget, val node: DependencyNodeKey?, val actual: Int, val maximum: Int) :
    RuntimeException("Engine budget $budget exceeded at ${node?.stableKey() ?: "graph"}: $actual > $maximum.")

/** Deterministic dirty-closure planning and topological execution. Each reachable node appears once. */
object RecomputeEngine {
    fun plan(graph: DependencyGraph, seeds: Set<DependencyNodeKey>, limits: EngineLimits = EngineLimits()): RecomputePlan {
        if (graph.nodeCount > limits.maxGraphNodes) throw EngineBudgetExceeded(EngineBudget.GRAPH_NODES, null, graph.nodeCount, limits.maxGraphNodes)
        if (graph.edgeCount > limits.maxGraphEdges) throw EngineBudgetExceeded(EngineBudget.GRAPH_EDGES, null, graph.edgeCount, limits.maxGraphEdges)
        if (seeds.isEmpty()) return RecomputePlan(emptyList(), emptyList())
        val dirty = linkedSetOf<DependencyNodeKey>()
        val queue = ArrayDeque<DependencyNodeKey>()
        seeds.sortedBy { it.stableKey() }.forEach { if (dirty.add(it)) { if (dirty.size > limits.maxDirtyNodesPerTransaction) throw EngineBudgetExceeded(EngineBudget.DIRTY_NODES, it, dirty.size, limits.maxDirtyNodesPerTransaction); queue.addLast(it) } }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val fanOut = graph.outgoingEdges(node).map { it.dependent }.distinct().size
            if (fanOut > limits.maxFanOutPerNode) throw EngineBudgetExceeded(EngineBudget.FAN_OUT, node, fanOut, limits.maxFanOutPerNode)
            graph.outgoingEdges(node).map { it.dependent }.distinct()
                .sortedBy { it.stableKey() }.forEach { dependent ->
                    if (dirty.add(dependent)) { if (dirty.size > limits.maxDirtyNodesPerTransaction) throw EngineBudgetExceeded(EngineBudget.DIRTY_NODES, dependent, dirty.size, limits.maxDirtyNodesPerTransaction); queue.addLast(dependent) }
                }
        }

        val indegree = dirty.associateWith { node ->
            graph.incomingEdges(node).map { it.source }.distinct().count { it in dirty }
        }.toMutableMap()
        val ready = ArrayDeque<DependencyNodeKey>()
        indegree.filterValues { it == 0 }.keys.sortedBy { it.stableKey() }.forEach(ready::addLast)
        val order = mutableListOf<DependencyNodeKey>()
        while (ready.isNotEmpty()) {
            val node = ready.removeFirst()
            order += node
            graph.outgoingEdges(node).map { it.dependent }.distinct().filter { it in dirty }
                .sortedBy { it.stableKey() }.forEach { child ->
                    val next = indegree.getValue(child) - 1
                    indegree[child] = next
                    if (next == 0) ready.addLast(child)
                }
        }
        check(order.size == dirty.size) { "Dirty subgraph contains a cycle; graph admission invariant was violated." }
        return RecomputePlan(dirty.sortedBy { it.stableKey() }, order)
    }

    fun execute(
        plan: RecomputePlan,
        project: Project,
        initialValues: Map<DependencyNodeKey, DerivedValue>,
        evaluators: Map<DependencyNodeKey, DependencyEvaluator>,
        cancellation: RecomputeCancellation = RecomputeCancellation.NEVER,
        limits: EngineLimits = EngineLimits()
    ): EvaluationResult {
        val staged = initialValues.toMutableMap().apply { plan.dirtyNodes.forEach(::remove) }
        val completed = mutableListOf<DependencyNodeKey>()
        var evaluations = 0
        for (node in plan.order) {
            if (cancellation.isCancelled()) return EvaluationResult.Cancelled(completed.toList())
            val evaluator = evaluators[node] ?: continue
            evaluations++
            if (evaluations > limits.maxEvaluationsPerTransaction) throw EngineBudgetExceeded(EngineBudget.EVALUATIONS, node, evaluations, limits.maxEvaluationsPerTransaction)
            try {
                staged[node] = evaluator.evaluate(project, staged.toMap())
                completed += node
                if (cancellation.isCancelled()) return EvaluationResult.Cancelled(completed.toList())
            } catch (e: Exception) {
                return EvaluationResult.Failure(
                    EvaluationFailure(node, e.message ?: e::class.java.simpleName),
                    completed.toList()
                )
            }
        }
        return EvaluationResult.Success(staged.toMap(), plan.order)
    }
}
