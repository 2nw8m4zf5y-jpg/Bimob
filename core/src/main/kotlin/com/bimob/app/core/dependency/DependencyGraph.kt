package com.bimob.app.core.dependency

import com.bimob.app.core.model.ModelId

/**
 * Stable, typed dependency identity. Keys are data descriptors, never JVM object references.
 * This vocabulary is deliberately flat and suitable for a future C++20 value-type port.
 */
sealed interface DependencyNodeKey {
    fun stableKey(): String
}

data class EntityProperty(
    val entityId: ModelId,
    val property: PropertyAspect
) : DependencyNodeKey {
    override fun stableKey() = "entity:${entityId.value}:property:${property.name}"
}

data class LevelElevation(val levelId: ModelId) : DependencyNodeKey {
    override fun stableKey() = "level:${levelId.value}:elevation"
}

data class WallGeometry(val wallId: ModelId) : DependencyNodeKey {
    override fun stableKey() = "wall:${wallId.value}:geometry"
}

data class OpeningCut(val openingId: ModelId) : DependencyNodeKey {
    override fun stableKey() = "opening:${openingId.value}:cut"
}

data class SpaceBoundary(val spaceId: ModelId) : DependencyNodeKey {
    override fun stableKey() = "space:${spaceId.value}:boundary"
}

data class SpaceArea(val spaceId: ModelId) : DependencyNodeKey {
    override fun stableKey() = "space:${spaceId.value}:area"
}

enum class PropertyAspect {
    AXIS, HEIGHT, THICKNESS, MATERIALS, TYPE, LEVEL, PARAMETERS, POSITION, DIMENSIONS, HOST
}

enum class DependencyKind {
    SEMANTIC_REFERENCE, OWNERSHIP_CONTEXT, HOSTED_GEOMETRY, FORMULA, CONSTRAINT, DERIVED_AGGREGATION
}

/** source -> dependent. The source is an input of the dependent. */
data class DependencyEdge(
    val source: DependencyNodeKey,
    val dependent: DependencyNodeKey,
    val kind: DependencyKind = DependencyKind.DERIVED_AGGREGATION
) {
    fun stableKey() = "${source.stableKey()}->${dependent.stableKey()}:${kind.name}"
}

enum class GraphAdmissionFailure {
    CYCLE_DETECTED, MAX_DEPTH_EXCEEDED
}

data class GraphDiagnostic(
    val failure: GraphAdmissionFailure,
    /** Closed path for cycles (first key is repeated at the end); path for depth failures. */
    val path: List<DependencyNodeKey>,
    val configuredMaximum: Int
)

sealed interface EdgeAdmission {
    data class Accepted(val graph: DependencyGraph) : EdgeAdmission
    data class Rejected(val diagnostic: GraphDiagnostic) : EdgeAdmission
}

data class DependencyGraphLimits(val maxDependencyDepth: Int = 4096) {
    init { require(maxDependencyDepth >= 1) { "maxDependencyDepth must be >= 1" } }
}

/**
 * Immutable snapshot of a DAG. Mutation APIs return a new graph (copy-on-write).
 * Internally stores flat key sets and adjacency lists, with deterministic iteration.
 */
class DependencyGraph private constructor(
    private val nodeSet: Set<DependencyNodeKey>,
    private val edgeSet: Set<DependencyEdge>,
    private val outgoing: Map<DependencyNodeKey, List<DependencyEdge>>,
    private val incoming: Map<DependencyNodeKey, List<DependencyEdge>>,
    val limits: DependencyGraphLimits
) {
    val nodes: List<DependencyNodeKey> get() = nodeSet.sortedBy { it.stableKey() }
    val edges: List<DependencyEdge> get() = edgeSet.sortedBy { it.stableKey() }
    val nodeCount: Int get() = nodeSet.size
    val edgeCount: Int get() = edgeSet.size

    fun containsNode(key: DependencyNodeKey) = key in nodeSet
    fun containsEdge(edge: DependencyEdge) = edge in edgeSet
    fun outgoingEdges(key: DependencyNodeKey): List<DependencyEdge> = outgoing[key].orEmpty()
    fun incomingEdges(key: DependencyNodeKey): List<DependencyEdge> = incoming[key].orEmpty()

    fun withNode(key: DependencyNodeKey): DependencyGraph {
        if (key in nodeSet) return this
        return build(nodeSet + key, edgeSet, limits)
    }

    fun admitEdge(edge: DependencyEdge): EdgeAdmission = admitEdges(listOf(edge))

    /** Batch admission is atomic: on rejection, the original graph is untouched. */
    fun admitEdges(batch: Collection<DependencyEdge>): EdgeAdmission {
        val candidateEdges = edgeSet + batch
        val candidateNodes = nodeSet + batch.flatMap { listOf(it.source, it.dependent) }
        val candidate = build(candidateNodes, candidateEdges, limits)
        val cycle = candidate.findCycle()
        if (cycle != null) return EdgeAdmission.Rejected(
            GraphDiagnostic(GraphAdmissionFailure.CYCLE_DETECTED, cycle, limits.maxDependencyDepth)
        )
        val tooDeep = candidate.longestPath()
        if (tooDeep.size > limits.maxDependencyDepth) return EdgeAdmission.Rejected(
            GraphDiagnostic(GraphAdmissionFailure.MAX_DEPTH_EXCEEDED, tooDeep, limits.maxDependencyDepth)
        )
        return EdgeAdmission.Accepted(candidate)
    }

    fun withoutEdge(edge: DependencyEdge): DependencyGraph {
        if (edge !in edgeSet) return this
        return build(nodeSet, edgeSet - edge, limits)
    }

    /** Removes a node and all incident edges, preventing dangling adjacency references. */
    fun withoutNode(key: DependencyNodeKey): DependencyGraph {
        if (key !in nodeSet) return this
        val remaining = edgeSet.filterNot { it.source == key || it.dependent == key }.toSet()
        return build(nodeSet - key, remaining, limits)
    }

    private fun findCycle(): List<DependencyNodeKey>? {
        // Iterative DFS with explicit frames avoids recursion on deep graphs.
        val color = HashMap<DependencyNodeKey, Int>() // 0=unseen, 1=active, 2=done
        val parent = HashMap<DependencyNodeKey, DependencyNodeKey>()
        for (start in nodes) {
            if (color[start] != null) continue
            color[start] = 1
            val stack = ArrayDeque<Pair<DependencyNodeKey, Iterator<DependencyNodeKey>>>()
            stack.addLast(start to neighbors(start).iterator())
            while (stack.isNotEmpty()) {
                val (current, iterator) = stack.last()
                if (!iterator.hasNext()) {
                    color[current] = 2
                    stack.removeLast()
                    continue
                }
                val next = iterator.next()
                when (color[next] ?: 0) {
                    0 -> {
                        parent[next] = current
                        color[next] = 1
                        stack.addLast(next to neighbors(next).iterator())
                    }
                    1 -> {
                        val chain = mutableListOf(current)
                        var cursor = current
                        while (cursor != next) {
                            cursor = parent[cursor] ?: break
                            chain.add(cursor)
                        }
                        chain.reverse()
                        chain.add(next)
                        return chain
                    }
                }
            }
        }
        return null
    }

    /** Returns the deterministic longest path as node sequence, or empty for an empty graph. */
    private fun longestPath(): List<DependencyNodeKey> {
        if (nodeSet.isEmpty()) return emptyList()
        val indegree = nodeSet.associateWith { incoming[it].orEmpty().size }.toMutableMap()
        val ready = ArrayDeque<DependencyNodeKey>()
        indegree.filterValues { it == 0 }.keys.sortedBy { it.stableKey() }.forEach(ready::addLast)
        val distance = nodeSet.associateWith { 1 }.toMutableMap()
        val parent = HashMap<DependencyNodeKey, DependencyNodeKey>()
        var processed = 0
        var maxNode: DependencyNodeKey? = null
        while (ready.isNotEmpty()) {
            val current = ready.removeFirst()
            processed++
            if (maxNode == null || distance.getValue(current) > distance.getValue(maxNode!!)) maxNode = current
            for (edge in outgoing[current].orEmpty()) {
                val child = edge.dependent
                val candidate = distance.getValue(current) + 1
                if (candidate > distance.getValue(child)) {
                    distance[child] = candidate
                    parent[child] = current
                }
                val remaining = indegree.getValue(child) - 1
                indegree[child] = remaining
                if (remaining == 0) ready.addLast(child)
            }
        }
        if (processed != nodeSet.size) return emptyList() // cycle is reported before depth check
        var cursor = maxNode ?: return emptyList()
        val path = mutableListOf(cursor)
        while (parent[cursor] != null) {
            cursor = parent.getValue(cursor)
            path.add(cursor)
        }
        path.reverse()
        return path
    }

    private fun neighbors(key: DependencyNodeKey): List<DependencyNodeKey> =
        outgoing[key].orEmpty().map { it.dependent }.distinct()

    companion object {
        fun empty(limits: DependencyGraphLimits = DependencyGraphLimits()): DependencyGraph =
            build(emptySet(), emptySet(), limits)

        private fun build(
            nodes: Set<DependencyNodeKey>,
            edges: Set<DependencyEdge>,
            limits: DependencyGraphLimits
        ): DependencyGraph {
            val normalizedNodes = nodes.toSet()
            val normalizedEdges = edges.toSet()
            val outMutable = normalizedNodes.associateWith { mutableListOf<DependencyEdge>() }
            val intoMutable = normalizedNodes.associateWith { mutableListOf<DependencyEdge>() }
            normalizedEdges.forEach { edge ->
                outMutable.getValue(edge.source).add(edge)
                intoMutable.getValue(edge.dependent).add(edge)
            }
            val out = outMutable.mapValues { (_, list) -> list.sortedBy { it.stableKey() } }
            val into = intoMutable.mapValues { (_, list) -> list.sortedBy { it.stableKey() } }
            return DependencyGraph(
                normalizedNodes, normalizedEdges,
                out.toMap(), into.toMap(), limits
            )
        }
    }
}
