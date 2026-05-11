package com.runtime.model

import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes

/**
 * Skeleton only: [generate] always returns [ErrorCodes.NOT_IMPLEMENTED].
 */
class AnthropicLikeModelProvider : ModelProvider {

    override val providerType: ProviderType = ProviderType.ANTHROPIC_LIKE

    override suspend fun generate(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<ModelGenerateResult> =
        AppResult.Failure(
            AppError(
                code = ErrorCodes.NOT_IMPLEMENTED,
                message = "Anthropic-like model provider is not implemented"
            )
        )
}
