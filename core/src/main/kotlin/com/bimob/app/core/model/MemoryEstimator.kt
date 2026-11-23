package com.bimob.app.core.model

/**
 * Deterministic logical retained-size estimate for history budgeting.
 *
 * This is deliberately not a JVM heap profiler: fixed structural weights and UTF-16
 * payload lengths make the estimate stable across devices and runs. It is used only
 * for deterministic history eviction, never as a claim about exact heap consumption.
 */
object MemoryEstimator {
    private const val PROJECT_BYTES = 160L
    private const val SITE_BYTES = 48L
    private const val BUILDING_BYTES = 64L
    private const val LEVEL_BYTES = 80L
    private const val ELEMENT_BYTES = 112L
    private const val WALL_BYTES = 176L
    private const val WALL_LAYER_BYTES = 48L
    private const val MATERIAL_BYTES = 96L
    private const val TYPE_BYTES = 80L
    private const val PARAMETER_BYTES = 40L
    private const val STRING_BYTES = 40L
    private const val ID_BYTES = 48L

    fun estimateProjectBytes(project: Project): Long {
        var total = PROJECT_BYTES
        total = add(total, estimateId(project.id))
        total = add(total, estimateString(project.name))
        total = add(total, SITE_BYTES)
        total = add(total, estimateId(project.site.id))
        total = add(total, estimateString(project.site.name))

        project.buildings.forEach { building ->
            total = add(total, BUILDING_BYTES)
            total = add(total, estimateId(building.id))
            total = add(total, estimateString(building.name))
        }
        project.levels.forEach { level ->
            total = add(total, LEVEL_BYTES)
            total = add(total, estimateId(level.id))
            total = add(total, estimateString(level.name))
            total = add(total, 16L) // elevation and optional height
        }
        project.elements.forEach { element ->
            total = add(total, if (element is WallElement) WALL_BYTES else ELEMENT_BYTES)
            total = add(total, estimateId(element.id))
            total = add(total, estimateId(element.typeId))
            total = add(total, estimateId(element.levelId))
            total = add(total, estimateParameters(element.parameters))
            if (element is WallElement) {
                total = add(total, 48L) // axis, height, thickness and elevation values
                total = add(total, element.layers.size.toLong() * WALL_LAYER_BYTES)
                element.layers.forEach { layer -> total = add(total, estimateId(layer.materialId)) }
            }
        }
        project.materials.forEach { material ->
            total = add(total, MATERIAL_BYTES)
            total = add(total, estimateId(material.id))
            total = add(total, estimateString(material.name))
            total = add(total, estimateString(material.category))
            total = add(total, 32L) // optional physical/cost numeric values
            total = add(total, estimateString(material.costUnit))
        }
        project.types.forEach { type ->
            total = add(total, TYPE_BYTES)
            total = add(total, estimateId(type.id))
            total = add(total, estimateString(type.name))
            total = add(total, estimateParameters(type.parameters))
        }
        return total
    }

    private fun estimateParameters(parameters: List<Parameter>): Long {
        var total = 0L
        parameters.forEach { parameter ->
            total = add(total, PARAMETER_BYTES)
            total = add(total, estimateId(parameter.definitionId))
            total = add(total, when (val value = parameter.value) {
                is ParameterValue.Bool -> 8L
                is ParameterValue.IntNumber -> 8L
                is ParameterValue.Real -> 8L
                is ParameterValue.Text -> estimateString(value.value)
                is ParameterValue.Reference -> estimateId(value.id)
                is ParameterValue.EnumValue -> estimateString(value.value)
            })
        }
        return total
    }

    private fun estimateId(id: ModelId?): Long = if (id == null) 0L else add(ID_BYTES, estimateString(id.value))
    private fun estimateString(value: String?): Long =
        if (value == null) 0L else add(STRING_BYTES, value.length.toLong() * 2L)

    /** Saturating arithmetic prevents estimator overflow from producing a small budget cost. */
    private fun add(a: Long, b: Long): Long =
        if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b
}
