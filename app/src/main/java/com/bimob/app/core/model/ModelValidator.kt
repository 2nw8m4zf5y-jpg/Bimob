package com.bimob.app.core.model

import kotlin.math.abs

enum class ValidationSeverity { ERROR, WARNING }

data class ValidationIssue(
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
    val elementId: ModelId? = null
)

/**
 * Checks model invariants without mutating the project.
 * Validation is deterministic and safe to run before commands, geometry jobs, or persistence.
 */
object ModelValidator {
    private const val THICKNESS_TOLERANCE_METERS = 0.001

    fun validate(project: Project): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun error(code: String, message: String, id: ModelId? = null) {
            issues += ValidationIssue(code, ValidationSeverity.ERROR, message, id)
        }
        fun warning(code: String, message: String, id: ModelId? = null) {
            issues += ValidationIssue(code, ValidationSeverity.WARNING, message, id)
        }

        if (project.schemaVersion < 1) error("project.schema_version", "Schema version must be at least 1.")
        val allIds = buildList {
            add(project.id); add(project.site.id)
            addAll(project.buildings.map { it.id })
            addAll(project.levels.map { it.id })
            addAll(project.elements.map { it.id })
            addAll(project.materials.map { it.id })
            addAll(project.types.map { it.id })
        }
        if (allIds.any { it.value.isBlank() }) error("id.blank", "Model IDs must not be blank.")
        allIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            error("id.duplicate", "Duplicate model ID: ${it.value}")
        }

        project.levels.forEach { level ->
            if (!level.elevationMeters.isFinite()) error("level.elevation.invalid", "Level elevation must be finite.", level.id)
            level.heightMeters?.let {
                if (!it.isFinite() || it <= 0.0) error("level.height.invalid", "Level height must be finite and positive.", level.id)
            }
        }
        project.materials.forEach { material ->
            material.densityKgPerCubicMeter?.let { if (!it.isFinite() || it <= 0.0) error("material.density.invalid", "Material density must be positive and finite.", material.id) }
            material.thermalConductivityWPerMeterKelvin?.let { if (!it.isFinite() || it < 0.0) error("material.conductivity.invalid", "Thermal conductivity must be finite and non-negative.", material.id) }
            material.costPerUnit?.let { if (!it.isFinite() || it < 0.0) error("material.cost.invalid", "Material cost must be finite and non-negative.", material.id) }
        }

        val levelIds = project.levels.map { it.id }.toSet()
        val typeById = project.types.associateBy { it.id }
        val materialIds = project.materials.map { it.id }.toSet()
        val elementIds = project.elements.map { it.id }.toSet()

        project.elements.forEach { element ->
            if (element.levelId != null && element.levelId !in levelIds) {
                error("element.level.missing", "Element references a level that does not exist.", element.id)
            }
            element.typeId?.let { typeId ->
                val type = typeById[typeId]
                if (type == null) error("element.type.missing", "Element references a type that does not exist.", element.id)
                else if (type.category != element.category) warning("element.type.category_mismatch", "Element type category does not match element category.", element.id)
            }
            val duplicateParameters = element.parameters.groupingBy { it.definitionId }.eachCount().filterValues { it > 1 }
            if (duplicateParameters.isNotEmpty()) warning("parameter.duplicate", "Element has duplicate parameter values.", element.id)
            element.parameters.forEach { parameter ->
                if (parameter.value is ParameterValue.Reference && parameter.value.id !in elementIds) {
                    warning("parameter.reference.missing", "Parameter references an element that does not exist.", element.id)
                }
            }

            if (element is WallElement) {
                val axis = element.axis
                val coordinates = listOf(axis.start.xMeters, axis.start.yMeters, axis.end.xMeters, axis.end.yMeters)
                if (coordinates.any { !it.isFinite() }) error("wall.axis.invalid", "Wall axis coordinates must be finite.", wallId(element))
                if (!element.lengthMeters.isFinite() || element.lengthMeters <= 0.0) error("wall.axis.zero_length", "Wall axis must have a positive finite length.", element.id)
                if (!element.heightMeters.isFinite() || element.heightMeters <= 0.0) error("wall.height.invalid", "Wall height must be positive and finite.", element.id)
                if (!element.thicknessMeters.isFinite() || element.thicknessMeters <= 0.0) error("wall.thickness.invalid", "Wall thickness must be positive and finite.", element.id)
                if (!element.baseElevationMeters.isFinite()) error("wall.base.invalid", "Wall base elevation must be finite.", element.id)
                element.topElevationMeters?.let {
                    if (!it.isFinite() || it <= element.baseElevationMeters) error("wall.top.invalid", "Wall top must be finite and above its base.", element.id)
                    else if (abs((it - element.baseElevationMeters) - element.heightMeters) > THICKNESS_TOLERANCE_METERS) {
                        warning("wall.height.top_mismatch", "Explicit top elevation differs from base elevation plus wall height.", element.id)
                    }
                }
                element.layers.forEach { layer ->
                    if (!layer.thicknessMeters.isFinite() || layer.thicknessMeters <= 0.0) error("wall.layer.thickness.invalid", "Wall layer thickness must be positive and finite.", element.id)
                    if (layer.materialId != null && layer.materialId !in materialIds) error("wall.layer.material.missing", "Wall layer references a material that does not exist.", element.id)
                }
                if (element.layers.isNotEmpty() && element.layers.all { it.thicknessMeters.isFinite() && it.thicknessMeters > 0.0 }) {
                    val layerTotal = element.layers.sumOf { it.thicknessMeters }
                    if (abs(layerTotal - element.thicknessMeters) > THICKNESS_TOLERANCE_METERS) {
                        warning("wall.layers.total_mismatch", "Wall layer total differs from overall wall thickness.", element.id)
                    }
                }
            }
        }
        return issues
    }

    private fun wallId(wall: WallElement) = wall.id
}
