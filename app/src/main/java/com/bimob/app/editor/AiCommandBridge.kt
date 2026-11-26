package com.bimob.app.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface CadCommand {
    data class Wall(val start: Vec2, val end: Vec2, val thicknessMeters: Double = 0.2, val anchor: String = "ОСЬ") : CadCommand
    data class Room(val origin: Vec2, val widthMeters: Double, val depthMeters: Double, val thicknessMeters: Double = 0.2) : CadCommand
    data object Undo : CadCommand
    data object Redo : CadCommand
}

data class CadBridgeResult(val success: Boolean, val message: String, val elapsedNanos: Long)

fun interface AiBridgeProvider {
    fun execute(input: String, editor: NativeEditorViewModel, origin: Vec2 = Vec2(0.0, 0.0), thicknessMeters: Double = 0.2, anchor: String = "ОСЬ"): CadBridgeResult
}

/** Deterministic offline parser. CAD distances without suffix are millimetres. */
class LocalCadCommandParser {
    fun parse(raw: String, origin: Vec2 = Vec2(0.0, 0.0)): CadCommand? {
        val text = raw.trim().uppercase()
        if (text.isEmpty()) return null
        if (text in setOf("UNDO", "ОТМЕНА", "CTRL+Z")) return CadCommand.Undo
        if (text in setOf("REDO", "ПОВТОР", "CTRL+Y")) return CadCommand.Redo
        val room = Regex("""^(?:КОМНАТА|ROOM)\s+(\d+(?:\.\d+)?)\s*[XХ*]\s*(\d+(?:\.\d+)?)$""").matchEntire(text)
        if (room != null) return CadCommand.Room(origin, room.groupValues[1].toDouble()/1000.0, room.groupValues[2].toDouble()/1000.0)

        val line = Regex("""^(?:L|LINE)\s+(-?\d+(?:\.\d+)?)[ ]*\s*[, ]\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)[ ]*\s*[, ]\s*(-?\d+(?:\.\d+)?)$""").matchEntire(text)
        if(line!=null) return CadCommand.Wall(Vec2(line.groupValues[1].toDouble()/1000.0,line.groupValues[2].toDouble()/1000.0),Vec2(line.groupValues[3].toDouble()/1000.0,line.groupValues[4].toDouble()/1000.0))

        val polar = Regex("""^(?:WALL\s+)?(\d+(?:\.\d+)?)\s*<\s*(-?\d+(?:\.\d+)?)$""").matchEntire(text)
        if(polar!=null) return wallFromPolar(origin,polar.groupValues[1].toDouble()/1000.0,polar.groupValues[2].toDouble())
        val wall = Regex("""^(?:WALL|СТЕНА)\s+(\d+(?:\.\d+)?)(?:\s+(-?\d+(?:\.\d+)?))?(?:\s+(ВПРАВО|ВЛЕВО|ВВЕРХ|ВНИЗ))?$""").matchEntire(text)
        if(wall!=null){
            val length=wall.groupValues[1].toDouble()/1000.0
            val angle=wall.groupValues[2].takeIf{it.isNotBlank()}?.toDouble() ?: when(wall.groupValues[3]){"ВЛЕВО"->180.0;"ВВЕРХ"->90.0;"ВНИЗ"->-90.0;else->0.0}
            return wallFromPolar(origin,length,angle)
        }
        val simplePolar=Regex("""^(\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)$""").matchEntire(text)
        if(simplePolar!=null)return wallFromPolar(origin,simplePolar.groupValues[1].toDouble()/1000.0,simplePolar.groupValues[2].toDouble())
        return null
    }
    private fun wallFromPolar(origin:Vec2,length:Double,angleDegrees:Double):CadCommand.Wall {
        val angle=angleDegrees*PI/180.0
        return CadCommand.Wall(origin,Vec2(origin.x+length*cos(angle),origin.y+length*sin(angle)))
    }
}

class AiCommandBridge(private val parser: LocalCadCommandParser = LocalCadCommandParser()) : AiBridgeProvider {
    override fun execute(input:String,editor:NativeEditorViewModel,origin:Vec2,thicknessMeters:Double,anchor:String):CadBridgeResult {
        val started=System.nanoTime()
        val command=parser.parse(input,origin) ?: return CadBridgeResult(false,"Команда не распознана",System.nanoTime()-started)
        val ok=when(command){
            CadCommand.Undo->editor.undo()
            CadCommand.Redo->editor.redo()
            is CadCommand.Wall->editor.createWall(command.start.x,command.start.y,command.end.x,command.end.y,thicknessMeters,anchor)
            is CadCommand.Room->editor.createRoom(command.origin.x,command.origin.y,command.widthMeters,command.depthMeters,thicknessMeters,anchor)
        }
        return CadBridgeResult(ok,if(ok)"Выполнено: ${command.javaClass.simpleName}" else editor.state.value.error ?: "Ядро отклонило команду",System.nanoTime()-started)
    }
}
