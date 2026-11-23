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

}
