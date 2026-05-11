package com.runtime.knowledge

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Local file import for `.txt` and `.md` only (UTF-8).
 */
class DefaultFileDocumentImporter : DocumentImporter {

    override suspend fun importDocument(path: String): AppResult<ImportedDocument> = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.NOT_FOUND, message = "File not found", metadata = mapOf("path" to path))
            )
        }
        val lower = file.name.lowercase()
        if (!lower.endsWith(".txt") && !lower.endsWith(".md")) {
            return@withContext AppResult.Failure(
                AppError(
                    ErrorCodes.INVALID_INPUT,
                    message = "Only .txt and .md files are supported",
                    metadata = mapOf("path" to path)
                )
            )
        }
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to read file", cause = e.message)
            )
        }
        AppResult.Success(
            ImportedDocument(
                id = UUID.randomUUID().toString(),
                sourcePath = file.absolutePath,
                title = file.nameWithoutExtension,
                text = text,
                metadata = mapOf(
                    "mimeType" to if (lower.endsWith(".md")) "text/markdown" else "text/plain",
                    "fileName" to file.name
                )
            )
        )
    }
}
