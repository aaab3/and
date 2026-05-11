package com.runtime.conversation

import com.runtime.core.AppResult

interface ConversationRepository {
    suspend fun createConversation(conversation: Conversation): AppResult<Conversation>
    suspend fun getConversation(conversationId: String): AppResult<Conversation?>
    suspend fun updateConversation(conversation: Conversation): AppResult<Unit>
}
