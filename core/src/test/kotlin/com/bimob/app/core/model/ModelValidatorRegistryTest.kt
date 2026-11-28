package com.bimob.app.core.model

import org.junit.Assert.*
import org.junit.Test

class ModelValidatorRegistryTest {
    @Test fun validatorsRunInStableIdAndIssueOrder() {
        val registry = ModelValidatorRegistry.empty()
            .withValidator("zeta", ModelValidationExtension {
                listOf(
                    ValidationIssue("zeta.warning", ValidationSeverity.WARNING, "warning"),
                    ValidationIssue("zeta.error", ValidationSeverity.ERROR, "error")
                )
            })
            .withValidator("alpha", ModelValidationExtension {
                listOf(ValidationIssue("alpha.second", ValidationSeverity.WARNING, "second"))
            })

        val extensionIssues = registry.validate(Project(name = "Valid"))
            .filter { it.code.startsWith("alpha.") || it.code.startsWith("zeta.") }

        assertEquals(listOf("alpha.second", "zeta.error", "zeta.warning"), extensionIssues.map { it.code })
        assertEquals(listOf("alpha", "zeta"), registry.extensionIds)
    }

    @Test fun duplicateAndMalformedIdsAreRejectedAndRegistryRemainsImmutable() {
        val original = ModelValidatorRegistry.empty()
        val registered = original.withValidator("architecture", ModelValidationExtension { emptyList() })
        assertTrue(original.extensionIds.isEmpty())
        assertEquals(listOf("architecture"), registered.extensionIds)

        try {
            registered.withValidator("architecture", ModelValidationExtension { emptyList() })
            fail("Expected duplicate registration rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already registered"))
        }

        try {
            original.withValidator("Bad ID", ModelValidationExtension { emptyList() })
            fail("Expected malformed ID rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("stable namespaced"))
        }

        assertEquals(listOf("architecture"), registered.withoutValidator("missing").extensionIds)
        assertTrue(registered.withoutValidator("architecture").extensionIds.isEmpty())
    }

    @Test fun extensionExceptionsAndUnnamespacedCodesFailClosed() {
        val registry = ModelValidatorRegistry.empty()
            .withValidator("broken", ModelValidationExtension { throw IllegalStateException("private details") })
            .withValidator("discipline", ModelValidationExtension {
                listOf(ValidationIssue("unscoped.code", ValidationSeverity.WARNING, "unscoped"))
            })

        val issues = registry.validate(Project(name = "Valid"))
        assertTrue(issues.any { it.code == "extension.validator.failed" && it.severity == ValidationSeverity.ERROR })
        assertTrue(issues.any { it.code == "extension.validator.invalid_code" && it.severity == ValidationSeverity.ERROR })
        assertFalse(issues.any { it.message.contains("private details") })
        assertFalse(issues.any { it.code == "unscoped.code" })
    }

    @Test fun extensionValidationRejectsCandidateWithoutPublishingRevision() {
        val registry = ModelValidatorRegistry.empty().withValidator(
            "project.naming",
            ModelValidationExtension { project ->
                if (project.name == "blocked") listOf(
                    ValidationIssue("project.naming.blocked", ValidationSeverity.ERROR, "Name is reserved.")
                ) else emptyList()
            }
        )
        val bus = CommandBus(Project(name = "Initial"), validatorRegistry = registry)
        val result = bus.execute(object : ModelMutation {
            override val label = "Rename"
            override fun apply(before: Project) = before.copy(name = "blocked")
        })

        assertTrue(result is CommandResult.Rejected)
        assertEquals(CommandFailureCode.VALIDATION_FAILED, (result as CommandResult.Rejected).code)
        assertEquals(0L, bus.snapshot().number)
        assertEquals("Initial", bus.snapshot().project.name)
        assertTrue(result.issues.any { it.code == "project.naming.blocked" })
    }

    @Test fun registeredValidatorDoesNotChangeBuiltInInvariantChecks() {
        val invalid = Project(name = "Invalid", levels = listOf(
            Level(id = ModelId("level"), name = "L1", elevationMeters = Double.NaN)
        ))
        val registry = ModelValidatorRegistry.empty().withValidator("discipline", ModelValidationExtension { emptyList() })
        assertTrue(registry.validate(invalid).any { it.code == "level.elevation.invalid" })
    }
}
