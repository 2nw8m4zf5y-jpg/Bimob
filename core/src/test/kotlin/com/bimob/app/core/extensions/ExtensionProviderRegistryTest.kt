package com.bimob.app.core.extensions

import com.bimob.app.core.model.ElementCategory
import com.bimob.app.core.model.ModelId
import com.bimob.app.core.model.ParameterDataType
import com.bimob.app.core.model.ParameterDefinition
import com.bimob.app.core.model.ParameterScope
import com.bimob.app.core.model.ParameterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionProviderRegistryTest {
    private val api = KernelApiVersion(1, 2, 0)
    private val contract = KernelApiVersion(1, 0, 0)
    private val range = KernelApiVersionRange(KernelApiVersion(1, 0, 0), KernelApiVersion(2, 0, 0))

    private fun manifests(vararg points: Pair<ExtensionPoint, String>): ExtensionRegistry {
        val contributions = points.map { (point, id) -> ExtensionContributionDescriptor(point, id, contract) }
        val manifest = ExtensionManifest("com.example.architecture", KernelApiVersion(1, 0, 0), range, contributions = contributions)
        return (ExtensionRegistry.empty(api).register(manifest) as ExtensionRegistryResult.Accepted).registry
    }

    private fun provider(point: ExtensionPoint, contributionId: String, extensionId: String = "com.example.architecture", version: KernelApiVersion = contract) =
        StubProvider(ProviderIdentity(extensionId, contributionId, point, version))

    @Test
    fun bindsOnlyDeclaredContributionWithExactContractVersion() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "wall"))
        val result = registry.register(provider(ExtensionPoint.GEOMETRY, "wall")) as ProviderRegistryResult.Accepted
        assertNotNull(result.registry.provider("com.example.architecture", "wall"))
        assertTrue(registry.providers.isEmpty())
    }

    @Test
    fun rejectsMissingExtensionAndUndeclaredContribution() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "wall"))
        assertRejected(registry.register(provider(ExtensionPoint.GEOMETRY, "wall", "com.example.unknown")), ProviderRegistryFailureCode.EXTENSION_NOT_REGISTERED)
        assertRejected(registry.register(provider(ExtensionPoint.GEOMETRY, "roof")), ProviderRegistryFailureCode.CONTRIBUTION_NOT_DECLARED)
    }

    @Test
    fun rejectsWrongPointAndContractVersion() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "wall"))
        assertRejected(registry.register(provider(ExtensionPoint.VALIDATOR, "wall")), ProviderRegistryFailureCode.WRONG_EXTENSION_POINT)
        assertRejected(registry.register(provider(ExtensionPoint.GEOMETRY, "wall", version = KernelApiVersion(1, 1, 0))), ProviderRegistryFailureCode.CONTRACT_VERSION_MISMATCH)
    }

    @Test
    fun duplicateProviderAndBatchAreRejectedAtomically() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "wall", ExtensionPoint.GEOMETRY to "roof"))
        val first = provider(ExtensionPoint.GEOMETRY, "wall")
        val accepted = registry.register(first) as ProviderRegistryResult.Accepted
        assertRejected(accepted.registry.register(first), ProviderRegistryFailureCode.DUPLICATE_PROVIDER)
        val batch = accepted.registry.registerAll(listOf(provider(ExtensionPoint.GEOMETRY, "roof"), first))
        assertRejected(batch, ProviderRegistryFailureCode.DUPLICATE_PROVIDER)
        assertEquals(listOf("wall"), accepted.registry.providers.map { it.identity.contributionId })
    }

    @Test
    fun providerSnapshotIsSortedAndStable() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "zeta", ExtensionPoint.GEOMETRY to "alpha"))
        val result = registry.registerAll(listOf(provider(ExtensionPoint.GEOMETRY, "zeta"), provider(ExtensionPoint.GEOMETRY, "alpha"))) as ProviderRegistryResult.Accepted
        assertEquals(listOf("alpha", "zeta"), result.registry.providers.map { it.identity.contributionId })
        assertNull(registry.provider("com.example.architecture", "alpha"))
        assertFalse(registry.providers.isNotEmpty())
    }

    @Test
    fun rejectsMalformedProviderIdentifiers() {
        val registry = ExtensionProviderRegistry.empty<StubProvider>(ExtensionPoint.GEOMETRY, manifests(ExtensionPoint.GEOMETRY to "wall"))
        assertRejected(registry.register(provider(ExtensionPoint.GEOMETRY, "Bad ID")), ProviderRegistryFailureCode.INVALID_PROVIDER_ID)
    }

    private fun assertRejected(result: ProviderRegistryResult<*>, code: ProviderRegistryFailureCode) {
        assertTrue("Expected rejection: $result", result is ProviderRegistryResult.Rejected)
        assertEquals(code, (result as ProviderRegistryResult.Rejected).failure.code)
    }

    private class StubProvider(override val identity: ProviderIdentity) : ExtensionProvider
}

class ExtensionProviderContractTest {
    @Test
    fun familyDefaultsAreTypedAndCollectionsAreFrozen() {
        val parameterId = ModelId("width")
        val mutableParameters = mutableListOf(ParameterDefinition(parameterId, "Width", ParameterDataType.LENGTH, ParameterScope.INSTANCE))
        val mutableDefaults = mutableMapOf(parameterId to ParameterValue.Real(0.9))
        val family = FamilyDefinition("door.single", "Single door", ElementCategory.DOOR, mutableParameters, mutableDefaults)
        mutableParameters.clear()
        mutableDefaults.clear()
        assertEquals(1, family.parameterDefinitions.size)
        assertEquals(1, family.defaultValues.size)
        assertTrue(runCatching { family.parameterDefinitions.add(ParameterDefinition(ModelId("extra"), "Extra", ParameterDataType.STRING, ParameterScope.INSTANCE)) }.isFailure)
        assertTrue(runCatching { FamilyDefinition("door.bad", "Bad", ElementCategory.DOOR, listOf(ParameterDefinition(parameterId, "Width", ParameterDataType.LENGTH, ParameterScope.INSTANCE)), mapOf(parameterId to ParameterValue.Text("wide"))) }.isFailure)
    }

    @Test
    fun geometryMeshValidatesFiniteVerticesAndTriangleIndices() {
        val mesh = GeometryMesh(
            listOf(Point3Meters(0.0, 0.0, 0.0), Point3Meters(1.0, 0.0, 0.0), Point3Meters(0.0, 1.0, 0.0)),
            listOf(0, 1, 2)
        )
        assertEquals(3, mesh.vertices.size)
        assertTrue(runCatching { GeometryMesh(mesh.vertices, listOf(0, 1)) }.isFailure)
        assertTrue(runCatching { GeometryMesh(mesh.vertices, listOf(0, 1, 3)) }.isFailure)
        assertTrue(runCatching { Point3Meters(Double.NaN, 0.0, 0.0) }.isFailure)
    }

    @Test
    fun scheduleOutputRejectsUnknownColumnsAndNonFiniteNumbers() {
        val definition = ScheduleDefinition("wall.schedule", "Walls", listOf(ScheduleColumn("length", "Length", "m")))
        assertTrue(runCatching { ScheduleCellValue.Number(Double.POSITIVE_INFINITY) }.isFailure)
        assertTrue(runCatching {
            ScheduleSnapshot(definition, listOf(ScheduleRow(ModelId("wall-1"), mapOf("unknown" to ScheduleCellValue.Text("x")))))
        }.isFailure)
        val snapshot = ScheduleSnapshot(definition, listOf(ScheduleRow(ModelId("wall-1"), mapOf("length" to ScheduleCellValue.Number(4.25, "m")))))
        assertEquals(1, snapshot.rows.size)
    }

    @Test
    fun progressRejectsInvalidCountersAndCancellationIsInjectable() {
        assertTrue(runCatching { ExtensionProgress("read", 5, 4) }.isFailure)
        val cancelled = ExtensionCancellation { true }
        assertTrue(cancelled.isCancelled())
        assertFalse(ExtensionCancellation.NEVER.isCancelled())
    }
}
