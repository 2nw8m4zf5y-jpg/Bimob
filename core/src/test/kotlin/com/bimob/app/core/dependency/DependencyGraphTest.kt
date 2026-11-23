package com.bimob.app.core.dependency

import com.bimob.app.core.model.ModelId
import org.junit.Assert.*
import org.junit.Test

class DependencyGraphTest {
    private fun id(value: String) = ModelId(value)
    private fun level(value: String) = LevelElevation(id(value))
    private fun wall(value: String) = WallGeometry(id(value))
    private fun room(value: String) = SpaceArea(id(value))
    private fun edge(a: DependencyNodeKey, b: DependencyNodeKey) = DependencyEdge(a, b)

    @Test fun admitsChainAndDiamondWithoutFalseCycle() {
        val l = level("L1")
        val w1 = wall("W1")
        val w2 = wall("W2")
        val r = room("R1")
        val graph = DependencyGraph.empty().admitEdges(listOf(edge(l,w1), edge(l,w2), edge(w1,r), edge(w2,r)))
        assertTrue(graph is EdgeAdmission.Accepted)
        val g = (graph as EdgeAdmission.Accepted).graph
        assertEquals(4, g.nodeCount)
        assertEquals(4, g.edgeCount)
        assertEquals(2, g.incomingEdges(r).size)
        assertEquals(2, g.outgoingEdges(l).size)
    }

    @Test fun detectsSelfCycleAndReportsClosedPath() {
        val a = level("A")
        assertCycle(DependencyGraph.empty().admitEdge(edge(a,a)), listOf(a,a))
    }

    @Test fun detectsPairCycle() {
        val a = level("A"); val b = wall("B")
        val g = DependencyGraph.empty().admitEdge(edge(a,b)) as EdgeAdmission.Accepted
        assertCycle(g.graph.admitEdge(edge(b,a)), listOf(a,b,a))
    }

    @Test fun detectsTransitiveCycleWithExactPath() {
        val a = level("A"); val b = wall("B"); val c = room("C")
        var g = DependencyGraph.empty()
        g = (g.admitEdge(edge(a,b)) as EdgeAdmission.Accepted).graph
        g = (g.admitEdge(edge(b,c)) as EdgeAdmission.Accepted).graph
        assertCycle(g.admitEdge(edge(c,a)), listOf(a,b,c,a))
    }

    @Test fun batchCycleRejectsWholeCandidate() {
        val a = level("A"); val b = wall("B"); val c = room("C")
        val before = (DependencyGraph.empty().admitEdge(edge(a,b)) as EdgeAdmission.Accepted).graph
        val result = before.admitEdges(listOf(edge(b,c), edge(c,a)))
        assertCycle(result, listOf(a,b,c,a))
        assertEquals(1, before.edgeCount)
        assertFalse(before.containsNode(c))
    }

    @Test fun removingEdgeAndNodeLeavesNoDanglingAdjacency() {
        val a = level("A"); val b = wall("B"); val c = room("C")
        var g = (DependencyGraph.empty().admitEdges(listOf(edge(a,b), edge(b,c))) as EdgeAdmission.Accepted).graph
        g = g.withoutEdge(edge(a,b))
        assertEquals(0, g.outgoingEdges(a).size)
        assertEquals(0, g.incomingEdges(b).size)
        assertEquals(1, g.edgeCount)
        g = g.withoutNode(b)
        assertFalse(g.containsNode(b))
        assertEquals(0, g.edgeCount)
        assertTrue(g.nodes.none { it == b })
        assertTrue(g.outgoingEdges(a).isEmpty())
        assertTrue(g.incomingEdges(c).isEmpty())
    }

    @Test fun rejectsDepthOverLimitAndPreservesOriginal() {
        val a = level("A"); val b = wall("B"); val c = room("C")
        val base = DependencyGraph.empty(DependencyGraphLimits(maxDependencyDepth = 2))
        val accepted = base.admitEdges(listOf(edge(a,b))) as EdgeAdmission.Accepted
        val result = accepted.graph.admitEdge(edge(b,c))
        assertTrue(result is EdgeAdmission.Rejected)
        val diagnostic = (result as EdgeAdmission.Rejected).diagnostic
        assertEquals(GraphAdmissionFailure.MAX_DEPTH_EXCEEDED, diagnostic.failure)
        assertEquals(listOf(a,b,c), diagnostic.path)
        assertEquals(1, accepted.graph.edgeCount)
    }

    @Test fun graphSnapshotsAreImmutableAndCopyOnWrite() {
        val a = level("A"); val b = wall("B")
        val g0 = DependencyGraph.empty()
        val g1 = (g0.admitEdge(edge(a,b)) as EdgeAdmission.Accepted).graph
        assertEquals(0, g0.edgeCount)
        assertEquals(1, g1.edgeCount)
        assertEquals(listOf(a), g1.nodes.filter { it == a })
        assertEquals(listOf(edge(a,b)), g1.edges)
    }

    private fun assertCycle(result: EdgeAdmission, expected: List<DependencyNodeKey>) {
        assertTrue(result is EdgeAdmission.Rejected)
        val diagnostic = (result as EdgeAdmission.Rejected).diagnostic
        assertEquals(GraphAdmissionFailure.CYCLE_DETECTED, diagnostic.failure)
        assertEquals(expected, diagnostic.path)
    }
}
