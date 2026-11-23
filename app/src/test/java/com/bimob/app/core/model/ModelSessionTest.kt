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
        assertEquals(0L, session.snapshot().revision.number)
        assertEquals(29.0, (session.snapshot().displayedProject.elements.single() as WallElement).axis.start.xMeters, 0.0)
        val result = session.endGesture()
        assertTrue(result is CommandResult.Committed)
        assertEquals(1L, session.snapshot().revision.number)
        assertTrue(session.snapshot().canUndo)
        assertTrue(session.snapshot().hasUnsavedChanges)
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(initial.revision.project, session.snapshot().revision.project)
        assertFalse(session.snapshot().hasUnsavedChanges)
        assertTrue(session.redo() is CommandResult.Committed)
        assertEquals(29.0, (session.snapshot().revision.project.elements.single() as WallElement).axis.start.xMeters, 0.0)
    }

    @Test fun gesturePreviewFreezesCallerOwnedCollections() {
        val session=ModelSession(project())
        assertTrue(session.beginGesture())
        val mutableElements=mutableListOf(wall("preview"))
        assertTrue(session.updateGesture { it.copy(elements=mutableElements) })
        mutableElements.clear()
        assertEquals(1,session.snapshot().displayedProject.elements.size)
        assertEquals("preview",session.snapshot().displayedProject.elements.single().id.value)
        session.cancelGesture()
    }

    @Test fun gestureCancelRestoresRevisionAndDoesNotCreateHistory() {
        val session = ModelSession(project(), initiallySaved = true)
        assertTrue(session.beginGesture("Draw wall"))
        session.updateGesture { it.copy(elements = it.elements + wall("w2")) }
        assertEquals(2, session.snapshot().displayedProject.elements.size)
        assertTrue(session.cancelGesture())
        assertEquals(1, session.snapshot().revision.project.elements.size)
        assertEquals(0L, session.snapshot().revision.number)
        assertFalse(session.snapshot().canUndo)
        assertFalse(session.snapshot().hasUnsavedChanges)
    }

    @Test fun initializationProjectMigrationRunsThroughCommandBus() {
        val legacy = Project(name="Legacy",elements=listOf(wall("legacy-1")))
        val session = ModelSession(Project(name="Legacy"))
        val result = session.execute(ReplaceProjectMutation("Migrate legacy sketch",legacy))
        assertTrue(result is CommandResult.Committed)
        assertEquals(1,session.snapshot().revision.project.elements.size)
        assertEquals(1L,session.snapshot().revision.number)
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
        assertEquals(0L, session.snapshot().revision.number)
        assertEquals("Session", session.snapshot().revision.project.name)
        assertEquals(0, session.snapshot().revision.dependencyGraph.edgeCount)
        assertEquals(CommandFailureCode.ENGINE_LIMIT_EXCEEDED, session.snapshot().diagnostic?.code)
    }

    @Test fun sessionSerializesUndoAgainstGestureByCancellingPreviewFirst() {
        val session = ModelSession(project(), initiallySaved = true)
        session.execute(ReplaceProjectMutation("Rename", project().copy(name = "Renamed")))
        assertTrue(session.beginGesture())
        session.updateGesture { it.copy(name = "Preview") }
        session.undo()
        assertFalse(session.snapshot().gestureActive)
        assertEquals("Session", session.snapshot().revision.project.name)
    }
}
