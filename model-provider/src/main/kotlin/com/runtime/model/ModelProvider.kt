package com.runtime.model

import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppResult
import kotlinx.coroutines.flow.Flow

interface ModelProvider {
    val providerType: ProviderType

    suspend fun generate(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<ModelGenerateResult>

    /**
     * Stream generation. Emits partial text chunks as they arrive.
     * Final chunk contains full usage / finish_reason / tool_calls.
     */
    fun generateStream(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): Flow<ModelStreamChunk>
}

data class ModelStreamChunk(
    val deltaText: String = "",
    val toolCalls: List<ModelToolCall> = emptyList(),
    val finishReason: String? = null,
    val usage: ModelUsage? = null,
    val isDone: Boolean = false,
    val error: String? = null
)
