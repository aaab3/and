package com.runtime.knowledge

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Deterministic local embedding (no external API). Swappable with a real [EmbeddingProvider] in composition root.
 */
class HashEmbeddingProvider(
    private val dimensions: Int = 128
) : EmbeddingProvider {

    init {
        require(dimensions > 0) { "dimensions must be positive" }
    }

    override suspend fun embed(texts: List<String>): AppResult<List<List<Float>>> = withContext(Dispatchers.Default) {
        if (texts.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "texts must not be empty")
            )
        }
        AppResult.Success(texts.map { textToVector(it) })
    }

    private fun textToVector(text: String): List<Float> {
        val v = FloatArray(dimensions)
        text.take(4000).forEachIndexed { i, c ->
            val h = (c.code * 31 + i) % dimensions
            v[h] += 1f
        }
        var sumSq = 0.0
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq).toFloat().coerceAtLeast(1e-6f)
        return v.map { it / norm }
    }
}
