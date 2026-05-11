package com.runtime.conversation

import com.runtime.core.AppResult

interface ConversationStore {
    suspend fun createConversation(
        workspaceId: String,
        title: String?
    ): AppResult<Conversation>

    suspend fun getConversation(
        conversationId: String
    ): AppResult<Conversation?>

    suspend fun appendUserMessage(
        conversationId: String,
        content: String
    ): AppResult<ConversationMessage>

    suspend fun appendAssistantMessage(
        conversationId: String,
        content: String
    ): AppResult<ConversationMessage>

    suspend fun loadMessageHistory(
        conversationId: String
    ): AppResult<List<ConversationMessage>>
}
