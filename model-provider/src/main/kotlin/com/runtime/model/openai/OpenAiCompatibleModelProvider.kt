package com.runtime.model.openai

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.runtime.binding.ProviderType
import com.runtime.binding.ResolvedModelBinding
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

class OpenAiCompatibleModelProvider : ModelProvider {

    override val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE

    override suspend fun generate(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): AppResult<ModelGenerateResult> {
        if (request.messages.isEmpty()) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "messages must not be empty"))
        }

        val client = buildClient(binding)

        return try {
            val chatRequest = buildChatRequest(binding, request)
            val response = client.chatCompletion(chatRequest)
            val choice = response.choices.firstOrNull()
                ?: return AppResult.Failure(AppError(ErrorCodes.PROVIDER_ERROR, "Empty choices"))

            val toolCalls = choice.message.toolCalls?.mapNotNull { tc ->
                if (tc is ToolCall.Function) {
                    ModelToolCall(
                        id = tc.id.id,
                        name = tc.function.name,
                        argumentsJson = tc.function.arguments
                    )
                } else null
            } ?: emptyList()

            val usage = response.usage?.let { u ->
                ModelUsage(u.promptTokens, u.completionTokens, u.totalTokens)
            }

            AppResult.Success(
                ModelGenerateResult(
                    text = choice.message.content.orEmpty(),
                    toolCalls = toolCalls,
                    finishReason = choice.finishReason?.value,
                    usage = usage,
                    rawProviderMessageId = response.id
                )
            )
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.PROVIDER_ERROR,
                    message = e.message?.take(200) ?: "Provider call failed",
                    cause = e.javaClass.simpleName
                )
            )
        } finally {
            client.close()
        }
    }

    override fun generateStream(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): Flow<ModelStreamChunk> = flow {
        if (request.messages.isEmpty()) {
            emit(ModelStreamChunk(error = "messages must not be empty", isDone = true))
            return@flow
        }

        val client = buildClient(binding)
        try {
            val chatRequest = buildChatRequest(binding, request)

            // Collect tool call fragments (streaming gives partial JSON)
            val toolCallsBuilder = mutableMapOf<Int, ToolCallBuilder>()
            var finishReason: String? = null
            var usage: ModelUsage? = null

            client.chatCompletions(chatRequest).collect { chunk ->
                val choice = chunk.choices.firstOrNull() ?: return@collect

                // Text delta
                val deltaText = choice.delta?.content.orEmpty()

                // Tool call deltas (accumulate by index)
                choice.delta?.toolCalls?.forEach { tc ->
                    val index = tc.index ?: 0
                    val builder = toolCallsBuilder.getOrPut(index) { ToolCallBuilder() }
                    tc.id?.id?.let { builder.id = it }
                    tc.function?.nameOrNull?.let { builder.name = it }
                    tc.function?.argumentsOrNull?.let { builder.arguments.append(it) }
                }

                finishReason = choice.finishReason?.value ?: finishReason
                chunk.usage?.let { u ->
                    usage = ModelUsage(u.promptTokens, u.completionTokens, u.totalTokens)
                }

                if (deltaText.isNotEmpty()) {
                    emit(ModelStreamChunk(deltaText = deltaText))
                }
            }

            // Final chunk with tool calls + finish reason
            val toolCalls = toolCallsBuilder.values.map {
                ModelToolCall(
                    id = it.id ?: "",
                    name = it.name ?: "",
                    argumentsJson = it.arguments.toString().ifEmpty { "{}" }
                )
            }

            emit(ModelStreamChunk(
                toolCalls = toolCalls,
                finishReason = finishReason,
                usage = usage,
                isDone = true
            ))
        } catch (e: Exception) {
            emit(ModelStreamChunk(
                error = e.message?.take(200) ?: "Stream error",
                isDone = true
            ))
        } finally {
            client.close()
        }
    }.catch { e ->
        emit(ModelStreamChunk(
            error = e.message?.take(200) ?: "Stream error",
            isDone = true
        ))
    }

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private fun buildClient(binding: ResolvedModelBinding): OpenAI {
        val baseUrl = binding.provider.baseUrl.trim().trimEnd('/')
        val host = OpenAIHost(baseUrl = "$baseUrl/")
        val config = OpenAIConfig(token = binding.secretValue, host = host)
        return OpenAI(config)
    }

    private fun buildChatRequest(
        binding: ResolvedModelBinding,
        request: ModelGenerateRequest
    ): ChatCompletionRequest {
        val messages = request.messages.map { msg ->
            when (msg.role) {
                "system" -> ChatMessage.System(msg.content)
                "assistant" -> ChatMessage.Assistant(msg.content)
                "tool" -> ChatMessage.Tool(
                    content = msg.content,
                    toolCallId = ToolId(msg.toolCallId ?: "")
                )
                else -> ChatMessage.User(msg.content)
            }
        }

        val tools: List<Tool>? = if (request.tools.isEmpty()) null else {
            request.tools.map { spec ->
                Tool.function(
                    name = spec.name,
                    description = spec.description,
                    parameters = Parameters.fromJsonString(spec.inputSchemaJson)
                )
            }
        }

        return chatCompletionRequest {
            model = ModelId(binding.model.modelId)
            this.messages = messages
            tools?.let { this.tools = it }
            request.temperature?.let { this.temperature = it }
            request.maxOutputTokens?.let { this.maxTokens = it }
        }
    }
}
