package com.runtime.knowledge

import com.runtime.core.AppResult

interface DocumentImporter {
    suspend fun importDocument(path: String): AppResult<ImportedDocument>
}

interface Chunker {
    suspend fun chunk(document: ImportedDocument): AppResult<List<DocumentChunk>>
}

interface EmbeddingProvider {
    suspend fun embed(texts: List<String>): AppResult<List<List<Float>>>
}

interface VectorIndex {
    suspend fun upsert(chunks: List<EmbeddedChunk>): AppResult<Unit>
    suspend fun search(queryVector: List<Float>, topK: Int): AppResult<List<RetrievedChunk>>
}

interface KnowledgeRetriever {
    suspend fun retrieve(
        query: String,
        topK: Int = 5
    ): AppResult<List<RetrievedChunk>>
}
