package com.bimob.app.core.model

import org.junit.Assert.*
import org.junit.Test

class CommandBusTest {
    private fun project() = Project(name = "Initial")
    private fun rename() = object : ModelMutation {
        override val label = "Rename project"
        override fun apply(before: Project) = before.copy(name = "Renamed")
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
}
