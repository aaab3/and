package com.runtime.binding

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.secret.SecretProvider

/**
 * Scope order (most specific first): CONVERSATION → SKILL → WORKSPACE → GLOBAL.
 * Within a scope, the first row from [ModelBindingRepository.findBindings] wins (repository sorts by priority DESC).
 */
class DefaultBindingResolver(
    private val bindingRepository: ModelBindingRepository,
    private val providerRepository: ProviderDefinitionRepository,
    private val modelRepository: ModelProfileRepository,
    private val credentialRepository: CredentialRefRepository,
    private val secretProvider: SecretProvider
) : BindingResolver {

    override suspend fun resolve(request: BindingResolveRequest): AppResult<ResolvedModelBinding> {
        if (request.workspaceId.isBlank()) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "workspaceId must not be blank"
                )
            )
        }

        val probes = buildList {
            if (request.conversationId != null) {
                add(BindingTargetType.CONVERSATION to request.conversationId)
            }
            if (request.skillId != null) {
                add(BindingTargetType.SKILL to request.skillId)
            }
            add(BindingTargetType.WORKSPACE to request.workspaceId)
            add(BindingTargetType.GLOBAL to null)
        }

        for ((targetType, targetId) in probes) {
            when (val found = bindingRepository.findBindings(targetType, targetId)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> {
                    val bindings = found.value
                    if (bindings.isEmpty()) continue
                    val binding = bindings.first()
                    return resolveChain(binding)
                }
            }
        }

        return AppResult.Failure(
            AppError(
                code = ErrorCodes.NOT_FOUND,
                message = "No model binding found for the given context",
                metadata = mapOf(
                    "workspaceId" to request.workspaceId,
                    "skillId" to (request.skillId ?: ""),
                    "conversationId" to (request.conversationId ?: "")
                )
            )
        )
    }

    private suspend fun resolveChain(binding: ModelBinding): AppResult<ResolvedModelBinding> {
        val provider = when (val r = providerRepository.getProvider(binding.providerId)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
                ?: return AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Provider definition not found",
                        metadata = mapOf("providerId" to binding.providerId)
                    )
                )
        }

        val model = when (val r = modelRepository.getModelProfile(binding.modelProfileId)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
                ?: return AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Model profile not found",
                        metadata = mapOf("modelProfileId" to binding.modelProfileId)
                    )
                )
        }

        if (model.providerId != binding.providerId) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "Model profile providerId does not match binding",
                    metadata = mapOf(
                        "bindingProviderId" to binding.providerId,
                        "modelProviderId" to model.providerId
                    )
                )
            )
        }

        val credentialRef = when (val r = credentialRepository.getCredentialRef(binding.credentialRefId)) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
                ?: return AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Credential ref not found",
                        metadata = mapOf("credentialRefId" to binding.credentialRefId)
                    )
                )
        }

        if (credentialRef.providerId != binding.providerId) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "Credential ref providerId does not match binding",
                    metadata = mapOf(
                        "bindingProviderId" to binding.providerId,
                        "credentialProviderId" to credentialRef.providerId
                    )
                )
            )
        }

        val secret = secretProvider.getSecret(credentialRef.secretAlias)
        return when (secret) {
            is AppResult.Failure -> secret
            is AppResult.Success ->
                AppResult.Success(
                    ResolvedModelBinding(
                        binding = binding,
                        provider = provider,
                        model = model,
                        credentialRef = credentialRef,
                        secretValue = secret.value
                    )
                )
        }
    }
}
