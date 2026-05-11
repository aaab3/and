package com.runtime.binding

import com.runtime.core.AppResult

interface ProviderDefinitionRepository {
    suspend fun getProvider(providerId: String): AppResult<ProviderDefinition?>
    suspend fun listProviders(): AppResult<List<ProviderDefinition>>
}

interface CredentialRefRepository {
    suspend fun getCredentialRef(credentialRefId: String): AppResult<CredentialRef?>
    suspend fun listCredentialRefs(providerId: String): AppResult<List<CredentialRef>>
}

interface ModelProfileRepository {
    suspend fun getModelProfile(modelProfileId: String): AppResult<ModelProfile?>
    suspend fun listModelProfiles(providerId: String): AppResult<List<ModelProfile>>
}

interface ModelBindingRepository {
    suspend fun findBindings(
        targetType: BindingTargetType,
        targetId: String?
    ): AppResult<List<ModelBinding>>
}
