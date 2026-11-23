package com.bimob.app.core.persistence

import com.bimob.app.core.model.*
import java.security.MessageDigest

data class LegacySketchWall(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val thickness: Double = 0.2)
data class LegacySketch(val name: String, val walls: List<LegacySketchWall>)

/** Deterministic migration of legacy sketch JSON to validated immutable domain objects. */
object LegacySketchMigrator {
    fun decode(json: String): LegacySketch {
        val root = Json.parse(json.encodeToByteArray()).obj()
        val name = root.opt("name").let { if (it == JNull) "Untitled" else it.text() }
        val walls = root.opt("walls").let { if (it == JNull) emptyList() else it.array().a.map { item ->
            val wall = item.obj()
            LegacySketchWall(wall.req("x1").double(), wall.req("y1").double(), wall.req("x2").double(), wall.req("y2").double(), wall.opt("thickness").let { if (it == JNull) .2 else it.double() })
        } }
        return LegacySketch(name, walls)
    }

    fun migrate(sketch: LegacySketch): Project {
        require(sketch.name.isNotBlank()) { "Legacy project name is empty." }
        val projectId = ModelId("legacy-project-" + stableHash(sketch.name).take(20))
        val siteId = ModelId("legacy-site-" + stableHash(sketch.name).take(20))
        val elements = sketch.walls.mapIndexed { index, wall ->
            require(wall.x1.isFinite() && wall.y1.isFinite() && wall.x2.isFinite() && wall.y2.isFinite() && wall.thickness.isFinite())
            require(wall.x1 != wall.x2 || wall.y1 != wall.y2) { "Legacy wall #${index + 1} has zero length." }
            require(wall.thickness > 0.0) { "Legacy wall #${index + 1} has invalid thickness." }
            WallElement(
                id = ModelId("legacy-wall-$index"),
                axis = WallAxis(Point2(wall.x1, wall.y1), Point2(wall.x2, wall.y2)),
                heightMeters = 2.7,
                thicknessMeters = wall.thickness
            )
        }
        val project = Project(id=projectId,name=sketch.name,site=Site(id=siteId,name="Legacy site"),elements=elements)
        val errors = ModelValidator.validate(project).filter { it.severity == ValidationSeverity.ERROR }
        require(errors.isEmpty()) { "Legacy migration validation failed: ${errors.joinToString { it.code }}" }
        return project
    }

    fun migrateJson(json: String): Project = migrate(decode(json))

    private fun stableHash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
