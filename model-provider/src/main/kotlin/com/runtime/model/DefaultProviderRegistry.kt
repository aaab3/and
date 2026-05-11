package com.runtime.model

import com.runtime.binding.ProviderType
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes

/**
 * Explicit registry lookup by [ProviderType]. No fallback between providers.
 */
class DefaultProviderRegistry(
    providers: Iterable<ModelProvider>
) : ProviderRegistry {

    private val byType: Map<ProviderType, ModelProvider> =
        providers.associateBy { it.providerType }

    init {
        val duplicates = providers.groupingBy { it.providerType }.eachCount().filter { it.value > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate ModelProvider for types: ${duplicates.joinToString()}"
        }
    }

    override fun getProvider(providerType: ProviderType): AppResult<ModelProvider> {
        val provider = byType[providerType]
            ?: return AppResult.Failure(
                AppError(
                    code = ErrorCodes.NOT_FOUND,
                    message = "No provider registered for type ${providerType.name}",
                    metadata = mapOf("providerType" to providerType.name)
                )
            )
        return AppResult.Success(provider)
    }

    override fun listProviders(): List<ModelProvider> = byType.values.toList()
}
