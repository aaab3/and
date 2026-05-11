package com.runtime.android

import com.runtime.conversation.Conversation
import com.runtime.conversation.ConversationMessage
import com.runtime.conversation.ConversationRepository
import com.runtime.conversation.MessageRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryConversationRepository : ConversationRepository {
    private val mutex = Mutex()
    private val byId = mutableMapOf<String, Conversation>()

    override suspend fun createConversation(conversation: Conversation): AppResult<Conversation> = mutex.withLock {
        byId[conversation.id] = conversation
        AppResult.Success(conversation)
    }

    override suspend fun getConversation(conversationId: String): AppResult<Conversation?> = mutex.withLock {
        AppResult.Success(byId[conversationId])
    }

    override suspend fun updateConversation(conversation: Conversation): AppResult<Unit> = mutex.withLock {
        if (!byId.containsKey(conversation.id)) {
            AppResult.Failure(
                AppError(
                    ErrorCodes.NOT_FOUND,
                    message = "Conversation not found for update",
                    metadata = mapOf("conversationId" to conversation.id)
                )
            )
        } else {
            byId[conversation.id] = conversation
            AppResult.Success(Unit)
        }
    }
}

class InMemoryMessageRepository : MessageRepository {
    private val mutex = Mutex()
    private val messages = mutableListOf<ConversationMessage>()

    override suspend fun appendMessage(message: ConversationMessage): AppResult<ConversationMessage> = mutex.withLock {
        messages.add(message)
        AppResult.Success(message)
    }

    override suspend fun listMessages(conversationId: String): AppResult<List<ConversationMessage>> = mutex.withLock {
        AppResult.Success(
            messages.filter { it.conversationId == conversationId }
                .sortedBy { it.createdAtEpochMs }
        )
    }
}
