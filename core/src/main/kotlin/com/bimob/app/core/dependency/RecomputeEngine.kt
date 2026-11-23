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

fun interface RecomputeCancellation {\n    fun isCancelled(): Boolean\n    companion object { val NEVER = RecomputeCancellation { false } }\n}\n\nfun interface DependencyEvaluator {
    fun evaluate(project: Project, currentValues: Map<DependencyNodeKey, DerivedValue>): DerivedValue
}

data class RecomputePlan(val dirtyNodes: List<DependencyNodeKey>, val order: List<DependencyNodeKey>)

/** Deterministic dirty-closure planning and topological execution. Each reachable node appears once. */
object RecomputeEngine {
    fun plan(graph: DependencyGraph, seeds: Set<DependencyNodeKey>): RecomputePlan {
        if (seeds.isEmpty()) return RecomputePlan(emptyList(), emptyList())
        val dirty = linkedSetOf<DependencyNodeKey>()
        val queue = ArrayDeque<DependencyNodeKey>()
        seeds.sortedBy { it.stableKey() }.forEach { if (dirty.add(it)) queue.addLast(it) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            graph.outgoingEdges(node).map { it.dependent }.distinct()
                .sortedBy { it.stableKey() }.forEach { dependent ->
                    if (dirty.add(dependent)) queue.addLast(dependent)
                }
        }

        val indegree = dirty.associateWith { node ->
            graph.incomingEdges(node).map { it.source }.distinct().count { it in dirty }
        }.toMutableMap()
        val ready = java.util.PriorityQueue<DependencyNodeKey>(compareBy { it.stableKey() })
        indegree.filterValues { it == 0 }.keys.forEach(ready::add)
        val order = mutableListOf<DependencyNodeKey>()
        while (ready.isNotEmpty()) {
            val node = ready.remove()
            order += node
            graph.outgoingEdges(node).map { it.dependent }.distinct().filter { it in dirty }
                .sortedBy { it.stableKey() }.forEach { child ->
                    val next = indegree.getValue(child) - 1
                    indegree[child] = next
                    if (next == 0) ready.add(child)
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
        cancellation: RecomputeCancellation = RecomputeCancellation.NEVER
    ): EvaluationResult {
        val staged = initialValues.toMutableMap().apply { plan.dirtyNodes.forEach(::remove) }
        val completed = mutableListOf<DependencyNodeKey>()
        for (node in plan.order) {
            if (cancellation.isCancelled()) return EvaluationResult.Cancelled(completed.toList())
            val evaluator = evaluators[node] ?: continue
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
