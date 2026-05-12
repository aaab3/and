package com.runtime.conversation

import com.runtime.binding.BindingResolveRequest
import com.runtime.binding.BindingResolver
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.*
import com.runtime.tool.ToolExecutionRequest
import com.runtime.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DefaultConversationRuntime(
    private val conversationStore: ConversationStore,
    private val bindingResolver: BindingResolver,
    private val providerRegistry: ProviderRegistry,
    private val toolRegistry: ToolRegistry? = null,
    private val systemPrompt: String? = null,
    private val tools: List<ModelToolSpec> = emptyList(),
    private val maxToolLoops: Int = 8
) : ConversationRuntime {

    // --- Non-streaming (kept for backwards compat) ---

    override suspend fun sendMessage(request: SendMessageRequest): AppResult<SendMessageResult> {
        if (request.userMessage.isBlank()) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "userMessage must not be blank"))
        }

        val conversation = when (val c = conversationStore.getConversation(request.conversationId)) {
            is AppResult.Failure -> return c
            is AppResult.Success -> c.value
                ?: return AppResult.Failure(AppError(ErrorCodes.NOT_FOUND, "Conversation not found"))
        }
        if (conversation.workspaceId != request.workspaceId) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "workspaceId mismatch"))
        }

        val userMsg = when (val u = conversationStore.appendUserMessage(request.conversationId, request.userMessage)) {
            is AppResult.Failure -> return u
            is AppResult.Success -> u.value
        }

        val history = when (val h = conversationStore.loadMessageHistory(request.conversationId)) {
            is AppResult.Failure -> return h
            is AppResult.Success -> h.value
        }

        val modelMessages = buildInitialMessages(history)

        val resolved = when (val r = bindingResolver.resolve(
            BindingResolveRequest(request.workspaceId, request.skillId, request.conversationId)
        )) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val provider = when (val p = providerRegistry.getProvider(resolved.provider.providerType)) {
            is AppResult.Failure -> return p
            is AppResult.Success -> p.value
        }

        var loopCount = 0
        var lastResult: ModelGenerateResult

        while (true) {
            val generateRequest = ModelGenerateRequest(modelMessages.toList(), tools)
            lastResult = when (val g = provider.generate(resolved, generateRequest)) {
                is AppResult.Failure -> return g
                is AppResult.Success -> g.value
            }

            if (lastResult.toolCalls.isEmpty() || toolRegistry == null) break
            loopCount++
            if (loopCount > maxToolLoops) break

            modelMessages.add(ModelMessage(role = "assistant", content = lastResult.text))
            for (toolCall in lastResult.toolCalls) {
                val toolResult = executeToolCall(toolCall, request)
                modelMessages.add(ModelMessage(role = "tool", content = toolResult, toolCallId = toolCall.id))
            }
        }

        val assistantMsg = when (val a = conversationStore.appendAssistantMessage(request.conversationId, lastResult.text)) {
            is AppResult.Failure -> return a
            is AppResult.Success -> a.value
        }

        val updatedConversation = when (val c2 = conversationStore.getConversation(request.conversationId)) {
            is AppResult.Failure -> return c2
            is AppResult.Success -> c2.value
                ?: return AppResult.Failure(AppError(ErrorCodes.NOT_FOUND, "Conversation disappeared"))
        }

        return AppResult.Success(SendMessageResult(updatedConversation, userMsg, assistantMsg, lastResult))
    }

    // --- Streaming ---

    override fun sendMessageStream(request: SendMessageRequest): Flow<SendMessageEvent> = flow {
        if (request.userMessage.isBlank()) {
            emit(SendMessageEvent.Error(ErrorCodes.INVALID_INPUT, "消息内容不能为空"))
            return@flow
        }

        // Validate conversation
        val conversation = when (val c = conversationStore.getConversation(request.conversationId)) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(c.error.code, c.error.message))
                return@flow
            }
            is AppResult.Success -> c.value ?: run {
                emit(SendMessageEvent.Error(ErrorCodes.NOT_FOUND, "对话不存在"))
                return@flow
            }
        }
        if (conversation.workspaceId != request.workspaceId) {
            emit(SendMessageEvent.Error(ErrorCodes.INVALID_INPUT, "workspace 不匹配"))
            return@flow
        }

        // Persist user message
        val userMsg = when (val u = conversationStore.appendUserMessage(request.conversationId, request.userMessage)) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(u.error.code, u.error.message))
                return@flow
            }
            is AppResult.Success -> u.value
        }
        emit(SendMessageEvent.UserMessagePersisted(userMsg))

        // Load history
        val history = when (val h = conversationStore.loadMessageHistory(request.conversationId)) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(h.error.code, h.error.message))
                return@flow
            }
            is AppResult.Success -> h.value
        }

        // Resolve binding + provider
        val resolved = when (val r = bindingResolver.resolve(
            BindingResolveRequest(request.workspaceId, request.skillId, request.conversationId)
        )) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(r.error.code, r.error.message))
                return@flow
            }
            is AppResult.Success -> r.value
        }
        val provider = when (val p = providerRegistry.getProvider(resolved.provider.providerType)) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(p.error.code, p.error.message))
                return@flow
            }
            is AppResult.Success -> p.value
        }

        val modelMessages = buildInitialMessages(history)
        val assistantTextBuilder = StringBuilder()

        var loopCount = 0
        while (true) {
            val generateRequest = ModelGenerateRequest(modelMessages.toList(), tools)
            val streamText = StringBuilder()
            val streamToolCalls = mutableListOf<ModelToolCall>()
            var streamError: String? = null

            provider.generateStream(resolved, generateRequest).collect { chunk ->
                if (chunk.error != null) {
                    streamError = chunk.error
                    return@collect
                }
                if (chunk.deltaText.isNotEmpty()) {
                    streamText.append(chunk.deltaText)
                    emit(SendMessageEvent.Delta(chunk.deltaText))
                }
                if (chunk.isDone) {
                    streamToolCalls.addAll(chunk.toolCalls)
                }
            }

            if (streamError != null) {
                emit(SendMessageEvent.Error(ErrorCodes.PROVIDER_ERROR, streamError!!))
                return@flow
            }

            assistantTextBuilder.append(streamText)

            // No tool calls → we're done
            if (streamToolCalls.isEmpty() || toolRegistry == null) break

            loopCount++
            if (loopCount > maxToolLoops) break

            // Add assistant message with tool calls
            modelMessages.add(ModelMessage(role = "assistant", content = streamText.toString()))

            // Execute each tool
            for (toolCall in streamToolCalls) {
                emit(SendMessageEvent.ToolExecuting(toolCall.name, toolCall.argumentsJson))
                val toolResult = executeToolCall(toolCall, request)
                val isError = toolResult.contains("\"error\"")
                emit(SendMessageEvent.ToolResult(toolCall.name, toolResult, isError))
                modelMessages.add(ModelMessage(role = "tool", content = toolResult, toolCallId = toolCall.id))
            }
        }

        // Persist final assistant message
        val finalText = assistantTextBuilder.toString()
        val assistantMsg = when (val a = conversationStore.appendAssistantMessage(request.conversationId, finalText)) {
            is AppResult.Failure -> {
                emit(SendMessageEvent.Error(a.error.code, a.error.message))
                return@flow
            }
            is AppResult.Success -> a.value
        }

        emit(SendMessageEvent.Done(assistantMsg))
    }

    // --- Shared helpers ---

    private fun buildInitialMessages(history: List<ConversationMessage>): MutableList<ModelMessage> {
        val messages = mutableListOf<ModelMessage>()
        systemPrompt?.let { messages.add(ModelMessage(role = "system", content = it)) }
        history.forEach { messages.add(it.toModelMessage()) }
        return messages
    }

    private suspend fun executeToolCall(toolCall: ModelToolCall, request: SendMessageRequest): String {
        val registry = toolRegistry ?: return """{"error":"no tool registry"}"""
        val handler = when (val t = registry.getTool(toolCall.name)) {
            is AppResult.Failure -> return """{"error":"tool not found: ${toolCall.name}"}"""
            is AppResult.Success -> t.value
        }
        val execRequest = ToolExecutionRequest(
            toolName = toolCall.name,
            argumentsJson = toolCall.argumentsJson,
            conversationId = request.conversationId,
            skillId = request.skillId,
            workspaceId = request.workspaceId
        )
        return when (val result = handler.execute(execRequest)) {
            is AppResult.Success -> result.value.outputJson
            is AppResult.Failure -> """{"error":"${result.error.message}"}"""
        }
    }

    private fun ConversationMessage.toModelMessage(): ModelMessage {
        val roleStr = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
            MessageRole.TOOL -> "tool"
        }
        return ModelMessage(role = roleStr, content = content, toolCallId = toolCallId)
    }
}
