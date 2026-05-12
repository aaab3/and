package com.runtime.tool.builtin

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HttpFetchTool(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : ToolHandler {

    override val manifest = ToolManifest(
        name = "http_fetch",
        description = "Fetch the content of a URL. Returns the response body as text (truncated to 8000 chars).",
        inputSchemaJson = """{"type":"object","properties":{"url":{"type":"string","description":"The URL to fetch"},"method":{"type":"string","description":"HTTP method (GET or POST). Defaults to GET."}},"required":["url"]}""",
        outputSchemaJson = """{"type":"object","properties":{"status":{"type":"integer"},"body":{"type":"string"},"content_type":{"type":"string"}}}""",
        requiresUserConfirmation = true,
        timeoutMs = 30_000
    )

    override suspend fun execute(request: ToolExecutionRequest): AppResult<ToolExecutionResult> = withContext(Dispatchers.IO) {
        val args = parseArgs(request.argumentsJson)
        val url = args["url"]
        if (url.isNullOrBlank()) {
            return@withContext AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "url is required"))
        }

        val method = (args["method"] ?: "GET").uppercase()

        try {
            val httpRequest = Request.Builder()
                .url(url)
                .apply {
                    when (method) {
                        "POST" -> post(okhttp3.RequestBody.create(null, ByteArray(0)))
                        else -> get()
                    }
                }
                .header("User-Agent", "RuntimeApp/1.0")
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string()?.take(8000) ?: ""
                val contentType = response.header("Content-Type") ?: ""
                val escaped = body
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "")
                    .replace("\t", "\\t")

                AppResult.Success(
                    ToolExecutionResult(
                        outputJson = """{"status":${response.code},"content_type":"$contentType","body":"$escaped"}"""
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.EXECUTION_ERROR, "HTTP fetch failed: ${e.message?.take(200)}")
            )
        }
    }
}
