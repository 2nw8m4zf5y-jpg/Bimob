package com.bimob.app.core.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRegistryTest {
    private val kernelApi = version(1, 2, 0)
    private val supportedKernelApi = KernelApiVersionRange(version(1, 0, 0), version(2, 0, 0))

    @Test
    fun registrationPublishesSortedManifestAndContributionSnapshot() {
        val registry = ExtensionRegistry.empty(kernelApi)
        val result = registry.register(
            manifest(
                id = "com.bimob.architecture",
                contributions = listOf(
                    contribution(ExtensionPoint.TOOL, "wall"),
                    contribution(ExtensionPoint.ELEMENT_TYPE, "basic-wall")
                )
            )
        )

        val accepted = result as ExtensionRegistryResult.Accepted
        assertEquals(listOf("com.bimob.architecture"), accepted.registry.manifests.map { it.id })
        assertEquals(
            listOf("wall"),
            accepted.registry.contributions(ExtensionPoint.TOOL).map { it.descriptor.id }
        )
        assertEquals(
            listOf("basic-wall"),
            accepted.registry.contributions(ExtensionPoint.ELEMENT_TYPE).map { it.descriptor.id }
        )
        assertTrue(registry.manifests.isEmpty())
    }

    @Test
    fun batchRegistrationIsAtomicWhenDependencyIsMissing() {
        val registry = ExtensionRegistry.empty(kernelApi)
        val result = registry.registerAll(
            listOf(
                manifest("com.bimob.architecture"),
                manifest(
                    id = "com.bimob.mep",
                    dependencies = listOf(dependency("com.bimob.catalog"))
                )
            )
        )

        assertRejected(result, ExtensionRegistryFailureCode.MISSING_DEPENDENCY)
        assertTrue(registry.manifests.isEmpty())
    }

    @Test
    fun dependencyVersionMustMatchDeclaredRange() {
        val catalog = manifest("com.bimob.catalog", version = version(2, 0, 0))
        val architecture = manifest(
            id = "com.bimob.architecture",
            dependencies = listOf(
                ExtensionDependency(
                    "com.bimob.catalog",
                    KernelApiVersionRange(version(1, 0, 0), version(2, 0, 0))
                )
            )
        )

        val result = ExtensionRegistry.empty(kernelApi).registerAll(listOf(catalog, architecture))

        assertRejected(result, ExtensionRegistryFailureCode.DEPENDENCY_VERSION_INCOMPATIBLE)
    }

    @Test
    fun dependencyCyclesAreRejected() {
        val first = manifest(
            id = "com.bimob.architecture",
            dependencies = listOf(dependency("com.bimob.mep"))
        )
        val second = manifest(
            id = "com.bimob.mep",
            dependencies = listOf(dependency("com.bimob.architecture"))
        )

        val result = ExtensionRegistry.empty(kernelApi).registerAll(listOf(first, second))

        assertRejected(result, ExtensionRegistryFailureCode.DEPENDENCY_CYCLE)
    }

    @Test
    fun cycleFailureExcludesExtensionsBlockedByCycle() {
        val first = manifest(
            id = "com.bimob.architecture",
            dependencies = listOf(dependency("com.bimob.mep"))
        )
        val second = manifest(
            id = "com.bimob.mep",
            dependencies = listOf(dependency("com.bimob.architecture"))
        )
        val blocked = manifest(
            id = "com.bimob.analysis",
            dependencies = listOf(dependency("com.bimob.architecture"))
        )

        val result = ExtensionRegistry.empty(kernelApi).registerAll(listOf(first, second, blocked))
        val failure = (result as ExtensionRegistryResult.Rejected).failure

        assertEquals(ExtensionRegistryFailureCode.DEPENDENCY_CYCLE, failure.code)
        assertEquals(listOf("com.bimob.architecture", "com.bimob.mep"), failure.extensionIds)
    }

    @Test
    fun selfDependencyIsReportedAsCycle() {
        val selfDependent = manifest(
            id = "com.bimob.catalog",
            dependencies = listOf(dependency("com.bimob.catalog"))
        )

        val result = ExtensionRegistry.empty(kernelApi).register(selfDependent)
        val failure = (result as ExtensionRegistryResult.Rejected).failure

        assertEquals(ExtensionRegistryFailureCode.DEPENDENCY_CYCLE, failure.code)
        assertEquals(listOf("com.bimob.catalog"), failure.extensionIds)
    }

    @Test
    fun contributionIdsAreUniqueWithinAnExtensionPointAcrossRegistry() {
        val first = manifest(
            id = "com.bimob.architecture",
            contributions = listOf(contribution(ExtensionPoint.TOOL, "wall"))
        )
        val second = manifest(
            id = "com.bimob.mep",
            contributions = listOf(contribution(ExtensionPoint.TOOL, "wall"))
        )

        val result = ExtensionRegistry.empty(kernelApi).registerAll(listOf(first, second))

        assertRejected(result, ExtensionRegistryFailureCode.DUPLICATE_CONTRIBUTION)
    }

    @Test
    fun incompatibleKernelApiAndDuplicateExtensionIdsAreRejected() {
        val incompatible = manifest(
            id = "com.bimob.architecture",
            kernelApi = KernelApiVersionRange(version(2, 0, 0), version(3, 0, 0))
        )
        assertRejected(
            ExtensionRegistry.empty(kernelApi).register(incompatible),
            ExtensionRegistryFailureCode.KERNEL_API_INCOMPATIBLE
        )

        val duplicate = ExtensionRegistry.empty(kernelApi).registerAll(
            listOf(
                manifest("com.bimob.architecture"),
                manifest("com.bimob.architecture", version = version(1, 1, 0))
            )
        )
        assertRejected(duplicate, ExtensionRegistryFailureCode.DUPLICATE_EXTENSION)
    }

    @Test
    fun unregisterIsBlockedWhileDependentsRemainRegistered() {
        val result = ExtensionRegistry.empty(kernelApi).registerAll(
            listOf(
                manifest("com.bimob.catalog"),
                manifest(
                    "com.bimob.architecture",
                    dependencies = listOf(dependency("com.bimob.catalog"))
                )
            )
        ) as ExtensionRegistryResult.Accepted

        val rejected = result.registry.unregister("com.bimob.catalog")
        assertRejected(rejected, ExtensionRegistryFailureCode.EXTENSION_HAS_DEPENDENTS)

        val afterDependent = result.registry.unregister("com.bimob.architecture")
            as ExtensionRegistryResult.Accepted
        val afterCatalog = afterDependent.registry.unregister("com.bimob.catalog")
            as ExtensionRegistryResult.Accepted
        assertTrue(afterCatalog.registry.manifests.isEmpty())
    }

    @Test
    fun registryDefensivelyCopiesManifestCollections() {
        val dependencies = mutableListOf<ExtensionDependency>()
        val contributions = mutableListOf(contribution(ExtensionPoint.TOOL, "wall"))
        val input = manifest(
            id = "com.bimob.architecture",
            dependencies = dependencies,
            contributions = contributions
        )

        val accepted = ExtensionRegistry.empty(kernelApi).register(input)
            as ExtensionRegistryResult.Accepted
        dependencies += dependency("com.bimob.catalog")
        contributions.clear()

        assertTrue(accepted.registry.manifest("com.bimob.architecture")!!.dependencies.isEmpty())
        assertEquals(
            listOf("wall"),
            accepted.registry.contributions(ExtensionPoint.TOOL).map { it.descriptor.id }
        )
    }

    @Test
    fun versionRangeIncludesMinimumAndExcludesMaximum() {
        val range = KernelApiVersionRange(version(1, 0, 0), version(2, 0, 0))

        assertTrue(range.contains(version(1, 0, 0)))
        assertTrue(range.contains(version(1, 9, 9)))
        assertFalse(range.contains(version(2, 0, 0)))
    }

    private fun manifest(
        id: String,
        version: KernelApiVersion = version(1, 0, 0),
        kernelApi: KernelApiVersionRange = supportedKernelApi,
        dependencies: List<ExtensionDependency> = emptyList(),
        contributions: List<ExtensionContributionDescriptor> = emptyList()
    ) = ExtensionManifest(id, version, kernelApi, dependencies, contributions)

    private fun dependency(id: String) = ExtensionDependency(
        id,
        KernelApiVersionRange(version(1, 0, 0), version(2, 0, 0))
    )

    private fun contribution(point: ExtensionPoint, id: String) =
        ExtensionContributionDescriptor(point, id)

    private fun version(major: Int, minor: Int, patch: Int) =
        KernelApiVersion(major, minor, patch)

    private fun assertRejected(
        result: ExtensionRegistryResult,
        expectedCode: ExtensionRegistryFailureCode
    ) {
        assertTrue("Expected rejection but got $result", result is ExtensionRegistryResult.Rejected)
        assertEquals(expectedCode, (result as ExtensionRegistryResult.Rejected).failure.code)
    }
}
