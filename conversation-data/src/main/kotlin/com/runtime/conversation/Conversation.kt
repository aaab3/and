package com.runtime.conversation

data class Conversation(
    val id: String,
    val workspaceId: String,
    val title: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAtEpochMs: Long,
    val toolCallId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
