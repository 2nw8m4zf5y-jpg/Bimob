package com.bimob.app.core.model

import org.junit.Assert.*
import org.junit.Test

class WallCommandsTest {
    @Test fun wallAddMoveDeleteAndParameterEditAreHistoryCommands() {
        val session = ModelSession.newProject("Walls")
        assertTrue(session.addWall("wall", 0.0, 0.0, 3.0, 0.0) is CommandResult.Committed)
        assertTrue(session.setWallThickness("wall", .3) is CommandResult.Committed)
        assertEquals(.3, session.snapshot().walls.single().thicknessMeters, .0)
        assertTrue(session.moveWall("wall", 1.0, 2.0) is CommandResult.Committed)
        val moved = session.snapshot().walls.single()
        assertEquals(1.0, moved.startX, .0); assertEquals(2.0, moved.startY, .0)
        assertTrue(session.deleteWall("wall") is CommandResult.Committed)
        assertTrue(session.snapshot().walls.isEmpty())
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(1, session.snapshot().walls.size)
        assertTrue(session.undo() is CommandResult.Committed)
        assertEquals(0.0, session.snapshot().walls.single().startX, .0)
    }

    @Test fun duplicateWallIdsAndInvalidThicknessRejectAtomically() {
        val session = ModelSession.newProject("Walls")
        assertTrue(session.addWall("wall", 0.0, 0.0, 3.0, 0.0) is CommandResult.Committed)
        val duplicate = session.addWall("wall", 0.0, 1.0, 3.0, 1.0) as CommandResult.Rejected
        assertEquals(CommandFailureCode.COMMAND_FAILED, duplicate.code)
        assertEquals(1L, session.snapshot().revisionNumber)
        val invalid = session.setWallThickness("wall", Double.NaN) as CommandResult.Rejected
        assertEquals(CommandFailureCode.COMMAND_FAILED, invalid.code)
        assertEquals(.2, session.snapshot().walls.single().thicknessMeters, .0)
        assertEquals(1L, session.snapshot().revisionNumber)
    }
}
