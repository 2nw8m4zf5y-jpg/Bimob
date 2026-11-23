package com.bimob.app.core.model

import org.junit.Assert.*
import org.junit.Test

class CommandBusTest {
    @Test fun revisionCounterNeverWraps() {
        assertEquals(1L, nextRevisionNumber(0L))
        assertEquals(Long.MAX_VALUE, nextRevisionNumber(Long.MAX_VALUE - 1))
        assertNull(nextRevisionNumber(Long.MAX_VALUE))
    }
    private fun project() = Project(name = "Initial")
    private fun rename() = object : ModelMutation {
        override val label = "Rename project"
        override fun apply(before: Project) = before.copy(name = "Renamed")
    }

    @Test fun snapshotsDetachAndExposeUnmodifiableCollections() {
        val mutableBuildings = mutableListOf(Building(name = "Before"))
        val mutableLevels = mutableListOf<Level>()
        val source = Project(name = "P", buildings = mutableBuildings, levels = mutableLevels)
        val bus = CommandBus(source)
        mutableBuildings += Building(name = "After")
        assertEquals(1, bus.snapshot().project.buildings.size)
        assertEquals("Before", bus.snapshot().project.buildings.single().name)
        assertThrows(UnsupportedOperationException::class.java) {
            (bus.snapshot().project.buildings as MutableList<Building>).add(Building(name = "Injected"))
        }
    }

    @Test fun commitPublishesRevisionAndEvent() {
        val bus = CommandBus(project()); val events = mutableListOf<ModelEvent>()
        bus.subscribe { events.add(it) }
        assertTrue(bus.execute(rename(), 0) is CommandResult.Committed)
        assertEquals(1L, bus.snapshot().number); assertEquals("Renamed", bus.snapshot().project.name)
        assertEquals(1, events.size)
    }
    @Test fun staleRevisionRejected() {
        val bus = CommandBus(project()); val r = bus.execute(rename(), 9)
        assertEquals(CommandFailureCode.STALE_REVISION, (r as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number); assertEquals("Initial", bus.snapshot().project.name)
    }
    @Test fun validationFailureDoesNotPublish() {
        val bus = CommandBus(project())
        val cmd = object : ModelMutation { override val label="Invalid"; override fun apply(before:Project)=before.copy(schemaVersion=0) }
        val r=bus.execute(cmd); assertEquals(CommandFailureCode.VALIDATION_FAILED,(r as CommandResult.Rejected).code)
        assertEquals(0L,bus.snapshot().number)
    }
    @Test fun exceptionDoesNotPublish() {
        val bus=CommandBus(project())
        val cmd=object:ModelMutation{override val label="Fail";override fun apply(before:Project):Project=error("boom")}
        val r=bus.execute(cmd); assertEquals(CommandFailureCode.COMMAND_FAILED,(r as CommandResult.Rejected).code)
        assertEquals(0L,bus.snapshot().number)
    }
    @Test fun cancellationPreventsCommit() {
        val bus=CommandBus(project()); val r=bus.execute(rename(), cancellation=CancellationCheck{true})
        assertEquals(CommandFailureCode.CANCELLED,(r as CommandResult.Rejected).code)
        assertEquals(0L,bus.snapshot().number)
    }
    @Test fun undoRedoKeepRevisionMonotonic() {
        val bus=CommandBus(project()); bus.execute(rename()); bus.undo()
        assertEquals("Initial",bus.snapshot().project.name); assertEquals(2L,bus.snapshot().number)
        bus.redo(); assertEquals("Renamed",bus.snapshot().project.name); assertEquals(3L,bus.snapshot().number)
    }
    @Test fun noOpDoesNotRecordHistory() {
        val bus=CommandBus(project())
        val noop=object:ModelMutation{override val label="No-op";override fun apply(before:Project)=before}
        bus.execute(noop); assertEquals(0L,bus.snapshot().number)
        assertEquals(CommandFailureCode.HISTORY_EMPTY,(bus.undo() as CommandResult.Rejected).code)
    }
    @Test fun listenerFailureDoesNotAffectCommit() {
        val bus=CommandBus(project()); bus.subscribe{error("listener")}
        assertTrue(bus.execute(rename()) is CommandResult.Committed); assertEquals(1L,bus.snapshot().number)
    }
    @Test fun historyLimitBoundsUndoDepth() {
        val bus = CommandBus(project(), historyLimit = 1)
        val changeA = object : ModelMutation { override val label="A"; override fun apply(before:Project)=before.copy(name="A") }
        val changeB = object : ModelMutation { override val label="B"; override fun apply(before:Project)=before.copy(name="B") }
        bus.execute(changeA); bus.execute(changeB)
        assertTrue(bus.undo() is CommandResult.Committed)
        assertEquals("A", bus.snapshot().project.name)
        assertEquals(CommandFailureCode.HISTORY_EMPTY, (bus.undo() as CommandResult.Rejected).code)
        assertTrue(bus.redo() is CommandResult.Committed)
        assertEquals("B", bus.snapshot().project.name)
    }
    @Test fun invalidInitialModelIsRejected() {
        val invalid = Project(name="bad", schemaVersion=0)
        assertThrows(IllegalArgumentException::class.java) { CommandBus(invalid) }
    }
    @Test fun staleUndoRejected() {
        val bus=CommandBus(project()); bus.execute(rename())
        val r=bus.undo(0); assertEquals(CommandFailureCode.STALE_REVISION,(r as CommandResult.Rejected).code)
        assertEquals("Renamed",bus.snapshot().project.name)
    }
    @Test fun multiEntityMutationCommitsAsOneRevision() {
        val bus = CommandBus(project())
        val mutation = object : ModelMutation {
            override val label = "Add building and level"
            override fun apply(before: Project) = before.copy(
                buildings = before.buildings + Building(name = "B1"),
                levels = before.levels + Level(name = "L1", elevationMeters = 0.0)
            )
        }
        val result = bus.execute(mutation) as CommandResult.Committed
        assertEquals(1L, result.revision.number)
        assertEquals(1, result.revision.project.buildings.size)
        assertEquals(1, result.revision.project.levels.size)
        assertEquals(1L, bus.snapshot().number)
    }

    @Test fun concurrentWritersPublishEventsInRevisionOrder() {
        val bus = CommandBus(project())
        val observed = java.util.Collections.synchronizedList(mutableListOf<Long>())
        bus.subscribe { observed += when (it) {
            is ModelEvent.RevisionCommitted -> it.revision.number
            is ModelEvent.RevisionRestored -> it.revision.number
        } }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..32).map { index ->
                pool.submit {
                    bus.execute(object : ModelMutation {
                        override val label = "Concurrent $index"
                        override fun apply(before: Project) = before.copy(name = label)
                    })
                }
            }
            tasks.forEach { it.get() }
        } finally { pool.shutdown() }
        assertEquals(32L, bus.snapshot().number)
        assertEquals((1L..32L).toList(), observed.toList())
    }

    @Test fun closingSubscriptionStopsFutureEvents() {
        val bus = CommandBus(project())
        var count = 0
        val subscription = bus.subscribe { count++ }
        bus.execute(rename())
        subscription.close()
        bus.execute(object : ModelMutation {
            override val label = "Another rename"
            override fun apply(before: Project) = before.copy(name = "Second")
        })
        assertEquals(1, count)
    }

    @Test fun pendingEventQueueRejectsBeforePublishingAndNeverDropsEvents() {
        val bus = CommandBus(project(), pendingEventLimit = 1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val secondStaged = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val observed = java.util.Collections.synchronizedList(mutableListOf<Long>())
        bus.subscribe { event ->
            val revision = when (event) {
                is ModelEvent.RevisionCommitted -> event.revision.number
                is ModelEvent.RevisionRestored -> event.revision.number
            }
            observed += revision
            if (revision == 1L) {
                entered.countDown()
                release.await()
            }
        }
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<CommandResult> { bus.execute(rename()) }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val second = executor.submit<CommandResult> {
                bus.execute(object : ModelMutation {
                    override val label = "Second"
                    override fun apply(before: Project): Project {
                        secondStaged.countDown()
                        return before.copy(name = "Second")
                    }
                })
            }
            assertTrue(secondStaged.await(2, java.util.concurrent.TimeUnit.SECONDS))
            // snapshot() synchronizes with the mutation's monitor and confirms publication.
            assertEquals(2L, bus.snapshot().number)
            // The second event is queued while revision 1's callback is still in flight.
            val rejected = bus.execute(object : ModelMutation {
                override val label = "Third"
                override fun apply(before: Project) = before.copy(name = "Third")
            }) as CommandResult.Rejected
            assertEquals(CommandFailureCode.EVENT_QUEUE_FULL, rejected.code)
            val undoRejected = bus.undo() as CommandResult.Rejected
            assertEquals(CommandFailureCode.EVENT_QUEUE_FULL, undoRejected.code)
            assertEquals(2L, bus.snapshot().number)
            assertEquals("Second", bus.snapshot().project.name)
            release.countDown()
            assertTrue(first.get(2, java.util.concurrent.TimeUnit.SECONDS) is CommandResult.Committed)
            assertTrue(second.get(2, java.util.concurrent.TimeUnit.SECONDS) is CommandResult.Committed)
            assertEquals(listOf(1L, 2L), observed.toList())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun reentrantListenerCannotInterleaveRevisionEvents() {
        val bus = CommandBus(project())
        val observed = mutableListOf<String>()
        bus.subscribe { event ->
            val revision = when (event) {
                is ModelEvent.RevisionCommitted -> event.revision.number
                is ModelEvent.RevisionRestored -> event.revision.number
            }
            observed += "first:$revision"
            if (revision == 1L) bus.execute(object : ModelMutation {
                override val label = "Nested"
                override fun apply(before: Project) = before.copy(name = "Nested")
            })
        }
        bus.subscribe { event ->
            val revision = when (event) {
                is ModelEvent.RevisionCommitted -> event.revision.number
                is ModelEvent.RevisionRestored -> event.revision.number
            }
            observed += "second:$revision"
        }
        bus.execute(rename())
        assertEquals(listOf("first:1", "second:1", "first:2", "second:2"), observed)
    }

    @Test fun memoryBudgetEvictsOldestUndoSnapshotAndKeepsCurrentRedoValid() {
        val firstLargeName = "A".repeat(2_000)
        val secondLargeName = "B".repeat(2_000)
        val budget = MemoryEstimator.estimateProjectBytes(project().copy(name = firstLargeName))
        val bus = CommandBus(project(), historyLimit = 10, historyMemoryBudgetBytes = budget)

        bus.execute(object : ModelMutation {
            override val label = "First large state"
            override fun apply(before: Project) = before.copy(name = firstLargeName)
        })
        bus.execute(object : ModelMutation {
            override val label = "Second large state"
            override fun apply(before: Project) = before.copy(name = secondLargeName)
        })

        // The budget fits one large snapshot. The older initial snapshot must be evicted.
        assertEquals(1, bus.undoDepth())
        assertEquals(0, bus.redoDepth())
        assertTrue(bus.historyMemoryUsageBytes() <= budget)

        assertTrue(bus.undo() is CommandResult.Committed)
        assertEquals(firstLargeName, bus.snapshot().project.name)
        assertEquals(0, bus.undoDepth())
        assertEquals(1, bus.redoDepth())
        assertEquals(CommandFailureCode.HISTORY_EMPTY, (bus.undo() as CommandResult.Rejected).code)

        assertTrue(bus.redo() is CommandResult.Committed)
        assertEquals(secondLargeName, bus.snapshot().project.name)
        assertTrue(bus.historyMemoryUsageBytes() <= budget)
        assertEquals(0, bus.redoDepth())
    }

    @Test fun memoryEstimatorIsDeterministicAndAccountsForEntitiesAndParameterPayloads() {
        val simple = project()
        val withEntities = simple.copy(
            buildings = listOf(Building(name = "Building")),
            levels = listOf(Level(name = "Level", elevationMeters = 0.0)),
            elements = listOf(WallElement(
                axis = WallAxis(Point2(0.0, 0.0), Point2(5.0, 0.0)),
                heightMeters = 3.0,
                thicknessMeters = 0.2,
                parameters = listOf(Parameter(ModelId("definition"), ParameterValue.Text("payload".repeat(100))))
            ))
        )
        val estimate = MemoryEstimator.estimateProjectBytes(withEntities)
        assertEquals(estimate, MemoryEstimator.estimateProjectBytes(withEntities))
        assertTrue(estimate > MemoryEstimator.estimateProjectBytes(simple))
    }


    @Test fun batchFailureOnThirdStepDiscardsEntireCandidate() {
        val bus = CommandBus(project())
        val events = java.util.Collections.synchronizedList(mutableListOf<ModelEvent>())
        bus.subscribe { events += it }
        val steps = listOf(
            object : ModelMutation { override val label = "1"; override fun apply(before: Project) = before.copy(name = "step-1") },
            object : ModelMutation { override val label = "2"; override fun apply(before: Project) = before.copy(name = "step-2") },
            object : ModelMutation { override val label = "3"; override fun apply(before: Project): Project = error("failure at step 3") },
            object : ModelMutation { override val label = "4"; override fun apply(before: Project) = before.copy(name = "step-4") },
            object : ModelMutation { override val label = "5"; override fun apply(before: Project) = before.copy(name = "step-5") }
        )
        val result = bus.execute(BatchCommand("Five-step batch", steps))
        assertEquals(CommandFailureCode.COMMAND_FAILED, (result as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number)
        assertEquals("Initial", bus.snapshot().project.name)
        assertEquals(0, bus.undoDepth())
        assertEquals(0, bus.redoDepth())
        assertTrue(events.isEmpty())
    }

    @Test fun cancellationBetweenBatchStagesDiscardsCandidate() {
        val bus = CommandBus(project())
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val first = object : ModelMutation {
            override val label = "Stage then cancel"
            override fun apply(before: Project) = before.copy(name = "private candidate")
            override fun stage(before: Project, context: TransactionContext): Project {
                context.checkpoint()
                val candidate = apply(before)
                cancelled.set(true)
                return candidate
            }
        }
        val second = object : ModelMutation {
            override val label = "Should never execute"
            override fun apply(before: Project) = before.copy(name = "published by second")
        }
        val result = bus.execute(BatchCommand("Cancelable batch", listOf(first, second)),
            cancellation = CancellationCheck { cancelled.get() })
        assertEquals(CommandFailureCode.CANCELLED, (result as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number)
        assertEquals("Initial", bus.snapshot().project.name)
        assertEquals(0, bus.undoDepth())
    }

    @Test fun concurrentSnapshotReadersObserveMonotonicCompleteRevisionsDuringUndoRedo() {
        val bus = CommandBus(project())
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val readerPool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val readers = (1..4).map {
            readerPool.submit {
                var previous = -1L
                while (running.get()) {
                    val revision = bus.snapshot()
                    if (revision.number < previous) failures += AssertionError("Revision moved backwards")
                    if (revision.project.name.isBlank()) failures += AssertionError("Observed incomplete model")
                    previous = revision.number
                }
            }
        }
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val task = writer.submit {
                repeat(50) { index ->
                    bus.execute(object : ModelMutation {
                        override val label = "Writer $index"
                        override fun apply(before: Project) = before.copy(name = "state-$index")
                    })
                    bus.undo()
                    bus.redo()
                }
            }
            task.get(10, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            running.set(false)
            readers.forEach { it.get(2, java.util.concurrent.TimeUnit.SECONDS) }
            readerPool.shutdownNow()
            writer.shutdownNow()
        }
        assertTrue("Reader observed an inconsistent snapshot: $failures", failures.isEmpty())
        assertEquals(150L, bus.snapshot().number)
    }

}
