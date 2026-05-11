package com.runtime.conversation.sqlite

import com.runtime.conversation.Conversation
import com.runtime.conversation.ConversationRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteConversationRepository(
    private val dataSource: SqliteDataSource
) : ConversationRepository {

    override suspend fun createConversation(conversation: Conversation): AppResult<Conversation> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO conversations ("id","workspaceId","title","createdAtEpochMs","updatedAtEpochMs")
                    VALUES (?,?,?,?,?)
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, conversation.id)
                    ps.setString(2, conversation.workspaceId)
                    if (conversation.title == null) ps.setNull(3, java.sql.Types.VARCHAR)
                    else ps.setString(3, conversation.title)
                    ps.setLong(4, conversation.createdAtEpochMs)
                    ps.setLong(5, conversation.updatedAtEpochMs)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(conversation)
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Failed to create conversation",
                    cause = e.message
                )
            )
        }
    }

    override suspend fun getConversation(conversationId: String): AppResult<Conversation?> = withContext(Dispatchers.IO) {
        try {
            val row = dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","workspaceId","title","createdAtEpochMs","updatedAtEpochMs"
                    FROM conversations WHERE "id" = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, conversationId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@withConnection null
                        Conversation(
                            id = rs.getString("id"),
                            workspaceId = rs.getString("workspaceId"),
                            title = rs.getString("title").takeUnless { rs.wasNull() },
                            createdAtEpochMs = rs.getLong("createdAtEpochMs"),
                            updatedAtEpochMs = rs.getLong("updatedAtEpochMs")
                        )
                    }
                }
            }
            AppResult.Success(row)
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Failed to load conversation",
                    cause = e.message
                )
            )
        }
    }

    override suspend fun updateConversation(conversation: Conversation): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val updated = dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    UPDATE conversations SET
                      "workspaceId" = ?,
                      "title" = ?,
                      "createdAtEpochMs" = ?,
                      "updatedAtEpochMs" = ?
                    WHERE "id" = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, conversation.workspaceId)
                    if (conversation.title == null) ps.setNull(2, java.sql.Types.VARCHAR)
                    else ps.setString(2, conversation.title)
                    ps.setLong(3, conversation.createdAtEpochMs)
                    ps.setLong(4, conversation.updatedAtEpochMs)
                    ps.setString(5, conversation.id)
                    ps.executeUpdate()
                }
            }
            if (updated == 0) {
                AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Conversation not found for update",
                        metadata = mapOf("conversationId" to conversation.id)
                    )
                )
            } else {
                AppResult.Success(Unit)
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Failed to update conversation",
                    cause = e.message
                )
            )
        }
    }
}
