package com.runtime.android.db

import com.runtime.conversation.Conversation
import com.runtime.conversation.ConversationRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomConversationRepository(
    private val db: AppDatabase
) : ConversationRepository {

    private val dao get() = db.conversationDao()

    override suspend fun createConversation(conversation: Conversation): AppResult<Conversation> = withContext(Dispatchers.IO) {
        try {
            dao.insert(conversation.toEntity())
            AppResult.Success(conversation)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to create conversation", cause = e.message)
            )
        }
    }

    override suspend fun getConversation(conversationId: String): AppResult<Conversation?> = withContext(Dispatchers.IO) {
        try {
            AppResult.Success(dao.getById(conversationId)?.toDomain())
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to load conversation", cause = e.message)
            )
        }
    }

    override suspend fun updateConversation(conversation: Conversation): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val rows = dao.update(conversation.toEntity())
            if (rows == 0) {
                AppResult.Failure(
                    AppError(
                        ErrorCodes.NOT_FOUND,
                        message = "Conversation not found for update",
                        metadata = mapOf("conversationId" to conversation.id)
                    )
                )
            } else {
                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to update conversation", cause = e.message)
            )
        }
    }
}
