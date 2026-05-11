package com.runtime.android.db

import com.runtime.conversation.ConversationMessage
import com.runtime.conversation.MessageRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomMessageRepository(
    private val db: AppDatabase
) : MessageRepository {

    private val dao get() = db.messageDao()

    override suspend fun appendMessage(message: ConversationMessage): AppResult<ConversationMessage> = withContext(Dispatchers.IO) {
        try {
            val entity = MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                role = message.role.name,
                content = message.content,
                toolCallId = message.toolCallId,
                createdAtEpochMs = message.createdAtEpochMs,
                metadataJson = encodeMetadata(message.metadata)
            )
            dao.insert(entity)
            AppResult.Success(message)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to append message", cause = e.message)
            )
        }
    }

    override suspend fun listMessages(conversationId: String): AppResult<List<ConversationMessage>> = withContext(Dispatchers.IO) {
        try {
            val rows = dao.listForConversation(conversationId)
            val out = mutableListOf<ConversationMessage>()
            for (row in rows) {
                when (val meta = decodeMetadata(row.metadataJson)) {
                    is AppResult.Failure -> return@withContext meta
                    is AppResult.Success -> out.add(row.toDomain(meta.value))
                }
            }
            AppResult.Success(out)
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Invalid message role in storage", cause = e.message)
            )
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to list messages", cause = e.message)
            )
        }
    }
}
