package com.bimob.app.core.model

import com.bimob.app.core.dependency.*
import org.junit.Assert.*
import org.junit.Test

class ModelSessionTest {
    private fun wall(id: String, x: Double = 0.0) = WallElement(
        id = ModelId(id), axis = WallAxis(Point2(x, 0.0), Point2(x + 2.0, 0.0)),
        heightMeters = 2.7, thicknessMeters = 0.2
    )
    private fun project() = Project(name = "Session", elements = listOf(wall("w1")))

    @Test fun gesturePreviewPublishesSnapshotsButCommitsSingleUndoEntry() {
        val session = ModelSession(project(), initiallySaved = true)
        val initial = session.snapshot()
        assertTrue(session.beginGesture("Move wall"))
        repeat(30) { n ->
            assertTrue(session.updateGesture { p ->
                p.copy(elements = p.elements.map { e -> if (e.id.value == "w1") (e as WallElement).copy(axis = WallAxis(Point2(n.toDouble(),0.0),Point2(n+2.0,0.0))) else e })
            })
        }
        assertTrue(session.snapshot().gestureActive)
        assertEquals(0L, session.snapshot().revisionNumber)
        assertEquals(29.0, session.snapshot().walls.single().startX, 0.0)
        val result = session.endGesture()
        assertTrue(result is CommandResult.Committed)
        assertEquals(1L, session.snapshot().revisionNumber)
        assertTrue(session.snapshot().canUndo)
        assertTrue(session.snapshot().hasUnsavedChanges)
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(initial.walls, session.snapshot().walls)
        assertFalse(session.snapshot().hasUnsavedChanges)
        assertTrue(session.redo() is CommandResult.Committed)
        assertEquals(29.0, session.snapshot().walls.single().startX, 0.0)
    }

    @Test fun gesturePreviewFreezesCallerOwnedCollections() {
        val session=ModelSession(project())
        assertTrue(session.beginGesture())
        val mutableElements=mutableListOf(wall("preview"))
        assertTrue(session.updateGesture { it.copy(elements=mutableElements) })
        mutableElements.clear()
        assertEquals(1,session.snapshot().walls.size)
        assertEquals("preview",session.snapshot().walls.single().id)
        session.cancelGesture()
    }

    @Test fun gestureCancelRestoresRevisionAndDoesNotCreateHistory() {
        val session = ModelSession(project(), initiallySaved = true)
        assertTrue(session.beginGesture("Draw wall"))
        session.updateGesture { it.copy(elements = it.elements + wall("w2")) }
        assertEquals(2, session.snapshot().walls.size)
        assertTrue(session.cancelGesture())
        assertEquals(1, session.snapshot().walls.size)
        assertEquals(0L, session.snapshot().revisionNumber)
        assertFalse(session.snapshot().canUndo)
        assertFalse(session.snapshot().hasUnsavedChanges)
    }

    @Test fun initializationProjectMigrationRunsThroughCommandBus() {
        val legacy = Project(name="Legacy",elements=listOf(wall("legacy-1")))
        val session = ModelSession(Project(name="Legacy"))
        val result = session.execute(ReplaceProjectMutation("Migrate legacy sketch",legacy))
        assertTrue(result is CommandResult.Committed)
        assertEquals(1,session.snapshot().walls.size)
        assertEquals(1L,session.snapshot().revisionNumber)
        assertTrue(session.snapshot().canUndo)
    }

    @Test fun engineBudgetFailureIsPublishedAndRollsBack() {
        val source = EntityProperty(ModelId("src"), PropertyAspect.PARAMETERS)
        val targets = (0..2).map { EntityProperty(ModelId("dst$it"), PropertyAspect.PARAMETERS) }
        val bus = CommandBus(project(), engineLimits = EngineLimits(maxGraphNodes = 3, maxGraphEdges = 2))
        val session = ModelSession(project(), commandBus = bus)
        val mutation = object : ModelMutation {
            override val label = "Exceed edge budget"
            override fun apply(before: Project) = before.copy(name = "candidate")
            override fun stage(before: Project, context: TransactionContext): Project {
                context.admitDependencies(targets.map { DependencyEdge(source, it) })
                return before.copy(name = "candidate")
            }
        }
        val result = session.execute(mutation) as CommandResult.Rejected
        assertEquals(CommandFailureCode.ENGINE_LIMIT_EXCEEDED, result.code)
        assertEquals(0L, session.snapshot().revisionNumber)
        assertEquals("Session", session.snapshot().projectName)
        assertEquals(1, session.snapshot().walls.size)
        assertEquals(CommandFailureCode.ENGINE_LIMIT_EXCEEDED, session.snapshot().diagnostic?.code)
    }

    @Test fun sessionSerializesUndoAgainstGestureByCancellingPreviewFirst() {
        val session = ModelSession(project(), initiallySaved = true)
        session.execute(ReplaceProjectMutation("Rename", project().copy(name = "Renamed")))
        assertTrue(session.beginGesture())
        session.updateGesture { it.copy(name = "Preview") }
        session.undo()
        assertFalse(session.snapshot().gestureActive)
        assertEquals("Session", session.snapshot().projectName)
    }
    @Test fun canvasScalarGestureApiCoalescesDrawAndMoveCommands() {
        val session = ModelSession.newProject("Canvas")
        assertTrue(session.beginGesture("Draw wall"))
        repeat(30) { frame -> assertTrue(session.previewDrawWall("wall", 0.0, 0.0, frame + 1.0, 0.0)) }
        assertEquals(30.0, session.snapshot().walls.single().endX, 0.0)
        assertTrue(session.endGesture() is CommandResult.Committed)
        assertEquals(1L, session.snapshot().revisionNumber)
        assertTrue(session.undo() is CommandResult.Committed)
        assertTrue(session.snapshot().walls.isEmpty())

        session.addWall("wall", 0.0, 0.0, 3.0, 0.0)
        assertTrue(session.beginGesture("Move wall"))
        repeat(10) { frame -> assertTrue(session.previewMoveWall("wall", frame.toDouble(), 2.0)) }
        assertTrue(session.endGesture() is CommandResult.Committed)
        assertEquals(9.0, session.snapshot().walls.single().startX, 0.0)
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(0.0, session.snapshot().walls.single().startX, 0.0)
    }

    @Test fun concurrentCommandsAreSerializedAndRevisionsRemainMonotonic() {
        val session = ModelSession(project(), initiallySaved = true)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 40).map { index ->
                java.util.concurrent.Callable {
                    session.execute(ReplaceProjectMutation("rename-$index", project().copy(name = "P$index")))
                }
            }
            val results = pool.invokeAll(tasks).map { it.get() }
            assertTrue(results.all { it is CommandResult.Committed })
            assertEquals(40L, session.snapshot().revisionNumber)
            assertTrue(session.snapshot().canUndo)
        } finally { pool.shutdownNow() }
    }
}
