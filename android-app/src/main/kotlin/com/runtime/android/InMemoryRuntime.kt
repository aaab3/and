package com.runtime.android

import com.runtime.binding.BindingTargetType
import com.runtime.binding.CredentialRef
import com.runtime.binding.CredentialRefRepository
import com.runtime.binding.ModelBinding
import com.runtime.binding.ModelBindingRepository
import com.runtime.binding.ModelProfile
import com.runtime.binding.ModelProfileRepository
import com.runtime.binding.ProviderDefinition
import com.runtime.binding.ProviderDefinitionRepository
import com.runtime.binding.ProviderType
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.secret.SecretProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySecretProvider : SecretProvider {
    private val mutex = Mutex()
    private val values = mutableMapOf<String, String>()

    override suspend fun putSecret(alias: String, value: String): AppResult<Unit> = mutex.withLock {
        values[alias] = value
        AppResult.Success(Unit)
    }

    override suspend fun getSecret(alias: String): AppResult<String> = mutex.withLock {
        val v = values[alias]
            ?: return@withLock AppResult.Failure(
                AppError(
                    ErrorCodes.NOT_FOUND,
                    message = "Secret not found",
                    metadata = mapOf("alias" to alias)
                )
            )
        AppResult.Success(v)
    }

    override suspend fun deleteSecret(alias: String): AppResult<Unit> = mutex.withLock {
        if (values.remove(alias) == null) {
            AppResult.Failure(
                AppError(
                    ErrorCodes.NOT_FOUND,
                    message = "Secret not found for delete",
                    metadata = mapOf("alias" to alias)
                )
            )
        } else {
            AppResult.Success(Unit)
        }
    }
}

class InMemoryProviderDefinitionRepository : ProviderDefinitionRepository {
    val providers = mutableMapOf<String, ProviderDefinition>()
    private val mutex = Mutex()

    override suspend fun getProvider(providerId: String): AppResult<ProviderDefinition?> = mutex.withLock {
        AppResult.Success(providers[providerId])
    }

    override suspend fun listProviders(): AppResult<List<ProviderDefinition>> = mutex.withLock {
        AppResult.Success(providers.values.sortedBy { it.id })
    }
}

class InMemoryCredentialRefRepository : CredentialRefRepository {
    val creds = mutableMapOf<String, CredentialRef>()
    private val mutex = Mutex()

    override suspend fun getCredentialRef(credentialRefId: String): AppResult<CredentialRef?> = mutex.withLock {
        AppResult.Success(creds[credentialRefId])
    }

    override suspend fun listCredentialRefs(providerId: String): AppResult<List<CredentialRef>> = mutex.withLock {
        AppResult.Success(creds.values.filter { it.providerId == providerId }.sortedBy { it.id })
    }
}

class InMemoryModelProfileRepository : ModelProfileRepository {
    val profiles = mutableMapOf<String, ModelProfile>()
    private val mutex = Mutex()

    override suspend fun getModelProfile(modelProfileId: String): AppResult<ModelProfile?> = mutex.withLock {
        AppResult.Success(profiles[modelProfileId])
    }

    override suspend fun listModelProfiles(providerId: String): AppResult<List<ModelProfile>> = mutex.withLock {
        AppResult.Success(profiles.values.filter { it.providerId == providerId }.sortedBy { it.id })
    }
}

class InMemoryModelBindingRepository : ModelBindingRepository {
    val bindings = mutableListOf<ModelBinding>()
    private val mutex = Mutex()

    override suspend fun findBindings(
        targetType: BindingTargetType,
        targetId: String?
    ): AppResult<List<ModelBinding>> = mutex.withLock {
        val matches = bindings.filter { b ->
            b.targetType == targetType && when {
                targetId == null -> b.targetId == null
                else -> b.targetId == targetId
            }
        }.sortedByDescending { it.priority }
        AppResult.Success(matches)
    }
}

suspend fun seedBindingWorkspace(
    secrets: SecretProvider,
    providers: InMemoryProviderDefinitionRepository,
    creds: InMemoryCredentialRefRepository,
    profiles: InMemoryModelProfileRepository,
    bindings: InMemoryModelBindingRepository,
    apiKey: String,
    baseUrl: String
) {
    val trimmedBase = baseUrl.trim().trimEnd('/')
    when (secrets.putSecret("alias-key", apiKey)) {
        is AppResult.Failure -> Unit
        is AppResult.Success -> Unit
    }
    providers.providers["p1"] = ProviderDefinition(
        id = "p1",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        name = "OpenAI-compatible",
        baseUrl = trimmedBase,
        defaultHeaders = emptyMap(),
        capabilities = emptySet()
    )
    creds.creds["c1"] = CredentialRef(
        id = "c1",
        providerId = "p1",
        displayName = "Default",
        secretAlias = "alias-key"
    )
    profiles.profiles["m1"] = ModelProfile(
        id = "m1",
        providerId = "p1",
        modelId = "gpt-4o-mini",
        displayName = "Mini",
        contextWindow = 8192,
        capabilities = emptySet()
    )
    bindings.bindings.removeAll { it.id == "b1" }
    bindings.bindings.add(
        ModelBinding(
            id = "b1",
            targetType = BindingTargetType.WORKSPACE,
            targetId = "ws-1",
            providerId = "p1",
            modelProfileId = "m1",
            credentialRefId = "c1",
            priority = 10
        )
    )
}
