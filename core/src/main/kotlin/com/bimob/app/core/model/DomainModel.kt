package com.bimob.app.core.model

import java.util.UUID

/** Internal model units are metres, square metres, cubic metres and radians. */
@JvmInline
value class ModelId(val value: String) {
    companion object { fun newId() = ModelId(UUID.randomUUID().toString()) }
}

data class Project(
    val id: ModelId = ModelId.newId(),
    val name: String,
    val schemaVersion: Int = 1,
    val site: Site = Site(),
    val buildings: List<Building> = emptyList(),
    val levels: List<Level> = emptyList(),
    /** Legacy UI/domain projection retained for source compatibility. */
    val elements: List<BimElement> = emptyList(),
    val materials: List<Material> = emptyList(),
    val types: List<ElementType> = emptyList(),
    /** Native-Core typed model collections. Legacy WallElements are mapped on construction. */
    val walls: List<Wall> = elements.filterIsInstance<WallElement>().map {
        Wall(it.id, it.axis, it.heightMeters, it.thicknessMeters, it.levelId ?: ModelId(""))
    },
    val openings: List<Opening> = emptyList(),
    val catalogInstances: List<CatalogInstance> = emptyList()
)
data class Site(val id: ModelId = ModelId.newId(), val name: String = "Site")
data class Building(val id: ModelId = ModelId.newId(), val name: String = "Building")
data class Level(val id: ModelId = ModelId.newId(), val name: String, val elevationMeters: Double, val heightMeters: Double? = null)

enum class ElementCategory { WALL, OPENING, DOOR, WINDOW, FLOOR, ROOF, STAIR, RAILING, ROOM, COLUMN, BEAM, FOUNDATION, PIPE, DUCT, CABLE_TRAY, ELECTRICAL_DEVICE, FURNITURE, ANNOTATION, GENERIC }

data class ElementType(val id: ModelId = ModelId.newId(), val name: String, val category: ElementCategory, val parameters: List<Parameter> = emptyList())

sealed interface BimElement {
    val id: ModelId
    val category: ElementCategory
    val typeId: ModelId?
    val levelId: ModelId?
    val parameters: List<Parameter>
}

data class WallElement(
    override val id: ModelId = ModelId.newId(),
    override val typeId: ModelId? = null,
    override val levelId: ModelId? = null,
    val axis: WallAxis,
    val heightMeters: Double,
    val thicknessMeters: Double,
    val baseElevationMeters: Double = 0.0,
    val topElevationMeters: Double? = null,
    val locationLine: WallLocationLine = WallLocationLine.CENTER,
    val layers: List<WallLayer> = emptyList(),
    override val parameters: List<Parameter> = emptyList()
) : BimElement {
    override val category get() = ElementCategory.WALL
    val lengthMeters get() = axis.lengthMeters
    val volumeCubicMeters get() = lengthMeters * heightMeters * thicknessMeters
}

data class WallAxis(val start: Point2, val end: Point2) {
    val lengthMeters: Double get() {
        val dx = end.xMeters - start.xMeters
        val dy = end.yMeters - start.yMeters
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
data class Point2(val xMeters: Double, val yMeters: Double)
enum class WallLocationLine { CENTER, CORE_CENTER, FINISH_FACE_INTERIOR, FINISH_FACE_EXTERIOR }
data class WallLayer(val materialId: ModelId?, val thicknessMeters: Double, val function: WallLayerFunction, val isCore: Boolean = false)
enum class WallLayerFunction { STRUCTURE, SUBSTRATE, INSULATION, FINISH, MEMBRANE, AIR_GAP, OTHER }

data class Material(
    val id: ModelId = ModelId.newId(),
    val name: String,
    val category: String = "General",
    val densityKgPerCubicMeter: Double? = null,
    val thermalConductivityWPerMeterKelvin: Double? = null,
    val costPerUnit: Double? = null,
    val costUnit: String? = null
)

enum class ParameterScope { INSTANCE, TYPE, PROJECT }
enum class ParameterDataType { BOOLEAN, INTEGER, REAL, STRING, LENGTH, AREA, VOLUME, ANGLE, MATERIAL_REF, ELEMENT_REF, ENUM, FORMULA }
data class ParameterDefinition(val id: ModelId = ModelId.newId(), val name: String, val dataType: ParameterDataType, val scope: ParameterScope, val unitSymbol: String? = null, val isReadOnly: Boolean = false, val formula: String? = null)
data class Parameter(val definitionId: ModelId, val value: ParameterValue)
sealed interface ParameterValue {
    data class Bool(val value: Boolean) : ParameterValue
    data class IntNumber(val value: Long) : ParameterValue
    data class Real(val value: Double) : ParameterValue
    data class Text(val value: String) : ParameterValue
    data class Reference(val id: ModelId) : ParameterValue
    data class EnumValue(val value: String) : ParameterValue
}

/** Native Core wall contract; lengths are metres. */
data class Wall(
    val id: ModelId,
    val axis: WallAxis,
    val height: Double,
    val thickness: Double,
    val levelId: ModelId
)

enum class OpeningKind { DOOR, WINDOW, GENERIC }

data class Opening(
    val id: ModelId,
    val hostWallId: ModelId,
    val kind: OpeningKind = OpeningKind.GENERIC,
    val offsetAlongWall: Double,
    val width: Double,
    val height: Double,
    val sillHeight: Double
)

enum class ScalingMode { FREE_STRETCH, UNIFORM, PRESETS }
data class DimensionBounds(val min: Double, val max: Double)

data class CatalogInstance(
    val id: ModelId,
    val assetId: String,
    val position: Point2,
    val rotationRadians: Double,
    val levelId: ModelId,
    val hostWallId: ModelId,
    val targetWidth: Double,
    val targetDepth: Double,
    val targetHeight: Double,
    val scalingMode: ScalingMode = ScalingMode.FREE_STRETCH,
    val bounds: CatalogInstanceBounds,
    val category: String,
    val scheduleTag: String
)

data class CatalogInstanceBounds(
    val width: DimensionBounds,
    val depth: DimensionBounds,
    val height: DimensionBounds
)
