package com.runtime.conversation

import com.runtime.core.AppResult

interface MessageRepository {
    suspend fun appendMessage(message: ConversationMessage): AppResult<ConversationMessage>
    suspend fun listMessages(conversationId: String): AppResult<List<ConversationMessage>>
}
