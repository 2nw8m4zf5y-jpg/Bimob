package com.bimob.app.core.extensions

import com.bimob.app.core.model.BimElement
import com.bimob.app.core.model.ElementCategory
import com.bimob.app.core.model.ElementType
import com.bimob.app.core.model.ModelId
import com.bimob.app.core.model.ParameterDefinition
import com.bimob.app.core.model.ParameterValue
import com.bimob.app.core.model.Project
import com.bimob.app.core.model.ValidationIssue
import java.util.Collections

/** Identity is metadata; it never grants a provider permission to mutate the model. */
data class ProviderIdentity(
    val extensionId: String,
    val contributionId: String,
    val point: ExtensionPoint,
    val contractVersion: KernelApiVersion
)

interface ExtensionProvider {
    val identity: ProviderIdentity
}

/** Portable host services. The host owns cancellation and stream lifetime. */
fun interface ExtensionCancellation {
    fun isCancelled(): Boolean
    companion object { val NEVER = ExtensionCancellation { false } }
}

data class ExtensionProgress(val stage: String, val completed: Long, val total: Long? = null) {
    init {
        require(stage.isNotBlank())
        require(completed >= 0)
        require(total == null || total >= 0)
        require(total == null || completed <= total)
    }
}

interface ExtensionExecutionContext {
    val cancellation: ExtensionCancellation
    fun reportProgress(progress: ExtensionProgress)
}

/** A family is a typed parameter schema plus optional geometry contribution reference. */
class FamilyDefinition(
    val id: String,
    val name: String,
    val category: ElementCategory,
    parameterDefinitions: List<ParameterDefinition>,
    defaultValues: Map<ModelId, ParameterValue> = emptyMap(),
    val geometryContributionId: String? = null
) {
    val parameterDefinitions: List<ParameterDefinition> =
        Collections.unmodifiableList(ArrayList(parameterDefinitions))
    val defaultValues: Map<ModelId, ParameterValue> =
        Collections.unmodifiableMap(LinkedHashMap(defaultValues))

    init {
        require(isStableContributionId(id)) { "Invalid family ID: $id" }
        require(name.isNotBlank()) { "Family name must not be blank." }
        require(this.parameterDefinitions.map { it.id }.distinct().size == this.parameterDefinitions.size) {
            "Family parameter IDs must be unique."
        }
        val definitions = this.parameterDefinitions.associateBy { it.id }
        require(this.defaultValues.keys.all { it in definitions }) {
            "Family defaults may only reference declared parameters."
        }
        this.defaultValues.forEach { (id, value) ->
            require(parameterValueMatches(definitions.getValue(id).dataType, value)) {
                "Default value type does not match parameter $id."
            }
        }
        geometryContributionId?.let {
            require(isStableContributionId(it)) { "Invalid geometry contribution ID: $it" }
        }
    }
}

private fun parameterValueMatches(
    type: com.bimob.app.core.model.ParameterDataType,
    value: ParameterValue
): Boolean = when (type) {
    com.bimob.app.core.model.ParameterDataType.BOOLEAN -> value is ParameterValue.Bool
    com.bimob.app.core.model.ParameterDataType.INTEGER -> value is ParameterValue.IntNumber
    com.bimob.app.core.model.ParameterDataType.REAL,
    com.bimob.app.core.model.ParameterDataType.LENGTH,
    com.bimob.app.core.model.ParameterDataType.AREA,
    com.bimob.app.core.model.ParameterDataType.VOLUME,
    com.bimob.app.core.model.ParameterDataType.ANGLE -> value is ParameterValue.Real && value.value.isFinite()
    com.bimob.app.core.model.ParameterDataType.STRING -> value is ParameterValue.Text
    com.bimob.app.core.model.ParameterDataType.MATERIAL_REF,
    com.bimob.app.core.model.ParameterDataType.ELEMENT_REF -> value is ParameterValue.Reference
    com.bimob.app.core.model.ParameterDataType.ENUM -> value is ParameterValue.EnumValue
    com.bimob.app.core.model.ParameterDataType.FORMULA -> false
}

/** Geometry coordinates are metres in the model coordinate system. */
data class Point3Meters(val x: Double, val y: Double, val z: Double) {
    init { require(x.isFinite() && y.isFinite() && z.isFinite()) { "Geometry coordinates must be finite." } }
}

class GeometryMesh(vertices: List<Point3Meters>, triangleIndices: List<Int>) {
    val vertices: List<Point3Meters> = Collections.unmodifiableList(ArrayList(vertices))
    val triangleIndices: List<Int> = Collections.unmodifiableList(ArrayList(triangleIndices))

    init {
        require(this.triangleIndices.size % 3 == 0) { "Triangle index count must be divisible by three." }
        require(this.triangleIndices.all { it in this.vertices.indices }) { "Triangle index is outside the vertex array." }
    }
}

class GeometryOutput(meshes: List<GeometryMesh>) {
    val meshes: List<GeometryMesh> = Collections.unmodifiableList(ArrayList(meshes))
}

data class GeometryContext(val cancellation: ExtensionCancellation = ExtensionCancellation.NEVER)

interface ElementTypeProvider : ExtensionProvider {
    fun elementTypes(): List<ElementType>
}

interface FamilyProvider : ExtensionProvider {
    fun families(): List<FamilyDefinition>
}

interface GeometryProvider : ExtensionProvider {
    fun supports(element: BimElement): Boolean
    fun generate(element: BimElement, context: GeometryContext): GeometryOutput
}

interface ValidatorProvider : ExtensionProvider {
    fun validate(project: Project): List<ValidationIssue>
}

data class ScheduleColumn(val id: String, val title: String, val unitSymbol: String? = null) {
    init {
        require(isStableContributionId(id)) { "Invalid schedule column ID: $id" }
        require(title.isNotBlank())
    }
}

class ScheduleDefinition(val id: String, val title: String, columns: List<ScheduleColumn>) {
    val columns: List<ScheduleColumn> = Collections.unmodifiableList(ArrayList(columns))
    init {
        require(isStableContributionId(id)) { "Invalid schedule ID: $id" }
        require(title.isNotBlank())
        require(this.columns.map { it.id }.distinct().size == this.columns.size) {
            "Schedule column IDs must be unique."
        }
    }
}

sealed interface ScheduleCellValue {
    data class Text(val value: String) : ScheduleCellValue
    data class Number(val value: Double, val unitSymbol: String? = null) : ScheduleCellValue {
        init { require(value.isFinite()) { "Schedule numbers must be finite." } }
    }
    data class BooleanValue(val value: Boolean) : ScheduleCellValue
    data class Reference(val modelId: ModelId) : ScheduleCellValue
}

class ScheduleRow(val elementId: ModelId, cells: Map<String, ScheduleCellValue>) {
    val cells: Map<String, ScheduleCellValue> = Collections.unmodifiableMap(LinkedHashMap(cells))
}

class ScheduleSnapshot(
    val definition: ScheduleDefinition,
    rows: List<ScheduleRow>,
    diagnostics: List<ExtensionDiagnostic> = emptyList()
) {
    val rows: List<ScheduleRow> = Collections.unmodifiableList(ArrayList(rows))
    val diagnostics: List<ExtensionDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
    init {
        val columnIds = definition.columns.map { it.id }.toSet()
        require(this.rows.all { row -> row.cells.keys.all { it in columnIds } }) {
            "Schedule row contains a cell for an undeclared column."
        }
    }
}

enum class ExtensionDiagnosticSeverity { INFO, WARNING, ERROR }

data class ExtensionDiagnostic(
    val code: String,
    val severity: ExtensionDiagnosticSeverity,
    val message: String,
    val elementId: ModelId? = null
) {
    init { require(code.isNotBlank() && message.isNotBlank()) }
}

interface ExtensionInput {
    val sourceName: String?
    val sizeBytes: Long?
    /** Returns -1 at end of stream. The host retains ownership and closes the source. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

interface ExtensionOutput {
    /** The host retains ownership and closes the destination. */
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun flush()
}

class ProjectImportResult(val project: Project, diagnostics: List<ExtensionDiagnostic> = emptyList()) {
    val diagnostics: List<ExtensionDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
}

class ProjectExportResult(val bytesWritten: Long, diagnostics: List<ExtensionDiagnostic> = emptyList()) {
    val diagnostics: List<ExtensionDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
    init { require(bytesWritten >= 0) }
}

interface ScheduleProvider : ExtensionProvider {
    fun generate(project: Project, definition: ScheduleDefinition, context: ExtensionExecutionContext): ScheduleSnapshot
}

interface ImportProvider : ExtensionProvider {
    fun importProject(source: ExtensionInput, context: ExtensionExecutionContext): ProjectImportResult
}

interface ExportProvider : ExtensionProvider {
    fun exportProject(project: Project, destination: ExtensionOutput, context: ExtensionExecutionContext): ProjectExportResult
}

private val CONTRIBUTION_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
private fun isStableContributionId(value: String): Boolean = CONTRIBUTION_ID_PATTERN.matches(value)
