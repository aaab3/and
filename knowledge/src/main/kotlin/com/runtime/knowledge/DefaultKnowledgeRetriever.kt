package com.runtime.knowledge

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes

/**
 * Composes [EmbeddingProvider] + [VectorIndex]; no chat-runtime coupling.
 */
class DefaultKnowledgeRetriever(
    private val embeddingProvider: EmbeddingProvider,
    private val vectorIndex: VectorIndex
) : KnowledgeRetriever {

    override suspend fun retrieve(query: String, topK: Int): AppResult<List<RetrievedChunk>> {
        if (query.isBlank()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "query must not be blank")
            )
        }
        val embedded = when (val e = embeddingProvider.embed(listOf(query.trim()))) {
            is AppResult.Failure -> return e
            is AppResult.Success -> e.value
        }
        val qv = embedded.firstOrNull()
            ?: return AppResult.Failure(
                AppError(ErrorCodes.PROVIDER_ERROR, message = "Embedding provider returned no vector")
            )
        return vectorIndex.search(qv, topK)
    }
}
