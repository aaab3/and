package com.runtime.tool.builtin

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReadTextFileTool : ToolHandler {

    override val manifest = ToolManifest(
        name = "read_text_file",
        description = "Read the content of a text file from the local filesystem. Returns the file content (truncated to 8000 chars).",
        inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string","description":"Absolute path to the file to read"}},"required":["path"]}""",
        outputSchemaJson = """{"type":"object","properties":{"content":{"type":"string"},"size":{"type":"integer"},"truncated":{"type":"boolean"}}}""",
        requiresUserConfirmation = true,
        timeoutMs = 10_000
    )

    override suspend fun execute(request: ToolExecutionRequest): AppResult<ToolExecutionResult> = withContext(Dispatchers.IO) {
        val args = parseArgs(request.argumentsJson)
        val path = args["path"]
        if (path.isNullOrBlank()) {
            return@withContext AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "path is required"))
        }

        val file = File(path)
        if (!file.isFile) {
            return@withContext AppResult.Failure(AppError(ErrorCodes.NOT_FOUND, "File not found: $path"))
        }

        try {
            val content = file.readText(Charsets.UTF_8)
            val truncated = content.length > 8000
            val output = if (truncated) content.take(8000) else content
            val escaped = output
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t")

            AppResult.Success(
                ToolExecutionResult(
                    outputJson = """{"content":"$escaped","size":${content.length},"truncated":$truncated}"""
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.EXECUTION_ERROR, "Failed to read file: ${e.message?.take(200)}")
            )
        }
    }
}
