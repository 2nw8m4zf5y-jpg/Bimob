package com.bimob.app.core.model

/**
 * Optional project-level validator contributed by a discipline or extension.
 *
 * Implementations must be deterministic, side-effect free, and must return issue codes
 * prefixed by their registered extension ID (for example, "structural.beam.span").
 * Throwing is treated as a validation error and prevents a transaction from committing.
 */
fun interface ModelValidationExtension {
    fun validate(project: Project): List<ValidationIssue>
}

/**
 * Immutable validator registry. Extension execution order and returned issue order are
 * deterministic, so the same model and registry produce the same diagnostic sequence.
 *
 * This is deliberately a core contract only: it does not load plugins, access Android,
 * or allow extensions to mutate a project.
 */
class ModelValidatorRegistry private constructor(
    private val entries: Map<String, ModelValidationExtension>
) {
    val extensionIds: List<String>
        get() = entries.keys.sorted()

    fun withValidator(id: String, validator: ModelValidationExtension): ModelValidatorRegistry {
        require(VALID_ID.matches(id)) { "Validator ID must be a stable namespaced identifier." }
        require(id !in entries) { "Validator already registered: $id" }
        return ModelValidatorRegistry(entries + (id to validator))
    }

    fun withoutValidator(id: String): ModelValidatorRegistry =
        if (id !in entries) this else ModelValidatorRegistry(entries - id)

    /** Runs built-in invariants first, then extensions in stable ID order. */
    fun validate(project: Project): List<ValidationIssue> {
        val result = ModelValidator.validate(project).toMutableList()
        for ((id, validator) in entries.toSortedMap()) {
            val extensionIssues = try {
                validator.validate(project)
            } catch (exception: Exception) {
                result += ValidationIssue(
                    code = "extension.validator.failed",
                    severity = ValidationSeverity.ERROR,
                    message = "Validator '$id' failed (${exception::class.simpleName ?: "Exception"})."
                )
                continue
            }

            val validIssues = extensionIssues.filter { it.code.startsWith("$id.") }
            result += validIssues.sortedWith(ISSUE_ORDER)
            if (validIssues.size != extensionIssues.size) {
                result += ValidationIssue(
                    code = "extension.validator.invalid_code",
                    severity = ValidationSeverity.ERROR,
                    message = "Validator '$id' returned issue codes outside its namespace."
                )
            }
        }
        return result.toList()
    }

    companion object {
        private val VALID_ID = Regex("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)*")
        private val ISSUE_ORDER = compareBy<ValidationIssue>(
            { it.severity.ordinal },
            { it.code },
            { it.elementId?.value.orEmpty() },
            { it.message }
        )

        fun empty() = ModelValidatorRegistry(emptyMap())
    }
}
