package com.bimob.app.core.dependency

/** Immutable configuration-level evaluator map. Transactions may apply local overrides. */
class EvaluatorRegistry private constructor(private val entries: Map<DependencyNodeKey, DependencyEvaluator>) {
    fun evaluatorFor(node: DependencyNodeKey): DependencyEvaluator? = entries[node]
    fun snapshot(): Map<DependencyNodeKey, DependencyEvaluator> = entries.toMap()
    fun withEvaluator(node: DependencyNodeKey, evaluator: DependencyEvaluator): EvaluatorRegistry = EvaluatorRegistry(entries + (node to evaluator))
    fun withoutEvaluator(node: DependencyNodeKey): EvaluatorRegistry = if (node !in entries) this else EvaluatorRegistry(entries - node)
    companion object {
        fun empty() = EvaluatorRegistry(emptyMap())
        fun of(entries: Map<DependencyNodeKey, DependencyEvaluator>) = EvaluatorRegistry(entries.toMap())
    }
}
