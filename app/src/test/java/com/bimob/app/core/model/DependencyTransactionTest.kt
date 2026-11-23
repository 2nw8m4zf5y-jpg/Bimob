package com.bimob.app.core.model

import com.bimob.app.core.dependency.*
import org.junit.Assert.*
import org.junit.Test

class DependencyTransactionTest {
    private fun key(id: String) = EntityProperty(ModelId(id), PropertyAspect.PARAMETERS)
    private fun edge(a: DependencyNodeKey, b: DependencyNodeKey) = DependencyEdge(a,b)
    private fun project() = Project(name = "Initial")

    @Test fun graphAndModelRollbackTogetherWhenSecondBatchStepFails() {
        val bus = CommandBus(project())
        val a = key("A"); val b = key("B")
        val first = object : ModelMutation {
            override val label = "Stage graph and model"
            override fun apply(before: Project) = before.copy(name = "Private candidate")
            override fun stage(before: Project, context: TransactionContext): Project {
                context.admitDependency(edge(a,b))
                context.markDirty(a)
                context.registerEvaluator(a) { p, _ -> DerivedValue.TextValue(p.name) }
                context.registerEvaluator(b) { _, values -> values[a] ?: error("source value missing") }
                return before.copy(name = "Private candidate")
            }
        }
        val second = object : ModelMutation {
            override val label = "Fail second stage"
            override fun apply(before: Project): Project = error("step two failure")
        }
        val result = bus.execute(BatchCommand("Atomic failure", listOf(first, second)))
        assertEquals(CommandFailureCode.COMMAND_FAILED, (result as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number)
        assertEquals("Initial", bus.snapshot().project.name)
        assertEquals(0, bus.snapshot().dependencyGraph.edgeCount)
        assertTrue(bus.snapshot().derivedValues.isEmpty())
        assertEquals(0, bus.undoDepth())
    }

    @Test fun undoAndRedoRestoreGraphAndDerivedValuesWithProject() {
        val bus = CommandBus(project())
        val a = key("source"); val b = key("dependent")
        val change = object : ModelMutation {
            override val label = "Add dependency"
            override fun apply(before: Project) = before.copy(name = "Changed")
            override fun stage(before: Project, context: TransactionContext): Project {
                context.admitDependency(edge(a,b))
                context.markDirty(a)
                context.registerEvaluator(a) { p, _ -> DerivedValue.TextValue(p.name) }
                context.registerEvaluator(b) { _, values -> values.getValue(a) }
                return before.copy(name = "Changed")
            }
        }
        assertTrue(bus.execute(change) is CommandResult.Committed)
        val committed = bus.snapshot()
        assertEquals(1, committed.dependencyGraph.edgeCount)
        assertEquals(DerivedValue.TextValue("Changed"), committed.derivedValues[b])

        assertTrue(bus.undo() is CommandResult.Committed)
        assertEquals("Initial", bus.snapshot().project.name)
        assertEquals(0, bus.snapshot().dependencyGraph.edgeCount)
        assertTrue(bus.snapshot().derivedValues.isEmpty())

        assertTrue(bus.redo() is CommandResult.Committed)
        assertEquals("Changed", bus.snapshot().project.name)
        assertEquals(1, bus.snapshot().dependencyGraph.edgeCount)
        assertEquals(DerivedValue.TextValue("Changed"), bus.snapshot().derivedValues[b])
    }

    @Test fun diamondRecomputesEachReachableEvaluatorOnceInTopologicalOrder() {
        val bus = CommandBus(project())
        val a=key("A"); val b=key("B"); val c=key("C"); val d=key("D")
        val unrelatedA=key("X"); val unrelatedB=key("Y")
        // Establish graph structure in its own revision; no nodes are marked dirty for recompute.
        val graphSetup=object:ModelMutation {
            override val label="Set dependency graph"
            override fun apply(before:Project)=before
            override fun stage(before:Project,context:TransactionContext):Project {
                context.admitDependencies(listOf(edge(a,b),edge(a,c),edge(b,d),edge(c,d),edge(unrelatedA,unrelatedB)))
                return before
            }
        }
        assertTrue(bus.execute(graphSetup) is CommandResult.Committed)
        val calls=mutableListOf<DependencyNodeKey>()
        val counts=mutableMapOf<DependencyNodeKey,Int>()
        val mutation=object:ModelMutation {
            override val label="Recompute diamond"
            override fun apply(before:Project)=before.copy(name="New input")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.markDirty(a)
                fun evaluator(node:DependencyNodeKey, block:(Project,Map<DependencyNodeKey,DerivedValue>)->DerivedValue)=DependencyEvaluator { p,v ->
                    calls += node; counts[node]=(counts[node]?:0)+1; block(p,v)
                }
                context.registerEvaluator(a,evaluator(a){p,_->DerivedValue.TextValue(p.name)})
                context.registerEvaluator(b,evaluator(b){_,v->v.getValue(a)})
                context.registerEvaluator(c,evaluator(c){_,v->v.getValue(a)})
                context.registerEvaluator(d,evaluator(d){_,v->
                    assertTrue(v.containsKey(b)); assertTrue(v.containsKey(c))
                    DerivedValue.TextValue("B+C")
                })
                context.registerEvaluator(unrelatedA,evaluator(unrelatedA){_,_->DerivedValue.TextValue("X")})
                context.registerEvaluator(unrelatedB,evaluator(unrelatedB){_,v->v.getValue(unrelatedA)})
                return before.copy(name="New input")
            }
        }
        assertTrue(bus.execute(mutation) is CommandResult.Committed)
        assertEquals(1,counts[a]); assertEquals(1,counts[b]); assertEquals(1,counts[c]); assertEquals(1,counts[d])
        assertEquals(0,counts[unrelatedA]?:0); assertEquals(0,counts[unrelatedB]?:0)
        assertEquals(1,calls.count { it==d })
        assertTrue(calls.indexOf(d)>calls.indexOf(b))
        assertTrue(calls.indexOf(d)>calls.indexOf(c))
        assertEquals(DerivedValue.TextValue("B+C"),bus.snapshot().derivedValues[d])
    }

    @Test fun evaluatorFailureRejectsEntireTransactionWithoutPublishingGraph() {
        val bus=CommandBus(project())
        val a=key("A"); val b=key("B")
        val mutation=object:ModelMutation {
            override val label="Evaluator failure"
            override fun apply(before:Project)=before.copy(name="Should not commit")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.admitDependency(edge(a,b)); context.markDirty(a)
                context.registerEvaluator(a){p,_->DerivedValue.TextValue(p.name)}
                context.registerEvaluator(b){_,_->throw IllegalArgumentException("invalid derived result")}
                return before.copy(name="Should not commit")
            }
        }
        val result=bus.execute(mutation) as CommandResult.Rejected
        assertEquals(CommandFailureCode.EVALUATION_FAILED,result.code)
        assertEquals(0L,bus.snapshot().number)
        assertEquals("Initial",bus.snapshot().project.name)
        assertEquals(0,bus.snapshot().dependencyGraph.edgeCount)
        assertTrue(bus.snapshot().derivedValues.isEmpty())
        assertEquals(0,bus.undoDepth())
    }

    @Test fun removingDependencyInvalidatesStaleDerivedDependentValue() {
        val bus=CommandBus(project())
        val a=key("source"); val b=key("dependent")
        val setup=object:ModelMutation {
            override val label="Seed graph and values"
            override fun apply(before:Project)=before.copy(name="Seed")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.admitDependency(edge(a,b)); context.markDirty(a)
                context.registerEvaluator(a){p,_->DerivedValue.TextValue(p.name)}
                context.registerEvaluator(b){_,v->v.getValue(a)}
                return before.copy(name="Seed")
            }
        }
        assertTrue(bus.execute(setup) is CommandResult.Committed)
        assertTrue(bus.snapshot().derivedValues.containsKey(b))
        val remove=object:ModelMutation {
            override val label="Remove edge"
            override fun apply(before:Project)=before.copy(name="Changed")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.removeDependency(edge(a,b))
                return before.copy(name="Changed")
            }
        }
        assertTrue(bus.execute(remove) is CommandResult.Committed)
        assertFalse(bus.snapshot().derivedValues.containsKey(b))
        assertEquals(0,bus.snapshot().dependencyGraph.edgeCount)
    }

    @Test fun historyBudgetCountsGraphAndDerivedSnapshotPayload() {
        val bus=CommandBus(project(),historyMemoryBudgetBytes=1_000_000)
        val a=key("history-source"); val b=key("history-dependent")
        val setup=object:ModelMutation {
            override val label="Store graph"
            override fun apply(before:Project)=before
            override fun stage(before:Project,context:TransactionContext):Project {
                context.admitDependency(edge(a,b))
                return before
            }
        }
        assertTrue(bus.execute(setup) is CommandResult.Committed)
        val baseline=MemoryEstimator.estimateProjectBytes(project())
        val commit=object:ModelMutation {
            override val label="Create graph history snapshot"
            override fun apply(before:Project)=before.copy(name="Revision 2")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.markDirty(a)
                context.registerEvaluator(a){_,_->DerivedValue.TextValue("source".repeat(20))}
                context.registerEvaluator(b){_,v->DerivedValue.TextValue(v.getValue(a).toString().repeat(20))}
                return before.copy(name="Revision 2")
            }
        }
        assertTrue(bus.execute(commit) is CommandResult.Committed)
        assertTrue(bus.historyMemoryUsageBytes()>baseline)
    }

    @Test fun cancellationDuringRecomputeDiscardsModelGraphAndDerivedCandidate() {
        val bus = CommandBus(project())
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val a = key("A"); val b = key("B")
        val mutation = object : ModelMutation {
            override val label = "Cancel in recompute"
            override fun apply(before: Project) = before.copy(name = "Candidate")
            override fun stage(before: Project, context: TransactionContext): Project {
                context.admitDependency(edge(a,b))
                context.markDirty(a)
                context.registerEvaluator(a) { p, _ ->
                    cancelled.set(true)
                    DerivedValue.TextValue(p.name)
                }
                context.registerEvaluator(b) { _, values -> values.getValue(a) }
                return before.copy(name = "Candidate")
            }
        }
        val result = bus.execute(mutation, cancellation = CancellationCheck { cancelled.get() })
        assertEquals(CommandFailureCode.CANCELLED, (result as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number)
        assertEquals("Initial", bus.snapshot().project.name)
        assertEquals(0, bus.snapshot().dependencyGraph.edgeCount)
        assertTrue(bus.snapshot().derivedValues.isEmpty())
        assertEquals(0, bus.undoDepth())
    }

    @Test fun cycleRejectedInTransactionBeforeRevisionPublication() {
        val bus=CommandBus(project())
        val a=key("A"); val b=key("B")
        val mutation=object:ModelMutation {
            override val label="Invalid dependency"
            override fun apply(before:Project)=before.copy(name="Changed")
            override fun stage(before:Project,context:TransactionContext):Project {
                context.admitDependency(edge(a,b)); context.admitDependency(edge(b,a))
                return before.copy(name="Changed")
            }
        }
        val result=bus.execute(mutation) as CommandResult.Rejected
        assertEquals(CommandFailureCode.DEPENDENCY_REJECTED,result.code)
        assertEquals(0L,bus.snapshot().number)
        assertEquals(0,bus.snapshot().dependencyGraph.edgeCount)
    }
}
