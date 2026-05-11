package com.runtime.knowledge

data class ImportedDocument(
    val id: String,
    val sourcePath: String,
    val title: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)

data class DocumentChunk(
    val id: String,
    val documentId: String,
    val text: String,
    val index: Int,
    val metadata: Map<String, String> = emptyMap()
)

data class EmbeddedChunk(
    val chunk: DocumentChunk,
    val vector: List<Float>
)

data class RetrievedChunk(
    val chunkId: String,
    val documentId: String,
    val text: String,
    val score: Double,
    val sourcePath: String,
    val metadata: Map<String, String> = emptyMap()
)
