package com.runtime.knowledge

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KnowledgePipelineTest {

    @Test
    fun importChunkEmbedIndexRetrieve_preservesSourcePath() = runBlocking {
        val file = Files.createTempFile("kb", ".txt").toFile()
        file.writeText(
            """
            The quick brown fox jumps over the lazy dog.
            Repeat: quick brown fox for retrieval testing.
            """.trimIndent()
        )
        try {
            val importer = DefaultFileDocumentImporter()
            val chunker = FixedSizeChunker(maxChunkChars = 80)
            val embedder = HashEmbeddingProvider(dimensions = 64)
            val index = InMemoryVectorIndex()
            val retriever = DefaultKnowledgeRetriever(embedder, index)

            val doc = assertIs<AppResult.Success<ImportedDocument>>(importer.importDocument(file.absolutePath)).value
            assertTrue(doc.sourcePath.endsWith(file.name))

            val chunks = assertIs<AppResult.Success<List<DocumentChunk>>>(chunker.chunk(doc)).value
            assertTrue(chunks.isNotEmpty())

            val vectors = assertIs<AppResult.Success<List<List<Float>>>>(
                embedder.embed(chunks.map { it.text })
            ).value
            assertEquals(chunks.size, vectors.size)

            val embedded = chunks.zip(vectors) { c, v -> EmbeddedChunk(c, v) }
            assertIs<AppResult.Success<Unit>>(index.upsert(embedded))

            val hits = assertIs<AppResult.Success<List<RetrievedChunk>>>(
                retriever.retrieve("quick brown fox", topK = 3)
            ).value
            assertTrue(hits.isNotEmpty())
            assertEquals(doc.sourcePath, hits.first().sourcePath)
            assertTrue(hits.first().metadata.containsKey("title"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsNonTxtMd() = runBlocking {
        val f = Files.createTempFile("kb", ".pdf").toFile()
        f.writeText("x")
        try {
            val r = DefaultFileDocumentImporter().importDocument(f.absolutePath)
            assertTrue(r is AppResult.Failure)
        } finally {
            f.delete()
        }
    }
}
