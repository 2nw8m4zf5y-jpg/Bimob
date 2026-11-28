package com.bimob.app.core.extensions

import java.util.Collections

data class ProviderKey(val point: ExtensionPoint, val extensionId: String, val contributionId: String)

/** Stable admission-time identity paired with a caller-owned provider instance. */
data class RegisteredProviderBinding<T : ExtensionProvider>(
    val identity: ProviderIdentity,
    val provider: T
)

enum class ProviderRegistryFailureCode {
    INVALID_PROVIDER_ID,
    WRONG_EXTENSION_POINT,
    EXTENSION_NOT_REGISTERED,
    CONTRIBUTION_NOT_DECLARED,
    CONTRACT_VERSION_MISMATCH,
    DUPLICATE_PROVIDER
}

data class ProviderRegistryFailure(
    val code: ProviderRegistryFailureCode,
    val message: String,
    val key: ProviderKey? = null
)

sealed interface ProviderRegistryResult<out T : ExtensionProvider> {
    data class Accepted<T : ExtensionProvider>(val registry: ExtensionProviderRegistry<T>) : ProviderRegistryResult<T>
    data class Rejected(val failure: ProviderRegistryFailure) : ProviderRegistryResult<Nothing>
}

/**
 * Immutable runtime-provider binding for one extension point.
 *
 * Admission requires a previously accepted manifest contribution with the same point,
 * IDs and contract version. This registry does not load code, discover classes or execute
 * providers. The provider instance remains owned by its caller.
 */
class ExtensionProviderRegistry<T : ExtensionProvider> private constructor(
    val point: ExtensionPoint,
    private val manifestRegistry: ExtensionRegistry,
    private val providersByKey: Map<ProviderKey, RegisteredProviderBinding<T>>
) {
    /** Bindings expose the identity captured at admission, not a mutable provider getter. */
    val bindings: List<RegisteredProviderBinding<T>>
        get() = Collections.unmodifiableList(
            providersByKey.entries
                .sortedWith(compareBy<Map.Entry<ProviderKey, RegisteredProviderBinding<T>>> { it.key.extensionId }
                    .thenBy { it.key.contributionId })
                .map { it.value }
        )

    /** Backward-compatible provider view, ordered by immutable registration keys. */
    val providers: List<T>
        get() = bindings.map { it.provider }

    fun provider(extensionId: String, contributionId: String): T? =
        providersByKey[ProviderKey(point, extensionId, contributionId)]?.provider

    fun register(provider: T): ProviderRegistryResult<T> = registerAll(listOf(provider))

    /** Batch admission is atomic. The original snapshot is never mutated. */
    fun registerAll(additions: List<T>): ProviderRegistryResult<T> {
        if (additions.isEmpty()) return ProviderRegistryResult.Accepted(this)
        val candidate = LinkedHashMap(providersByKey)
        for (provider in additions) {
            // Capture identity exactly once; providers remain caller-owned and may expose mutable getters.
            val identity = provider.identity
            val key = ProviderKey(identity.point, identity.extensionId, identity.contributionId)
            if (!isStableProviderId(identity.extensionId) || !isStableProviderId(identity.contributionId)) {
                return rejected(ProviderRegistryFailureCode.INVALID_PROVIDER_ID, "Invalid provider identity.", key)
            }
            if (identity.point != point) {
                return rejected(ProviderRegistryFailureCode.WRONG_EXTENSION_POINT, "Provider point ${identity.point} does not match registry point $point.", key)
            }
            if (key in candidate) {
                return rejected(ProviderRegistryFailureCode.DUPLICATE_PROVIDER, "Provider is already registered: $key.", key)
            }
            val manifest = manifestRegistry.manifest(identity.extensionId)
                ?: return rejected(ProviderRegistryFailureCode.EXTENSION_NOT_REGISTERED, "Extension is not registered: ${identity.extensionId}.", key)
            val declared = manifest.contributions.firstOrNull {
                it.point == point && it.id == identity.contributionId
            } ?: return rejected(ProviderRegistryFailureCode.CONTRIBUTION_NOT_DECLARED, "Manifest does not declare provider contribution $key.", key)
            if (declared.contractVersion != identity.contractVersion) {
                return rejected(ProviderRegistryFailureCode.CONTRACT_VERSION_MISMATCH, "Provider contract ${identity.contractVersion} does not match manifest contract ${declared.contractVersion}.", key)
            }
            candidate[key] = RegisteredProviderBinding(identity, provider)
        }
        val immutable = Collections.unmodifiableMap(LinkedHashMap(candidate.toSortedMap(
            compareBy<ProviderKey> { it.point.name }.thenBy { it.extensionId }.thenBy { it.contributionId }
        )))
        return ProviderRegistryResult.Accepted(ExtensionProviderRegistry(point, manifestRegistry, immutable))
    }

    private fun rejected(code: ProviderRegistryFailureCode, message: String, key: ProviderKey) =
        ProviderRegistryResult.Rejected(ProviderRegistryFailure(code, message, key))

    companion object {
        fun <T : ExtensionProvider> empty(point: ExtensionPoint, manifests: ExtensionRegistry) =
            ExtensionProviderRegistry<T>(point, manifests, emptyMap())

        private val STABLE_PROVIDER_ID = Regex("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
        private fun isStableProviderId(value: String): Boolean = STABLE_PROVIDER_ID.matches(value)
    }
}
