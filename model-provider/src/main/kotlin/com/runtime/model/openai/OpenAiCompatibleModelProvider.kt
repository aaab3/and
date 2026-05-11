package com.runtime.model.openai

import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.ModelGenerateRequest
import com.runtime.model.ModelGenerateResult
import com.runtime.model.ModelMessage
import com.runtime.model.ModelProvider
import com.runtime.model.ModelToolCall
import com.runtime.model.ModelToolSpec
import com.runtime.model.ModelUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Text-only, non-streaming [chat completions](https://platform.openai.com/docs/api-reference/chat/create)-compatible client.
 * Does not execute tools; only sends declarations and parses returned tool calls.
 */
class OpenAiCompatibleModelProvider(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: JsonElementCodec = JsonElementCodec()
) : ModelProvider {

    override val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE

    override suspend fun generate(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<ModelGenerateResult> = withContext(Dispatchers.IO) {
        if (request.messages.isEmpty()) {
            return@withContext AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, "messages must not be empty")
            )
        }

        val bodyResult = json.buildChatCompletionBody(binding, request)
        val bodyJson = when (bodyResult) {
            is AppResult.Failure -> return@withContext bodyResult
            is AppResult.Success -> bodyResult.value
        }

        val url = resolveChatCompletionsUrl(binding.provider.baseUrl)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val httpRequest = Request.Builder()
            .url(url)
            .post(json.encodeJsonObject(bodyJson).toRequestBody(mediaType))
            .header("Authorization", "Bearer ${binding.secretValue}")
            .apply {
                binding.provider.defaultHeaders.forEach { (k, v) ->
                    header(k, v)
                }
            }
            .build()

        try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string()
                    ?: return@use AppResult.Failure(
                        AppError(ErrorCodes.PROVIDER_ERROR, "Empty response body", metadata = mapOf("httpStatus" to "${response.code}"))
                    )

                if (!response.isSuccessful) {
                    return@use AppResult.Failure(
                        parseProviderHttpError(response.code, raw)
                    )
                }

                json.parseChatCompletionResponse(raw)
            }
        } catch (e: IOException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.PROVIDER_ERROR,
                    message = "Network error calling provider",
                    cause = e.message
                )
            )
        }
    }

    companion object {
        fun resolveChatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
                return trimmed
            }
            return if (trimmed.endsWith("/v1", ignoreCase = true)) {
                "$trimmed/chat/completions"
            } else {
                "$trimmed/v1/chat/completions"
            }
        }

        private fun parseProviderHttpError(code: Int, raw: String): AppError {
            val msg = try {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: raw.take(500)
            } catch (_: Exception) {
                raw.take(500)
            }
            return AppError(
                code = ErrorCodes.PROVIDER_ERROR,
                message = msg,
                metadata = mapOf("httpStatus" to "$code")
            )
        }
    }
}

/** Encapsulates JSON build/parse for easier unit testing. */
class JsonElementCodec(
    private val jsonFormat: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = false
    }
) {
    fun encodeJsonObject(obj: JsonObject): String = jsonFormat.encodeToString(JsonObject.serializer(), obj)

    fun buildChatCompletionBody(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<JsonObject> {
        val messages = buildJsonArray {
            for (m in request.messages) {
                add(messageToJson(m))
            }
        }

        val toolsArray = if (request.tools.isEmpty()) {
            null
        } else {
            val built = buildJsonArray {
                for (t in request.tools) {
                    when (val params = parseParameters(t)) {
                        is AppResult.Failure -> return params
                        is AppResult.Success -> {
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("function"))
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", JsonPrimitive(t.name))
                                            put("description", JsonPrimitive(t.description))
                                            put("parameters", params.value)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            built
        }

        return AppResult.Success(
            buildJsonObject {
                put("model", JsonPrimitive(binding.model.modelId))
                put("messages", messages)
                put("stream", JsonPrimitive(false))
                request.temperature?.let { put("temperature", JsonPrimitive(it)) }
                request.maxOutputTokens?.let { put("max_tokens", JsonPrimitive(it)) }
                if (toolsArray != null) {
                    put("tools", toolsArray)
                }
            }
        )
    }

    private fun parseParameters(tool: ModelToolSpec): AppResult<JsonElement> =
        try {
            val el = kotlinx.serialization.json.Json.parseToJsonElement(tool.inputSchemaJson.trim())
            when (el) {
                is JsonObject -> AppResult.Success(el)
                JsonNull -> AppResult.Success(buildJsonObject { })
                else -> AppResult.Failure(
                    AppError(
                        code = ErrorCodes.INVALID_INPUT,
                        message = "tool inputSchemaJson must be a JSON object",
                        metadata = mapOf("toolName" to tool.name)
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "Invalid tool inputSchemaJson",
                    cause = e.message,
                    metadata = mapOf("toolName" to tool.name)
                )
            )
        }

    private fun messageToJson(m: ModelMessage): JsonObject =
        buildJsonObject {
            put("role", JsonPrimitive(m.role))
            if (m.toolCallId != null) {
                put("tool_call_id", JsonPrimitive(m.toolCallId))
            }
            put("content", JsonPrimitive(m.content))
        }

    fun parseChatCompletionResponse(raw: String): AppResult<ModelGenerateResult> {
        return try {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            root["error"]?.jsonObject?.let { err ->
                val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "provider error"
                return@parseChatCompletionResponse AppResult.Failure(
                    AppError(ErrorCodes.PROVIDER_ERROR, message = msg)
                )
            }

            val id = root["id"]?.jsonPrimitive?.contentOrNull
            val choices = root["choices"]?.jsonArray
                ?: return@parseChatCompletionResponse AppResult.Failure(
                    AppError(ErrorCodes.PARSE_ERROR, message = "Missing choices in provider response")
                )
            if (choices.isEmpty()) {
                return@parseChatCompletionResponse AppResult.Failure(
                    AppError(ErrorCodes.PROVIDER_ERROR, message = "Empty choices from provider")
                )
            }

            val choice0 = choices[0].jsonObject
            val finishReason = choice0["finish_reason"]?.jsonPrimitive?.contentOrNull
            val message = choice0["message"]?.jsonObject
                ?: return@parseChatCompletionResponse AppResult.Failure(
                    AppError(ErrorCodes.PARSE_ERROR, message = "Missing message in first choice")
                )

            val text = extractTextContent(message["content"])
            val toolCalls = parseToolCalls(message["tool_calls"])

            val usageEl = root["usage"]?.jsonObject
            val usage = usageEl?.let { u ->
                ModelUsage(
                    inputTokens = u["prompt_tokens"]?.jsonPrimitive?.intOrNull,
                    outputTokens = u["completion_tokens"]?.jsonPrimitive?.intOrNull,
                    totalTokens = u["total_tokens"]?.jsonPrimitive?.intOrNull
                )
            }

            AppResult.Success(
                ModelGenerateResult(
                    text = text,
                    toolCalls = toolCalls,
                    finishReason = finishReason,
                    usage = usage,
                    rawProviderMessageId = id
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.PARSE_ERROR,
                    message = "Failed to parse provider JSON",
                    cause = e.message
                )
            )
        }
    }

    private fun extractTextContent(content: JsonElement?): String {
        if (content == null || content is JsonNull) return ""
        if (content !is JsonPrimitive) return ""
        val prim = content
        return if (prim.isString) prim.content else prim.contentOrNull ?: prim.toString()
    }

    private fun parseToolCalls(element: JsonElement?): List<ModelToolCall> {
        if (element == null || element is JsonNull) return emptyList()
        val arr = element as? JsonArray ?: return emptyList()
        val out = mutableListOf<ModelToolCall>()
        for (item in arr) {
            val obj = item.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val fn = obj["function"]?.jsonObject ?: continue
            val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val args = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            out.add(ModelToolCall(id = id, name = name, argumentsJson = args))
        }
        return out
    }
}
