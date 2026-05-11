package com.runtime.knowledge

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

/**
 * Splits document text into fixed-size windows (character-based P0 chunker).
 */
class FixedSizeChunker(
    private val maxChunkChars: Int = 512
) : Chunker {

    init {
        require(maxChunkChars > 0) { "maxChunkChars must be positive" }
    }

    override suspend fun chunk(document: ImportedDocument): AppResult<List<DocumentChunk>> = withContext(Dispatchers.Default) {
        if (document.text.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "Document text is empty")
            )
        }
        val baseMeta = document.metadata + mapOf(
            "sourcePath" to document.sourcePath,
            "title" to document.title
        )
        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var idx = 0
        while (start < document.text.length) {
            val end = min(start + maxChunkChars, document.text.length)
            val slice = document.text.substring(start, end)
            chunks.add(
                DocumentChunk(
                    id = UUID.randomUUID().toString(),
                    documentId = document.id,
                    text = slice,
                    index = idx,
                    metadata = baseMeta
                )
            )
            idx++
            start = end
        }
        AppResult.Success(chunks)
    }
}
