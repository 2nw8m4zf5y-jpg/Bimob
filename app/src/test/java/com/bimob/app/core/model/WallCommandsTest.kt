package com.bimob.app.core.model

import org.junit.Assert.*
import org.junit.Test

class WallCommandsTest {
    private fun project() = Project(name="Walls",elements=listOf(WallElement(id=ModelId("wall"),axis=WallAxis(Point2(0.0,0.0),Point2(3.0,0.0)),heightMeters=2.7,thicknessMeters=.2)))

    @Test fun wallAddMoveDeleteAndParameterEditAreHistoryCommands() {
        val session=ModelSession(project(),initiallySaved=true)
        assertTrue(session.execute(SetWallThicknessCommand(ModelId("wall"),.3)) is CommandResult.Committed)
        assertEquals(.3,(session.snapshot().revision.project.elements.single() as WallElement).thicknessMeters,.0)
        assertTrue(session.execute(MoveWallCommand(ModelId("wall"),1.0,2.0)) is CommandResult.Committed)
        val moved=session.snapshot().revision.project.elements.single() as WallElement
        assertEquals(1.0,moved.axis.start.xMeters,.0);assertEquals(2.0,moved.axis.start.yMeters,.0)
        assertTrue(session.execute(DeleteWallCommand(ModelId("wall"))) is CommandResult.Committed)
        assertTrue(session.snapshot().revision.project.elements.isEmpty())
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(1,session.snapshot().revision.project.elements.size)
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(0.0,(session.snapshot().revision.project.elements.single() as WallElement).axis.start.xMeters,.0)
    }

    @Test fun duplicateWallIdsAndInvalidThicknessRejectAtomically() {
        val session=ModelSession(project())
        val wall=project().elements.single() as WallElement
        val duplicate=session.execute(AddWallCommand(wall)) as CommandResult.Rejected
        assertEquals(CommandFailureCode.COMMAND_FAILED,duplicate.code)
        assertEquals(0L,session.snapshot().revision.number)
        val invalid=session.execute(SetWallThicknessCommand(wall.id,Double.NaN)) as CommandResult.Rejected
        assertEquals(CommandFailureCode.COMMAND_FAILED,invalid.code)
        assertEquals(.2,(session.snapshot().revision.project.elements.single() as WallElement).thicknessMeters,.0)
    }
}
