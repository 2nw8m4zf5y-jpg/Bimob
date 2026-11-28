package com.bimob.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCadCommandParserTest {
    private val parser = LocalCadCommandParser()

    @Test fun parsesPolarCadInput() {
        val wall = parser.parse("4000<0", Vec2(1.0, 2.0)) as CadCommand.Wall
        assertEquals(Vec2(1.0, 2.0), wall.start)
        assertEquals(5.0, wall.end.x, 1e-9)
        assertEquals(2.0, wall.end.y, 1e-9)
    }

    @Test fun parsesLineCoordinatesInMillimetres() {
        val wall = parser.parse("L 0,0 4000,0") as CadCommand.Wall
        assertEquals(Vec2(0.0,0.0), wall.start)
        assertEquals(Vec2(4.0,0.0), wall.end)
    }

    @Test fun parsesRussianWallDirectionAndRoom() {
        val wall = parser.parse("стена 4000 вправо",Vec2(2.0,3.0)) as CadCommand.Wall
        assertEquals(Vec2(6.0,3.0),wall.end)
        val room = parser.parse("комната 4000x5000",Vec2(1.0,1.0)) as CadCommand.Room
        assertEquals(4.0,room.widthMeters,1e-9)
        assertEquals(5.0,room.depthMeters,1e-9)
        assertEquals(Vec2(1.0,1.0),room.origin)
    }

    @Test fun parsesHistoryCommandsAndRejectsUnknown() {
        assertTrue(parser.parse("UNDO") is CadCommand.Undo)
        assertTrue(parser.parse("повтор") is CadCommand.Redo)
        assertEquals(null,parser.parse("сделай красиво"))
    }
}
