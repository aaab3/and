package com.runtime.conversation

import com.runtime.core.AppResult
import kotlinx.coroutines.flow.Flow

interface ConversationRuntime {
    suspend fun sendMessage(request: SendMessageRequest): AppResult<SendMessageResult>

    /**
     * Stream a message send. Emits [SendMessageEvent]s as content arrives.
     * Finalizes with [SendMessageEvent.Done] or [SendMessageEvent.Error].
     */
    fun sendMessageStream(request: SendMessageRequest): Flow<SendMessageEvent>
}

sealed interface SendMessageEvent {
    /** User message persisted successfully. */
    data class UserMessagePersisted(val message: ConversationMessage) : SendMessageEvent

    /** Partial assistant text delta (streaming token). */
    data class Delta(val text: String) : SendMessageEvent

    /** A tool is being executed (shows in UI as a chip/bubble). */
    data class ToolExecuting(val toolName: String, val argumentsJson: String) : SendMessageEvent

    /** A tool finished. */
    data class ToolResult(val toolName: String, val outputJson: String, val isError: Boolean) : SendMessageEvent

    /** Final assistant message persisted. */
    data class Done(val assistantMessage: ConversationMessage) : SendMessageEvent

    /** Fatal error; stream terminated. */
    data class Error(val code: String, val message: String) : SendMessageEvent
}
