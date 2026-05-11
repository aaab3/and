package com.runtime.conversation

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Orchestrates repositories for the [ConversationStore] contract; serializes mutating operations.
 */
class DefaultConversationStore(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) : ConversationStore {

    private val mutex = Mutex()

    override suspend fun createConversation(workspaceId: String, title: String?): AppResult<Conversation> =
        mutex.withLock {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val conversation = Conversation(
                id = id,
                workspaceId = workspaceId,
                title = title,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            conversationRepository.createConversation(conversation)
        }

    override suspend fun getConversation(conversationId: String): AppResult<Conversation?> =
        mutex.withLock {
            conversationRepository.getConversation(conversationId)
        }

    override suspend fun appendUserMessage(conversationId: String, content: String): AppResult<ConversationMessage> =
        appendMessage(conversationId, content, MessageRole.USER)

    override suspend fun appendAssistantMessage(conversationId: String, content: String): AppResult<ConversationMessage> =
        appendMessage(conversationId, content, MessageRole.ASSISTANT)

    override suspend fun loadMessageHistory(conversationId: String): AppResult<List<ConversationMessage>> =
        mutex.withLock {
            messageRepository.listMessages(conversationId)
        }

    private suspend fun appendMessage(
        conversationId: String,
        content: String,
        role: MessageRole
    ): AppResult<ConversationMessage> = mutex.withLock {
        when (val existing = conversationRepository.getConversation(conversationId)) {
            is AppResult.Failure -> existing
            is AppResult.Success -> {
                val conversation = existing.value
                    ?: return@withLock AppResult.Failure(
                        AppError(
                            code = ErrorCodes.NOT_FOUND,
                            message = "Conversation not found",
                            metadata = mapOf("conversationId" to conversationId)
                        )
                    )
                val now = System.currentTimeMillis()
                val message = ConversationMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = role,
                    content = content,
                    createdAtEpochMs = now,
                    toolCallId = null,
                    metadata = emptyMap()
                )
                when (val appended = messageRepository.appendMessage(message)) {
                    is AppResult.Failure -> appended
                    is AppResult.Success -> {
                        val updated = conversation.copy(updatedAtEpochMs = now)
                        when (val saved = conversationRepository.updateConversation(updated)) {
                            is AppResult.Failure -> saved
                            is AppResult.Success -> appended
                        }
                    }
                }
            }
        }
    }
}
