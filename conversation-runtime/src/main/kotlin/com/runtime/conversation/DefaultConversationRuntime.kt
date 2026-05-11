package com.runtime.conversation

import com.runtime.binding.BindingResolveRequest
import com.runtime.binding.BindingResolver
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.model.ModelGenerateRequest
import com.runtime.model.ModelMessage
import com.runtime.model.ProviderRegistry

/**
 * Minimal send loop: persist user → history → binding → provider → persist assistant on success only.
 */
class DefaultConversationRuntime(
    private val conversationStore: ConversationStore,
    private val bindingResolver: BindingResolver,
    private val providerRegistry: ProviderRegistry
) : ConversationRuntime {

    override suspend fun sendMessage(request: SendMessageRequest): AppResult<SendMessageResult> {
        if (request.userMessage.isBlank()) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "userMessage must not be blank")
            )
        }

        val conversation = when (val c = conversationStore.getConversation(request.conversationId)) {
            is AppResult.Failure -> return c
            is AppResult.Success -> c.value
                ?: return AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Conversation not found",
                        metadata = mapOf("conversationId" to request.conversationId)
                    )
                )
        }

        if (conversation.workspaceId != request.workspaceId) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "workspaceId does not match conversation",
                    metadata = mapOf(
                        "workspaceId" to request.workspaceId,
                        "conversationWorkspaceId" to conversation.workspaceId
                    )
                )
            )
        }

        val userMsg = when (val u = conversationStore.appendUserMessage(request.conversationId, request.userMessage)) {
            is AppResult.Failure -> return u
            is AppResult.Success -> u.value
        }

        val history = when (val h = conversationStore.loadMessageHistory(request.conversationId)) {
            is AppResult.Failure -> return h
            is AppResult.Success -> h.value
        }

        val modelMessages = history.map { it.toModelMessage() }
        val generateRequest = ModelGenerateRequest(messages = modelMessages)

        val resolved = when (val r = bindingResolver.resolve(
            BindingResolveRequest(
                workspaceId = request.workspaceId,
                skillId = request.skillId,
                conversationId = request.conversationId
            )
        )) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }

        val provider = when (val p = providerRegistry.getProvider(resolved.provider.providerType)) {
            is AppResult.Failure -> return p
            is AppResult.Success -> p.value
        }

        val providerResult = when (val g = provider.generate(resolved, generateRequest)) {
            is AppResult.Failure -> return g
            is AppResult.Success -> g.value
        }

        val assistantText = providerResult.text
        val assistantMsg = when (val a = conversationStore.appendAssistantMessage(request.conversationId, assistantText)) {
            is AppResult.Failure -> return a
            is AppResult.Success -> a.value
        }

        val updatedConversation = when (val c2 = conversationStore.getConversation(request.conversationId)) {
            is AppResult.Failure -> return c2
            is AppResult.Success -> c2.value
                ?: return AppResult.Failure(
                    AppError(ErrorCodes.NOT_FOUND, message = "Conversation disappeared after send")
                )
        }

        return AppResult.Success(
            SendMessageResult(
                conversation = updatedConversation,
                userMessage = userMsg,
                assistantMessage = assistantMsg,
                providerResult = providerResult
            )
        )
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
