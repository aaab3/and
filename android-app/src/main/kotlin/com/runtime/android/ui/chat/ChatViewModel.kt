package com.runtime.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runtime.conversation.*
import com.runtime.core.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val runtimeProvider: () -> ConversationRuntime?,
    private val skillRuntimeProvider: (skillName: String) -> ConversationRuntime? = { null },
    private val store: ConversationStore,
    private val onTitleGenerated: (conversationId: String, userMsg: String, assistantMsg: String) -> Unit = { _, _, _ -> },
    private val onMessageDeleted: (messageId: String) -> Unit = { _ -> },
    private val workspaceId: String = "ws-1"
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(conversationId = conversationId, messages = emptyList()) }
            val result = withContext(Dispatchers.IO) { store.loadMessageHistory(conversationId) }
            when (result) {
                is AppResult.Success -> {
                    val messages = result.value.map { msg ->
                        UiMessage(msg.id, msg.role, msg.content, msg.createdAtEpochMs)
                    }
                    _uiState.update { it.copy(messages = messages) }
                }
                is AppResult.Failure -> _uiState.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val conversationId = _uiState.value.conversationId
        if (conversationId.isBlank()) return

        val (skillName, actualMessage) = parseSkillPrefix(text)

        val runtime = if (skillName != null) {
            skillRuntimeProvider(skillName) ?: run {
                _uiState.update { it.copy(error = "找不到技能 '$skillName'") }
                return
            }
        } else {
            runtimeProvider() ?: run {
                _uiState.update { it.copy(error = "未配置模型，请前往「设置」添加") }
                return
            }
        }

        // Optimistic user message
        val tempUserId = "temp-user-${System.currentTimeMillis()}"
        val tempAssistantId = "temp-assistant-${System.currentTimeMillis()}"
        _uiState.update {
            it.copy(
                messages = it.messages + UiMessage(
                    id = tempUserId,
                    role = MessageRole.USER,
                    content = text,
                    timestamp = System.currentTimeMillis()
                ) + UiMessage(
                    id = tempAssistantId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true
                ),
                isSending = true,
                error = null
            )
        }

        viewModelScope.launch {
            val streamingText = StringBuilder()
            var isFirstMessage = _uiState.value.messages.size <= 2

            runtime.sendMessageStream(
                SendMessageRequest(
                    workspaceId = workspaceId,
                    conversationId = conversationId,
                    userMessage = actualMessage
                )
            ).collect { event ->
                when (event) {
                    is SendMessageEvent.UserMessagePersisted -> {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map {
                                if (it.id == tempUserId) UiMessage(
                                    id = event.message.id,
                                    role = MessageRole.USER,
                                    content = event.message.content,
                                    timestamp = event.message.createdAtEpochMs
                                ) else it
                            })
                        }
                    }
                    is SendMessageEvent.Delta -> {
                        // Special marker: reset the streaming message content
                        if (event.text == "\u0000CLEAR\u0000") {
                            streamingText.clear()
                            _uiState.update { state ->
                                state.copy(messages = state.messages.map {
                                    if (it.id == tempAssistantId) it.copy(content = "") else it
                                })
                            }
                        } else {
                            streamingText.append(event.text)
                            val current = streamingText.toString()
                            _uiState.update { state ->
                                state.copy(messages = state.messages.map {
                                    if (it.id == tempAssistantId) it.copy(content = current) else it
                                })
                            }
                        }
                    }
                    is SendMessageEvent.ToolExecuting -> {
                        val toolMsg = UiMessage(
                            id = "tool-${System.currentTimeMillis()}",
                            role = MessageRole.TOOL,
                            content = "🔧 ${event.toolName}",
                            timestamp = System.currentTimeMillis(),
                            toolName = event.toolName,
                            toolArgs = event.argumentsJson,
                            isStreaming = true
                        )
                        _uiState.update { state ->
                            // Insert tool message before the streaming assistant message
                            val newMessages = state.messages.toMutableList()
                            val assistantIdx = newMessages.indexOfFirst { it.id == tempAssistantId }
                            if (assistantIdx >= 0) {
                                newMessages.add(assistantIdx, toolMsg)
                            } else {
                                newMessages.add(toolMsg)
                            }
                            state.copy(messages = newMessages)
                        }
                    }
                    is SendMessageEvent.ToolResult -> {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map {
                                if (it.role == MessageRole.TOOL && it.toolName == event.toolName && it.isStreaming) {
                                    it.copy(
                                        toolResult = event.outputJson,
                                        toolIsError = event.isError,
                                        isStreaming = false
                                    )
                                } else it
                            })
                        }
                    }
                    is SendMessageEvent.Done -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map {
                                    if (it.id == tempAssistantId) UiMessage(
                                        id = event.assistantMessage.id,
                                        role = MessageRole.ASSISTANT,
                                        content = event.assistantMessage.content,
                                        timestamp = event.assistantMessage.createdAtEpochMs,
                                        isStreaming = false
                                    ) else it
                                },
                                isSending = false
                            )
                        }

                        // Generate title for first message
                        if (isFirstMessage) {
                            onTitleGenerated(conversationId, actualMessage, event.assistantMessage.content)
                        }
                    }
                    is SendMessageEvent.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.filter { it.id != tempAssistantId },
                                isSending = false,
                                error = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != messageId })
        }
        onMessageDeleted(messageId)
    }

    fun resendLastUser() {
        val messages = _uiState.value.messages
        val lastUser = messages.findLast { it.role == MessageRole.USER } ?: return
        sendMessage(lastUser.content)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(conversationTitle = title) }
    }

    private fun parseSkillPrefix(text: String): Pair<String?, String> {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("@")) return null to text
        val spaceIdx = trimmed.indexOf(' ')
        if (spaceIdx <= 1) return null to text
        val skillName = trimmed.substring(1, spaceIdx)
        val message = trimmed.substring(spaceIdx + 1).trim()
        return if (message.isNotBlank()) skillName to message else null to text
    }
}
