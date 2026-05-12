package com.runtime.android

import com.runtime.android.db.ProviderEntity
import com.runtime.binding.*
import com.runtime.core.AppResult

/**
 * v2 simplified binding resolver: always resolves to the configured default provider.
 * No multi-scope priority, no 4-table lookup. Just returns the one active provider.
 */
class SimpleBindingResolver(
    private val providerEntity: ProviderEntity,
    private val secretValue: String
) : BindingResolver {

    override suspend fun resolve(request: BindingResolveRequest): AppResult<ResolvedModelBinding> {
        val provider = ProviderDefinition(
            id = providerEntity.id,
            providerType = ProviderType.OPENAI_COMPATIBLE,
            name = providerEntity.name,
            baseUrl = providerEntity.baseUrl,
            defaultHeaders = emptyMap(),
            capabilities = emptySet()
        )
        val model = ModelProfile(
            id = "model-${providerEntity.id}",
            providerId = providerEntity.id,
            modelId = providerEntity.modelId,
            displayName = providerEntity.modelId,
            contextWindow = null,
            capabilities = emptySet()
        )
        val cred = CredentialRef(
            id = "cred-${providerEntity.id}",
            providerId = providerEntity.id,
            displayName = providerEntity.name,
            secretAlias = providerEntity.secretAlias
        )
        val binding = ModelBinding(
            id = "binding-${providerEntity.id}",
            targetType = BindingTargetType.GLOBAL,
            targetId = null,
            providerId = providerEntity.id,
            modelProfileId = model.id,
            credentialRefId = cred.id,
            priority = 0
        )
        return AppResult.Success(
            ResolvedModelBinding(
                binding = binding,
                provider = provider,
                model = model,
                credentialRef = cred,
                secretValue = secretValue
            )
        )
    }
}
