package com.runtime.knowledge

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/**
 * In-process index for P0; replace with SQLite/ANN later without changing [VectorIndex] contract.
 */
class InMemoryVectorIndex : VectorIndex {

    private val mutex = Mutex()
    private val items = mutableListOf<EmbeddedChunk>()

    override suspend fun upsert(chunks: List<EmbeddedChunk>): AppResult<Unit> = mutex.withLock {
        if (chunks.isEmpty()) return AppResult.Success(Unit)
        val dim = chunks.first().vector.size
        for (c in chunks) {
            if (c.vector.isEmpty() || c.vector.size != dim) {
                return AppResult.Failure(
                    AppError(
                        ErrorCodes.INVALID_INPUT,
                        message = "All embedding vectors must have the same non-zero dimension"
                    )
                )
            }
        }
        items.addAll(chunks)
        AppResult.Success(Unit)
    }

    override suspend fun search(queryVector: List<Float>, topK: Int): AppResult<List<RetrievedChunk>> = mutex.withLock {
        if (topK <= 0) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "topK must be positive")
            )
        }
        if (queryVector.isEmpty()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "queryVector must not be empty")
            )
        }
        if (items.isEmpty()) {
            return AppResult.Success(emptyList())
        }
        val qDim = queryVector.size
        val scored = items.mapNotNull { ec ->
            if (ec.vector.size != qDim) return@mapNotNull null
            val score = cosineSimilarity(queryVector, ec.vector)
            ec to score
        }
        if (scored.size < items.size) {
            return AppResult.Failure(
                AppError(
                    ErrorCodes.INVALID_INPUT,
                    message = "Indexed vector dimension does not match query vector"
                )
            )
        }
        val top = scored.sortedByDescending { it.second }.take(topK)
        val results = top.map { (ec, score) ->
            val meta = ec.chunk.metadata
            val sourcePath = meta["sourcePath"] ?: ""
            RetrievedChunk(
                chunkId = ec.chunk.id,
                documentId = ec.chunk.documentId,
                text = ec.chunk.text,
                score = score,
                sourcePath = sourcePath,
                metadata = meta
            )
        }
        AppResult.Success(results)
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-12) 0.0 else dot / denom
    }
}
