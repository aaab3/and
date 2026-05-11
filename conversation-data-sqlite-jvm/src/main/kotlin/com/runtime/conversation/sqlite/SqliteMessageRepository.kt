package com.runtime.conversation.sqlite

import com.runtime.conversation.ConversationMessage
import com.runtime.conversation.MessageRepository
import com.runtime.conversation.MessageRole
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteMessageRepository(
    private val dataSource: SqliteDataSource
) : MessageRepository {

    override suspend fun appendMessage(message: ConversationMessage): AppResult<ConversationMessage> = withContext(Dispatchers.IO) {
        try {
            val metadataJson = encodeMetadata(message.metadata)
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO messages (
                      "id","conversationId","role","content","toolCallId","createdAtEpochMs","metadataJson"
                    ) VALUES (?,?,?,?,?,?,?)
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, message.id)
                    ps.setString(2, message.conversationId)
                    ps.setString(3, message.role.name)
                    ps.setString(4, message.content)
                    if (message.toolCallId == null) ps.setNull(5, java.sql.Types.VARCHAR)
                    else ps.setString(5, message.toolCallId)
                    ps.setLong(6, message.createdAtEpochMs)
                    ps.setString(7, metadataJson)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(message)
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Failed to append message",
                    cause = e.message
                )
            )
        }
    }

    override suspend fun listMessages(conversationId: String): AppResult<List<ConversationMessage>> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","conversationId","role","content","toolCallId","createdAtEpochMs","metadataJson"
                    FROM messages
                    WHERE "conversationId" = ?
                    ORDER BY "createdAtEpochMs" ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, conversationId)
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<ConversationMessage>()
                        var parseError: AppError? = null
                        while (rs.next()) {
                            when (val decoded = decodeMetadata(rs.getString("metadataJson"))) {
                                is AppResult.Failure -> {
                                    parseError = decoded.error
                                    break
                                }
                                is AppResult.Success -> {
                                    out.add(
                                        ConversationMessage(
                                            id = rs.getString("id"),
                                            conversationId = rs.getString("conversationId"),
                                            role = MessageRole.valueOf(rs.getString("role")),
                                            content = rs.getString("content"),
                                            createdAtEpochMs = rs.getLong("createdAtEpochMs"),
                                            toolCallId = rs.getString("toolCallId").takeUnless { rs.wasNull() },
                                            metadata = decoded.value
                                        )
                                    )
                                }
                            }
                        }
                        if (parseError != null) {
                            AppResult.Failure(parseError)
                        } else {
                            AppResult.Success(out)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Failed to list messages",
                    cause = e.message
                )
            )
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(
                AppError(
                    code = ErrorCodes.STORAGE_ERROR,
                    message = "Invalid message role in storage",
                    cause = e.message
                )
            )
        }
    }
}
