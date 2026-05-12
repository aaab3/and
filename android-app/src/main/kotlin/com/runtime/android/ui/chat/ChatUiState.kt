package com.runtime.android.ui.chat

import com.runtime.conversation.MessageRole

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val conversationId: String = "",
    val conversationTitle: String = "新对话"
)

data class UiMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolIsError: Boolean = false
) {
    val isUser: Boolean get() = role == MessageRole.USER
    val isTool: Boolean get() = role == MessageRole.TOOL
}
