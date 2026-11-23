package com.bimob.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelValidatorTest {
    @Test fun validSimpleWallHasNoIssues() {
        val level = Level(name = "Ground", elevationMeters = 0.0)
        val wall = WallElement(
            levelId = level.id,
            axis = WallAxis(Point2(0.0, 0.0), Point2(4.0, 0.0)),
            heightMeters = 2.7,
            thicknessMeters = 0.2
        )
        val issues = ModelValidator.validate(Project(name = "Test", levels = listOf(level), elements = listOf(wall)))
        assertTrue(issues.isEmpty())
    }

    @Test fun reportsInvalidWallDimensionsAndMissingLevel() {
        val wall = WallElement(
            levelId = ModelId("missing-level"),
            axis = WallAxis(Point2(0.0, 0.0), Point2(0.0, 0.0)),
            heightMeters = 0.0,
            thicknessMeters = -0.1
        )
        val codes = ModelValidator.validate(Project(name = "Test", elements = listOf(wall))).map { it.code }
        assertTrue(codes.contains("element.level.missing"))
        assertTrue(codes.contains("wall.axis.zero_length"))
        assertTrue(codes.contains("wall.height.invalid"))
        assertTrue(codes.contains("wall.thickness.invalid"))
    }

    @Test fun layerTotalMismatchIsWarning() {
        val wall = WallElement(
            axis = WallAxis(Point2(0.0, 0.0), Point2(3.0, 0.0)),
            heightMeters = 2.5,
            thicknessMeters = 0.2,
            layers = listOf(WallLayer(null, 0.1, WallLayerFunction.STRUCTURE))
        )
        val issues = ModelValidator.validate(Project(name = "Test", elements = listOf(wall)))
        val mismatch = issues.single { it.code == "wall.layers.total_mismatch" }
        assertEquals(ValidationSeverity.WARNING, mismatch.severity)
    }
}
