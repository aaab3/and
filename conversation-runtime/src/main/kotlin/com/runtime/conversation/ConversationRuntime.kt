package com.runtime.conversation

import com.runtime.core.AppResult

interface ConversationRuntime {
    suspend fun sendMessage(
        request: SendMessageRequest
    ): AppResult<SendMessageResult>
}
