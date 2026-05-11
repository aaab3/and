package com.runtime.android.db

import com.runtime.conversation.Conversation
import com.runtime.conversation.ConversationMessage
import com.runtime.conversation.MessageRole

internal fun ConversationEntity.toDomain(): Conversation =
    Conversation(
        id = id,
        workspaceId = workspaceId,
        title = title,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

internal fun Conversation.toEntity(): ConversationEntity =
    ConversationEntity(
        id = id,
        workspaceId = workspaceId,
        title = title,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

internal fun MessageEntity.toDomain(metadata: Map<String, String>): ConversationMessage =
    ConversationMessage(
        id = id,
        conversationId = conversationId,
        role = MessageRole.valueOf(role),
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        toolCallId = toolCallId,
        metadata = metadata
    )
