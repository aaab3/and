package com.runtime.conversation

import com.runtime.model.ModelGenerateResult

data class SendMessageRequest(
    val workspaceId: String,
    val conversationId: String,
    val userMessage: String,
    val skillId: String? = null
)

data class SendMessageResult(
    val conversation: Conversation,
    val userMessage: ConversationMessage,
    val assistantMessage: ConversationMessage,
    val providerResult: ModelGenerateResult
)
