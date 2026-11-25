package com.bimob.app.core.extensions

import java.util.ArrayDeque
import java.util.Collections

/**
 * Kernel API version used for extension compatibility checks.
 *
 * This deliberately supports only stable major/minor/patch versions. Pre-release and
 * build metadata are excluded from the kernel contract until a concrete compatibility
 * requirement exists.
 */
data class KernelApiVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<KernelApiVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) {
            "Version components must be non-negative."
        }
    }

    override fun compareTo(other: KernelApiVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }
}

/** Half-open version interval: minimum is included, maximum is excluded. */
data class KernelApiVersionRange(
    val minimumInclusive: KernelApiVersion,
    val maximumExclusive: KernelApiVersion
) {
    init {
        require(minimumInclusive < maximumExclusive) {
            "Version range must have a minimum below its exclusive maximum."
        }
    }

    fun contains(version: KernelApiVersion): Boolean =
        version >= minimumInclusive && version < maximumExclusive
}

/** Stable extension points. Descriptors are metadata, not executable providers. */
enum class ExtensionPoint {
    TOOL,
    ELEMENT_TYPE,
    FAMILY,
    GEOMETRY,
    VALIDATOR,
    SCHEDULE,
    IMPORT,
    EXPORT,
    MIGRATION
}

data class ExtensionContributionDescriptor(
    val point: ExtensionPoint,
    val id: String,
    val contractVersion: KernelApiVersion = KernelApiVersion(1, 0, 0)
)

data class ExtensionDependency(
    val extensionId: String,
    val compatibleVersions: KernelApiVersionRange
)

data class ExtensionManifest(
    val id: String,
    val version: KernelApiVersion,
    val kernelApi: KernelApiVersionRange,
    val dependencies: List<ExtensionDependency> = emptyList(),
    val contributions: List<ExtensionContributionDescriptor> = emptyList()
)

data class RegisteredContribution(
    val extensionId: String,
    val descriptor: ExtensionContributionDescriptor
)

enum class ExtensionRegistryFailureCode {
    INVALID_ID,
    DUPLICATE_EXTENSION,
    DUPLICATE_CONTRIBUTION,
    DUPLICATE_DEPENDENCY,
    KERNEL_API_INCOMPATIBLE,
    MISSING_DEPENDENCY,
    DEPENDENCY_VERSION_INCOMPATIBLE,
    DEPENDENCY_CYCLE,
    EXTENSION_NOT_FOUND,
    EXTENSION_HAS_DEPENDENTS
}

data class ExtensionRegistryFailure(
    val code: ExtensionRegistryFailureCode,
    val message: String,
    val extensionIds: List<String> = emptyList()
)

sealed interface ExtensionRegistryResult {
    data class Accepted(val registry: ExtensionRegistry) : ExtensionRegistryResult
    data class Rejected(val failure: ExtensionRegistryFailure) : ExtensionRegistryResult
}

/**
 * Immutable admission registry for feature manifests.
 *
 * Registration is all-or-nothing. It validates kernel API compatibility, dependency
 * versions, dependency cycles and globally unique contribution IDs before publishing a
 * new registry snapshot. It does not load code or execute providers; runtime provider
 * contracts are deliberately kept separate from metadata admission.
 */
class ExtensionRegistry private constructor(
    val kernelApiVersion: KernelApiVersion,
    private val manifestsById: Map<String, ExtensionManifest>
) {
    val manifests: List<ExtensionManifest>
        get() = manifestsById.values.sortedBy { it.id }

    fun manifest(extensionId: String): ExtensionManifest? = manifestsById[extensionId]

    fun contributions(point: ExtensionPoint): List<RegisteredContribution> =
        manifestsById.values
            .flatMap { manifest ->
                manifest.contributions
                    .filter { it.point == point }
                    .map { RegisteredContribution(manifest.id, it) }
            }
            .sortedWith(compareBy<RegisteredContribution> { it.descriptor.id }.thenBy { it.extensionId })

    fun register(manifest: ExtensionManifest): ExtensionRegistryResult =
        registerAll(listOf(manifest))

    /** Adds a batch atomically; no partial registry is returned on validation failure. */
    fun registerAll(additions: List<ExtensionManifest>): ExtensionRegistryResult {
        if (additions.isEmpty()) return ExtensionRegistryResult.Accepted(this)

        val incomingIds = mutableSetOf<String>()
        val frozenAdditions = additions.map(::freezeManifest)

        for (manifest in frozenAdditions) {
            if (!isStableId(manifest.id)) {
                return rejected(
                    ExtensionRegistryFailureCode.INVALID_ID,
                    "Invalid extension ID: ${manifest.id}.",
                    listOf(manifest.id)
                )
            }
            if (manifest.id in manifestsById || !incomingIds.add(manifest.id)) {
                return rejected(
                    ExtensionRegistryFailureCode.DUPLICATE_EXTENSION,
                    "Extension ID is already registered or duplicated in this batch: ${manifest.id}.",
                    listOf(manifest.id)
                )
            }
            if (!manifest.kernelApi.contains(kernelApiVersion)) {
                return rejected(
                    ExtensionRegistryFailureCode.KERNEL_API_INCOMPATIBLE,
                    "Extension ${manifest.id} does not support kernel API ${kernelApiVersion.asText()}.",
                    listOf(manifest.id)
                )
            }

            val dependencyIds = manifest.dependencies.map { it.extensionId }
            if (dependencyIds.any { !isStableId(it) }) {
                return rejected(
                    ExtensionRegistryFailureCode.INVALID_ID,
                    "Extension ${manifest.id} declares an invalid dependency ID.",
                    listOf(manifest.id)
                )
            }
            if (dependencyIds.size != dependencyIds.distinct().size) {
                return rejected(
                    ExtensionRegistryFailureCode.DUPLICATE_DEPENDENCY,
                    "Extension ${manifest.id} declares the same dependency more than once.",
                    listOf(manifest.id)
                )
            }
            val contributionIds = manifest.contributions.map { it.point to it.id }
            if (manifest.contributions.any { !isStableId(it.id) }) {
                return rejected(
                    ExtensionRegistryFailureCode.INVALID_ID,
                    "Extension ${manifest.id} declares an invalid contribution ID.",
                    listOf(manifest.id)
                )
            }
            if (contributionIds.size != contributionIds.distinct().size) {
                return rejected(
                    ExtensionRegistryFailureCode.DUPLICATE_CONTRIBUTION,
                    "Extension ${manifest.id} declares a contribution more than once.",
                    listOf(manifest.id)
                )
            }
        }

        val candidate = LinkedHashMap(manifestsById)
        frozenAdditions.forEach { candidate[it.id] = it }

        val contributionOwners = mutableMapOf<Pair<ExtensionPoint, String>, String>()
        candidate.values.sortedBy { it.id }.forEach { manifest ->
            manifest.contributions.forEach { contribution ->
                val key = contribution.point to contribution.id
                val previousOwner = contributionOwners.putIfAbsent(key, manifest.id)
                if (previousOwner != null) {
                    return rejected(
                        ExtensionRegistryFailureCode.DUPLICATE_CONTRIBUTION,
                        "Contribution ${contribution.point}:${contribution.id} is declared by both ${previousOwner} and ${manifest.id}.",
                        listOf(previousOwner, manifest.id).sorted()
                    )
                }
            }
        }

        for (manifest in candidate.values.sortedBy { it.id }) {
            for (dependency in manifest.dependencies.sortedBy { it.extensionId }) {
                val target = candidate[dependency.extensionId]
                    ?: return rejected(
                        ExtensionRegistryFailureCode.MISSING_DEPENDENCY,
                        "Extension ${manifest.id} requires missing extension ${dependency.extensionId}.",
                        listOf(manifest.id, dependency.extensionId).sorted()
                    )
                if (!dependency.compatibleVersions.contains(target.version)) {
                    return rejected(
                        ExtensionRegistryFailureCode.DEPENDENCY_VERSION_INCOMPATIBLE,
                        "Extension ${manifest.id} does not accept ${dependency.extensionId} version ${target.version.asText()}.",
                        listOf(manifest.id, dependency.extensionId).sorted()
                    )
                }
            }
        }

        val cycleMembers = findCycleMembers(candidate)
        if (cycleMembers.isNotEmpty()) {
            return rejected(
                ExtensionRegistryFailureCode.DEPENDENCY_CYCLE,
                "Extension dependency cycle detected among: ${cycleMembers.joinToString()}.",
                cycleMembers
            )
        }

        val immutableCandidate = Collections.unmodifiableMap(
            LinkedHashMap(candidate.toSortedMap())
        )
        return ExtensionRegistryResult.Accepted(ExtensionRegistry(kernelApiVersion, immutableCandidate))
    }

    /** An extension cannot be removed while another registered extension depends on it. */
    fun unregister(extensionId: String): ExtensionRegistryResult {
        if (extensionId !in manifestsById) {
            return rejected(
                ExtensionRegistryFailureCode.EXTENSION_NOT_FOUND,
                "Extension is not registered: $extensionId.",
                listOf(extensionId)
            )
        }
        val dependents = manifestsById.values
            .filter { manifest -> manifest.dependencies.any { it.extensionId == extensionId } }
            .map { it.id }
            .sorted()
        if (dependents.isNotEmpty()) {
            return rejected(
                ExtensionRegistryFailureCode.EXTENSION_HAS_DEPENDENTS,
                "Cannot unregister $extensionId while these extensions depend on it: ${dependents.joinToString()}.",
                dependents + extensionId
            )
        }
        val remaining = LinkedHashMap(manifestsById)
        remaining.remove(extensionId)
        return ExtensionRegistryResult.Accepted(
            ExtensionRegistry(
                kernelApiVersion,
                Collections.unmodifiableMap(LinkedHashMap(remaining.toSortedMap()))
            )
        )
    }

    private fun rejected(
        code: ExtensionRegistryFailureCode,
        message: String,
        extensionIds: List<String>
    ) = ExtensionRegistryResult.Rejected(
        ExtensionRegistryFailure(code, message, extensionIds.distinct().sorted())
    )

    private fun findCycleMembers(manifests: Map<String, ExtensionManifest>): List<String> {
        val graph = manifests.mapValues { (_, manifest) ->
            manifest.dependencies.map { it.extensionId }.sorted()
        }
        val reverseGraph = manifests.keys.associateWith { mutableListOf<String>() }.toMutableMap()
        graph.forEach { (extensionId, dependencies) ->
            dependencies.forEach { dependencyId -> reverseGraph.getValue(dependencyId).add(extensionId) }
        }
        reverseGraph.values.forEach { it.sort() }

        // Iterative DFS avoids recursion depth failures for long extension chains.
        data class DfsFrame(val node: String, var nextNeighborIndex: Int)
        val visited = mutableSetOf<String>()
        val finishOrder = mutableListOf<String>()
        for (start in manifests.keys.sorted()) {
            if (!visited.add(start)) continue
            val stack = ArrayDeque<DfsFrame>()
            stack.addLast(DfsFrame(start, 0))
            while (stack.isNotEmpty()) {
                val frame = stack.peekLast()
                val neighbors = graph.getValue(frame.node)
                if (frame.nextNeighborIndex < neighbors.size) {
                    val neighbor = neighbors[frame.nextNeighborIndex++]
                    if (visited.add(neighbor)) stack.addLast(DfsFrame(neighbor, 0))
                } else {
                    finishOrder.add(frame.node)
                    stack.removeLast()
                }
            }
        }

        // Kosaraju's second pass identifies actual strongly connected components,
        // excluding acyclic extensions that merely depend on a cycle.
        val assigned = mutableSetOf<String>()
        val cycleMembers = mutableSetOf<String>()
        for (start in finishOrder.asReversed()) {
            if (!assigned.add(start)) continue
            val component = mutableListOf<String>()
            val stack = ArrayDeque<String>()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                component.add(node)
                reverseGraph.getValue(node).forEach { predecessor ->
                    if (assigned.add(predecessor)) stack.addLast(predecessor)
                }
            }
            if (component.size > 1 || graph.getValue(start).contains(start)) {
                cycleMembers.addAll(component)
            }
        }
        return cycleMembers.sorted()
    }
    private fun freezeManifest(manifest: ExtensionManifest): ExtensionManifest =
        manifest.copy(
            dependencies = Collections.unmodifiableList(ArrayList(manifest.dependencies)),
            contributions = Collections.unmodifiableList(ArrayList(manifest.contributions))
        )

    companion object {
        private val STABLE_ID = Regex("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")

        fun empty(kernelApiVersion: KernelApiVersion = KernelApiVersion(1, 0, 0)): ExtensionRegistry =
            ExtensionRegistry(kernelApiVersion, emptyMap())

        private fun isStableId(value: String): Boolean = STABLE_ID.matches(value)
    }
}

private fun KernelApiVersion.asText(): String = "$major.$minor.$patch"
